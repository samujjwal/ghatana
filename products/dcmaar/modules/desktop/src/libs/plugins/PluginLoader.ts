/**
 * Plugin Loader
 * 
 * Loads and validates WASM plugins with:
 * - Manifest validation
 * - WASM module loading
 * - Sandbox initialization
 * - Security checks
 */

import type {
  Plugin,
  PluginManifest,
  PluginContext,
  PluginState,
  PluginSandboxConfig,
  PluginError as _PluginErrorType,
} from './types';
import { PluginError, PluginState as State } from './types';
import { PluginSandbox } from './PluginSandbox';

/**
 * Plugin loader for WASM modules
 */
export class PluginLoader {
  private plugins: Map<string, Plugin> = new Map();
  private sandboxes: Map<string, PluginSandbox> = new Map();

  /**
   * Load a plugin from a URL or file
   */
  async loadPlugin(
    manifestUrl: string,
    wasmUrl: string,
    config?: Record<string, unknown>
  ): Promise<Plugin> {
    try {
      // Load and validate manifest
      const manifest = await this.loadManifest(manifestUrl);
      this.validateManifest(manifest);

      // Check if plugin already loaded
      if (this.plugins.has(manifest.metadata.id)) {
        throw new PluginError(
          'Plugin already loaded',
          manifest.metadata.id,
          'ALREADY_LOADED'
        );
      }

      // Load WASM module
      const wasmModule = await this.loadWASM(wasmUrl);

      // Create sandbox
      const sandboxConfig: PluginSandboxConfig = {
        maxMemoryMB: manifest.capabilities.maxMemoryMB || 100,
        maxCpuPercent: manifest.capabilities.maxCpuPercent || 50,
        maxNetworkRequests: manifest.capabilities.maxNetworkRequests || 10,
        timeout: 30000, // 30 seconds
        allowedDomains: [],
        allowedAPIs: [],
      };

      const sandbox = new PluginSandbox(manifest.metadata.id, sandboxConfig);
      this.sandboxes.set(manifest.metadata.id, sandbox);

      // Create plugin context
      const context = await sandbox.createContext(config || {});

      // Instantiate WASM module
      const imports = this.createWASMImports(context);
      // @ts-ignore - WebAssembly.Imports type is too strict for dynamic imports
      const instance = await WebAssembly.instantiate(wasmModule, { env: imports });

      // Create plugin
      const plugin: Plugin = {
        manifest,
        instance,
        context,
        state: State.LOADED,
      };

      this.plugins.set(manifest.metadata.id, plugin);

      return plugin;
    } catch (error) {
      throw new PluginError(
        `Failed to load plugin: ${error instanceof Error ? error.message : 'Unknown error'}`,
        'unknown',
        'LOAD_FAILED',
        error
      );
    }
  }

  /**
   * Unload a plugin
   */
  async unloadPlugin(pluginId: string): Promise<void> {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) {
      throw new PluginError('Plugin not found', pluginId, 'NOT_FOUND');
    }

    // Deactivate if active
    if (plugin.state === State.ACTIVE) {
      await this.deactivatePlugin(pluginId);
    }

    // Cleanup sandbox
    const sandbox = this.sandboxes.get(pluginId);
    if (sandbox) {
      await sandbox.destroy();
      this.sandboxes.delete(pluginId);
    }

    // Remove plugin
    this.plugins.delete(pluginId);
  }

  /**
   * Activate a plugin
   */
  async activatePlugin(pluginId: string): Promise<void> {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) {
      throw new PluginError('Plugin not found', pluginId, 'NOT_FOUND');
    }

    if (plugin.state === State.ACTIVE) {
      return; // Already active
    }

    try {
      plugin.state = State.ACTIVATING;

      // Call plugin's activate function if it exists
      const exports = plugin.instance.exports as any;
      if (exports.activate) {
        await exports.activate();
      }

      plugin.state = State.ACTIVE;
    } catch (error) {
      plugin.state = State.ERROR;
      throw new PluginError(
        `Failed to activate plugin: ${error instanceof Error ? error.message : 'Unknown error'}`,
        pluginId,
        'ACTIVATION_FAILED',
        error
      );
    }
  }

  /**
   * Deactivate a plugin
   */
  async deactivatePlugin(pluginId: string): Promise<void> {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) {
      throw new PluginError('Plugin not found', pluginId, 'NOT_FOUND');
    }

    if (plugin.state !== State.ACTIVE) {
      return; // Not active
    }

    try {
      plugin.state = State.DEACTIVATING;

      // Call plugin's deactivate function if it exists
      const exports = plugin.instance.exports as any;
      if (exports.deactivate) {
        await exports.deactivate();
      }

      plugin.state = State.LOADED;
    } catch (error) {
      plugin.state = State.ERROR;
      throw new PluginError(
        `Failed to deactivate plugin: ${error instanceof Error ? error.message : 'Unknown error'}`,
        pluginId,
        'DEACTIVATION_FAILED',
        error
      );
    }
  }

  /**
   * Get a plugin by ID
   */
  getPlugin(pluginId: string): Plugin | undefined {
    return this.plugins.get(pluginId);
  }

  /**
   * Get all plugins
   */
  getAllPlugins(): Plugin[] {
    return Array.from(this.plugins.values());
  }

  /**
   * Get plugins by state
   */
  getPluginsByState(state: PluginState): Plugin[] {
    return Array.from(this.plugins.values()).filter(p => p.state === state);
  }

  /**
   * Load manifest from URL
   */
  private async loadManifest(url: string): Promise<PluginManifest> {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to load manifest: ${response.statusText}`);
    }
    return response.json();
  }

  /**
   * Validate plugin manifest
   */
  private validateManifest(manifest: PluginManifest): void {
    // Required fields
    if (!manifest.metadata?.id) {
      throw new Error('Manifest missing metadata.id');
    }
    if (!manifest.metadata?.name) {
      throw new Error('Manifest missing metadata.name');
    }
    if (!manifest.metadata?.version) {
      throw new Error('Manifest missing metadata.version');
    }
    if (!manifest.main) {
      throw new Error('Manifest missing main entry point');
    }

    // Validate version format (semver)
    const versionRegex = /^\d+\.\d+\.\d+$/;
    if (!versionRegex.test(manifest.metadata.version)) {
      throw new Error('Invalid version format (must be semver)');
    }

    // Validate capabilities
    if (manifest.capabilities) {
      const caps = manifest.capabilities;
      if (caps.maxMemoryMB && (caps.maxMemoryMB < 1 || caps.maxMemoryMB > 1000)) {
        throw new Error('maxMemoryMB must be between 1 and 1000');
      }
      if (caps.maxCpuPercent && (caps.maxCpuPercent < 1 || caps.maxCpuPercent > 100)) {
        throw new Error('maxCpuPercent must be between 1 and 100');
      }
    }
  }

  /**
   * Load WASM module from URL
   */
  private async loadWASM(url: string): Promise<WebAssembly.Module> {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to load WASM: ${response.statusText}`);
    }
    const buffer = await response.arrayBuffer();
    return WebAssembly.compile(buffer);
  }

  /**
   * Create WASM imports for plugin
   */
  private createWASMImports(context: PluginContext): Record<string, unknown> {
    return {
      log: (level: number, message: string) => {
        switch (level) {
          case 0:
            context.logger.debug(message);
            break;
          case 1:
            context.logger.info(message);
            break;
          case 2:
            context.logger.warn(message);
            break;
          case 3:
            context.logger.error(message);
            break;
        }
      },
      // Add more imports as needed
    };
  }
}

// Export singleton instance
export const pluginLoader = new PluginLoader();
