/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.mcp;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Minimal HTTP transport contract for MCP tool calls.
 *
 * <p>Abstracted so that production code can use the platform OkHttpAdapter and test
 * code can use a WireMock-backed stub without requiring the full HTTP platform dependency
 * inside the tool-runtime module.
 *
 * @doc.type interface
 * @doc.purpose HTTP transport boundary for MCP tool adapter
 * @doc.layer platform
 * @doc.pattern SPI
 */
@FunctionalInterface
public interface McpHttpTransport {

    /**
     * Perform a POST request with a JSON body and optional headers.
     *
     * @param url      the endpoint URL
     * @param jsonBody the JSON request payload
     * @param headers  additional HTTP headers; may be empty
     * @return promise resolving to the response body string
     */
    Promise<String> post(String url, String jsonBody, Map<String, String> headers);
}
