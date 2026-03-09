package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for ArtifactSigner agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that signs build artifacts for integrity verification input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArtifactSignerInput(@NotNull String artifactId, @NotNull String artifactPath, @NotNull String signingKeyId) {
  public ArtifactSignerInput {
    if (artifactId == null || artifactId.isEmpty()) {
      throw new IllegalArgumentException("artifactId cannot be null or empty");
    }
    if (artifactPath == null || artifactPath.isEmpty()) {
      throw new IllegalArgumentException("artifactPath cannot be null or empty");
    }
    if (signingKeyId == null || signingKeyId.isEmpty()) {
      throw new IllegalArgumentException("signingKeyId cannot be null or empty");
    }
  }
}
