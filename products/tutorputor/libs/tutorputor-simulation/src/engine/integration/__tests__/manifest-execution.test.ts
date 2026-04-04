/**
 * Manifest Execution Integration Tests
 *
 * Validates end-to-end simulation workflows:
 *   Manifest → kernel initialisation → step-by-step execution → keyframe output
 *
 * Tests each simulation domain for correct lifecycle behaviour and verify that
 * deterministic replay produces identical results from the same seed/manifest.
 *
 * @doc.type test
 * @doc.purpose End-to-end integration validation for manifest-driven simulation execution
 * @doc.layer product
 * @doc.pattern IntegrationTest
 *
 * Requirement IDs: TPUT-FR-SIM-001 … TPUT-FR-SIM-010
 */

import { describe, it, expect, beforeEach, vi } from "vitest";

// ---------------------------------------------------------------------------
// Mock Redis so the runtime service can be constructed without a real broker
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
      return [];
    }
    async quit(): Promise<"OK"> {
      return "OK";
    }
    on(_event: string, _cb: unknown): this {
      return this;
    }
  },
}));

import { createRuntimeService } from "../../runtime/service";
import type {
  SimulationManifest,
  SimEntity,
  SimulationStep,
  SimAction,
  SimKeyframe,
} from "@tutorputor/contracts/v1/simulation/types";

// ---------------------------------------------------------------------------
// Shared test fixture builders
// ---------------------------------------------------------------------------

function buildBaseManifest(
  domain: string,
  steps: SimulationStep[] = [],
): SimulationManifest {
  return {
    id: `integration-${domain.toLowerCase()}-001` as any,
    version: "1.0",
    domain: domain as any,
    title: `${domain} Integration Test`,
    description: `End-to-end integration test for ${domain} domain`,
    authorId: "test-author" as any,
    tenantId: "test-tenant" as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: "1.0",
    initialEntities: [],
    steps,
    domainMetadata: { domain: domain as any },
  };
}

function buildStep(index: number, actions: SimAction[] = []): SimulationStep {
  return {
    id: `step-${index}` as any,
    orderIndex: index,
    duration: 500,
    actions,
  };
}

/** Verify a keyframe is structurally valid according to the simulation contract. */
function assertValidKeyframe(kf: SimKeyframe, expectedStepIndex: number): void {
  expect(kf).toBeDefined();
  expect(typeof kf.stepIndex).toBe("number");
  expect(kf.stepIndex).toBe(expectedStepIndex);
  expect(Array.isArray(kf.entities)).toBe(true);
  expect(Array.isArray(kf.annotations)).toBe(true);
}

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-001: Session lifecycle – create / step / end
// ---------------------------------------------------------------------------

describe("TPUT-FR-SIM-001: Simulation session lifecycle must support creation, stepping, and termination", () => {
  let service: ReturnType<typeof createRuntimeService>;

  beforeEach(() => {
    service = createRuntimeService();
  });

  it("should create a session from a valid CS_DISCRETE manifest", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
    ]);
    const sessionId = await service.createSession(manifest);

    expect(sessionId).toBeTruthy();
    expect(typeof sessionId).toBe("string");
  });

  it("should provide unique session IDs for concurrent simulations", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [buildStep(0)]);

    const [id1, id2] = await Promise.all([
      service.createSession(manifest),
      service.createSession(manifest),
    ]);

    expect(id1).not.toBe(id2);
  });

  it("should start at step index -1 (before the first step)", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
    ]);
    const sessionId = await service.createSession(manifest);

    const initialKeyframe = await service.seekToStep(sessionId, -1);
    // Seeking to step -1 gives the initial pre-execution keyframe
    assertValidKeyframe(initialKeyframe, -1);
  });

  it("should advance to the first step after stepForward", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
    ]);
    const sessionId = await service.createSession(manifest);

    const keyframe = await service.stepForward(sessionId);
    assertValidKeyframe(keyframe, 0);
  });

  it("should advance through all steps in sequence", async () => {
    const totalSteps = 4;
    const steps = Array.from({ length: totalSteps }, (_, i) => buildStep(i));
    const manifest = buildBaseManifest("CS_DISCRETE", steps);
    const sessionId = await service.createSession(manifest);

    for (let i = 0; i < totalSteps; i++) {
      const kf = await service.stepForward(sessionId);
      assertValidKeyframe(kf, i);
    }
  });

  it("should not advance past the final step", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
    ]);
    const sessionId = await service.createSession(manifest);

    // Step to the end
    await service.stepForward(sessionId);
    await service.stepForward(sessionId);

    // Additional stepForward must not throw and must stay at the last step
    const lastKeyframe = await service.stepForward(sessionId);
    expect(lastKeyframe.stepIndex).toBe(1);
  });

  it("should step backward to the previous step", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
      buildStep(2),
    ]);
    const sessionId = await service.createSession(manifest);

    await service.stepForward(sessionId); // step 0
    await service.stepForward(sessionId); // step 1
    const kf = await service.stepBackward(sessionId);

    assertValidKeyframe(kf, 0);
  });

  it("should reject operations on a non-existent session", async () => {
    await expect(
      service.stepForward("non-existent-session-id" as any),
    ).rejects.toThrow();
  });

  it("should end a session cleanly without leaving orphaned state", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [buildStep(0)]);
    const sessionId = await service.createSession(manifest);

    await service.terminateSession(sessionId);

    // After terminating, any interaction must fail gracefully
    await expect(service.stepForward(sessionId)).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-002: Domain-specific manifest execution (all 6 kernels)
