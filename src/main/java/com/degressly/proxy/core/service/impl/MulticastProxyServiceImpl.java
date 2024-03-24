package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.ResponsesDto;
import com.degressly.proxy.core.service.DiffPublisherService;
import com.degressly.proxy.core.service.MulticastProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.tuple.Pair;
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
	List<DiffPublisherService> publishers = Collections.emptyList();

	Logger logger = LoggerFactory.getLogger(MulticastProxyService.class);

	ExecutorService primaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService secondaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService candidateExecutorService = Executors.newCachedThreadPool();

	ExecutorService publisherExecutorService = Executors.newCachedThreadPool();

	@Override
	public ResponseEntity getResponseFromPrimary(HttpServletRequest httpServletRequest,
			MultiValueMap<String, String> headers, MultiValueMap<String, String> params, String body) {

		Future<ResponseEntity> primaryResponseFuture = primaryExecutorService
			.submit(() -> getResponse(PRIMARY_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> secondaryResponseFuture = secondaryExecutorService
			.submit(() -> getResponse(SECONDARY_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> candidateResponseFuture = candidateExecutorService
			.submit(() -> getResponse(CANDIDATE_HOST, httpServletRequest, headers, params, body));

		publisherExecutorService.submit(() -> publishResponses(MDC.get(TRACE_ID), primaryResponseFuture,
				secondaryResponseFuture, candidateResponseFuture));

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

		UriComponentsBuilder urlTemplate = UriComponentsBuilder.fromHttpUrl(host + httpServletRequest.getRequestURI());
		Map<String, String> queryParams = new HashMap<>();

		for (Map.Entry<String, List<String>> entry : params.entrySet()) {
			urlTemplate.queryParam(entry.getKey(), new StringBuilder("{" + entry.getKey() + "}"));
			queryParams.put(entry.getKey(), entry.getValue().getFirst());
		}
		urlTemplate = urlTemplate.encode();
		var uriComponents = urlTemplate.buildAndExpand(queryParams);
		var finalUrl = uriComponents.toString();

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

	private void publishResponses(String traceId, Future<ResponseEntity> primaryResponseFuture,
			Future<ResponseEntity> secondaryResponseFuture, Future<ResponseEntity> candidateResponseFuture) {

		List<Pair<Future<ResponseEntity>, DownstreamResult>> responsePairs = new ArrayList<>(
				Arrays.asList(Pair.of(primaryResponseFuture, new DownstreamResult()),
						Pair.of(secondaryResponseFuture, new DownstreamResult()),
						Pair.of(candidateResponseFuture, new DownstreamResult())));

		for (Pair<Future<ResponseEntity>, DownstreamResult> responsePair : responsePairs) {
			try {
				ResponseEntity primaryResponse = primaryResponseFuture.get();
				responsePair.getRight().setHttpResponse(primaryResponse);
			}
			catch (Exception e) {
				responsePair.getRight().setException(e);
			}
		}

		ResponsesDto responsesDto = ResponsesDto.builder()
			.primaryResult(responsePairs.get(0).getRight())
			.secondaryResult(responsePairs.get(1).getRight())
			.candidateResult(responsePairs.get(2).getRight())
			.build();

		for (DiffPublisherService publisher : publishers) {
			publisher.publish(responsesDto);
		}
	}

}
