/**
 * Plugin System Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

import { PluginManager, type Plugin, type PluginPermissions } from '../pluginSystem';

import type { CanvasDocument, CanvasElement } from '../../types/canvas-document';

describe.skip('PluginManager', () => {
  let manager: PluginManager;
  const mockDocument: CanvasDocument = {
    id: 'test',
    version: '1.0.0',
    title: 'Test',
    viewport: { center: { x: 0, y: 0 }, zoom: 1.0 },
    elements: {},
    elementOrder: [],
    metadata: {},
    capabilities: {
      canEdit: true,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: true,
      canRedo: true,
      canExport: true,
      canImport: true,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  beforeEach(() => {
    manager = new PluginManager();
    manager.setDocumentAccessor(() => mockDocument);
    manager.setElementAccessor({
      get: (id: string) => mockDocument.elements[id] || null,
      add: (element: Partial<CanvasElement>) => 'new-id',
      update: (id: string, changes: Partial<CanvasElement>) => true,
      remove: (id: string) => true,
    });
  });

  describe('Plugin Registration', () => {
    it('should register a plugin', async () => {
      const plugin: Plugin = {
        metadata: {
          id: 'test-plugin',
          name: 'Test Plugin',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
      };

      await manager.register(plugin);
      
      const plugins = manager.getPlugins();
      expect(plugins).toHaveLength(1);
      expect(plugins[0]).toBe(plugin);
    });

    it('should call plugin init during registration', async () => {
      const initSpy = vi.fn();
      const plugin: Plugin = {
        metadata: {
          id: 'test-plugin',
          name: 'Test',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: initSpy,
      };

      await manager.register(plugin);
      
      expect(initSpy).toHaveBeenCalledTimes(1);
    });

    it('should reject duplicate plugin IDs', async () => {
      const plugin1: Plugin = {
        metadata: {
          id: 'duplicate',
          name: 'Plugin 1',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
      };

      const plugin2: Plugin = {
        ...plugin1,
        metadata: { ...plugin1.metadata, name: 'Plugin 2' },
      };

      await manager.register(plugin1);
      
      await expect(manager.register(plugin2)).rejects.toThrow('already registered');
    });
  });

  describe('Plugin Activation', () => {
    it('should activate a plugin', async () => {
      const activateSpy = vi.fn();
      const plugin: Plugin = {
        metadata: {
          id: 'test-plugin',
          name: 'Test',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
        activate: activateSpy,
      };

      await manager.register(plugin);
      await manager.activate('test-plugin');
      
      expect(activateSpy).toHaveBeenCalledTimes(1);
      expect(manager.isActive('test-plugin')).toBe(true);
    });

    it('should deactivate a plugin', async () => {
      const deactivateSpy = vi.fn();
      const plugin: Plugin = {
        metadata: {
          id: 'test-plugin',
          name: 'Test',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
        deactivate: deactivateSpy,
      };

      await manager.register(plugin);
      await manager.activate('test-plugin');
      await manager.deactivate('test-plugin');
      
      expect(deactivateSpy).toHaveBeenCalledTimes(1);
      expect(manager.isActive('test-plugin')).toBe(false);
    });

    it('should not activate non-existent plugin', async () => {
      await expect(manager.activate('non-existent')).rejects.toThrow('not found');
    });
  });

  describe('Permission System', () => {
    it('should allow read access with readDocument permission', async () => {
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'reader-plugin',
          name: 'Reader',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      const doc = capturedAPI.getDocument();
      expect(doc).toEqual(mockDocument);
    });

    it('should deny read access without permission', async () => {
      const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'no-read-plugin',
          name: 'No Read',
          version: '1.0.0',
          permissions: {
            readDocument: false,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      const doc = capturedAPI.getDocument();
      expect(doc).toBeNull();
      expect(consoleWarn).toHaveBeenCalled();
      
      consoleWarn.mockRestore();
    });

    it('should allow write access with writeDocument permission', async () => {
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'writer-plugin',
          name: 'Writer',
          version: '1.0.0',
          permissions: {
            readDocument: false,
            writeDocument: true,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      const id = capturedAPI.addElement({ type: 'node' });
      expect(id).toBe('new-id');
    });

    it('should deny write access without permission', async () => {
      const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'no-write-plugin',
          name: 'No Write',
          version: '1.0.0',
          permissions: {
            readDocument: false,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      const id = capturedAPI.addElement({ type: 'node' });
      expect(id).toBeNull();
      
      consoleWarn.mockRestore();
    });
  });

  describe('Event System', () => {
    it('should allow event subscription with permission', async () => {
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'event-plugin',
          name: 'Events',
          version: '1.0.0',
          permissions: {
            readDocument: false,
            writeDocument: false,
            subscribeEvents: true,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      const handler = vi.fn();
      capturedAPI.on('test-event', handler);
      
      manager.emit('test-event', 'data');
      
      expect(handler).toHaveBeenCalledWith('data');
    });

    it('should support event unsubscription', async () => {
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'event-plugin',
          name: 'Events',
          version: '1.0.0',
          permissions: {
            readDocument: false,
            writeDocument: false,
            subscribeEvents: true,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      const handler = vi.fn();
      const unsubscribe = capturedAPI.on('test-event', handler);
      
      unsubscribe();
      manager.emit('test-event', 'data');
      
      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('Tool Registration', () => {
    it('should register tools with permission', async () => {
      let capturedAPI: unknown;
      const plugin: Plugin = {
        metadata: {
          id: 'tool-plugin',
          name: 'Tools',
          version: '1.0.0',
          permissions: {
            readDocument: false,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: true,
            modifyRendering: false,
          },
        },
        init: (api) => {
          capturedAPI = api;
        },
      };

      await manager.register(plugin);
      
      capturedAPI.registerTool({
        id: 'custom-tool',
        name: 'Custom Tool',
        onActivate: vi.fn(),
        onDeactivate: vi.fn(),
      });
      
      const tools = manager.getTools();
      expect(tools).toHaveLength(1);
      expect(tools[0].id).toBe('custom-tool');
    });
  });

  describe('Cleanup', () => {
    it('should unregister plugin and clean up', async () => {
      const destroySpy = vi.fn();
      const plugin: Plugin = {
        metadata: {
          id: 'cleanup-plugin',
          name: 'Cleanup',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
        destroy: destroySpy,
      };

      await manager.register(plugin);
      await manager.unregister('cleanup-plugin');
      
      expect(destroySpy).toHaveBeenCalledTimes(1);
      expect(manager.getPlugins()).toHaveLength(0);
    });

    it('should destroy all plugins', async () => {
      const plugin1: Plugin = {
        metadata: {
          id: 'plugin-1',
          name: 'Plugin 1',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
      };

      const plugin2: Plugin = {
        metadata: {
          id: 'plugin-2',
          name: 'Plugin 2',
          version: '1.0.0',
          permissions: {
            readDocument: true,
            writeDocument: false,
            subscribeEvents: false,
            registerTools: false,
            modifyRendering: false,
          },
        },
        init: vi.fn(),
      };

      await manager.register(plugin1);
      await manager.register(plugin2);
      
      await manager.destroy();
      
      expect(manager.getPlugins()).toHaveLength(0);
    });
  });
});
