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
} from '../domain/ProductLifecyclePhase.js';
import type {
  KernelLifecycleProviderContext,
  KernelProviderMode,
  ProductUnit,
  RegistryProvider,
  SourceProvider,
} from '@ghatana/kernel-product-contracts';

/** Phases that are handled by the deployment target adapter, NOT surface adapters. */
const DEPLOYMENT_PHASES = new Set<ProductLifecyclePhase>(['deploy', 'verify', 'rollback']);

/** Phases that use the package adapter from product package config. */
const PACKAGE_PHASES = new Set<ProductLifecyclePhase>(['package']);

/** Surface phases that delegate to the surface-level build adapter. */
const SURFACE_PHASES = new Set<ProductLifecyclePhase>(['dev', 'validate', 'test', 'build']);

/** Internal registry shape loaded from toolchain-adapter-registry.json */
type ToolchainRegistry = Record<string, Record<string, unknown>>;

export interface ProductLifecyclePlannerProviderContext extends KernelLifecycleProviderContext {
  readonly registryProvider?: RegistryProvider;
  readonly sourceProvider?: SourceProvider;
}

export interface ProductLifecyclePlanOptions {
  readonly surfaceSelector?: string[];
  readonly environment?: string;
  readonly sourceRef?: string;
  readonly outputDir?: string;
  readonly runId?: string;
  readonly correlationId?: string;
  readonly providerMode?: KernelProviderMode;
  readonly shapeOnly?: boolean;
}


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

  constructor(
    repoRoot: string = process.cwd(),
    configDir?: string,
    providerContext?: ProductLifecyclePlannerProviderContext,
  ) {
    this.repoRoot = repoRoot;
    const actualConfigDir = configDir ?? path.join(repoRoot, 'config');
    this.exclusionsPath = path.join(actualConfigDir, 'kernel-lifecycle-exclusions.json');
    this.registryPath = path.join(actualConfigDir, 'canonical-product-registry.json');
    this.profilesPath = path.join(actualConfigDir, 'product-lifecycle-profiles.json');
    this.toolchainPath = path.join(actualConfigDir, 'toolchain-adapter-registry.json');
    this.providerContext = providerContext;
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
        // Fall back to file-based approach if provider fails
        console.warn(`Provider failed to load ProductUnit for ${productId}, falling back to file-based validation:`, error);
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

    if (lifecycleStatus !== 'enabled' || lifecycle.enabled !== true) {
      throw new Error(
        `Product "${productId}" does not have lifecycle execution enabled. ` +
          `lifecycleStatus="${String(lifecycleStatus)}", lifecycle.enabled=${String(lifecycle.enabled)}. ` +
          'Set lifecycleStatus: "enabled" and lifecycle.enabled: true to proceed.',
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

    const config = yaml.parse(configContent) as Record<string, unknown>;

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
    const raw = JSON.parse(await fs.readFile(this.profilesPath, 'utf-8'));
    const profile = (raw.profiles as Record<string, unknown>)[profileId];
    if (!profile) {
      throw new Error(
        `Lifecycle profile "${profileId}" not found in product-lifecycle-profiles.json.`,
      );
    }
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
    const providerMode = options.providerMode ?? this.providerContext?.mode ?? 'bootstrap';
    this.assertProviderModeAvailable(providerMode);

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
          'Supported phases: dev, validate, test, build, package, deploy, verify, rollback.',
      );
    }

    const gates = this.resolveGates(phase, config, profile);
    const expectedArtifacts = this.resolveExpectedArtifacts(productId, phase, config, profile, selectedSurfaces);
    const requiredManifests = this.resolveRequiredManifests(phase, config);
    const requiredPlugins = this.resolveRequiredPlugins(config);
    const approvalRequirements = this.resolveApprovalRequirements(phase, config, options.environment);

    return {
      schemaVersion: '1.0.0',
      runId,
      correlationId,
      providerMode,
      productId,
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
      expectedArtifacts,
      requiredManifests,
      requiredPlugins,
      approvalRequirements,
      outputDirectory,
      estimatedDurationMs: this.estimateDuration(steps),
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
      phase === 'deploy' ? 'deploy' : phase === 'verify' ? 'verify' : 'rollback';

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
    return [];
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

    if (!config.allowExperimentalAdapters && !Boolean(adapter.safeForDefault)) {
      throw new Error(
        `Adapter "${adapterId}" is not marked safeForDefault in toolchain-adapter-registry.json. ` +
          'Set allowExperimentalAdapters: true in kernel-product.yaml only after an explicit safety review.',
      );
    }

    return adapter;
  }

  private assertProviderModeAvailable(providerMode: KernelProviderMode): void {
    if (providerMode === 'platform' && this.providerContext === undefined) {
      throw new Error('Kernel platform provider mode requires a provider context.');
    }
    if (providerMode === 'platform' && this.providerContext?.mode !== 'platform') {
      throw new Error(
        `Kernel platform provider mode requires a platform provider context; received ${this.providerContext?.mode ?? 'none'}.`,
      );
    }
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Loaders
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private async loadRegistry(): Promise<Record<string, Record<string, unknown>>> {
    const raw = JSON.parse(await fs.readFile(this.registryPath, 'utf-8'));
    return raw.registry as Record<string, Record<string, unknown>>;
  }

  private async loadToolchains(): Promise<ToolchainRegistry> {
    const raw = JSON.parse(await fs.readFile(this.toolchainPath, 'utf-8'));
    return raw.adapters as ToolchainRegistry;
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
