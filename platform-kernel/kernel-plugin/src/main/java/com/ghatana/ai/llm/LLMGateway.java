package com.ghatana.ai.llm;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Minimal LLM gateway contract required by extracted kernel plugin AI SPI.
 * @doc.layer platform
 * @doc.pattern Gateway
 */
public interface LLMGateway {

    Promise<CompletionResult> complete(CompletionRequest request);
}
