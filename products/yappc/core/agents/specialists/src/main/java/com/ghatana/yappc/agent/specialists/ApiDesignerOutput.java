package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ApiDesigner agent.
 *
 * @doc.type record
 * @doc.purpose Expert API designer for REST, GraphQL and contract-first design output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ApiDesignerOutput(@NotNull String designId, @NotNull String apiContract, @NotNull List<String> endpoints, @NotNull Map<String, Object> metadata) {
  public ApiDesignerOutput {
    if (designId == null || designId.isEmpty()) {
      throw new IllegalArgumentException("designId cannot be null or empty");
    }
    if (apiContract == null || apiContract.isEmpty()) {
      throw new IllegalArgumentException("apiContract cannot be null or empty");
    }
    if (endpoints == null) {
      endpoints = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
