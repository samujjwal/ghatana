package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for Institutionalize agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that captures and institutionalizes learned patterns and practices input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record InstitutionalizeInput(@NotNull String patternId, @NotNull String description, @NotNull Map<String, Object> evidence) {
  public InstitutionalizeInput {
    if (patternId == null || patternId.isEmpty()) {
      throw new IllegalArgumentException("patternId cannot be null or empty");
    }
    if (description == null || description.isEmpty()) {
      throw new IllegalArgumentException("description cannot be null or empty");
    }
    if (evidence == null) {
      evidence = Map.of();
    }
  }
}
