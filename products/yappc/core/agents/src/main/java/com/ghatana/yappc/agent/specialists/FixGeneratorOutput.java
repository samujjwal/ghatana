package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from FixGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that generates code fixes for identified bugs output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FixGeneratorOutput(@NotNull String fixId, @NotNull String patchCode, @NotNull String explanation, @NotNull Map<String, Object> metadata) {
  public FixGeneratorOutput {
    if (fixId == null || fixId.isEmpty()) {
      throw new IllegalArgumentException("fixId cannot be null or empty");
    }
    if (patchCode == null || patchCode.isEmpty()) {
      throw new IllegalArgumentException("patchCode cannot be null or empty");
    }
    if (explanation == null || explanation.isEmpty()) {
      throw new IllegalArgumentException("explanation cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
