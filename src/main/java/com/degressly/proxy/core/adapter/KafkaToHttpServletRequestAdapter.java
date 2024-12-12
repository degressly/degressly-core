package com.degressly.proxy.core.adapter;

import com.degressly.proxy.core.dto.DegresslyRequest;
import com.degressly.proxy.core.dto.GeneratedHttpServletRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.net.URISyntaxException;

@UtilityClass
public class KafkaToHttpServletRequestAdapter {

	public static GeneratedHttpServletRequest convert(DegresslyRequest degresslyRequest)
			throws JsonProcessingException {
		GeneratedHttpServletRequest httpServletRequest = new GeneratedHttpServletRequest();

		if ("OUTGOING".equals(degresslyRequest.getType())) {
			httpServletRequest.setMethod(HttpMethod.POST.name());
		}
		else {
			httpServletRequest.setMethod(degresslyRequest.getMethod());
		}

		httpServletRequest.setRequestURI(getRequestURI(degresslyRequest.getUrl()));
		return httpServletRequest;

	}

	private static String getRequestURI(String url) {
		try {
			URI uri = new URI(url);

			return uri.getPath();
		}
		catch (URISyntaxException e) {
			return url;
		}
	}

}
