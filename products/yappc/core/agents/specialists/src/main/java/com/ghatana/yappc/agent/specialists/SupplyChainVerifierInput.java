package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for SupplyChainVerifier agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that verifies supply chain integrity input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SupplyChainVerifierInput(@NotNull String artifactId, @NotNull String sbomId, @NotNull Map<String, Object> verificationPolicy) {
  public SupplyChainVerifierInput {
    if (artifactId == null || artifactId.isEmpty()) {
      throw new IllegalArgumentException("artifactId cannot be null or empty");
    }
    if (sbomId == null || sbomId.isEmpty()) {
      throw new IllegalArgumentException("sbomId cannot be null or empty");
    }
    if (verificationPolicy == null) {
      verificationPolicy = Map.of();
    }
  }
}
