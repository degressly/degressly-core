package com.degressly.proxy.core.controller;

import com.degressly.proxy.core.service.MulticastService;
import com.degressly.proxy.core.service.impl.HttpProxyMulticastServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.degressly.proxy.core.Constants.TRACE_ID;

@Controller
@RequiredArgsConstructor
public class ProxyController {

	private final HttpProxyMulticastServiceImpl multicastService;

	@RequestMapping(value = "/**", method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD,
			RequestMethod.OPTIONS, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.TRACE })
	public ResponseEntity proxy(final HttpServletRequest request,
			final @RequestHeader MultiValueMap<String, String> headers,
			final @RequestParam MultiValueMap<String, String> params,
			final @RequestBody(required = false) String body) {

		if (headers.containsKey(TRACE_ID)) {
			MDC.put(TRACE_ID, headers.get(TRACE_ID).getFirst());
		}
		else {
			MDC.put(TRACE_ID, UUID.randomUUID().toString());
		}

		ResponseEntity resp = multicastService.getResponse(request, headers, params, body);
		return resp;
	}

}
