package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.Observation;
import com.degressly.proxy.core.http.HttpClient;
import com.degressly.proxy.core.service.MulticastService;
import com.degressly.proxy.core.service.ObservationPublisherService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.degressly.proxy.core.Constants.TRACE_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpProxyMulticastServiceImpl implements MulticastService {

	@Value("${return.response.from:PRIMARY}")
	private String RETURN_RESPONSE_FROM;

	@Value("${primary.host}")
	private String PRIMARY_HOST;

	@Value("${secondary.host}")
	private String SECONDARY_HOST;

	@Value("${candidate.host}")
	private String CANDIDATE_HOST;

	private final List<ObservationPublisherService> publishers;

	private final HttpClient httpClient;

	ExecutorService primaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService secondaryExecutorService = Executors.newCachedThreadPool();

	ExecutorService candidateExecutorService = Executors.newCachedThreadPool();

	ExecutorService publisherExecutorService = Executors.newCachedThreadPool();

	@Override
	public ResponseEntity getResponse(HttpServletRequest httpServletRequest, MultiValueMap<String, String> headers,
			MultiValueMap<String, String> params, String body) {

		String traceId = MDC.get(TRACE_ID);

		Future<ResponseEntity> secondaryResponseFuture = secondaryExecutorService
			.submit(() -> httpClient.getResponse(traceId, SECONDARY_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> candidateResponseFuture = candidateExecutorService
			.submit(() -> httpClient.getResponse(traceId, CANDIDATE_HOST, httpServletRequest, headers, params, body));

		Future<ResponseEntity> primaryResponseFuture = primaryExecutorService
			.submit(() -> httpClient.getResponse(traceId, PRIMARY_HOST, httpServletRequest, headers, params, body));

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

}
