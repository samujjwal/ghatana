package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DebugOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates debugging workflows using specialized debug micro-agents output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DebugOrchestratorOutput(@NotNull String debugSessionId, @NotNull String rootCause, @NotNull List<String> fixSuggestions, @NotNull Map<String, Object> metadata) {
  public DebugOrchestratorOutput {
    if (debugSessionId == null || debugSessionId.isEmpty()) {
      throw new IllegalArgumentException("debugSessionId cannot be null or empty");
    }
    if (rootCause == null || rootCause.isEmpty()) {
      throw new IllegalArgumentException("rootCause cannot be null or empty");
    }
    if (fixSuggestions == null) {
      fixSuggestions = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
