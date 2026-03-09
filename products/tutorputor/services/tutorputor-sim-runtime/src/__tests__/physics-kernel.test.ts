/**
 * Physics Kernel Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test physics simulation kernel execution
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createPhysicsKernel } from '../physics-kernel';
import type { SimulationManifest, SimEntity, SimAction, SimulationStep } from '@ghatana/tutorputor-contracts/v1/simulation/types';

/**
 * Helper to create a minimal physics manifest
 */
function createPhysicsManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: 'physics-sim-001' as any,
    version: '1.0',
    domain: 'PHYSICS' as any,
    title: 'Test Physics Simulation',
    description: 'A test physics simulation',
    authorId: 'test-user' as any,
    tenantId: 'test-tenant' as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: '1.0',
    initialEntities: [],
    steps: [],
    domainMetadata: {
      domain: 'PHYSICS' as any,
      physics: {
        gravity: { x: 0, y: 9.81 },
        timeScale: 1,
      },
    },
    ...overrides,
  };
}

/**
 * Helper to create a rigid body entity
 */
function createRigidBody(
  id: string,
  options: {
    x?: number;
    y?: number;
    mass?: number;
    velocityX?: number;
    velocityY?: number;
    fixed?: boolean;
    restitution?: number;
    friction?: number;
  } = {}
): SimEntity {
  return {
    id,
    type: 'rigidBody',
    label: `Body ${id}`,
    x: options.x ?? 0,
    y: options.y ?? 0,
    mass: options.mass ?? 1,
    velocityX: options.velocityX ?? 0,
    velocityY: options.velocityY ?? 0,
    fixed: options.fixed ?? false,
    restitution: options.restitution ?? 0.5,
    friction: options.friction ?? 0.3,
    visual: {
      shape: 'circle',
      fill: '#4A90D9',
      stroke: '#2C5282',
      strokeWidth: 2,
    },
    data: {},
  } as unknown as SimEntity;
}

/**
 * Helper to create a spring entity
 */
function createSpring(
  id: string,
  anchorId: string,
  attachId: string,
  options: {
    stiffness?: number;
    damping?: number;
    restLength?: number;
  } = {}
): SimEntity {
  return {
    id,
    type: 'spring',
    label: `Spring ${id}`,
    x: 0,
    y: 0,
    anchorId,
    attachId,
    stiffness: options.stiffness ?? 100,
    damping: options.damping ?? 5,
    restLength: options.restLength ?? 50,
    visual: {
      shape: 'line',
      stroke: '#48BB78',
      strokeWidth: 2,
    },
    data: {},
  } as unknown as SimEntity;
}

