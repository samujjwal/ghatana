/**
 * Plugin Loader Tests
 * 
 * Tests for plugin loading, validation, and lifecycle management.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PluginLoader } from '../PluginLoader';
import { PluginState } from '../types';
import type { PluginManifest } from '../types';

describe('PluginLoader', () => {
  let loader: PluginLoader;

  beforeEach(() => {
    loader = new PluginLoader();
  });

  afterEach(() => {
    // Clean up all plugins
    const plugins = loader.getAllPlugins();
    plugins.forEach(plugin => {
      loader.unloadPlugin(plugin.manifest.metadata.id).catch(() => {});
    });
  });

  const mockManifest: PluginManifest = {
    metadata: {
      id: 'test-plugin',
      name: 'Test Plugin',
      version: '1.0.0',
      author: 'Test Author',
      description: 'A test plugin',
      license: 'MIT',
    },
    capabilities: {
      canProcessMetrics: true,
      maxMemoryMB: 50,
      maxCpuPercent: 25,
    },
    main: 'plugin.wasm',
  };

  describe('Manifest Validation', () => {
    it('validates required fields', async () => {
      const invalidManifest = { ...mockManifest };
      delete (invalidManifest.metadata as any).id;

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => invalidManifest,
      });

      await expect(
        loader.loadPlugin('manifest.json', 'plugin.wasm')
      ).rejects.toThrow('Manifest missing metadata.id');
    });

    it('validates version format', async () => {
      const invalidManifest = {
        ...mockManifest,
        metadata: {
          ...mockManifest.metadata,
          version: 'invalid',
        },
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => invalidManifest,
      });

      await expect(
        loader.loadPlugin('manifest.json', 'plugin.wasm')
      ).rejects.toThrow('Invalid version format');
    });

    it('validates resource limits', async () => {
      const invalidManifest = {
        ...mockManifest,
        capabilities: {
          ...mockManifest.capabilities,
          maxMemoryMB: 2000, // Too high
        },
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => invalidManifest,
      });

      await expect(
        loader.loadPlugin('manifest.json', 'plugin.wasm')
      ).rejects.toThrow('maxMemoryMB must be between 1 and 1000');
    });
  });

  describe('Plugin Loading', () => {
    it('loads a valid plugin', async () => {
      // Mock fetch for manifest
      global.fetch = vi.fn()
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockManifest,
        })
        .mockResolvedValueOnce({
          ok: true,
          arrayBuffer: async () => new ArrayBuffer(8),
        });

      // Mock WebAssembly.compile
      global.WebAssembly.compile = vi.fn().mockResolvedValue({});
      global.WebAssembly.instantiate = vi.fn().mockResolvedValue({
        exports: {},
      });

      const plugin = await loader.loadPlugin('manifest.json', 'plugin.wasm');

      expect(plugin.manifest.metadata.id).toBe('test-plugin');
      expect(plugin.state).toBe(PluginState.LOADED);
    });

    it('prevents loading duplicate plugins', async () => {
      global.fetch = vi.fn()
        .mockResolvedValue({
          ok: true,
          json: async () => mockManifest,
        });

      global.WebAssembly.compile = vi.fn().mockResolvedValue({});
      global.WebAssembly.instantiate = vi.fn().mockResolvedValue({
        exports: {},
      });

      // Load first time
      await loader.loadPlugin('manifest.json', 'plugin.wasm');

      // Try to load again
      await expect(
        loader.loadPlugin('manifest.json', 'plugin.wasm')
      ).rejects.toThrow('Plugin already loaded');
    });

    it('handles network errors', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        statusText: 'Not Found',
      });

      await expect(
        loader.loadPlugin('manifest.json', 'plugin.wasm')
      ).rejects.toThrow('Failed to load manifest');
    });
  });

  describe('Plugin Lifecycle', () => {
    let mockPlugin: any;

    beforeEach(async () => {
      global.fetch = vi.fn()
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockManifest,
        })
        .mockResolvedValueOnce({
          ok: true,
          arrayBuffer: async () => new ArrayBuffer(8),
        });

      global.WebAssembly.compile = vi.fn().mockResolvedValue({});
      global.WebAssembly.instantiate = vi.fn().mockResolvedValue({
        exports: {
          activate: vi.fn(),
          deactivate: vi.fn(),
        },
      });

      mockPlugin = await loader.loadPlugin('manifest.json', 'plugin.wasm');
    });

    it('activates a loaded plugin', async () => {
      await loader.activatePlugin(mockPlugin.manifest.metadata.id);
      const plugin = loader.getPlugin(mockPlugin.manifest.metadata.id);
      expect(plugin?.state).toBe(PluginState.ACTIVE);
    });

    it('deactivates an active plugin', async () => {
      await loader.activatePlugin(mockPlugin.manifest.metadata.id);
      await loader.deactivatePlugin(mockPlugin.manifest.metadata.id);
      const plugin = loader.getPlugin(mockPlugin.manifest.metadata.id);
      expect(plugin?.state).toBe(PluginState.LOADED);
    });

    it('unloads a plugin', async () => {
      await loader.unloadPlugin(mockPlugin.manifest.metadata.id);
      const plugin = loader.getPlugin(mockPlugin.manifest.metadata.id);
      expect(plugin).toBeUndefined();
    });

    it('deactivates before unloading if active', async () => {
      await loader.activatePlugin(mockPlugin.manifest.metadata.id);
      await loader.unloadPlugin(mockPlugin.manifest.metadata.id);
      const plugin = loader.getPlugin(mockPlugin.manifest.metadata.id);
      expect(plugin).toBeUndefined();
    });

    it('throws error when activating non-existent plugin', async () => {
      await expect(
        loader.activatePlugin('non-existent')
      ).rejects.toThrow('Plugin not found');
    });
  });

  describe('Plugin Queries', () => {
    it('returns all plugins', async () => {
      const plugins = loader.getAllPlugins();
      expect(Array.isArray(plugins)).toBe(true);
    });

    it('filters plugins by state', async () => {
      global.fetch = vi.fn()
        .mockResolvedValue({
          ok: true,
          json: async () => mockManifest,
        });

      global.WebAssembly.compile = vi.fn().mockResolvedValue({});
      global.WebAssembly.instantiate = vi.fn().mockResolvedValue({
        exports: {},
      });

      await loader.loadPlugin('manifest.json', 'plugin.wasm');

      const loadedPlugins = loader.getPluginsByState(PluginState.LOADED);
      expect(loadedPlugins.length).toBeGreaterThan(0);
      expect(loadedPlugins[0].state).toBe(PluginState.LOADED);
    });

    it('gets plugin by ID', async () => {
      global.fetch = vi.fn()
        .mockResolvedValue({
          ok: true,
          json: async () => mockManifest,
        });

      global.WebAssembly.compile = vi.fn().mockResolvedValue({});
      global.WebAssembly.instantiate = vi.fn().mockResolvedValue({
        exports: {},
      });

      await loader.loadPlugin('manifest.json', 'plugin.wasm');

      const plugin = loader.getPlugin('test-plugin');
      expect(plugin).toBeDefined();
      expect(plugin?.manifest.metadata.id).toBe('test-plugin');
    });
  });
});
