/**
 * DeploymentProvider - interface for deploying to environments.
 *
 * @doc.type interface
 * @doc.purpose Deployment provider interface for deployment operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider";

/**
 * Deployment configuration.
 */
export interface DeploymentConfig {
  readonly environment: string;
  readonly config: Record<string, unknown>;
  readonly artifactIds: readonly string[];
}

/**
 * Deployment result.
 */
export interface DeploymentResult {
  readonly deploymentId: string;
  readonly status: string;
  readonly deployedAt: string;
  readonly endpoints: readonly string[];
}

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
