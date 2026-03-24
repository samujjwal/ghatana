/**
 * Plugin System for Canvas Customization
 *
 * Provides a safe, sandboxed environment for custom plugins to extend canvas functionality.
 * Plugins can register tools, add custom elements, modify rendering, and react to events.
 *
 * Features:
 * - Plugin lifecycle (init, activate, deactivate, destroy)
 * - Permission-based API access
 * - Resource cleanup and teardown
 * - Event subscription
 * - Custom tool registration
 *
 * @module theming/pluginSystem
 */

import type { CanvasElement, CanvasDocument } from '../types/canvas-document';

/**
 * Plugin permissions
 */
export interface PluginPermissions {
  readonly readDocument: boolean;
  readonly writeDocument: boolean;
  readonly subscribeEvents: boolean;
  readonly registerTools: boolean;
  readonly modifyRendering: boolean;
}

/**
 * Plugin metadata
 */
export interface PluginMetadata {
  readonly id: string;
  readonly name: string;
  readonly version: string;
  readonly author?: string;
  readonly description?: string;
  readonly permissions: PluginPermissions;
}

/**
 * Plugin API context provided to plugins
 */
export interface PluginAPI {
  // Document access (requires readDocument permission)
  readonly getDocument: () => CanvasDocument | null;
  readonly getElement: (id: string) => CanvasElement | null;

  // Document mutation (requires writeDocument permission)
  readonly addElement: (element: Partial<CanvasElement>) => string | null;
  readonly updateElement: (id: string, changes: Partial<CanvasElement>) => boolean;
  readonly removeElement: (id: string) => boolean;

  // Event subscription (requires subscribeEvents permission)
  readonly on: (event: string, handler: (...args: unknown[]) => void) => () => void;

  // Tool registration (requires registerTools permission)
  readonly registerTool: (tool: PluginTool) => () => void;

  // Rendering hooks (requires modifyRendering permission)
  readonly addRenderHook: (hook: RenderHook) => () => void;
}

/**
 * Custom tool definition
 */
export interface PluginTool {
  readonly id: string;
  readonly name: string;
  readonly icon?: string;
  readonly hotkey?: string;
  readonly onActivate: () => void;
  readonly onDeactivate: () => void;
}

/**
 * Render hook for custom rendering
 */
export interface RenderHook {
  readonly id: string;
  readonly priority: number; // Higher = runs later
  readonly render: (ctx: CanvasRenderingContext2D, document: CanvasDocument) => void;
}

/**
 * Plugin interface that all plugins must implement
 */
export interface Plugin {
  readonly metadata: PluginMetadata;
  init(api: PluginAPI): void | Promise<void>;
  activate?(): void | Promise<void>;
  deactivate?(): void | Promise<void>;
  destroy?(): void | Promise<void>;
}

/**
 * Plugin registry and lifecycle manager
 */
export class PluginManager {
  private plugins: Map<string, Plugin> = new Map();
  private activePlugins: Set<string> = new Set();
  private eventHandlers: Map<string, Set<(...args: unknown[]) => void>> = new Map();
  private registeredTools: Map<string, PluginTool> = new Map();
  private renderHooks: RenderHook[] = [];
  private documentGetter: (() => CanvasDocument | null) | null = null;
  private elementAccessor: {
    get: (id: string) => CanvasElement | null;
    add: (element: Partial<CanvasElement>) => string;
    update: (id: string, changes: Partial<CanvasElement>) => boolean;
    remove: (id: string) => boolean;
  } | null = null;

  /**
   * Set document accessor for plugins
   */
  setDocumentAccessor(getter: () => CanvasDocument | null): void {
    this.documentGetter = getter;
  }

  /**
   * Set element accessor for plugins
   */
  setElementAccessor(accessor: {
    get: (id: string) => CanvasElement | null;
    add: (element: Partial<CanvasElement>) => string;
    update: (id: string, changes: Partial<CanvasElement>) => boolean;
    remove: (id: string) => boolean;
  }): void {
    this.elementAccessor = accessor;
  }

  /**
   * Register a plugin
   */
  async register(plugin: Plugin): Promise<void> {
    const { id } = plugin.metadata;

    if (this.plugins.has(id)) {
      throw new Error(`Plugin "${id}" is already registered`);
    }

    // Create plugin API with permission checks
    const api = this.createPluginAPI(plugin);

    // Initialize plugin
    try {
      await plugin.init(api);
      this.plugins.set(id, plugin);
    } catch (error) {
      throw new Error(`Failed to initialize plugin "${id}": ${error}`);
    }
  }

  /**
   * Unregister a plugin
   */
  async unregister(pluginId: string): Promise<void> {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) return;

    // Deactivate if active
    if (this.activePlugins.has(pluginId)) {
      await this.deactivate(pluginId);
    }

    // Destroy plugin
    if (plugin.destroy) {
      await plugin.destroy();
    }

