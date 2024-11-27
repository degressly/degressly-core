package com.degressly.proxy.core.http;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.degressly.proxy.core.Constants.HEADERS_TO_SKIP;

@Service
@Slf4j
@RequiredArgsConstructor
public class HttpClient {

	@Value("${primary.host}")
	private String PRIMARY_HOST;

	@Value("${secondary.host}")
	private String SECONDARY_HOST;

	@Value("${candidate.host}")
	private String CANDIDATE_HOST;

	private final RestTemplate restTemplate = new RestTemplate();

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
	private long WAIT_AFTER_FORWARDING_TO_PRIMARY;

	public ResponseEntity getResponse(String traceId, String host, HttpServletRequest httpServletRequest,
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

			log.info("Response for for url {}: Status: {}, Headers: {}, Body: {}", finalUrl, "200",
					response.getHeaders(), response.getBody());

		}
		catch (RestClientResponseException e) {
			log.info("Response for for url {}: Status: {} Headers: {}, Body: {}", finalUrl, e.getStatusCode(),
					e.getResponseHeaders(), e.getResponseBodyAsString());
			return new ResponseEntity(e.getResponseBodyAsString(), skipRestrictedHeaders(e.getResponseHeaders()),
					HttpStatus.valueOf(e.getStatusCode().value()));
		}
		catch (Exception e) {
			log.info("Exception when calling downstream {}", e.getClass().getCanonicalName(), e);
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

		return urlTemplate.build().toUriString();
	}

	@SneakyThrows
	private void waitIfApplicable(String host) {
		if (PRIMARY_HOST.equals(host) && WAIT_AFTER_FORWARDING_TO_PRIMARY < 0) {
			Thread.sleep(Math.abs(WAIT_AFTER_FORWARDING_TO_PRIMARY));
			return;
		}

		if (WAIT_AFTER_FORWARDING_TO_PRIMARY > 0 && (SECONDARY_HOST.equals(host) || CANDIDATE_HOST.equals(host))) {
			Thread.sleep(WAIT_AFTER_FORWARDING_TO_PRIMARY);
		}
	}

}
