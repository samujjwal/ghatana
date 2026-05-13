import { ProductLifecycleStep, ProductLifecycleStepResult, ExecutionLogger } from '../domain/ProductLifecyclePhase.js';

/**
 * Adapter registry interface
 */
export interface AdapterRegistry {
  getAdapter(adapterId: string): Adapter;
}

/**
 * Adapter interface
 */
export interface Adapter {
  execute(context: AdapterContext): Promise<AdapterResult>;
}

/**
 * Adapter result
 */
export interface AdapterResult {
  status: 'succeeded' | 'failed' | 'skipped';
}

/**
 * Adapter context
 */
export interface AdapterContext {
  productId: string;
  phase: string;
  surface: {
    type: string;
    adapter: string;
    path: string;
  };
  environment: string | undefined;
  sourceRef: string | undefined;
  dryRun: boolean;
  surfaceConfig: Record<string, unknown>;
  phaseConfig: Record<string, unknown>;
  logger: ExecutionLogger;
  metadata: Record<string, unknown> | undefined;
  outputDir: string | undefined;
}

/**
 * Product lifecycle step runner
 */
export class ProductLifecycleStepRunner {
  private adapterRegistry: AdapterRegistry;

  constructor(adapterRegistry: AdapterRegistry) {
    this.adapterRegistry = adapterRegistry;
  }

  /**
   * Run a single lifecycle step
   */
  async run(
    step: ProductLifecycleStep,
    context: StepContext,
  ): Promise<ProductLifecycleStepResult> {
    const startTime = Date.now();
    context.logger.info(`Running step: ${step.id}`, {
      phase: step.phase,
      surface: step.surface,
      adapter: step.adapter,
    });

    try {
      const adapter = this.adapterRegistry.getAdapter(step.adapter);
      const adapterContext = this.buildAdapterContext(step, context);

      const result = await adapter.execute(adapterContext);

      const stepResult: ProductLifecycleStepResult = {
        stepId: step.id,
        status: result.status === 'succeeded' ? 'succeeded' : 'failed',
        exitCode: result.status === 'succeeded' ? 0 : 1,
        durationMs: Date.now() - startTime,
      };

      context.logger.info(`Step ${step.id} completed`, {
        status: stepResult.status,
        durationMs: stepResult.durationMs,
      });

      return stepResult;
    } catch (error) {
      const stepResult: ProductLifecycleStepResult = {
        stepId: step.id,
        status: 'failed',
        exitCode: 1,
        durationMs: Date.now() - startTime,
      };

      context.logger.error(`Step ${step.id} failed`, {
        error: error instanceof Error ? error.message : String(error),
      });

      return stepResult;
    }
  }

  /**
   * Run multiple steps in parallel
   */
  async runParallel(
    steps: ProductLifecycleStep[],
    context: StepContext,
  ): Promise<ProductLifecycleStepResult[]> {
    const promises = steps.map((step) => this.run(step, context));
    return Promise.all(promises);
  }

  /**
   * Run multiple steps sequentially
   */
  async runSequential(
    steps: ProductLifecycleStep[],
    context: StepContext,
  ): Promise<ProductLifecycleStepResult[]> {
    const results: ProductLifecycleStepResult[] = [];

    for (const step of steps) {
      const result = await this.run(step, context);
      results.push(result);

      if (result.status === 'failed') {
        context.logger.warn(`Stopping sequential execution due to failure in step ${step.id}`);
        break;
      }
    }

    return results;
  }

  /**
   * Build adapter context from step context
   */
  private buildAdapterContext(step: ProductLifecycleStep, context: StepContext): AdapterContext {
    return {
      productId: context.productId,
      phase: step.phase,
      surface: {
        type: context.surfaceType,
        adapter: step.adapter,
        path: context.surfacePath,
      },
      environment: context.environment,
      sourceRef: context.sourceRef,
      dryRun: context.dryRun,
      surfaceConfig: context.surfaceConfig,
      phaseConfig: context.phaseConfig,
      logger: context.logger,
      metadata: context.metadata,
      outputDir: context.outputDirectory,
    };
  }
}

/**
 * Step context
 */
export interface StepContext {
  productId: string;
  surfaceType: string;
  surfacePath: string;
  environment?: string;
  sourceRef?: string;
  dryRun: boolean;
  surfaceConfig: Record<string, unknown>;
  phaseConfig: Record<string, unknown>;
  logger: ExecutionLogger;
  metadata?: Record<string, unknown>;
  outputDirectory: string;
}
