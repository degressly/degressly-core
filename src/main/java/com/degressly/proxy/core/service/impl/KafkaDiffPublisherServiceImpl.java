package com.degressly.proxy.core.service.impl;

import com.degressly.proxy.core.dto.ResponsesDto;
import com.degressly.proxy.core.kafka.ProducerTemplate;
import com.degressly.proxy.core.service.DiffPublisherService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import static com.degressly.proxy.core.Constants.TRACE_ID;

@Service
@ConditionalOnProperty("diff.publisher.bootstrap-servers")
public class KafkaDiffPublisherServiceImpl implements DiffPublisherService {

	@Autowired
	ProducerTemplate kafkaTemplate;

	Logger logger = LoggerFactory.getLogger(KafkaDiffPublisherServiceImpl.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void publish(ResponsesDto result) {
		try {
			String payload = objectMapper.writeValueAsString(result);
            logger.info("Sending payload {}", payload);
			kafkaTemplate.sendMessage(payload);
		}
		catch (JsonProcessingException e) {
			logger.error("Error parsing object: {}", result, e);
		}

	}

}
