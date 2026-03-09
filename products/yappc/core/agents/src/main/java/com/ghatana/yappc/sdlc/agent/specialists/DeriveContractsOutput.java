package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from derive contracts specialist.
 *
 * @doc.type record
 * @doc.purpose API contract derivation output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeriveContractsOutput(
    @NotNull String contractId,
    @NotNull List<String> apiEndpoints,
    @NotNull List<String> eventSchemas,
    @NotNull Map<String, String> protocols,
    @NotNull Map<String, Object> metadata) {

  public DeriveContractsOutput {
    if (contractId == null || contractId.isEmpty()) {
      throw new IllegalArgumentException("contractId cannot be null or empty");
    }
    if (apiEndpoints == null) {
      apiEndpoints = List.of();
    }
    if (eventSchemas == null) {
      eventSchemas = List.of();
    }
    if (protocols == null) {
      protocols = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
