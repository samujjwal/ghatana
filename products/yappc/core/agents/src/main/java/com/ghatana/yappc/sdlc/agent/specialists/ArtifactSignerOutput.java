package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ArtifactSigner agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that signs build artifacts for integrity verification output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArtifactSignerOutput(@NotNull String signatureId, @NotNull String signature, @NotNull String algorithm, @NotNull Map<String, Object> metadata) {
  public ArtifactSignerOutput {
    if (signatureId == null || signatureId.isEmpty()) {
      throw new IllegalArgumentException("signatureId cannot be null or empty");
    }
    if (signature == null || signature.isEmpty()) {
      throw new IllegalArgumentException("signature cannot be null or empty");
    }
    if (algorithm == null || algorithm.isEmpty()) {
      throw new IllegalArgumentException("algorithm cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
