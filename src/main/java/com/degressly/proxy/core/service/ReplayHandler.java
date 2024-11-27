package com.degressly.proxy.core.service;

import com.degressly.proxy.core.dto.DegresslyRequest;

public interface ReplayHandler {

	void handle(DegresslyRequest degresslyRequest) throws Exception;

}
