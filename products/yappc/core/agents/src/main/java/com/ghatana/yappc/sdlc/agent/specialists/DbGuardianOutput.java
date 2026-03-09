package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DbGuardian agent.
 *
 * @doc.type record
 * @doc.purpose Expert database guardian for schema design, migration and query optimization output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DbGuardianOutput(@NotNull String reviewId, @NotNull String assessment, @NotNull List<String> recommendations, @NotNull Map<String, Object> metadata) {
  public DbGuardianOutput {
    if (reviewId == null || reviewId.isEmpty()) {
      throw new IllegalArgumentException("reviewId cannot be null or empty");
    }
    if (assessment == null || assessment.isEmpty()) {
      throw new IllegalArgumentException("assessment cannot be null or empty");
    }
    if (recommendations == null) {
      recommendations = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
