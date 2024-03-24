package com.degressly.proxy.core.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@Data
@Builder
public class ResponsesDto {

	String traceId;

	String requestUrl;

	DownstreamResult primaryResult;

	DownstreamResult secondaryResult;

	DownstreamResult candidateResult;

}
