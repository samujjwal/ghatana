package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from StackTraceAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that parses and analyzes stack traces for root cause identification output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StackTraceAnalyzerOutput(@NotNull String analysisId, @NotNull String rootCauseClass, @NotNull String rootCauseMethod, @NotNull List<String> callChain, @NotNull Map<String, Object> metadata) {
  public StackTraceAnalyzerOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (rootCauseClass == null || rootCauseClass.isEmpty()) {
      throw new IllegalArgumentException("rootCauseClass cannot be null or empty");
    }
    if (rootCauseMethod == null || rootCauseMethod.isEmpty()) {
      throw new IllegalArgumentException("rootCauseMethod cannot be null or empty");
    }
    if (callChain == null) {
      callChain = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
