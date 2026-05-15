import {
  ProductLifecyclePhase,
  ProductLifecycleResult,
  ProductLifecycleStep,
  ProductLifecyclePlan,
  ProductLifecycleStepResult,
  ProductLifecycleManifestType,
  ProductGateResult,
  ProductLifecycleApprovalRef,
  ProductLifecycleApprovalRequirement,
} from '../domain/ProductLifecyclePhase.js';
import {
  ProductLifecycleStepRunner,
  StepContext,
} from './ProductLifecycleStepRunner.js';
import { ExecutionResultCollector } from './ExecutionResultCollector.js';
import { ConsoleExecutionLogger } from './ExecutionLogger.js';
import type { KernelLifecycleEventEmitter } from '../events/KernelLifecycleEventEmitter.js';
import type { LifecycleHealthAggregator } from '../health/LifecycleHealthAggregator.js';
import { LifecycleManifestWriter } from '../manifest/LifecycleManifestWriter.js';
import { GateExecutor } from '../gates/GateExecutor.js';
import type {
  ApprovalRequest,
  KernelLifecycleProviderContext,
} from '@ghatana/kernel-product-contracts';

export interface ProductLifecycleExecutionOptions {
  dryRun: boolean;
  outputDirectory: string;
  environment?: string;
  sourceRef?: string;
  logger?: ConsoleExecutionLogger;
  eventEmitter?: KernelLifecycleEventEmitter;
  healthAggregator?: LifecycleHealthAggregator;
  providerContext?: KernelLifecycleProviderContext;
}

/**
 * Product lifecycle executor — supports sequential and parallel (DAG) execution modes.
 */
export class ProductLifecycleExecutor {
  private readonly stepRunner: ProductLifecycleStepRunner;
  private readonly resultCollector: ExecutionResultCollector;

  constructor(stepRunner: ProductLifecycleStepRunner, resultCollector: ExecutionResultCollector) {
    this.stepRunner = stepRunner;
    this.resultCollector = resultCollector;
  }

  async executePlan(
    plan: ProductLifecyclePlan,
    options: ProductLifecycleExecutionOptions,
  ): Promise<ProductLifecycleResult> {
    return this.execute(plan.productId, plan.phase, plan.steps, options, plan);
  }

  async execute(
    productId: string,
    phase: ProductLifecyclePhase,
    steps: ProductLifecycleStep[],
    options: ProductLifecycleExecutionOptions,
    plan?: ProductLifecyclePlan,
  ): Promise<ProductLifecycleResult> {
    this.resultCollector.reset();
    const logger = options.logger ?? new ConsoleExecutionLogger();

    const phaseMode = plan?.phaseMode ?? 'sequential';
    const runId = plan?.runId ?? this.generateRunId();

    // Emit lifecycle phase start event
    if (options.eventEmitter) {
      options.eventEmitter.emitLifecyclePhaseStart(productId, runId, phase, plan?.correlationId);
    }

    const startTime = Date.now();
    const gateExecution = plan
      ? await new GateExecutor({
          ...(options.providerContext !== undefined ? { providerContext: options.providerContext } : {}),
        }).execute({
          productId,
          runId,
          phase,
          gates: plan.gates,
          artifacts: [],
          ...(options.environment !== undefined ? { environment: options.environment } : {}),
          ...(plan.productUnit !== undefined ? { productUnit: plan.productUnit } : {}),
          providerMode: plan.providerMode,
        })
      : { gates: [] };
    for (const gateResult of gateExecution.gates) {
      this.resultCollector.addGateResult(gateResult);
      options.eventEmitter?.emitGateEvaluated(
        productId,
        runId,
        phase,
        gateResult.gateId,
        gateResult.status === 'passed',
        gateResult.details ?? gateResult.status,
        gateResult.evidenceRefs ?? [],
        gateResult.durationMs ?? 0,
        plan?.correlationId,
      );
    }

    const approvalResolution = gateExecution.failedRequiredGate === undefined && plan
      ? await this.requestRequiredApprovals(plan, options)
      : { approvalRefs: [] as ProductLifecycleApprovalRef[] };

    if (gateExecution.failedRequiredGate === undefined && approvalResolution.failure === undefined) {
      if (phaseMode === 'parallel' || phaseMode === 'dag') {
        await this.executeDAG(steps, productId, runId, options, logger, plan);
      } else {
        await this.executeSequential(steps, productId, runId, options, logger, plan);
      }
    }

    let result = this.resultCollector.collect(productId, phase, options.outputDirectory, runId, {
      ...(plan?.correlationId !== undefined ? { correlationId: plan.correlationId } : {}),
      providerMode: plan?.providerMode ?? 'bootstrap',
      ...(plan?.productUnitRef !== undefined ? { productUnitRef: plan.productUnitRef } : {}),
      ...(approvalResolution.approvalRefs.length > 0 ? { approvalRefs: approvalResolution.approvalRefs } : {}),
    });
    result = this.failClosedWhenRequiredGateFailed(result, gateExecution.failedRequiredGate);
    result = this.failClosedWhenApprovalRequired(result, approvalResolution.failure);
    result = this.failClosedWhenRequiredArtifactsMissing(result, plan);
    const duration = Date.now() - startTime;

    // Emit lifecycle phase complete event
    if (options.eventEmitter) {
      options.eventEmitter.emitLifecyclePhaseComplete(
        productId,
        runId,
        phase,
        result.status,
        duration,
        plan?.correlationId
      );
    }

    const healthSnapshot = options.healthAggregator && plan
      ? await options.healthAggregator.aggregateLifecycleHealth(productId, runId, [phase])
      : {
          schemaVersion: '1.0.0',
          productId,
          runId,
          status: result.status === 'succeeded' ? 'healthy' : result.status === 'failed' ? 'failed' : 'skipped',
          phase,
          snapshotAt: new Date().toISOString(),
        };
    void healthSnapshot;

    const manifestWriter = new LifecycleManifestWriter({
      outputDirectory: options.outputDirectory,
      ...(options.providerContext !== undefined ? { providerContext: options.providerContext } : {}),
    });
    const manifestWrite = await manifestWriter.writeRequiredManifests({
      result,
      requiredManifests: this.requiredManifestTypes(result, plan),
      ...(options.environment !== undefined ? { environment: options.environment } : {}),
    });
    result = manifestWrite.result;

    return result;
  }

