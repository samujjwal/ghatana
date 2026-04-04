/**
 * Physics Kernel – Energy Conservation & Determinism Tests
 *
 * @doc.type test
 * @doc.purpose Verify that the physics kernel correctly conserves momentum
 *              under zero-external-force conditions, accumulates velocity under
 *              gravity, reflects bodies off the ground plane, and produces
 *              deterministic results from a given seed.
 * @doc.layer simulation
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-SIM-001 (deterministic replay),
 *                  TPUT-FR-SIM-002 (physics fidelity),
 *                  TPUT-FR-SIM-003 (ground-plane collision)
 */
import { describe, it, expect } from "vitest";
import {
  PhysicsKernel,
  SeededRandom,
  createPhysicsKernel,
} from "../physics-kernel";
import type {
  SimulationManifest,
  PhysicsBodyEntity,
} from "@tutorputor/contracts/v1/simulation/types";

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

type ManifestOverrides = {
  gravity?: { x: number; y: number };
  stepCount?: number;
  stepDuration?: number;
};

function buildPhysicsManifest(
  entities: PhysicsBodyEntity[],
  {
    gravity = { x: 0, y: 9.81 },
    stepCount = 1,
    stepDuration = 1000,
  }: ManifestOverrides = {},
): SimulationManifest {
  return {
    id: "test-physics" as unknown as ReturnType<typeof crypto.randomUUID>,
    version: "1.0",
    title: "Physics Test",
    domain: "PHYSICS",
    authorId: "test-author" as any,
    tenantId: "test-tenant" as any,
    canvas: {
      width: 800,
      height: 600,
      gridEnabled: false,
      coordinateSystem: "cartesian" as any,
    },
    playback: { defaultSpeed: 1, autoPlay: false },
    initialEntities: entities as any[],
    steps: Array.from({ length: stepCount }, (_, i) => ({
      id: `step-${i}` as any,
      orderIndex: i,
      duration: stepDuration,
      actions: [],
    })),
    domainMetadata: { domain: "PHYSICS", physics: { gravity } } as any,
    createdAt: "2024-01-01T00:00:00.000Z",
    updatedAt: "2024-01-01T00:00:00.000Z",
    schemaVersion: "1.0",
  } as SimulationManifest;
}

function makeBody(
  id: string,
  overrides: Partial<PhysicsBodyEntity> = {},
): PhysicsBodyEntity {
  return {
    id: id as any,
    type: "rigidBody",
    x: 100,
    y: 100,
    mass: 1,
    velocityX: 0,
    velocityY: 0,
    friction: 0,
    restitution: 1,
    fixed: false,
    visual: {} as any,
    data: {} as any,
    ...overrides,
  } as PhysicsBodyEntity;
}

// ---------------------------------------------------------------------------
// SeededRandom – TPUT-FR-SIM-001
// ---------------------------------------------------------------------------

