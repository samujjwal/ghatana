package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from RegressionTestWriter agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that generates regression tests for fixed bugs output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RegressionTestWriterOutput(@NotNull String testId, @NotNull String testCode, int testCount, @NotNull Map<String, Object> metadata) {
  public RegressionTestWriterOutput {
    if (testId == null || testId.isEmpty()) {
      throw new IllegalArgumentException("testId cannot be null or empty");
    }
    if (testCode == null || testCode.isEmpty()) {
      throw new IllegalArgumentException("testCode cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
