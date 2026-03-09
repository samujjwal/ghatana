package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from Reproduction agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that generates minimal reproduction steps for bugs output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReproductionOutput(@NotNull String reproId, @NotNull List<String> steps, @NotNull String reproCode, @NotNull Map<String, Object> metadata) {
  public ReproductionOutput {
    if (reproId == null || reproId.isEmpty()) {
      throw new IllegalArgumentException("reproId cannot be null or empty");
    }
    if (steps == null) {
      steps = List.of();
    }
    if (reproCode == null || reproCode.isEmpty()) {
      throw new IllegalArgumentException("reproCode cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
