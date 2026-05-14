import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import {
  ProductLifecyclePhase,
  ProductLifecycleResult,
  ProductLifecycleStep,
  ProductLifecyclePlan,
  ProductLifecycleStepResult,
} from '../domain/ProductLifecyclePhase.js';
import {
  ProductLifecycleStepRunner,
  StepContext,
} from './ProductLifecycleStepRunner.js';
import { ExecutionResultCollector } from './ExecutionResultCollector.js';
import { ConsoleExecutionLogger } from './ExecutionLogger.js';

export interface ProductLifecycleExecutionOptions {
  dryRun: boolean;
  outputDirectory: string;
  environment?: string;
  sourceRef?: string;
  logger?: ConsoleExecutionLogger;
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

    if (phaseMode === 'parallel' || phaseMode === 'dag') {
      await this.executeDAG(steps, productId, options, logger, plan);
    } else {
      await this.executeSequential(steps, productId, options, logger, plan);
    }

    const result = this.resultCollector.collect(productId, phase, options.outputDirectory);

    // Write phase-specific output summaries after execution
    await this.writePhaseOutputs(phase, result, options.outputDirectory, productId, options.environment);

    return result;
  }

  /**
   * Execute steps sequentially, skipping any whose dependencies have failed or been skipped.
   */
  private async executeSequential(
    steps: ProductLifecycleStep[],
    productId: string,
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
        this.resultCollector.addStepResult({ stepId: step.id, status: 'skipped', durationMs: 0 });
        state.set(step.id, 'skipped');
        continue;
      }

      const result = await this.runOrDryRun(step, productId, options, logger, plan);
      this.resultCollector.addStepResult(result);
      state.set(step.id, result.status === 'succeeded' ? 'done' : result.status);
    }
  }

  /**
   * Execute steps in DAG order — steps whose dependencies are satisfied run concurrently.
   */
  private async executeDAG(
    steps: ProductLifecycleStep[],
    productId: string,
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
            this.resultCollector.addStepResult({ stepId: step.id, status: 'skipped', durationMs: 0 });
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
          this.resultCollector.addStepResult({ stepId: step.id, status: 'skipped', durationMs: 0 });
          settled.add(step.id);
          state.set(step.id, 'skipped');
          return false;
        }
        return true;
      });

      if (toRun.length === 0) continue;

      // Run eligible steps concurrently
      const batchResults = await Promise.all(
        toRun.map((step) => this.runOrDryRun(step, productId, options, logger, plan)),
      );

      for (const result of batchResults) {
        this.resultCollector.addStepResult(result);
        settled.add(result.stepId);
        state.set(result.stepId, result.status === 'succeeded' ? 'done' : result.status);
      }
    }
  }

  private async runOrDryRun(
    step: ProductLifecycleStep,
    productId: string,
    options: ProductLifecycleExecutionOptions,
    logger: ConsoleExecutionLogger,
    plan?: ProductLifecyclePlan,
  ): Promise<ProductLifecycleStepResult> {
    if (options.dryRun) {
      return {
        stepId: step.id,
        status: 'skipped' as const,
        exitCode: 0,
        stdout: step.execution
          ? `[DRY-RUN] ${step.execution.command} ${step.execution.args.join(' ')}`
          : `[DRY-RUN] ${step.phase} phase for ${step.surface} via ${step.adapter}`,
        durationMs: 0,
      };
    }
    return this.executeAdapterStep(step, productId, options, logger, plan);
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
    };

    return this.stepRunner.run(step, stepContext);
  }

  /**
   * Write phase-specific output files after a phase completes.
   * - deploy → deployment-manifest.json
   * - test   → test-summary.json
   */
  private async writePhaseOutputs(
    phase: ProductLifecyclePhase,
    result: ProductLifecycleResult,
    outputDirectory: string,
    productId: string,
    environment?: string,
  ): Promise<void> {
    try {
      await fs.mkdir(outputDirectory, { recursive: true });

      if (phase === 'deploy') {
        const deployManifest = {
          schemaVersion: '1.0.0',
          productId,
          environment: environment ?? 'local',
          status: result.status,
          deployedAt: result.completedAt,
          steps: result.steps.map((s) => ({ stepId: s.stepId, status: s.status })),
        };
        await fs.writeFile(
          path.join(outputDirectory, 'deployment-manifest.json'),
          JSON.stringify(deployManifest, null, 2),
          'utf-8',
        );
      }

      if (phase === 'test') {
        const totalSteps = result.steps.length;
        const passedSteps = result.steps.filter((s) => s.status === 'succeeded').length;
        const failedSteps = result.steps.filter((s) => s.status === 'failed').length;
        const testSummary = {
          schemaVersion: '1.0.0',
          productId,
          status: result.status,
          completedAt: result.completedAt,
          totalSteps,
          passedSteps,
          failedSteps,
          skippedSteps: totalSteps - passedSteps - failedSteps,
          steps: result.steps.map((s) => ({ stepId: s.stepId, status: s.status, durationMs: s.durationMs })),
        };
        await fs.writeFile(
          path.join(outputDirectory, 'test-summary.json'),
          JSON.stringify(testSummary, null, 2),
          'utf-8',
        );
      }
    } catch (err) {
      // Phase output writing is best-effort; do not fail the overall result
      const msg = err instanceof Error ? err.message : String(err);
      console.warn(`[executor] Failed to write phase outputs for ${phase}: ${msg}`);
    }
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

