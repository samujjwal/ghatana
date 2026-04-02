/**
 * Learning Domain Business Metrics
 *
 * Exposes Prometheus counters for key learning domain operations:
 * enrollments, progress updates, and module completions.
 *
 * Counters are lazy-registered singletons using prom-client's global
 * default registry — safe to import multiple times without duplication.
 *
 * @doc.type module
 * @doc.purpose Business-level Prometheus metrics for the learning domain
 * @doc.layer product
 * @doc.pattern Facade
 */

import { Counter, register } from "prom-client";

// ---------------------------------------------------------------------------
// Counters
// ---------------------------------------------------------------------------

/**
 * Tracks enrollment events. Incremented on every successful `enrollInModule`
 * call; useful for measuring adoption and growth per tenant.
 *
 * Labels: tenantId, status ("new" | "reactivated")
 */
const learningEnrollmentsTotal = new Counter({
  name: "tutorputor_learning_enrollments_total",
  help: "Total number of module enrollment events",
  labelNames: ["tenant_id", "status"],
  registers: [register],
});

/**
 * Tracks progress update invocations (PATCH /enrollments/:id/progress).
 * Labels: tenantId
 */
const learningProgressUpdatesTotal = new Counter({
  name: "tutorputor_learning_progress_updates_total",
  help: "Total number of learning progress update events",
  labelNames: ["tenant_id"],
  registers: [register],
});

/**
 * Tracks module completions (progressPercent reaches 100).
 * Labels: tenantId
 */
const learningCompletionsTotal = new Counter({
  name: "tutorputor_learning_completions_total",
  help: "Total number of module completion events",
  labelNames: ["tenant_id"],
  registers: [register],
});

// ---------------------------------------------------------------------------
// Exported facade
// ---------------------------------------------------------------------------

/** Singleton facade for learning domain metrics. */
export const learningMetrics = {
  /**
   * Records a successful enrollment.
   *
   * @param tenantId - tenant scope
   * @param status - "new" for first-time enrollments, "reactivated" for upserts
   */
  recordEnrollment(tenantId: string, status: "new" | "reactivated"): void {
    try {
      learningEnrollmentsTotal.inc({ tenant_id: tenantId, status });
    } catch {
      // metrics must never affect the business path
    }
  },

  /**
   * Records a progress update event.
   *
   * @param tenantId - tenant scope
   */
  recordProgressUpdate(tenantId: string): void {
    try {
      learningProgressUpdatesTotal.inc({ tenant_id: tenantId });
    } catch {
      // metrics must never affect the business path
    }
  },

  /**
   * Records a module completion (progress reached 100%).
   *
   * @param tenantId - tenant scope
   */
  recordCompletion(tenantId: string): void {
    try {
      learningCompletionsTotal.inc({ tenant_id: tenantId });
    } catch {
      // metrics must never affect the business path
    }
  },
} as const;
