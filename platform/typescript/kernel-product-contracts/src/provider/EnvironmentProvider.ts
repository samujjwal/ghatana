/**
 * EnvironmentProvider - interface for managing environment configuration.
 *
 * @doc.type interface
 * @doc.purpose Environment provider interface for environment configuration
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider";

/**
 * Environment configuration.
 */
export interface EnvironmentConfig {
  readonly environment: string;
  readonly variables: Record<string, string>;
  readonly secrets: readonly string[];
}

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
