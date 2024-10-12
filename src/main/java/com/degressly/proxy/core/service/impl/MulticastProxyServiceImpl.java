package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.Observation;
import com.degressly.proxy.core.service.ObservationPublisherService;
import com.degressly.proxy.core.service.MulticastProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.degressly.proxy.core.Constants.HEADERS_TO_SKIP;
import static com.degressly.proxy.core.Constants.TRACE_ID;

@Slf4j
@Service
public class MulticastProxyServiceImpl implements MulticastProxyService {

	@Value("${primary.host}")
	private String PRIMARY_HOST;

	@Value("${secondary.host}")
	private String SECONDARY_HOST;

	@Value("${candidate.host}")
	private String CANDIDATE_HOST;

	@Value("${return.response.from:PRIMARY}")
	private String RETURN_RESPONSE_FROM;

	/**
	 * Specifies the amount of time in milliseconds to wait before sending the request to
	 * secondary and candidate instances, after the request has been sent to the primary
	 * instance. May contain negative values, in which case the request will be sent to
	 * secondary/candidate before being sent to primary; this might be required when live
	 * cherry-picks from primary are being performed at runtime and the flow requires
	 * absence of a row for validation.
	 * <p>
	 * Please note that this does not account for any external factors such as network
	 * latency, it is purely the time to put the thread to sleep before initiating the
	 * call.
	 */
	@Value("${wait.after.forwarding.to.primary:0}")
	private String WAIT_AFTER_FORWARDING_TO_PRIMARY = "0";

	private final RestTemplate restTemplate = new RestTemplate();

	@Autowired
	List<ObservationPublisherService> publishers = Collections.emptyList();

	Logger logger = LoggerFactory.getLogger(MulticastProxyService.class);

	ExecutorService primaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService secondaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService candidateExecutorService = Executors.newCachedThreadPool();

	ExecutorService publisherExecutorService = Executors.newCachedThreadPool();

