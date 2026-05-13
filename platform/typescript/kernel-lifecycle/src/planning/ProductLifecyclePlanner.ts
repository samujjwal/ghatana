import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import * as yaml from 'yaml';
import {
  ProductLifecyclePhase,
  KernelProductConfiguration,
  LifecyclePlan,
  LifecyclePlanStep,
  LifecyclePhaseConfiguration,
  ProductSurface,
} from '../domain/ProductLifecyclePhase.js';

/**
 * Product lifecycle planner
 */
export class ProductLifecyclePlanner {
  private exclusionsPath: string;
  private registryPath: string;
  private profilesPath: string;
  private toolchainPath: string;
  private repoRoot: string;

  constructor(repoRoot: string = process.cwd(), configDir?: string) {
    this.repoRoot = repoRoot;
    const actualConfigDir = configDir || path.join(repoRoot, 'config');
    this.exclusionsPath = path.join(actualConfigDir, 'kernel-lifecycle-exclusions.json');
    this.registryPath = path.join(actualConfigDir, 'canonical-product-registry.json');
    this.profilesPath = path.join(actualConfigDir, 'product-lifecycle-profiles.json');
    this.toolchainPath = path.join(actualConfigDir, 'toolchain-adapter-registry.json');
  }

  private async loadRegistry(): Promise<Record<string, Record<string, unknown>>> {
    const registry = JSON.parse(await fs.readFile(this.registryPath, 'utf-8'));
    return registry.registry as Record<string, Record<string, unknown>>;
  }

  private async loadToolchains(): Promise<Record<string, Record<string, unknown>>> {
    const toolchains = JSON.parse(await fs.readFile(this.toolchainPath, 'utf-8'));
    return toolchains.adapters as Record<string, Record<string, unknown>>;
  }

  private async loadExclusions(): Promise<Set<string>> {
    try {
      const exclusions = JSON.parse(await fs.readFile(this.exclusionsPath, 'utf-8'));
      return new Set(Object.keys(exclusions.excludedProducts ?? {}));
    } catch {
      return new Set();
    }
  }

