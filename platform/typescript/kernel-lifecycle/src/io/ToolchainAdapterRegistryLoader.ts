import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Toolchain adapter registry loader
 */
export class ToolchainAdapterRegistryLoader {
  private registryPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.registryPath = path.join(configDir, 'toolchain-adapter-registry.json');
  }

  /**
   * Load toolchain adapter registry
   */
  async load(): Promise<ToolchainAdapterRegistry> {
    const content = await fs.readFile(this.registryPath, 'utf-8');
    return JSON.parse(content) as ToolchainAdapterRegistry;
  }

  /**
   * Get adapter by ID
   */
  async getAdapter(adapterId: string): Promise<ToolchainAdapter> {
    const registry = await this.load();
    const adapter = registry.adapters[adapterId];

    if (!adapter) {
      throw new Error(`Toolchain adapter ${adapterId} not found`);
    }

    return adapter;
  }

  /**
   * Get all adapter IDs
   */
  async getAdapterIds(): Promise<string[]> {
    const registry = await this.load();
    return Object.keys(registry.adapters);
  }

  /**
   * Get adapters by kind
   */
  async getAdaptersByKind(kind: string): Promise<ToolchainAdapter[]> {
    const registry = await this.load();
    return Object.values(registry.adapters).filter((a) => a.kind === kind);
  }

  /**
   * Validate adapter structure
   */
  async validateAdapter(adapterId: string): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];

    try {
      const adapter = await this.getAdapter(adapterId);

      if (!adapter.kind) {
        errors.push({ path: 'kind', message: 'Kind is required' });
      }

      if (!adapter.supportedSurfaceTypes || adapter.supportedSurfaceTypes.length === 0) {
        errors.push({ path: 'supportedSurfaceTypes', message: 'Supported surface types are required' });
      }

      if (!adapter.supportedPhases || adapter.supportedPhases.length === 0) {
        errors.push({ path: 'supportedPhases', message: 'Supported phases are required' });
      }

      if (!adapter.implementation) {
        errors.push({ path: 'implementation', message: 'Implementation path is required' });
      }
    } catch (error) {
      errors.push({
        path: 'root',
        message: `Failed to load adapter: ${error instanceof Error ? error.message : String(error)}`,
      });
    }

    return errors;
  }
}

/**
 * Toolchain adapter registry
 */
export interface ToolchainAdapterRegistry {
  version: string;
  adapters: Record<string, ToolchainAdapter>;
}

/**
 * Toolchain adapter
 */
export interface ToolchainAdapter {
  kind: string;
  supportedSurfaceTypes: string[];
  supportedPhases: string[];
  requires: string[];
  outputs: string[];
  implementation: string;
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
