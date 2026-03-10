package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for SystemsArchitect agent.
 *
 * @doc.type record
 * @doc.purpose Strategic systems architect for cross-cutting technical decisions input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SystemsArchitectInput(@NotNull String systemId, @NotNull String designChallenge, @NotNull Map<String, Object> context) {
  public SystemsArchitectInput {
    if (systemId == null || systemId.isEmpty()) {
      throw new IllegalArgumentException("systemId cannot be null or empty");
    }
    if (designChallenge == null || designChallenge.isEmpty()) {
      throw new IllegalArgumentException("designChallenge cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
