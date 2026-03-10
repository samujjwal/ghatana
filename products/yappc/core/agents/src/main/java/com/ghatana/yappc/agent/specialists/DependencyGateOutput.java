package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DependencyGate agent.
 *
 * @doc.type record
 * @doc.purpose Governance agent that enforces dependency policies and license compliance output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DependencyGateOutput(@NotNull String gateId, boolean passed, @NotNull List<String> violations, @NotNull Map<String, Object> metadata) {
  public DependencyGateOutput {
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
