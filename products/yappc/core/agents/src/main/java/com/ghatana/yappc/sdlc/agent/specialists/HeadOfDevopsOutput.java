package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from HeadOfDevops agent.
 *
 * @doc.type record
 * @doc.purpose Strategic head of DevOps overseeing infrastructure and delivery pipelines output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HeadOfDevopsOutput(@NotNull String strategyId, @NotNull String recommendation, @NotNull List<String> actionItems, @NotNull Map<String, Object> metadata) {
  public HeadOfDevopsOutput {
    if (strategyId == null || strategyId.isEmpty()) {
      throw new IllegalArgumentException("strategyId cannot be null or empty");
    }
    if (recommendation == null || recommendation.isEmpty()) {
      throw new IllegalArgumentException("recommendation cannot be null or empty");
    }
    if (actionItems == null) {
      actionItems = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
