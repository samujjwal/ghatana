package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from NetworkTrace agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes network traces for latency and connectivity issues output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record NetworkTraceOutput(@NotNull String analysisId, @NotNull List<String> latencyIssues, @NotNull List<String> errors, @NotNull Map<String, Object> metadata) {
  public NetworkTraceOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (latencyIssues == null) {
      latencyIssues = List.of();
    }
    if (errors == null) {
      errors = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
