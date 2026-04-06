package com.ghatana.yappc.agents.testing;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * @doc.type record
 * @doc.purpose Represents a human-readable test scenario produced before test code generation
 * @doc.layer product
 * @doc.pattern DTO
 */
public record TestScenario(
    @NotNull String title,
    @NotNull ScenarioCategory category,
    @NotNull String givenClause,
    @NotNull String whenClause,
    @NotNull String thenClause,
    @NotNull List<String> coverageTargets) {

  public TestScenario {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title cannot be blank");
    }
    if (category == null) {
      throw new IllegalArgumentException("category cannot be null");
    }
    if (givenClause == null || givenClause.isBlank()) {
      throw new IllegalArgumentException("givenClause cannot be blank");
    }
    if (whenClause == null || whenClause.isBlank()) {
      throw new IllegalArgumentException("whenClause cannot be blank");
    }
    if (thenClause == null || thenClause.isBlank()) {
      throw new IllegalArgumentException("thenClause cannot be blank");
    }
    coverageTargets = coverageTargets == null ? List.of() : List.copyOf(coverageTargets);
  }

  public enum ScenarioCategory {
    HAPPY_PATH,
    EDGE_CASE,
    BOUNDARY_VALUE
  }
}