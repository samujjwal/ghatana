import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ToolchainAdapter, ToolchainAdapterContext, AdapterLogger } from './ToolchainAdapter.js';
import type { ProductLifecyclePhase } from '@ghatana/kernel-product-contracts';
import type { AdapterArtifact, AdapterResult } from '@ghatana/kernel-lifecycle';
import { SpawnCommandRunner } from './execution/SpawnCommandRunner.js';
import type { CommandRunner } from './execution/CommandRunner.js';
import { GradleJavaServiceAdapter } from './adapters/GradleJavaServiceAdapter.js';
import { PnpmViteReactAdapter } from './adapters/PnpmViteReactAdapter.js';
import { PnpmNodeApiAdapter } from './adapters/PnpmNodeApiAdapter.js';
import { CargoRustAdapter } from './adapters/CargoRustAdapter.js';
import { PythonPyprojectAdapter } from './adapters/PythonPyprojectAdapter.js';
import { DockerBuildxAdapter } from './adapters/DockerBuildxAdapter.js';
import { ComposeLocalAdapter } from './adapters/ComposeLocalAdapter.js';
import { ProductInteractionBrokerAdapter } from './adapters/ProductInteractionBrokerAdapter.js';

/**
 * Toolchain adapter registry.
 * Use {@link createDefaultToolchainAdapterRegistry} to obtain a pre-registered instance.
 *
 * @doc.type class
 * @doc.purpose Holds all registered ToolchainAdapter instances and provides lookup by adapter ID.
 * @doc.layer platform
 * @doc.pattern Registry
 */
export class ToolchainAdapterRegistry {
  private registryPath: string;
  private adapters: Map<string, ToolchainAdapter> = new Map();

  constructor(configDir: string = path.join(process.cwd(), 'config')) {
    this.registryPath = path.join(configDir, 'toolchain-adapter-registry.json');
  }

  /**
   * Load adapter definitions from registry
   */
  async loadRegistry(): Promise<Record<string, AdapterDefinition>> {
    const content = await fs.readFile(this.registryPath, 'utf-8');
    const registry = JSON.parse(content) as { adapters: Record<string, AdapterDefinition> };
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
    return this.getAll().filter((adapter) =>
      adapter.supportedPhases.includes(phase as ProductLifecyclePhase),
    );
  }

