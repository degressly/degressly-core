package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.adapter.KafkaToHttpServletRequestAdapter;
import com.degressly.proxy.core.dto.CachePopulationRequest;
import com.degressly.proxy.core.dto.DegresslyRequest;
import com.degressly.proxy.core.dto.GeneratedHttpServletRequest;
import com.degressly.proxy.core.http.HttpClient;
import com.degressly.proxy.core.service.ReplayHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.degressly.proxy.core.Constants.DEGRESSLY_CACHE_POPULATION_REQUEST;
import static com.degressly.proxy.core.Constants.TRACE_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReplayHandlerImpl implements ReplayHandler {

	private final HttpProxyMulticastServiceImpl multicastService;

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${degressly.downstream.host:localhost:8080}")
	private String downstreamHost;

	@Value("${degressly.delay.between.outgoing.calls:0}")
	private long delayBetweenOutgoingCalls;

	private final ExecutorService outgoingExecutorService = Executors.newVirtualThreadPerTaskExecutor();

	private final ExecutorService incomingExecutorService = Executors.newSingleThreadExecutor();

	private Future<?> previousIncomingRequestFuture = null;

	@Override
	public void handle(DegresslyRequest degresslyRequest) {

		if ("OUTGOING".equals(degresslyRequest.getType())) {
			outgoingExecutorService.submit(() -> handleOutgoingRequest(degresslyRequest));
		}
		else {
			performOneConcurrentIncomingRequest(degresslyRequest);
		}

	}

	private void performOneConcurrentIncomingRequest(DegresslyRequest degresslyRequest) {
		if (previousIncomingRequestFuture != null && !previousIncomingRequestFuture.isDone()) {
			try {
				previousIncomingRequestFuture.get();
			}
			catch (Exception e) {
				log.error("Error while executing incoming request: ", e);
			}
		}
		previousIncomingRequestFuture = incomingExecutorService.submit(() -> {
			handleIncomingRequest(degresslyRequest);
			if (delayBetweenOutgoingCalls > 0) {
				try {
					Thread.sleep(delayBetweenOutgoingCalls);
				}
				catch (InterruptedException e) {
					log.error("Error while sleeping between outgoing calls: ", e);
				}
			}
		});

	}

	private void handleIncomingRequest(DegresslyRequest degresslyRequest) {
		var httpServletRequest = getGeneratedHttpServletRequest(degresslyRequest);
		if (httpServletRequest == null) {
			return;
		}

		MDC.put(TRACE_ID, degresslyRequest.getTraceId());

		multicastService.getResponse(httpServletRequest, getMultiValueMap(degresslyRequest.getHeaders()),
				getMultiValueMap(degresslyRequest.getParams()), degresslyRequest.getBody(), true);
	}

	private static MultiValueMap<String, String> getMultiValueMap(Map<String, List<String>> originalMap) {
		if (CollectionUtils.isEmpty(originalMap)) {
			return new LinkedMultiValueMap<>();
		}

		return new LinkedMultiValueMap<>(originalMap);
	}

	private void handleOutgoingRequest(DegresslyRequest degresslyRequest) {
		var httpServletRequest = getGeneratedHttpServletRequest(degresslyRequest);
		if (httpServletRequest == null) {
			return;
		}
		var cachePopulationRequest = CachePopulationRequest.builder()
			.url(degresslyRequest.getUrl())
			.method(degresslyRequest.getMethod())
			.statusCode(degresslyRequest.getStatusCode())
			.body(degresslyRequest.getResponseBody())
			.headers(degresslyRequest.getResponseHeaders())
			.build();
		try {
			httpClient.getResponse(degresslyRequest.getTraceId(), downstreamHost, httpServletRequest,
					new LinkedMultiValueMap<>(
							Map.of(DEGRESSLY_CACHE_POPULATION_REQUEST, Collections.singletonList("true"))),
					new LinkedMultiValueMap<>(), objectMapper.writeValueAsString(cachePopulationRequest));
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	private static GeneratedHttpServletRequest getGeneratedHttpServletRequest(DegresslyRequest degresslyRequest) {
		var httpServletRequest = new GeneratedHttpServletRequest();
		try {
			httpServletRequest = KafkaToHttpServletRequestAdapter.convert(degresslyRequest);
		}
		catch (JsonProcessingException e) {
			log.error("Error while converting downstream request:", e);
			return null;
		}
		return httpServletRequest;
	}

}
