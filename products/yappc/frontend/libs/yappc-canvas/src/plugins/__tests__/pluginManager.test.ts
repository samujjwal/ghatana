/**
 * Plugin Manager Tests
 * 
 * Comprehensive test suite for the plugin architecture system.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import { PluginManager, PluginError, getPluginManager } from '../pluginManager';

import type { Plugin, PluginManifest, PluginContext } from '../types';

describe('PluginManager', () => {
  let manager: PluginManager;

  beforeEach(() => {
    // Reset singleton for each test
    (PluginManager as unknown).instance = null;
    manager = PluginManager.getInstance();
    
    // Clear localStorage
    localStorage.clear();
  });

  describe('Singleton Pattern', () => {
    it('should return same instance', () => {
      const instance1 = PluginManager.getInstance();
      const instance2 = PluginManager.getInstance();
      expect(instance1).toBe(instance2);
    });

    it('should return same instance from getPluginManager', () => {
      const instance1 = getPluginManager();
      const instance2 = PluginManager.getInstance();
      expect(instance1).toBe(instance2);
    });
  });

  describe('Plugin Registration', () => {
    it('should register a valid plugin', async () => {
      const plugin = createTestPlugin();
      await manager.register(plugin);

      const plugins = manager.getPlugins();
      expect(plugins).toHaveLength(1);
      expect(plugins[0].id).toBe('test.plugin');
    });

    it('should call onLoad hook during registration', async () => {
      let capturedState: string | undefined;
      const onLoad = vi.fn((context: PluginContext) => {
        // Capture the state at the moment onLoad is called
        capturedState = context.state;
      });
      const plugin = createTestPlugin({ onLoad });
      
      await manager.register(plugin);
      expect(onLoad).toHaveBeenCalledTimes(1);
      expect(capturedState).toBe('initializing');
      expect(onLoad).toHaveBeenCalledWith(expect.objectContaining({
        manifest: plugin.manifest,
      }));
    });

    it('should auto-activate by default', async () => {
      const onActivate = vi.fn();
      const plugin = createTestPlugin({ onActivate });
      
      await manager.register(plugin);
      expect(onActivate).toHaveBeenCalledTimes(1);
      expect(manager.getState('test.plugin')).toBe('active');
    });

    it('should not auto-activate when autoActivate is false', async () => {
      const onActivate = vi.fn();
      const plugin = createTestPlugin({ onActivate });
      
      await manager.register(plugin, { autoActivate: false });
      expect(onActivate).not.toHaveBeenCalled();
      expect(manager.getState('test.plugin')).toBe('disabled');
    });

    it('should reject plugin with invalid manifest', async () => {
      const invalidPlugin = {
        manifest: {
          // Missing required fields
        } as PluginManifest,
      } as Plugin;

      await expect(manager.register(invalidPlugin)).rejects.toThrow();
    });

    it('should reject plugin with invalid version format', async () => {
      const plugin = createTestPlugin({
        manifest: { version: 'invalid' },
      });

      await expect(manager.register(plugin)).rejects.toThrow('semver format');
    });

    it('should reject duplicate plugin registration', async () => {
      const plugin = createTestPlugin();
      
      await manager.register(plugin);
      await expect(manager.register(plugin)).rejects.toThrow('already registered');
    });

    it('should handle plugin load errors', async () => {
      const onLoad = vi.fn().mockRejectedValue(new Error('Load failed'));
      const plugin = createTestPlugin({ onLoad });

      await expect(manager.register(plugin)).rejects.toThrow('Failed to load plugin');
      expect(manager.getPlugins()).toHaveLength(0);
    });
  });

  describe('Plugin Lifecycle', () => {
    it('should activate a plugin', async () => {
      const onActivate = vi.fn();
      const plugin = createTestPlugin({ onActivate });
      
      await manager.register(plugin, { autoActivate: false });
      await manager.activate('test.plugin');
      
      expect(onActivate).toHaveBeenCalledTimes(1);
      expect(manager.getState('test.plugin')).toBe('active');
    });

    it('should deactivate a plugin', async () => {
      const onDeactivate = vi.fn();
      const plugin = createTestPlugin({ onDeactivate });
      
      await manager.register(plugin);
      await manager.deactivate('test.plugin');
      
      expect(onDeactivate).toHaveBeenCalledTimes(1);
      expect(manager.getState('test.plugin')).toBe('disabled');
    });

    it('should pause and resume a plugin', async () => {
      const onPause = vi.fn();
      const onResume = vi.fn();
      const plugin = createTestPlugin({ onPause, onResume });
      
      await manager.register(plugin);
      await manager.pause('test.plugin');
      
      expect(onPause).toHaveBeenCalledTimes(1);
      expect(manager.getState('test.plugin')).toBe('paused');
      
      await manager.resume('test.plugin');
      
      expect(onResume).toHaveBeenCalledTimes(1);
      expect(manager.getState('test.plugin')).toBe('active');
    });

    it('should unregister a plugin', async () => {
      const onUninstall = vi.fn();
      const plugin = createTestPlugin({ onUninstall });
      
      await manager.register(plugin);
      await manager.unregister('test.plugin');
      
      expect(onUninstall).toHaveBeenCalledTimes(1);
      expect(manager.getPlugins()).toHaveLength(0);
    });

    it('should deactivate before unregistering', async () => {
      const onDeactivate = vi.fn();
      const onUninstall = vi.fn();
      const plugin = createTestPlugin({ onDeactivate, onUninstall });
      
      await manager.register(plugin);
      await manager.unregister('test.plugin');
      
      expect(onDeactivate).toHaveBeenCalled();
      expect(onUninstall).toHaveBeenCalled();
    });

    it('should handle activation errors', async () => {
      const onActivate = vi.fn().mockRejectedValue(new Error('Activation failed'));
      const plugin = createTestPlugin({ onActivate });
      
      await manager.register(plugin, { autoActivate: false });
      await expect(manager.activate('test.plugin')).rejects.toThrow('Failed to activate');
      expect(manager.getState('test.plugin')).toBe('error');
    });
  });

  describe('Plugin Dependencies', () => {
    it('should allow plugin with satisfied dependencies', async () => {
      const plugin1 = createTestPlugin();
      const plugin2 = createTestPlugin({
        manifest: {
          id: 'test.plugin2',
          dependencies: ['test.plugin'],
        },
      });

      await manager.register(plugin1);
      await manager.register(plugin2);

      expect(manager.getPlugins()).toHaveLength(2);
    });

    it('should reject plugin with missing dependencies', async () => {
      const plugin = createTestPlugin({
        manifest: {
          dependencies: ['missing.plugin'],
        },
      });

      await expect(manager.register(plugin)).rejects.toThrow('Missing required dependency');
    });

    it('should reject plugin when dependency is not active', async () => {
      const plugin1 = createTestPlugin();
      const plugin2 = createTestPlugin({
        manifest: {
          id: 'test.plugin2',
          dependencies: ['test.plugin'],
        },
      });

      await manager.register(plugin1, { autoActivate: false });
      await expect(manager.register(plugin2)).rejects.toThrow('not active');
    });

    it('should allow optional dependencies to be missing', async () => {
      const plugin = createTestPlugin({
        manifest: {
          optionalDependencies: ['missing.plugin'],
        },
      });

      await expect(manager.register(plugin)).resolves.not.toThrow();
    });
  });

  describe('Plugin Context', () => {
    let context: PluginContext;

    beforeEach(async () => {
      const plugin = createTestPlugin({
        onLoad: (ctx) => {
          context = ctx;
        },
      });
      await manager.register(plugin);
    });

    it('should provide manifest in context', () => {
      expect(context.manifest.id).toBe('test.plugin');
      expect(context.manifest.name).toBe('Test Plugin');
    });

    it('should provide canvas API', () => {
      expect(context.canvas).toBeDefined();
      expect(context.canvas.getDocument).toBeDefined();
      expect(context.canvas.executeCommand).toBeDefined();
      expect(context.canvas.registerCommand).toBeDefined();
    });

    it('should provide storage API', () => {
      expect(context.storage).toBeDefined();
      expect(context.storage.get).toBeDefined();
      expect(context.storage.set).toBeDefined();
      expect(context.storage.delete).toBeDefined();
      expect(context.storage.clear).toBeDefined();
    });

    it('should provide events API', () => {
      expect(context.events).toBeDefined();
      expect(context.events.on).toBeDefined();
      expect(context.events.once).toBeDefined();
      expect(context.events.emit).toBeDefined();
    });

    it('should provide UI API', () => {
      expect(context.ui).toBeDefined();
      expect(context.ui.notify).toBeDefined();
      expect(context.ui.showDialog).toBeDefined();
      expect(context.ui.registerPanel).toBeDefined();
    });

    it('should provide logger', () => {
      expect(context.logger).toBeDefined();
      expect(context.logger.debug).toBeDefined();
      expect(context.logger.info).toBeDefined();
      expect(context.logger.warn).toBeDefined();
      expect(context.logger.error).toBeDefined();
    });
  });

  describe('Plugin Storage', () => {
    let context: PluginContext;

    beforeEach(async () => {
      const plugin = createTestPlugin({
        onLoad: (ctx) => {
          context = ctx;
        },
      });
      await manager.register(plugin);
    });

    it('should store and retrieve values', async () => {
      await context.storage.set('key1', 'value1');
      const value = await context.storage.get('key1');
      expect(value).toBe('value1');
    });

    it('should handle complex objects', async () => {
      const obj = { foo: 'bar', nested: { baz: 123 } };
      await context.storage.set('obj', obj);
      const retrieved = await context.storage.get('obj');
      expect(retrieved).toEqual(obj);
    });

    it('should delete values', async () => {
      await context.storage.set('key1', 'value1');
      await context.storage.delete('key1');
      const value = await context.storage.get('key1');
      expect(value).toBeUndefined();
    });

    it('should clear all values', async () => {
      await context.storage.set('key1', 'value1');
      await context.storage.set('key2', 'value2');
      await context.storage.clear();
      
      const value1 = await context.storage.get('key1');
      const value2 = await context.storage.get('key2');
      expect(value1).toBeUndefined();
      expect(value2).toBeUndefined();
    });

    it('should list all keys', async () => {
      await context.storage.set('key1', 'value1');
      await context.storage.set('key2', 'value2');
      
      const keys = await context.storage.keys();
      expect(keys).toContain('key1');
      expect(keys).toContain('key2');
      expect(keys).toHaveLength(2);
    });

    it('should isolate storage between plugins', async () => {
      const plugin2 = createTestPlugin({
        manifest: { id: 'test.plugin2' },
        onLoad: async (ctx) => {
          await ctx.storage.set('shared-key', 'plugin2-value');
        },
      });
      
      await context.storage.set('shared-key', 'plugin1-value');
      await manager.register(plugin2);
      
      const value1 = await context.storage.get('shared-key');
      expect(value1).toBe('plugin1-value');
    });
  });

  describe('Plugin Events', () => {
    let context: PluginContext;

    beforeEach(async () => {
      const plugin = createTestPlugin({
        onLoad: (ctx) => {
          context = ctx;
        },
      });
      await manager.register(plugin);
    });

    it('should subscribe to events', () => {
      const handler = vi.fn();
      const unsubscribe = context.events.on('element:created', handler);
      
      manager.emitEvent('element:created', { id: 'test' });
      expect(handler).toHaveBeenCalledWith({ id: 'test' });
      
      unsubscribe();
    });

    it('should unsubscribe from events', () => {
      const handler = vi.fn();
      const unsubscribe = context.events.on('element:created', handler);
      
      unsubscribe();
      manager.emitEvent('element:created', { id: 'test' });
      
      expect(handler).not.toHaveBeenCalled();
    });

    it('should subscribe once', () => {
      const handler = vi.fn();
      context.events.once('element:created', handler);
      
      manager.emitEvent('element:created', { id: 'test1' });
      manager.emitEvent('element:created', { id: 'test2' });
      
      expect(handler).toHaveBeenCalledTimes(1);
      expect(handler).toHaveBeenCalledWith({ id: 'test1' });
    });

    it('should handle multiple listeners for same event', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();
      
      context.events.on('element:created', handler1);
      context.events.on('element:created', handler2);
      
      manager.emitEvent('element:created', { id: 'test' });
      
      expect(handler1).toHaveBeenCalledWith({ id: 'test' });
      expect(handler2).toHaveBeenCalledWith({ id: 'test' });
    });

    it('should handle errors in event handlers', () => {
      const errorHandler = vi.fn(() => {
        throw new Error('Handler error');
      });
      const successHandler = vi.fn();
      
      context.events.on('element:created', errorHandler);
      context.events.on('element:created', successHandler);
      
      // Should not throw and should call all handlers
      expect(() => {
        manager.emitEvent('element:created', { id: 'test' });
      }).not.toThrow();
      
      expect(successHandler).toHaveBeenCalled();
    });
  });

  describe('Plugin Queries', () => {
    it('should get all plugins', async () => {
      const plugin1 = createTestPlugin();
      const plugin2 = createTestPlugin({ manifest: { id: 'test.plugin2' } });
      
      await manager.register(plugin1);
      await manager.register(plugin2);
      
      const plugins = manager.getPlugins();
      expect(plugins).toHaveLength(2);
      expect(plugins.map((p) => p.id)).toContain('test.plugin');
      expect(plugins.map((p) => p.id)).toContain('test.plugin2');
    });

    it('should get only active plugins', async () => {
      const plugin1 = createTestPlugin();
      const plugin2 = createTestPlugin({ manifest: { id: 'test.plugin2' } });
      
      await manager.register(plugin1);
      await manager.register(plugin2, { autoActivate: false });
      
      const activePlugins = manager.getActivePlugins();
      expect(activePlugins).toHaveLength(1);
      expect(activePlugins[0].id).toBe('test.plugin');
    });

    it('should get plugin state', async () => {
      const plugin = createTestPlugin();
      await manager.register(plugin);
      
      expect(manager.getState('test.plugin')).toBe('active');
    });

    it('should return undefined for unknown plugin', () => {
      expect(manager.getState('unknown.plugin')).toBeUndefined();
    });
  });

  describe('PluginError', () => {
    it('should create error with correct properties', () => {
      const error = new PluginError('Test error', 'test.plugin', 'RUNTIME_ERROR');
      
      expect(error.message).toBe('Test error');
      expect(error.pluginId).toBe('test.plugin');
      expect(error.code).toBe('RUNTIME_ERROR');
      expect(error.name).toBe('PluginError');
    });

    it('should be instanceof Error', () => {
      const error = new PluginError('Test', 'test', 'RUNTIME_ERROR');
      expect(error).toBeInstanceOf(Error);
    });
  });
});

// Test helpers

function createTestPlugin(overrides?: Partial<Plugin>): Plugin {
  const defaultManifest: PluginManifest = {
    id: 'test.plugin',
    name: 'Test Plugin',
    version: '1.0.0',
    author: { name: 'Test Author' },
    description: 'A test plugin',
    minCanvasVersion: '1.0.0',
    ...overrides?.manifest,
  };

  return {
    manifest: defaultManifest,
    onLoad: overrides?.onLoad,
    onActivate: overrides?.onActivate,
    onPause: overrides?.onPause,
    onResume: overrides?.onResume,
    onDeactivate: overrides?.onDeactivate,
    onUninstall: overrides?.onUninstall,
    onError: overrides?.onError,
  };
}
