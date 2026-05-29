/**
 * GateHealthSnapshot - health snapshot for gate evaluation.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for gate evaluation operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Gate evaluation status.
 */
export interface GateEvaluationStatus {
  readonly gateId: string;
  readonly passed: boolean;
  readonly reason: string;
  readonly evaluatedAt: string;
  readonly duration: number;
}

export const GateEvaluationStatusSchema = z
  .object({
    gateId: z.string().trim().min(1),
    passed: z.boolean(),
    reason: z.string().trim().min(1),
    evaluatedAt: z.string().datetime({ offset: true }),
    duration: z.number().nonnegative(),
  })
  .strict();

/**
 * Gate health snapshot.
 */
export interface GateHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Phase identifier.
   */
  readonly phase: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Gate evaluation statuses.
   */
  readonly gates: readonly GateEvaluationStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const GateHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    phase: z.string().trim().min(1),
    status: HealthStatusSchema,
    gates: z.array(GateEvaluationStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validateGateEvaluationStatus(
  value: unknown
): value is GateEvaluationStatus {
  return GateEvaluationStatusSchema.safeParse(value).success;
}

export function validateGateHealthSnapshot(
  value: unknown
): value is GateHealthSnapshot {
  return GateHealthSnapshotSchema.safeParse(value).success;
}
