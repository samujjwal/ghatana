package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from UnitTestWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates unit tests for source code output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UnitTestWriterOutput(@NotNull String testCode, int testCount, @NotNull List<String> testMethods, @NotNull Map<String, Object> metadata) {
  public UnitTestWriterOutput {
    if (testCode == null || testCode.isEmpty()) {
      throw new IllegalArgumentException("testCode cannot be null or empty");
    }
    if (testMethods == null) {
      testMethods = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
