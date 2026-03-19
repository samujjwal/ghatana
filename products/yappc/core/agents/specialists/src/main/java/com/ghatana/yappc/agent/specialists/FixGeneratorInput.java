package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for FixGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that generates code fixes for identified bugs input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FixGeneratorInput(@NotNull String bugId, @NotNull String rootCause, @NotNull String affectedCode, @NotNull Map<String, Object> context) {
  public FixGeneratorInput {
    if (bugId == null || bugId.isEmpty()) {
      throw new IllegalArgumentException("bugId cannot be null or empty");
    }
    if (rootCause == null || rootCause.isEmpty()) {
      throw new IllegalArgumentException("rootCause cannot be null or empty");
    }
    if (affectedCode == null || affectedCode.isEmpty()) {
      throw new IllegalArgumentException("affectedCode cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
