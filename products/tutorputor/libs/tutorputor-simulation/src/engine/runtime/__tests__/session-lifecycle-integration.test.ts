/**
 * Simulation Session Lifecycle Integration Tests
 *
 * Validates concurrent session isolation, session termination and resource
 * cleanup, state persistence through serialise/deserialise round-trips, and
 * bulk step navigation across large manifests.
 *
 * @doc.type test
 * @doc.purpose Integration coverage for session lifecycle guarantees of SimRuntimeService
 * @doc.layer product
 * @doc.pattern IntegrationTest
 *
 * Requirement IDs: TPUT-FR-SIM-007 … TPUT-FR-SIM-010
 */

import { describe, it, expect, beforeEach, vi } from "vitest";

// ---------------------------------------------------------------------------
// Redis mock – in-memory Map, isolated per test via beforeEach reset
// ---------------------------------------------------------------------------
vi.mock("ioredis", () => ({
  default: class MockRedis {
    private store = new Map<string, string>();
    async get(key: string): Promise<string | null> {
      return this.store.get(key) ?? null;
    }
    async set(key: string, value: string): Promise<"OK"> {
      this.store.set(key, value);
      return "OK";
    }
    async del(key: string): Promise<number> {
      this.store.delete(key);
      return 1;
    }
    async expire(_key: string, _secs: number): Promise<number> {
      return 1;
    }
    async keys(_pattern: string): Promise<string[]> {
      return [...this.store.keys()];
    }
    async quit(): Promise<"OK"> {
      return "OK";
    }
    on(_event: string, _cb: unknown): this {
      return this;
    }
  },
}));

import { createRuntimeService } from "../service";
import type {
  SimulationManifest,
  SimEntity,
  SimulationStep,
} from "@tutorputor/contracts/v1/simulation/types";

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function buildStep(orderIndex: number): SimulationStep {
  return {
    id: `step-${orderIndex}` as any,
    orderIndex,
    actions: [],
  };
}

