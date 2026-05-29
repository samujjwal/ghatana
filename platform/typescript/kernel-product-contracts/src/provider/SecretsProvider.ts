/**
 * SecretsProvider - interface for securely accessing deployment secrets.
 *
 * @doc.type interface
 * @doc.purpose Secrets provider interface for secret access
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

/**
 * Secrets provider for securely accessing deployment secrets.
 */
export interface SecretsProvider extends KernelProvider {
  /**
   * Gets a secret value.
   */
  getSecret(secretName: string): Promise<string | null>;

  /**
   * Sets a secret value.
   */
  setSecret(secretName: string, value: string): Promise<void>;

  /**
   * Deletes a secret.
   */
  deleteSecret(secretName: string): Promise<void>;

  /**
   * Lists secret names.
   */
  listSecrets(): Promise<readonly string[]>;
}

export const SecretsProviderSchema = z.custom<SecretsProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.getSecret === "function" &&
      typeof provider.setSecret === "function" &&
      typeof provider.deleteSecret === "function" &&
      typeof provider.listSecrets === "function"
    );
  },
  "SecretsProvider requires secret management functions"
);

export function validateSecretsProvider(
  value: unknown
): value is SecretsProvider {
  return SecretsProviderSchema.safeParse(value).success;
}
