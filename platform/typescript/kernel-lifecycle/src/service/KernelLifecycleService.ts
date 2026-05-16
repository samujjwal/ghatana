import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import {
  type ApprovalProvider,
  type ApprovalDecision,
  type ApprovalRequest,
  type KernelLifecycleEvent,
  type KernelLifecycleProviderContext,
  type ProductUnit,
  type ProductUnitScope,
  type RegistryProvider,
  type ProductUnitIntent,
  type ProductUnitIntentApplicationResult,
  type ProductUnitIntentCapableRegistryProvider,
  type ProductUnitIntentApplyOptions,
  validateProductUnitIntent,
  validateKernelLifecycleProviderContext,
  isProductUnitIntentCapableRegistryProvider,
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
  LifecycleManifestCorruptError,
  LifecycleRunIndexUnavailableError,
  ManifestNotFoundError,
  ProductUnitNotFoundError,
  ProviderUnavailableError,
} from './KernelLifecycleErrors.js';
import { ManifestPointerStore, type ManifestPointers } from './ManifestPointerStore.js';
import { ProductLifecycleNotReadyError } from '../planning/ProductLifecyclePlanner.js';

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
  readonly providerMode?: 'bootstrap' | 'platform';
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
  readonly providerMode?: 'bootstrap' | 'platform';
}

export interface ApprovalResult {
  readonly approvalId: string;
  readonly status: 'pending' | 'approved' | 'rejected' | 'failed';
  readonly ref?: string;
  readonly reasonCode?: string;
  readonly message?: string;
}

export interface PendingApprovalQuery extends KernelLifecycleScopeQuery {
  readonly productUnitId?: string;
  readonly runId?: string;
}

