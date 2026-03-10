package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for SbomSigner agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that signs SBOM for supply chain trust input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SbomSignerInput(@NotNull String sbomId, @NotNull String sbomContent, @NotNull String signingKeyId) {
  public SbomSignerInput {
    if (sbomId == null || sbomId.isEmpty()) {
      throw new IllegalArgumentException("sbomId cannot be null or empty");
    }
    if (sbomContent == null || sbomContent.isEmpty()) {
      throw new IllegalArgumentException("sbomContent cannot be null or empty");
    }
    if (signingKeyId == null || signingKeyId.isEmpty()) {
      throw new IllegalArgumentException("signingKeyId cannot be null or empty");
    }
  }
}
