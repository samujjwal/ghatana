/**
 * Runtime Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test simulation runtime service session management and execution
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

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
import type { SimulationManifest, SimKeyframe, SimEntity } from '@ghatana/tutorputor-contracts/v1/simulation/types';
import type { SimRuntimeService } from '@ghatana/tutorputor-contracts/v1/simulation/services';

/**
 * Create a test manifest with steps and keyframes
 */
function createTestManifest(options: {
  id?: string;
  domain?: string;
  stepCount?: number;
} = {}): SimulationManifest {
  const stepCount = options.stepCount ?? 5;

  const steps = Array.from({ length: stepCount }, (_, i) => ({
    id: `step-${i}` as any as any,
    orderIndex: i,
    actions: [],
  }));

  const initialEntities: SimEntity[] = [
    {
      id: 'entity-1',
      type: 'test_entity',

      x: 0,
      y: 0,
      visual: { fill: '#4A90D9' },
      data: { value: 0 },
    } as unknown as SimEntity,
    {
      id: 'entity-2',
      type: 'test_entity',

      x: 100,
      y: 0,
      visual: { fill: '#48BB78' },
      data: { value: 100 },
    } as unknown as SimEntity,
  ];

  return {
    id: (options.id ?? 'test-manifest-001') as any,
    version: '1.0',
    domain: (options.domain ?? 'CS_DISCRETE') as any,
    title: 'Test Simulation',
    description: 'A test simulation for service testing',
    authorId: 'test-user' as any,
    tenantId: 'test-tenant' as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: '1.0',
    initialEntities,
    steps,
    domainMetadata: { domain: (options.domain ?? 'CS_DISCRETE') as any },
  };
}

/**
 * Create mock keyframes for a manifest
 */
function createMockKeyframes(stepCount: number): SimKeyframe[] {
  const keyframes: SimKeyframe[] = [];

  // Initial keyframe
  keyframes.push({
    stepIndex: -1,
    timestamp: 0,
    entities: [
      { id: 'entity-1', type: 'test_entity', x: 0, y: 0 } as SimEntity,
      { id: 'entity-2', type: 'test_entity', x: 100, y: 0 } as SimEntity,
    ],
    annotations: [],
  });

  // Step keyframes
  for (let i = 0; i < stepCount; i++) {
    keyframes.push({
      stepIndex: i,
      timestamp: (i + 1) * 1000,
      entities: [
        { id: 'entity-1', type: 'test_entity', x: (i + 1) * 20, y: 0 } as SimEntity,
        { id: 'entity-2', type: 'test_entity', x: 100 - (i + 1) * 20, y: 0 } as SimEntity,
      ],
      annotations: [{ id: `ann-${i}`, text: `Step ${i}`, position: { x: 50, y: -20 } }],
    });
  }

  return keyframes;
}

