package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReactExpertWorker agent.
 *
 * @doc.type record
 * @doc.purpose Stack expert worker agent for React component patterns and hooks input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReactExpertWorkerInput(@NotNull String codeContext, @NotNull String question, @NotNull Map<String, Object> projectMetadata) {
  public ReactExpertWorkerInput {
    if (codeContext == null || codeContext.isEmpty()) {
      throw new IllegalArgumentException("codeContext cannot be null or empty");
    }
    if (question == null || question.isEmpty()) {
      throw new IllegalArgumentException("question cannot be null or empty");
    }
    if (projectMetadata == null) {
      projectMetadata = Map.of();
    }
  }
}
