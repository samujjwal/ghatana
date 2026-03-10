package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from validate architecture specialist.
 *
 * @doc.type record
 * @doc.purpose Architecture validation output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ValidateArchitectureOutput(
    @NotNull String validationId,
    @NotNull boolean isValid,
    @NotNull List<String> issues,
    @NotNull List<String> recommendations,
    @NotNull Map<String, Object> metrics,
    @NotNull Map<String, Object> metadata) {

  public ValidateArchitectureOutput {
    if (validationId == null || validationId.isEmpty()) {
      throw new IllegalArgumentException("validationId cannot be null or empty");
    }
    if (issues == null) {
      issues = List.of();
    }
    if (recommendations == null) {
      recommendations = List.of();
    }
    if (metrics == null) {
      metrics = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