describe('SimRuntimeService', () => {
  let service: any;

  beforeEach(() => {
    service = createRuntimeService();
  });

  describe('createSession()', () => {
    it('should create a new session from manifest', async () => {
      const manifest = createTestManifest();

      const sessionId = await (service as any).createSession(manifest);
      const session = await (service as any).getSession(sessionId);

      expect(sessionId).toBeDefined();
      expect(session.sessionId).toBe(sessionId);
      expect(session.manifest.id).toBe(manifest.id);
      expect(session.currentStepIndex).toBe(-1); // Initial state
    });

    it('should create unique session IDs', async () => {
      const manifest = createTestManifest();

      const session1 = await (service as any).createSession(manifest);
      const session2 = await (service as any).createSession(manifest);

      expect(session1).not.toBe(session2);
    });

    it('should store session keyframes', async () => {
      const manifest = createTestManifest({ stepCount: 3 });

      const sessionId = await (service as any).createSession(manifest);
      const session = await (service as any).getSession(sessionId);

      expect(session.currentKeyframe).toBeDefined();
    });
  });

  describe('stepForward()', () => {
    it('should advance to next step', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      const state = await (service as any).stepForward(session);

      expect(state.stepIndex).toBe(0);
    });

    it('should advance through multiple steps', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).stepForward(session);
      await (service as any).stepForward(session);
      const state = await (service as any).stepForward(session);

      expect(state.stepIndex).toBe(2);
    });

    it('should not advance past final step', async () => {
      const manifest = createTestManifest({ stepCount: 3 });
      const session = await (service as any).createSession(manifest);

      // Step to end
      for (let i = 0; i < 5; i++) {
        await (service as any).stepForward(session);
      }

      const state = await (service as any).getState(session) as any;

      expect(state.stepIndex).toBe(2); // 0-indexed, 3 steps = max step 2
    });

    it('should throw error for invalid session', async () => {
      await expect(
        service.stepForward('non-existent-session')
      ).rejects.toThrow();
    });
  });

  describe('stepBackward()', () => {
    it('should go back to previous step', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).stepForward(session);
      await (service as any).stepForward(session);
      await (service as any).stepForward(session);

      const state = await (service as any).stepBackward(session);

      expect(state.stepIndex).toBe(1);
    });

    it('should not go before initial state', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).stepForward(session);

      // Try to go back multiple times
      await (service as any).stepBackward(session);
      await (service as any).stepBackward(session);
      await (service as any).stepBackward(session);

      const state = await (service as any).getState(session) as any;

      expect(state.stepIndex).toBe(-1);
    });
  });

  describe('seekTo()', () => {
    it('should seek to specific step', async () => {
      const manifest = createTestManifest({ stepCount: 10 });
      const session = await (service as any).createSession(manifest);

      const state = await (service as any).seekTo(session, 5000);

      expect(state.stepIndex).toBe(5);
    });

    it('should seek to beginning (step -1)', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).stepForward(session);
      await (service as any).stepForward(session);

      const state = await (service as any).seekTo(session, -1);

      expect(state.stepIndex).toBe(-1);
    });

    it('should clamp to valid range when seeking beyond bounds', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      const state = await (service as any).seekTo(session, 100000);

      expect(state.stepIndex).toBe(4); // Max step is 4 (0-indexed)
    });

    it('should clamp negative values to -1', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      const state = await (service as any).seekTo(session, -100000);

      expect(state.stepIndex).toBe(-1);
    });
  });

  describe('getState()', () => {
    it('should return current state', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).stepForward(session);
      await (service as any).stepForward(session);

      const state = await (service as any).getState(session) as any;

      expect(state.stepIndex).toBe(1);
      expect(state.entities).toBeDefined();
      expect(state.annotations).toBeDefined();
    });

    it('should return entities at current step', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      const state = await (service as any).getState(session) as any;

      expect(state.entities).toHaveLength(2);
    });

    it('should throw for non-existent session', async () => {
      await expect(
        service.getState('non-existent-session')
      ).rejects.toThrow();
    });
  });

  describe('endSession()', () => {
    it('should end an active session', async () => {
      const manifest = createTestManifest();
      const session = await (service as any).createSession(manifest);

      await (service as any).endSession(session);

      await expect(
        service.getState(session) as any
      ).rejects.toThrow();
    });

    it('should not throw when ending non-existent session', async () => {
      await expect(
        service.endSession('non-existent-session')
      ).resolves.not.toThrow();
    });
  });

  describe('listSessions()', () => {
    it('should list all active sessions', async () => {
      const manifest1 = createTestManifest({ id: 'manifest-1' });
      const manifest2 = createTestManifest({ id: 'manifest-2' });

      await (service as any).createSession(manifest1);
      await (service as any).createSession(manifest2);

      const sessions = await (service as any).listSessions();

      expect(sessions).toHaveLength(2);
    });

    it('should not list ended sessions', async () => {
      const manifest = createTestManifest();
      const session = await (service as any).createSession(manifest);

      await (service as any).endSession(session);

      const sessions = await (service as any).listSessions();

      expect(sessions).toHaveLength(0);
    });
  });

  describe('getSession()', () => {
    it('should return session info', async () => {
      const manifest = createTestManifest();
      const session = await (service as any).createSession(manifest);

      const retrieved = await (service as any).getSession(session) as any;

      expect(retrieved).toBeDefined();
      expect(retrieved!.sessionId).toBe(session);
      expect(retrieved!.simulationId).toBe(manifest.id);
    });

    it('should return undefined for non-existent session', async () => {
      const session = await (service as any).getSession('non-existent-session');

      expect(session).toBeFalsy();
    });
  });
});

