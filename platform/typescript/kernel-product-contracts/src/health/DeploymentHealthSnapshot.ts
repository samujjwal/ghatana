/**
 * DeploymentHealthSnapshot - health snapshot for deployments.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for deployment operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Deployment health status.
 */
export interface DeploymentHealthStatus {
  readonly deploymentId: string;
  readonly environment: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly deployedAt: string;
  readonly endpoints: readonly string[];
}

export const DeploymentHealthStatusSchema = z
  .object({
    deploymentId: z.string().trim().min(1),
    environment: z.string().trim().min(1),
    status: HealthStatusSchema,
    message: z.string().trim().min(1),
    deployedAt: z.string().datetime({ offset: true }),
    endpoints: z.array(z.string().trim().min(1)),
  })
  .strict();

/**
 * Deployment health snapshot.
 */
export interface DeploymentHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Deployment health statuses.
   */
  readonly deployments: readonly DeploymentHealthStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const DeploymentHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    status: HealthStatusSchema,
    deployments: z.array(DeploymentHealthStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validateDeploymentHealthStatus(
  value: unknown
): value is DeploymentHealthStatus {
  return DeploymentHealthStatusSchema.safeParse(value).success;
}

export function validateDeploymentHealthSnapshot(
  value: unknown
): value is DeploymentHealthSnapshot {
  return DeploymentHealthSnapshotSchema.safeParse(value).success;
}
