/**
 * Tests for ResumePolicy contracts.
 */

import { describe, it, expect } from "vitest";
import { DefaultResumePolicy, DEFAULT_RESUME_POLICY_CONFIG } from "../ResumePolicy";
import type { RunRecord } from "../RunStore";
import type { PhaseGraph } from "../PhaseGraph";
import type { ProductLifecyclePhase } from "../ProductLifecyclePhase";

describe("ResumePolicy", () => {
  describe("DefaultResumePolicy", () => {
    it("should use default configuration", () => {
      const policy = new DefaultResumePolicy();
      expect(policy).toBeDefined();
    });

    it("should allow custom configuration", () => {
      const customConfig = {
        maxResumeAttempts: 5,
        allowResumeFromFailed: false,
        maxRunAgeHours: 48,
      };
      const policy = new DefaultResumePolicy(customConfig);
      expect(policy).toBeDefined();
    });

    it("should not resume a running run", async () => {
      const policy = new DefaultResumePolicy();
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "running",
        startedAt: new Date().toISOString(),
        metadata: {},
        updatedAt: new Date().toISOString(),
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [],
        state: "running",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      const decision = await policy.getResumeDecision(runRecord, phaseGraph);
      expect(decision.canResume).toBe(false);
      expect(decision.reason).toContain("still in progress");
    });

    it("should not resume a succeeded run", async () => {
      const policy = new DefaultResumePolicy();
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
        durationMs: 3600000,
        metadata: {},
        updatedAt: new Date().toISOString(),
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [],
        state: "succeeded",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      const decision = await policy.getResumeDecision(runRecord, phaseGraph);
      expect(decision.canResume).toBe(false);
      expect(decision.reason).toContain("already succeeded");
    });

    it("should not resume a run that is too old", async () => {
      const policy = new DefaultResumePolicy({ maxRunAgeHours: 1 });
      const oldDate = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
      
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "failed",
        startedAt: oldDate,
        completedAt: oldDate,
        durationMs: 3600000,
        metadata: {},
        updatedAt: oldDate,
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [
          {
            nodeId: "node-1",
            phase: "build" as ProductLifecyclePhase,
            dependsOn: [],
            state: "failed",
          },
        ],
        state: "failed",
        createdAt: oldDate,
        updatedAt: oldDate,
      };

      const decision = await policy.getResumeDecision(runRecord, phaseGraph);
      expect(decision.canResume).toBe(false);
      expect(decision.reason).toContain("too old");
    });

    it("should resume from failed nodes when allowed", async () => {
      const policy = new DefaultResumePolicy({ allowResumeFromFailed: true });
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "failed",
        startedAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
        durationMs: 3600000,
        metadata: {},
        updatedAt: new Date().toISOString(),
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [
          {
            nodeId: "node-1",
            phase: "build" as ProductLifecyclePhase,
            dependsOn: [],
            state: "failed",
          },
        ],
        state: "failed",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      const decision = await policy.getResumeDecision(runRecord, phaseGraph);
      expect(decision.canResume).toBe(true);
      expect(decision.strategy).toBe("from-failed-node");
      expect(decision.reexecutableNodes).toHaveLength(1);
    });

    it("should resume from beginning when resume from failed is not allowed", async () => {
      const policy = new DefaultResumePolicy({ allowResumeFromFailed: false, allowSkipFailed: false });
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "failed",
        startedAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
        durationMs: 3600000,
        metadata: {},
        updatedAt: new Date().toISOString(),
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [
          {
            nodeId: "node-1",
            phase: "build" as ProductLifecyclePhase,
            dependsOn: [],
            state: "failed",
          },
        ],
        state: "failed",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      const decision = await policy.getResumeDecision(runRecord, phaseGraph);
      expect(decision.canResume).toBe(true);
      expect(decision.strategy).toBe("from-beginning");
    });

    it("should validate resume successfully for recent failed run", async () => {
      const policy = new DefaultResumePolicy();
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "failed",
        startedAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
        durationMs: 3600000,
        metadata: {},
        updatedAt: new Date().toISOString(),
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [
          {
            nodeId: "node-1",
            phase: "build" as ProductLifecyclePhase,
            dependsOn: [],
            state: "failed",
          },
        ],
        state: "failed",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      const validation = await policy.validateResume(runRecord, phaseGraph);
      expect(validation.valid).toBe(true);
      expect(validation.errors).toHaveLength(0);
    });

    it("should validate resume with errors for old run", async () => {
      const policy = new DefaultResumePolicy({ maxRunAgeHours: 1 });
      const oldDate = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
      
      const runRecord: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "failed",
        startedAt: oldDate,
        completedAt: oldDate,
        durationMs: 3600000,
        metadata: {},
        updatedAt: oldDate,
      };

      const phaseGraph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [],
        state: "failed",
        createdAt: oldDate,
        updatedAt: oldDate,
      };

      const validation = await policy.validateResume(runRecord, phaseGraph);
      expect(validation.valid).toBe(false);
      expect(validation.errors.length).toBeGreaterThan(0);
      expect(validation.errors[0]).toContain("too old");
    });
  });
});
