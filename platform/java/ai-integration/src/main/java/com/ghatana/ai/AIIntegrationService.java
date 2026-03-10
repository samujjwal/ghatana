/*
 * Copyright (c) 2026 Ghatana Technologies
 * Platform AI Integration Module
 */
package com.ghatana.ai;

import io.activej.promise.Promise;

/**
 * High-level AI service interface for code and text completion tasks.
 *
 * <p>Products that need AI capabilities should depend on this interface rather than
 * talking directly to {@link com.ghatana.ai.llm.LLMGateway} or any provider SDK.
 * The concrete implementation is supplied by the product's DI module and can be
 * backed by any combination of LLM providers.
 *
 * <p>Usage example:
 * <pre>{@code
 * // Synchronous (must run on a blocking executor):
 * String code = aiService.generateCode(prompt);
 *
 * // Asynchronous (ActiveJ event-loop safe):
 * return aiService.complete(prompt).map(text -> buildResponse(text));
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose High-level AI service for code generation and text completion
 * @doc.layer core
 * @doc.pattern Service
 */
public interface AIIntegrationService {

  /**
   * Synchronously generate code from the given prompt.
   *
   * <p><b>IMPORTANT:</b> Implementations of this method may block the calling thread.
   * Always invoke from within a {@code Promise.ofBlocking(executor, ...)} context to
   * avoid blocking the ActiveJ event loop.
   *
   * @param prompt the code-generation prompt (non-null)
   * @return the generated code text; may be an error message string on failure
   */
  String generateCode(String prompt);

  /**
   * Asynchronously complete the given prompt.
   *
   * <p>This method is event-loop safe — it returns a {@link Promise} and must not
   * block internally.
   *
   * @param prompt the text prompt (non-null)
   * @return Promise resolving to the completion text
   */
  Promise<String> complete(String prompt);
}
