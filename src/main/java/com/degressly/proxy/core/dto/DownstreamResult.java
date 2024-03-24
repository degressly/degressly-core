package com.degressly.proxy.core.dto;

import lombok.Data;
import org.springframework.http.ResponseEntity;

@Data
public class DownstreamResult {

	private ResponseEntity httpResponse;

	private Exception exception;

}
