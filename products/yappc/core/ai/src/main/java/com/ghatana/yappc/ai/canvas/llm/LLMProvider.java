package com.ghatana.yappc.ai.canvas.llm;

import io.activej.promise.Promise;

/**
 * Provider interface for LLM operations within YAPPC canvas.
 *
 * <p>Abstracts LLM access for canvas-specific use cases such as
 * requirement extraction, code generation, and document analysis.
 *
 * @doc.type interface
 * @doc.purpose LLM provider abstraction for canvas AI features
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface LLMProvider {

    /**
     * Generate a completion from the LLM.
     *
     * @param request the LLM request
     * @return promise completing with the LLM response
     */
    Promise<LLMResponse> generate(LLMRequest request);
}