const structuredConsoleLogger: KernelLifecycleLogger = {
  info: (message: string, meta?: Record<string, unknown>): void => {
    console.info(JSON.stringify({ level: 'info', message, ...meta, ts: new Date().toISOString() }));
  },
  warn: (message: string, meta?: Record<string, unknown>): void => {
    console.warn(JSON.stringify({ level: 'warn', message, ...meta, ts: new Date().toISOString() }));
  },
  error: (message: string, meta?: Record<string, unknown>): void => {
    console.error(JSON.stringify({ level: 'error', message, ...meta, ts: new Date().toISOString() }));
  },
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
    this.registryProvider = options.registryProvider ?? (new GhatanaFileRegistryProvider({
      registryPath: path.join(this.repoRoot, 'config', 'canonical-product-registry.json'),
    }) as unknown as RegistryProvider);
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
    this.logger = options.logger ?? structuredConsoleLogger;
  }

  async listProductUnits(query: KernelLifecycleScopeQuery = {}): Promise<readonly ProductUnit[]> {
    this.validateProviderContext(query.correlationId, query.providerMode);
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
    this.validateProviderContext(query.correlationId, query.providerMode);
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
    this.validateProviderContext(options.correlationId, options.providerMode);
    this.logger.info('lifecycle.plan.creating', {
      productUnitId,
      phase,
      providerMode: options.providerMode ?? this.providerContext.mode,
      correlationId: options.correlationId,
    });
    let plan: ProductLifecyclePlan;
    try {
      plan = await this.planner.plan(productUnitId, phase, this.planOptions(options));
    } catch (error) {
      if (error instanceof ProductLifecycleNotReadyError) {
        throw new KernelLifecycleError({
          reasonCode: error.reasonCode,
          message: error.message,
          ...(options.correlationId === undefined ? {} : { correlationId: options.correlationId }),
          productUnitId,
          phase,
          safeDetails: error.toSafeDetails(),
          cause: error,
        });
      }
      throw error;
    }
    await this.writeJson(path.join(plan.outputDirectory, 'lifecycle-plan.json'), this.safePlan(plan));
    await this.pointerStore.writeLatestPointers(plan.productId, plan.phase, {
      lifecyclePlan: path.join(plan.outputDirectory, 'lifecycle-plan.json'),
      runId: plan.runId,
      correlationId: plan.correlationId,
      providerMode: plan.providerMode,
    });
    await this.recordRuntimeTruth(plan, 'planned', [`lifecycle-plan:${plan.runId}`]);
    await this.recordProvenance(plan, [`lifecycle-plan:${plan.runId}`]);

    // Append lifecycle.plan.created event (distinct from phase execution start)
    if (this.providerContext.events !== undefined) {
      await this.providerContext.events.appendEvent(
        this.createLifecyclePlanCreatedEvent(
          plan.productId,
          plan.runId,
          plan.phase,
          plan.correlationId,
          plan.providerMode,
          this.clock(),
        ),
        { required: false, correlationId: plan.correlationId },
      );
    }

    this.logger.info('lifecycle.plan.created', {
      productUnitId: plan.productId,
      runId: plan.runId,
      phase: plan.phase,
      providerMode: plan.providerMode,
      correlationId: plan.correlationId,
    });
    return plan;
  }

  async executeLifecyclePlan(
    plan: ProductLifecyclePlan,
    options: ExecuteLifecyclePlanOptions,
  ): Promise<ProductLifecycleResult> {
    this.validateProviderContext(plan.correlationId, options.providerMode ?? plan.providerMode);
    if (this.executor === undefined) {
      throw new ProviderUnavailableError('KernelLifecycleService requires an executor to execute lifecycle plans', {
        correlationId: plan.correlationId,
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
      });
    }

    // Append lifecycle event for execution start
    if (this.providerContext.events !== undefined) {
      await this.providerContext.events.appendEvent(
        this.createLifecyclePhaseStartedEvent(
          plan.productId,
          plan.runId,
          plan.phase,
          plan.correlationId,
          this.clock(),
        ),
        { required: false, correlationId: plan.correlationId },
      );
    }

    // Record runtime truth for execution started
    await this.recordRuntimeTruth(plan, 'execution-started', [`execution:${plan.runId}`]);

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

    // Record runtime truth for execution result
    const status = result.status === 'succeeded' ? 'succeeded' : 'failed';
    await this.recordRuntimeTruth(plan, status === 'succeeded' ? 'execution-succeeded' : 'execution-failed', this.resultEvidenceRefs(result));

    // Append lifecycle event for execution complete
    if (this.providerContext.events !== undefined) {
      await this.providerContext.events.appendEvent(
        this.createLifecyclePhaseCompletedEvent(
          plan.productId,
          plan.runId,
          plan.phase,
          plan.correlationId,
          result.startedAt,
          result.completedAt,
          status,
        ),
        { required: false, correlationId: plan.correlationId },
      );
    }

    if (result.status === 'failed') {
      if (result.failure?.reasonCode === 'approval-required') {
        // Record runtime truth for approval required
        await this.recordRuntimeTruth(plan, 'approval-required', this.resultEvidenceRefs(result));

        this.logger.warn('lifecycle.execution.approval-required', {
          productUnitId: plan.productId,
          runId: plan.runId,
          phase: plan.phase,
          correlationId: plan.correlationId,
        });
        throw new ApprovalRequiredError(result.failure.message, {
          correlationId: plan.correlationId,
          productUnitId: plan.productId,
          runId: plan.runId,
          phase: plan.phase,
        });
      }
      this.logger.error('lifecycle.execution.failed', {
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
        reasonCode: result.failure?.reasonCode,
        correlationId: plan.correlationId,
      });
    } else {
      this.logger.info('lifecycle.execution.succeeded', {
        productUnitId: plan.productId,
        runId: plan.runId,
        phase: plan.phase,
        correlationId: plan.correlationId,
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
    this.validateProviderContext(query.correlationId, query.providerMode);
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
    query: LifecycleRunQuery = {},
  ): Promise<LifecycleRunSummary> {
    this.validateProviderContext(query.correlationId, query.providerMode);
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
    query: LifecycleRunQuery = {},
  ): Promise<unknown> {
    this.validateProviderContext(query.correlationId, query.providerMode);
    const resolvedPhase = phase ?? (await this.getLifecycleRun(productUnitId, runId)).phase ?? 'build';
    const manifestPath = await this.pointerStore.resolveManifestByType(
      productUnitId,
      runId,
      resolvedPhase,
      manifestType,
    );
    try {
      return await this.readJson(manifestPath);
    } catch (error: unknown) {
      if (this.isNodeFileNotFound(error)) {
        throw new ManifestNotFoundError(`Manifest not found at ${manifestPath}`, {
          productUnitId,
          runId,
          phase: resolvedPhase,
          ...(query.correlationId === undefined ? {} : { correlationId: query.correlationId }),
          safeDetails: {
            manifestType,
          },
        });
      }
      if (error instanceof SyntaxError) {
        throw new LifecycleManifestCorruptError({
          filePath: manifestPath,
          operation: 'getManifest',
          cause: error,
        });
      }
      throw error;
    }
  }

  async listPendingApprovals(query: PendingApprovalQuery = {}): Promise<readonly ApprovalRequest[]> {
    this.validateProviderContext(query.correlationId, query.providerMode);
    const provider = this.providerContext.approvals;
    if (provider === undefined) {
      throw new ProviderUnavailableError('Approval provider is not configured', {
        ...(query.correlationId === undefined ? {} : { correlationId: query.correlationId }),
        ...(query.productUnitId === undefined ? {} : { productUnitId: query.productUnitId }),
        ...(query.runId === undefined ? {} : { runId: query.runId }),
      });
    }
    if (!hasPendingApprovalReader(provider)) {
      throw new ProviderUnavailableError('Approval provider does not support pending approval listing', {
        ...(query.correlationId === undefined ? {} : { correlationId: query.correlationId }),
        ...(query.productUnitId === undefined ? {} : { productUnitId: query.productUnitId }),
        ...(query.runId === undefined ? {} : { runId: query.runId }),
      });
    }

    const pendingApprovals = await provider.listPendingApprovals();
    const filteredById = pendingApprovals.filter((approval) => {
      if (query.productUnitId !== undefined && approval.productUnitId !== query.productUnitId) {
        return false;
      }
      if (query.runId !== undefined && approval.runId !== query.runId) {
        return false;
      }
      return true;
    });

    const expectedScope = query.scope;
    if (expectedScope === undefined) {
      return filteredById;
    }

    const scopeFiltered = await Promise.all(
      filteredById.map(async (approval) => {
        const productUnit = await this.registryProvider.getProductUnit(approval.productUnitId);
        if (productUnit === null) {
          return null;
        }
        return this.scopeMatches(productUnit.scope, expectedScope) ? approval : null;
      }),
    );

    return scopeFiltered.filter((approval): approval is ApprovalRequest => approval !== null);
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

  async applyProductUnitIntent(
    intent: ProductUnitIntent,
    options: { readonly mode?: 'bootstrap' | 'platform'; readonly allowWrite: boolean }
  ): Promise<ProductUnitIntentApplicationResult> {
    // Validate ProductUnitIntent at method boundary
    const intentValidation = validateProductUnitIntent(intent);
    if (!intentValidation.valid) {
      return {
        schemaVersion: '1.0.0',
        intentId: intent.intentId,
        status: 'failed',
        productUnitId: intent.productUnit.id,
        correlationId: intent.producer.correlationId,
        providerMode: options.mode ?? this.providerContext.mode,
        registryProviderId: intent.target.registryProvider,
        sourceProviderId: intent.target.sourceProvider,
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: intentValidation.errors,
      };
    }

    // Resolve provider mode with fallback to providerContext.mode
    const providerMode = options.mode ?? this.providerContext.mode;

    // Reject invalid mode
    if (providerMode !== 'bootstrap' && providerMode !== 'platform') {
      return {
        schemaVersion: '1.0.0',
        intentId: intent.intentId,
        status: 'failed',
        productUnitId: intent.productUnit.id,
        correlationId: intent.producer.correlationId,
        providerMode,
        registryProviderId: intent.target.registryProvider,
        sourceProviderId: intent.target.sourceProvider,
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: ['provider-mode-not-available'],
        errors: [`Invalid provider mode: ${providerMode}`],
      };
    }

    // Fail closed in platform mode if required providers are missing
    this.validateProviderContext(intent.producer.correlationId, providerMode);

    // Require registry provider to support ProductUnitIntent application
    if (!isProductUnitIntentCapableRegistryProvider(this.registryProvider)) {
      return {
        schemaVersion: '1.0.0',
        intentId: intent.intentId,
        status: 'failed',
        productUnitId: intent.productUnit.id,
        correlationId: intent.producer.correlationId,
        providerMode,
        registryProviderId: this.registryProvider.providerId,
        sourceProviderId: intent.target.sourceProvider,
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: ['registry-apply-failed'],
        errors: ['Registry provider does not support ProductUnitIntent application'],
      };
    }

    const capableRegistry = this.registryProvider as ProductUnitIntentCapableRegistryProvider;
    const applyOptions: ProductUnitIntentApplyOptions = { allowWrite: options.allowWrite };

    // In preview mode or apply mode
    const applyProviderResult = options.allowWrite
      ? await capableRegistry.applyProductUnitIntent(intent, applyOptions)
      : undefined;
    const previewProviderResult = options.allowWrite
      ? undefined
      : await capableRegistry.previewApplyProductUnitIntent(intent);
    const providerResult = applyProviderResult ?? previewProviderResult;
    if (providerResult === undefined) {
      throw new ProviderUnavailableError('ProductUnitIntent application did not produce a registry result', {
        correlationId: intent.producer.correlationId,
        productUnitId: intent.productUnit.id,
        runId: intent.intentId,
        phase: 'create',
      });
    }
    const applied = applyProviderResult?.applied === true;
    const status = !providerResult.valid || providerResult.errors.length > 0
      ? 'blocked'
      : applied
        ? 'applied'
        : 'previewed';
    const lifecycleEventRefs: string[] = [];
    const runtimeTruthRefs: string[] = [];
    const provenanceRefs: string[] = [];

    // Build result
    if (this.providerContext.events !== undefined) {
      const eventResult = await this.providerContext.events.appendEvent(
        applied
          ? this.createIntentAppliedEvent(intent, applyProviderResult)
          : this.createIntentValidatedEvent(intent, providerResult),
        { required: false, correlationId: intent.producer.correlationId },
      );
      if (eventResult.success && eventResult.ref !== undefined) {
        lifecycleEventRefs.push(eventResult.ref);
      }
    }

    // Record runtime truth and provenance
    const evidenceRefs = [...lifecycleEventRefs];
    if (this.providerContext.runtimeTruth !== undefined) {
      const truthResult = await this.providerContext.runtimeTruth.recordRuntimeTruth(
        {
          productUnitId: intent.productUnit.id,
          runId: intent.intentId,
          phase: 'create' as ProductLifecyclePhase,
          status,
          observedAt: this.clock(),
          evidenceRefs,
        },
        { required: true, correlationId: intent.producer.correlationId },
      );
      if (truthResult.success) {
        runtimeTruthRefs.push(truthResult.ref ?? 'runtime-truth://kernel-lifecycle');
      }
    }

    if (this.providerContext.provenance !== undefined) {
      const provenanceResult = await this.providerContext.provenance.recordProvenance(
        {
          provenanceId: `kernel-intent:${intent.intentId}`,
          productUnitId: intent.productUnit.id,
          runId: intent.intentId,
          source: 'kernel-lifecycle-service',
          evidenceRefs,
          recordedAt: this.clock(),
        },
        { required: true, correlationId: intent.producer.correlationId },
      );
      if (provenanceResult.success) {
        provenanceRefs.push(provenanceResult.ref ?? 'provenance://kernel-lifecycle');
      }
    }

    const result: ProductUnitIntentApplicationResult = {
      schemaVersion: '1.0.0',
      intentId: intent.intentId,
      status,
      productUnitId: intent.productUnit.id,
      correlationId: intent.producer.correlationId,
      providerMode,
      registryProviderId: this.registryProvider.providerId,
      sourceProviderId: intent.target.sourceProvider,
      ...(options.allowWrite ? { applicationRef: providerResult.registryPath } : { previewRef: providerResult.registryPath }),
      ...(applied ? { appliedAt: this.clock() } : {}),
      lifecycleEventRefs,
      provenanceRefs,
      runtimeTruthRefs,
      blockedReasons: providerResult.errors,
      errors: providerResult.errors,
    };

    // Clear/refresh registry cache after successful apply
    if (applied && 'reload' in this.registryProvider) {
      (this.registryProvider as { reload: () => void }).reload();
    }

    return result;
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

  private validateProviderContext(correlationId?: string, requestedMode?: 'bootstrap' | 'platform'): void {
    const validation = validateKernelLifecycleProviderContext(this.providerContext);
    if (!validation.valid) {
      const missingProviders = [...validation.missingProviders];
      const invalidBackingStores = [...validation.invalidBackingStores];
      const missingProvidersMessage = missingProviders.length > 0
        ? `missing lifecycle providers: ${missingProviders.join(', ')}`
        : undefined;
      const invalidBackingStoresMessage = invalidBackingStores.length > 0
        ? `invalid backing stores: ${invalidBackingStores.map((entry) => `${entry.providerName}=${entry.backingStore}`).join(', ')}`
        : undefined;
      const reasons = [missingProvidersMessage, invalidBackingStoresMessage].filter(
        (reason): reason is string => reason !== undefined,
      );
      throw new ProviderUnavailableError(
        `Kernel ${this.providerContext.mode} mode provider context is invalid${reasons.length > 0 ? ` (${reasons.join('; ')})` : ''}`,
        {
          ...(correlationId === undefined ? {} : { correlationId }),
          safeDetails: { missingProviders, invalidBackingStores },
        },
      );
    }

    // Reject platform-mode run when provider context mode is bootstrap
    if (requestedMode === 'platform' && this.providerContext.mode !== 'platform') {
      throw new ProviderUnavailableError(
        `Platform mode requested but provider context is in ${this.providerContext.mode} mode. Platform mode requires all providers to be Data Cloud-backed.`,
        {
          ...(correlationId === undefined ? {} : { correlationId }),
          safeDetails: { contextMode: this.providerContext.mode, requestedMode: 'platform' },
        },
      );
    }

    // For platform mode, require all providers to be available
    if (this.providerContext.mode === 'platform' || requestedMode === 'platform') {
      const platformProviders = this.providerContext;
      const missingPlatformProviders: string[] = [];

      if (platformProviders.events === undefined) {
        missingPlatformProviders.push('events');
      }
      if (platformProviders.artifacts === undefined) {
        missingPlatformProviders.push('artifacts');
      }
      if (platformProviders.health === undefined) {
        missingPlatformProviders.push('health');
      }
      if (platformProviders.approvals === undefined) {
        missingPlatformProviders.push('approvals');
      }
      if (platformProviders.provenance === undefined) {
        missingPlatformProviders.push('provenance');
      }
      if (platformProviders.memory === undefined) {
        missingPlatformProviders.push('memory');
      }
      if (platformProviders.runtimeTruth === undefined) {
        missingPlatformProviders.push('runtimeTruth');
      }

      if (missingPlatformProviders.length > 0) {
        throw new ProviderUnavailableError(
          `Platform mode requires Data Cloud-backed providers. Missing: ${missingPlatformProviders.join(', ')}`,
          {
            ...(correlationId === undefined ? {} : { correlationId }),
            safeDetails: { missingProviders: missingPlatformProviders },
          },
        );
      }
    }
  }

  private createLifecyclePlanCreatedEvent(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    correlationId: string | undefined,
    providerMode: 'bootstrap' | 'platform',
    createdAt: string,
  ): KernelLifecycleEvent {
    return {
      metadata: {
        eventId: `plan-created:${runId}:${phase}`,
        schemaVersion: '1.0.0',
        eventType: 'lifecycle.plan.created',
        productUnitId,
        runId,
        phase,
        timestamp: createdAt,
        source: 'kernel-lifecycle-service',
        correlationId: correlationId ?? runId,
      },
      payload: {
        planRunId: runId,
        phase,
        providerMode,
        createdAt,
      },
    };
  }

  private createLifecyclePhaseStartedEvent(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    correlationId: string | undefined,
    startedAt: string,
  ): KernelLifecycleEvent {
    return {
      metadata: {
        eventId: `phase-started:${runId}:${phase}`,
        schemaVersion: '1.0.0',
        eventType: 'lifecycle.phase.started',
        productUnitId,
        runId,
        phase,
        timestamp: startedAt,
        source: 'kernel-lifecycle-service',
        correlationId: correlationId ?? runId,
      },
      payload: {
        phase,
        status: 'running',
        startedAt,
      },
    };
  }

  private createLifecyclePhaseCompletedEvent(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    correlationId: string | undefined,
    startedAt: string,
    completedAt: string,
    status: 'succeeded' | 'failed' | 'skipped',
  ): KernelLifecycleEvent {
    return {
      metadata: {
        eventId: `phase-completed:${runId}:${phase}`,
        schemaVersion: '1.0.0',
        eventType: 'lifecycle.phase.completed',
        productUnitId,
        runId,
        phase,
        timestamp: completedAt,
        source: 'kernel-lifecycle-service',
        correlationId: correlationId ?? runId,
      },
      payload: {
        phase,
        status,
        durationMs: Math.max(0, Date.parse(completedAt) - Date.parse(startedAt)),
        completedAt,
      },
    };
  }

  private createIntentValidatedEvent(
    intent: ProductUnitIntent,
    providerResult: { readonly valid: boolean; readonly errors: readonly string[] },
  ): KernelLifecycleEvent {
    const timestamp = this.clock();
    return {
      metadata: {
        eventId: `intent-validated:${intent.intentId}`,
        schemaVersion: '1.0.0',
        eventType: 'product-unit.intent.validated',
        productUnitId: intent.productUnit.id,
        runId: intent.intentId,
        phase: 'create',
        timestamp,
        source: 'kernel-lifecycle-service',
        correlationId: intent.producer.correlationId,
      },
      payload: {
        intentId: intent.intentId,
        valid: providerResult.valid,
        errors: [...providerResult.errors],
      },
    };
  }

  private createIntentAppliedEvent(
    intent: ProductUnitIntent,
    providerResult: { readonly applied: boolean; readonly diff: readonly string[] },
  ): KernelLifecycleEvent {
    const timestamp = this.clock();
    return {
      metadata: {
        eventId: `intent-applied:${intent.intentId}`,
        schemaVersion: '1.0.0',
        eventType: 'product-unit.intent.applied',
        productUnitId: intent.productUnit.id,
        runId: intent.intentId,
        phase: 'create',
        timestamp,
        source: 'kernel-lifecycle-service',
        correlationId: intent.producer.correlationId,
      },
      payload: {
        intentId: intent.intentId,
        productUnitId: intent.productUnit.id,
        applied: providerResult.applied,
        changedFiles: [...providerResult.diff],
      },
    };
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
    } catch (error: unknown) {
      if (this.isNodeFileNotFound(error)) {
        return [];
      }
      this.logger.error('Lifecycle truth read failed', {
        reasonCode: 'lifecycle-run-index-unavailable',
        productUnitId,
        filePath: productRoot,
      });
      throw new LifecycleRunIndexUnavailableError({
        productUnitId,
        filePath: productRoot,
        operation: 'listPhaseDirectories',
        cause: error,
      });
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
    let raw: string;
    try {
      raw = await fs.readFile(filePath, 'utf-8');
    } catch (error: unknown) {
      if (this.isNodeFileNotFound(error)) {
        return null;
      }
      this.logger.error('Lifecycle truth read failed', {
        reasonCode: 'lifecycle-truth-read-failed',
        filePath,
      });
      throw new LifecycleRunIndexUnavailableError({
        filePath,
        operation: 'readJsonIfExists',
        cause: error,
      });
    }
    try {
      return JSON.parse(raw) as TValue;
    } catch (error: unknown) {
      this.logger.error('Lifecycle truth read failed', {
        reasonCode: 'lifecycle-manifest-corrupt',
        filePath,
      });
      throw new LifecycleManifestCorruptError({
        filePath,
        operation: 'readJsonIfExists',
        cause: error,
      });
    }
  }

  private isNodeFileNotFound(error: unknown): boolean {
    return (
      typeof error === 'object' &&
      error !== null &&
      'code' in error &&
      (error as { code: unknown }).code === 'ENOENT'
    );
  }
}

function hasPendingApprovalReader(provider: unknown): provider is ApprovalProvider {
  return typeof provider === 'object' &&
    provider !== null &&
    'listPendingApprovals' in provider &&
    typeof (provider as { readonly listPendingApprovals?: unknown }).listPendingApprovals === 'function';
}