function buildManifest(
  stepCount: number,
  id = "lifecycle-test",
): SimulationManifest {
  const steps = Array.from({ length: stepCount }, (_, i) => buildStep(i));
  const entity: SimEntity = {
    id: "node-0",
    type: "array_element",
    x: 0,
    y: 0,
    visual: { fill: "#4A90D9" },
    data: { value: 42 },
  } as unknown as SimEntity;

  return {
    id: id as any,
    version: "1.0",
    domain: "CS_DISCRETE" as any,
    title: "Lifecycle Test Simulation",
    description: "Used for session lifecycle integration tests",
    authorId: "test-author" as any,
    tenantId: "tenant-test" as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: "1.0",
    initialEntities: [entity],
    steps,
    domainMetadata: { domain: "CS_DISCRETE" as any },
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("SimRuntimeService – Session Lifecycle Integration", () => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let service: any;

  beforeEach(() => {
    // Fresh service (and fresh Redis mock instance) for every test
    service = createRuntimeService();
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-SIM-007  Concurrent sessions do not interfere with each other
  // -----------------------------------------------------------------------
  describe("TPUT-FR-SIM-007: Concurrent session isolation", () => {
    it("independently advanced sessions maintain separate step indices", async () => {
      const manifestA = buildManifest(10, "manifest-a");
      const manifestB = buildManifest(10, "manifest-b");

      const sessionA = await service.createSession(manifestA);
      const sessionB = await service.createSession(manifestB);

      // Advance A three steps, leave B at initial position
      await service.stepForward(sessionA);
      await service.stepForward(sessionA);
      const kfA = await service.stepForward(sessionA);

      const stateB = await service.getSessionState(sessionB);

      // A must be at step index 2; B must still be at -1
      expect((kfA as any).stepIndex).toBe(2);
      expect((stateB as any).currentStepIndex).toBe(-1);
    });

    it("terminating one session does not affect another session", async () => {
      const manifest = buildManifest(5);
      const sessionA = await service.createSession(manifest);
      const sessionB = await service.createSession(manifest);

      await service.stepForward(sessionA);

      // Terminate A
      await service.terminateSession(sessionA);

      // B must still be navigable
      const kf = await service.stepForward(sessionB);
      expect((kf as any).stepIndex).toBe(0);
    });

    it("multiple sessions created from the same manifest receive unique IDs", async () => {
      const manifest = buildManifest(3);
      const ids = await Promise.all(
        Array.from({ length: 5 }, () => service.createSession(manifest)),
      );

      const unique = new Set(ids);
      expect(unique.size).toBe(5);
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-SIM-008  Session termination frees resources
  // -----------------------------------------------------------------------
  describe("TPUT-FR-SIM-008: Session termination and resource cleanup", () => {
    it("terminateSession resolves without error", async () => {
      const session = await service.createSession(buildManifest(3));
      await expect(service.terminateSession(session)).resolves.not.toThrow();
    });

    it("accessing a terminated session rejects with a meaningful error", async () => {
      const session = await service.createSession(buildManifest(3));
      await service.terminateSession(session);

      await expect(service.stepForward(session)).rejects.toThrow();
    });

    it("a terminated session does not appear accessible after cleanup", async () => {
      const session = await service.createSession(buildManifest(3));
      await service.terminateSession(session);

      await expect(service.getSessionState(session)).rejects.toThrow();
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-SIM-009  State persistence through serialise/deserialise
  // -----------------------------------------------------------------------
  describe("TPUT-FR-SIM-009: State persistence across step transitions", () => {
    it("state retrieved after steps reflects the correct step index", async () => {
      const manifest = buildManifest(8);
      const session = await service.createSession(manifest);

      await service.stepForward(session);
      await service.stepForward(session);
      await service.stepForward(session);

      const state = (await service.getSessionState(session)) as any;
      expect(state.currentStepIndex).toBe(2);
    });

    it("stepping backward after forward steps reflects lower step index in persisted state", async () => {
      const manifest = buildManifest(6);
      const session = await service.createSession(manifest);

      await service.stepForward(session);
      await service.stepForward(session);
      await service.stepForward(session);
      await service.stepBackward(session);

      const state = (await service.getSessionState(session)) as any;
      expect(state.currentStepIndex).toBe(1);
    });

    it("seeking directly to a step is persisted correctly", async () => {
      const manifest = buildManifest(10);
      const session = await service.createSession(manifest);

      await service.seekToStep(session, 7);

      const state = (await service.getSessionState(session)) as any;
      expect(state.currentStepIndex).toBe(7);
    });

    it("returning to initial position (seek to -1 or 0 from beginning) is persisted", async () => {
      const manifest = buildManifest(5);
      const session = await service.createSession(manifest);

      await service.stepForward(session);
      await service.stepForward(session);
      await service.seekToStep(session, -1);

      const state = (await service.getSessionState(session)) as any;
      expect(state.currentStepIndex).toBe(-1);
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-SIM-010  Large manifest navigation completes without error
  // -----------------------------------------------------------------------
  describe("TPUT-FR-SIM-010: Bulk step navigation over large manifests", () => {
    it("stepping through 100 steps completes and lands on the final step", async () => {
      const stepCount = 100;
      const manifest = buildManifest(stepCount);
      const session = await service.createSession(manifest);

      let lastKf: unknown;
      for (let i = 0; i < stepCount; i++) {
        lastKf = await service.stepForward(session);
      }

      expect((lastKf as any).stepIndex).toBe(stepCount - 1);
    });

    it("stepping beyond the final step clamps at the last step", async () => {
      const stepCount = 5;
      const manifest = buildManifest(stepCount);
      const session = await service.createSession(manifest);

      for (let i = 0; i < stepCount + 3; i++) {
        await service.stepForward(session);
      }

      const state = (await service.getSessionState(session)) as any;
      expect(state.currentStepIndex).toBe(stepCount - 1);
    });

    it("seekToStep beyond bounds clamps to the last valid step", async () => {
      const manifest = buildManifest(5);
      const session = await service.createSession(manifest);

      const kf = (await service.seekToStep(session, 9999)) as any;
      expect(kf.stepIndex).toBe(4);
    });

    it("seeking back from the middle and forward again reaches the correct step", async () => {
      const manifest = buildManifest(20);
      const session = await service.createSession(manifest);

      await service.seekToStep(session, 10);
      await service.seekToStep(session, 2);
      const kf = (await service.seekToStep(session, 15)) as any;

      expect(kf.stepIndex).toBe(15);
    });
  });
});