	@Override
	public ResponseEntity getResponse(HttpServletRequest httpServletRequest, MultiValueMap<String, String> headers,
			MultiValueMap<String, String> params, String body) {

		String traceId = MDC.get(TRACE_ID);

		Future<ResponseEntity> secondaryResponseFuture = secondaryExecutorService
			.submit(() -> getResponse(traceId, SECONDARY_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> candidateResponseFuture = candidateExecutorService
			.submit(() -> getResponse(traceId, CANDIDATE_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> primaryResponseFuture = primaryExecutorService
			.submit(() -> getResponse(traceId, PRIMARY_HOST, httpServletRequest, headers, params, body));

		publisherExecutorService.submit(() -> publishResponses(traceId, httpServletRequest.getRequestURI(),
				primaryResponseFuture, secondaryResponseFuture, candidateResponseFuture));

		try {
			return switch (RETURN_RESPONSE_FROM) {
				case "SECONDARY" -> secondaryResponseFuture.get();
				case "CANDIDATE" -> candidateResponseFuture.get();
				default -> primaryResponseFuture.get();
			};

		}
		catch (InterruptedException | ExecutionException e) {
			log.error("Error while requesting downstream", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	private ResponseEntity getResponse(String traceId, String host, HttpServletRequest httpServletRequest,
			MultiValueMap<String, String> requestHeaders, MultiValueMap<String, String> params, String body) {

		waitIfApplicable(host);

		MultiValueMap<String, String> headers = skipRestrictedHeaders(requestHeaders);

		headers.put("x-degressly-trace-id", Collections.singletonList(traceId));
		var httpEntity = new HttpEntity<>(body, headers);
		var queryParams = new HashMap<String, String>();

		var finalUrl = getFinalUrl(host, httpServletRequest, params, queryParams);

		HttpEntity<String> response;

		try {
			response = restTemplate.exchange(finalUrl, HttpMethod.valueOf(httpServletRequest.getMethod()), httpEntity,
					String.class, queryParams);

			logger.info("Response for for url {}: Status: {}, Headers: {}, Body: {}", finalUrl, "200",
					response.getHeaders(), response.getBody());

		}
		catch (RestClientResponseException e) {
			logger.info("Response for for url {}: Status: {} Headers: {}, Body: {}", finalUrl, e.getStatusCode(),
					e.getResponseHeaders(), e.getResponseBodyAsString());
			return new ResponseEntity(e.getResponseBodyAsString(), skipRestrictedHeaders(e.getResponseHeaders()),
					HttpStatus.valueOf(e.getStatusCode().value()));
		}
		catch (Exception e) {
			logger.info("Exception when calling downstream {}", e.getClass().getCanonicalName(), e);
			throw e;
		}

		return new ResponseEntity(response.getBody(), skipRestrictedHeaders(response.getHeaders()), HttpStatus.OK);

	}

	private static MultiValueMap<String, String> skipRestrictedHeaders(MultiValueMap<String, String> requestHeaders) {

		if (CollectionUtils.isEmpty(requestHeaders)) {
			return new LinkedMultiValueMap<>();
		}

		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
		requestHeaders.forEach((requestHeader, value) -> {
			if (!HEADERS_TO_SKIP.contains(requestHeader)) {
				headers.put(requestHeader, value);
			}
		});
		return headers;
	}

	private static String getFinalUrl(String host, HttpServletRequest httpServletRequest,
			MultiValueMap<String, String> params, Map<String, String> queryParams) {
		UriComponentsBuilder urlTemplate = UriComponentsBuilder.fromHttpUrl(host + httpServletRequest.getRequestURI());

		for (Map.Entry<String, List<String>> entry : params.entrySet()) {
			urlTemplate.queryParam(entry.getKey(), new StringBuilder("{" + entry.getKey() + "}"));
			queryParams.put(entry.getKey(), entry.getValue().getFirst());
		}

		urlTemplate = urlTemplate.encode();
		var uriComponents = urlTemplate.buildAndExpand(queryParams);
		var finalUrl = uriComponents.toString();
		return finalUrl;
	}

	private void publishResponses(String traceId, String requestUrl, Future<ResponseEntity> primaryResponseFuture,
			Future<ResponseEntity> secondaryResponseFuture, Future<ResponseEntity> candidateResponseFuture) {

		List<Future<ResponseEntity>> responseFutures = new ArrayList<>(
				Arrays.asList(primaryResponseFuture, secondaryResponseFuture, candidateResponseFuture));

		List<DownstreamResult> downstreamResults = new ArrayList<>();

		for (Future<ResponseEntity> responseFuture : responseFutures) {
			var downstreamResult = new DownstreamResult();
			downstreamResults.add(downstreamResult);

			try {
				ResponseEntity response = responseFuture.get();
				downstreamResult.setStatusCode(response.getStatusCode().toString());
				downstreamResult.setHeaders(response.getHeaders());
				downstreamResult.setBody((String) response.getBody());
			}
			catch (Exception e) {
				downstreamResult.setException(e.getMessage());
			}
		}

		Observation observation = Observation.builder()
			.traceId(traceId)
			.requestUrl(requestUrl)
			.observationType("RESPONSE")
			.primaryResult(downstreamResults.get(0))
			.secondaryResult(downstreamResults.get(1))
			.candidateResult(downstreamResults.get(2))
			.build();

		for (ObservationPublisherService publisher : publishers) {
			publisher.publish(observation);
		}
	}

	@SneakyThrows
	private void waitIfApplicable(String host) {
		int time = Integer.parseInt(WAIT_AFTER_FORWARDING_TO_PRIMARY);
		if (PRIMARY_HOST.equals(host) && time < 0) {
			Thread.sleep(Math.abs(time));
			return;
		}

		if (time > 0 && (SECONDARY_HOST.equals(host) || CANDIDATE_HOST.equals(host))) {
			Thread.sleep(time);
		}
	}

}
