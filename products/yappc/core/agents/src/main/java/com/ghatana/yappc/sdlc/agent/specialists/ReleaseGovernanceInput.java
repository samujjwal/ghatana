package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReleaseGovernance agent.
 *
 * @doc.type record
 * @doc.purpose Governance agent that enforces release policies and approval workflows input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReleaseGovernanceInput(@NotNull String releaseId, @NotNull Map<String, Object> releaseManifest, @NotNull Map<String, Object> policies) {
  public ReleaseGovernanceInput {
    if (releaseId == null || releaseId.isEmpty()) {
      throw new IllegalArgumentException("releaseId cannot be null or empty");
    }
    if (releaseManifest == null) {
      releaseManifest = Map.of();
    }
    if (policies == null) {
      policies = Map.of();
    }
  }
}
