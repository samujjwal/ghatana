package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for SecretStoreIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for secret management systems input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SecretStoreIntegrationInput(@NotNull String vaultId, @NotNull String operation, @NotNull Map<String, Object> secretRef) {
  public SecretStoreIntegrationInput {
    if (vaultId == null || vaultId.isEmpty()) {
      throw new IllegalArgumentException("vaultId cannot be null or empty");
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("operation cannot be null or empty");
    }
    if (secretRef == null) {
      secretRef = Map.of();
    }
  }
}