  private generateRunId(): string {
    const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const rand = Math.random().toString(36).substring(2, 11);
    return `${ts}-${rand}`;
  }

  private failClosedWhenRequiredArtifactsMissing(
    result: ProductLifecycleResult,
    plan?: ProductLifecyclePlan,
  ): ProductLifecycleResult {
    if (!plan || plan.expectedArtifacts.length === 0 || result.status !== 'succeeded') {
      return result;
    }

    const missing = plan.expectedArtifacts.filter((expected) => {
      if (!expected.required) {
        return false;
      }
      return !result.artifacts.some(
        (artifact) => artifact.surface === expected.surface && artifact.type === expected.type,
      );
    });

    if (missing.length === 0) {
      return result;
    }

    const message = `Missing required output artifacts: ${missing
      .map((artifact) => `${artifact.surface}:${artifact.type}`)
      .join(', ')}`;

    return {
      ...result,
      status: 'failed',
      failure: {
        reasonCode: 'artifact-missing',
        stepId: 'artifact-validation',
        message,
      },
    };
  }

  private async requestRequiredApprovals(
    plan: ProductLifecyclePlan,
    options: ProductLifecycleExecutionOptions,
  ): Promise<{
    readonly approvalRefs: ProductLifecycleApprovalRef[];
    readonly failure?: { readonly stepId: string; readonly message: string; readonly cause?: string };
  }> {
    const requiredApprovals = plan.approvalRequirements.filter((approval) => approval.required);
    if (requiredApprovals.length === 0) {
      return { approvalRefs: [] };
    }

    const approvalProvider = options.providerContext?.approvals;
    if (approvalProvider === undefined) {
      return {
        approvalRefs: [],
        failure: {
          stepId: 'approval-provider',
          message: `Lifecycle approvals are required but no approval provider is configured: ${requiredApprovals
            .map((approval) => approval.approvalId)
            .join(', ')}`,
        },
      };
    }

    const approvalRefs: ProductLifecycleApprovalRef[] = [];
    for (const approval of requiredApprovals) {
      const request = this.buildApprovalRequest(plan, approval, options);
      if (request === undefined) {
        return {
          approvalRefs,
          failure: {
            stepId: `approval:${approval.approvalId}`,
            message: `Lifecycle approval ${approval.approvalId} does not declare required approvers`,
          },
        };
      }

      const result = await approvalProvider.requestLifecycleApproval(request, {
        required: true,
        correlationId: plan.correlationId,
      });
      if (!result.success) {
        return {
          approvalRefs,
          failure: {
            stepId: `approval:${approval.approvalId}`,
            message: `Lifecycle approval request failed: ${approval.approvalId}`,
            ...(result.error !== undefined ? { cause: result.error } : {}),
          },
        };
      }
      approvalRefs.push({
        approvalId: approval.approvalId,
        status: 'pending',
        ref: result.ref ?? `approval:${approval.approvalId}`,
      });
    }

    return {
      approvalRefs,
      failure: {
        stepId: 'approval-required',
        message: `Lifecycle approval required before ${plan.phase}: ${requiredApprovals
          .map((approval) => approval.approvalId)
          .join(', ')}`,
      },
    };
  }

