package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ProjectOnboarding agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that automates project onboarding and setup output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProjectOnboardingOutput(@NotNull String projectId, @NotNull List<String> createdResources, @NotNull String setupStatus, @NotNull Map<String, Object> metadata) {
  public ProjectOnboardingOutput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (createdResources == null) {
      createdResources = List.of();
    }
    if (setupStatus == null || setupStatus.isEmpty()) {
      throw new IllegalArgumentException("setupStatus cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
