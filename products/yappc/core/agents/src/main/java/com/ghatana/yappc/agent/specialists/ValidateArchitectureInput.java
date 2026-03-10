package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for validate architecture specialist.
 *
 * @doc.type record
 * @doc.purpose Architecture validation input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ValidateArchitectureInput(
    @NotNull String architectureId, @NotNull String contractId, @NotNull String modelId) {
  public ValidateArchitectureInput {
    if (architectureId == null || architectureId.isEmpty()) {
      throw new IllegalArgumentException("architectureId cannot be null or empty");
    }
    if (contractId == null) {
      contractId = "";
    }
    if (modelId == null) {
      modelId = "";
    }
  }
}
