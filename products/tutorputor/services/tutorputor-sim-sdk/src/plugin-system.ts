/**
 * Plugin System - Extension point for custom kernels and features.
 *
 * @doc.type class
 * @doc.purpose Provides plugin architecture for simulation extensibility
 * @doc.layer product
 * @doc.pattern Plugin
 */

import type { SimKernelService, SimulationDomain } from '@ghatana/tutorputor-contracts/v1/simulation';

/**
 * Plugin metadata.
 */
export interface PluginMetadata {
  /** Plugin unique identifier */
  id: string;
  /** Human-readable name */
  name: string;
  /** Plugin version (semver) */
  version: string;
  /** Plugin description */
  description: string;
  /** Author name or organization */
  author: string;
  /** License (e.g., MIT, Apache-2.0) */
  license?: string;
  /** Repository URL */
  repository?: string;
  /** Tags for categorization */
  tags?: string[];
}

/**
 * Kernel plugin definition.
 */
export interface KernelPlugin {
  /** Plugin metadata */
  metadata: PluginMetadata;
  /** Domain this kernel handles */
  domain: SimulationDomain | string;
  /** Supported simulation types */
  supportedTypes: string[];
  /** Whether the kernel is async */
  isAsync: boolean;
  /** Kernel factory function */
  createKernel: (config?: unknown) => SimKernelService;
}

/**
 * Prompt pack plugin for custom domains.
 */
export interface PromptPackPlugin {
  /** Plugin metadata */
  metadata: PluginMetadata;
  /** Domain this prompt pack handles */
  domain: SimulationDomain | string;
  /** System prompt for LLM */
  systemPrompt: string;
  /** Examples for few-shot learning */
  examples: Array<{
    input: string;
    output: string;
  }>;
  /** Entity templates */
  entityTemplates?: Array<{
    type: string;
    template: Record<string, unknown>;
  }>;
}

/**
 * Visualizer plugin for custom rendering.
 */
export interface VisualizerPlugin {
  /** Plugin metadata */
  metadata: PluginMetadata;
  /** Domains this visualizer supports */
  supportedDomains: string[];
  /** Render function */
  render: (
    canvas: HTMLCanvasElement | OffscreenCanvas,
    keyframe: unknown,
    options?: Record<string, unknown>
  ) => void;
  /** Export function */
  export?: (keyframe: unknown, format: 'png' | 'svg' | 'json') => Promise<Blob>;
}

/**
 * Plugin types union.
 */
export type SimulationPlugin = KernelPlugin | PromptPackPlugin | VisualizerPlugin;

/**
 * Plugin type guards.
 */
export function isKernelPlugin(plugin: SimulationPlugin): plugin is KernelPlugin {
  return 'createKernel' in plugin && typeof plugin.createKernel === 'function';
}

export function isPromptPackPlugin(plugin: SimulationPlugin): plugin is PromptPackPlugin {
  return 'systemPrompt' in plugin && typeof plugin.systemPrompt === 'string';
}

export function isVisualizerPlugin(plugin: SimulationPlugin): plugin is VisualizerPlugin {
  return 'render' in plugin && typeof plugin.render === 'function';
}

/**
 * Plugin registry.
 */
class PluginRegistry {
  private kernelPlugins: Map<string, KernelPlugin> = new Map();
  private promptPackPlugins: Map<string, PromptPackPlugin> = new Map();
  private visualizerPlugins: Map<string, VisualizerPlugin> = new Map();
  private listeners: Map<string, Set<(plugin: SimulationPlugin) => void>> = new Map();

  /**
   * Register a plugin.
   */
  register(plugin: SimulationPlugin): void {
    const id = plugin.metadata.id;

    if (isKernelPlugin(plugin)) {
      if (this.kernelPlugins.has(id)) {
        throw new Error(`Kernel plugin already registered: ${id}`);
      }
      this.kernelPlugins.set(id, plugin);
      this.emit('kernel:registered', plugin);
    } else if (isPromptPackPlugin(plugin)) {
      if (this.promptPackPlugins.has(id)) {
        throw new Error(`Prompt pack plugin already registered: ${id}`);
      }
      this.promptPackPlugins.set(id, plugin);
      this.emit('promptPack:registered', plugin);
    } else if (isVisualizerPlugin(plugin)) {
      if (this.visualizerPlugins.has(id)) {
        throw new Error(`Visualizer plugin already registered: ${id}`);
      }
      this.visualizerPlugins.set(id, plugin);
      this.emit('visualizer:registered', plugin);
    }
  }

  /**
   * Unregister a plugin.
   */
  unregister(id: string): void {
    this.kernelPlugins.delete(id);
    this.promptPackPlugins.delete(id);
    this.visualizerPlugins.delete(id);
  }

  /**
   * Get a kernel plugin by domain.
   */
  getKernelForDomain(domain: string): KernelPlugin | undefined {
    for (const plugin of this.kernelPlugins.values()) {
      if (plugin.domain === domain) {
        return plugin;
      }
    }
    return undefined;
  }

  /**
   * Get a prompt pack for a domain.
   */
  getPromptPackForDomain(domain: string): PromptPackPlugin | undefined {
    for (const plugin of this.promptPackPlugins.values()) {
      if (plugin.domain === domain) {
        return plugin;
      }
    }
    return undefined;
  }

  /**
   * Get visualizers for a domain.
   */
  getVisualizersForDomain(domain: string): VisualizerPlugin[] {
    return Array.from(this.visualizerPlugins.values()).filter((p) =>
      p.supportedDomains.includes(domain)
    );
  }

  /**
   * Get all kernel plugins.
   */
  getAllKernels(): KernelPlugin[] {
    return Array.from(this.kernelPlugins.values());
  }

  /**
   * Get all prompt packs.
   */
  getAllPromptPacks(): PromptPackPlugin[] {
    return Array.from(this.promptPackPlugins.values());
  }

  /**
   * Get all visualizers.
   */
  getAllVisualizers(): VisualizerPlugin[] {
    return Array.from(this.visualizerPlugins.values());
  }

  /**
   * List all registered plugins.
   */
  listPlugins(): PluginMetadata[] {
    const all = [
      ...this.kernelPlugins.values(),
      ...this.promptPackPlugins.values(),
      ...this.visualizerPlugins.values(),
    ];
    return all.map((p) => p.metadata);
  }

  /**
   * Subscribe to plugin events.
   */
  on(event: string, callback: (plugin: SimulationPlugin) => void): () => void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(callback);

    return () => {
      this.listeners.get(event)?.delete(callback);
    };
  }

  /**
   * Emit a plugin event.
   */
  private emit(event: string, plugin: SimulationPlugin): void {
    this.listeners.get(event)?.forEach((cb) => cb(plugin));
  }

  /**
   * Clear all plugins.
   */
  clear(): void {
    this.kernelPlugins.clear();
    this.promptPackPlugins.clear();
    this.visualizerPlugins.clear();
  }
}

/**
 * Global plugin registry instance.
 */
export const pluginRegistry = new PluginRegistry();

/**
 * Helper to define a kernel plugin.
 */
export function defineKernelPlugin(plugin: KernelPlugin): KernelPlugin {
  return plugin;
}

/**
 * Helper to define a prompt pack plugin.
 */
export function definePromptPackPlugin(plugin: PromptPackPlugin): PromptPackPlugin {
  return plugin;
}

/**
 * Helper to define a visualizer plugin.
 */
export function defineVisualizerPlugin(plugin: VisualizerPlugin): VisualizerPlugin {
  return plugin;
}
