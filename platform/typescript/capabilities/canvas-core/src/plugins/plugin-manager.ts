/**
 * @ghatana/canvas Plugin Manager
 *
 * Central registry and lifecycle manager for Canvas plugins.
 * Handles plugin loading, activation, and communication.
 *
 * @doc.type class
 * @doc.purpose Plugin lifecycle management
 * @doc.layer core
 * @doc.pattern Singleton
 */

import type {
  CanvasPlugin,
  PluginManifest,
  PluginState,
  PluginContext,
  PluginCanvasAPI,
  PluginEventAPI,
  PluginLogger,
  ElementDefinition,
  NodeTypeDefinition,
  EdgeTypeDefinition,
  ToolDefinition,
  PanelDefinition,
} from "./types";

/**
 * Plugin instance with runtime state
 */
interface PluginInstance {
  plugin: CanvasPlugin;
  manifest: PluginManifest;
  state: PluginState;
  context: PluginContext;
}

/**
 * Plugin registration options
 */
export interface PluginRegistrationOptions {
  /** Auto-activate after registration */
  autoActivate?: boolean;
}

/**
 * Plugin Manager Error
 */
export class PluginError extends Error {
  constructor(
    message: string,
    public readonly pluginId: string,
    public readonly code: PluginErrorCode,
  ) {
    super(message);
    this.name = "PluginError";
  }
}

export type PluginErrorCode =
  | "LOAD_FAILED"
  | "ACTIVATE_FAILED"
  | "DEPENDENCY_MISSING"
  | "VERSION_INCOMPATIBLE"
  | "ALREADY_REGISTERED"
  | "NOT_FOUND";

/**
 * Plugin Manager singleton
 *
 * Manages the lifecycle of all canvas plugins.
 */
export class PluginManager {
  private static instance: PluginManager | null = null;

  private plugins = new Map<string, PluginInstance>();
  private canvasApi: PluginCanvasAPI | null = null;
  private eventApi: PluginEventAPI | null = null;

  private constructor() {}

  /**
   * Get singleton instance
   */
  static getInstance(): PluginManager {
    if (!PluginManager.instance) {
      PluginManager.instance = new PluginManager();
    }
    return PluginManager.instance;
  }

  /**
   * Reset instance (for testing)
   */
  static resetInstance(): void {
    PluginManager.instance = null;
  }

  /**
   * Set the canvas API (called by HybridCanvas on mount)
   */
  setCanvasAPI(api: PluginCanvasAPI): void {
    this.canvasApi = api;
  }

  /**
   * Set the event API
   */
  setEventAPI(api: PluginEventAPI): void {
    this.eventApi = api;
  }

  /**
   * Register a plugin
   */
  async register(
    plugin: CanvasPlugin,
    options: PluginRegistrationOptions = {},
  ): Promise<void> {
    const { manifest } = plugin;

    // Validate manifest
    this.validateManifest(manifest);

    // Check if already registered
    if (this.plugins.has(manifest.id)) {
      throw new PluginError(
        `Plugin "${manifest.id}" is already registered`,
        manifest.id,
        "ALREADY_REGISTERED",
      );
    }

    // Check dependencies
    await this.checkDependencies(manifest);

    // Create plugin context
    const context = this.createContext(manifest);

    // Create instance
    const instance: PluginInstance = {
      plugin,
      manifest,
      state: "uninitialized",
      context,
    };

    // Register
    this.plugins.set(manifest.id, instance);

    try {
      // Initialize
      instance.state = "initializing";
      await plugin.onLoad?.(context);

      instance.state = "disabled";

      // Auto-activate if requested
      if (options.autoActivate !== false) {
        await this.activate(manifest.id);
      }

      this.log("info", `Plugin "${manifest.id}" registered successfully`);
    } catch (error) {
      instance.state = "error";
      this.plugins.delete(manifest.id);

      throw new PluginError(
        `Failed to load plugin "${manifest.id}": ${(error as Error).message}`,
        manifest.id,
        "LOAD_FAILED",
      );
    }
  }

  /**
   * Unregister a plugin
   */
  async unregister(pluginId: string): Promise<void> {
    const instance = this.plugins.get(pluginId);
    if (!instance) {
      throw new PluginError(
        `Plugin "${pluginId}" not found`,
        pluginId,
        "NOT_FOUND",
      );
    }

    try {
      // Deactivate if active
      if (instance.state === "active") {
        await this.deactivate(pluginId);
      }

      // Uninstall
      instance.state = "uninstalling";
      await instance.plugin.onUninstall?.(instance.context);

      // Remove from registry
      this.plugins.delete(pluginId);

      this.log("info", `Plugin "${pluginId}" unregistered`);
    } catch (error) {
      throw new PluginError(
        `Failed to unregister plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        "LOAD_FAILED",
      );
    }
  }

  /**
   * Activate a plugin
   */
  async activate(pluginId: string): Promise<void> {
    const instance = this.plugins.get(pluginId);
    if (!instance) {
      throw new PluginError(
        `Plugin "${pluginId}" not found`,
        pluginId,
        "NOT_FOUND",
      );
    }

    if (instance.state === "active") {
      return; // Already active
    }

    try {
      await instance.plugin.onActivate?.(instance.context);
      instance.state = "active";

      this.log("info", `Plugin "${pluginId}" activated`);
    } catch (error) {
      instance.state = "error";
      throw new PluginError(
        `Failed to activate plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        "ACTIVATE_FAILED",
      );
    }
  }

