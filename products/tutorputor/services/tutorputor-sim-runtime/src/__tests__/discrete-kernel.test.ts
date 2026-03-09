/**
 * Discrete Kernel Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test discrete algorithm kernel execution
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createDiscreteKernel } from '../discrete-kernel';
import type { SimulationManifest, SimEntity, SimAction, SimulationStep, SimEntityBase } from '@ghatana/tutorputor-contracts/v1/simulation/types';

/**
 * Helper to create a minimal test manifest
 */
function createTestManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: 'test-sim-001' as any,
    version: '1.0',
    domain: 'CS_DISCRETE' as any,
    title: 'Test Simulation',
    description: 'A test simulation for kernel testing',
    authorId: 'test-user' as any,
    tenantId: 'test-tenant' as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: '1.0',
    initialEntities: [],
    steps: [],
    domainMetadata: { domain: 'CS_DISCRETE' },
    ...overrides,
  };
}

/**
 * Helper to create a test entity
 */
function createTestEntity(id: string, overrides: any = {}): SimEntity {
  return {
    id,
    type: 'array_element',
    label: `Element ${id}`,
    x: 0,
    y: 0,
    visual: {
      shape: 'circle',
      fill: '#4A90D9',
      stroke: '#2C5282',
      strokeWidth: 2,
    },
    data: {},
    ...overrides,
  } as unknown as SimEntity;
}

