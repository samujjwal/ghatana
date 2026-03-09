package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DiffAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes code diffs to identify regression risks output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DiffAnalyzerOutput(@NotNull String analysisId, @NotNull List<String> riskAreas, @NotNull List<String> impactedTests, @NotNull Map<String, Object> metadata) {
  public DiffAnalyzerOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (riskAreas == null) {
      riskAreas = List.of();
    }
    if (impactedTests == null) {
      impactedTests = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
