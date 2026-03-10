package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from CodeReviewer agent.
 *
 * @doc.type record
 * @doc.purpose Expert code reviewer for automated code quality and best practice enforcement output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CodeReviewerOutput(@NotNull String reviewId, @NotNull List<String> findings, @NotNull String verdict, @NotNull Map<String, Object> metadata) {
  public CodeReviewerOutput {
    if (reviewId == null || reviewId.isEmpty()) {
      throw new IllegalArgumentException("reviewId cannot be null or empty");
    }
    if (findings == null) {
      findings = List.of();
    }
    if (verdict == null || verdict.isEmpty()) {
      throw new IllegalArgumentException("verdict cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
