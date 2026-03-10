package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ReleaseGate agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that enforces release quality gates output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReleaseGateOutput(@NotNull String gateId, boolean passed, @NotNull List<String> violations, @NotNull Map<String, Object> metadata) {
  public ReleaseGateOutput {
    if (gateId == null || gateId.isEmpty()) {
      throw new IllegalArgumentException("gateId cannot be null or empty");
    }
    if (violations == null) {
      violations = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
