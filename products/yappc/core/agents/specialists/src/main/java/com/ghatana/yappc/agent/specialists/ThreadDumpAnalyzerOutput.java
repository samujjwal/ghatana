package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ThreadDumpAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes JVM thread dumps for deadlocks and contention output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ThreadDumpAnalyzerOutput(@NotNull String analysisId, @NotNull List<String> deadlocks, @NotNull List<String> contentionPoints, @NotNull Map<String, Object> metadata) {
  public ThreadDumpAnalyzerOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (deadlocks == null) {
      deadlocks = List.of();
    }
    if (contentionPoints == null) {
      contentionPoints = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
