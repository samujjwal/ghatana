import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import * as crypto from 'node:crypto';
import * as yaml from 'yaml';
import {
  ProductLifecyclePhase,
  KernelProductConfiguration,
  PackageSurfaceConfig,
  DeploymentEnvironmentConfig,
  VerifyEnvironmentConfig,
  ArtifactConfig,
  ProductLifecyclePlan,
  ProductLifecycleStep,
  LifecyclePhaseConfiguration,
  ProductSurface,
  ProductSurfaceSelection,
  ProductGatePlan,
  ProductExpectedArtifact,
  LifecycleStepKind,
  ProductLifecycleManifestType,
  ProductLifecycleRequiredPlugin,
  ProductLifecycleApprovalRequirement,
  ProductLifecycleApprovalRequirementConfig,
  ProductLifecycleHealthCheck,
} from '../domain/ProductLifecyclePhase.js';
import type {
  KernelLifecycleProviderContext,
  KernelProviderMode,
  ProductUnit,
} from '@ghatana/kernel-product-contracts';

/** Phases that are handled by the deployment target adapter, NOT surface adapters. */
const DEPLOYMENT_PHASES = new Set<ProductLifecyclePhase>(['deploy', 'verify', 'rollback', 'promote']);

/** Phases that use the package adapter from product package config. */
const PACKAGE_PHASES = new Set<ProductLifecyclePhase>(['package']);

/** Surface phases that delegate to the surface-level build adapter. */
const SURFACE_PHASES = new Set<ProductLifecyclePhase>(['dev', 'validate', 'test', 'build']);
const NO_EXPECTED_ARTIFACTS: readonly ProductExpectedArtifact[] = Object.freeze([]);

/** Internal registry shape loaded from toolchain-adapter-registry.json */
type ToolchainRegistry = Record<string, Record<string, unknown>>;

// Re-export KernelLifecycleProviderContext as-is since it already has all required properties
export type ProductLifecyclePlannerProviderContext = KernelLifecycleProviderContext;

export interface ProductLifecyclePlanOptions {
  readonly surfaceSelector?: string[];
  readonly environment?: string;
  readonly sourceRef?: string;
  readonly outputDir?: string;
  readonly runId?: string;
  readonly correlationId?: string;
  readonly providerMode?: KernelProviderMode;
  readonly shapeOnly?: boolean;
  readonly allowBootstrapFallback?: boolean;
  readonly semanticArtifactRefs?: readonly string[];
}

export interface ProductLifecyclePlannerLogger {
  warn(message: string, meta?: Record<string, unknown>): void;
}

export class ProductLifecycleNotReadyError extends Error {
  readonly reasonCode = 'not-ready';

  constructor(
    readonly productId: string,
    readonly lifecycleStatus: string,
    readonly lifecycleExecutionAllowed: boolean | undefined,
    readonly readinessReasonCodes: readonly string[] = [],
    readonly readinessRequiredGates: readonly string[] = [],
  ) {
    super(
      `Product "${productId}" is not ready for lifecycle execution: ` +
        `lifecycleStatus="${lifecycleStatus}", lifecycleExecutionAllowed=${String(lifecycleExecutionAllowed)}.`,
    );
    this.name = 'ProductLifecycleNotReadyError';
  }

  toSafeDetails(): Record<string, unknown> {
    return {
      productId: this.productId,
      lifecycleStatus: this.lifecycleStatus,
      lifecycleExecutionAllowed: this.lifecycleExecutionAllowed,
      ...(this.readinessReasonCodes.length > 0
        ? { readinessReasonCodes: this.readinessReasonCodes }
        : {}),
      ...(this.readinessRequiredGates.length > 0
        ? { readinessRequiredGates: this.readinessRequiredGates }
        : {}),
    };
  }
}

const fallbackPlannerLogger: ProductLifecyclePlannerLogger = {
  warn: () => undefined,
};


/**
 * @doc.type class
 * @doc.purpose Plans kernel product lifecycle phases, resolving the correct adapter per phase:
 *   surface adapters for dev/validate/test/build, package adapters for package,
 *   and deployment target adapters for deploy/verify/rollback.
 * @doc.layer platform
 * @doc.pattern Service
 */
export class ProductLifecyclePlanner {
  private readonly exclusionsPath: string;
  private readonly registryPath: string;
  private readonly profilesPath: string;
  private readonly toolchainPath: string;
  private readonly repoRoot: string;
  private readonly providerContext: ProductLifecyclePlannerProviderContext | undefined;
  private readonly logger: ProductLifecyclePlannerLogger;

