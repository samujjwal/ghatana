package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for StackTraceAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that parses and analyzes stack traces for root cause identification input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StackTraceAnalyzerInput(@NotNull String stackTrace, @NotNull String language, @NotNull Map<String, Object> context) {
  public StackTraceAnalyzerInput {
    if (stackTrace == null || stackTrace.isEmpty()) {
      throw new IllegalArgumentException("stackTrace cannot be null or empty");
    }
    if (language == null || language.isEmpty()) {
      throw new IllegalArgumentException("language cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
