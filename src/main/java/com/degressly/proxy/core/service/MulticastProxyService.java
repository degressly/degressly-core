package com.degressly.proxy.core.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

public interface MulticastProxyService {

	ResponseEntity getResponse(HttpServletRequest httpServletRequest, MultiValueMap<String, String> headers,
			MultiValueMap<String, String> params, String body);

}
