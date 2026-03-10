package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from intake specialist.
 *
 * @doc.type record
 * @doc.purpose Structured requirements output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntakeOutput(
    @NotNull List<String> functionalRequirements,
    @NotNull List<String> nonFunctionalRequirements,
    @NotNull Map<String, String> constraints,
    @NotNull Map<String, Object> metadata) {

  public IntakeOutput {
    if (functionalRequirements == null) {
      functionalRequirements = List.of();
    }
    if (nonFunctionalRequirements == null) {
      nonFunctionalRequirements = List.of();
    }
    if (constraints == null) {
      constraints = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
