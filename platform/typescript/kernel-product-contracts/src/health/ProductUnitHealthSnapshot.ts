/**
 * ProductUnitHealthSnapshot - health snapshot for a ProductUnit.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for ProductUnit lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Surface health status.
 */
export interface SurfaceHealthStatus {
  readonly surfaceId: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly lastUpdated: string;
}

export const SurfaceHealthStatusSchema = z
  .object({
    surfaceId: z.string().trim().min(1),
    status: HealthStatusSchema,
    message: z.string().trim().min(1),
    lastUpdated: z.string().datetime({ offset: true }),
  })
  .strict();

/**
 * ProductUnit health snapshot.
 */
export interface ProductUnitHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Surface health statuses.
   */
  readonly surfaces: readonly SurfaceHealthStatus[];

  /**
   * Lifecycle execution status.
   */
  readonly lifecycleStatus: string;

  /**
   * Last lifecycle run timestamp.
   */
  readonly lastLifecycleRun?: string;

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;

  /**
   * Additional metadata.
   */
  readonly metadata?: Record<string, unknown>;
}

export const ProductUnitHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    status: HealthStatusSchema,
    surfaces: z.array(SurfaceHealthStatusSchema),
    lifecycleStatus: z.string().trim().min(1),
    lastLifecycleRun: z.string().datetime({ offset: true }).optional(),
    snapshotAt: z.string().datetime({ offset: true }),
    metadata: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();

export function validateSurfaceHealthStatus(
  value: unknown
): value is SurfaceHealthStatus {
  return SurfaceHealthStatusSchema.safeParse(value).success;
}

export function validateProductUnitHealthSnapshot(
  value: unknown
): value is ProductUnitHealthSnapshot {
  return ProductUnitHealthSnapshotSchema.safeParse(value).success;
}
