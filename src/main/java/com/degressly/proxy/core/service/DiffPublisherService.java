package com.degressly.proxy.core.service;

import com.degressly.proxy.core.dto.Observation;

public interface DiffPublisherService {

	void publish(Observation result);

}
