package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ProjectOnboarding agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that automates project onboarding and setup input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProjectOnboardingInput(@NotNull String projectName, @NotNull String template, @NotNull Map<String, Object> config) {
  public ProjectOnboardingInput {
    if (projectName == null || projectName.isEmpty()) {
      throw new IllegalArgumentException("projectName cannot be null or empty");
    }
    if (template == null || template.isEmpty()) {
      throw new IllegalArgumentException("template cannot be null or empty");
    }
    if (config == null) {
      config = Map.of();
    }
  }
}
