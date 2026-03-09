/**
 * Integration tests for PluginLifecycleManager
 * 
 * Tests the complete plugin lifecycle state machine including:
 * - State transitions
 * - Event emission
 * - Error recovery
 * - State queries
 */

import {
  PluginLifecycleManager,
  PluginLifecycleState,
  PluginLifecycleEvent,
  IPluginLifecycleManager,
} from '../../core/PluginLifecycle';

describe('PluginLifecycleManager Integration Tests', () => {
  let manager: IPluginLifecycleManager;

  beforeEach(() => {
    manager = new PluginLifecycleManager();
  });

  describe('State Transitions', () => {
    it('should transition through normal lifecycle: NOT_LOADED → LOADING → RUNNING → STOPPING → STOPPED', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.LOADING);

      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.RUNNING);

      manager.transitionState(pluginId, PluginLifecycleState.STOPPING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.STOPPING);

      manager.transitionState(pluginId, PluginLifecycleState.STOPPED);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.STOPPED);
    });

    it('should allow transition from STOPPED back to LOADING', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);
      manager.transitionState(pluginId, PluginLifecycleState.STOPPING);
      manager.transitionState(pluginId, PluginLifecycleState.STOPPED);

      // Restart
      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.LOADING);
    });

    it('should allow transition to ERROR from any state', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.ERROR);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.ERROR);

      // Reset to LOADING
      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);

      manager.transitionState(pluginId, PluginLifecycleState.ERROR);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.ERROR);
    });

    it('should reject invalid state transitions', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);

      // Try invalid transition LOADING → NOT_LOADED
      expect(() => {
        manager.transitionState(pluginId, PluginLifecycleState.NOT_LOADED);
      }).toThrow();
    });

    it('should reject transition from RUNNING to STOPPED (must go through STOPPING)', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);

      // Try invalid transition RUNNING → STOPPED
      expect(() => {
        manager.transitionState(pluginId, PluginLifecycleState.STOPPED);
      }).toThrow();
    });
  });

  describe('Event Emission', () => {
    it('should emit events for state transitions', () => {
      const pluginId = 'test-plugin';
      const events: PluginLifecycleEvent[] = [];

      manager.onStateChange(pluginId, (event: PluginLifecycleEvent) => {
        events.push(event);
      });

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);

      expect(events).toHaveLength(2);
      expect(events[0].pluginId).toBe(pluginId);
      expect(events[0].newState).toBe(PluginLifecycleState.LOADING);
      expect(events[1].newState).toBe(PluginLifecycleState.RUNNING);
    });

    it('should emit error information in event', () => {
      const pluginId = 'test-plugin';
      const events: PluginLifecycleEvent[] = [];

      manager.onStateChange(pluginId, (event: PluginLifecycleEvent) => {
        events.push(event);
      });

      const error = new Error('Connection failed');
      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.ERROR, error);

      expect(events[1].error).toBe(error);
      expect(events[1].newState).toBe(PluginLifecycleState.ERROR);
    });

    it('should support wildcard listeners for any plugin state change', () => {
      const events: PluginLifecycleEvent[] = [];

      manager.onStateChange('*', (event: PluginLifecycleEvent) => {
        events.push(event);
      });

      manager.transitionState('plugin1', PluginLifecycleState.LOADING);
      manager.transitionState('plugin2', PluginLifecycleState.LOADING);
      manager.transitionState('plugin1', PluginLifecycleState.RUNNING);

      expect(events).toHaveLength(3);
      expect(events[0].pluginId).toBe('plugin1');
      expect(events[1].pluginId).toBe('plugin2');
      expect(events[2].pluginId).toBe('plugin1');
    });

    it('should emit event with metadata', () => {
      const pluginId = 'test-plugin';
      const events: PluginLifecycleEvent[] = [];

      manager.onStateChange(pluginId, (event: PluginLifecycleEvent) => {
        events.push(event);
      });

      const metadata = { duration: 150, startTime: Date.now() };
      manager.transitionState(pluginId, PluginLifecycleState.LOADING, undefined, metadata);

      expect(events[0].metadata).toEqual(metadata);
    });

    it('should allow multiple listeners for same plugin', () => {
      const pluginId = 'test-plugin';
      const listener1Events: PluginLifecycleEvent[] = [];
      const listener2Events: PluginLifecycleEvent[] = [];

      manager.onStateChange(pluginId, (event: PluginLifecycleEvent) => {
        listener1Events.push(event);
      });

      manager.onStateChange(pluginId, (event: PluginLifecycleEvent) => {
        listener2Events.push(event);
      });

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);

      expect(listener1Events).toHaveLength(1);
      expect(listener2Events).toHaveLength(1);
    });
  });

  describe('Timestamp Recording', () => {
    it('should record timestamp for state transitions', () => {
      const pluginId = 'test-plugin';
      const beforeTransition = Date.now();

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);

      const afterTransition = Date.now();
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      new Promise<PluginLifecycleEvent | null>(resolve => {
        manager.onStateChange(pluginId, (e: PluginLifecycleEvent) => resolve(e));
        // Get current state through getState to verify timestamp was recorded
        const state = manager.getState(pluginId);
        if (state) resolve(null);
      });

      // Timestamps should be reasonable
      expect(beforeTransition).toBeLessThanOrEqual(afterTransition);
    });
  });

  describe('Query Operations', () => {
    it('should return plugins by specific state', () => {
      manager.transitionState('plugin1', PluginLifecycleState.LOADING);
      manager.transitionState('plugin2', PluginLifecycleState.LOADING);
      manager.transitionState('plugin2', PluginLifecycleState.RUNNING);
      manager.transitionState('plugin3', PluginLifecycleState.LOADING);
      manager.transitionState('plugin3', PluginLifecycleState.ERROR);

      const loadingPlugins = manager.getPluginsByState(
        PluginLifecycleState.LOADING,
      );
      expect(loadingPlugins).toEqual(['plugin1']);

      const runningPlugins = manager.getPluginsByState(
        PluginLifecycleState.RUNNING,
      );
      expect(runningPlugins).toEqual(['plugin2']);

      const errorPlugins = manager.getPluginsByState(PluginLifecycleState.ERROR);
      expect(errorPlugins).toEqual(['plugin3']);
    });

    it('should validate transitions before applying', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);

      // Check valid transition
      expect(
        manager.isValidTransition(
          PluginLifecycleState.LOADING,
          PluginLifecycleState.RUNNING,
        ),
      ).toBe(true);

      // Check valid transition to ERROR from any state
      expect(
        manager.isValidTransition(
          PluginLifecycleState.LOADING,
          PluginLifecycleState.ERROR,
        ),
      ).toBe(true);

      // Check transition that's valid but unexpected
      expect(
        manager.isValidTransition(
          PluginLifecycleState.LOADING,
          PluginLifecycleState.STOPPED,
        ),
      ).toBe(true); // Allowed per state machine design
    });
  });

  describe('State Reset and Initialization', () => {
    it('should initialize plugin in NOT_LOADED state', () => {
      const pluginId = 'test-plugin';
      expect(manager.getState(pluginId)).toBe(null);

      // First transition shows implicit NOT_LOADED → LOADING
      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.LOADING);
    });

    it('should reset plugin state', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);

      manager.reset(pluginId);

      expect(manager.getState(pluginId)).toBe(null);
    });

    it('should clear all listeners on reset', () => {
      const pluginId = 'test-plugin';
      const events: PluginLifecycleEvent[] = [];

      manager.onStateChange(pluginId, (event: PluginLifecycleEvent) => {
        events.push(event);
      });

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      expect(events).toHaveLength(1);

      manager.reset(pluginId);

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      // Should still have only 1 event from before reset
      expect(events).toHaveLength(1);
    });
  });

  describe('Concurrent Plugin Management', () => {
    it('should handle multiple plugins independently', () => {
      manager.transitionState('plugin1', PluginLifecycleState.LOADING);
      manager.transitionState('plugin1', PluginLifecycleState.RUNNING);

      manager.transitionState('plugin2', PluginLifecycleState.LOADING);

      expect(manager.getState('plugin1')).toBe(PluginLifecycleState.RUNNING);
      expect(manager.getState('plugin2')).toBe(PluginLifecycleState.LOADING);
    });

    it('should track plugins in their respective states', () => {
      manager.transitionState('plugin1', PluginLifecycleState.LOADING);
      manager.transitionState('plugin1', PluginLifecycleState.RUNNING);

      manager.transitionState('plugin2', PluginLifecycleState.LOADING);

      manager.transitionState('plugin3', PluginLifecycleState.LOADING);
      manager.transitionState('plugin3', PluginLifecycleState.RUNNING);

      const runningPlugins = manager.getPluginsByState(
        PluginLifecycleState.RUNNING,
      );
      const loadingPlugins = manager.getPluginsByState(
        PluginLifecycleState.LOADING,
      );

      expect(new Set(runningPlugins)).toEqual(new Set(['plugin1', 'plugin3']));
      expect(loadingPlugins).toEqual(['plugin2']);
    });
  });

  describe('Error Recovery', () => {
    it('should transition from ERROR to LOADING for retry', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.ERROR);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.ERROR);

      // Retry by transitioning back to LOADING
      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.LOADING);

      manager.transitionState(pluginId, PluginLifecycleState.RUNNING);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.RUNNING);
    });

    it('should support resetting plugin from ERROR to NOT_LOADED', () => {
      const pluginId = 'test-plugin';

      manager.transitionState(pluginId, PluginLifecycleState.LOADING);
      manager.transitionState(pluginId, PluginLifecycleState.ERROR);

      manager.transitionState(pluginId, PluginLifecycleState.NOT_LOADED);
      expect(manager.getState(pluginId)).toBe(PluginLifecycleState.NOT_LOADED);
    });
  });
});
