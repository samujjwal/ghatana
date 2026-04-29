/**
 * Simulation concurrency and soak tests.
 *
 * Covers:
 * - 50 concurrent sessions created in parallel without ID collisions
 * - All sessions independently navigable without cross-contamination
 * - Soak: 500 sequential step operations across 10 sessions complete within budget
 * - Sessions terminated in parallel without errors
 * - Out-of-order parallel step navigation produces correct final states
 *
 * @doc.type test
 * @doc.purpose Concurrency and soak test coverage for SimRuntimeService
 * @doc.layer product
 * @doc.pattern PerformanceTest
 */

import { describe, it, expect, beforeEach, vi } from "vitest";

vi.mock("ioredis", () => ({
  default: class MockRedis {
    private store = new Map<string, string>();
    async get(key: string): Promise<string | null> { return this.store.get(key) ?? null; }
    async set(key: string, value: string): Promise<"OK"> { this.store.set(key, value); return "OK"; }
    async del(key: string): Promise<number> { this.store.delete(key); return 1; }
    async expire(_k: string, _s: number): Promise<number> { return 1; }
    async keys(_p: string): Promise<string[]> { return [...this.store.keys()]; }
    async quit(): Promise<"OK"> { return "OK"; }
    on(_e: string, _cb: unknown): this { return this; }
  },
}));

import { createRuntimeService } from "../service.js";
import type { SimulationManifest, SimEntity } from "@tutorputor/contracts/v1/simulation/types";

function buildManifest(id: string, stepCount: number): SimulationManifest {
  const entity: SimEntity = {
    id: "node-0",
    type: "array_element",
    x: 0, y: 0,
    visual: { fill: "#4A90D9" },
    data: { value: 1 },
  } as unknown as SimEntity;

  return {
    id: id as unknown as SimulationManifest["id"],
    version: "1.0",
    domain: "CS_DISCRETE" as unknown as SimulationManifest["domain"],
    title: `Soak-${id}`,
    description: "Soak test manifest",
    authorId: "author-1" as unknown as SimulationManifest["authorId"],
    tenantId: "tenant-soak" as unknown as SimulationManifest["tenantId"],
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: "1.0",
    initialEntities: [entity],
    steps: Array.from({ length: stepCount }, (_, i) => ({
      id: `step-${i}` as unknown as SimulationManifest["steps"][0]["id"],
      orderIndex: i,
      actions: [],
    })),
    domainMetadata: { domain: "CS_DISCRETE" as unknown as SimulationManifest["domain"] },
  };
}

describe("SimRuntimeService — Concurrency & Soak Tests", () => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let service: any;

  beforeEach(() => {
    service = createRuntimeService();
  });

  describe("Concurrency: 50 parallel session creates", () => {
    it("produces 50 unique session IDs", async () => {
      const manifests = Array.from({ length: 50 }, (_, i) =>
        buildManifest(`concurrent-${i}`, 5),
      );
      const ids = await Promise.all(manifests.map((m) => service.createSession(m)));
      const unique = new Set(ids as string[]);
      expect(unique.size).toBe(50);
    });

    it("each session reflects its own manifest domain independently", async () => {
      const manifests = Array.from({ length: 50 }, (_, i) =>
        buildManifest(`iso-${i}`, 3),
      );
      const ids = (await Promise.all(manifests.map((m) => service.createSession(m)))) as string[];

      // Step each session forward once in parallel
      const kfs = await Promise.all(ids.map((id) => service.stepForward(id)));
      // All should be at step index 0
      kfs.forEach((kf) => expect((kf as { stepIndex: number }).stepIndex).toBe(0));
    });

    it("terminating all sessions in parallel does not throw", async () => {
      const ids = (await Promise.all(
        Array.from({ length: 50 }, (_, i) =>
          service.createSession(buildManifest(`term-${i}`, 2)),
        ),
      )) as string[];

      await expect(
        Promise.all(ids.map((id) => service.terminateSession(id))),
      ).resolves.not.toThrow();
    });
  });

  describe("Soak: 500 sequential step operations across 10 sessions", () => {
    it("completes within 10 seconds", async () => {
      const SESSIONS = 10;
      const STEPS_PER_SESSION = 50; // 10 × 50 = 500 total ops

      const manifests = Array.from({ length: SESSIONS }, (_, i) =>
        buildManifest(`soak-${i}`, STEPS_PER_SESSION),
      );
      const ids = (await Promise.all(
        manifests.map((m) => service.createSession(m)),
      )) as string[];

      const start = Date.now();

      for (const id of ids) {
        for (let s = 0; s < STEPS_PER_SESSION; s++) {
          await service.stepForward(id);
        }
      }

      const elapsed = Date.now() - start;
      expect(elapsed).toBeLessThan(10_000);

      // Verify final positions
      for (const id of ids) {
        const state = (await service.getSessionState(id)) as { currentStepIndex: number };
        expect(state.currentStepIndex).toBe(STEPS_PER_SESSION - 1);
      }
    }, 15_000 /* test timeout */);
  });

  describe("Out-of-order parallel step navigation", () => {
    it("sessions advanced at different rates maintain independent state", async () => {
      const manifests = Array.from({ length: 5 }, (_, i) =>
        buildManifest(`ooo-${i}`, 20),
      );
      const ids = (await Promise.all(
        manifests.map((m) => service.createSession(m)),
      )) as string[];

      // Advance each session to a different target step
      const targets = [0, 4, 9, 14, 19];
      await Promise.all(
        ids.map((id, idx) => service.seekToStep(id, targets[idx]!)),
      );

      const states = await Promise.all(
        ids.map((id) => service.getSessionState(id) as Promise<{ currentStepIndex: number }>),
      );
      states.forEach((state, idx) => {
        expect(state.currentStepIndex).toBe(targets[idx]);
      });
    });
  });
});
