/**
 * PreviewSecurityHealthSnapshot - health snapshot for preview security operations.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for preview security operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Security check status.
 */
export interface SecurityCheckStatus {
  readonly checkId: string;
  readonly checkType: string;
  readonly status: HealthStatus;
  readonly severity: string;
  readonly vulnerabilityCount: number;
  readonly message: string;
  readonly lastChecked: string;
}

export const SecurityCheckStatusSchema = z
  .object({
    checkId: z.string().trim().min(1),
    checkType: z.string().trim().min(1),
    status: HealthStatusSchema,
    severity: z.string().trim().min(1),
    vulnerabilityCount: z.number().int().nonnegative(),
    message: z.string().trim().min(1),
    lastChecked: z.string().datetime({ offset: true }),
  })
  .strict();

/**
 * Preview security health snapshot.
 */
export interface PreviewSecurityHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Security check statuses.
   */
  readonly securityChecks: readonly SecurityCheckStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const PreviewSecurityHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    status: HealthStatusSchema,
    securityChecks: z.array(SecurityCheckStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validateSecurityCheckStatus(
  value: unknown
): value is SecurityCheckStatus {
  return SecurityCheckStatusSchema.safeParse(value).success;
}

export function validatePreviewSecurityHealthSnapshot(
  value: unknown
): value is PreviewSecurityHealthSnapshot {
  return PreviewSecurityHealthSnapshotSchema.safeParse(value).success;
}
