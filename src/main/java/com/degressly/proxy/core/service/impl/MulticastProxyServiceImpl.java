package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.Observation;
import com.degressly.proxy.core.service.ObservationPublisherService;
import com.degressly.proxy.core.service.MulticastProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.degressly.proxy.core.Constants.TRACE_ID;

@Service
public class MulticastProxyServiceImpl implements MulticastProxyService {

	@Value("${primary.host}")
	private String PRIMARY_HOST;

	@Value("${secondary.host}")
	private String SECONDARY_HOST;

	@Value("${candidate.host}")
	private String CANDIDATE_HOST;

	@Autowired
	List<ObservationPublisherService> publishers = Collections.emptyList();

	Logger logger = LoggerFactory.getLogger(MulticastProxyService.class);

	ExecutorService primaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService secondaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService candidateExecutorService = Executors.newCachedThreadPool();

	ExecutorService publisherExecutorService = Executors.newCachedThreadPool();

	@Override
	public ResponseEntity getResponseFromPrimary(HttpServletRequest httpServletRequest,
			MultiValueMap<String, String> headers, MultiValueMap<String, String> params, String body) {

		String traceId = MDC.get(TRACE_ID);

		Future<ResponseEntity> primaryResponseFuture = primaryExecutorService
			.submit(() -> getResponse(PRIMARY_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> secondaryResponseFuture = secondaryExecutorService
			.submit(() -> getResponse(SECONDARY_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> candidateResponseFuture = candidateExecutorService
			.submit(() -> getResponse(CANDIDATE_HOST, httpServletRequest, headers, params, body));

		publisherExecutorService.submit(() -> publishResponses(traceId, httpServletRequest.getRequestURI(),
				primaryResponseFuture, secondaryResponseFuture, candidateResponseFuture));

		try {
			return primaryResponseFuture.get();
		}
		catch (InterruptedException | ExecutionException e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	private ResponseEntity getResponse(String host, HttpServletRequest httpServletRequest,
			MultiValueMap<String, String> headers, MultiValueMap<String, String> params, String body) {

		var restTemplate = new RestTemplate();
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
		catch (HttpClientErrorException e) {
			logger.info("Response for for url {}: Status: {} Headers: {}, Body: {}", finalUrl, e.getStatusCode(),
					e.getResponseHeaders(), e.getResponseBodyAsString());
			return new ResponseEntity(e.getResponseBodyAsString(), e.getResponseHeaders(),
					HttpStatus.valueOf(e.getStatusCode().value()));
		}

		return new ResponseEntity(response.getBody(), response.getHeaders(), HttpStatus.OK);

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
				downstreamResult.setHttpResponse(response);
			}
			catch (Exception e) {
				downstreamResult.setException(e);
			}
		}

		Observation observation = Observation.builder()
			.traceId(traceId)
			.requestUrl(requestUrl)
			.primaryResult(downstreamResults.get(0))
			.secondaryResult(downstreamResults.get(1))
			.candidateResult(downstreamResults.get(2))
			.build();

		for (ObservationPublisherService publisher : publishers) {
			publisher.publish(observation);
		}
	}

}
