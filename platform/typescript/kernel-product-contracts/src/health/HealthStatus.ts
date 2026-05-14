/**
 * HealthStatus - health status for health snapshots.
 *
 * @doc.type type
 * @doc.purpose Health status enumeration for health snapshots
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * Health status for health snapshots.
 */
export type HealthStatus =
  | "healthy"
  | "degraded"
  | "blocked"
  | "failed"
  | "skipped"
  | "unknown";
