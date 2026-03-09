package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ThreadDumpAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes JVM thread dumps for deadlocks and contention input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ThreadDumpAnalyzerInput(@NotNull String dumpContent, @NotNull Map<String, Object> analysisConfig) {
  public ThreadDumpAnalyzerInput {
    if (dumpContent == null || dumpContent.isEmpty()) {
      throw new IllegalArgumentException("dumpContent cannot be null or empty");
    }
    if (analysisConfig == null) {
      analysisConfig = Map.of();
    }
  }
}
