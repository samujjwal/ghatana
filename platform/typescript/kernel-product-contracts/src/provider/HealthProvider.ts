/**
 * HealthProvider - interface for performing health checks and status reporting.
 *
 * @doc.type interface
 * @doc.purpose Health provider interface for health checks
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider.js";

/**
 * Health check result.
 */
export interface HealthCheckResult {
  readonly checkId: string;
  readonly name: string;
  readonly status: "healthy" | "degraded" | "blocked" | "failed" | "skipped" | "unknown";
  readonly message: string;
  readonly checkedAt: string;
  readonly duration: number;
}

/**
 * Deployment health snapshot.
 */
export interface DeploymentHealthSnapshot {
  readonly deploymentId: string;
  readonly environment: string;
  readonly checks: readonly HealthCheckResult[];
  readonly overallStatus: "healthy" | "degraded" | "blocked" | "failed";
  readonly snapshotAt: string;
}

/**
 * Health provider for performing health checks and status reporting.
 */
export interface HealthProvider extends KernelProvider {
  /**
   * Performs a health check.
   */
  performHealthCheck(checkId: string): Promise<HealthCheckResult>;

  /**
   * Gets deployment health snapshot.
   */
  getDeploymentHealth(deploymentId: string): Promise<DeploymentHealthSnapshot>;

  /**
   * Lists health checks.
   */
  listHealthChecks(): Promise<readonly string[]>;
}
