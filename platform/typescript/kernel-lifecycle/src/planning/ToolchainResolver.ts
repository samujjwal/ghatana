import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { ProductSurfaceType, ProductLifecyclePhase } from '../domain/ProductLifecyclePhase.js';

/**
 * Toolchain resolver for lifecycle planning
 */
export class ToolchainResolver {
  private registryPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.registryPath = path.join(configDir, 'toolchain-adapter-registry.json');
  }

  /**
   * Resolve adapter by ID
   */
  async resolve(adapterId: string): Promise<ToolchainAdapter> {
    const registry = JSON.parse(await fs.readFile(this.registryPath, 'utf-8'));
    const adapter = registry.adapters[adapterId];

    if (!adapter) {
      throw new Error(`Toolchain adapter ${adapterId} not found`);
    }

    return adapter as ToolchainAdapter;
  }

  /**
   * Get adapter for surface and phase
   */
  async getAdapterForSurfaceAndPhase(
    surfaceType: ProductSurfaceType,
    phase: ProductLifecyclePhase,
  ): Promise<string[]> {
    const registry = JSON.parse(await fs.readFile(this.registryPath, 'utf-8'));
    const adapters: string[] = [];

    for (const [adapterId, adapter] of Object.entries(registry.adapters)) {
      const adapterData = adapter as ToolchainAdapter;
      if (
        adapterData.supportedSurfaceTypes.includes(surfaceType) &&
        adapterData.supportedPhases.includes(phase)
      ) {
        adapters.push(adapterId);
      }
    }

    return adapters;
  }

  /**
   * Validate adapter supports phase and surface
   */
  async validateAdapter(
    adapterId: string,
    phase: ProductLifecyclePhase,
    surfaceType: ProductSurfaceType,
  ): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];

    try {
      const adapter = await this.resolve(adapterId);

      if (!adapter.supportedPhases.includes(phase)) {
        errors.push({
          path: 'phase',
          message: `Adapter ${adapterId} does not support phase ${phase}`,
        });
      }

      if (!adapter.supportedSurfaceTypes.includes(surfaceType)) {
        errors.push({
          path: 'surfaceType',
          message: `Adapter ${adapterId} does not support surface type ${surfaceType}`,
        });
      }
    } catch (e) {
      errors.push({
        path: 'adapterId',
        message: `Adapter ${adapterId} not found in registry`,
      });
    }

    return errors;
  }

  /**
   * Get required fields for adapter
   */
  async getRequiredFields(adapterId: string): Promise<string[]> {
    const adapter = await this.resolve(adapterId);
    return adapter.requires || [];
  }

  /**
   * Get expected outputs for adapter
   */
  async getExpectedOutputs(adapterId: string): Promise<string[]> {
    const adapter = await this.resolve(adapterId);
    return adapter.outputs || [];
  }
}

/**
 * Toolchain adapter
 */
export interface ToolchainAdapter {
  kind: string;
  supportedSurfaceTypes: ProductSurfaceType[];
  supportedPhases: ProductLifecyclePhase[];
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
