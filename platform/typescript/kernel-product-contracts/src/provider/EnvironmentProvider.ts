/**
 * EnvironmentProvider - interface for managing environment configuration.
 *
 * @doc.type interface
 * @doc.purpose Environment provider interface for environment configuration
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

/**
 * Environment configuration.
 */
export interface EnvironmentConfig {
  readonly environment: string;
  readonly variables: Record<string, string>;
  readonly secrets: readonly string[];
}

export const EnvironmentConfigSchema = z
  .object({
    environment: z.string().trim().min(1),
    variables: z.record(z.string(), z.string()),
    secrets: z.array(z.string().trim().min(1)),
  })
  .strict();

/**
 * Environment provider for managing environment configuration.
 */
export interface EnvironmentProvider extends KernelProvider {
  /**
   * Gets environment configuration.
   */
  getEnvironmentConfig(environment: string): Promise<EnvironmentConfig>;

  /**
   * Sets environment configuration.
   */
  setEnvironmentConfig(
    environment: string,
    config: EnvironmentConfig
  ): Promise<void>;

  /**
   * Lists environments.
   */
  listEnvironments(): Promise<readonly string[]>;
}

export const EnvironmentProviderSchema = z.custom<EnvironmentProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.getEnvironmentConfig === "function" &&
      typeof provider.setEnvironmentConfig === "function" &&
      typeof provider.listEnvironments === "function"
    );
  },
  "EnvironmentProvider requires environment configuration functions"
);

export function validateEnvironmentConfig(
  value: unknown
): value is EnvironmentConfig {
  return EnvironmentConfigSchema.safeParse(value).success;
}

export function validateEnvironmentProvider(
  value: unknown
): value is EnvironmentProvider {
  return EnvironmentProviderSchema.safeParse(value).success;
}