    // Clean up resources
    this.plugins.delete(pluginId);
    this.cleanupPluginResources(pluginId);
  }

  /**
   * Activate a plugin
   */
  async activate(pluginId: string): Promise<void> {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) {
      throw new Error(`Plugin "${pluginId}" not found`);
    }

    if (this.activePlugins.has(pluginId)) {
      return; // Already active
    }

    if (plugin.activate) {
      await plugin.activate();
    }

    this.activePlugins.add(pluginId);
  }

  /**
   * Deactivate a plugin
   */
  async deactivate(pluginId: string): Promise<void> {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) return;

    if (!this.activePlugins.has(pluginId)) {
      return; // Not active
    }

    if (plugin.deactivate) {
      await plugin.deactivate();
    }

    this.activePlugins.delete(pluginId);
  }

  /**
   * Check if plugin is active
   */
  isActive(pluginId: string): boolean {
    return this.activePlugins.has(pluginId);
  }

  /**
   * Get list of registered plugins
   */
  getPlugins(): Plugin[] {
    return Array.from(this.plugins.values());
  }

  /**
   * Get registered tools
   */
  getTools(): PluginTool[] {
    return Array.from(this.registeredTools.values());
  }

  /**
   * Emit an event to all subscribers
   */
  emit(event: string, ...args: unknown[]): void {
    const handlers = this.eventHandlers.get(event);
    if (!handlers) return;

    handlers.forEach((handler) => {
      try {
        handler(...args);
      } catch (error) {
        console.error(`Error in plugin event handler for "${event}":`, error);
      }
    });
  }

  /**
   * Execute all render hooks
   */
  executeRenderHooks(ctx: CanvasRenderingContext2D, document: CanvasDocument): void {
    // Sort by priority
    const sortedHooks = [...this.renderHooks].sort((a, b) => a.priority - b.priority);

    sortedHooks.forEach((hook) => {
      try {
        hook.render(ctx, document);
      } catch (error) {
        console.error(`Error in render hook "${hook.id}":`, error);
      }
    });
  }

  /**
   * Create sandboxed API for a plugin
   */
  private createPluginAPI(plugin: Plugin): PluginAPI {
    const { permissions } = plugin.metadata;

    return {
      getDocument: () => {
        if (!permissions.readDocument) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks readDocument permission`);
          return null;
        }
        return this.documentGetter?.() || null;
      },

      getElement: (id: string) => {
        if (!permissions.readDocument) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks readDocument permission`);
          return null;
        }
        return this.elementAccessor?.get(id) || null;
      },

      addElement: (element: Partial<CanvasElement>) => {
        if (!permissions.writeDocument) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks writeDocument permission`);
          return null;
        }
        return this.elementAccessor?.add(element) || null;
      },

      updateElement: (id: string, changes: Partial<CanvasElement>) => {
        if (!permissions.writeDocument) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks writeDocument permission`);
          return false;
        }
        return this.elementAccessor?.update(id, changes) || false;
      },

      removeElement: (id: string) => {
        if (!permissions.writeDocument) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks writeDocument permission`);
          return false;
        }
        return this.elementAccessor?.remove(id) || false;
      },

      on: (event: string, handler: (...args: unknown[]) => void) => {
        if (!permissions.subscribeEvents) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks subscribeEvents permission`);
          return () => {};
        }

        if (!this.eventHandlers.has(event)) {
          this.eventHandlers.set(event, new Set());
        }

        this.eventHandlers.get(event)!.add(handler);

        return () => {
          this.eventHandlers.get(event)?.delete(handler);
        };
      },

      registerTool: (tool: PluginTool) => {
        if (!permissions.registerTools) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks registerTools permission`);
          return () => {};
        }

        this.registeredTools.set(tool.id, tool);

        return () => {
          this.registeredTools.delete(tool.id);
        };
      },

      addRenderHook: (hook: RenderHook) => {
        if (!permissions.modifyRendering) {
          console.warn(`Plugin "${plugin.metadata.id}" lacks modifyRendering permission`);
          return () => {};
        }

        this.renderHooks.push(hook);

        return () => {
          const index = this.renderHooks.findIndex((h) => h.id === hook.id);
          if (index >= 0) {
            this.renderHooks.splice(index, 1);
          }
        };
      },
    };
  }

  /**
   * Clean up resources associated with a plugin
   */
  private cleanupPluginResources(pluginId: string): void {
    // Remove tools registered by this plugin
    Array.from(this.registeredTools.entries()).forEach(([id, tool]) => {
      if (id.startsWith(`${pluginId}:`)) {
        this.registeredTools.delete(id);
      }
    });

    // Remove render hooks
    this.renderHooks = this.renderHooks.filter((hook) => !hook.id.startsWith(`${pluginId}:`));
  }

  /**
   * Destroy all plugins and cleanup
   */
  async destroy(): Promise<void> {
    const pluginIds = Array.from(this.plugins.keys());

    for (const id of pluginIds) {
      await this.unregister(id);
    }

    this.plugins.clear();
    this.activePlugins.clear();
    this.eventHandlers.clear();
    this.registeredTools.clear();
    this.renderHooks = [];
  }
}

/**
 * Global plugin manager instance
 */
export const globalPluginManager = new PluginManager();
