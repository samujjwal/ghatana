package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReactExpert agent.
 *
 * @doc.type record
 * @doc.purpose Expert React/TypeScript engineer for frontend architecture and patterns input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReactExpertInput(@NotNull String componentContext, @NotNull String question, @NotNull Map<String, Object> projectMetadata) {
  public ReactExpertInput {
    if (componentContext == null || componentContext.isEmpty()) {
      throw new IllegalArgumentException("componentContext cannot be null or empty");
    }
    if (question == null || question.isEmpty()) {
      throw new IllegalArgumentException("question cannot be null or empty");
    }
    if (projectMetadata == null) {
      projectMetadata = Map.of();
    }
  }
}
