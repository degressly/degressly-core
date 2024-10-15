package com.degressly.proxy.core.kafka;

import com.degressly.proxy.core.dto.DegresslyRequest;
import com.degressly.proxy.core.service.ReplayHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("replay.consumer.bootstrap-servers")
public class ReplayReceiver {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private final ReplayHandler replayHandler;

	@KafkaListener(topics = "${replay.consumer.topic-name}", groupId = "${replay.consumer.group-id:replay_default}")
	public void listen(String message) {

		try {
			log.info("Message received: {}", message);
			var degresslyRequest = objectMapper.readValue(message, DegresslyRequest.class);
			replayHandler.handle(degresslyRequest);
		}
		catch (Exception e) {
			// Do nothing
			log.error("Error occured when pushing to downstream: ", e);
		}
	}

}
