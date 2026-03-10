package com.ghatana.yappc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ContextGathering agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that gathers contextual information for decision making input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContextGatheringInput(@NotNull String queryId, @NotNull String scope, @NotNull List<String> sources) {
  public ContextGatheringInput {
    if (queryId == null || queryId.isEmpty()) {
      throw new IllegalArgumentException("queryId cannot be null or empty");
    }
    if (scope == null || scope.isEmpty()) {
      throw new IllegalArgumentException("scope cannot be null or empty");
    }
    if (sources == null) {
      sources = List.of();
    }
  }
}
