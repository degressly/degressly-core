package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.ResponsesDto;
import com.degressly.proxy.core.service.DiffPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggerDiffPublisherServiceImpl implements DiffPublisherService {

	Logger logger = LoggerFactory.getLogger(LoggerDiffPublisherServiceImpl.class);

	@Override
	public void publish(ResponsesDto responsesDto) {
		DownstreamResult primaryResult = responsesDto.getPrimaryResult();
		DownstreamResult secondaryResult = responsesDto.getSecondaryResult();
		DownstreamResult candidateResult = responsesDto.getCandidateResult();
		logger.info("Primary Exception:", primaryResult.getException());
		logger.info("Primary Http Response: {}", primaryResult.getHttpResponse());
		logger.info("Secondary Exception:", secondaryResult.getException());
		logger.info("Secondary Http Response: {}", secondaryResult.getHttpResponse());
		logger.info("Candidate Exception:", candidateResult.getException());
		logger.info("Candidate Http Response: {}", candidateResult.getHttpResponse());
	}

}
