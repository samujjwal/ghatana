package com.ghatana.platform.ai.integration;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Test-scope compatibility shim for legacy launcher failure-injection tests.
 */
public interface LlmGateway {
    Promise<String> complete(String prompt, Map<String, Object> context);
}
