package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ContextGathering agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that gathers contextual information for decision making output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContextGatheringOutput(@NotNull String contextId, @NotNull Map<String, Object> gatheredContext, @NotNull List<String> sources, @NotNull Map<String, Object> metadata) {
  public ContextGatheringOutput {
    if (contextId == null || contextId.isEmpty()) {
      throw new IllegalArgumentException("contextId cannot be null or empty");
    }
    if (gatheredContext == null) {
      gatheredContext = Map.of();
    }
    if (sources == null) {
      sources = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
