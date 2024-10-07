package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.Observation;
import com.degressly.proxy.core.service.ObservationPublisherService;
import jakarta.servlet.http.HttpServletRequest;
import joptsimple.internal.Reflection;
import org.jose4j.http.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@RunWith(MockitoJUnitRunner.Silent.class)
public class MulticastProxyServiceImplTest {

	@Mock
	private ObservationPublisherService publisher1;

	@Mock
	private ObservationPublisherService publisher2;

	@Mock
	private RestTemplate restTemplate;

	@Spy
	private List<ObservationPublisherService> publishers = new ArrayList<>();

	@InjectMocks
	private MulticastProxyServiceImpl multicastProxyService;

	@Before
	public void init() {
		publishers.add(publisher1);
		publishers.add(publisher2);

		ReflectionTestUtils.setField(multicastProxyService, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(multicastProxyService, "RETURN_RESPONSE_FROM", "PRIMARY");
		ReflectionTestUtils.setField(multicastProxyService, "PRIMARY_HOST", "http://PRIMARY_HOST");
		ReflectionTestUtils.setField(multicastProxyService, "SECONDARY_HOST", "http://SECONDARY_HOST");
		ReflectionTestUtils.setField(multicastProxyService, "CANDIDATE_HOST", "http://CANDIDATE_HOST");
	}

	@Test
	public void multicastTest() throws InterruptedException {

		var httpServletRequest = getHttpServeletRequest();
		var headers = getHeaders();
		var params = getParams();

		var primaryResponse = new ResponseEntity<>("primaryResponse", new LinkedMultiValueMap<>(), HttpStatus.OK);
		var secondaryResponse = new ResponseEntity<>("secondaryResponse", new LinkedMultiValueMap<>(), HttpStatus.OK);
		var candidateResponse = new ResponseEntity<>("candidateResponse", new LinkedMultiValueMap<>(), HttpStatus.OK);

		when(httpServletRequest.getRequestURI()).thenReturn("/test");
		when(httpServletRequest.getMethod()).thenReturn(HttpMethod.POST.name());

		when(restTemplate.exchange(eq("http://PRIMARY_HOST/test?param1=test"), any(HttpMethod.class),
				any(HttpEntity.class), eq(String.class), anyMap()))
			.thenReturn(primaryResponse);
		when(restTemplate.exchange(eq("http://SECONDARY_HOST/test?param1=test"), any(HttpMethod.class),
				any(HttpEntity.class), eq(String.class), anyMap()))
			.thenReturn(secondaryResponse);
		when(restTemplate.exchange(eq("http://CANDIDATE_HOST/test?param1=test"), any(HttpMethod.class),
				any(HttpEntity.class), eq(String.class), anyMap()))
			.thenReturn(candidateResponse);

		ResponseEntity<?> multicastResponse = multicastProxyService.getResponse(httpServletRequest, headers, params,
				null);

		// Sleep to prevent test from being flaky if primary thread takes longer to
		// complete
		Thread.sleep(1000);

		// Verify that all three downstreams have been hit
		verify(restTemplate).exchange(eq("http://PRIMARY_HOST/test?param1=test"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class), any(Map.class));
		verify(restTemplate).exchange(eq("http://SECONDARY_HOST/test?param1=test"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class), any(Map.class));
		verify(restTemplate).exchange(eq("http://CANDIDATE_HOST/test?param1=test"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class), any(Map.class));

		// Verify that observations are published to all publishers
		ArgumentCaptor<Observation> observationArgumentCaptor = ArgumentCaptor.forClass(Observation.class);
		verify(publisher1).publish(observationArgumentCaptor.capture());
		verify(publisher2).publish(any());

		// Verify that all observations are logged appropriately
		var capturedObservation = observationArgumentCaptor.getValue();
		Assert.assertEquals("/test", capturedObservation.getRequestUrl());
		Assert.assertEquals("RESPONSE", capturedObservation.getObservationType());
		Assert.assertEquals(primaryResponse.getBody(), capturedObservation.getPrimaryResult().getBody());
		Assert.assertEquals(secondaryResponse.getBody(), capturedObservation.getSecondaryResult().getBody());
		Assert.assertEquals(candidateResponse.getBody(), capturedObservation.getCandidateResult().getBody());

		// Verify that response returned is according to RETURN_RESPONSE_FROM variable
		Assert.assertEquals(primaryResponse, multicastResponse);
	}

	@Test
	public void verifyReturnFromPrimaryOnExceptionFromSecondary() throws InterruptedException {

		var httpServletRequest = getHttpServeletRequest();
		var headers = getHeaders();
		var params = getParams();

		var primaryResponse = new ResponseEntity<>("primaryResponse", new LinkedMultiValueMap<>(), HttpStatus.OK);

		when(httpServletRequest.getRequestURI()).thenReturn("/test");
		when(httpServletRequest.getMethod()).thenReturn(HttpMethod.POST.name());

		when(restTemplate.exchange(eq("http://PRIMARY_HOST/test?param1=test"), any(HttpMethod.class),
				any(HttpEntity.class), eq(String.class), anyMap()))
			.thenReturn(primaryResponse);
		when(restTemplate.exchange(eq("http://SECONDARY_HOST/test?param1=test"), any(HttpMethod.class),
				any(HttpEntity.class), eq(String.class), anyMap()))
			.thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));
		when(restTemplate.exchange(eq("http://CANDIDATE_HOST/test?param1=test"), any(HttpMethod.class),
				any(HttpEntity.class), eq(String.class), anyMap()))
			.thenThrow(new RuntimeException());

		ResponseEntity<?> multicastResponse = multicastProxyService.getResponse(httpServletRequest, headers, params,
				null);

		// Sleep to prevent test from being flaky if primary thread takes longer to
		// complete
		Thread.sleep(1000);

		// Verify that all three downstreams have been hit
		verify(restTemplate).exchange(eq("http://PRIMARY_HOST/test?param1=test"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class), any(Map.class));
		verify(restTemplate).exchange(eq("http://SECONDARY_HOST/test?param1=test"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class), any(Map.class));
		verify(restTemplate).exchange(eq("http://CANDIDATE_HOST/test?param1=test"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class), any(Map.class));

		// Verify that response returned is according to RETURN_RESPONSE_FROM variable
		Assert.assertEquals(primaryResponse, multicastResponse);
	}

	private HttpServletRequest getHttpServeletRequest() {
		return mock(HttpServletRequest.class);
	}

	private MultiValueMap<String, String> getHeaders() {
		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

		headers.put("Accept-Encoding", Collections.singletonList("test"));
		headers.put("Host", Collections.singletonList("test"));
		headers.put("ValidHeader", Collections.singletonList("test"));

		return headers;
	}

	private MultiValueMap<String, String> getParams() {
		var params = new LinkedMultiValueMap<String, String>();
		params.put("param1", Collections.singletonList("test"));
		return params;
	}

}