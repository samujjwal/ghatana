/**
 * @doc.type test
 * @doc.purpose Verifies learning domain business metrics emit to the correct prom-client counters
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, expect, it, beforeEach } from "vitest";
import { register } from "prom-client";
import { learningMetrics } from "./learning-metrics.js";

// Helpers to read prom-client counter values from the default registry
async function getCounterValue(
  metricName: string,
  labels: Record<string, string>,
): Promise<number> {
  const metrics = await register.getMetricsAsJSON();
  const metric = metrics.find((m) => m.name === metricName);
  if (!metric) return 0;
  const values = (metric as { values: Array<{ labels: Record<string, string>; value: number }> }).values;
  const entry = values.find((v) =>
    Object.entries(labels).every(([k, val]) => v.labels[k] === val),
  );
  return entry?.value ?? 0;
}

describe("learningMetrics", () => {
  // Each test uses a unique tenant to avoid cross-test counter pollution
  const tenant = (suffix: string): string => `lm-test-tenant-${suffix}`;

  describe("recordEnrollment", () => {
    it('increments tutorputor_learning_enrollments_total with status="new"', async () => {
      const tenantId = tenant("enroll-new");
      const before = await getCounterValue(
        "tutorputor_learning_enrollments_total",
        { tenant_id: tenantId, status: "new" },
      );

      learningMetrics.recordEnrollment(tenantId, "new");

      const after = await getCounterValue(
        "tutorputor_learning_enrollments_total",
        { tenant_id: tenantId, status: "new" },
      );
      expect(after).toBe(before + 1);
    });

    it('increments tutorputor_learning_enrollments_total with status="reactivated"', async () => {
      const tenantId = tenant("enroll-react");
      const before = await getCounterValue(
        "tutorputor_learning_enrollments_total",
        { tenant_id: tenantId, status: "reactivated" },
      );

      learningMetrics.recordEnrollment(tenantId, "reactivated");

      const after = await getCounterValue(
        "tutorputor_learning_enrollments_total",
        { tenant_id: tenantId, status: "reactivated" },
      );
      expect(after).toBe(before + 1);
    });
  });

  describe("recordProgressUpdate", () => {
    it("increments tutorputor_learning_progress_updates_total for the correct tenant", async () => {
      const tenantId = tenant("progress");
      const before = await getCounterValue(
        "tutorputor_learning_progress_updates_total",
        { tenant_id: tenantId },
      );

      learningMetrics.recordProgressUpdate(tenantId);

      const after = await getCounterValue(
        "tutorputor_learning_progress_updates_total",
        { tenant_id: tenantId },
      );
      expect(after).toBe(before + 1);
    });
  });

  describe("recordCompletion", () => {
    it("increments tutorputor_learning_completions_total for the correct tenant", async () => {
      const tenantId = tenant("completion");
      const before = await getCounterValue(
        "tutorputor_learning_completions_total",
        { tenant_id: tenantId },
      );

      learningMetrics.recordCompletion(tenantId);

      const after = await getCounterValue(
        "tutorputor_learning_completions_total",
        { tenant_id: tenantId },
      );
      expect(after).toBe(before + 1);
    });

    it("does not affect different tenants", async () => {
      const tenantA = tenant("isolation-a");
      const tenantB = tenant("isolation-b");
      const before = await getCounterValue(
        "tutorputor_learning_completions_total",
        { tenant_id: tenantB },
      );

      learningMetrics.recordCompletion(tenantA);

      const after = await getCounterValue(
        "tutorputor_learning_completions_total",
        { tenant_id: tenantB },
      );
      expect(after).toBe(before);
    });
  });
});
