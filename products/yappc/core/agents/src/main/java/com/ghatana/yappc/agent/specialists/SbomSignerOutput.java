package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from SbomSigner agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that signs SBOM for supply chain trust output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SbomSignerOutput(@NotNull String signatureId, @NotNull String signature, @NotNull String format, @NotNull Map<String, Object> metadata) {
  public SbomSignerOutput {
    if (signatureId == null || signatureId.isEmpty()) {
      throw new IllegalArgumentException("signatureId cannot be null or empty");
    }
    if (signature == null || signature.isEmpty()) {
      throw new IllegalArgumentException("signature cannot be null or empty");
    }
    if (format == null || format.isEmpty()) {
      throw new IllegalArgumentException("format cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
