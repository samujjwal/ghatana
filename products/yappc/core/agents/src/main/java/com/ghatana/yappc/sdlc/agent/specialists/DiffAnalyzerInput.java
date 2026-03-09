package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DiffAnalyzer agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes code diffs to identify regression risks input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DiffAnalyzerInput(@NotNull String commitId, @NotNull String diffContent, @NotNull Map<String, Object> context) {
  public DiffAnalyzerInput {
    if (commitId == null || commitId.isEmpty()) {
      throw new IllegalArgumentException("commitId cannot be null or empty");
    }
    if (diffContent == null || diffContent.isEmpty()) {
      throw new IllegalArgumentException("diffContent cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
