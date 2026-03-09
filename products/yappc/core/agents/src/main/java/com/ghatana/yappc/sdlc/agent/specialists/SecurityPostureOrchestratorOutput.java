package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from SecurityPostureOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates security posture assessment across the organization output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SecurityPostureOrchestratorOutput(@NotNull String assessmentId, @NotNull String postureScore, @NotNull List<String> gaps, @NotNull Map<String, Object> metadata) {
  public SecurityPostureOrchestratorOutput {
    if (assessmentId == null || assessmentId.isEmpty()) {
      throw new IllegalArgumentException("assessmentId cannot be null or empty");
    }
    if (postureScore == null || postureScore.isEmpty()) {
      throw new IllegalArgumentException("postureScore cannot be null or empty");
    }
    if (gaps == null) {
      gaps = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
