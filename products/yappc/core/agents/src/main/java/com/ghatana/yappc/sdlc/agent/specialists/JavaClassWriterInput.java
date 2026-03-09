package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for JavaClassWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates Java classes from specifications input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record JavaClassWriterInput(@NotNull String className, @NotNull String packageName, @NotNull String specification, @NotNull Map<String, Object> context) {
  public JavaClassWriterInput {
    if (className == null || className.isEmpty()) {
      throw new IllegalArgumentException("className cannot be null or empty");
    }
    if (packageName == null || packageName.isEmpty()) {
      throw new IllegalArgumentException("packageName cannot be null or empty");
    }
    if (specification == null || specification.isEmpty()) {
      throw new IllegalArgumentException("specification cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
