/**
 * LifecycleHealthSnapshot - health snapshot for lifecycle execution.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for lifecycle execution operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Phase health status.
 */
export interface PhaseHealthStatus {
  readonly phase: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly duration: number;
  readonly completedAt: string;
}

export const PhaseHealthStatusSchema = z
  .object({
    phase: z.string().trim().min(1),
    status: HealthStatusSchema,
    message: z.string().trim().min(1),
    duration: z.number().nonnegative(),
    completedAt: z.string().datetime({ offset: true }),
  })
  .strict();

/**
 * Lifecycle health snapshot.
 */
export interface LifecycleHealthSnapshot {
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
   * Phase health statuses.
   */
  readonly phases: readonly PhaseHealthStatus[];

  /**
   * Current phase.
   */
  readonly currentPhase?: string;

  /**
   * Total execution duration.
   */
  readonly totalDuration: number;

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const LifecycleHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    status: HealthStatusSchema,
    phases: z.array(PhaseHealthStatusSchema),
    currentPhase: z.string().trim().min(1).optional(),
    totalDuration: z.number().nonnegative(),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validatePhaseHealthStatus(
  value: unknown
): value is PhaseHealthStatus {
  return PhaseHealthStatusSchema.safeParse(value).success;
}

export function validateLifecycleHealthSnapshot(
  value: unknown
): value is LifecycleHealthSnapshot {
  return LifecycleHealthSnapshotSchema.safeParse(value).success;
}
