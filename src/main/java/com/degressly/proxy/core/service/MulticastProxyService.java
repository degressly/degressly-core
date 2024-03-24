package com.degressly.proxy.core.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.util.Map;

public interface MulticastProxyService {

	public ResponseEntity getResponseFromPrimary(HttpServletRequest httpServletRequest,
			MultiValueMap<String, String> headers, MultiValueMap<String, String> params, String body);

}
