package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from SupplyChainVerifier agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that verifies supply chain integrity output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SupplyChainVerifierOutput(@NotNull String verificationId, boolean verified, @NotNull List<String> issues, @NotNull Map<String, Object> metadata) {
  public SupplyChainVerifierOutput {
    if (verificationId == null || verificationId.isEmpty()) {
      throw new IllegalArgumentException("verificationId cannot be null or empty");
    }
    if (issues == null) {
      issues = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