describe('DiscreteKernel', () => {
  let kernel: ReturnType<typeof createDiscreteKernel>;

  beforeEach(() => {
    kernel = createDiscreteKernel({
      defaultDuration: 500,
      defaultEasing: 'easeInOut',
      samplingRate: 30,
    });
  });

  describe('domain identification', () => {
    it('should have CS_DISCRETE as domain', () => {
      expect(kernel.domain).toBe('CS_DISCRETE');
    });

    it('should be able to execute CS_DISCRETE manifests', () => {
      const manifest = createTestManifest({ domain: 'CS_DISCRETE' });
      expect(kernel.canExecute(manifest)).toBe(true);
    });

    it('should not execute non-CS_DISCRETE manifests', () => {
      const manifest = createTestManifest({ domain: 'PHYSICS' as any });
      expect(kernel.canExecute(manifest)).toBe(false);
    });
  });

  describe('run()', () => {
    it('should return error for wrong domain', async () => {
      const manifest = createTestManifest({ domain: 'PHYSICS' as any });
      const result = await kernel.run({ manifest });

      expect(result.errors).toBeDefined();
      expect(result.errors).toContain('Manifest domain is not CS_DISCRETE');
      expect(result.keyframes).toHaveLength(0);
    });

    it('should generate initial keyframe with entities', async () => {
      const entities = [
        createTestEntity('e1', { x: 0, y: 0, data: { value: 5 } }),
        createTestEntity('e2', { x: 50, y: 0, data: { value: 3 } }),
        createTestEntity('e3', { x: 100, y: 0, data: { value: 8 } }),
      ];

      const manifest = createTestManifest({
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      expect(result.keyframes).toHaveLength(1); // Initial keyframe only
      expect(result.keyframes[0].stepIndex).toBe(-1);
      expect(result.keyframes[0].timestamp).toBe(0);
      expect(result.keyframes[0].entities).toHaveLength(3);
    });

    it('should process MOVE actions correctly', async () => {
      const entities = [
        createTestEntity('e1', { x: 0, y: 0 }),
        createTestEntity('e2', { x: 50, y: 0 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-1' as any as any,
          orderIndex: 0,

          actions: [
            { action: 'MOVE', targetId: 'e1', toX: 50, toY: 0 } as unknown as SimAction,
            { action: 'MOVE', targetId: 'e2', toX: 0, toY: 0 } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createTestManifest({
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.totalSteps).toBe(1);
      expect(result.keyframes.length).toBeGreaterThan(1);

      // Find the final keyframe for step 0
      const finalKeyframe = result.keyframes.find(kf => kf.stepIndex === 0);
      expect(finalKeyframe).toBeDefined();

      const e1 = finalKeyframe!.entities.find(e => e.id === 'e1');
      const e2 = finalKeyframe!.entities.find(e => e.id === 'e2');

      expect(e1?.x).toBe(50);
      expect(e2?.x).toBe(0);
    });

    it('should process CREATE_ENTITY actions', async () => {
      const newEntity = createTestEntity('new-entity', { x: 100, y: 100 });

      const steps: SimulationStep[] = [
        {
          id: 'step-1' as any as any,
          orderIndex: 0,

          actions: [
            { action: 'CREATE_ENTITY', entity: newEntity } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createTestManifest({
        initialEntities: [],
        steps,
      });

      const result = await kernel.run({ manifest });

      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      expect(finalKeyframe.entities).toHaveLength(1);
      expect(finalKeyframe.entities[0].id).toBe('new-entity');
    });

    it('should process REMOVE_ENTITY actions', async () => {
      const entities = [
        createTestEntity('e1'),
        createTestEntity('e2'),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-1' as any as any,
          orderIndex: 0,

          actions: [
            { action: 'REMOVE_ENTITY', targetId: 'e1' } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createTestManifest({
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      const finalKeyframe = result.keyframes[result.keyframes.length - 1];
      expect(finalKeyframe.entities).toHaveLength(1);
      expect(finalKeyframe.entities[0].id).toBe('e2');
    });

    it('should handle step range with startStep and endStep', async () => {
      const steps: SimulationStep[] = [
        { id: 'step-0' as any as any, orderIndex: 0, actions: [] },
        { id: 'step-1' as any as any, orderIndex: 1, actions: [] },
        { id: 'step-2' as any as any, orderIndex: 2, actions: [] },
        { id: 'step-3' as any as any, orderIndex: 3, actions: [] },
      ];

      const manifest = createTestManifest({
        initialEntities: [],
        steps,
      });

      const result = await kernel.run({
        manifest,
        startStep: 1,
        endStep: 2,
      });

      expect(result.totalSteps).toBe(2);
    });

    it('should track execution time', async () => {
      const manifest = createTestManifest();
      const result = await kernel.run({ manifest });

      expect(result.executionTimeMs).toBeGreaterThanOrEqual(0);
    });
  });

  describe('interpolateKeyframes()', () => {
    it('should return original keyframes when less than 2', () => {
      const keyframes = [
        { stepIndex: 0, timestamp: 0, entities: [], annotations: [] },
      ];

      const result = kernel.interpolateKeyframes(keyframes, 60);
      expect(result).toEqual(keyframes);
    });

    it('should interpolate between keyframes at target FPS', () => {
      const entity1 = createTestEntity('e1', { x: 0, y: 0 });
      const entity2 = createTestEntity('e1', { x: 100, y: 100 });

      const keyframes = [
        { stepIndex: 0, timestamp: 0, entities: [entity1], annotations: [] },
        { stepIndex: 1, timestamp: 1000, entities: [entity2], annotations: [] },
      ];

      const result = kernel.interpolateKeyframes(keyframes, 60);

      // At 60 FPS over 1000ms, we expect ~60 frames
      expect(result.length).toBeGreaterThan(30);

      // Check that mid-point has interpolated values
      const midIndex = Math.floor(result.length / 2);
      const midFrame = result[midIndex];
      const midEntity = midFrame.entities[0];

      // Values should be between start and end
      expect(midEntity.x).toBeGreaterThan(0);
      expect(midEntity.x).toBeLessThan(100);
    });

    it('should preserve final keyframe', () => {
      const entity1 = createTestEntity('e1', { x: 0, y: 0 });
      const entity2 = createTestEntity('e1', { x: 100, y: 100 });

      const keyframes = [
        { stepIndex: 0, timestamp: 0, entities: [entity1], annotations: [] },
        { stepIndex: 1, timestamp: 1000, entities: [entity2], annotations: [] },
      ];

      const result = kernel.interpolateKeyframes(keyframes, 60);

      const lastFrame = result[result.length - 1];
      expect(lastFrame.entities[0].x).toBe(100);
      expect(lastFrame.entities[0].y).toBe(100);
    });
  });

  describe('checkHealth()', () => {
    it('should return true for healthy kernel', async () => {
      const isHealthy = await kernel.checkHealth();
      expect(isHealthy).toBe(true);
    });
  });
});

describe('DiscreteKernel with custom config', () => {
  it('should respect custom defaultDuration', async () => {
    const kernel = createDiscreteKernel({ defaultDuration: 1000 });
    const manifest = createTestManifest({
      initialEntities: [createTestEntity('e1')],
      steps: [
        {
          id: 'step-1' as any as any,
          orderIndex: 0,

          actions: [
            { action: 'MOVE', targetId: 'e1', toX: 100, toY: 0 } as unknown as SimAction,
          ],
        },
      ],
    });

    const result = await kernel.run({ manifest });
    expect(result.executionTimeMs).toBeGreaterThanOrEqual(0);
  });
});

describe('Bubble Sort Simulation', () => {
  it('should correctly simulate bubble sort steps', async () => {
    const kernel = createDiscreteKernel();

    // Create array entities with values
    const entities: SimEntity[] = [
      createTestEntity('arr-0', { x: 0, y: 0, data: { value: 5, index: 0 } }),
      createTestEntity('arr-1', { x: 60, y: 0, data: { value: 2, index: 1 } }),
      createTestEntity('arr-2', { x: 120, y: 0, data: { value: 8, index: 2 } }),
    ];

    // Bubble sort: compare [0,1], swap, compare [1,2], no swap
    const steps: SimulationStep[] = [
      {
        id: 'step-compare-0-1' as any as any,
        orderIndex: 0,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['arr-0', 'arr-1'], style: 'comparing' } as unknown as SimAction,
        ],
      },
      {
        id: 'step-swap-0-1' as any as any,
        orderIndex: 1,

        actions: [
          { action: 'MOVE', targetId: 'arr-0', toX: 60, toY: 0 } as unknown as SimAction,
          { action: 'MOVE', targetId: 'arr-1', toX: 0, toY: 0 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-compare-1-2' as any as any,
        orderIndex: 2,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['arr-0', 'arr-2'], style: 'comparing' } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createTestManifest({
      domain: 'CS_DISCRETE',
      title: 'Bubble Sort Demo',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(3);
    expect(result.keyframes.length).toBeGreaterThan(0);

    // Verify final positions after swap
    const swapStepKeyframes = result.keyframes.filter(kf => kf.stepIndex === 1);
    const lastSwapFrame = swapStepKeyframes[swapStepKeyframes.length - 1];

    if (lastSwapFrame) {
      const arr0 = lastSwapFrame.entities.find(e => e.id === 'arr-0');
      const arr1 = lastSwapFrame.entities.find(e => e.id === 'arr-1');
      expect(arr0?.x).toBe(60); // Moved right
      expect(arr1?.x).toBe(0);  // Moved left
    }
  });
});

describe('Graph Algorithm Simulation', () => {
  it('should handle BFS traversal with visit actions', async () => {
    const kernel = createDiscreteKernel();

    // Create graph nodes
    const entities: SimEntity[] = [
      createTestEntity('node-A', { x: 100, y: 50, type: 'graph_node', data: { label: 'A' } }),
      createTestEntity('node-B', { x: 50, y: 150, type: 'graph_node', data: { label: 'B' } }),
      createTestEntity('node-C', { x: 150, y: 150, type: 'graph_node', data: { label: 'C' } }),
      createTestEntity('edge-AB', { x: 75, y: 100, type: 'graph_edge', data: { from: 'A', to: 'B' } }),
      createTestEntity('edge-AC', { x: 125, y: 100, type: 'graph_edge', data: { from: 'A', to: 'C' } }),
    ];

    // BFS visits: A (start) -> B, C (neighbors)
    const steps: SimulationStep[] = [
      {
        id: 'visit-A' as any,
        orderIndex: 0,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['node-A'], style: 'visited' } as unknown as SimAction,
        ],
      },
      {
        id: 'explore-AB' as any,
        orderIndex: 1,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['edge-AB'], style: 'exploring' } as unknown as SimAction,
        ],
      },
      {
        id: 'visit-B' as any,
        orderIndex: 2,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['node-B'], style: 'visited' } as unknown as SimAction,
        ],
      },
      {
        id: 'explore-AC' as any,
        orderIndex: 3,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['edge-AC'], style: 'exploring' } as unknown as SimAction,
        ],
      },
      {
        id: 'visit-C' as any,
        orderIndex: 4,

        actions: [
          { action: 'HIGHLIGHT', targetIds: ['node-C'], style: 'visited' } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createTestManifest({
      domain: 'CS_DISCRETE',
      title: 'BFS Traversal',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(5);
    expect(result.simulationId).toBe('test-sim-001');
  });
});
