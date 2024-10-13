package com.degressly.proxy.core.adapter;

import com.degressly.proxy.core.dto.DegresslyRequest;
import com.degressly.proxy.core.dto.GeneratedHttpServletRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KafkaToHttpServletRequestAdapter {

	public static GeneratedHttpServletRequest convert(DegresslyRequest degresslyRequest)
			throws JsonProcessingException {
		GeneratedHttpServletRequest httpServletRequest = new GeneratedHttpServletRequest();
		httpServletRequest.setMethod("POST");
		return httpServletRequest;

	}

}
