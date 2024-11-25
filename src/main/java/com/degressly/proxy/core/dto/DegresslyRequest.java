package com.degressly.proxy.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DegresslyRequest {

	private String type;

	private String traceId;

	private String method;

	private String url;

	private int statusCode;

	private Map<String, List<String>> headers;

	private Map<String, List<String>> params;

	private String body;

	private Map<String, List<String>> responseHeaders;

	private String responseBody;

	private boolean throwException;

	private long responseTime;

}
