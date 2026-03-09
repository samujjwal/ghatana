package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for Reproduction agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that generates minimal reproduction steps for bugs input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReproductionInput(@NotNull String bugId, @NotNull String description, @NotNull Map<String, Object> environment) {
  public ReproductionInput {
    if (bugId == null || bugId.isEmpty()) {
      throw new IllegalArgumentException("bugId cannot be null or empty");
    }
    if (description == null || description.isEmpty()) {
      throw new IllegalArgumentException("description cannot be null or empty");
    }
    if (environment == null) {
      environment = Map.of();
    }
  }
}
