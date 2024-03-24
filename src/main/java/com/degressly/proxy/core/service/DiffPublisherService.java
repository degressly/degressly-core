package com.degressly.proxy.core.service;

import com.degressly.proxy.core.dto.DownstreamResult;
import com.degressly.proxy.core.dto.ResponsesDto;

public interface DiffPublisherService {

	void publish(ResponsesDto result);

}
