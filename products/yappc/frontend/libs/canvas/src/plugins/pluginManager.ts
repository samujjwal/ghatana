/**
 * Plugin Manager
 * 
 * Central registry and lifecycle manager for Canvas plugins.
 * Handles plugin loading, activation, sandboxing, and communication.
 */

import type {
  Plugin,
  PluginManifest,
  PluginState,
  PluginContext,
  PluginRegistrationOptions,
  PluginError as PluginErrorType,
  PluginErrorCode,
  PluginCanvasAPI,
  PluginStorage,
  PluginEventAPI,
  PluginUIAPI,
  PluginLogger,
  EventHandler,
  CanvasEvent,
} from './types';

/**
 * Plugin Manager singleton
 */
export class PluginManager {
  private static instance: PluginManager | null = null;
  
  private plugins = new Map<string, PluginInstance>();
  private eventListeners = new Map<string, Set<EventHandler>>();
  
  /**
   *
   */
  private constructor() {
    // Private constructor for singleton
  }
  
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
   * Register a plugin
   */
  async register(
    plugin: Plugin,
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
        'LOAD_FAILED',
      );
    }
    
    // Check dependencies
    await this.checkDependencies(manifest);
    
    // Check version compatibility
    this.checkVersionCompatibility(manifest);
    
    // Create plugin instance
    const instance: PluginInstance = {
      plugin,
      manifest,
      state: 'uninitialized',
      context: this.createContext(manifest, options),
      options,
    };
    
    // Register plugin
    this.plugins.set(manifest.id, instance);
    
    try {
      // Initialize plugin
      instance.state = 'initializing';
      // Update context state before calling onLoad
      instance.context.state = 'initializing';
      await plugin.onLoad?.(instance.context);
      
      // After loading, set to disabled and optionally activate
      instance.state = 'disabled';
      instance.context.state = 'disabled';
      
      // Auto-activate if requested
      if (options.autoActivate !== false) {
        await this.activate(manifest.id);
      }
      
      this.log('info', `Plugin "${manifest.id}" registered successfully`);
    } catch (error) {
      instance.state = 'error';
      this.plugins.delete(manifest.id);
      
      throw new PluginError(
        `Failed to load plugin "${manifest.id}": ${(error as Error).message}`,
        manifest.id,
        'LOAD_FAILED',
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
        'LOAD_FAILED',
      );
    }
    
    try {
      // Deactivate if active
      if (instance.state === 'active') {
        await this.deactivate(pluginId);
      }
      
      // Uninstall
      instance.state = 'uninstalling';
      instance.context.state = 'uninstalling';
      await instance.plugin.onUninstall?.(instance.context);
      
      // Remove from registry
      this.plugins.delete(pluginId);
      
      // Clear storage
      await instance.context.storage.clear();
      
      this.log('info', `Plugin "${pluginId}" unregistered`);
    } catch (error) {
      throw new PluginError(
        `Failed to unregister plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        'RUNTIME_ERROR',
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
        'ACTIVATION_FAILED',
      );
    }
    
    if (instance.state === 'active') {
      return; // Already active
    }
    
    try {
      await instance.plugin.onActivate?.(instance.context);
      instance.state = 'active';
      instance.context.state = 'active';
      
      this.log('info', `Plugin "${pluginId}" activated`);
    } catch (error) {
      instance.state = 'error';
      
      throw new PluginError(
        `Failed to activate plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        'ACTIVATION_FAILED',
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
        'RUNTIME_ERROR',
      );
    }
    
    if (instance.state !== 'active') {
      return; // Not active
    }
    
    try {
      await instance.plugin.onDeactivate?.(instance.context);
      instance.state = 'disabled';
      instance.context.state = 'disabled';
      
      this.log('info', `Plugin "${pluginId}" deactivated`);
    } catch (error) {
      throw new PluginError(
        `Failed to deactivate plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        'RUNTIME_ERROR',
      );
    }
  }
  
  /**
   * Pause a plugin
   */
  async pause(pluginId: string): Promise<void> {
    const instance = this.plugins.get(pluginId);
    if (!instance) return;
    
    if (instance.state === 'active') {
      await instance.plugin.onPause?.(instance.context);
      instance.state = 'paused';
      instance.context.state = 'paused';
    }
  }
  
  /**
   * Resume a plugin
   */
  async resume(pluginId: string): Promise<void> {
    const instance = this.plugins.get(pluginId);
    if (!instance) return;
    
    if (instance.state === 'paused') {
      await instance.plugin.onResume?.(instance.context);
      instance.state = 'active';
      instance.context.state = 'active';
    }
  }
  
  /**
   * Get plugin state
   */
  getState(pluginId: string): PluginState | undefined {
    return this.plugins.get(pluginId)?.state;
  }
  
  /**
   * Get all plugins
   */
  getPlugins(): PluginManifest[] {
    return Array.from(this.plugins.values()).map((instance) => instance.manifest);
  }
  
  /**
   * Get active plugins
   */
  getActivePlugins(): PluginManifest[] {
    return Array.from(this.plugins.values())
      .filter((instance) => instance.state === 'active')
      .map((instance) => instance.manifest);
  }
  
  /**
   * Emit Canvas event to all plugins
   */
  emitEvent(event: CanvasEvent, data?: unknown): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach((handler) => {
        try {
          handler(data);
        } catch (error) {
          this.log('error', `Error in event handler for "${event}":`, error);
        }
      });
    }
  }
  
  // Private methods
  
  /**
   *
   */
  private validateManifest(manifest: PluginManifest): void {
    if (!manifest.id) {
      throw new Error('Plugin manifest must include an id');
    }
    if (!manifest.name) {
      throw new Error('Plugin manifest must include a name');
    }
    if (!manifest.version) {
      throw new Error('Plugin manifest must include a version');
    }
    
    // Validate version format (semver)
    const versionRegex = /^\d+\.\d+\.\d+$/;
    if (!versionRegex.test(manifest.version)) {
      throw new Error('Plugin version must be in semver format (e.g., 1.0.0)');
    }
  }
  
  /**
   *
   */
  private async checkDependencies(manifest: PluginManifest): Promise<void> {
    if (!manifest.dependencies) return;
    
    for (const depId of manifest.dependencies) {
      const dep = this.plugins.get(depId);
      if (!dep) {
        throw new PluginError(
          `Missing required dependency: ${depId}`,
          manifest.id,
          'DEPENDENCY_MISSING',
        );
      }
      if (dep.state !== 'active') {
        throw new PluginError(
          `Dependency "${depId}" is not active`,
          manifest.id,
          'DEPENDENCY_MISSING',
        );
      }
    }
  }
  
  /**
   *
   */
  private checkVersionCompatibility(manifest: PluginManifest): void {
    // For now, just check that minCanvasVersion is present
    // In a real implementation, compare with actual Canvas version
    if (!manifest.minCanvasVersion) {
      this.log('warn', `Plugin "${manifest.id}" does not specify minCanvasVersion`);
    }
  }
  
  /**
   *
   */
  private createContext(
    manifest: PluginManifest,
    options: PluginRegistrationOptions,
  ): PluginContext {
    const pluginId = manifest.id;
    
    return {
      manifest,
      state: 'uninitialized',
      canvas: this.createCanvasAPI(pluginId),
      storage: this.createStorageAPI(pluginId),
      events: this.createEventAPI(pluginId),
      ui: this.createUIAPI(pluginId),
      logger: this.createLogger(pluginId),
    };
  }
  
  /**
   *
   */
  private createCanvasAPI(pluginId: string): PluginCanvasAPI {
    return {
      getDocument: () => {
        // Return read-only document
        // In real implementation, return actual canvas document
        return {} as unknown;
      },
      executeCommand: async (commandId: string, args?: unknown) => {
        this.log('debug', `Plugin "${pluginId}" executing command: ${commandId}`);
        // Execute command on canvas
      },
      registerCommand: (command) => {
        this.log('debug', `Plugin "${pluginId}" registering command: ${command.id}`);
        // Register command
      },
      registerElementType: (elementType) => {
        this.log('debug', `Plugin "${pluginId}" registering element type: ${elementType.type}`);
        // Register element type
      },
      registerTool: (tool) => {
        this.log('debug', `Plugin "${pluginId}" registering tool: ${tool.id}`);
        // Register tool
      },
      registerExporter: (exporter) => {
        this.log('debug', `Plugin "${pluginId}" registering exporter: ${exporter.id}`);
        // Register exporter
      },
      registerImporter: (importer) => {
        this.log('debug', `Plugin "${pluginId}" registering importer: ${importer.id}`);
        // Register importer
      },
    };
  }
  
  /**
   *
   */
  private createStorageAPI(pluginId: string): PluginStorage {
    const storageKey = `plugin:${pluginId}`;
    
    return {
      get: async <T = unknown>(key: string): Promise<T | undefined> => {
        const data = localStorage.getItem(`${storageKey}:${key}`);
        return data ? JSON.parse(data) : undefined;
      },
      set: async <T = unknown>(key: string, value: T): Promise<void> => {
        localStorage.setItem(`${storageKey}:${key}`, JSON.stringify(value));
      },
      delete: async (key: string): Promise<void> => {
        localStorage.removeItem(`${storageKey}:${key}`);
      },
      clear: async (): Promise<void> => {
        const keys = Object.keys(localStorage).filter((k) => k.startsWith(storageKey));
        keys.forEach((k) => localStorage.removeItem(k));
      },
      keys: async (): Promise<string[]> => {
        return Object.keys(localStorage)
          .filter((k) => k.startsWith(storageKey))
          .map((k) => k.replace(`${storageKey}:`, ''));
      },
    };
  }
  
  /**
   *
   */
  private createEventAPI(pluginId: string): PluginEventAPI {
    return {
      on: (event: CanvasEvent, handler: EventHandler): (() => void) => {
        if (!this.eventListeners.has(event)) {
          this.eventListeners.set(event, new Set());
        }
        this.eventListeners.get(event)!.add(handler);
        
        // Return unsubscribe function
        return () => {
          this.eventListeners.get(event)?.delete(handler);
        };
      },
      once: (event: CanvasEvent, handler: EventHandler): (() => void) => {
        const wrappedHandler = (data: unknown) => {
          handler(data);
          this.eventListeners.get(event)?.delete(wrappedHandler);
        };
        
        if (!this.eventListeners.has(event)) {
          this.eventListeners.set(event, new Set());
        }
        this.eventListeners.get(event)!.add(wrappedHandler);
        
        return () => {
          this.eventListeners.get(event)?.delete(wrappedHandler);
        };
      },
      emit: (event: string, data?: unknown): void => {
        this.log('debug', `Plugin "${pluginId}" emitting event: ${event}`);
        // Emit custom plugin event
      },
    };
  }
  
  /**
   *
   */
  private createUIAPI(pluginId: string): PluginUIAPI {
    return {
      registerPanel: (panel) => {
        this.log('debug', `Plugin "${pluginId}" registering panel: ${panel.id}`);
        // Register UI panel
      },
      notify: (message: string, type = 'info' as const) => {
        const logLevel = type === 'success' ? 'info' : type === 'warning' ? 'warn' : type;
        this.log(logLevel, `Plugin "${pluginId}": ${message}`);
        // Show notification
      },
      showDialog: async (dialog) => {
        this.log('debug', `Plugin "${pluginId}" showing dialog: ${dialog.title}`);
        // Show dialog and return result
        return undefined;
      },
      registerContextMenuItem: (item) => {
        this.log('debug', `Plugin "${pluginId}" registering context menu item: ${item.id}`);
        // Register context menu item
      },
    };
  }
  
  /**
   *
   */
  private createLogger(pluginId: string): PluginLogger {
    const prefix = `[Plugin:${pluginId}]`;
    
    return {
      debug: (message: string, ...args: unknown[]) => {
        console.debug(prefix, message, ...args);
      },
      info: (message: string, ...args: unknown[]) => {
        console.info(prefix, message, ...args);
      },
      warn: (message: string, ...args: unknown[]) => {
        console.warn(prefix, message, ...args);
      },
      error: (message: string, ...args: unknown[]) => {
        console.error(prefix, message, ...args);
      },
    };
  }
  
  /**
   *
   */
  private log(level: 'debug' | 'info' | 'warn' | 'error', message: string, ...args: unknown[]): void {
    const prefix = '[PluginManager]';
    switch (level) {
      case 'debug':
        console.debug(prefix, message, ...args);
        break;
      case 'info':
        console.info(prefix, message, ...args);
        break;
      case 'warn':
        console.warn(prefix, message, ...args);
        break;
      case 'error':
        console.error(prefix, message, ...args);
        break;
    }
  }
}

/**
 * Plugin instance (internal)
 */
interface PluginInstance {
  plugin: Plugin;
  manifest: PluginManifest;
  state: PluginState;
  context: PluginContext;
  options: PluginRegistrationOptions;
}

/**
 * Plugin error class
 */
export class PluginError extends Error implements PluginErrorType {
  /**
   *
   */
  constructor(
    message: string,
    public readonly pluginId: string,
    public readonly code: PluginErrorCode,
  ) {
    super(message);
    this.name = 'PluginError';
  }
}

/**
 * Convenience function to get plugin manager instance
 */
export function getPluginManager(): PluginManager {
  return PluginManager.getInstance();
}