  private buildApprovalRequest(
    plan: ProductLifecyclePlan,
    approval: ProductLifecycleApprovalRequirement,
    options: ProductLifecycleExecutionOptions,
  ): ApprovalRequest | undefined {
    if (approval.requiredApprovers === undefined || approval.requiredApprovers.length === 0) {
      return undefined;
    }

    return {
      approvalId: approval.approvalId,
      productUnitId: plan.productId,
      runId: plan.runId,
      correlationId: plan.correlationId,
      requestedBy: 'kernel-lifecycle',
      requestedAt: new Date().toISOString(),
      reason: `Approval required for ${approval.action}`,
      environment: options.environment ?? plan.environment ?? 'local',
      action: approval.action,
      riskLevel: approval.riskLevel,
      evidenceRefs: [`lifecycle-plan:${plan.runId}`, `approval-source:${approval.source}`],
      requiredApprovers: approval.requiredApprovers,
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    };
  }

  private failClosedWhenApprovalRequired(
    result: ProductLifecycleResult,
    failure?: { readonly stepId: string; readonly message: string; readonly cause?: string },
  ): ProductLifecycleResult {
    if (failure === undefined || result.status === 'failed') {
      return result;
    }

    return {
      ...result,
      status: 'failed',
      failure: {
        reasonCode: 'approval-required',
        stepId: failure.stepId,
        message: failure.message,
        ...(failure.cause !== undefined ? { cause: failure.cause } : {}),
      },
    };
  }

  private failClosedWhenRequiredGateFailed(
    result: ProductLifecycleResult,
    failedGate?: ProductGateResult,
  ): ProductLifecycleResult {
    if (failedGate === undefined || result.status === 'failed') {
      return result;
    }
    return {
      ...result,
      status: 'failed',
      failure: {
        reasonCode: 'gate-failed',
        stepId: `gate:${failedGate.gateId}`,
        message: failedGate.details ?? `Required gate ${failedGate.gateId} failed`,
      },
    };
  }

  /**
   * Execute steps sequentially, skipping any whose dependencies have failed or been skipped.
   */
  private async executeSequential(
    steps: ProductLifecycleStep[],
    productId: string,
    runId: string,
    options: ProductLifecycleExecutionOptions,
    logger: ConsoleExecutionLogger,
    plan?: ProductLifecyclePlan,
  ): Promise<void> {
    const state = new Map<string, 'pending' | 'done' | 'failed' | 'skipped'>();
    for (const step of steps) {
      state.set(step.id, 'pending');
    }

    for (const step of steps) {
      const unmetDependency = step.dependsOn.find((dependencyId) => state.get(dependencyId) !== 'done');
      if (unmetDependency) {
        const result = { stepId: step.id, status: 'skipped' as const, durationMs: 0 };
        this.emitStepComplete(productId, runId, step, result, options, plan, [
          `dependency:${unmetDependency}:not-done`,
        ]);
        this.recordStepResult(result);
        state.set(step.id, 'skipped');
        continue;
      }

      const result = await this.runOrDryRun(step, productId, runId, options, logger, plan);
      this.recordStepResult(result);
      state.set(step.id, result.status === 'succeeded' ? 'done' : result.status);
    }
  }

