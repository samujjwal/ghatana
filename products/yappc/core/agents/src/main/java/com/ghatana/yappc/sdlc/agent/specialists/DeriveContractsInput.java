package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for derive contracts specialist.
 *
 * @doc.type record
 * @doc.purpose API contract derivation input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeriveContractsInput(
    @NotNull String architectureId, @NotNull String interfaceRequirements) {
  public DeriveContractsInput {
    if (architectureId == null || architectureId.isEmpty()) {
      throw new IllegalArgumentException("architectureId cannot be null or empty");
    }
    if (interfaceRequirements == null) {
      interfaceRequirements = "";
    }
  }
}
