package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from PrismaExpert agent.
 *
 * @doc.type record
 * @doc.purpose Stack expert agent for Prisma ORM patterns and database modeling output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PrismaExpertOutput(@NotNull String adviceId, @NotNull String recommendation, @NotNull List<String> codeExamples, @NotNull Map<String, Object> metadata) {
  public PrismaExpertOutput {
    if (adviceId == null || adviceId.isEmpty()) {
      throw new IllegalArgumentException("adviceId cannot be null or empty");
    }
    if (recommendation == null || recommendation.isEmpty()) {
      throw new IllegalArgumentException("recommendation cannot be null or empty");
    }
    if (codeExamples == null) {
      codeExamples = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