  /**
   * Execute steps in DAG order — steps whose dependencies are satisfied run concurrently.
   */
  private async executeDAG(
    steps: ProductLifecycleStep[],
    productId: string,
    runId: string,
    options: ProductLifecycleExecutionOptions,
    logger: ConsoleExecutionLogger,
    plan?: ProductLifecyclePlan,
  ): Promise<void> {
    const state = new Map<string, 'pending' | 'done' | 'failed' | 'skipped'>();
    for (const step of steps) {
      state.set(step.id, 'pending');
    }

    const settled = new Set<string>();

    while (settled.size < steps.length) {
      // Find steps that are ready to run (all dependencies done, not yet settled)
      const ready = steps.filter((step) => {
        if (settled.has(step.id)) return false;
        return step.dependsOn.every((dep) => {
          const depState = state.get(dep);
          return depState === 'done' || depState === 'skipped';
        });
      });

      if (ready.length === 0) {
        // Skip remaining unsettled steps if no progress can be made
        for (const step of steps) {
          if (!settled.has(step.id)) {
            const result = { stepId: step.id, status: 'skipped' as const, durationMs: 0 };
            this.emitStepComplete(productId, runId, step, result, options, plan, [
              'dag:no-ready-steps',
            ]);
            this.recordStepResult(result);
            settled.add(step.id);
            state.set(step.id, 'skipped');
          }
        }
        break;
      }

      // Skip steps whose dependencies have failed
      const toRun = ready.filter((step) => {
        const hasFailedDep = step.dependsOn.some((dep) => state.get(dep) === 'failed');
        if (hasFailedDep) {
          const failedDependency = step.dependsOn.find((dep) => state.get(dep) === 'failed') ?? 'unknown';
          const result = { stepId: step.id, status: 'skipped' as const, durationMs: 0 };
          this.emitStepComplete(productId, runId, step, result, options, plan, [
            `dependency:${failedDependency}:failed`,
          ]);
          this.recordStepResult(result);
          settled.add(step.id);
          state.set(step.id, 'skipped');
          return false;
        }
        return true;
      });

      if (toRun.length === 0) continue;

      // Run eligible steps concurrently
      const batchResults = await Promise.all(
        toRun.map((step) => this.runOrDryRun(step, productId, runId, options, logger, plan)),
      );

      for (const result of batchResults) {
        this.recordStepResult(result);
        settled.add(result.stepId);
        state.set(result.stepId, result.status === 'succeeded' ? 'done' : result.status);
      }
    }
  }

  private async runOrDryRun(
    step: ProductLifecycleStep,
    productId: string,
    runId: string,
    options: ProductLifecycleExecutionOptions,
    logger: ConsoleExecutionLogger,
    plan?: ProductLifecyclePlan,
  ): Promise<ProductLifecycleStepResult> {
    this.emitStepStart(productId, runId, step, options, plan);

    if (options.dryRun) {
      const timestamp = new Date().toISOString();
      const result = {
        stepId: step.id,
        phase: step.phase,
        surface: step.surface,
        adapter: step.adapter,
        status: 'skipped' as const,
        startedAt: timestamp,
        completedAt: timestamp,
        exitCode: 0,
        stdout: step.execution
          ? `[DRY-RUN] ${step.execution.command} ${step.execution.args.join(' ')}`
          : `[DRY-RUN] ${step.phase} phase for ${step.surface} via ${step.adapter}`,
        durationMs: 0,
        artifacts: [],
        errors: [],
        warnings: [],
        ...(plan?.correlationId ? { correlationId: plan.correlationId } : {}),
      };
      this.emitStepComplete(productId, runId, step, result, options, plan, ['dry-run']);
      return result;
    }
    const result = await this.executeAdapterStep(step, productId, options, logger, plan);
    this.emitStepComplete(productId, runId, step, result, options, plan);
    return result;
  }

  private async executeAdapterStep(
    step: ProductLifecycleStep,
    productId: string,
    options: ProductLifecycleExecutionOptions,
    logger: ConsoleExecutionLogger,
    plan?: ProductLifecyclePlan,
  ): Promise<ProductLifecycleStepResult> {
    const surfacePath = this.resolveSurfacePath(step.surface, plan);

    const adapterCtx = step.adapterContext ?? {};
    const surfaceConfig: Record<string, unknown> = {
      ...(adapterCtx.surfaceConfig ?? {}),
      ...(adapterCtx.packageConfig ?? {}),
      ...(adapterCtx.deploymentConfig ?? {}),
      ...(adapterCtx.environmentConfig ?? {}),
    };

    const stepContext: StepContext = {
      productId,
      surfaceType: step.surface,
      surfacePath,
      ...(options.environment ? { environment: options.environment } : {}),
      ...(options.sourceRef ? { sourceRef: options.sourceRef } : {}),
      dryRun: options.dryRun,
      surfaceConfig,
      phaseConfig: {},
      logger,
      outputDirectory: options.outputDirectory,
      required: this.isStepRequired(step, plan),
    };

    const startedAt = new Date().toISOString();
    const result = await this.stepRunner.run(step, stepContext);
    return {
      ...result,
      phase: step.phase,
      surface: step.surface,
      adapter: step.adapter,
      startedAt,
      completedAt: new Date().toISOString(),
      errors:
        result.errors ??
        (result.status === 'failed' ? [result.stderr ?? `Step ${step.id} failed`] : []),
      warnings: result.warnings ?? [],
      ...(plan?.correlationId ? { correlationId: plan.correlationId } : {}),
    };
  }

