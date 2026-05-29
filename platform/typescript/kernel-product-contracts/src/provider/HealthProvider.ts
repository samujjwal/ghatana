/**
 * HealthProvider - interface for performing health checks and status reporting.
 *
 * @doc.type interface
 * @doc.purpose Health provider interface for health checks
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
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

const ProviderHealthStatusSchema = z.enum([
  "healthy",
  "degraded",
  "blocked",
  "failed",
  "skipped",
  "unknown",
]);

export const HealthCheckResultSchema = z
  .object({
    checkId: z.string().trim().min(1),
    name: z.string().trim().min(1),
    status: ProviderHealthStatusSchema,
    message: z.string().trim().min(1),
    checkedAt: z.string().datetime({ offset: true }),
    duration: z.number().nonnegative(),
  })
  .strict();

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

export const DeploymentHealthSnapshotSchema = z
  .object({
    deploymentId: z.string().trim().min(1),
    environment: z.string().trim().min(1),
    checks: z.array(HealthCheckResultSchema),
    overallStatus: z.enum(["healthy", "degraded", "blocked", "failed"]),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

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

export const HealthProviderSchema = z.custom<HealthProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.performHealthCheck === "function" &&
      typeof provider.getDeploymentHealth === "function" &&
      typeof provider.listHealthChecks === "function"
    );
  },
  "HealthProvider requires health check query functions"
);

export function validateHealthCheckResult(
  value: unknown
): value is HealthCheckResult {
  return HealthCheckResultSchema.safeParse(value).success;
}

export function validateDeploymentHealthSnapshot(
  value: unknown
): value is DeploymentHealthSnapshot {
  return DeploymentHealthSnapshotSchema.safeParse(value).success;
}

export function validateHealthProvider(value: unknown): value is HealthProvider {
  return HealthProviderSchema.safeParse(value).success;
}
