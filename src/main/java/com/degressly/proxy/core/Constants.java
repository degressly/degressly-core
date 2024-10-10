package com.degressly.proxy.core;

import java.util.Set;

public interface Constants {

	String TRACE_ID = "trace-id";

	Set<String> HEADERS_TO_SKIP = Set.of("Accept-Encoding", "accept-encoding", "connection", "accept", "Accept",
			"Connection", "content-length", "Content-Length", "transfer-encoding", "host", "Host", "Transfer-Encoding",
			"Keep-Alive", "keep-alive", "Trailer", "trailer", "Upgrade", "upgrade", "Proxy-Authorization",
			"proxy-authorization", "Proxy-Authenticate", "proxy-authenticate");

	enum REPLICA_TYPE {

		PRIMARY, SECONDARY, CANDIDATE

	}

	// Set<String> HEADERS_TO_SKIP = Set.of();

}
