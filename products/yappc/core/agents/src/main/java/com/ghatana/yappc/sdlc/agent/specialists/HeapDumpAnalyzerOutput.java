package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from HeapDumpAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes JVM heap dumps for memory issues output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HeapDumpAnalyzerOutput(@NotNull String analysisId, @NotNull List<String> leakSuspects, @NotNull Map<String, Object> memoryProfile, @NotNull Map<String, Object> metadata) {
  public HeapDumpAnalyzerOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (leakSuspects == null) {
      leakSuspects = List.of();
    }
    if (memoryProfile == null) {
      memoryProfile = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
