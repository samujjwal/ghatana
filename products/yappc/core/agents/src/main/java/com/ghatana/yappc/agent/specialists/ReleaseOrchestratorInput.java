package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReleaseOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Release pipeline orchestration request input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReleaseOrchestratorInput(
    @NotNull String releaseId,
    @NotNull String version,
    @NotNull String releaseType,
    @NotNull List<String> artifacts,
    @NotNull Map<String, Object> context) {

  public ReleaseOrchestratorInput {
    if (releaseId == null || releaseId.isEmpty()) {
      throw new IllegalArgumentException("releaseId cannot be null or empty");
    }
    if (version == null || version.isEmpty()) {
      throw new IllegalArgumentException("version cannot be null or empty");
    }
    if (releaseType == null || releaseType.isEmpty()) {
      releaseType = "standard";
    }
    if (artifacts == null) {
      artifacts = List.of();
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
