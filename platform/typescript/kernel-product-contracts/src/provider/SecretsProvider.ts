/**
 * SecretsProvider - interface for securely accessing deployment secrets.
 *
 * @doc.type interface
 * @doc.purpose Secrets provider interface for secret access
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

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
