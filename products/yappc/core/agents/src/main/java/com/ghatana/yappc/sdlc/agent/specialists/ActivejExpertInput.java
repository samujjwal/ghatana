package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ActivejExpert agent.
 *
 * @doc.type record
 * @doc.purpose Stack expert agent for ActiveJ framework patterns and best practices input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ActivejExpertInput(@NotNull String codeContext, @NotNull String question, @NotNull Map<String, Object> projectMetadata) {
  public ActivejExpertInput {
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
