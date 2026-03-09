package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ReleaseGovernance agent.
 *
 * @doc.type record
 * @doc.purpose Governance agent that enforces release policies and approval workflows output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReleaseGovernanceOutput(@NotNull String governanceId, boolean approved, @NotNull List<String> conditions, @NotNull Map<String, Object> metadata) {
  public ReleaseGovernanceOutput {
    if (governanceId == null || governanceId.isEmpty()) {
      throw new IllegalArgumentException("governanceId cannot be null or empty");
    }
    if (conditions == null) {
      conditions = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