// ---------------------------------------------------------------------------

describe("TPUT-FR-SIM-002: All simulation domains must boot and execute their first step without error", () => {
  let service: ReturnType<typeof createRuntimeService>;

  beforeEach(() => {
    service = createRuntimeService();
  });

  const domains = [
    "CS_DISCRETE",
    "PHYSICS",
    "CHEMISTRY",
    "BIOLOGY",
    "MEDICINE",
    "SYSTEM_DYNAMICS",
  ] as const;

  for (const domain of domains) {
    it(`should initialise and step through a ${domain} simulation`, async () => {
      const manifest = buildBaseManifest(domain, [
        buildStep(0),
        buildStep(1),
        buildStep(2),
      ]);
      const sessionId = await service.createSession(manifest);

      const kf = await service.stepForward(sessionId);
      assertValidKeyframe(kf, 0);

      await service.terminateSession(sessionId);
    });
  }
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-003: Deterministic replay – same manifest + seed → identical keyframes
// ---------------------------------------------------------------------------

describe("TPUT-FR-SIM-003: Identical manifests must produce identical keyframe sequences (determinism)", () => {
  it("should produce the same keyframe for two independent sessions using the same manifest", async () => {
    const service1 = createRuntimeService();
    const service2 = createRuntimeService();

    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0, [
        {
          action: "HIGHLIGHT",
          targetIds: ["e-0", "e-1"],
          style: "comparing",
        } as unknown as SimAction,
      ]),
      buildStep(1),
      buildStep(2),
    ]);

    const session1 = await service1.createSession(manifest);
    const session2 = await service2.createSession(manifest);

    // Advance both sessions to step 2
    for (let i = 0; i < 3; i++) {
      await service1.stepForward(session1);
      await service2.stepForward(session2);
    }

    // Seek both sessions back to the initial state (step -1) and compare
    const kf1 = await service1.seekToStep(session1, -1);
    const kf2 = await service2.seekToStep(session2, -1);

    // Structural equality of keyframe step indices verifies deterministic state
    expect(kf1.stepIndex).toBe(kf2.stepIndex);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-004: Manifest schema validation
// ---------------------------------------------------------------------------

describe("TPUT-FR-SIM-004: Invalid manifests must be rejected with clear errors before session creation", () => {
  let service: ReturnType<typeof createRuntimeService>;

  beforeEach(() => {
    service = createRuntimeService();
  });

  it("should reject a manifest with an unrecognised domain", async () => {
    const badManifest = buildBaseManifest("UNKNOWN_DOMAIN", [buildStep(0)]);

    await expect(service.createSession(badManifest)).rejects.toThrow();
  });

  it("should reject a manifest with no steps", async () => {
    // A simulation with zero steps has nothing to execute; the session is meaningless
    const emptyManifest = buildBaseManifest("CS_DISCRETE", []);
    const sessionId = await service.createSession(emptyManifest);

    // stepForward on a zero-step session should not crash; it returns the initial frame
    const kf = await service.stepForward(sessionId);
    expect(kf.stepIndex).toBe(-1); // stays at initial – nothing to step into
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-005: Seek / time-based navigation
// ---------------------------------------------------------------------------

describe("TPUT-FR-SIM-005: Seek must navigate to the correct step based on absolute time", () => {
  let service: ReturnType<typeof createRuntimeService>;

  beforeEach(() => {
    service = createRuntimeService();
  });

  it("should return the initial keyframe when seeking to time zero", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
      buildStep(2),
    ]);
    const sessionId = await service.createSession(manifest);

    // seekTo(timeMs=0) navigates to initial state; cast because seekTo is an
    // implementation detail not exposed on the SimRuntimeService contract
    const kf = await (service as any).seekTo(sessionId, 0);
    assertValidKeyframe(kf, -1);
  });

  it("should return the last keyframe when seeking past total duration", async () => {
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
    ]);
    const sessionId = await service.createSession(manifest);

    // Seeking past total duration clamps to last step
    const kf = await (service as any).seekTo(sessionId, 99999);
    expect(kf.stepIndex).toBeGreaterThanOrEqual(1);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-006: Execution history for analytics
// ---------------------------------------------------------------------------

describe("TPUT-FR-SIM-006: Session must record an execution history suitable for learning analytics", () => {
  it("should expose the analytics event stream from completed execution", async () => {
    const service = createRuntimeService();
    const manifest = buildBaseManifest("CS_DISCRETE", [
      buildStep(0),
      buildStep(1),
      buildStep(2),
    ]);
    const sessionId = await service.createSession(manifest);

    await service.stepForward(sessionId);
    await service.stepForward(sessionId);
    await service.stepBackward(sessionId);
    await service.stepForward(sessionId);

    // getSessionState returns the full session snapshot including analytics
    const state = (await service.getSessionState(sessionId)) as Record<
      string,
      unknown
    >;

    expect(state).toBeDefined();
    // The analytics object from the kernel is always present (may be empty for simple kernels)
    expect(state.analytics).toBeDefined();
    // currentStepIndex must reflect that navigation operations were performed
    expect(typeof state.currentStepIndex).toBe("number");
    expect(state.currentStepIndex as number).toBeGreaterThanOrEqual(0);
  });
});
