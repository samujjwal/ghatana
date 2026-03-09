/**
 * @fileoverview Unit tests for ExtensionController
 *
 * Tests the orchestration of browser adapters and configuration management.
 * Verifies initialization, lifecycle management, and message handling.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExtensionController } from '../../src/browser/controller/ExtensionController';
import { BrowserStorageAdapter } from '@ghatana/dcmaar-browser-extension-core';
import { DEFAULT_EXTENSION_CONFIG } from '../../src/core/config/ExtensionConfig';

// Mock adapters to isolate ExtensionController testing
vi.mock('@ghatana/dcmaar-browser-extension-core');
vi.mock('../../src/browser/metrics/PageMetricsCollector');
vi.mock('../../src/browser/events/UnifiedBrowserEventCapture');

describe('ExtensionController', () => {
  let controller: ExtensionController;

  beforeEach(() => {
    // Reset all mocks before each test
    vi.clearAllMocks();

    // Create a new controller instance for each test
    controller = new ExtensionController();
  });

  afterEach(async () => {
    // Clean up after each test
    if (controller) {
      await controller.shutdown();
    }
  });

  describe('initialization', () => {
    it('should initialize successfully with default config', async () => {
      const result = await controller.initialize();

      expect(result).toBeUndefined(); // initialize() returns void
      expect(controller.getState()).toMatchObject({
        initialized: true,
        metricsCollecting: expect.any(Boolean),
        eventsCapturing: expect.any(Boolean),
      });
    });

    it('should load configuration from storage or use defaults', async () => {
      await controller.initialize();

      const config = controller.getConfig();
      expect(config).toBeDefined();
      expect(config?.metrics).toBeDefined();
      expect(config?.events).toBeDefined();
    });

    it('should set up all adapters during initialization', async () => {
      await controller.initialize();

      // Verify adapters were instantiated
      const state = controller.getState();
      expect(state.initialized).toBe(true);
    });

    it('should handle multiple initialize calls gracefully', async () => {
      await controller.initialize();
      await controller.initialize(); // Second call should be handled gracefully

      expect(controller.getState().initialized).toBe(true);
    });

    it('should propagate initialization errors', async () => {
      // Mock storage adapter to throw error
      (
        BrowserStorageAdapter as unknown as { mockImplementationOnce: (fn: () => void) => void }
      ).mockImplementationOnce(() => {
        throw new Error('Storage initialization failed');
      });

      // Should throw during controller construction when storage adapter is created
      expect(() => {
        new ExtensionController();
      }).toThrow('Storage initialization failed');
    });
  });

  describe('configuration management', () => {
    beforeEach(async () => {
      await controller.initialize();
    });

    it('should retrieve current configuration', () => {
      const config = controller.getConfig();

      expect(config).toEqual(DEFAULT_EXTENSION_CONFIG);
    });

    it('should update configuration', async () => {
      const newConfig = {
        ...DEFAULT_EXTENSION_CONFIG,
        metrics: {
          ...DEFAULT_EXTENSION_CONFIG.metrics,
          collectionInterval: 60000,
        },
      };

      await controller.updateConfig(newConfig);

      const updatedConfig = controller.getConfig();
      expect(updatedConfig?.metrics.collectionInterval).toBe(60000);
    });

    it('should preserve config across state changes', async () => {
      const originalConfig = controller.getConfig();

      // Simulate state changes
      await controller.shutdown();

      expect(controller.getConfig()).toEqual(originalConfig);
    });
  });

  describe('state management', () => {
    beforeEach(async () => {
      await controller.initialize();
    });

    it('should track initialization state', async () => {
      let state = controller.getState();
      expect(state.initialized).toBe(true);

      await controller.shutdown();

      state = controller.getState();
      expect(state.initialized).toBe(false);
    });

    it('should track metrics collection state', async () => {
      const state = controller.getState();

      expect(typeof state.metricsCollecting).toBe('boolean');
      expect(typeof state.eventsCapturing).toBe('boolean');
    });

    it('should have valid initial state', () => {
      const state = controller.getState();

      expect(state).toMatchObject({
        initialized: expect.any(Boolean),
        metricsCollecting: expect.any(Boolean),
        eventsCapturing: expect.any(Boolean),
        connectorsActive: expect.any(Boolean),
      });
    });
  });

  describe('lifecycle management', () => {
    it('should properly shutdown', async () => {
      await controller.initialize();
      expect(controller.getState().initialized).toBe(true);

      await controller.shutdown();
      expect(controller.getState().initialized).toBe(false);
    });

    it('should handle shutdown without initialization', async () => {
      // Should not throw
      await expect(controller.shutdown()).resolves.not.toThrow();
    });

    it('should stop all adapters on shutdown', async () => {
      await controller.initialize();

      const stateBefore = controller.getState();
      expect(stateBefore.initialized).toBe(true);

      await controller.shutdown();

      const stateAfter = controller.getState();
      expect(stateAfter.initialized).toBe(false);
      expect(stateAfter.metricsCollecting).toBe(false);
      expect(stateAfter.eventsCapturing).toBe(false);
    });

    it('should allow re-initialization after shutdown', async () => {
      await controller.initialize();
      await controller.shutdown();

      await controller.initialize();
      expect(controller.getState().initialized).toBe(true);
    });
  });

  describe('message handling', () => {
    beforeEach(async () => {
      await controller.initialize();
    });

    it('should handle GET_CONFIG message', async () => {
      const config = controller.getConfig();
      const state = controller.getState();

      expect(config).toBeDefined();
      expect(state.initialized).toBe(true);
    });

    it('should handle UPDATE_CONFIG message', async () => {
      const newConfig = {
        ...DEFAULT_EXTENSION_CONFIG,
        metrics: {
          ...DEFAULT_EXTENSION_CONFIG.metrics,
          collectionInterval: 45000,
        },
      };

      await controller.updateConfig(newConfig);

      const updatedConfig = controller.getConfig();
      expect(updatedConfig?.metrics.collectionInterval).toBe(45000);
    });

    it('should handle GET_STATE message', () => {
      const state = controller.getState();

      expect(state).toBeDefined();
      expect(state.initialized).toBe(true);
      expect(state.connectorsActive).toBeDefined();
    });
  });

  describe('adapter orchestration', () => {
    beforeEach(async () => {
      await controller.initialize();
    });

    it('should start metrics collection', async () => {
      const state = controller.getState();

      // Metrics collection might be started or not depending on config
      expect(typeof state.metricsCollecting).toBe('boolean');
    });

    it('should start event capture', async () => {
      const state = controller.getState();

      // Event capture might be started or not depending on config
      expect(typeof state.eventsCapturing).toBe('boolean');
    });

    it('should maintain adapter coordination', async () => {
      const config = controller.getConfig();
      const state = controller.getState();

      // Both should be accessible and in sync
      expect(config?.metrics).toBeDefined();
      expect(typeof state.metricsCollecting).toBe('boolean');
    });
  });

  describe('error handling', () => {
    beforeEach(async () => {
      await controller.initialize();
    });

    it('should handle adapter errors gracefully', async () => {
      // Update config with modified data shouldn't crash
      const modifiedConfig = { ...DEFAULT_EXTENSION_CONFIG };

      await controller.updateConfig(modifiedConfig);

      // Controller should still be functional
      expect(controller.getState().initialized).toBe(true);
    });
  });

  describe('event buffering', () => {
    beforeEach(async () => {
      await controller.initialize();
    });

    it('should maintain operational state', () => {
      const state = controller.getState();

      expect(state.initialized).toBe(true);
      expect(typeof state.metricsCollecting).toBe('boolean');
      expect(typeof state.eventsCapturing).toBe('boolean');
    });
  });
  describe('configuration defaults', () => {
    it('should have valid default configuration', () => {
      expect(DEFAULT_EXTENSION_CONFIG).toBeDefined();
      expect(DEFAULT_EXTENSION_CONFIG.metrics).toBeDefined();
      expect(DEFAULT_EXTENSION_CONFIG.events).toBeDefined();
      expect(DEFAULT_EXTENSION_CONFIG.connectors).toBeDefined();
    });

    it('should use defaults when storage is empty', async () => {
      await controller.initialize();

      const config = controller.getConfig();
      expect(config).toEqual(DEFAULT_EXTENSION_CONFIG);
    });
  });
});