  /**
   * Get adapters that support a given surface type
   */
  getBySurfaceType(surfaceType: string): ToolchainAdapter[] {
    return this.getAll().filter((adapter) =>
      adapter.supportedSurfaceTypes.includes(surfaceType as ToolchainAdapterContext['surface']['type']),
    );
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
  readiness: 'declared-only' | 'planning-only' | 'execution-ready' | 'production-ready';
  requiresApprovalForProduction: boolean;
  supportsBootstrapMode: boolean;
  supportsPlatformMode: boolean;
  lifecycleEnabled: boolean;
  featureFlagRequired?: boolean;
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Bridge: ToolchainAdapter â†’ AdapterRegistry (StepRunner interface)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Minimal Adapter interface required by ProductLifecycleStepRunner.
 * Maps 1:1 to the interface in execution/ProductLifecycleStepRunner.ts.
 */
export interface StepRunnerAdapter {
  execute(context: StepRunnerAdapterContext): Promise<AdapterResult>;
}

/**
 * Context as expected by ProductLifecycleStepRunner.AdapterContext.
 * Kept in sync with the interface in kernel-lifecycle.
 */
export interface StepRunnerAdapterContext {
  productId: string;
  phase: string;
  surface: { type: string; adapter: string; path: string };
  environment: string | undefined;
  sourceRef: string | undefined;
  dryRun: boolean;
  surfaceConfig: Record<string, unknown>;
  phaseConfig: Record<string, unknown>;
  logger: AdapterLogger;
  metadata: Record<string, unknown> | undefined;
  outputDir: string | undefined;
}

/**
 * Bridges a {@link ToolchainAdapter} to the narrower {@link StepRunnerAdapter} interface
 * expected by {@link ProductLifecycleStepRunner}.
 */
class ToolchainAdapterBridge implements StepRunnerAdapter {
  constructor(private readonly delegate: ToolchainAdapter) {}

  async execute(ctx: StepRunnerAdapterContext): Promise<AdapterResult> {
    const toolchainCtx: ToolchainAdapterContext = {
      productId: ctx.productId,
      phase: ctx.phase as ProductLifecyclePhase,
      surface: {
        type: ctx.surface.type as ToolchainAdapterContext['surface']['type'],
        adapter: ctx.surface.adapter,
        path: ctx.surface.path,
      },
      ...this.resolveExecutionIds(ctx.metadata),
      ...(ctx.environment !== undefined ? { environment: ctx.environment } : {}),
      ...(ctx.sourceRef !== undefined ? { sourceRef: ctx.sourceRef } : {}),
      dryRun: ctx.dryRun,
      surfaceConfig: ctx.surfaceConfig,
      phaseConfig: ctx.phaseConfig,
      logger: ctx.logger,
      // Note: metadata is BuildMetadata in ToolchainAdapterContext - not propagated from StepRunner context.
      ...(ctx.outputDir !== undefined ? { outputDir: ctx.outputDir } : {}),
    };

    const result = await this.delegate.execute(toolchainCtx);
    return {
      status: result.status,
      steps: result.steps,
      artifacts: result.artifacts.map((artifactPath, index) =>
        this.toAdapterArtifact(ctx, artifactPath, index),
      ),
      ...(result.testResults !== undefined ? { testResults: result.testResults } : {}),
      ...(result.coverageResults !== undefined ? { coverageResults: result.coverageResults } : {}),
      durationMs: result.durationMs,
      ...(result.failure !== undefined ? { failure: result.failure } : {}),
      ...(result.warnings !== undefined ? { warnings: result.warnings } : {}),
      ...(result.stdout !== undefined ? { stdout: result.stdout } : {}),
      ...(result.stderr !== undefined ? { stderr: result.stderr } : {}),
      ...(result.manifestRefs !== undefined ? { manifestRefs: result.manifestRefs } : {}),
      ...(result.evidenceRefs !== undefined ? { evidenceRefs: result.evidenceRefs } : {}),
    };
  }

  private resolveExecutionIds(
    metadata: Record<string, unknown> | undefined,
  ): Pick<ToolchainAdapterContext, 'runId' | 'correlationId'> {
    const runId = metadata?.runId;
    const correlationId = metadata?.correlationId;
    return {
      ...(typeof runId === 'string' && runId.length > 0 ? { runId } : {}),
      ...(typeof correlationId === 'string' && correlationId.length > 0 ? { correlationId } : {}),
    };
  }

  private toAdapterArtifact(
    ctx: StepRunnerAdapterContext,
    artifactPath: string,
    index: number,
  ): AdapterArtifact {
    const artifactType = this.inferArtifactType(ctx, artifactPath);
    return {
      id: `${ctx.phase}-${ctx.surface.adapter}-artifact-${index}`,
      type: artifactType,
      path: artifactPath,
      fingerprint: artifactPath,
      producedBy: ctx.surface.adapter,
      metadata: {
        productId: ctx.productId,
        phase: ctx.phase,
        surfaceType: ctx.surface.type,
        ...(artifactType === 'container-image' ? { packaging: 'container' } : {}),
      },
      ...(artifactType === 'container-image' ? { image: artifactPath } : {}),
    };
  }

  private inferArtifactType(ctx: StepRunnerAdapterContext, artifactPath: string): string {
    if (ctx.phase === 'package') {
      return ctx.surface.type === 'web' && artifactPath.includes('/dist')
        ? 'static-web-bundle'
        : 'container-image';
    }
    if (ctx.phase === 'deploy') {
      return 'deployment-manifest';
    }
    if (ctx.phase === 'verify') {
      return 'verify-health-report';
    }
    if (ctx.phase === 'test') {
      return 'test-report';
    }
    return ctx.surface.type === 'web' ? 'static-web-bundle' : 'jvm-service';
  }
}

/**
 * StepRunner-compatible adapter registry backed by a {@link ToolchainAdapterRegistry}.
 *
 * @doc.type class
 * @doc.purpose Adapts ToolchainAdapterRegistry to the AdapterRegistry interface expected by ProductLifecycleStepRunner.
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export class ToolchainAdapterRegistryBridge {
  constructor(private readonly registry: ToolchainAdapterRegistry) {}

  getAdapter(adapterId: string): StepRunnerAdapter {
    const adapter = this.registry.get(adapterId);
    if (!adapter) {
      throw new Error(
        `No toolchain adapter registered for id "${adapterId}". ` +
          `Registered adapters: [${this.registry.getAll().map((a) => a.id).join(', ')}]`,
      );
    }
    return new ToolchainAdapterBridge(adapter);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Factory
// ─────────────────────────────────────────────────────────────────────────────

export interface DefaultToolchainAdapterRegistryOptions {
  repoRoot: string;
  commandRunner?: CommandRunner;
}

/**
 * Create a {@link ToolchainAdapterRegistry} pre-registered with all canonical production adapters.
 * Returns both the registry and a StepRunner-compatible bridge.
 *
 * @doc.type function
 * @doc.purpose Wires canonical toolchain adapters with SpawnCommandRunner for kernel lifecycle execution.
 * @doc.layer platform
 * @doc.pattern Factory
 */
export function createDefaultToolchainAdapterRegistry(
  options: DefaultToolchainAdapterRegistryOptions,
): { registry: ToolchainAdapterRegistry; bridge: ToolchainAdapterRegistryBridge } {
  const runner: CommandRunner = options.commandRunner ?? new SpawnCommandRunner();
  const adapterOptions = { repoRoot: options.repoRoot, commandRunner: runner };

  const registry = new ToolchainAdapterRegistry();
  registry.register(new GradleJavaServiceAdapter(adapterOptions));
  registry.register(new PnpmViteReactAdapter(adapterOptions));
  registry.register(new PnpmNodeApiAdapter(adapterOptions));
  registry.register(new CargoRustAdapter(adapterOptions));
  registry.register(new PythonPyprojectAdapter(adapterOptions));
  registry.register(new DockerBuildxAdapter(adapterOptions));
  registry.register(new ComposeLocalAdapter(adapterOptions));
  registry.register(new ProductInteractionBrokerAdapter());

  return { registry, bridge: new ToolchainAdapterRegistryBridge(registry) };
}
