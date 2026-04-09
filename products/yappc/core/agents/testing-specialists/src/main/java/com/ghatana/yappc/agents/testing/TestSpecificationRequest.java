package com.ghatana.yappc.agents.testing;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * @doc.type record
 * @doc.purpose Captures the class-under-test context and requirements for scenario generation
 * @doc.layer product
 * @doc.pattern DTO
 */
public record TestSpecificationRequest(
    @NotNull String className,
    @NotNull String classSource,
    @NotNull List<String> requirements) {

  public TestSpecificationRequest {
    if (className == null || className.isBlank()) {
      throw new IllegalArgumentException("className cannot be blank");
    }
    if (classSource == null || classSource.isBlank()) {
      throw new IllegalArgumentException("classSource cannot be blank");
    }
    requirements = requirements == null ? List.of() : List.copyOf(requirements);
  }
}
