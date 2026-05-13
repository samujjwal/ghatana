import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import * as yaml from 'yaml';
import {
  ProductLifecyclePhase,
  KernelProductConfiguration,
  LifecyclePlan,
  LifecyclePlanStep,
  LifecyclePhaseConfiguration,
} from '../domain/ProductLifecyclePhase.js';

/**
 * Product lifecycle planner
 */
export class ProductLifecyclePlanner {
  private registryPath: string;
  private profilesPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.registryPath = path.join(configDir, 'canonical-product-registry.json');
    this.profilesPath = path.join(configDir, 'product-lifecycle-profiles.json');
  }

  /**
   * Load product lifecycle configuration
   */
  async loadProductConfig(productId: string): Promise<KernelProductConfiguration> {
    const registry = JSON.parse(await fs.readFile(this.registryPath, 'utf-8'));
    const product = registry.registry[productId];

    if (!product) {
      throw new Error(`Product ${productId} not found in registry`);
    }

    if (!product.lifecycleConfigPath) {
      throw new Error(`Product ${productId} does not have lifecycleConfigPath`);
    }

    const configPath = path.join('/Users/samujjwal/Development/ghatana', product.lifecycleConfigPath);
    const configContent = await fs.readFile(configPath, 'utf-8');
    const config = yaml.parse(configContent);

    return {
      productId: config.productId,
      lifecycleProfile: config.lifecycleProfile,
      surfaces: config.surfaces,
      phases: config.phases,
    };
  }

  /**
   * Load lifecycle profile
   */
  async loadLifecycleProfile(profileId: string): Promise<Record<string, unknown>> {
    const profiles = JSON.parse(await fs.readFile(this.profilesPath, 'utf-8'));
    const profile = profiles.profiles[profileId];

    if (!profile) {
      throw new Error(`Lifecycle profile ${profileId} not found`);
    }

    return profile;
  }

  /**
   * Plan a lifecycle phase
   */
  async plan(
    productId: string,
    phase: ProductLifecyclePhase,
    options: {
      surfaceSelector?: string[];
      environment?: string;
    } = {},
  ): Promise<LifecyclePlan> {
    const config = await this.loadProductConfig(productId);
    const profile = await this.loadLifecycleProfile(config.lifecycleProfile);

    const phaseConfig = this.resolvePhaseConfig(phase, config, profile);
    const surfaces = options.surfaceSelector || phaseConfig.defaultSurfaces;

    const steps: LifecyclePlanStep[] = [];
    let stepIndex = 0;

    for (const surface of surfaces) {
      const surfaceConfig = config.surfaces[surface];
      if (!surfaceConfig) {
        throw new Error(`Surface ${surface} not found in product config`);
      }

      const step: LifecyclePlanStep = {
        id: `${phase}-${surface}-${stepIndex++}`,
        phase,
        surface,
        adapter: surfaceConfig.adapter,
        description: `Execute ${phase} phase for ${surface} using ${surfaceConfig.adapter}`,
        dependsOn: [],
      };

      if (phaseConfig.mode === 'sequential' && steps.length > 0) {
        step.dependsOn = [steps[steps.length - 1].id];
      }

      steps.push(step);
    }

    return {
      productId,
      phase,
      surfaces,
      steps,
      estimatedDurationMs: this.estimateDuration(steps),
    };
  }

  /**
   * Resolve phase configuration from product config and profile
   */
  private resolvePhaseConfig(
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    profile: Record<string, unknown>,
  ): LifecyclePhaseConfiguration {
    // Check for product-specific phase override
    if (config.phases[phase]) {
      return config.phases[phase];
    }

    // Fall back to profile default
    const profileDefaultSurfaces = (profile.defaultSurfaces as Record<string, string[]>)[phase];
    if (!profileDefaultSurfaces) {
      throw new Error(`Phase ${phase} not defined in profile ${config.lifecycleProfile}`);
    }

    return {
      defaultSurfaces: profileDefaultSurfaces,
      mode: 'sequential',
    };
  }

  /**
   * Estimate duration for a plan (heuristic)
   */
  private estimateDuration(steps: LifecyclePlanStep[]): number {
    // Simple heuristic: 30 seconds per step
    return steps.length * 30000;
  }
}