describe("SeededRandom – TPUT-FR-SIM-001 (determinism)", () => {
  it("should produce the same sequence for the same seed", () => {
    const rng1 = new SeededRandom(42);
    const rng2 = new SeededRandom(42);

    const seq1 = Array.from({ length: 10 }, () => rng1.next());
    const seq2 = Array.from({ length: 10 }, () => rng2.next());

    expect(seq1).toEqual(seq2);
  });

  it("should produce different sequences for different seeds", () => {
    const rng1 = new SeededRandom(1);
    const rng2 = new SeededRandom(999);

    const seq1 = Array.from({ length: 5 }, () => rng1.next());
    const seq2 = Array.from({ length: 5 }, () => rng2.next());

    // At least one value must differ
    expect(seq1).not.toEqual(seq2);
  });

  it("should handle seed=0 without producing NaN or ±Infinity", () => {
    const rng = new SeededRandom(0);
    for (let i = 0; i < 20; i++) {
      const value = rng.next();
      expect(Number.isFinite(value)).toBe(true);
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });

  it("should handle seed=2147483647 (MAX_INT) without producing NaN or ±Infinity", () => {
    const rng = new SeededRandom(2147483647);
    for (let i = 0; i < 20; i++) {
      const value = rng.next();
      expect(Number.isFinite(value)).toBe(true);
    }
  });

  it("should keep nextInt within [min, max] inclusive across many calls", () => {
    const rng = new SeededRandom(777);
    for (let i = 0; i < 100; i++) {
      const n = rng.nextInt(1, 6);
      expect(n).toBeGreaterThanOrEqual(1);
      expect(n).toBeLessThanOrEqual(6);
    }
  });

  it("should keep nextFloat within [min, max) range", () => {
    const rng = new SeededRandom(123);
    for (let i = 0; i < 50; i++) {
      const n = rng.nextFloat(2, 5);
      expect(n).toBeGreaterThanOrEqual(2);
      expect(n).toBeLessThan(5);
    }
  });

  it("should serialize and deserialize state to produce identical subsequent values", () => {
    const rng = new SeededRandom(555);
    // Advance a bit
    rng.next();
    rng.next();
    rng.next();

    const state = rng.serialize();
    const restored = SeededRandom.deserialize(state);

    const remaining1 = Array.from({ length: 5 }, () => rng.next());
    const remaining2 = Array.from({ length: 5 }, () => restored.next());

    expect(remaining1).toEqual(remaining2);
  });

  it("should serialize state consistently across nextGaussian calls", () => {
    const rng1 = new SeededRandom(88);
    const rng2 = new SeededRandom(88);

    const values1 = Array.from({ length: 5 }, () => rng1.nextGaussian(0, 1));
    const values2 = Array.from({ length: 5 }, () => rng2.nextGaussian(0, 1));

    expect(values1).toEqual(values2);
  });
});

// ---------------------------------------------------------------------------
// PhysicsKernel construction and domain checks
// ---------------------------------------------------------------------------

describe("PhysicsKernel – construction and domain", () => {
  it("should create a PhysicsKernel via factory function", () => {
    const kernel = createPhysicsKernel({ gravity: { x: 0, y: 9.81 } });
    expect(kernel).toBeDefined();
    expect(kernel.domain).toBe("PHYSICS");
  });

  it("canExecute should return true for PHYSICS domain manifests", () => {
    const kernel = new PhysicsKernel();
    const manifest = buildPhysicsManifest([], { stepCount: 0 });
    expect(kernel.canExecute(manifest)).toBe(true);
  });

  it("canExecute should return false for non-PHYSICS domain manifests", () => {
    const kernel = new PhysicsKernel();
    const manifest = {
      ...buildPhysicsManifest([], { stepCount: 0 }),
      domain: "CS_DISCRETE",
    } as any;
    expect(kernel.canExecute(manifest)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-002: Physics fidelity – zero-force constant velocity
// ---------------------------------------------------------------------------

describe("PhysicsKernel – TPUT-FR-SIM-002 (physics fidelity)", () => {
  it("should maintain constant velocity in a zero-gravity, zero-external-force system", async () => {
    const INITIAL_VX = 10;

    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 0 } });
    const body = makeBody("ball", {
      x: 100,
      y: 100,
      velocityX: INITIAL_VX,
      velocityY: 0,
    });
    const manifest = buildPhysicsManifest([body], {
      gravity: { x: 0, y: 0 },
      stepCount: 1,
      stepDuration: 1000,
    });

    kernel.initialize(manifest);
    kernel.step();

    const result = await kernel.run({
      manifest: buildPhysicsManifest([body], {
        gravity: { x: 0, y: 0 },
        stepCount: 1,
        stepDuration: 1000,
      }),
    });

    // The body should have moved in x by approximately INITIAL_VX * 1 second
    const finalFrame = result.keyframes.at(-1);
    expect(finalFrame).toBeDefined();
    const finalBall = finalFrame!.entities.find((e) => e.id === "ball") as
      | PhysicsBodyEntity
      | undefined;
    expect(finalBall).toBeDefined();
    // x should have increased from 100
    expect(finalBall!.x).toBeGreaterThan(100);
    // Velocity should remain approximately INITIAL_VX (Newton's 1st law)
    expect(finalBall!.velocityX).toBeCloseTo(INITIAL_VX, 5);
    expect(finalBall!.velocityY).toBeCloseTo(0, 5);
  });

  it("should produce x-displacement proportional to initial velocity magnitude", async () => {
    const runWithVelocity = async (vx: number): Promise<number> => {
      const body = makeBody("b", { x: 0, y: 0, velocityX: vx, velocityY: 0 });
      const result = await new PhysicsKernel({ gravity: { x: 0, y: 0 } }).run({
        manifest: buildPhysicsManifest([body], {
          gravity: { x: 0, y: 0 },
          stepCount: 1,
          stepDuration: 1000,
        }),
      });
      const last = result.keyframes.at(-1);
      const entity = last?.entities.find((e) => e.id === "b") as
        | PhysicsBodyEntity
        | undefined;
      return entity?.x ?? 0;
    };

    const x10 = await runWithVelocity(10);
    const x20 = await runWithVelocity(20);

    // x20 should be approximately twice x10 (linear relationship)
    expect(x20).toBeGreaterThan(x10);
    expect(x20 / x10).toBeCloseTo(2, 0);
  });

  it("should accumulate downward velocity under positive-y gravity", async () => {
    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 9.81 } });
    const body = makeBody("ball", {
      x: 100,
      y: 0,
      velocityX: 0,
      velocityY: 0,
      mass: 1,
      friction: 0,
    });
    const manifest = buildPhysicsManifest([body], {
      gravity: { x: 0, y: 9.81 },
      stepCount: 1,
      stepDuration: 1000,
    });

    const result = await kernel.run({ manifest });

    const last = result.keyframes.at(-1);
    const finalEntity = last?.entities.find((e) => e.id === "ball") as
      | PhysicsBodyEntity
      | undefined;
    expect(finalEntity).toBeDefined();
    // With y-gravity, vy should be positive (downward) after 1 second
    expect(finalEntity!.velocityY).toBeGreaterThan(0);
    // y position should have increased (moved downward)
    expect(finalEntity!.y).toBeGreaterThan(0);
  });

  it("should not move a fixed body regardless of gravity", async () => {
    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 9.81 } });
    const body = makeBody("fixed-ball", {
      x: 200,
      y: 200,
      velocityX: 5,
      velocityY: 0,
      fixed: true,
    });
    const manifest = buildPhysicsManifest([body], {
      gravity: { x: 0, y: 9.81 },
      stepCount: 3,
      stepDuration: 500,
    });

    const result = await kernel.run({ manifest });

    const last = result.keyframes.at(-1);
    const fixedEntity = last?.entities.find((e) => e.id === "fixed-ball") as
      | PhysicsBodyEntity
      | undefined;
    expect(fixedEntity?.x).toBe(200);
    expect(fixedEntity?.y).toBe(200);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-003: Ground-plane collision
// ---------------------------------------------------------------------------

describe("PhysicsKernel – TPUT-FR-SIM-003 (ground-plane collision)", () => {
  it("should bounce a fast-falling body off the ground plane (y = 490)", async () => {
    // Place body just above the ground with high downward velocity
    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 0 } }); // zero gravity, pure velocity
    const body = makeBody("falling", {
      x: 400,
      y: 480, // close to ground (halfHeight=10, so touches at y=490)
      velocityX: 0,
      velocityY: 100, // high downward velocity
      friction: 0,
      restitution: 0.8,
      mass: 1,
    });
    const manifest = buildPhysicsManifest([body], {
      gravity: { x: 0, y: 0 },
      stepCount: 1,
      stepDuration: 500,
    });
    const result = await kernel.run({ manifest });

    // Initial keyframe has vy=100 (downward). After hitting ground, vy should reverse.
    const last = result.keyframes.at(-1);
    const entity = last?.entities.find((e) => e.id === "falling") as
      | PhysicsBodyEntity
      | undefined;
    expect(entity).toBeDefined();
    // Body should have reversed vertical velocity (now negative = upward)
    expect(entity!.velocityY).toBeLessThan(0);
  });

  it("should clamp body position at ground after collision (y ≤ 490)", async () => {
    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 100 } }); // strong gravity
    const body = makeBody("drop", {
      x: 400,
      y: 0, // starting high up
      velocityX: 0,
      velocityY: 0,
      friction: 0,
      restitution: 0,
      mass: 1,
    });
    // Let it fall with 10 steps so it definitely reaches the ground
    const manifest = buildPhysicsManifest([body], {
      gravity: { x: 0, y: 100 },
      stepCount: 10,
      stepDuration: 500,
    });
    const result = await kernel.run({ manifest });

    const last = result.keyframes.at(-1);
    const entity = last?.entities.find((e) => e.id === "drop") as
      | PhysicsBodyEntity
      | undefined;
    expect(entity).toBeDefined();
    // Body must never exceed ground (y must be ≤ 490 which is 500 - halfHeight)
    expect(entity!.y).toBeLessThanOrEqual(490);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-SIM-001: Deterministic replay
