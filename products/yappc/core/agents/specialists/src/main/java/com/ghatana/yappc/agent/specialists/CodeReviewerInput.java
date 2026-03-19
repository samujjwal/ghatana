package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for CodeReviewer agent.
 *
 * @doc.type record
 * @doc.purpose Expert code reviewer for automated code quality and best practice enforcement input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CodeReviewerInput(@NotNull String pullRequestId, @NotNull String diffContent, @NotNull Map<String, Object> reviewCriteria) {
  public CodeReviewerInput {
    if (pullRequestId == null || pullRequestId.isEmpty()) {
      throw new IllegalArgumentException("pullRequestId cannot be null or empty");
    }
    if (diffContent == null || diffContent.isEmpty()) {
      throw new IllegalArgumentException("diffContent cannot be null or empty");
    }
    if (reviewCriteria == null) {
      reviewCriteria = Map.of();
    }
  }
}