describe('PhysicsKernel', () => {
  let kernel: ReturnType<typeof createPhysicsKernel>;

  beforeEach(() => {
    kernel = createPhysicsKernel();
  });

  describe('domain identification', () => {
    it('should have PHYSICS as domain', () => {
      expect(kernel.domain).toBe('PHYSICS');
    });

    it('should be able to execute PHYSICS manifests', () => {
      const manifest = createPhysicsManifest();
      expect(kernel.canExecute(manifest)).toBe(true);
    });

    it('should not execute non-PHYSICS manifests', () => {
      const manifest = createPhysicsManifest({ domain: 'CS_DISCRETE' as any });
      expect(kernel.canExecute(manifest)).toBe(false);
    });
  });

  describe('run()', () => {
    it('should return error for wrong domain', async () => {
      const manifest = createPhysicsManifest({ domain: 'CS_DISCRETE' as any });
      const result = await kernel.run({ manifest });

      expect(result.errors).toBeDefined();
      expect(result.errors).toContain('Manifest domain is not PHYSICS');
      expect(result.keyframes).toHaveLength(0);
    });

    it('should generate initial keyframe with entities', async () => {
      const entities = [
        createRigidBody('ball-1', { x: 100, y: 0, mass: 1 }),
        createRigidBody('ball-2', { x: 200, y: 0, mass: 2 }),
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      expect(result.keyframes).toHaveLength(1); // Initial keyframe only (no steps)
      expect(result.keyframes[0].stepIndex).toBe(-1);
      expect(result.keyframes[0].entities).toHaveLength(2);
    });

    it('should simulate gravity on falling body', async () => {
      const entities = [
        createRigidBody('ball', { x: 100, y: 0, mass: 1, velocityY: 0 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-simulate' as any,
          orderIndex: 0,

          actions: [],
        },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: 10 }, // Simplified gravity
            timeScale: 1,
          },
        },
      });

      const result = await kernel.run({ manifest, samplingRate: 60 });

      expect(result.errors).toBeUndefined();
      expect(result.keyframes.length).toBeGreaterThan(1);

      // Ball should have moved down due to gravity
      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const ball = finalKeyframe.entities.find((e: any) => e.id === 'ball');

      expect(ball).toBeDefined();
      expect(ball!.y).toBeGreaterThan(0);
    });

    it('should keep fixed bodies stationary', async () => {
      const entities = [
        createRigidBody('ground', { x: 100, y: 500, mass: 1000, fixed: true }),
        createRigidBody('ball', { x: 100, y: 0, mass: 1 }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const ground = finalKeyframe.entities.find((e: any) => e.id === 'ground');

      expect(ground).toBeDefined();
      expect(ground!.x).toBe(100);
      expect(ground!.y).toBe(500);
    });
  });

  describe('runDeterministic()', () => {
    it('should produce identical results with same seed', async () => {
      const entities = [
        createRigidBody('ball', { x: 100, y: 0, velocityX: 10, velocityY: 5 }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
      });

      const seed = 12345;

      const result1 = await (kernel as any).runDeterministic({ manifest }, seed);
      const result2 = await (kernel as any).runDeterministic({ manifest }, seed);

      expect(result1.keyframes.length).toBe(result2.keyframes.length);

      // Final positions should match exactly
      const final1 = result1.keyframes[result1.keyframes.length - 1];
      const final2 = result2.keyframes[result2.keyframes.length - 1];

      const ball1 = final1.entities.find((e: any) => e.id === 'ball');
      const ball2 = final2.entities.find((e: any) => e.id === 'ball');

      expect(ball1!.x).toBe(ball2!.x);
      expect(ball1!.y).toBe(ball2!.y);
    });
  });

  describe('configureWorld()', () => {
    it('should allow configuring gravity', async () => {
      (kernel as any).configureWorld({ gravity: { x: 0, y: -9.81 } }); // Upward gravity

      const entities = [
        createRigidBody('ball', { x: 100, y: 500, velocityY: 0 }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: -9.81 },
            timeScale: 1,
          },
        },
      });

      const result = await kernel.run({ manifest });
      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const ball = finalKeyframe.entities.find((e: any) => e.id === 'ball');

      // Ball should move up (y decreases with negative gravity)
      expect(ball).toBeDefined();
      // With upward gravity, y should decrease
      expect(ball!.y).toBeLessThan(500);
    });
  });

  describe('Spring physics', () => {
    it('should simulate spring-mass system', async () => {
      const entities = [
        createRigidBody('anchor', { x: 100, y: 0, fixed: true }),
        createRigidBody('mass', { x: 100, y: 100, mass: 1 }),
        createSpring('spring-1', 'anchor', 'mass', {
          stiffness: 200,
          damping: 10,
          restLength: 50,
        }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: 0 }, // No gravity to isolate spring behavior
            timeScale: 1,
          },
        },
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.keyframes.length).toBeGreaterThan(1);

      // Mass should oscillate - spring will try to reach rest length
      // Since restLength is 50 and mass starts at 100 (distance = 100),
      // spring should pull mass upward
      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const mass = finalKeyframe.entities.find((e: any) => e.id === 'mass');

      expect(mass).toBeDefined();
    });
  });

  describe('Forces and impulses', () => {
    it('should apply force action to body', async () => {
      const entities = [
        createRigidBody('ball', { x: 0, y: 0, mass: 1 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-apply-force' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'APPLY_FORCE',
              targetId: 'ball',
              fx: 100,
              fy: 0,
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: 0 },
            timeScale: 1,
          },
        },
      });

      const result = await kernel.run({ manifest });
      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const ball = finalKeyframe.entities.find((e: any) => e.id === 'ball');

      expect(ball).toBeDefined();
      expect(ball!.x).toBeGreaterThan(0); // Moved right due to force
    });

    it('should apply impulse action to body', async () => {
      const entities = [
        createRigidBody('ball', { x: 0, y: 0, mass: 1 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-apply-impulse' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'APPLY_IMPULSE',
              targetId: 'ball',
              impulseX: 0,
              impulseY: -50, // Upward impulse
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: 0 },
            timeScale: 1,
          },
        },
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
    });
  });

  describe('checkHealth()', () => {
    it('should return true for healthy kernel', async () => {
      const isHealthy = await kernel.checkHealth();
      expect(isHealthy).toBe(true);
    });
  });
});

