import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import {
  type ApprovalDecision,
  type ApprovalRequest,
  type KernelLifecycleProviderContext,
  type ProductUnit,
  type ProductUnitScope,
  type RegistryProvider,
  validateKernelLifecycleProviderContext,
} from '@ghatana/kernel-product-contracts';
import { createBootstrapKernelProviders, GhatanaFileRegistryProvider } from '@ghatana/kernel-providers';
import type {
  ProductLifecycleManifestType,
  ProductLifecyclePhase,
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../domain/ProductLifecyclePhase.js';
import type { ProductLifecycleExecutionOptions, ProductLifecycleExecutor } from '../execution/ProductLifecycleExecutor.js';
import { ProductLifecyclePlanner, type ProductLifecyclePlanOptions } from '../planning/ProductLifecyclePlanner.js';
import {
  ApprovalRequiredError,
  ExecutionFailedError,
  KernelLifecycleError,
  ProductUnitNotFoundError,
  ProviderUnavailableError,
} from './KernelLifecycleErrors.js';
import { ManifestPointerStore, type ManifestPointers } from './ManifestPointerStore.js';

export interface KernelLifecycleLogger {
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
}

export interface LifecycleRunSummary {
  readonly runId: string;
  readonly correlationId: string;
  readonly productUnitId: string;
  readonly phase?: ProductLifecyclePhase;
  readonly status: 'succeeded' | 'failed' | 'skipped' | 'planned' | 'unknown';
  readonly manifestRefs?: Record<string, string>;
  readonly failureReasonCode?: string;
  readonly approvalRefs?: readonly string[];
  readonly eventsRef?: string;
  readonly healthSnapshotRef?: string;
}

export interface KernelLifecycleServiceOptions {
  readonly repoRoot?: string;
  readonly outputRoot?: string;
  readonly planner?: ProductLifecyclePlanner;
  readonly executor?: ProductLifecycleExecutor;
  readonly registryProvider?: RegistryProvider;
  readonly providerContext?: KernelLifecycleProviderContext;
  readonly clock?: () => string;
  readonly logger?: KernelLifecycleLogger;
}

export interface KernelLifecycleScopeQuery {
  readonly scope?: ProductUnitScope;
  readonly correlationId?: string;
}

export interface CreateLifecyclePlanOptions extends KernelLifecycleScopeQuery {
  readonly surfaceSelector?: readonly string[];
  readonly environment?: string;
  readonly sourceRef?: string;
  readonly outputDir?: string;
  readonly providerMode?: KernelLifecycleProviderContext['mode'];
}

export interface ExecuteLifecyclePlanOptions extends KernelLifecycleScopeQuery {
  readonly dryRun: boolean;
  readonly environment?: string;
  readonly sourceRef?: string;
}

export interface RunLifecyclePhaseOptions extends CreateLifecyclePlanOptions {
  readonly dryRun: boolean;
}

export interface LifecycleRunQuery {
  readonly phase?: ProductLifecyclePhase;
  readonly correlationId?: string;
}

export interface ApprovalResult {
  readonly approvalId: string;
  readonly status: 'pending' | 'approved' | 'rejected' | 'failed';
  readonly ref?: string;
  readonly reasonCode?: string;
  readonly message?: string;
}

const fallbackLogger: KernelLifecycleLogger = {
  info: () => undefined,
  warn: () => undefined,
  error: () => undefined,
};

export class KernelLifecycleService {
  private readonly repoRoot: string;
  private readonly outputRoot: string;
  private readonly planner: ProductLifecyclePlanner;
  private readonly executor: ProductLifecycleExecutor | undefined;
  private readonly registryProvider: RegistryProvider;
  private readonly providerContext: KernelLifecycleProviderContext;
  private readonly pointerStore: ManifestPointerStore;
  private readonly clock: () => string;
  private readonly logger: KernelLifecycleLogger;

  constructor(options: KernelLifecycleServiceOptions = {}) {
    this.repoRoot = path.resolve(options.repoRoot ?? process.cwd());
    const bootstrap = options.providerContext === undefined
      ? createBootstrapKernelProviders({
          repoRoot: this.repoRoot,
          ...(options.outputRoot === undefined ? {} : { outputRoot: options.outputRoot }),
        })
      : undefined;
    this.providerContext = options.providerContext ?? bootstrap!.context;
    this.outputRoot = bootstrap?.outputRoot ?? path.resolve(
      this.repoRoot,
      options.outputRoot ?? path.join('.kernel', 'out'),
    );
    this.registryProvider = options.registryProvider ?? new GhatanaFileRegistryProvider({
      registryPath: path.join(this.repoRoot, 'config', 'canonical-product-registry.json'),
    });
    this.planner = options.planner ?? new ProductLifecyclePlanner(this.repoRoot, undefined, {
      ...this.providerContext,
      registryProvider: this.registryProvider,
    });
    this.executor = options.executor;
    this.pointerStore = new ManifestPointerStore({
      repoRoot: this.repoRoot,
      outputRoot: path.relative(this.repoRoot, this.outputRoot),
    });
    this.clock = options.clock ?? (() => new Date().toISOString());
    this.logger = options.logger ?? fallbackLogger;
  }

  async listProductUnits(query: KernelLifecycleScopeQuery = {}): Promise<readonly ProductUnit[]> {
    this.validateProviderContext(query.correlationId);
    const productUnits = await this.registryProvider.listProductUnits();
    const scope = query.scope;
    if (scope === undefined) {
      return productUnits;
    }
    return productUnits.filter((productUnit) => this.scopeMatches(productUnit.scope, scope));
  }

  async getProductUnit(
    productUnitId: string,
    query: KernelLifecycleScopeQuery = {},
  ): Promise<ProductUnit> {
    this.validateProviderContext(query.correlationId);
    const productUnit = await this.registryProvider.getProductUnit(productUnitId);
    if (productUnit === null) {
      throw new ProductUnitNotFoundError(productUnitId, query.correlationId);
    }
    if (query.scope !== undefined && !this.scopeMatches(productUnit.scope, query.scope)) {
      throw new ProductUnitNotFoundError(productUnitId, query.correlationId);
    }
    return productUnit;
  }

  async createLifecyclePlan(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options: CreateLifecyclePlanOptions = {},
  ): Promise<ProductLifecyclePlan> {
    this.validateProviderContext(options.correlationId);
    const plan = await this.planner.plan(productUnitId, phase, this.planOptions(options));
    await this.writeJson(path.join(plan.outputDirectory, 'lifecycle-plan.json'), this.safePlan(plan));
    await this.pointerStore.writeLatestPointers(plan.productId, plan.phase, {
      lifecyclePlan: path.join(plan.outputDirectory, 'lifecycle-plan.json'),
      runId: plan.runId,
      correlationId: plan.correlationId,
      providerMode: plan.providerMode,
    });
    await this.recordRuntimeTruth(plan, 'planned', [`lifecycle-plan:${plan.runId}`]);
    await this.recordProvenance(plan, [`lifecycle-plan:${plan.runId}`]);
    return plan;
  }

  async executeLifecyclePlan(
    plan: ProductLifecyclePlan,
    options: ExecuteLifecyclePlanOptions,
  ): Promise<ProductLifecycleResult> {
    this.validateProviderContext(plan.correlationId);
    if (this.executor === undefined) {
      throw new ProviderUnavailableError('KernelLifecycleService requires an executor to execute lifecycle plans', {
        correlationId: plan.correlationId,
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
      });
    }

    const executionOptions: ProductLifecycleExecutionOptions = {
      dryRun: options.dryRun,
      outputDirectory: plan.outputDirectory,
      providerContext: this.providerContext,
      ...(options.environment === undefined ? {} : { environment: options.environment }),
      ...(options.sourceRef === undefined ? {} : { sourceRef: options.sourceRef }),
    };
    const result = await this.executor.executePlan(plan, executionOptions);
    await this.writeJson(path.join(plan.outputDirectory, 'lifecycle-result.json'), this.safeResult(result));
    await this.writeLatestPointers(plan, result);
    await this.recordRuntimeTruth(
      plan,
      result.status === 'succeeded' ? 'succeeded' : 'failed',
      this.resultEvidenceRefs(result),
    );
    if (result.status === 'failed') {
      if (result.failure?.reasonCode === 'approval-required') {
        throw new ApprovalRequiredError(result.failure.message, {
          correlationId: plan.correlationId,
          productUnitId: plan.productId,
          runId: plan.runId,
          phase: plan.phase,
        });
      }
      this.logger.warn('Lifecycle execution failed', {
        productUnitId: plan.productId,
        runId: plan.runId,
        reasonCode: result.failure?.reasonCode,
      });
    }
    return result;
  }

  async runLifecyclePhase(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options: RunLifecyclePhaseOptions,
  ): Promise<ProductLifecycleResult> {
    const plan = await this.createLifecyclePlan(productUnitId, phase, options);
    return this.executeLifecyclePlan(plan, options);
  }

  async listLifecycleRuns(
    productUnitId: string,
    query: LifecycleRunQuery = {},
  ): Promise<readonly LifecycleRunSummary[]> {
    const phases = query.phase === undefined ? await this.listPhaseDirectories(productUnitId) : [query.phase];
    const summaries: LifecycleRunSummary[] = [];
    for (const phase of phases) {
      const runDirs = await this.pointerStore.listRunDirectories(productUnitId, phase);
      for (const runDir of runDirs) {
        const summary = await this.loadRunSummary(productUnitId, phase, path.basename(runDir));
        if (summary !== null && (query.correlationId === undefined || summary.correlationId === query.correlationId)) {
          summaries.push(summary);
        }
      }
    }
    return summaries.sort((left, right) => right.runId.localeCompare(left.runId));
  }

  async getLifecycleRun(
    productUnitId: string,
    runId: string,
  ): Promise<LifecycleRunSummary> {
    const phases = await this.listPhaseDirectories(productUnitId);
    for (const phase of phases) {
      const summary = await this.loadRunSummary(productUnitId, phase, runId);
      if (summary !== null) {
        return summary;
      }
    }
    throw new KernelLifecycleError({
      reasonCode: 'run-not-found',
      message: `Lifecycle run not found: ${productUnitId}/${runId}`,
      productUnitId,
      runId,
    });
  }

  async getManifest(
    productUnitId: string,
    runId: string,
    manifestType: ProductLifecycleManifestType,
    phase?: ProductLifecyclePhase,
  ): Promise<unknown> {
    const resolvedPhase = phase ?? (await this.getLifecycleRun(productUnitId, runId)).phase ?? 'build';
    const manifestPath = await this.pointerStore.resolveManifestByType(
      productUnitId,
      runId,
      resolvedPhase,
      manifestType,
    );
    return this.readJson(manifestPath);
  }

  async requestApproval(request: ApprovalRequest): Promise<ApprovalResult> {
    const provider = this.providerContext.approvals;
    if (provider === undefined) {
      throw new ProviderUnavailableError('Approval provider is not configured', {
        ...(request.correlationId === undefined ? {} : { correlationId: request.correlationId }),
        productUnitId: request.productUnitId,
        ...(request.runId === undefined ? {} : { runId: request.runId }),
      });
    }
    const result = await provider.requestLifecycleApproval(request, {
      required: true,
      correlationId: request.correlationId ?? request.approvalId,
    });
    return {
      approvalId: request.approvalId,
      status: result.success ? 'pending' : 'failed',
      ...(result.ref === undefined ? {} : { ref: result.ref }),
      ...(result.error === undefined ? {} : { message: result.error, reasonCode: 'provider-unavailable' }),
    };
  }

  async submitApprovalDecision(
    approvalId: string,
    decision: ApprovalDecision,
  ): Promise<ApprovalResult> {
    const provider = this.providerContext.approvals;
    if (provider === undefined) {
      throw new ProviderUnavailableError('Approval provider is not configured');
    }
    const result = await provider.decideLifecycleApproval(decision, {
      required: true,
      correlationId: approvalId,
    });
    return {
      approvalId,
      status: result.success ? (decision.approved ? 'approved' : 'rejected') : 'failed',
      ...(result.ref === undefined ? {} : { ref: result.ref }),
      ...(result.error === undefined ? {} : { message: result.error, reasonCode: 'provider-unavailable' }),
    };
  }

  normalizeError(error: unknown): KernelLifecycleError {
    if (error instanceof KernelLifecycleError) {
      return error;
    }
    return new ExecutionFailedError(error instanceof Error ? error.message : String(error));
  }

  private planOptions(options: CreateLifecyclePlanOptions): ProductLifecyclePlanOptions {
    return {
      ...(options.surfaceSelector === undefined ? {} : { surfaceSelector: [...options.surfaceSelector] }),
      ...(options.environment === undefined ? {} : { environment: options.environment }),
      ...(options.sourceRef === undefined ? {} : { sourceRef: options.sourceRef }),
      ...(options.outputDir === undefined ? {} : { outputDir: options.outputDir }),
      ...(options.correlationId === undefined ? {} : { correlationId: options.correlationId }),
      providerMode: options.providerMode ?? this.providerContext.mode,
    };
  }

  private validateProviderContext(correlationId?: string): void {
    const validation = validateKernelLifecycleProviderContext(this.providerContext);
    if (!validation.valid) {
      throw new ProviderUnavailableError(
        `Kernel ${this.providerContext.mode} mode is missing lifecycle providers: ${validation.missingProviders.join(', ')}`,
        {
          ...(correlationId === undefined ? {} : { correlationId }),
          safeDetails: { missingProviders: validation.missingProviders },
        },
      );
    }
  }

  private scopeMatches(actual: ProductUnitScope | undefined, expected: ProductUnitScope): boolean {
    if (actual === undefined) {
      return true;
    }
    return actual.tenantId === expected.tenantId &&
      actual.workspaceId === expected.workspaceId &&
      actual.projectId === expected.projectId;
  }

  private async writeLatestPointers(
    plan: ProductLifecyclePlan,
    result: ProductLifecycleResult,
  ): Promise<void> {
    const pointers: ManifestPointers = {
      lifecyclePlan: path.join(plan.outputDirectory, 'lifecycle-plan.json'),
      lifecycleResult: path.join(plan.outputDirectory, 'lifecycle-result.json'),
      ...(result.manifestRefs ?? {}),
      runId: plan.runId,
      correlationId: plan.correlationId,
      providerMode: plan.providerMode,
    };
    await this.pointerStore.writeLatestPointers(plan.productId, plan.phase, pointers);
  }

  private async listPhaseDirectories(productUnitId: string): Promise<readonly ProductLifecyclePhase[]> {
    const productRoot = path.join(this.outputRoot, 'products', productUnitId);
    try {
      const entries = await fs.readdir(productRoot, { withFileTypes: true });
      return entries
        .filter((entry) => entry.isDirectory())
        .map((entry) => entry.name as ProductLifecyclePhase);
    } catch {
      return [];
    }
  }

  private async loadRunSummary(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    runId: string,
  ): Promise<LifecycleRunSummary | null> {
    const resultPath = path.join(this.outputRoot, 'products', productUnitId, phase, runId, 'lifecycle-result.json');
    const planPath = path.join(this.outputRoot, 'products', productUnitId, phase, runId, 'lifecycle-plan.json');
    const result = await this.readJsonIfExists<ProductLifecycleResult>(resultPath);
    if (result !== null) {
      return {
        runId: result.runId,
        correlationId: result.correlationId ?? result.runId,
        productUnitId: result.productId,
        phase: result.phase,
        status: result.status,
        ...(result.manifestRefs === undefined ? {} : { manifestRefs: this.manifestRefsToRecord(result.manifestRefs) }),
        ...(result.failure?.reasonCode === undefined ? {} : { failureReasonCode: result.failure.reasonCode }),
        ...(result.approvalRefs === undefined ? {} : { approvalRefs: result.approvalRefs.map((approval) => approval.ref) }),
        ...(result.eventsRef === undefined ? {} : { eventsRef: result.eventsRef }),
        ...(result.healthSnapshotRef === undefined ? {} : { healthSnapshotRef: result.healthSnapshotRef }),
      };
    }
    const plan = await this.readJsonIfExists<ProductLifecyclePlan>(planPath);
    if (plan === null) {
      return null;
    }
    return {
      runId: plan.runId,
      correlationId: plan.correlationId,
      productUnitId: plan.productId,
      phase: plan.phase,
      status: 'planned',
      manifestRefs: { lifecyclePlan: planPath },
    };
  }

  private async recordRuntimeTruth(
    plan: ProductLifecyclePlan,
    status: string,
    evidenceRefs: readonly string[],
  ): Promise<void> {
    if (this.providerContext.runtimeTruth === undefined) {
      return;
    }
    const result = await this.providerContext.runtimeTruth.recordRuntimeTruth(
      {
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
        status,
        observedAt: this.clock(),
        evidenceRefs,
      },
      { required: true, correlationId: plan.correlationId },
    );
    if (!result.success) {
      throw new ProviderUnavailableError(result.error ?? 'Runtime truth provider write failed', {
        correlationId: plan.correlationId,
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
      });
    }
  }

  private async recordProvenance(
    plan: ProductLifecyclePlan,
    evidenceRefs: readonly string[],
  ): Promise<void> {
    if (this.providerContext.provenance === undefined) {
      return;
    }
    const result = await this.providerContext.provenance.recordProvenance(
      {
        provenanceId: `kernel-lifecycle:${plan.runId}`,
        productUnitId: plan.productId,
        runId: plan.runId,
        source: 'kernel-lifecycle-service',
        evidenceRefs,
        recordedAt: this.clock(),
      },
      { required: true, correlationId: plan.correlationId },
    );
    if (!result.success) {
      throw new ProviderUnavailableError(result.error ?? 'Provenance provider write failed', {
        correlationId: plan.correlationId,
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
      });
    }
  }

  private resultEvidenceRefs(result: ProductLifecycleResult): readonly string[] {
    return [
      ...(result.manifestRefs?.lifecycleResult === undefined ? [] : [result.manifestRefs.lifecycleResult]),
      ...(result.manifestRefs?.artifactManifest === undefined ? [] : [result.manifestRefs.artifactManifest]),
      ...(result.manifestRefs?.deploymentManifest === undefined ? [] : [result.manifestRefs.deploymentManifest]),
      ...(result.manifestRefs?.verifyHealthReport === undefined ? [] : [result.manifestRefs.verifyHealthReport]),
    ];
  }

  private safePlan(plan: ProductLifecyclePlan): ProductLifecyclePlan {
    return plan;
  }

  private safeResult(result: ProductLifecycleResult): ProductLifecycleResult {
    return result;
  }

  private manifestRefsToRecord(value: ProductLifecycleResult['manifestRefs']): Record<string, string> {
    if (value === undefined) {
      return {};
    }
    const entries: [string, string][] = [];
    if (value.lifecyclePlan !== undefined) entries.push(['lifecyclePlan', value.lifecyclePlan]);
    if (value.lifecycleResult !== undefined) entries.push(['lifecycleResult', value.lifecycleResult]);
    if (value.lifecycleEvents !== undefined) entries.push(['lifecycleEvents', value.lifecycleEvents]);
    if (value.gateResultManifest !== undefined) entries.push(['gateResultManifest', value.gateResultManifest]);
    if (value.artifactManifest !== undefined) entries.push(['artifactManifest', value.artifactManifest]);
    if (value.deploymentManifest !== undefined) entries.push(['deploymentManifest', value.deploymentManifest]);
    if (value.verifyHealthReport !== undefined) entries.push(['verifyHealthReport', value.verifyHealthReport]);
    if (value.lifecycleHealthSnapshot !== undefined) entries.push(['lifecycleHealthSnapshot', value.lifecycleHealthSnapshot]);
    return Object.fromEntries(entries);
  }

  private async writeJson(filePath: string, value: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(value, null, 2)}\n`, 'utf-8');
    await fs.rename(tempPath, filePath);
  }

  private async readJson(filePath: string): Promise<unknown> {
    return JSON.parse(await fs.readFile(filePath, 'utf-8')) as unknown;
  }

  private async readJsonIfExists<TValue>(filePath: string): Promise<TValue | null> {
    try {
      return JSON.parse(await fs.readFile(filePath, 'utf-8')) as TValue;
    } catch {
      return null;
    }
  }
}
