package com.degressly.proxy.core.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@Data
public class DownstreamResult {

	private Map<String, List<String>> headers;

	private String statusCode;

	private String body;

	private String exception;

}