describe('SimRuntimeService - Interpolation', () => {
  let service: SimRuntimeService;

  beforeEach(() => {
    service = createRuntimeService();
  });

  describe('interpolate()', () => {
    it('should interpolate between keyframes', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      // Seek to step 1
      await (service as any).seekTo(session, 1);

      // Interpolate at 50% between step 1 and 2
      const interpolated = await (service as any).interpolate(session, 0.5);

      expect(interpolated).toBeDefined();
      expect(interpolated.entities).toBeDefined();
    });

    it('should return current state at progress 0', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).seekTo(session, 1);

      const state = await (service as any).getState(session) as any;
      const interpolated = await (service as any).interpolate(session, 0);

      // Entity positions should be equal
      expect(interpolated.entities[0].x).toBe(state.entities[0].x);
    });

    it('should clamp progress to 0-1 range', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).seekTo(session, 1);

      // Should not throw
      await (service as any).interpolate(session, -0.5);
      await (service as any).interpolate(session, 1.5);
    });
  });
});

describe('SimRuntimeService - Playback', () => {
  let service: SimRuntimeService;

  beforeEach(() => {
    service = createRuntimeService();
  });

  describe('play() and pause()', () => {
    it('should start playback', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).play(session);

      const info = await (service as any).getState(session) as any;
      expect(info.isPlaying).toBe(true);
    });

    it('should pause playback', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).play(session);
      await (service as any).pause(session);

      const info = await (service as any).getState(session) as any;
      expect(info.isPlaying).toBe(false);
    });
  });

  describe('setPlaybackSpeed()', () => {
    it('should set playback speed', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      await (service as any).setPlaybackSpeed(session, 2.0);

      const info = await (service as any).getState(session) as any;
      expect(info!.playbackSpeed).toBe(2.0);
    });

    it('should clamp speed to valid range', async () => {
      const manifest = createTestManifest({ stepCount: 5 });
      const session = await (service as any).createSession(manifest);

      // Should clamp to min (0.25)
      await (service as any).setPlaybackSpeed(session, 0.1);
      let info = await (service as any).getState(session) as any;
      expect(info!.playbackSpeed).toBe(0.25);

      // Should clamp to max (4.0)
      await (service as any).setPlaybackSpeed(session, 10.0);
      info = await (service as any).getState(session) as any;
      expect(info!.playbackSpeed).toBe(4.0);
    });
  });
});

describe('SimRuntimeService - Error Handling', () => {
  let service: SimRuntimeService;

  beforeEach(() => {
    service = createRuntimeService();
  });

  it('should handle invalid manifest gracefully', async () => {
    const invalidManifest = {
      id: 'invalid',
      // Missing required fields
    } as unknown as SimulationManifest;

    await expect(
      service.createSession(invalidManifest)
    ).rejects.toThrow();
  });

  it('should maintain state consistency after errors', async () => {
    const manifest = createTestManifest();
    const session = await (service as any).createSession(manifest);

    // Try invalid operation
    try {
      await (service as any).getState('invalid-session');
    } catch {
      // Expected
    }

    // Original session should still work
    const state = await (service as any).getState(session) as any;
    expect(state).toBeDefined();
  });
});