  private recordStepResult(result: ProductLifecycleStepResult): void {
    this.resultCollector.addStepResult(result);
    for (const artifact of result.artifacts ?? []) {
      this.resultCollector.addArtifact(artifact);
    }
  }

  private emitStepStart(
    productId: string,
    runId: string,
    step: ProductLifecycleStep,
    options: ProductLifecycleExecutionOptions,
    plan?: ProductLifecyclePlan,
  ): void {
    options.eventEmitter?.emitLifecycleStepStart(
      productId,
      runId,
      step.phase,
      step.id,
      step.stepKind,
      step.surface,
      step.adapter,
      plan?.correlationId,
    );
  }

  private emitStepComplete(
    productId: string,
    runId: string,
    step: ProductLifecycleStep,
    result: ProductLifecycleStepResult,
    options: ProductLifecycleExecutionOptions,
    plan?: ProductLifecyclePlan,
    extraEvidenceRefs: readonly string[] = [],
  ): void {
    options.eventEmitter?.emitLifecycleStepComplete(
      productId,
      runId,
      step.phase,
      step.id,
      step.stepKind,
      step.surface,
      step.adapter,
      result.status,
      result.durationMs,
      [...this.stepEvidenceRefs(result), ...extraEvidenceRefs],
      plan?.correlationId,
      result.exitCode,
    );
  }

  private stepEvidenceRefs(result: ProductLifecycleStepResult): string[] {
    return [
      ...(result.artifacts ?? []).map((artifact) => `artifact:${artifact.id}`),
      ...(result.manifestRefs?.artifactManifest ? [`manifest:${result.manifestRefs.artifactManifest}`] : []),
      ...(result.manifestRefs?.deploymentManifest ? [`manifest:${result.manifestRefs.deploymentManifest}`] : []),
      ...(result.manifestRefs?.verifyHealthReport ? [`manifest:${result.manifestRefs.verifyHealthReport}`] : []),
      ...(result.testResults ? [`tests:${result.testResults.tests}:failures:${result.testResults.failures}`] : []),
      ...(result.coverageResults ? [`coverage:lines:${result.coverageResults.lineCoverage}`] : []),
    ];
  }

  private isStepRequired(step: ProductLifecycleStep, plan?: ProductLifecyclePlan): boolean {
    if (plan === undefined) {
      return true;
    }
    if (plan.expectedArtifacts.length === 0) {
      return true;
    }
    return plan.expectedArtifacts.some((artifact) => artifact.surface === step.surface && artifact.required);
  }

  private requiredManifestTypes(
    result: ProductLifecycleResult,
    plan?: ProductLifecyclePlan,
  ): readonly ProductLifecycleManifestType[] {
    const required = new Set<ProductLifecycleManifestType>([
      ...(plan?.requiredManifests ?? []),
      'lifecycle-result',
      'lifecycle-health-snapshot',
      'gate-result-manifest',
    ]);

    if (result.artifacts.length > 0) {
      required.add('artifact-manifest');
    }
    if (result.phase === 'deploy') {
      required.add('deployment-manifest');
    }
    if (result.phase === 'verify') {
      required.add('verify-health-report');
    }
    if ((plan?.providerMode ?? result.providerMode) === 'bootstrap') {
      required.add('lifecycle-events');
    }

    return [...required];
  }

  /** Resolve the source path for a surface name from the plan, or fall back to the surface ID. */
  private resolveSurfacePath(surface: string, plan?: ProductLifecyclePlan): string {
    if (!plan) return surface;

    for (const s of plan.surfaces) {
      if (s.surface === surface && typeof s.config.source === 'string') {
        return s.config.source;
      }
    }

    return surface;
  }
}
