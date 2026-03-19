package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for IssueTrackerIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for issue tracking systems input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IssueTrackerIntegrationInput(@NotNull String trackerId, @NotNull String operation, @NotNull Map<String, Object> issueData) {
  public IssueTrackerIntegrationInput {
    if (trackerId == null || trackerId.isEmpty()) {
      throw new IllegalArgumentException("trackerId cannot be null or empty");
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("operation cannot be null or empty");
    }
    if (issueData == null) {
      issueData = Map.of();
    }
  }
}