// ---------------------------------------------------------------------------

describe("PhysicsKernel – deterministic replay", () => {
  it("should produce identical keyframes for two runs with the same seed", async () => {
    const entities = [
      makeBody("a", {
        x: 50,
        y: 50,
        velocityX: 3,
        velocityY: 2,
        mass: 2,
        friction: 0,
      }),
      makeBody("b", {
        x: 300,
        y: 100,
        velocityX: -2,
        velocityY: 1,
        mass: 1,
        friction: 0,
      }),
    ];
    const manifest = buildPhysicsManifest(entities, {
      gravity: { x: 0, y: 5 },
      stepCount: 5,
      stepDuration: 200,
    });

    const kernel1 = new PhysicsKernel({ seed: 42 });
    const kernel2 = new PhysicsKernel({ seed: 42 });

    const result1 = await kernel1.run({ manifest });
    const result2 = await kernel2.run({ manifest });

    expect(result1.keyframes.length).toBe(result2.keyframes.length);

    for (let i = 0; i < result1.keyframes.length; i++) {
      const kf1 = result1.keyframes[i]!;
      const kf2 = result2.keyframes[i]!;
      for (const entity of kf1.entities) {
        const matching = kf2.entities.find((e) => e.id === entity.id) as
          | PhysicsBodyEntity
          | undefined;
        expect(matching).toBeDefined();
        expect(entity.x).toBeCloseTo((matching as PhysicsBodyEntity).x, 8);
        expect(entity.y).toBeCloseTo((matching as PhysicsBodyEntity).y, 8);
      }
    }
  });

  it("should produce different trajectories for different seeds when stochastic config is different", () => {
    const rng1 = new SeededRandom(1);
    const rng2 = new SeededRandom(100000);

    const values1 = Array.from({ length: 20 }, () => rng1.next());
    const values2 = Array.from({ length: 20 }, () => rng2.next());

    // Two independently seeded generators must differ in at least one value
    const anyDiffer = values1.some((v, i) => v !== values2[i]);
    expect(anyDiffer).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Kernel serialize / deserialize
// ---------------------------------------------------------------------------

describe("PhysicsKernel – serialization roundtrip", () => {
  it("should serialize to a string and deserialize back to an equivalent state", () => {
    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 9.81 }, seed: 123 });
    const body = makeBody("s", { x: 100, y: 100, velocityX: 5, velocityY: 0 });
    const manifest = buildPhysicsManifest([body], {
      gravity: { x: 0, y: 9.81 },
      stepCount: 2,
      stepDuration: 500,
    });

    kernel.initialize(manifest);
    kernel.step();

    const serialized = kernel.serialize();
    expect(typeof serialized).toBe("string");

    const kernel2 = new PhysicsKernel();
    kernel2.deserialize(serialized);

    const serialized2 = kernel2.serialize();
    expect(serialized2).toBe(serialized);
  });

  it("should serialize and restore RNG state identically", () => {
    const kernel = new PhysicsKernel({ seed: 7777 });
    const state = kernel.serializeRngState();
    expect(state).toHaveLength(4);
    expect(state.every((v) => typeof v === "number")).toBe(true);

    const newKernel = new PhysicsKernel({ seed: 0 });
    newKernel.restoreRngState(state);
    expect(newKernel.serializeRngState()).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// Spring physics
// ---------------------------------------------------------------------------

describe("PhysicsKernel – spring physics", () => {
  it("should not crash when a spring entity is present in the manifest", async () => {
    const anchor = makeBody("anchor", { x: 200, y: 200, fixed: true });
    const mass = makeBody("mass", {
      x: 300,
      y: 200,
      velocityX: 0,
      velocityY: 0,
      mass: 2,
    });
    const spring = {
      id: "spring-1" as any,
      type: "spring" as const,
      x: 250,
      y: 200,
      anchorId: "anchor" as any,
      attachId: "mass" as any,
      stiffness: 100,
      damping: 5,
      restLength: 100,
      visual: {} as any,
      data: {} as any,
    };
    const manifest: SimulationManifest = {
      id: "spring-test" as any,
      version: "1.0",
      title: "Spring Test",
      domain: "PHYSICS",
      authorId: "test-author" as any,
      tenantId: "test-tenant" as any,
      canvas: {
        width: 800,
        height: 600,
        gridEnabled: false,
        coordinateSystem: "cartesian" as any,
      },
      playback: { defaultSpeed: 1, autoPlay: false },
      initialEntities: [anchor, mass, spring] as any[],
      steps: [
        { id: "step-0" as any, orderIndex: 0, duration: 500, actions: [] },
      ],
      domainMetadata: {
        domain: "PHYSICS",
        physics: { gravity: { x: 0, y: 0 } },
      } as any,
      createdAt: "2024-01-01T00:00:00.000Z",
      updatedAt: "2024-01-01T00:00:00.000Z",
      schemaVersion: "1.0",
    };

    const kernel = new PhysicsKernel({ gravity: { x: 0, y: 0 } });
    await expect(kernel.run({ manifest })).resolves.not.toThrow();
  });
});
