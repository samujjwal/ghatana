package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for derive data models specialist.
 *
 * @doc.type record
 * @doc.purpose Data model derivation input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeriveDataModelsInput(@NotNull String architectureId, @NotNull String domainModel) {
  public DeriveDataModelsInput {
    if (architectureId == null || architectureId.isEmpty()) {
      throw new IllegalArgumentException("architectureId cannot be null or empty");
    }
    if (domainModel == null) {
      domainModel = "";
    }
  }
}