  /**
   * Deactivate a plugin
   */
  async deactivate(pluginId: string): Promise<void> {
    const instance = this.plugins.get(pluginId);
    if (!instance) {
      throw new PluginError(
        `Plugin "${pluginId}" not found`,
        pluginId,
        "NOT_FOUND",
      );
    }

    if (instance.state !== "active") {
      return; // Not active
    }

    try {
      await instance.plugin.onDeactivate?.(instance.context);
      instance.state = "disabled";

      this.log("info", `Plugin "${pluginId}" deactivated`);
    } catch (error) {
      instance.state = "error";
      throw new PluginError(
        `Failed to deactivate plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        "ACTIVATE_FAILED",
      );
    }
  }

  /**
   * Get all registered plugins
   */
  getPlugins(): readonly CanvasPlugin[] {
    return Array.from(this.plugins.values()).map((i) => i.plugin);
  }

  /**
   * Get active plugins
   */
  getActivePlugins(): readonly CanvasPlugin[] {
    return Array.from(this.plugins.values())
      .filter((i) => i.state === "active")
      .map((i) => i.plugin);
  }

  /**
   * Get plugin by ID
   */
  getPlugin(pluginId: string): CanvasPlugin | undefined {
    return this.plugins.get(pluginId)?.plugin;
  }

  /**
   * Get plugin state
   */
  getPluginState(pluginId: string): PluginState | undefined {
    return this.plugins.get(pluginId)?.state;
  }

  /**
   * Get all element definitions from active plugins
   */
  getElementDefinitions(): readonly ElementDefinition[] {
    return this.getActivePlugins().flatMap((p) => p.elements ?? []);
  }

  /**
   * Get all node type definitions from active plugins
   */
  getNodeTypeDefinitions(): readonly NodeTypeDefinition[] {
    return this.getActivePlugins().flatMap((p) => p.nodeTypes ?? []);
  }

  /**
   * Get all edge type definitions from active plugins
   */
  getEdgeTypeDefinitions(): readonly EdgeTypeDefinition[] {
    return this.getActivePlugins().flatMap((p) => p.edgeTypes ?? []);
  }

  /**
   * Get all tool definitions from active plugins
   */
  getToolDefinitions(): readonly ToolDefinition[] {
    return this.getActivePlugins().flatMap((p) => p.tools ?? []);
  }

  /**
   * Get all panel definitions from active plugins
   */
  getPanelDefinitions(): readonly PanelDefinition[] {
    return this.getActivePlugins().flatMap((p) => p.panels ?? []);
  }

  // =========================================================================
  // PRIVATE HELPERS
  // =========================================================================

  private validateManifest(manifest: PluginManifest): void {
    if (!manifest.id || typeof manifest.id !== "string") {
      throw new Error("Plugin manifest must have a valid id");
    }
    if (!manifest.name || typeof manifest.name !== "string") {
      throw new Error("Plugin manifest must have a valid name");
    }
    if (!manifest.version || typeof manifest.version !== "string") {
      throw new Error("Plugin manifest must have a valid version");
    }
  }

  private async checkDependencies(manifest: PluginManifest): Promise<void> {
    for (const depId of manifest.dependencies ?? []) {
      if (!this.plugins.has(depId)) {
        throw new PluginError(
          `Plugin "${manifest.id}" requires "${depId}" which is not registered`,
          manifest.id,
          "DEPENDENCY_MISSING",
        );
      }
    }
  }

  private createContext(manifest: PluginManifest): PluginContext {
    const logger = this.createLogger(manifest.id);

    return {
      manifest,
      state: "uninitialized",
      canvas: this.createCanvasProxy(manifest.id),
      events: this.createEventProxy(manifest.id),
      logger,
    };
  }

  private createCanvasProxy(pluginId: string): PluginCanvasAPI {
    // Create a proxy that delegates to the actual canvas API
    // This allows for lazy binding when canvas mounts
    return new Proxy({} as PluginCanvasAPI, {
      get: (_target, prop) => {
        if (!this.canvasApi) {
          throw new Error(`Canvas API not available for plugin "${pluginId}"`);
        }
        const api = this.canvasApi as unknown as Record<string, unknown>;
        const value = api[prop as string];
        if (typeof value === "function") {
          return value.bind(this.canvasApi);
        }
        return value;
      },
    });
  }

  private createEventProxy(pluginId: string): PluginEventAPI {
    return new Proxy({} as PluginEventAPI, {
      get: (_target, prop) => {
        if (!this.eventApi) {
          throw new Error(`Event API not available for plugin "${pluginId}"`);
        }
        const api = this.eventApi as unknown as Record<string, unknown>;
        const value = api[prop as string];
        if (typeof value === "function") {
          return value.bind(this.eventApi);
        }
        return value;
      },
    });
  }

  private createLogger(pluginId: string): PluginLogger {
    const prefix = `[Plugin:${pluginId}]`;
    return {
      debug: (msg, ...args) => console.debug(prefix, msg, ...args),
      info: (msg, ...args) => console.info(prefix, msg, ...args),
      warn: (msg, ...args) => console.warn(prefix, msg, ...args),
      error: (msg, ...args) => console.error(prefix, msg, ...args),
    };
  }

  private log(
    level: "debug" | "info" | "warn" | "error",
    message: string,
  ): void {
    const prefix = "[PluginManager]";
    console[level](prefix, message);
  }
}

/**
 * Get the plugin manager singleton
 */
export function getPluginManager(): PluginManager {
  return PluginManager.getInstance();
}
