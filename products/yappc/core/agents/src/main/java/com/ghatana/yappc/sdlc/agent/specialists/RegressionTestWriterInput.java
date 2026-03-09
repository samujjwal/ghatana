package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for RegressionTestWriter agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that generates regression tests for fixed bugs input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RegressionTestWriterInput(@NotNull String bugId, @NotNull String fixCode, @NotNull String framework, @NotNull Map<String, Object> context) {
  public RegressionTestWriterInput {
    if (bugId == null || bugId.isEmpty()) {
      throw new IllegalArgumentException("bugId cannot be null or empty");
    }
    if (fixCode == null || fixCode.isEmpty()) {
      throw new IllegalArgumentException("fixCode cannot be null or empty");
    }
    if (framework == null || framework.isEmpty()) {
      throw new IllegalArgumentException("framework cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
