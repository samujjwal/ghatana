package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for FastifyExpert agent.
 *
 * @doc.type record
 * @doc.purpose Stack expert agent for Fastify/Node.js API patterns and best practices input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FastifyExpertInput(@NotNull String codeContext, @NotNull String question, @NotNull Map<String, Object> projectMetadata) {
  public FastifyExpertInput {
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
