package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for UnitTestWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates unit tests for source code input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UnitTestWriterInput(@NotNull String sourceCode, @NotNull String className, @NotNull String framework, @NotNull Map<String, Object> context) {
  public UnitTestWriterInput {
    if (sourceCode == null || sourceCode.isEmpty()) {
      throw new IllegalArgumentException("sourceCode cannot be null or empty");
    }
    if (className == null || className.isEmpty()) {
      throw new IllegalArgumentException("className cannot be null or empty");
    }
    if (framework == null || framework.isEmpty()) {
      throw new IllegalArgumentException("framework cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
