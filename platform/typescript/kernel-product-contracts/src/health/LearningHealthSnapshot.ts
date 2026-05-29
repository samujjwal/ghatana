/**
 * LearningHealthSnapshot - health snapshot for learning delta operations.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for learning delta operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Learning delta status.
 */
export interface LearningDeltaStatus {
  readonly deltaId: string;
  readonly status: HealthStatus;
  readonly promotionStatus: string;
  readonly message: string;
  readonly createdAt: string;
}

export const LearningDeltaStatusSchema = z
  .object({
    deltaId: z.string().trim().min(1),
    status: HealthStatusSchema,
    promotionStatus: z.string().trim().min(1),
    message: z.string().trim().min(1),
    createdAt: z.string().datetime({ offset: true }),
  })
  .strict();

/**
 * Learning health snapshot.
 */
export interface LearningHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Learning delta statuses.
   */
  readonly learningDeltas: readonly LearningDeltaStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const LearningHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    status: HealthStatusSchema,
    learningDeltas: z.array(LearningDeltaStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validateLearningDeltaStatus(
  value: unknown
): value is LearningDeltaStatus {
  return LearningDeltaStatusSchema.safeParse(value).success;
}

export function validateLearningHealthSnapshot(
  value: unknown
): value is LearningHealthSnapshot {
  return LearningHealthSnapshotSchema.safeParse(value).success;
}
