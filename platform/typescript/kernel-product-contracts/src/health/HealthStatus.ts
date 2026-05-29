/**
 * HealthStatus - health status for health snapshots.
 *
 * @doc.type type
 * @doc.purpose Health status enumeration for health snapshots
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

/**
 * Health status for health snapshots.
 */
export type HealthStatus =
  | "healthy"
  | "degraded"
  | "blocked"
  | "failed"
  | "skipped"
  | "unknown"
  | "requires-approval"
  | "requires-verification"
  | "obsolete"
  | "quarantined";

export const HealthStatusSchema = z.enum([
  "healthy",
  "degraded",
  "blocked",
  "failed",
  "skipped",
  "unknown",
  "requires-approval",
  "requires-verification",
  "obsolete",
  "quarantined",
]);

export function validateHealthStatus(value: unknown): value is HealthStatus {
  return HealthStatusSchema.safeParse(value).success;
}
