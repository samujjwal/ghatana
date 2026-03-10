package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from implement specialist.
 *
 * @doc.type record
 * @doc.purpose Code implementation output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ImplementOutput(
    @NotNull String implementationId,
    @NotNull String unitName,
    @NotNull List<String> implementedFiles,
    @NotNull Map<String, Integer> metrics,
    @NotNull Map<String, Object> metadata) {

  public ImplementOutput {
    if (implementationId == null || implementationId.isEmpty()) {
      throw new IllegalArgumentException("implementationId cannot be null or empty");
    }
    if (unitName == null || unitName.isEmpty()) {
      throw new IllegalArgumentException("unitName cannot be null or empty");
    }
    if (implementedFiles == null) {
      implementedFiles = List.of();
    }
    if (metrics == null) {
      metrics = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
