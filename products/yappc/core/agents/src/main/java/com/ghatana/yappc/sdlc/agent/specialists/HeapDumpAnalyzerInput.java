package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for HeapDumpAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes JVM heap dumps for memory issues input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HeapDumpAnalyzerInput(@NotNull String dumpPath, @NotNull String dumpFormat, @NotNull Map<String, Object> analysisConfig) {
  public HeapDumpAnalyzerInput {
    if (dumpPath == null || dumpPath.isEmpty()) {
      throw new IllegalArgumentException("dumpPath cannot be null or empty");
    }
    if (dumpFormat == null || dumpFormat.isEmpty()) {
      throw new IllegalArgumentException("dumpFormat cannot be null or empty");
    }
    if (analysisConfig == null) {
      analysisConfig = Map.of();
    }
  }
}
