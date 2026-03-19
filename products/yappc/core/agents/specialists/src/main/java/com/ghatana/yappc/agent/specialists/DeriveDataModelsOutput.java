package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from derive data models specialist.
 *
 * @doc.type record
 * @doc.purpose Data model derivation output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeriveDataModelsOutput(
    @NotNull String modelId,
    @NotNull List<String> entities,
    @NotNull Map<String, List<String>> relationships,
    @NotNull Map<String, String> storageStrategies,
    @NotNull Map<String, Object> metadata) {

  public DeriveDataModelsOutput {
    if (modelId == null || modelId.isEmpty()) {
      throw new IllegalArgumentException("modelId cannot be null or empty");
    }
    if (entities == null) {
      entities = List.of();
    }
    if (relationships == null) {
      relationships = Map.of();
    }
    if (storageStrategies == null) {
      storageStrategies = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
