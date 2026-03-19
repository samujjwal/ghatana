package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from SystemsArchitect agent.
 *
 * @doc.type record
 * @doc.purpose Strategic systems architect for cross-cutting technical decisions output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SystemsArchitectOutput(@NotNull String decisionId, @NotNull String architectureDecision, @NotNull List<String> tradeoffs, @NotNull Map<String, Object> metadata) {
  public SystemsArchitectOutput {
    if (decisionId == null || decisionId.isEmpty()) {
      throw new IllegalArgumentException("decisionId cannot be null or empty");
    }
    if (architectureDecision == null || architectureDecision.isEmpty()) {
      throw new IllegalArgumentException("architectureDecision cannot be null or empty");
    }
    if (tradeoffs == null) {
      tradeoffs = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
