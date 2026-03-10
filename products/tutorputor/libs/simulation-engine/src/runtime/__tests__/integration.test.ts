/**
 * Simulation Engine Integration Tests
 *
 * @doc.type test
 * @doc.purpose End-to-end integration tests for the simulation engine
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest';

// Mock ioredis
vi.mock('ioredis', () => {
  return {
    default: class Redis {
      private data = new Map();
      constructor() { }
      async get(key: string) { return this.data.get(key); }
      async set(key: string, value: string) { this.data.set(key, value); return 'OK'; }
      async del(key: string) { this.data.delete(key); return 1; }
      async expire(key: string, seconds: number) { return 1; }
      async keys(pattern: string) { return Array.from(this.data.keys()); }
      async quit() { return 'OK'; }
      on(event: string, callback: Function) { }
    }
  };
});

import { createRuntimeService } from '../service';
// import { createKernelRegistry } from '../kernel-registry';
// import { createDiscreteKernel } from '../discrete-kernel';
// import { createPhysicsKernel } from '../physics-kernel';
// import { createChemistryKernel } from '../chemistry-kernel';
// import { createBiologyKernel } from '../biology-kernel';
// import { createMedicineKernel } from '../medicine-kernel';
// import { createSystemDynamicsKernel } from '../system-dynamics-kernel';
import type { SimulationManifest, SimEntity, SimulationStep, SimAction } from '@ghatana/tutorputor-contracts/v1/simulation/types';
import type { SimRuntimeService } from '@ghatana/tutorputor-contracts/v1/simulation/services';

/**
 * Full integration test suite for simulation engine
 */
