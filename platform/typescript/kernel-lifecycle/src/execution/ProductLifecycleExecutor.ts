import { spawnSync } from 'node:child_process';
import {
  LifecyclePlan,
  ProductLifecyclePhase,
  ProductLifecycleResult,
  ProductLifecycleStep,
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
 * Product lifecycle executor
 */
export class ProductLifecycleExecutor {
  private readonly stepRunner: ProductLifecycleStepRunner;
  private readonly resultCollector: ExecutionResultCollector;

  constructor(stepRunner: ProductLifecycleStepRunner, resultCollector: ExecutionResultCollector) {
    this.stepRunner = stepRunner;
    this.resultCollector = resultCollector;
  }

  async executePlan(plan: LifecyclePlan, options: ProductLifecycleExecutionOptions): Promise<ProductLifecycleResult> {
    return this.execute(plan.productId, plan.phase, plan.steps as ProductLifecycleStep[], options);
  }

  async execute(
    productId: string,
    phase: ProductLifecyclePhase,
    steps: ProductLifecycleStep[],
    options: ProductLifecycleExecutionOptions,
  ): Promise<ProductLifecycleResult> {
    this.resultCollector.reset();
    const logger = options.logger ?? new ConsoleExecutionLogger();

    const state = new Map<string, 'pending' | 'done' | 'failed' | 'skipped'>();
    for (const step of steps) {
      state.set(step.id, 'pending');
    }

    for (const step of steps) {
      const unmetDependency = step.dependsOn.find((dependencyId) => state.get(dependencyId) !== 'done');
      if (unmetDependency) {
        this.resultCollector.addStepResult({
          stepId: step.id,
          status: 'skipped',
          durationMs: 0,
        });
        state.set(step.id, 'skipped');
        continue;
      }

      const result = options.dryRun
        ? {
            stepId: step.id,
            status: 'skipped' as const,
            exitCode: 0,
            stdout: step.execution
              ? `[DRY-RUN] ${step.execution.command} ${step.execution.args.join(' ')}`
              : `[DRY-RUN] ${step.phase} phase for ${step.surface} via ${step.adapter}`,
            durationMs: 0,
          }
        : step.execution
          ? this.executeShellStep(step, options)
          : await this.executeAdapterStep(step, productId, options, logger);

      this.resultCollector.addStepResult(result);

      if (result.status === 'failed') {
        state.set(step.id, 'failed');
        continue;
      }

      state.set(step.id, result.status === 'succeeded' ? 'done' : 'skipped');
    }

    return this.resultCollector.collect(productId, phase, options.outputDirectory);
  }

  private executeShellStep(
    step: ProductLifecycleStep,
    options: ProductLifecycleExecutionOptions,
  ): { stepId: string; status: 'succeeded' | 'failed' | 'skipped'; exitCode?: number; stdout?: string; stderr?: string; durationMs: number } {
    if (!step.execution) {
      return {
        stepId: step.id,
        status: 'failed',
        exitCode: 1,
        stderr: 'Missing execution details',
        durationMs: 0,
      };
    }

    if (options.dryRun) {
      return {
        stepId: step.id,
        status: 'skipped',
        exitCode: 0,
        stdout: `[DRY-RUN] ${step.execution.command} ${step.execution.args.join(' ')}`,
        durationMs: 0,
      };
    }

    const startedAt = Date.now();
    const result = spawnSync(step.execution.command, step.execution.args, {
      cwd: step.execution.workingDirectory,
      shell: false,
      encoding: 'utf8',
    });

    return {
      stepId: step.id,
      status: result.status === 0 ? 'succeeded' : 'failed',
      exitCode: result.status ?? 1,
      stdout: result.stdout?.slice(0, 20000) ?? '',
      stderr: result.stderr?.slice(0, 20000) ?? '',
      durationMs: Date.now() - startedAt,
    };
  }

  private async executeAdapterStep(
    step: ProductLifecycleStep,
    productId: string,
    options: ProductLifecycleExecutionOptions,
    logger: ConsoleExecutionLogger,
  ) {
    const stepContext: StepContext = {
      productId,
      surfaceType: step.surface,
      surfacePath: step.surface,
      ...(options.environment ? { environment: options.environment } : {}),
      ...(options.sourceRef ? { sourceRef: options.sourceRef } : {}),
      dryRun: options.dryRun,
      surfaceConfig: {},
      phaseConfig: {},
      logger,
      outputDirectory: options.outputDirectory,
    };

    return this.stepRunner.run(step, stepContext);
  }
}
