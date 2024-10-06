package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.Observation;
import com.degressly.proxy.core.service.ObservationPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggerObservationPublisherServiceImpl implements ObservationPublisherService {

	Logger logger = LoggerFactory.getLogger(LoggerObservationPublisherServiceImpl.class);

	@Override
	public void publish(Observation observation) {
		DownstreamResult primaryResult = observation.getPrimaryResult();
		DownstreamResult secondaryResult = observation.getSecondaryResult();
		DownstreamResult candidateResult = observation.getCandidateResult();
		logger.info("Primary Exception:", primaryResult.getException());
		logger.info("Primary Http Response: {}", primaryResult.getBody());
		logger.info("Secondary Exception:", secondaryResult.getException());
		logger.info("Secondary Http Response: {}", secondaryResult.getBody());
		logger.info("Candidate Exception:", candidateResult.getException());
		logger.info("Candidate Http Response: {}", candidateResult.getBody());
	}

}
