import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ToolchainAdapter } from './ToolchainAdapter.js';

/**
 * Toolchain adapter registry
 */
export class ToolchainAdapterRegistry {
  private registryPath: string;
  private adapters: Map<string, ToolchainAdapter> = new Map();

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.registryPath = path.join(configDir, 'toolchain-adapter-registry.json');
  }

  /**
   * Load adapter definitions from registry
   */
  async loadRegistry(): Promise<Record<string, AdapterDefinition>> {
    const content = await fs.readFile(this.registryPath, 'utf-8');
    const registry = JSON.parse(content);
    return registry.adapters;
  }

  /**
   * Register an adapter instance
   */
  register(adapter: ToolchainAdapter): void {
    this.adapters.set(adapter.id, adapter);
  }

  /**
   * Get an adapter by ID
   */
  get(id: string): ToolchainAdapter | undefined {
    return this.adapters.get(id);
  }

  /**
   * Check if an adapter is registered
   */
  has(id: string): boolean {
    return this.adapters.has(id);
  }

  /**
   * Get all registered adapters
   */
  getAll(): ToolchainAdapter[] {
    return Array.from(this.adapters.values());
  }

  /**
   * Get adapters that support a given phase
   */
  getByPhase(phase: string): ToolchainAdapter[] {
    return this.getAll().filter((adapter) => adapter.supportedPhases.includes(phase as any));
  }

  /**
   * Get adapters that support a given surface type
   */
  getBySurfaceType(surfaceType: string): ToolchainAdapter[] {
    return this.getAll().filter((adapter) => adapter.supportedSurfaceTypes.includes(surfaceType as any));
  }
}

/**
 * Adapter definition from registry
 */
export interface AdapterDefinition {
  kind: string;
  supportedSurfaceTypes: string[];
  supportedPhases: string[];
  requires: string[];
  outputs: string[];
  implementation?: string;
}