  /**
   * Load product lifecycle configuration
   */
  async loadProductConfig(productId: string): Promise<KernelProductConfiguration> {
    const registry = await this.loadRegistry();
    const exclusions = await this.loadExclusions();
    const product = registry[productId];

    if (!product) {
      throw new Error(`Product ${productId} not found in registry`);
    }

    if (exclusions.has(productId)) {
      throw new Error(`Product ${productId} is excluded from kernel lifecycle execution`);
    }

    const lifecycleStatus = product.lifecycleStatus;
    const lifecycle = (product.lifecycle ?? {}) as { enabled?: boolean };
    const lifecycleConfigPath = product.lifecycleConfigPath;

    if (lifecycleStatus !== 'enabled' || lifecycle.enabled !== true) {
      throw new Error(`Product ${productId} does not have lifecycle execution enabled`);
    }

    if (typeof lifecycleConfigPath !== 'string' || lifecycleConfigPath.length === 0) {
      throw new Error(`Product ${productId} does not have lifecycleConfigPath`);
    }

    const configPath = path.join(this.repoRoot, lifecycleConfigPath);
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
      sourceRef?: string;
      outputDir?: string;
    } = {},
  ): Promise<LifecyclePlan> {
    const config = await this.loadProductConfig(productId);
    const profile = await this.loadLifecycleProfile(config.lifecycleProfile);
    const toolchains = await this.loadToolchains();

    const phaseConfig = this.resolvePhaseConfig(phase, config, profile);
    const surfaces = options.surfaceSelector || phaseConfig.defaultSurfaces;

    const steps: LifecyclePlanStep[] = [];
    let stepIndex = 0;

    for (const surface of surfaces) {
      const surfaceConfig = config.surfaces[surface];
      if (!surfaceConfig) {
        throw new Error(`Surface ${surface} not found in product config`);
      }

      this.validateAdapter(surface, surfaceConfig, phase, toolchains);
      const command = this.resolveCommand(phase, surface, surfaceConfig);

      const step: LifecyclePlanStep = {
        id: `${phase}-${surface}-${stepIndex++}`,
        phase,
        surface,
        adapter: surfaceConfig.adapter,
        description: `Execute ${phase} phase for ${surface} using ${surfaceConfig.adapter}`,
        dependsOn: [],
        estimatedDurationMs: 30000,
        execution: command,
      };

      if (phaseConfig.mode === 'sequential' && steps.length > 0) {
        step.dependsOn = [steps[steps.length - 1].id];
      }

      steps.push(step);
    }

    const estimatedDurationMs = this.estimateDuration(steps);
    return {
      schemaVersion: '1.0.0',
      productId,
      phase,
      lifecycleProfile: config.lifecycleProfile,
      ...(options.environment ? { environment: options.environment } : {}),
      ...(options.sourceRef ? { sourceRef: options.sourceRef } : {}),
      surfaces,
      gates: [],
      steps,
      expectedArtifacts: [],
      outputDirectory: this.resolveOutputDirectory(productId, phase, options.outputDir),
      estimatedDurationMs,
    };
  }

  private validateAdapter(
    surfaceName: string,
    surfaceConfig: ProductSurface,
    phase: ProductLifecyclePhase,
    toolchains: Record<string, Record<string, unknown>>,
  ): void {
    const adapterId = String(surfaceConfig.adapter ?? '');
    if (!adapterId) {
      throw new Error(`Surface ${surfaceName} does not declare an adapter`);
    }

    const adapter = toolchains[adapterId];
    if (!adapter) {
      throw new Error(`Unknown adapter ${adapterId} for surface ${surfaceName}`);
    }

    const supportedPhases = (adapter.supportedPhases ?? []) as string[];
    if (!supportedPhases.includes(phase)) {
      throw new Error(`Adapter ${adapterId} does not support phase ${phase}`);
    }

    const supportedSurfaceTypes = (adapter.supportedSurfaceTypes ?? []) as string[];
    if (!supportedSurfaceTypes.includes(surfaceName)) {
      throw new Error(`Adapter ${adapterId} does not support surface ${surfaceName}`);
    }

    const requiredFields = (adapter.requires ?? []) as string[];
    for (const requiredField of requiredFields) {
      if (surfaceConfig[requiredField] === undefined) {
        throw new Error(`Adapter ${adapterId} for surface ${surfaceName} requires ${requiredField}`);
      }
    }
  }

  private resolveCommand(
    phase: ProductLifecyclePhase,
    surfaceName: string,
    surfaceConfig: ProductSurface,
  ): { command: string; args: string[]; workingDirectory: string } {
    const adapterId = String(surfaceConfig.adapter);

    if (adapterId === 'gradle-java-service') {
      const taskByPhase: Partial<Record<ProductLifecyclePhase, string>> = {
        dev: String(surfaceConfig.devTask ?? 'bootRun'),
        validate: String(surfaceConfig.validateTask ?? 'check'),
        test: String(surfaceConfig.testTask ?? 'test'),
        build: String(surfaceConfig.buildTask ?? 'build'),
        package: String(surfaceConfig.packageTask ?? 'assemble'),
      };
      const task = taskByPhase[phase];
      if (!task) {
        throw new Error(`Unsupported phase ${phase} for ${adapterId} on ${surfaceName}`);
      }
      return {
        command: process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew',
        args: [`${String(surfaceConfig.gradleModule)}:${task}`, '--no-daemon'],
        workingDirectory: this.repoRoot,
      };
    }

    if (adapterId === 'pnpm-vite-react') {
      const scriptByPhase: Partial<Record<ProductLifecyclePhase, string>> = {
        dev: String(surfaceConfig.devScript ?? 'dev'),
        validate: String(surfaceConfig.validateScript ?? 'type-check'),
        test: String(surfaceConfig.testScript ?? 'test'),
        build: String(surfaceConfig.buildScript ?? 'build'),
        package: String(surfaceConfig.packageScript ?? 'build'),
      };
      const script = scriptByPhase[phase];
      if (!script) {
        throw new Error(`Unsupported phase ${phase} for ${adapterId} on ${surfaceName}`);
      }
      const packagePath = String(surfaceConfig.packagePath);
      const packageDirectory = packagePath.endsWith('package.json')
        ? path.dirname(packagePath)
        : packagePath;
      return {
        command: 'pnpm',
        args: ['--dir', packageDirectory, 'run', script],
        workingDirectory: this.repoRoot,
      };
    }

    throw new Error(`Execution mapping is not implemented for adapter ${adapterId} on ${surfaceName}`);
  }

  private resolveOutputDirectory(productId: string, phase: ProductLifecyclePhase, outputDir?: string): string {
    if (outputDir && outputDir.length > 0) {
      return path.isAbsolute(outputDir) ? outputDir : path.resolve(this.repoRoot, outputDir);
    }
    return path.join(this.repoRoot, '.kernel', 'out', 'products', productId, phase, 'latest');
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
