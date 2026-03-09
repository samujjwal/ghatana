package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ProductsOfficer agent.
 *
 * @doc.type record
 * @doc.purpose Strategic products officer managing portfolio vision and priorities output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProductsOfficerOutput(@NotNull String assessmentId, @NotNull List<String> priorities, @NotNull Map<String, Object> recommendations, @NotNull Map<String, Object> metadata) {
  public ProductsOfficerOutput {
    if (assessmentId == null || assessmentId.isEmpty()) {
      throw new IllegalArgumentException("assessmentId cannot be null or empty");
    }
    if (priorities == null) {
      priorities = List.of();
    }
    if (recommendations == null) {
      recommendations = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
