/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai;

import com.ghatana.ai.AIIntegrationService;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter that bridges {@link LLMGateway} to the {@link AIIntegrationService} interface
 * required by {@code CodeGenerationService} and {@code TestGenerationService}.
 *
 * <p>Uses the multi-provider gateway (with cost enforcement, routing, and fallback)
 * as the underlying engine. The synchronous {@link #generateCode(String)} method blocks
 * the calling thread and must only be called from within a {@code Promise.ofBlocking}
 * context (as both consumer services already do).
 *
 * <p><b>Calling pattern</b>
 * <pre>{@code
 * // Already inside Promise.ofBlocking — blocking is safe:
 * String code = aiService.generateCode(prompt);
 * }</pre>
 *
 * @see com.ghatana.yappc.api.codegen.CodeGenerationService
 * @see com.ghatana.yappc.api.testing.TestGenerationService
 * @doc.type class
 * @doc.purpose Adapts LLMGateway to AIIntegrationService for codegen and testgen
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class LLMGatewayAIIntegrationService implements AIIntegrationService {

  private static final Logger logger =
      LoggerFactory.getLogger(LLMGatewayAIIntegrationService.class);

  /** Conservative token limit for code-generation prompts. */
  private static final int CODE_GEN_MAX_TOKENS = 4096;

  private final LLMGateway gateway;

  /**
   * Create the adapter.
   *
   * @param gateway the multi-provider LLM gateway (non-null)
   */
  public LLMGatewayAIIntegrationService(LLMGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    logger.info("LLMGatewayAIIntegrationService created — backed by {}",
        gateway.getClass().getSimpleName());
  }

  /**
   * Synchronously generate code from the given prompt.
   *
   * <p><b>MUST</b> be called from a blocking executor context (e.g., inside
   * {@code Promise.ofBlocking(executor, () -> aiService.generateCode(prompt))}).
   *
   * @param prompt the code-generation prompt
   * @return the generated code text, or an error message on failure
   */
  @Override
  public String generateCode(String prompt) {
    Objects.requireNonNull(prompt, "prompt cannot be null");
    try {
      CompletionRequest request = CompletionRequest.builder()
          .messages(List.of(
              ChatMessage.system(
                  "You are an expert Java/TypeScript code generator. "
                  + "Generate production-quality, well-documented code. "
                  + "When generating multiple files, separate them with '// FILE: <filename>' markers."),
              ChatMessage.user(prompt)
          ))
          .maxTokens(CODE_GEN_MAX_TOKENS)
          .temperature(0.2) // low temperature for deterministic code generation
          .build();

      return gateway.complete(request)
          .toCompletableFuture()
          .get()
          .getText();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Code generation interrupted", e);
      return "// ERROR: Code generation was interrupted";
    } catch (Exception e) {
      logger.error("Code generation failed: {}", e.getMessage(), e);
      return "// ERROR: Code generation failed — " + e.getMessage();
    }
  }

  /**
   * Asynchronously complete the given prompt.
   *
   * @param prompt the prompt to complete
   * @return Promise resolving to the completion text
   */
  @Override
  public Promise<String> complete(String prompt) {
    Objects.requireNonNull(prompt, "prompt cannot be null");
    CompletionRequest request = CompletionRequest.builder()
        .messages(List.of(ChatMessage.user(prompt)))
        .maxTokens(CODE_GEN_MAX_TOKENS)
        .temperature(0.3)
        .build();

    return gateway.complete(request).map(result -> result.getText());
  }
}
