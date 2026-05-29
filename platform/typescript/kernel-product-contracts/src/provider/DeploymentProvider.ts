/**
 * DeploymentProvider - interface for deploying to environments.
 *
 * @doc.type interface
 * @doc.purpose Deployment provider interface for deployment operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

/**
 * Deployment configuration.
 */
export interface DeploymentConfig {
  readonly environment: string;
  readonly config: Record<string, unknown>;
  readonly artifactIds: readonly string[];
}

export const DeploymentConfigSchema = z
  .object({
    environment: z.string().trim().min(1),
    config: z.record(z.string(), z.unknown()),
    artifactIds: z.array(z.string().trim().min(1)),
  })
  .strict();

/**
 * Deployment result.
 */
export interface DeploymentResult {
  readonly deploymentId: string;
  readonly status: string;
  readonly deployedAt: string;
  readonly endpoints: readonly string[];
}

export const DeploymentResultSchema = z
  .object({
    deploymentId: z.string().trim().min(1),
    status: z.string().trim().min(1),
    deployedAt: z.string().datetime({ offset: true }),
    endpoints: z.array(z.string().trim().min(1)),
  })
  .strict();

/**
 * Deployment provider for deploying to environments.
 */
export interface DeploymentProvider extends KernelProvider {
  /**
   * Deploys to an environment.
   */
  deploy(config: DeploymentConfig): Promise<DeploymentResult>;

  /**
   * Gets deployment status.
   */
  getDeploymentStatus(deploymentId: string): Promise<{
    status: string;
    completed: boolean;
    success: boolean;
  }>;

  /**
   * Rolls back a deployment.
   */
  rollback(deploymentId: string): Promise<DeploymentResult>;

  /**
   * Lists deployments for an environment.
   */
  listDeployments(environment: string): Promise<readonly DeploymentResult[]>;
}

export const DeploymentProviderSchema = z.custom<DeploymentProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.deploy === "function" &&
      typeof provider.getDeploymentStatus === "function" &&
      typeof provider.rollback === "function" &&
      typeof provider.listDeployments === "function"
    );
  },
  "DeploymentProvider requires deployment functions"
);

export function validateDeploymentConfig(
  value: unknown
): value is DeploymentConfig {
  return DeploymentConfigSchema.safeParse(value).success;
}

export function validateDeploymentResult(
  value: unknown
): value is DeploymentResult {
  return DeploymentResultSchema.safeParse(value).success;
}

export function validateDeploymentProvider(
  value: unknown
): value is DeploymentProvider {
  return DeploymentProviderSchema.safeParse(value).success;
}