  constructor(
    repoRoot: string = process.cwd(),
    configDir?: string,
    providerContext?: ProductLifecyclePlannerProviderContext,
    logger: ProductLifecyclePlannerLogger = fallbackPlannerLogger,
  ) {
    this.repoRoot = repoRoot;
    const actualConfigDir = configDir ?? path.join(repoRoot, 'config');
    this.exclusionsPath = path.join(actualConfigDir, 'kernel-lifecycle-exclusions.json');
    this.registryPath = path.join(actualConfigDir, 'canonical-product-registry.json');
    this.profilesPath = path.join(actualConfigDir, 'product-lifecycle-profiles.json');
    this.toolchainPath = path.join(actualConfigDir, 'toolchain-adapter-registry.json');
    this.providerContext = providerContext;
    this.logger = logger;
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Public API
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  /**
   * Load ProductUnit from provider context or fall back to registry-based validation.
   */
  async loadProductUnit(productId: string): Promise<ProductUnit | null> {
    if (this.providerContext?.registryProvider) {
      try {
        return await this.providerContext.registryProvider.getProductUnit(productId);
      } catch (error) {
        this.logger.warn('Provider lookup failed; falling back to file-based ProductUnit validation', {
          reasonCode: 'registry-provider-fallback',
          productId,
          cause: error instanceof Error ? error.message : String(error),
        });
      }
    }
    return null; // Provider not available, will use file-based validation
  }

  /**
   * Load product lifecycle configuration from the canonical registry and kernel-product.yaml.
   */
  async loadProductConfig(productId: string): Promise<KernelProductConfiguration> {
    const registry = await this.loadRegistry();
    const exclusions = await this.loadExclusions();
    const product = registry[productId];

    if (!product) {
      throw new Error(
        `Product "${productId}" not found in the canonical registry. ` +
          'Check config/canonical-product-registry.json.',
      );
    }

    if (exclusions.has(productId)) {
      throw new Error(
        `Product "${productId}" is excluded from Kernel lifecycle execution. ` +
          'Check config/kernel-lifecycle-exclusions.json.',
      );
    }

    const lifecycleStatus = product.lifecycleStatus;
    const lifecycle = (product.lifecycle ?? {}) as { enabled?: boolean };
    const lifecycleExecutionAllowed =
      typeof product.lifecycleExecutionAllowed === 'boolean'
        ? product.lifecycleExecutionAllowed
        : undefined;
    const lifecycleReadiness =
      typeof product.lifecycleReadiness === 'object' && product.lifecycleReadiness !== null
        ? (product.lifecycleReadiness as {
            readonly reasonCodes?: readonly string[];
            readonly requiredGates?: readonly string[];
          })
        : undefined;
    const readinessReasonCodes = Array.isArray(lifecycleReadiness?.reasonCodes)
      ? lifecycleReadiness.reasonCodes.filter((value): value is string => typeof value === 'string')
      : [];
    const readinessRequiredGates = Array.isArray(lifecycleReadiness?.requiredGates)
      ? lifecycleReadiness.requiredGates.filter((value): value is string => typeof value === 'string')
      : [];

    if (lifecycleExecutionAllowed === false) {
      throw new ProductLifecycleNotReadyError(
        productId,
        typeof lifecycleStatus === 'string' ? lifecycleStatus : 'unknown',
        lifecycleExecutionAllowed,
        readinessReasonCodes,
        readinessRequiredGates,
      );
    }

    if (lifecycleStatus !== 'enabled' || lifecycle.enabled !== true) {
      throw new ProductLifecycleNotReadyError(
        productId,
        typeof lifecycleStatus === 'string' ? lifecycleStatus : 'unknown',
        lifecycleExecutionAllowed,
        readinessReasonCodes,
        readinessRequiredGates,
      );
    }

    const lifecycleConfigPath = product.lifecycleConfigPath;
    if (typeof lifecycleConfigPath !== 'string' || lifecycleConfigPath.length === 0) {
      throw new Error(
        `Product "${productId}" does not declare lifecycleConfigPath in the registry. ` +
          'Add lifecycleConfigPath pointing to the kernel-product.yaml file.',
      );
    }

    const configPath = path.join(this.repoRoot, lifecycleConfigPath);
    let configContent: string;
    try {
      configContent = await fs.readFile(configPath, 'utf-8');
    } catch {
      throw new Error(
        `kernel-product.yaml not found for product "${productId}" at ${configPath}. ` +
          'Create the file or fix lifecycleConfigPath in the registry.',
      );
    }

    const config = this.parseYamlObject(configContent, configPath);
    this.assertKernelProductConfigShape(config, productId, configPath);

    return {
      productId: String(config.productId ?? productId),
      lifecycleProfile: String(config.lifecycleProfile ?? ''),
      allowExperimentalAdapters: Boolean(config.allowExperimentalAdapters ?? false),
      ...(config.requiredManifests !== undefined
        ? { requiredManifests: config.requiredManifests as NonNullable<KernelProductConfiguration['requiredManifests']> }
        : {}),
      ...(config.plugins !== undefined
        ? { plugins: config.plugins as NonNullable<KernelProductConfiguration['plugins']> }
        : {}),
      ...(config.environments !== undefined
        ? { environments: config.environments as NonNullable<KernelProductConfiguration['environments']> }
        : {}),
      ...(config.approvals !== undefined
        ? { approvals: config.approvals as NonNullable<KernelProductConfiguration['approvals']> }
        : {}),
      surfaces: (config.surfaces ?? {}) as KernelProductConfiguration['surfaces'],
      phases: (config.phases ?? {}) as KernelProductConfiguration['phases'],
      ...(config.package !== undefined ? { package: config.package as Record<string, PackageSurfaceConfig> } : {}),
      ...(config.deployment !== undefined ? { deployment: config.deployment as Record<string, DeploymentEnvironmentConfig> } : {}),
      ...(config.verify !== undefined ? { verify: config.verify as Record<string, VerifyEnvironmentConfig> } : {}),
      ...(config.gates !== undefined ? { gates: config.gates as Record<string, string[]> } : {}),
      ...(config.artifacts !== undefined ? { artifacts: config.artifacts as Record<string, Record<string, ArtifactConfig>> } : {}),
    };
  }

  /** Load the lifecycle profile by ID. */
  async loadLifecycleProfile(profileId: string): Promise<Record<string, unknown>> {
    const raw = JSON.parse(await fs.readFile(this.profilesPath, 'utf-8')) as unknown;
    this.assertObject(raw, this.profilesPath, 'profiles root');
    this.assertRecordProperty(raw, 'profiles', this.profilesPath);
    const profiles = raw.profiles as Record<string, unknown>;
    const profile = profiles[profileId];
    if (!profile) {
      throw new Error(
        `Lifecycle profile "${profileId}" not found in product-lifecycle-profiles.json.`,
      );
    }
    this.assertObject(profile, this.profilesPath, `profiles.${profileId}`);
    this.assertLifecycleProfileShape(profile, profileId, this.profilesPath);
    return profile as Record<string, unknown>;
  }

  /**
   * Plan a lifecycle phase.
   *
   * Routing rules:
   * - dev / validate / test / build â†’ surface adapters (gradle-java-service, pnpm-vite-react)
   * - package                       â†’ package adapters (docker-buildx)
   * - deploy / verify / rollback    â†’ deployment target adapter (compose-local)
   */
  async plan(
    productId: string,
    phase: ProductLifecyclePhase,
    options: ProductLifecyclePlanOptions = {},
  ): Promise<ProductLifecyclePlan> {
    const requestedMode = options.providerMode ?? this.providerContext?.mode ?? 'bootstrap';
    const providerMode = this.assertProviderModeAvailable(
      requestedMode,
      options.allowBootstrapFallback ?? false,
      options.correlationId,
    );

    const [config, toolchains, productUnit] = await Promise.all([
      this.loadProductConfig(productId),
      this.loadToolchains(),
      this.loadProductUnit(productId),
    ]);

    const profile = await this.loadLifecycleProfile(config.lifecycleProfile);
    const runId = options.runId ?? this.generateRunId();
    const correlationId = options.correlationId ?? `corr-${runId}`;

    const outputDirectory = this.resolveOutputDirectory(
      productId,
      phase,
      runId,
      options.outputDir,
    );

    let steps: ProductLifecycleStep[];
    let selectedSurfaces: ProductSurfaceSelection[];
    let phaseMode: ProductLifecyclePlan['phaseMode'];

    if (DEPLOYMENT_PHASES.has(phase)) {
      ({ steps, selectedSurfaces, phaseMode } = await this.resolveDeploymentSteps(
        productId,
        phase,
        config,
        options.environment,
        toolchains,
        options.shapeOnly ?? false,
      ));
    } else if (PACKAGE_PHASES.has(phase)) {
      ({ steps, selectedSurfaces, phaseMode } = await this.resolvePackageSteps(
        productId,
        phase,
        config,
        profile,
        toolchains,
        options.surfaceSelector,
        options.shapeOnly ?? false,
      ));
    } else if (SURFACE_PHASES.has(phase)) {
      ({ steps, selectedSurfaces, phaseMode } = this.resolveSurfaceSteps(
        productId,
        phase,
        config,
        profile,
        toolchains,
        options.surfaceSelector,
        options.shapeOnly ?? false,
      ));
    } else {
      throw new Error(
        `Phase "${phase}" is not yet supported by Kernel lifecycle planning. ` +
          'Supported phases: dev, validate, test, build, package, deploy, verify, rollback, promote.',
      );
    }

    const gates = this.resolveGates(phase, config, profile);
    const expectedArtifacts = this.resolveExpectedArtifacts(productId, phase, config, profile, selectedSurfaces);
    const requiredManifests = this.resolveRequiredManifests(phase, config);
    const requiredPlugins = this.resolveRequiredPlugins(config);
    const approvalRequirements = this.resolveApprovalRequirements(phase, config, options.environment);
    const adapterIds = [...new Set(steps.map((s) => s.adapter).filter(Boolean))];
    const healthChecks = this.resolveHealthChecks(selectedSurfaces);

    return {
      schemaVersion: '1.0.0',
      runId,
      correlationId,
      providerMode,
      productId,
      productUnitId: productId,
      ...(productUnit ? { productUnitRef: productUnit.id } : {}),
      ...this.resolveProviderRefs(productUnit),
      phase,
      phaseMode,
      lifecycleProfile: config.lifecycleProfile,
      ...(options.environment ? { environment: options.environment } : {}),
      ...(options.sourceRef ? { sourceRef: options.sourceRef } : {}),
      ...(productUnit ? { productUnit } : {}),
      surfaces: selectedSurfaces,
      gates,
      steps,
      adapterIds,
      expectedArtifacts,
      requiredManifests,
      requiredPlugins,
      approvalRequirements,
      healthChecks,
      outputDirectory,
      estimatedDurationMs: this.estimateDuration(steps),
      warnings: [],
      blockingReasons: [],
    };
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Phase resolvers
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  /**
   * Resolve steps for surface phases (dev / validate / test / build).
   */
  private resolveSurfaceSteps(
    productId: string,
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    profile: Record<string, unknown>,
    toolchains: ToolchainRegistry,
    surfaceSelector?: string[],
    shapeOnly: boolean = false,
  ): { steps: ProductLifecycleStep[]; selectedSurfaces: ProductSurfaceSelection[]; phaseMode: ProductLifecyclePlan['phaseMode'] } {
    const phaseConfig = this.resolvePhaseConfig(phase, config, profile);
    const surfaces = surfaceSelector ?? phaseConfig.defaultSurfaces;
    const phaseMode = this.mapMode(phaseConfig.mode);

    const selectedSurfaces: ProductSurfaceSelection[] = [];
    const steps: ProductLifecycleStep[] = [];
    let stepIndex = 0;

    for (const surfaceName of surfaces) {
      const surfaceConfig = config.surfaces[surfaceName];
      if (!surfaceConfig) {
        throw new Error(
          `Surface "${surfaceName}" declared for phase "${phase}" is not defined in ` +
            `kernel-product.yaml surfaces for product "${productId}".`,
        );
      }

      const surfaceType = this.resolveSurfaceType(surfaceName, surfaceConfig);
      this.validateSurfaceAdapter(surfaceName, surfaceConfig, surfaceType, phase, toolchains, config, shapeOnly);

      selectedSurfaces.push({
        surface: surfaceName,
        type: surfaceType,
        adapter: surfaceConfig.adapter,
        config: { ...surfaceConfig } as Record<string, unknown>,
      });

      const stepId = `${phase}-${surfaceName}-${stepIndex++}`;
      const step: ProductLifecycleStep = {
        id: stepId,
        stepKind: 'surface',
        phase,
        surface: surfaceName,
        adapter: String(surfaceConfig.adapter),
        adapterSelectionSource:
          String(surfaceConfig.adapter) === this.resolveProfileAdapter(profile, surfaceName)
            ? 'profile-default'
            : 'product-config-override',
        description: `${phase} ${surfaceName} via ${String(surfaceConfig.adapter)}`,
        dependsOn:
          phaseMode === 'sequential' && steps.length > 0
            ? [steps[steps.length - 1].id]
            : [],
        estimatedDurationMs: 30_000,
        adapterContext: {
          surfaceConfig: { ...surfaceConfig } as Record<string, unknown>,
          ...(config.artifacts?.[phase]?.[surfaceName] !== undefined
            ? { artifactConfig: config.artifacts[phase][surfaceName] as unknown as Record<string, unknown> }
            : {}),
        },
      };

      steps.push(step);
    }

    return { steps, selectedSurfaces, phaseMode };
  }

  /**
   * Resolve steps for the package phase â€” uses docker-buildx per surface.
   */
  private async resolvePackageSteps(
    productId: string,
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    profile: Record<string, unknown>,
    toolchains: ToolchainRegistry,
    surfaceSelector?: string[],
    shapeOnly: boolean = false,
  ): Promise<{
    steps: ProductLifecycleStep[];
    selectedSurfaces: ProductSurfaceSelection[];
    phaseMode: ProductLifecyclePlan['phaseMode'];
  }> {
    const packageConfig = config.package;
    if (!packageConfig || Object.keys(packageConfig).length === 0) {
      throw new Error(
        `Product "${productId}" does not declare a "package" section in kernel-product.yaml. ` +
          'Add a package section with per-surface docker-buildx adapter config.',
      );
    }

    const phaseConfig = config.phases['package'];
    const surfaces =
      surfaceSelector ?? (phaseConfig?.defaultSurfaces ?? Object.keys(packageConfig));
    const phaseMode = this.mapMode(phaseConfig?.mode ?? 'sequential');

    const selectedSurfaces: ProductSurfaceSelection[] = [];
    const steps: ProductLifecycleStep[] = [];
    let stepIndex = 0;

    for (const surfaceName of surfaces) {
      const surfacePkgConfig = packageConfig[surfaceName];
      if (!surfacePkgConfig) {
        throw new Error(
          `Surface "${surfaceName}" is selected for packaging but has no entry under ` +
            `"package" in kernel-product.yaml for product "${productId}".`,
        );
      }

      const adapterId = surfacePkgConfig.adapter ?? 'docker-buildx';
      this.validateAdapterForPhase(adapterId, phase, toolchains, config, shapeOnly);

      selectedSurfaces.push({
        surface: surfaceName,
        type:
          config.surfaces[surfaceName] !== undefined
            ? this.resolveSurfaceType(surfaceName, config.surfaces[surfaceName])
            : 'backend-api',
        adapter: adapterId,
        config: { ...surfacePkgConfig } as Record<string, unknown>,
      });

      const stepId = `${phase}-${surfaceName}-${stepIndex++}`;
      const step: ProductLifecycleStep = {
        id: stepId,
        stepKind: 'package',
        phase,
        surface: surfaceName,
        adapter: adapterId,
        adapterSelectionSource:
          adapterId === this.resolveProfileAdapter(profile, `package.${surfaceName}`)
            ? 'profile-default'
            : 'product-config-override',
        description: `package ${surfaceName} container image via ${adapterId}`,
        dependsOn:
          phaseMode === 'sequential' && steps.length > 0
            ? [steps[steps.length - 1].id]
            : [],
        estimatedDurationMs: 120_000,
        adapterContext: {
          packageConfig: { ...surfacePkgConfig } as Record<string, unknown>,
          ...(config.artifacts?.['package']?.[surfaceName] !== undefined
            ? { artifactConfig: config.artifacts['package'][surfaceName] as unknown as Record<string, unknown> }
            : {}),
        },
      };

      steps.push(step);
    }

    return { steps, selectedSurfaces, phaseMode };
  }

  /**
   * Resolve steps for deploy / verify / rollback â€” uses the deployment target adapter.
   */
  private async resolveDeploymentSteps(
    productId: string,
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    environment: string | undefined,
    toolchains: ToolchainRegistry,
    shapeOnly: boolean = false,
  ): Promise<{
    steps: ProductLifecycleStep[];
    selectedSurfaces: ProductSurfaceSelection[];
    phaseMode: ProductLifecyclePlan['phaseMode'];
  }> {
    const resolvedEnv =
      environment ??
      ((config.phases['deploy'] as (LifecyclePhaseConfiguration & { defaultEnvironment?: string }) | undefined)
        ?.defaultEnvironment ??
        'local');

    const deploymentConfig = config.deployment?.[resolvedEnv];
    if (!deploymentConfig) {
      throw new Error(
        `Product "${productId}" does not have a deployment config for environment "${resolvedEnv}". ` +
          `Declare deployment.${resolvedEnv} in kernel-product.yaml.`,
      );
    }

    const adapterId = deploymentConfig.adapter ?? 'compose-local';
    this.validateAdapterForPhase(adapterId, phase, toolchains, config, shapeOnly);
    const verifyConfig = config.verify?.[resolvedEnv];

    const stepKind: LifecycleStepKind =
      phase === 'deploy' ? 'deploy' : phase === 'verify' ? 'verify' : phase === 'promote' ? 'promotion' : 'rollback';

    const stepId = `${phase}-${resolvedEnv}-0`;
    const step: ProductLifecycleStep = {
      id: stepId,
      stepKind,
      phase,
      surface: resolvedEnv,
      adapter: adapterId,
      adapterSelectionSource: 'product-config-override',
      description: `${phase} to ${resolvedEnv} via ${adapterId}`,
      dependsOn: [],
      estimatedDurationMs: phase === 'verify' ? 60_000 : 90_000,
      adapterContext: {
        deploymentConfig: { ...deploymentConfig } as Record<string, unknown>,
        environmentConfig: { environment: resolvedEnv } as Record<string, unknown>,
        ...(verifyConfig
          ? { surfaceConfig: { ...verifyConfig } as Record<string, unknown> }
          : {}),
      },
    };

    const selectedSurfaces: ProductSurfaceSelection[] = [
      {
        surface: resolvedEnv,
        type: 'backend-api',
        adapter: adapterId,
        config: { ...deploymentConfig } as Record<string, unknown>,
      },
    ];

    return { steps: [step], selectedSurfaces, phaseMode: 'sequential' };
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Gate + artifact resolution
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private resolveGates(
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    profile: Record<string, unknown>,
  ): ProductGatePlan[] {
    const phaseGateIds: string[] = [
      ...this.getProfileRequiredGates(phase, profile),
      ...(config.gates?.[phase] ?? []),
    ];

    const seen = new Set<string>();
    const gates: ProductGatePlan[] = [];
    for (const gateId of phaseGateIds) {
      if (!seen.has(gateId)) {
        seen.add(gateId);
        gates.push({
          gateId,
          gateName: gateId,
          required: true,
          phase,
          source: 'lifecycle-profile',
          status: 'pending',
        });
      }
    }
    return gates;
  }

  private getProfileRequiredGates(
    phase: ProductLifecyclePhase,
    profile: Record<string, unknown>,
  ): string[] {
    const requiredGates = profile.requiredGates as Record<string, string[]> | undefined;
    return requiredGates?.[phase] ?? [];
  }

  private resolveExpectedArtifacts(
    productId: string,
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    _profile: Record<string, unknown>,
    surfaces: ProductSurfaceSelection[],
  ): ProductExpectedArtifact[] {
    const configuredArtifacts = config.artifacts?.[phase];
    if (configuredArtifacts) {
      return Object.entries(configuredArtifacts).map(([surface, artConfig]) => {
        const type = String(((artConfig as unknown) as Record<string, unknown>).type ?? 'unknown');
        return {
          surface,
          type,
          required: Boolean(((artConfig as unknown) as Record<string, unknown>).required ?? true),
          ...this.resolveArtifactProviderTruth(productId, surface, phase, type),
        };
      });
    }

    return surfaces.flatMap((sel) =>
      this.defaultArtifactsForPhaseAndSurface(productId, phase, sel.surface, sel.type),
    );
  }

  private resolveRequiredManifests(
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
  ): ProductLifecycleManifestType[] {
    return [...(config.requiredManifests?.[phase] ?? [])];
  }

  private resolveRequiredPlugins(config: KernelProductConfiguration): ProductLifecycleRequiredPlugin[] {
    return Object.entries(config.plugins ?? {}).map(([pluginId, pluginConfig]) => ({
      pluginId,
      required: pluginConfig.required ?? true,
      ...(pluginConfig.providerId !== undefined ? { providerId: pluginConfig.providerId } : {}),
    }));
  }

  private resolveApprovalRequirements(
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    environment: string | undefined,
  ): ProductLifecycleApprovalRequirement[] {
    const declared = (config.approvals?.[phase] ?? []).map((requirement) =>
      this.normalizeApprovalRequirement(phase, requirement),
    );

    const environmentApproval = this.resolveEnvironmentApprovalRequirement(phase, config, environment);
    return environmentApproval ? [...declared, environmentApproval] : declared;
  }

  private normalizeApprovalRequirement(
    phase: ProductLifecyclePhase,
    requirement: ProductLifecycleApprovalRequirementConfig,
  ): ProductLifecycleApprovalRequirement {
    const action = requirement.action.trim();
    const approvalId = requirement.approvalId?.trim() || `${phase}-${action}`;
    return {
      approvalId,
      action,
      riskLevel: requirement.riskLevel ?? 'medium',
      required: requirement.required ?? true,
      ...(requirement.requiredApprovers !== undefined
        ? { requiredApprovers: requirement.requiredApprovers.map((approver) => approver.trim()).filter(Boolean) }
        : {}),
      source: requirement.source ?? 'kernel-product.approvals',
    };
  }

  private resolveEnvironmentApprovalRequirement(
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    environment: string | undefined,
  ): ProductLifecycleApprovalRequirement | undefined {
    if (!DEPLOYMENT_PHASES.has(phase)) {
      return undefined;
    }

    const resolvedEnvironment =
      environment ??
      ((config.phases['deploy'] as (LifecyclePhaseConfiguration & { defaultEnvironment?: string }) | undefined)
        ?.defaultEnvironment ??
        'local');
    const environmentConfig = config.environments?.[resolvedEnvironment];
    if (environmentConfig?.approvalRequired !== true) {
      return undefined;
    }

    return {
      approvalId: `${phase}-${resolvedEnvironment}-approval`,
      action: `${phase}:${resolvedEnvironment}`,
      riskLevel: phase === 'deploy' ? 'high' : 'medium',
      required: true,
      source: `kernel-product.environments.${resolvedEnvironment}`,
    };
  }

  private resolveHealthChecks(
    surfaces: ProductSurfaceSelection[],
  ): ProductLifecycleHealthCheck[] {
    const healthChecks: ProductLifecycleHealthCheck[] = [];
    for (const sel of surfaces) {
      const healthConfig = (sel.config as Record<string, unknown>).health;
      if (healthConfig === undefined || healthConfig === null || typeof healthConfig !== 'object') {
        continue;
      }
      const h = healthConfig as Record<string, unknown>;
      const type = (typeof h.type === 'string' ? h.type : 'none') as ProductLifecycleHealthCheck['type'];
      healthChecks.push({
        surface: sel.surface,
        type,
        ...(typeof h.livePath === 'string' ? { livePath: h.livePath } : {}),
        ...(typeof h.readyPath === 'string' ? { readyPath: h.readyPath } : {}),
        ...(typeof h.portVariable === 'string' ? { portVariable: h.portVariable } : {}),
        ...(typeof h.defaultPort === 'number' ? { defaultPort: h.defaultPort } : {}),
        ...(Array.isArray(h.command) ? { command: h.command as string[] } : {}),
      });
    }
    return healthChecks;
  }

  private resolveProviderRefs(
    productUnit: ProductUnit | null,
  ): Pick<ProductLifecyclePlan, 'providerRefs'> {
    const providerRefs: NonNullable<ProductLifecyclePlan['providerRefs']> = {
      ...(productUnit !== null ? { registryProviderId: productUnit.registryProviderRef.providerId } : {}),
      ...(productUnit !== null ? { sourceProviderId: productUnit.sourceProviderRef.providerId } : {}),
      ...(this.providerContext?.events !== undefined ? { eventProviderId: this.providerContext.events.providerId } : {}),
      ...(this.providerContext?.artifacts !== undefined ? { artifactProviderId: this.providerContext.artifacts.providerId } : {}),
      ...(this.providerContext?.health !== undefined ? { healthProviderId: this.providerContext.health.providerId } : {}),
      ...(this.providerContext?.approvals !== undefined ? { approvalProviderId: this.providerContext.approvals.providerId } : {}),
      ...(this.providerContext?.provenance !== undefined
        ? { provenanceProviderId: this.providerContext.provenance.providerId }
        : {}),
      ...(this.providerContext?.runtimeTruth !== undefined
        ? { runtimeTruthProviderId: this.providerContext.runtimeTruth.providerId }
        : {}),
    };

    return Object.keys(providerRefs).length > 0 ? { providerRefs } : {};
  }

  private resolveArtifactProviderTruth(
    productId: string,
    surface: string,
    phase: ProductLifecyclePhase,
    type: string,
  ): Pick<ProductExpectedArtifact, 'providerId' | 'semanticRef'> {
    const artifactProvider = this.providerContext?.artifacts;
    if (artifactProvider === undefined) {
      return {};
    }
    return {
      providerId: artifactProvider.providerId,
      semanticRef: `artifact://${productId}/${phase}/${surface}/${type}`,
    };
  }

  private defaultArtifactsForPhaseAndSurface(
    productId: string,
    phase: ProductLifecyclePhase,
    surface: string,
    surfaceType: string,
  ): ProductExpectedArtifact[] {
    if (phase === 'build') {
      if (surfaceType === 'backend-api' || surfaceType === 'worker') {
        return [
          {
            surface,
            type: 'jvm-service',
            required: true,
            ...this.resolveArtifactProviderTruth(productId, surface, phase, 'jvm-service'),
          },
        ];
      }
      if (surfaceType === 'web') {
        return [
          {
            surface,
            type: 'static-web-bundle',
            required: true,
            ...this.resolveArtifactProviderTruth(productId, surface, phase, 'static-web-bundle'),
          },
        ];
      }
    }
    if (phase === 'package') {
      return [
        {
          surface,
          type: 'container-image',
          required: true,
          ...this.resolveArtifactProviderTruth(productId, surface, phase, 'container-image'),
        },
      ];
    }
    if (phase === 'test') {
      return [
        {
          surface,
          type: 'test-report',
          required: false,
          ...this.resolveArtifactProviderTruth(productId, surface, phase, 'test-report'),
        },
      ];
    }
    return Array.from(NO_EXPECTED_ARTIFACTS);
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Validation helpers
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private validateSurfaceAdapter(
    surfaceName: string,
    surfaceConfig: ProductSurface,
    surfaceType: ProductSurface['type'],
    phase: ProductLifecyclePhase,
    toolchains: ToolchainRegistry,
    config: KernelProductConfiguration,
    shapeOnly: boolean,
  ): void {
    const adapterId = String(surfaceConfig.adapter ?? '');
    if (!adapterId) {
      throw new Error(`Surface "${surfaceName}" does not declare an adapter in kernel-product.yaml.`);
    }

    const adapter = this.validateAdapterForPhase(adapterId, phase, toolchains, config, shapeOnly, surfaceName);
    const supportedSurfaceTypes = (adapter.supportedSurfaceTypes ?? []) as string[];

    if (supportedSurfaceTypes.length > 0 && !supportedSurfaceTypes.includes(surfaceType)) {
      throw new Error(
        `Adapter "${adapterId}" does not support surface type "${surfaceType}" for surface "${surfaceName}". ` +
          `Supported surface types: ${supportedSurfaceTypes.join(', ')}`,
      );
    }
  }

  private validateAdapterExists(adapterId: string, toolchains: ToolchainRegistry): void {
    if (!toolchains[adapterId]) {
      const available = Object.keys(toolchains).join(', ');
      throw new Error(`Unknown adapter "${adapterId}". Available adapters: ${available}`);
    }
  }

  private validateAdapterForPhase(
    adapterId: string,
    phase: ProductLifecyclePhase,
    toolchains: ToolchainRegistry,
    config: KernelProductConfiguration,
    shapeOnly: boolean,
    surfaceName?: string,
  ): Record<string, unknown> {
    this.validateAdapterExists(adapterId, toolchains);
    const adapter = toolchains[adapterId];
    const supportedPhases = (adapter.supportedPhases ?? []) as string[];
    if (!supportedPhases.includes(phase)) {
      const surfaceContext = surfaceName === undefined ? '' : ` for surface "${surfaceName}"`;
      throw new Error(
        `Adapter "${adapterId}" does not support phase "${phase}"${surfaceContext}. ` +
          `Supported phases: ${supportedPhases.join(', ')}`,
      );
    }

    const status = String(adapter.status ?? 'unknown');
    if (status !== 'implemented' && !shapeOnly) {
      throw new Error(
        `Adapter "${adapterId}" has status "${status}" in toolchain-adapter-registry.json and cannot be used for executable planning. ` +
          'Use shapeOnly planning to inspect planned or partial adapters without executing them.',
      );
    }

    const readiness = typeof adapter.readiness === 'string' ? adapter.readiness : undefined;
    if (
      readiness !== undefined &&
      readiness !== 'execution-ready' &&
      readiness !== 'production-ready' &&
      !shapeOnly
    ) {
      throw new Error(
        `Adapter "${adapterId}" has readiness "${readiness}" and cannot be used for executable planning. ` +
          'Use shapeOnly planning to inspect planning-only or declared adapters without executing them.',
      );
    }

    if (adapter.lifecycleEnabled === false && !shapeOnly) {
      throw new Error(
        `Adapter "${adapterId}" is not lifecycleEnabled in toolchain-adapter-registry.json and cannot be used for executable planning. ` +
          'Enable lifecycle only after adapter contract and readiness checks pass.',
      );
    }

    if (!config.allowExperimentalAdapters && !Boolean(adapter.safeForDefault)) {
      throw new Error(
        `Adapter "${adapterId}" is not marked safeForDefault in toolchain-adapter-registry.json. ` +
          'Set allowExperimentalAdapters: true in kernel-product.yaml only after an explicit safety review.',
      );
    }

    return adapter;
  }

  private assertProviderModeAvailable(
    providerMode: KernelProviderMode,
    allowBootstrapFallback: boolean = false,
    correlationId?: string,
  ): KernelProviderMode {
    // In bootstrap mode, continue with file-based validation even if providers are missing
    if (providerMode === 'bootstrap') {
      return 'bootstrap';
    }

    // In platform mode, check for required providers
    if (providerMode === 'platform') {
      if (this.providerContext === undefined) {
        if (allowBootstrapFallback) {
          this.logger.warn('Platform provider context unavailable; falling back to bootstrap mode', {
            reasonCode: 'platform-provider-context-missing',
            correlationId,
            allowBootstrapFallback,
          });
          return 'bootstrap';
        }
        throw new Error('Kernel platform provider mode requires a provider context. Set allowBootstrapFallback: true to fall back to bootstrap mode.');
      }

      if (this.providerContext.mode !== 'platform') {
        if (allowBootstrapFallback) {
          this.logger.warn('Platform mode requested with non-platform provider context; using bootstrap fallback', {
            reasonCode: 'platform-provider-context-mode-mismatch',
            correlationId,
            providerContextMode: this.providerContext.mode,
            allowBootstrapFallback,
          });
          return 'bootstrap';
        }
        throw new Error(
          `Kernel platform provider mode requires a platform provider context; received ${this.providerContext.mode}. Set allowBootstrapFallback: true to fall back to bootstrap mode.`,
        );
      }

      // Check for required providers in platform mode
      const missingProviders: string[] = [];
      if (this.providerContext.registryProvider === undefined) {
        missingProviders.push('registryProvider');
      }
      if (this.providerContext.sourceProvider === undefined) {
        missingProviders.push('sourceProvider');
      }
      if (this.providerContext.artifacts === undefined) {
        missingProviders.push('artifacts');
      }
      if (this.providerContext.events === undefined) {
        missingProviders.push('events');
      }
      if (this.providerContext.health === undefined) {
        missingProviders.push('health');
      }
      if (this.providerContext.approvals === undefined) {
        missingProviders.push('approvals');
      }
      if (this.providerContext.provenance === undefined) {
        missingProviders.push('provenance');
      }
      if (this.providerContext.runtimeTruth === undefined) {
        missingProviders.push('runtimeTruth');
      }

      if (missingProviders.length > 0) {
        if (allowBootstrapFallback) {
          this.logger.warn('Missing required platform providers; using bootstrap fallback', {
            reasonCode: 'platform-required-providers-missing',
            correlationId,
            missingProviders,
            allowBootstrapFallback,
          });
          return 'bootstrap';
        }
        throw new Error(
          `Kernel platform provider mode requires all providers to be available. Missing: ${missingProviders.join(', ')}. Set allowBootstrapFallback: true to fall back to bootstrap mode.`,
        );
      }

      return 'platform';
    }

    throw new Error(`Invalid provider mode: ${providerMode}`);
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Loaders
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private async loadRegistry(): Promise<Record<string, Record<string, unknown>>> {
    const raw = JSON.parse(await fs.readFile(this.registryPath, 'utf-8')) as unknown;
    this.assertObject(raw, this.registryPath, 'registry root');
    this.assertRecordProperty(raw, 'registry', this.registryPath);
    return raw.registry as Record<string, Record<string, unknown>>;
  }

  private async loadToolchains(): Promise<ToolchainRegistry> {
    const raw = JSON.parse(await fs.readFile(this.toolchainPath, 'utf-8')) as unknown;
    this.assertObject(raw, this.toolchainPath, 'toolchain registry root');
    this.assertRecordProperty(raw, 'adapters', this.toolchainPath);
    const adapters = raw.adapters as ToolchainRegistry;
    this.assertToolchainRegistryShape(adapters, this.toolchainPath);
    return adapters;
  }

  private async loadExclusions(): Promise<Set<string>> {
    try {
      const raw = JSON.parse(await fs.readFile(this.exclusionsPath, 'utf-8'));
      return new Set(Object.keys((raw.excludedProducts as Record<string, unknown>) ?? {}));
    } catch {
      return new Set();
    }
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Helpers
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private resolvePhaseConfig(
    phase: ProductLifecyclePhase,
    config: KernelProductConfiguration,
    profile: Record<string, unknown>,
  ): LifecyclePhaseConfiguration {
    if (config.phases[phase]) {
      return config.phases[phase];
    }

    const profileDefaultSurfaces = (
      profile.defaultSurfaces as Record<string, string[]> | undefined
    )?.[phase];
    if (!profileDefaultSurfaces) {
      throw new Error(
        `Phase "${phase}" is not defined in product phases or lifecycle profile "${config.lifecycleProfile}".`,
      );
    }

    return { defaultSurfaces: profileDefaultSurfaces, mode: 'sequential' };
  }

  private resolveSurfaceType(surfaceName: string, surfaceConfig: ProductSurface): ProductSurface['type'] {
    if (surfaceConfig.type !== undefined) {
      return surfaceConfig.type;
    }
    const runtime = typeof surfaceConfig.runtime === 'string' ? surfaceConfig.runtime : '';
    if (runtime === 'java-service') {
      return 'backend-api';
    }
    if (runtime === 'static-web') {
      return 'web';
    }
    if (surfaceName === 'web') {
      return 'web';
    }
    if (surfaceName.includes('worker')) {
      return 'worker';
    }
    return 'backend-api';
  }

  private parseYamlObject(content: string, filePath: string): Record<string, unknown> {
    try {
      const parsed = yaml.parse(content) as unknown;
      this.assertObject(parsed, filePath, 'kernel product config');
      return parsed;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Invalid YAML in ${filePath}: ${message}`);
    }
  }

  private assertKernelProductConfigShape(
    config: Record<string, unknown>,
    productId: string,
    configPath: string,
  ): void {
    if (typeof config.lifecycleProfile !== 'string' || config.lifecycleProfile.trim().length === 0) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare a non-empty lifecycleProfile (${configPath}).`,
      );
    }
    this.assertRecordProperty(config, 'surfaces', configPath);
    this.assertRecordProperty(config, 'phases', configPath);
    this.assertSurfacesConfigShape(config.surfaces as Record<string, unknown>, configPath, productId);
    this.assertPhasesConfigShape(config.phases as Record<string, unknown>, configPath, productId);
    this.assertRequiredManifestsConfigShape(config, configPath, productId);
    this.assertPluginsConfigShape(config, configPath, productId);
    this.assertEnvironmentsConfigShape(config, configPath, productId);
    this.assertApprovalsConfigShape(config, configPath, productId);
    this.assertPackageConfigShape(config, configPath, productId);
    this.assertDeploymentConfigShape(config, configPath, productId);
    this.assertVerifyConfigShape(config, configPath, productId);
    this.assertGatesConfigShape(config, configPath, productId);
    this.assertArtifactsConfigShape(config, configPath, productId);
  }

  private assertRequiredManifestsConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const requiredManifests = config.requiredManifests;
    if (requiredManifests === undefined) {
      return;
    }
    if (typeof requiredManifests !== 'object' || requiredManifests === null || Array.isArray(requiredManifests)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare requiredManifests as an object (${configPath}).`,
      );
    }

    const allowedManifestTypes = new Set<ProductLifecycleManifestType>([
      'lifecycle-plan',
      'lifecycle-result',
      'gate-result-manifest',
      'artifact-manifest',
      'deployment-manifest',
      'rollback-manifest',
      'verify-health-report',
      'lifecycle-health-snapshot',
      'lifecycle-events',
    ]);

    for (const [phaseName, manifests] of Object.entries(requiredManifests as Record<string, unknown>)) {
      if (
        !Array.isArray(manifests) ||
        manifests.some((manifest) => typeof manifest !== 'string' || !allowedManifestTypes.has(manifest as ProductLifecycleManifestType))
      ) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare requiredManifests.${phaseName} as an array of valid manifest types (${configPath}).`,
        );
      }
    }
  }

  private assertPluginsConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const plugins = config.plugins;
    if (plugins === undefined) {
      return;
    }
    if (typeof plugins !== 'object' || plugins === null || Array.isArray(plugins)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare plugins as an object (${configPath}).`,
      );
    }

    for (const [pluginId, pluginValue] of Object.entries(plugins as Record<string, unknown>)) {
      if (typeof pluginValue !== 'object' || pluginValue === null || Array.isArray(pluginValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare plugins.${pluginId} as an object (${configPath}).`,
        );
      }

      const pluginConfig = pluginValue as Record<string, unknown>;
      this.assertOptionalBooleanProperty(pluginConfig, 'required', `plugins.${pluginId}`, configPath, productId);
      this.assertOptionalNonEmptyStringProperty(pluginConfig, 'providerId', `plugins.${pluginId}`, configPath, productId);
    }
  }

  private assertEnvironmentsConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const environments = config.environments;
    if (environments === undefined) {
      return;
    }
    if (typeof environments !== 'object' || environments === null || Array.isArray(environments)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare environments as an object (${configPath}).`,
      );
    }

    for (const [environmentName, environmentValue] of Object.entries(environments as Record<string, unknown>)) {
      if (typeof environmentValue !== 'object' || environmentValue === null || Array.isArray(environmentValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare environments.${environmentName} as an object (${configPath}).`,
        );
      }

      const environmentConfig = environmentValue as Record<string, unknown>;
      this.assertOptionalBooleanProperty(
        environmentConfig,
        'approvalRequired',
        `environments.${environmentName}`,
        configPath,
        productId,
      );
      this.assertOptionalBooleanProperty(
        environmentConfig,
        'safeForDefault',
        `environments.${environmentName}`,
        configPath,
        productId,
      );
      this.assertOptionalStringArrayProperty(
        environmentConfig,
        'requiredGates',
        `environments.${environmentName}`,
        configPath,
        productId,
      );
      this.assertOptionalNonEmptyStringProperty(environmentConfig, 'type', `environments.${environmentName}`, configPath, productId);
      this.assertOptionalNonEmptyStringProperty(
        environmentConfig,
        'deploymentProvider',
        `environments.${environmentName}`,
        configPath,
        productId,
      );
      this.assertOptionalNonEmptyStringProperty(
        environmentConfig,
        'secretsProvider',
        `environments.${environmentName}`,
        configPath,
        productId,
      );
    }
  }

  private assertApprovalsConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const approvals = config.approvals;
    if (approvals === undefined) {
      return;
    }
    if (typeof approvals !== 'object' || approvals === null || Array.isArray(approvals)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare approvals as an object (${configPath}).`,
      );
    }

    const allowedRiskLevels = new Set(['low', 'medium', 'high', 'critical']);
    for (const [phaseName, approvalItems] of Object.entries(approvals as Record<string, unknown>)) {
      if (!Array.isArray(approvalItems)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare approvals.${phaseName} as an array (${configPath}).`,
        );
      }

      for (const approvalItem of approvalItems) {
        if (typeof approvalItem !== 'object' || approvalItem === null || Array.isArray(approvalItem)) {
          throw new Error(
            `kernel-product.yaml for product "${productId}" must declare approvals.${phaseName} entries as objects (${configPath}).`,
          );
        }

        const approvalConfig = approvalItem as Record<string, unknown>;
        this.assertRequiredNonEmptyStringProperty(approvalConfig, 'action', `approvals.${phaseName}`, configPath, productId);
        this.assertOptionalNonEmptyStringProperty(approvalConfig, 'approvalId', `approvals.${phaseName}`, configPath, productId);
        this.assertOptionalBooleanProperty(approvalConfig, 'required', `approvals.${phaseName}`, configPath, productId);
        this.assertOptionalStringArrayProperty(approvalConfig, 'requiredApprovers', `approvals.${phaseName}`, configPath, productId);
        this.assertOptionalNonEmptyStringProperty(approvalConfig, 'source', `approvals.${phaseName}`, configPath, productId);

        const riskLevel = approvalConfig.riskLevel;
        if (riskLevel !== undefined && (typeof riskLevel !== 'string' || !allowedRiskLevels.has(riskLevel))) {
          throw new Error(
            `kernel-product.yaml for product "${productId}" must declare approvals.${phaseName}.riskLevel as one of low|medium|high|critical (${configPath}).`,
          );
        }
      }
    }
  }

  private assertSurfacesConfigShape(
    surfaces: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    for (const [surfaceName, surfaceValue] of Object.entries(surfaces)) {
      if (typeof surfaceValue !== 'object' || surfaceValue === null || Array.isArray(surfaceValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare surfaces.${surfaceName} as an object (${configPath}).`,
        );
      }

      const surfaceConfig = surfaceValue as Record<string, unknown>;
      this.assertOptionalNonEmptyStringProperty(surfaceConfig, 'adapter', `surfaces.${surfaceName}`, configPath, productId);
      this.assertOptionalNonEmptyStringProperty(surfaceConfig, 'path', `surfaces.${surfaceName}`, configPath, productId);
      this.assertOptionalNonEmptyStringProperty(surfaceConfig, 'type', `surfaces.${surfaceName}`, configPath, productId);
      this.assertOptionalNonEmptyStringProperty(
        surfaceConfig,
        'implementationStatus',
        `surfaces.${surfaceName}`,
        configPath,
        productId,
      );
    }
  }

  private assertPhasesConfigShape(
    phases: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    for (const [phaseName, phaseValue] of Object.entries(phases)) {
      if (typeof phaseValue !== 'object' || phaseValue === null || Array.isArray(phaseValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare phases.${phaseName} as an object (${configPath}).`,
        );
      }

      const phaseConfig = phaseValue as Record<string, unknown>;
      const defaultSurfaces = phaseConfig.defaultSurfaces;
      if (
        defaultSurfaces !== undefined &&
        (!Array.isArray(defaultSurfaces) ||
          defaultSurfaces.some((surface) => typeof surface !== 'string' || surface.trim().length === 0))
      ) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare phases.${phaseName}.defaultSurfaces as a non-empty string array (${configPath}).`,
        );
      }

      const mode = phaseConfig.mode;
      if (mode !== undefined && mode !== 'sequential' && mode !== 'parallel' && mode !== 'dag') {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare phases.${phaseName}.mode as one of sequential|parallel|dag (${configPath}).`,
        );
      }
    }
  }

  private assertOptionalNonEmptyStringProperty(
    source: Record<string, unknown>,
    propertyName: string,
    contextPath: string,
    configPath: string,
    productId: string,
  ): void {
    const value = source[propertyName];
    if (value !== undefined && (typeof value !== 'string' || value.trim().length === 0)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare ${contextPath}.${propertyName} as a non-empty string (${configPath}).`,
      );
    }
  }

  private assertRequiredNonEmptyStringProperty(
    source: Record<string, unknown>,
    propertyName: string,
    contextPath: string,
    configPath: string,
    productId: string,
  ): void {
    const value = source[propertyName];
    if (typeof value !== 'string' || value.trim().length === 0) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare ${contextPath}.${propertyName} as a non-empty string (${configPath}).`,
      );
    }
  }

  private assertOptionalBooleanProperty(
    source: Record<string, unknown>,
    propertyName: string,
    contextPath: string,
    configPath: string,
    productId: string,
  ): void {
    const value = source[propertyName];
    if (value !== undefined && typeof value !== 'boolean') {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare ${contextPath}.${propertyName} as a boolean (${configPath}).`,
      );
    }
  }

  private assertOptionalStringArrayProperty(
    source: Record<string, unknown>,
    propertyName: string,
    contextPath: string,
    configPath: string,
    productId: string,
  ): void {
    const value = source[propertyName];
    if (
      value !== undefined &&
      (!Array.isArray(value) || value.some((item) => typeof item !== 'string' || item.trim().length === 0))
    ) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare ${contextPath}.${propertyName} as a non-empty string array (${configPath}).`,
      );
    }
  }

  private assertOptionalPositiveIntegerProperty(
    source: Record<string, unknown>,
    propertyName: string,
    contextPath: string,
    configPath: string,
    productId: string,
  ): void {
    const value = source[propertyName];
    if (value !== undefined && (typeof value !== 'number' || !Number.isInteger(value) || value <= 0)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare ${contextPath}.${propertyName} as a positive integer (${configPath}).`,
      );
    }
  }

  private assertOptionalPortProperty(
    source: Record<string, unknown>,
    propertyName: string,
    contextPath: string,
    configPath: string,
    productId: string,
  ): void {
    const value = source[propertyName];
    if (value !== undefined && (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > 65_535)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare ${contextPath}.${propertyName} as an integer between 1 and 65535 (${configPath}).`,
      );
    }
  }

  private assertPackageConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const packageConfig = config.package;
    if (packageConfig === undefined) {
      return;
    }
    if (typeof packageConfig !== 'object' || packageConfig === null || Array.isArray(packageConfig)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare package as an object (${configPath}).`,
      );
    }

    for (const [surfaceName, packageValue] of Object.entries(packageConfig as Record<string, unknown>)) {
      if (typeof packageValue !== 'object' || packageValue === null || Array.isArray(packageValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare package.${surfaceName} as an object (${configPath}).`,
        );
      }

      this.assertOptionalNonEmptyStringProperty(
        packageValue as Record<string, unknown>,
        'adapter',
        `package.${surfaceName}`,
        configPath,
        productId,
      );
    }
  }

  private assertDeploymentConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const deploymentConfig = config.deployment;
    if (deploymentConfig === undefined) {
      return;
    }
    if (typeof deploymentConfig !== 'object' || deploymentConfig === null || Array.isArray(deploymentConfig)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare deployment as an object (${configPath}).`,
      );
    }

    for (const [environmentName, environmentValue] of Object.entries(deploymentConfig as Record<string, unknown>)) {
      if (typeof environmentValue !== 'object' || environmentValue === null || Array.isArray(environmentValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare deployment.${environmentName} as an object (${configPath}).`,
        );
      }

      this.assertOptionalNonEmptyStringProperty(
        environmentValue as Record<string, unknown>,
        'adapter',
        `deployment.${environmentName}`,
        configPath,
        productId,
      );
    }
  }

  private assertVerifyConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const verifyConfig = config.verify;
    if (verifyConfig === undefined) {
      return;
    }
    if (typeof verifyConfig !== 'object' || verifyConfig === null || Array.isArray(verifyConfig)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare verify as an object (${configPath}).`,
      );
    }

    for (const [environmentName, verifyValue] of Object.entries(verifyConfig as Record<string, unknown>)) {
      if (typeof verifyValue !== 'object' || verifyValue === null || Array.isArray(verifyValue)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare verify.${environmentName} as an object (${configPath}).`,
        );
      }

      const verifyEnvironment = verifyValue as Record<string, unknown>;
      this.assertOptionalNonEmptyStringProperty(
        verifyEnvironment,
        'adapter',
        `verify.${environmentName}`,
        configPath,
        productId,
      );

      const healthChecks = verifyEnvironment.healthChecks;
      if (healthChecks === undefined) {
        continue;
      }
      if (typeof healthChecks !== 'object' || healthChecks === null || Array.isArray(healthChecks)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare verify.${environmentName}.healthChecks as an object (${configPath}).`,
        );
      }

      for (const [checkId, checkValue] of Object.entries(healthChecks as Record<string, unknown>)) {
        if (typeof checkValue !== 'object' || checkValue === null || Array.isArray(checkValue)) {
          throw new Error(
            `kernel-product.yaml for product "${productId}" must declare verify.${environmentName}.healthChecks.${checkId} as an object (${configPath}).`,
          );
        }

        const checkConfig = checkValue as Record<string, unknown>;
        const checkPath = `verify.${environmentName}.healthChecks.${checkId}`;
        const checkType = checkConfig.type;
        if (checkType !== 'http' && checkType !== 'tcp') {
          throw new Error(
            `kernel-product.yaml for product "${productId}" must declare ${checkPath}.type as one of http|tcp (${configPath}).`,
          );
        }

        this.assertOptionalNonEmptyStringProperty(checkConfig, 'url', checkPath, configPath, productId);
        this.assertOptionalNonEmptyStringProperty(checkConfig, 'host', checkPath, configPath, productId);
        this.assertOptionalNonEmptyStringProperty(checkConfig, 'path', checkPath, configPath, productId);
        this.assertOptionalPositiveIntegerProperty(checkConfig, 'retries', checkPath, configPath, productId);
        this.assertOptionalPositiveIntegerProperty(checkConfig, 'intervalMs', checkPath, configPath, productId);
        this.assertOptionalPositiveIntegerProperty(checkConfig, 'timeoutMs', checkPath, configPath, productId);
        this.assertOptionalPortProperty(checkConfig, 'port', checkPath, configPath, productId);

        if (checkType === 'http') {
          const hasUrl = typeof checkConfig.url === 'string' && checkConfig.url.trim().length > 0;
          const hasHost = typeof checkConfig.host === 'string' && checkConfig.host.trim().length > 0;
          if (!hasUrl && !hasHost) {
            throw new Error(
              `kernel-product.yaml for product "${productId}" must declare ${checkPath}.url or ${checkPath}.host for http checks (${configPath}).`,
            );
          }
        }

        if (checkType === 'tcp') {
          const hasHost = typeof checkConfig.host === 'string' && checkConfig.host.trim().length > 0;
          if (!hasHost) {
            throw new Error(
              `kernel-product.yaml for product "${productId}" must declare ${checkPath}.host as a non-empty string for tcp checks (${configPath}).`,
            );
          }

          const port = checkConfig.port;
          if (typeof port !== 'number' || !Number.isInteger(port) || port < 1 || port > 65_535) {
            throw new Error(
              `kernel-product.yaml for product "${productId}" must declare ${checkPath}.port as an integer between 1 and 65535 for tcp checks (${configPath}).`,
            );
          }
        }
      }
    }
  }

  private assertGatesConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const gates = config.gates;
    if (gates === undefined) {
      return;
    }
    if (typeof gates !== 'object' || gates === null || Array.isArray(gates)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare gates as an object (${configPath}).`,
      );
    }

    for (const [phaseName, gateIds] of Object.entries(gates as Record<string, unknown>)) {
      if (!Array.isArray(gateIds) || gateIds.some((gateId) => typeof gateId !== 'string' || gateId.trim().length === 0)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare gates.${phaseName} as a non-empty string array (${configPath}).`,
        );
      }
    }
  }

  private assertArtifactsConfigShape(
    config: Record<string, unknown>,
    configPath: string,
    productId: string,
  ): void {
    const artifacts = config.artifacts;
    if (artifacts === undefined) {
      return;
    }
    if (typeof artifacts !== 'object' || artifacts === null || Array.isArray(artifacts)) {
      throw new Error(
        `kernel-product.yaml for product "${productId}" must declare artifacts as an object (${configPath}).`,
      );
    }

    for (const [phaseName, phaseArtifacts] of Object.entries(artifacts as Record<string, unknown>)) {
      if (typeof phaseArtifacts !== 'object' || phaseArtifacts === null || Array.isArray(phaseArtifacts)) {
        throw new Error(
          `kernel-product.yaml for product "${productId}" must declare artifacts.${phaseName} as an object (${configPath}).`,
        );
      }

      for (const [surfaceName, artifactValue] of Object.entries(phaseArtifacts as Record<string, unknown>)) {
        if (typeof artifactValue !== 'object' || artifactValue === null || Array.isArray(artifactValue)) {
          throw new Error(
            `kernel-product.yaml for product "${productId}" must declare artifacts.${phaseName}.${surfaceName} as an object (${configPath}).`,
          );
        }

        const artifactConfig = artifactValue as Record<string, unknown>;
        this.assertRequiredNonEmptyStringProperty(
          artifactConfig,
          'type',
          `artifacts.${phaseName}.${surfaceName}`,
          configPath,
          productId,
        );
        this.assertOptionalNonEmptyStringProperty(
          artifactConfig,
          'packaging',
          `artifacts.${phaseName}.${surfaceName}`,
          configPath,
          productId,
        );
        this.assertOptionalBooleanProperty(
          artifactConfig,
          'required',
          `artifacts.${phaseName}.${surfaceName}`,
          configPath,
          productId,
        );
        this.assertOptionalStringArrayProperty(
          artifactConfig,
          'paths',
          `artifacts.${phaseName}.${surfaceName}`,
          configPath,
          productId,
        );
      }
    }
  }

  private assertRecordProperty(
    value: Record<string, unknown>,
    propertyName: string,
    filePath: string,
  ): void {
    const property = value[propertyName];
    if (typeof property !== 'object' || property === null || Array.isArray(property)) {
      throw new Error(`Expected object property "${propertyName}" in ${filePath}.`);
    }
  }

  private assertObject(value: unknown, filePath: string, context: string): asserts value is Record<string, unknown> {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
      throw new Error(`Expected ${context} object in ${filePath}.`);
    }
  }

  private assertLifecycleProfileShape(
    profile: Record<string, unknown>,
    profileId: string,
    filePath: string,
  ): void {
    const defaultSurfaces = profile.defaultSurfaces;
    if (defaultSurfaces === undefined) {
      return;
    }
    if (typeof defaultSurfaces !== 'object' || defaultSurfaces === null || Array.isArray(defaultSurfaces)) {
      throw new Error(
        `Lifecycle profile "${profileId}" must declare defaultSurfaces as an object in ${filePath}.`,
      );
    }

    for (const [phase, surfaces] of Object.entries(defaultSurfaces)) {
      if (!Array.isArray(surfaces) || surfaces.some((surface) => typeof surface !== 'string' || surface.trim().length === 0)) {
        throw new Error(
          `Lifecycle profile "${profileId}" must declare defaultSurfaces.${phase} as a non-empty string array in ${filePath}.`,
        );
      }
    }

    const requiredGates = profile.requiredGates;
    if (requiredGates !== undefined) {
      if (typeof requiredGates !== 'object' || requiredGates === null || Array.isArray(requiredGates)) {
        throw new Error(
          `Lifecycle profile "${profileId}" must declare requiredGates as an object in ${filePath}.`,
        );
      }
      for (const [phase, gates] of Object.entries(requiredGates)) {
        if (!Array.isArray(gates) || gates.some((gate) => typeof gate !== 'string' || gate.trim().length === 0)) {
          throw new Error(
            `Lifecycle profile "${profileId}" must declare requiredGates.${phase} as a non-empty string array in ${filePath}.`,
          );
        }
      }
    }

    const defaultAdapters = profile.defaultAdapters;
    if (defaultAdapters !== undefined) {
      if (typeof defaultAdapters !== 'object' || defaultAdapters === null || Array.isArray(defaultAdapters)) {
        throw new Error(
          `Lifecycle profile "${profileId}" must declare defaultAdapters as an object in ${filePath}.`,
        );
      }
      for (const [adapterKey, adapterValue] of Object.entries(defaultAdapters)) {
        if (typeof adapterValue !== 'string' || adapterValue.trim().length === 0) {
          throw new Error(
            `Lifecycle profile "${profileId}" must declare defaultAdapters.${adapterKey} as a non-empty string in ${filePath}.`,
          );
        }
      }
    }
  }

  private assertToolchainRegistryShape(adapters: ToolchainRegistry, filePath: string): void {
    const allowedReadiness = new Set(['execution-ready', 'production-ready', 'planning-only', 'declared-only']);
    for (const [adapterId, adapterValue] of Object.entries(adapters)) {
      if (typeof adapterValue !== 'object' || adapterValue === null || Array.isArray(adapterValue)) {
        throw new Error(`Adapter "${adapterId}" in ${filePath} must be an object.`);
      }

      const adapter = adapterValue as Record<string, unknown>;
      const supportedPhases = adapter.supportedPhases;
      if (!Array.isArray(supportedPhases) || supportedPhases.some((phase) => typeof phase !== 'string' || phase.trim().length === 0)) {
        throw new Error(
          `Adapter "${adapterId}" in ${filePath} must declare supportedPhases as a non-empty string array.`,
        );
      }

      const supportedSurfaceTypes = adapter.supportedSurfaceTypes;
      if (!Array.isArray(supportedSurfaceTypes) || supportedSurfaceTypes.some((surfaceType) => typeof surfaceType !== 'string' || surfaceType.trim().length === 0)) {
        throw new Error(
          `Adapter "${adapterId}" in ${filePath} must declare supportedSurfaceTypes as a non-empty string array.`,
        );
      }

      if (typeof adapter.status !== 'string' || adapter.status.trim().length === 0) {
        throw new Error(
          `Adapter "${adapterId}" in ${filePath} must declare status as a non-empty string.`,
        );
      }

      if (typeof adapter.safeForDefault !== 'boolean') {
        throw new Error(
          `Adapter "${adapterId}" in ${filePath} must declare safeForDefault as a boolean.`,
        );
      }

      if (typeof adapter.lifecycleEnabled !== 'boolean') {
        throw new Error(
          `Adapter "${adapterId}" in ${filePath} must declare lifecycleEnabled as a boolean.`,
        );
      }

      if (adapter.readiness !== undefined) {
        if (typeof adapter.readiness !== 'string' || !allowedReadiness.has(adapter.readiness)) {
          throw new Error(
            `Adapter "${adapterId}" in ${filePath} must declare readiness as one of execution-ready|production-ready|planning-only|declared-only.`,
          );
        }
      }
    }
  }

  private resolveOutputDirectory(
    productId: string,
    phase: ProductLifecyclePhase,
    runId: string,
    outputDir?: string,
  ): string {
    if (outputDir && outputDir.length > 0) {
      return path.isAbsolute(outputDir) ? outputDir : path.resolve(this.repoRoot, outputDir);
    }
    return path.join(this.repoRoot, '.kernel', 'out', 'products', productId, phase, runId);
  }

  private mapMode(mode: string): ProductLifecyclePlan['phaseMode'] {
    if (mode === 'parallel') return 'parallel';
    if (mode === 'dag') return 'dag';
    return 'sequential';
  }

  private generateRunId(): string {
    const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const rand = crypto.randomBytes(4).toString('hex');
    return `${ts}-${rand}`;
  }

  private estimateDuration(steps: ProductLifecycleStep[]): number {
    return steps.reduce((sum, step) => sum + step.estimatedDurationMs, 0);
  }

  private resolveProfileAdapter(
    profile: Record<string, unknown> | undefined,
    adapterKey: string,
  ): string | undefined {
    const defaultAdapters = profile?.defaultAdapters as Record<string, string> | undefined;
    return defaultAdapters?.[adapterKey];
  }
}
