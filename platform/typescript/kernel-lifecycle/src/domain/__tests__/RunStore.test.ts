/**
 * Tests for RunStore contracts.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { InMemoryRunStore } from "../RunStore";
import type { RunRecord } from "../RunStore";
import type { ProductLifecyclePhase } from "../ProductLifecyclePhase";

describe("RunStore", () => {
  let runStore: InMemoryRunStore;

  beforeEach(() => {
    runStore = new InMemoryRunStore();
  });

  describe("InMemoryRunStore", () => {
    it("should create a run record", async () => {
      const record: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "running",
        startedAt: "2024-01-01T00:00:00.000Z",
        metadata: {},
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      await runStore.createRun(record);
      const retrieved = await runStore.getRun("test-run-1");

      expect(retrieved).toBeDefined();
      expect(retrieved?.runId).toBe("test-run-1");
      expect(retrieved?.status).toBe("running");
    });

    it("should update a run record", async () => {
      const record: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "running",
        startedAt: "2024-01-01T00:00:00.000Z",
        metadata: {},
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      await runStore.createRun(record);
      await runStore.updateRun("test-run-1", {
        status: "succeeded",
        completedAt: "2024-01-01T01:00:00.000Z",
        durationMs: 3600000,
      });

      const retrieved = await runStore.getRun("test-run-1");
      expect(retrieved?.status).toBe("succeeded");
      expect(retrieved?.completedAt).toBe("2024-01-01T01:00:00.000Z");
      expect(retrieved?.durationMs).toBe(3600000);
    });

    it("should query runs with filters", async () => {
      const record1: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2024-01-01T00:00:00.000Z",
        completedAt: "2024-01-01T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2024-01-01T01:00:00.000Z",
      };

      const record2: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "product-b",
        runId: "run-2",
        phase: "build" as ProductLifecyclePhase,
        status: "failed",
        startedAt: "2024-01-02T00:00:00.000Z",
        completedAt: "2024-01-02T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2024-01-02T01:00:00.000Z",
      };

      await runStore.createRun(record1);
      await runStore.createRun(record2);

      const result = await runStore.queryRuns({ productId: "product-a" });
      expect(result.runs).toHaveLength(1);
      expect(result.runs[0].runId).toBe("run-1");
      expect(result.totalCount).toBe(1);
    });

    it("should query runs by metadata filters", async () => {
      await runStore.createRun({
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-local",
        phase: "deploy" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2024-01-01T00:00:00.000Z",
        metadata: {
          lifecycleProfile: "polyglot-service-product",
          providerMode: "bootstrap",
          environment: "local",
          sourceRef: "abc123",
        },
        updatedAt: "2024-01-01T01:00:00.000Z",
      });
      await runStore.createRun({
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-prod",
        phase: "deploy" as ProductLifecyclePhase,
        status: "failed",
        startedAt: "2024-01-02T00:00:00.000Z",
        metadata: {
          lifecycleProfile: "java-service-product",
          providerMode: "platform",
          environment: "prod",
          sourceRef: "def456",
        },
        updatedAt: "2024-01-02T01:00:00.000Z",
      });

      const result = await runStore.queryRuns({
        productId: "product-a",
        lifecycleProfile: "polyglot-service-product",
        providerMode: "bootstrap",
        environment: "local",
        sourceRef: "abc123",
      });

      expect(result.totalCount).toBe(1);
      expect(result.runs[0].runId).toBe("run-local");
    });

    it("should get latest run for product and phase", async () => {
      const record1: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2024-01-01T00:00:00.000Z",
        completedAt: "2024-01-01T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2024-01-01T01:00:00.000Z",
      };

      const record2: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-2",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2024-01-02T00:00:00.000Z",
        completedAt: "2024-01-02T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2024-01-02T01:00:00.000Z",
      };

      await runStore.createRun(record1);
      await runStore.createRun(record2);

      const latest = await runStore.getLatestRun("product-a", "build");
      expect(latest?.runId).toBe("run-2");
    });

    it("should get running runs for product", async () => {
      const record1: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "running",
        startedAt: "2024-01-01T00:00:00.000Z",
        metadata: {},
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      const record2: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "product-a",
        runId: "run-2",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2024-01-01T00:00:00.000Z",
        completedAt: "2024-01-01T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2024-01-01T01:00:00.000Z",
      };

      await runStore.createRun(record1);
      await runStore.createRun(record2);

      const running = await runStore.getRunningRuns("product-a");
      expect(running).toHaveLength(1);
      expect(running[0].runId).toBe("run-1");
    });

    it("should delete a run record", async () => {
      const record: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "running",
        startedAt: "2024-01-01T00:00:00.000Z",
        metadata: {},
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      await runStore.createRun(record);
      await runStore.deleteRun("test-run-1");

      const retrieved = await runStore.getRun("test-run-1");
      expect(retrieved).toBeNull();
    });

    it("should delete runs older than timestamp", async () => {
      const record1: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "run-old",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2023-01-01T00:00:00.000Z",
        completedAt: "2023-01-01T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2023-01-01T01:00:00.000Z",
      };

      const record2: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "run-new",
        phase: "build" as ProductLifecyclePhase,
        status: "succeeded",
        startedAt: "2024-01-01T00:00:00.000Z",
        completedAt: "2024-01-01T01:00:00.000Z",
        durationMs: 3600000,
        metadata: {},
        updatedAt: "2024-01-01T01:00:00.000Z",
      };

      await runStore.createRun(record1);
      await runStore.createRun(record2);

      const deletedCount = await runStore.deleteRunsOlderThan("2024-01-01T00:00:00.000Z");
      expect(deletedCount).toBe(1);

      const oldRun = await runStore.getRun("run-old");
      const newRun = await runStore.getRun("run-new");
      expect(oldRun).toBeNull();
      expect(newRun).toBeDefined();
    });

    it("should clear all records", async () => {
      const record: RunRecord = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        phase: "build" as ProductLifecyclePhase,
        status: "running",
        startedAt: "2024-01-01T00:00:00.000Z",
        metadata: {},
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      await runStore.createRun(record);
      runStore.clear();

      const retrieved = await runStore.getRun("test-run-1");
      const result = await runStore.queryRuns({});
      expect(retrieved).toBeNull();
      expect(result.totalCount).toBe(0);
    });
  });
});