describe('Simulation Engine Integration', () => {
  let runtimeService: any;
  // let kernelRegistry: ReturnType<typeof createKernelRegistry>;

  beforeAll(() => {
    // Set up kernel registry with all kernels
    // kernelRegistry = createKernelRegistry();
    // kernelRegistry.registerKernel('CS_DISCRETE', createDiscreteKernel());
    // kernelRegistry.registerKernel('PHYSICS', createPhysicsKernel());
    // kernelRegistry.registerKernel('CHEMISTRY', createChemistryKernel());
    // kernelRegistry.registerKernel('BIOLOGY', createBiologyKernel());
    // kernelRegistry.registerKernel('MEDICINE', createMedicineKernel());
    // kernelRegistry.registerKernel('SYSTEM_DYNAMICS', createSystemDynamicsKernel());

    // Create runtime service
    runtimeService = createRuntimeService();
  });

  // afterAll(async () => {
  //   // Clean up any remaining sessions
  //   // const sessions = await runtimeService.listSessions();
  //   // for (const session of sessions) {
  //   //   await runtimeService.endSession(session);
  //   // }
  // });

  describe('Full Simulation Lifecycle', () => {
    it('should complete full lifecycle: create → step → seek → end', async () => {
      // 1. Create a manifest
      const manifest: SimulationManifest = {
        id: 'integration-test-001' as any as any,
        version: '1.0',
        domain: 'CS_DISCRETE' as any,
        title: 'Bubble Sort Integration Test',
        description: 'Full lifecycle test with bubble sort',
        authorId: 'test-user' as any,
        tenantId: 'test-tenant' as any,
        canvas: { width: 800, height: 600 },
        playback: { defaultSpeed: 1, autoPlay: false },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        schemaVersion: '1.0',
        initialEntities: [
          { id: 'arr-0', type: 'array_element', x: 0, y: 0, data: { value: 5 } } as unknown as SimEntity,
          { id: 'arr-1', type: 'array_element', x: 60, y: 0, data: { value: 3 } } as unknown as SimEntity,
          { id: 'arr-2', type: 'array_element', x: 120, y: 0, data: { value: 8 } } as unknown as SimEntity,
          { id: 'arr-3', type: 'array_element', x: 180, y: 0, data: { value: 1 } } as unknown as SimEntity,
        ],
        steps: [
          {
            id: 'step-0' as any as any,
            orderIndex: 0,

            actions: [
              { action: 'HIGHLIGHT', targetIds: ['arr-0', 'arr-1'], style: 'comparing' } as unknown as SimAction,
            ],
          },
          {
            id: 'step-1' as any as any,
            orderIndex: 1,

            actions: [
              { action: 'MOVE', targetId: 'arr-0', toX: 60, toY: 0 } as unknown as SimAction,
              { action: 'MOVE', targetId: 'arr-1', toX: 0, toY: 0 } as unknown as SimAction,
            ],
          },
          {
            id: 'step-2' as any as any,
            orderIndex: 2,

            actions: [
              { action: 'HIGHLIGHT', targetIds: ['arr-0', 'arr-2'], style: 'comparing' } as unknown as SimAction,
            ],
          },
          {
            id: 'step-3' as any as any,
            orderIndex: 3,

            actions: [
              { action: 'HIGHLIGHT', targetIds: ['arr-2', 'arr-3'], style: 'comparing' } as unknown as SimAction,
            ],
          },
          {
            id: 'step-4' as any as any,
            orderIndex: 4,

            actions: [
              { action: 'MOVE', targetId: 'arr-2', toX: 180, toY: 0 } as unknown as SimAction,
              { action: 'MOVE', targetId: 'arr-3', toX: 120, toY: 0 } as unknown as SimAction,
            ],
          },
        ],
        domainMetadata: { domain: 'CS_DISCRETE' },
      };

      // 2. Create session
      const session = await runtimeService.createSession(manifest);
      const sessionObj = await runtimeService.getSession(session);
      expect(session).toBeDefined();
      expect(sessionObj.manifest.id).toBe('integration-test-001');
      expect(sessionObj.currentStepIndex).toBe(-1);

      // 3. Step forward through simulation
      let state = await runtimeService.stepForward(session);
      expect(state.stepIndex).toBe(0);

      state = await runtimeService.stepForward(session);
      expect(state.stepIndex).toBe(1);

      state = await runtimeService.stepForward(session);
      expect(state.stepIndex).toBe(2);

      // 4. Step backward
      state = await runtimeService.stepBackward(session);
      expect(state.stepIndex).toBe(1);

      // 5. Seek to specific step
      state = await runtimeService.seekTo(session, 4000);
      expect(state.stepIndex).toBe(4);

      // 6. Seek back to beginning
      state = await runtimeService.seekTo(session, -1);
      expect(state.stepIndex).toBe(-1);

      // 7. Get state
      state = await runtimeService.getState(session);
      expect(state.entities).toHaveLength(4);

      // 8. End session
      await runtimeService.endSession(session);

      // 9. Verify session is ended
      const sessionInfo = await runtimeService.getSession(session);
      expect(sessionInfo).toBeFalsy();
    });

    it('should handle multiple concurrent sessions', async () => {
      const manifests = [
        createTestManifest('concurrent-1', 'CS_DISCRETE', 3),
        createTestManifest('concurrent-2', 'CS_DISCRETE', 5),
        createTestManifest('concurrent-3', 'CS_DISCRETE', 7),
      ];

      // Create all sessions
      const sessionIds = await Promise.all(
        manifests.map(m => runtimeService.createSession(m))
      );

      expect(sessionIds).toHaveLength(3);
      for (let i = 0; i < sessionIds.length; i++) {
        const s = await runtimeService.getSession(sessionIds[i]);
        expect(s!.simulationId).toBe(`concurrent-${i + 1}`);
      }

      // Step each session independently
      await runtimeService.stepForward(sessionIds[0]);
      await runtimeService.stepForward(sessionIds[0]);

      await runtimeService.stepForward(sessionIds[1]);

      // Session states should be independent
      const state0 = await runtimeService.getState(sessionIds[0]);
      const state1 = await runtimeService.getState(sessionIds[1]);
      const state2 = await runtimeService.getState(sessionIds[2]);

      expect(state0.currentStepIndex).toBe(1);
      expect(state1.currentStepIndex).toBe(0);
      expect(state2.currentStepIndex).toBe(-1);

      // Clean up
      await Promise.all(
        sessionIds.map(id => runtimeService.endSession(id))
      );
    });
  });

  describe('Cross-Domain Simulations', () => {
    it('should run physics simulation end-to-end', async () => {
      const manifest: SimulationManifest = {
        id: 'physics-integration' as any,
        version: '1.0',
        domain: 'PHYSICS',
        title: 'Free Fall Physics',
        description: 'Ball falling under gravity',
        authorId: 'test-user' as any,
        tenantId: 'test-tenant' as any,
        canvas: { width: 800, height: 600 },
        playback: { defaultSpeed: 1, autoPlay: false },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        schemaVersion: '1.0',
        initialEntities: [
          {
            id: 'ball',
            type: 'rigidBody',

            x: 100,
            y: 0,
            mass: 1,
            velocityX: 0,
            velocityY: 0,
            fixed: false,
          } as unknown as SimEntity,
          {
            id: 'ground',
            type: 'rigidBody',

            x: 100,
            y: 500,
            mass: 1000,
            fixed: true,
          } as unknown as SimEntity,
        ],
        steps: [
          { id: 'step-0' as any as any, orderIndex: 0, actions: [] },
          { id: 'step-1' as any as any, orderIndex: 1, actions: [] },
        ],
        domainMetadata: {
          domain: 'PHYSICS' as any,
          physics: {
            gravity: { x: 0, y: 9.81 },
            timeScale: 1,
          },
        },
      };

      const session = await runtimeService.createSession(manifest);

      // Step through simulation
      await runtimeService.stepForward(session);
      const state = await runtimeService.getState(session);

      expect(state.entities.find((e: any) => e.id === 'ball')).toBeDefined();
      expect(state.entities.find((e: any) => e.id === 'ground')).toBeDefined();

      await runtimeService.endSession(session);
    });

    it('should run chemistry simulation end-to-end', async () => {
      const manifest: SimulationManifest = {
        id: 'chemistry-integration' as any,
        version: '1.0',
        domain: 'CHEMISTRY',
        title: 'Water Formation',
        description: '2H2 + O2 → 2H2O',
        authorId: 'test-user' as any,
        tenantId: 'test-tenant' as any,
        canvas: { width: 800, height: 600 },
        playback: { defaultSpeed: 1, autoPlay: false },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        schemaVersion: '1.0',
        initialEntities: [
          { id: 'H1', type: 'atom', element: 'H', x: 0, y: 0 } as unknown as SimEntity,
          { id: 'H2', type: 'atom', element: 'H', x: 30, y: 0 } as unknown as SimEntity,
          { id: 'O1', type: 'atom', element: 'O', x: 100, y: 0 } as unknown as SimEntity,
        ],
        steps: [
          {
            id: 'step-0' as any as any,
            orderIndex: 0,

            actions: [
              { action: 'FORM_BOND', atom1Id: 'H1', atom2Id: 'O1', bondOrder: 1 } as unknown as SimAction,
            ],
          },
          {
            id: 'step-1' as any as any,
            orderIndex: 1,

            actions: [
              { action: 'FORM_BOND', atom1Id: 'H2', atom2Id: 'O1', bondOrder: 1 } as unknown as SimAction,
            ],
          },
        ],
        domainMetadata: {
          domain: 'CHEMISTRY' as any,
          chemistry: { temperature: 298, pressure: 1 } as any,
        },
      };

      const session = await runtimeService.createSession(manifest);

      await runtimeService.stepForward(session);
      await runtimeService.stepForward(session);

      const state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(1);

      await runtimeService.endSession(session);
    });

    it('should run biology simulation end-to-end', async () => {
      const manifest: SimulationManifest = {
        id: 'biology-integration' as any,
        version: '1.0',
        domain: 'BIOLOGY',
        title: 'Cell Division',
        description: 'Mitosis phases',
        authorId: 'test-user' as any,
        tenantId: 'test-tenant' as any,
        canvas: { width: 800, height: 600 },
        playback: { defaultSpeed: 1, autoPlay: false },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        schemaVersion: '1.0',
        initialEntities: [
          {
            id: 'cell-1',
            type: 'cell',

            x: 200,
            y: 200,
            phase: 'G1',
          } as unknown as SimEntity,
        ],
        steps: [
          {
            id: 'step-g1' as any as any,
            orderIndex: 0,

            actions: [{ action: 'SET_PHASE', targetId: 'cell-1', phase: 'G1' } as unknown as SimAction],
          },
          {
            id: 'step-s' as any as any,
            orderIndex: 1,

            actions: [{ action: 'SET_PHASE', targetId: 'cell-1', phase: 'S' } as unknown as SimAction],
          },
          {
            id: 'step-g2' as any as any,
            orderIndex: 2,

            actions: [{ action: 'SET_PHASE', targetId: 'cell-1', phase: 'G2' } as unknown as SimAction],
          },
          {
            id: 'step-m' as any as any,
            orderIndex: 3,

            actions: [{ action: 'SET_PHASE', targetId: 'cell-1', phase: 'M' } as unknown as SimAction],
          },
        ],
        domainMetadata: {
          domain: 'BIOLOGY' as any,
          biology: { timeScale: 'hours', ecosystemType: 'cellular' } as any,
        },
      };

      const session = await runtimeService.createSession(manifest);

      // Step through all phases
      for (let i = 0; i < 4; i++) {
        await runtimeService.stepForward(session);
      }

      const state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(3);

      await runtimeService.endSession(session);
    });

    it('should run medicine/epidemiology simulation end-to-end', async () => {
      const manifest: SimulationManifest = {
        id: 'medicine-integration' as any,
        version: '1.0',
        domain: 'MEDICINE',
        title: 'SIR Epidemic Model',
        description: 'Disease spread simulation',
        authorId: 'test-user' as any,
        tenantId: 'test-tenant' as any,
        canvas: { width: 800, height: 600 },
        playback: { defaultSpeed: 1, autoPlay: false },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        schemaVersion: '1.0',
        initialEntities: [
          { id: 'S', type: 'epidemic_compartment', compartment: 'S', count: 999, x: 100, y: 200 } as unknown as SimEntity,
          { id: 'I', type: 'epidemic_compartment', compartment: 'I', count: 1, x: 300, y: 200 } as unknown as SimEntity,
          { id: 'R', type: 'epidemic_compartment', compartment: 'R', count: 0, x: 500, y: 200 } as unknown as SimEntity,
        ],
        steps: Array.from({ length: 10 }, (_, i) => ({
          id: `day-${i}` as any,
          orderIndex: i,
          actions: [{ action: 'SIR_UPDATE', beta: 0.3, gamma: 0.1 } as unknown as SimAction],
        })),
        domainMetadata: {
          domain: 'MEDICINE' as any,
          medicine: { epidemiologyModel: 'SIR' } as any,
        },
      };

      const session = await runtimeService.createSession(manifest);

      // Simulate 5 days
      for (let i = 0; i < 5; i++) {
        await runtimeService.stepForward(session);
      }

      const state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(4);

      await runtimeService.endSession(session);
    });

    it('should run system dynamics simulation end-to-end', async () => {
      const manifest: SimulationManifest = {
        id: 'sysdy-integration' as any,
        version: '1.0',
        domain: 'SYSTEM_DYNAMICS' as any,
        title: 'Population Growth',
        description: 'Exponential growth model',
        authorId: 'test-user' as any,
        tenantId: 'test-tenant' as any,
        canvas: { width: 800, height: 600 },
        playback: { defaultSpeed: 1, autoPlay: false },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        schemaVersion: '1.0',
        initialEntities: [
          { id: 'population', type: 'stock', value: 100, x: 200, y: 200 } as unknown as SimEntity,
          { id: 'births', type: 'flow', rate: 0.1, toStock: 'population', x: 100, y: 200 } as unknown as SimEntity,
        ],
        steps: Array.from({ length: 10 }, (_, i) => ({
          id: `year-${i}` as any,
          orderIndex: i,
          actions: [
            { action: 'CALCULATE_FLOW', flowId: 'births', formula: 'population * 0.1' } as unknown as SimAction,
            { action: 'APPLY_FLOWS', stockId: 'population' } as unknown as SimAction,
          ],
        })),
        domainMetadata: {
          domain: 'SYSTEM_DYNAMICS' as any,
          systemDynamics: { timeUnit: 'years', simulationDuration: 10, timeStep: 0.1 } as any,
        } as any,
      };

      const session = await runtimeService.createSession(manifest);

      // Simulate 5 years
      for (let i = 0; i < 5; i++) {
        await runtimeService.stepForward(session);
      }

      const state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(4);

      await runtimeService.endSession(session);
    });
  });

  describe('Playback Controls', () => {
    it('should handle play, pause, and speed controls', async () => {
      const manifest = createTestManifest('playback-test', 'CS_DISCRETE', 10);
      const session = await runtimeService.createSession(manifest);

      // Start playback
      await runtimeService.play(session);
      let info = await runtimeService.getSession(session);
      expect(info!.isPlaying).toBe(true);

      // Set speed
      await runtimeService.setPlaybackSpeed(session, 2.0);
      info = await runtimeService.getSession(session);
      expect(info!.playbackSpeed).toBe(2.0);

      // Pause
      await runtimeService.pause(session);
      info = await runtimeService.getSession(session);
      expect(info!.isPlaying).toBe(false);

      await runtimeService.endSession(session);
    });

    it('should interpolate between keyframes smoothly', async () => {
      const manifest = createTestManifest('interpolation-test', 'CS_DISCRETE', 5);
      const session = await runtimeService.createSession(manifest);

      // Move to step 1
      await runtimeService.stepForward(session);

      // Interpolate at various points
      const interp0 = await runtimeService.interpolate(session, 0);
      const interp50 = await runtimeService.interpolate(session, 0.5);
      const interp100 = await runtimeService.interpolate(session, 1.0);

      expect(interp0.entities).toBeDefined();
      expect(interp50.entities).toBeDefined();
      expect(interp100.entities).toBeDefined();

      await runtimeService.endSession(session);
    });
  });

  describe('Error Recovery', () => {
    it('should handle session errors gracefully', async () => {
      // Try operations on non-existent session
      await expect(
        runtimeService.stepForward('non-existent-session')
      ).rejects.toThrow();

      await expect(
        runtimeService.getState('non-existent-session')
      ).rejects.toThrow();

      // End non-existent session should not throw
      await expect(
        runtimeService.endSession('non-existent-session')
      ).resolves.not.toThrow();
    });

    it('should maintain state after error', async () => {
      const manifest = createTestManifest('error-recovery', 'CS_DISCRETE', 5);
      const session = await runtimeService.createSession(manifest);

      await runtimeService.stepForward(session);
      await runtimeService.stepForward(session);

      // Try to cause an error (invalid session)
      try {
        await runtimeService.stepForward('invalid');
      } catch {
        // Expected
      }

      // Original session should still work
      const state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(1);

      await runtimeService.endSession(session);
    });

    it('should handle boundary conditions', async () => {
      const manifest = createTestManifest('boundary-test', 'CS_DISCRETE', 3);
      const session = await runtimeService.createSession(manifest);

      // Try to step back from beginning
      await runtimeService.stepBackward(session);
      let state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(-1);

      // Step to end
      for (let i = 0; i < 10; i++) {
        await runtimeService.stepForward(session);
      }

      state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(2); // Max step is 2 (0-indexed, 3 steps)

      // Seek beyond bounds
      await runtimeService.seekTo(session, 5000);
      state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(2);

      await runtimeService.seekTo(session, -100);
      state = await runtimeService.getState(session);
      expect(state.stepIndex).toBe(-1);

      await runtimeService.endSession(session);
    });
  });

  describe('Performance', () => {
    it('should handle large simulations', async () => {
      // Create a manifest with many steps
      const manifest = createTestManifest('large-sim', 'CS_DISCRETE', 100);

      const startTime = Date.now();
      const session = await runtimeService.createSession(manifest);
      const createTime = Date.now() - startTime;

      expect(createTime).toBeLessThan(5000); // Should create in under 5 seconds

      // Step through many steps quickly
      const stepStart = Date.now();
      for (let i = 0; i < 50; i++) {
        await runtimeService.stepForward(session);
      }
      const stepTime = Date.now() - stepStart;

      expect(stepTime).toBeLessThan(2000); // Should step 50 times in under 2 seconds

      await runtimeService.endSession(session);
    });

    it('should handle many concurrent sessions', async () => {
      const sessionCount = 20;
      const manifests = Array.from({ length: sessionCount }, (_, i) =>
        createTestManifest(`concurrent-perf-${i}`, 'CS_DISCRETE', 5)
      );

      const startTime = Date.now();
      const sessions = await Promise.all(
        manifests.map(m => runtimeService.createSession(m))
      );
      const createTime = Date.now() - startTime;

      expect(sessions).toHaveLength(sessionCount);
      expect(createTime).toBeLessThan(5000);

      // Clean up
      await Promise.all(
        sessions.map(s => runtimeService.endSession(s.sessionId))
      );
    });
  });
});

/**
 * Helper function to create test manifests
 */
function createTestManifest(
  id: string,
  domain: string,
  stepCount: number
): SimulationManifest {
  return {
    id: id as any,
    version: '1.0',
    domain: domain as any,
    title: `Test Simulation: ${id}`,
    description: 'Auto-generated test simulation',
    authorId: 'test-user' as any,
    tenantId: 'test-tenant' as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: '1.0',
    initialEntities: [
      { id: 'entity-1', type: 'test_entity', x: 0, y: 0, data: {} } as unknown as SimEntity,
      { id: 'entity-2', type: 'test_entity', x: 100, y: 0, data: {} } as unknown as SimEntity,
    ],
    steps: Array.from({ length: stepCount }, (_, i) => ({
      id: `step-${i}` as any,
      orderIndex: i,
      actions: [],
    })),
    domainMetadata: { domain: domain as any },
  };
}
