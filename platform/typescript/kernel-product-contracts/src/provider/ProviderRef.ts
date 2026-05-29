/**
 * ProviderRef - reference to a provider implementation.
 *
 * @doc.type interface
 * @doc.purpose Provider reference for ProductUnit configuration
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

/**
 * Reference to a provider implementation.
 */
export interface ProviderRef {
  /**
   * Provider identifier (e.g., "ghatana-file-registry", "github", "aws-codepipeline").
   */
  readonly providerId: string;

  /**
   * Optional provider-specific configuration.
   */
  readonly config?: Record<string, unknown>;
}

export const ProviderRefSchema = z
  .object({
    providerId: z.string().trim().min(1),
    config: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();

export function validateProviderRef(value: unknown): value is ProviderRef {
  return ProviderRefSchema.safeParse(value).success;
}