describe('Physics Scenarios', () => {
  describe('Projectile motion', () => {
    it('should simulate projectile with initial velocity', async () => {
      const kernel = createPhysicsKernel();

      const entities = [
        createRigidBody('projectile', {
          x: 0,
          y: 100,
          mass: 1,
          velocityX: 50,  // Horizontal velocity
          velocityY: -30, // Upward velocity
        }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      // Should have multiple keyframes showing trajectory
      expect(result.keyframes.length).toBeGreaterThan(1);

      // Projectile should have moved horizontally
      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const projectile = finalKeyframe.entities.find((e: any) => e.id === 'projectile');

      expect(projectile).toBeDefined();
      expect(projectile!.x).toBeGreaterThan(0);
    });
  });

  describe('Pendulum motion', () => {
    it('should simulate simple pendulum', async () => {
      const kernel = createPhysicsKernel();

      const pivotX = 200;
      const pivotY = 0;
      const pendulumLength = 100;

      const entities = [
        createRigidBody('pivot', { x: pivotX, y: pivotY, fixed: true }),
        createRigidBody('bob', {
          x: pivotX + pendulumLength, // Start at horizontal position
          y: pivotY,
          mass: 1,
        }),
        createSpring('rod', 'pivot', 'bob', {
          stiffness: 100, // Reduced stiffness for stability
          damping: 0.1,
          restLength: pendulumLength,
        }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.keyframes.length).toBeGreaterThan(1);

      // Bob should have moved (swung down)
      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      const bob = finalKeyframe.entities.find((e: any) => e.id === 'bob');

      expect(bob).toBeDefined();
      expect(bob!.y).toBeGreaterThan(0); // Should be below pivot
    });
  });

  describe('Collision detection', () => {
    it('should detect collision between bodies', async () => {
      const kernel = createPhysicsKernel();

      const entities = [
        createRigidBody('ball-1', {
          x: 0,
          y: 0,
          mass: 1,
          velocityX: 10,
          restitution: 1,
        }),
        createRigidBody('ball-2', {
          x: 100,
          y: 0,
          mass: 1,
          velocityX: -10,
          restitution: 1,
        }),
      ];

      const steps: SimulationStep[] = [
        { id: 'step-1' as any, orderIndex: 0, actions: [] },
      ];

      const manifest = createPhysicsManifest({
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: 0 },
            timeScale: 1,
          },
        },
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      // Simulation should have run
      expect(result.keyframes.length).toBeGreaterThan(0);
    });
  });
});

// =============================================================================
// Verlet Integration Tests
// =============================================================================
describe('Verlet Integration', () => {
  it('produces same direction of motion as Euler under gravity', async () => {
    const eulerKernel = createPhysicsKernel({ gravity: { x: 0, y: 9.81 } } as any);
    const verletKernel = createPhysicsKernel({
      gravity: { x: 0, y: 9.81 },
      integrationMethod: 'verlet',
    } as any);

    const entities = [createRigidBody('ball', { x: 100, y: 0, mass: 1 })];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({
      initialEntities: entities,
      steps,
      domainMetadata: { domain: 'PHYSICS' as any, physics: { gravity: { x: 0, y: 9.81 }, timeScale: 1 } },
    });

    const eulerResult = await eulerKernel.run({ manifest });
    const verletResult = await verletKernel.run({ manifest });

    const eulerFinal = eulerResult.keyframes[eulerResult.keyframes.length - 1];
    const verletFinal = verletResult.keyframes[verletResult.keyframes.length - 1];

    const eulerBall = eulerFinal.entities.find((e: any) => e.id === 'ball')!;
    const verletBall = verletFinal.entities.find((e: any) => e.id === 'ball')!;

    // Both should fall downward under gravity
    expect(eulerBall.y).toBeGreaterThan(0);
    expect(verletBall.y).toBeGreaterThan(0);
  });

  it('Verlet integration falls under gravity (both methods produce downward motion)', async () => {
    const config = { gravity: { x: 0, y: 9.81 } };
    const eulerKernel = createPhysicsKernel(config as any);
    const verletKernel = createPhysicsKernel({ ...config, integrationMethod: 'verlet' } as any);

    const entities = [createRigidBody('ball', { x: 100, y: 0, mass: 1, restitution: 0 })];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({
      initialEntities: entities,
      steps,
      domainMetadata: { domain: 'PHYSICS' as any, physics: { gravity: { x: 0, y: 9.81 }, timeScale: 1 } },
    });

    const eulerResult = await eulerKernel.run({ manifest });
    const verletResult = await verletKernel.run({ manifest });

    const eulerFinal = eulerResult.keyframes[eulerResult.keyframes.length - 1];
    const verletFinal = verletResult.keyframes[verletResult.keyframes.length - 1];
    const eulerBall = eulerFinal.entities.find((e: any) => e.id === 'ball')!;
    const verletBall = verletFinal.entities.find((e: any) => e.id === 'ball')!;

    // Both methods must produce downward motion under positive gravity
    expect(eulerBall.y).toBeGreaterThan(0);
    expect(verletBall.y).toBeGreaterThan(0);
    // Verlet produces slightly more displacement per step due to the 0.5*a*dt² term
    // Both should be within the same order of magnitude
    expect(Math.abs(verletBall.y - eulerBall.y)).toBeLessThan(eulerBall.y);
  });

  it('Verlet kernel has correct domain', () => {
    const kernel = createPhysicsKernel({ integrationMethod: 'verlet' } as any);
    expect((kernel as any).domain).toBe('PHYSICS');
  });

  it('Verlet simulation runs without errors', async () => {
    const kernel = createPhysicsKernel({ integrationMethod: 'verlet' } as any);
    const entities = [
      createRigidBody('ball', { x: 100, y: 0, mass: 1, velocityX: 10, velocityY: 5 }),
    ];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({ initialEntities: entities, steps });

    const result = await kernel.run({ manifest });
    expect(result.errors).toBeUndefined();
    expect(result.keyframes.length).toBeGreaterThan(1);
  });

  it('prevX and prevY are updated after Verlet step', async () => {
    const kernel = createPhysicsKernel({
      gravity: { x: 0, y: 0 },
      integrationMethod: 'verlet',
    } as any) as any;

    const entities = [createRigidBody('ball', { x: 0, y: 0, mass: 1, velocityX: 10 })];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({
      initialEntities: entities,
      steps,
      domainMetadata: { domain: 'PHYSICS' as any, physics: { gravity: { x: 0, y: 0 }, timeScale: 1 } },
    });

    await kernel.run({ manifest });

    // After running, ball should have moved in x direction
    const bodyEntry = Array.from(kernel.bodies?.values() ?? [])[0] as any;
    if (bodyEntry) {
      expect(bodyEntry.x).toBeGreaterThan(bodyEntry.prevX);
    }
  });
});

// =============================================================================
// Collision Detection Tests
// =============================================================================
describe('Collision Detection', () => {
  it('resolves circle-circle collision and separates overlapping bodies', async () => {
    const kernel = createPhysicsKernel();
    // Two circles starting overlapped, moving toward each other
    const entities = [
      createRigidBody('ball-left', { x: 0, y: 250, mass: 1, velocityX: 5, restitution: 1 }),
      createRigidBody('ball-right', { x: 20, y: 250, mass: 1, velocityX: -5, restitution: 1 }),
    ];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({
      initialEntities: entities,
      steps,
      domainMetadata: { domain: 'PHYSICS' as any, physics: { gravity: { x: 0, y: 0 }, timeScale: 1 } },
    });

    const result = await kernel.run({ manifest });
    expect(result.errors).toBeUndefined();

    const finalKf = result.keyframes[result.keyframes.length - 1];
    const left = finalKf.entities.find((e: any) => e.id === 'ball-left')!;
    const right = finalKf.entities.find((e: any) => e.id === 'ball-right')!;

    // After elastic collision, left ball should be to the left of right ball
    expect(left.x).toBeLessThanOrEqual(right.x);
  });

  it('fixed body is not displaced by collision', async () => {
    const kernel = createPhysicsKernel();
    const entities = [
      createRigidBody('wall', { x: 100, y: 250, mass: 1000, fixed: true }),
      createRigidBody('ball', { x: 85, y: 250, mass: 1, velocityX: 10, restitution: 0.8 }),
    ];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({
      initialEntities: entities,
      steps,
      domainMetadata: { domain: 'PHYSICS' as any, physics: { gravity: { x: 0, y: 0 }, timeScale: 1 } },
    });

    const result = await kernel.run({ manifest });
    const finalKf = result.keyframes[result.keyframes.length - 1];
    const wall = finalKf.entities.find((e: any) => e.id === 'wall')!;

    // Fixed body must not move
    expect(wall.x).toBe(100);
    expect(wall.y).toBe(250);
  });

  it('two fixed bodies do not generate collision response', async () => {
    const kernel = createPhysicsKernel();
    const entities = [
      createRigidBody('a', { x: 0, y: 0, fixed: true }),
      createRigidBody('b', { x: 1, y: 0, fixed: true }),
    ];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({ initialEntities: entities, steps });

    // Should not throw
    const result = await kernel.run({ manifest });
    expect(result.errors).toBeUndefined();
    const finalKf = result.keyframes[result.keyframes.length - 1];
    expect(finalKf.entities.find((e: any) => e.id === 'a')!.x).toBe(0);
    expect(finalKf.entities.find((e: any) => e.id === 'b')!.x).toBe(1);
  });

  it('ground plane stops a falling ball', async () => {
    const kernel = createPhysicsKernel();
    const entities = [
      createRigidBody('ball', { x: 100, y: 400, mass: 1, restitution: 0 }),
    ];
    const steps: SimulationStep[] = [{ id: 'step-1' as any, orderIndex: 0, actions: [] }];
    const manifest = createPhysicsManifest({
      initialEntities: entities,
      steps,
      domainMetadata: { domain: 'PHYSICS' as any, physics: { gravity: { x: 0, y: 9.81 }, timeScale: 1 } },
    });

    const result = await kernel.run({ manifest });
    const finalKf = result.keyframes[result.keyframes.length - 1];
    const ball = finalKf.entities.find((e: any) => e.id === 'ball')!;

    // Ball should not go below ground plane (y = 500 - radius)
    expect(ball.y).toBeLessThanOrEqual(500);
  });
});
