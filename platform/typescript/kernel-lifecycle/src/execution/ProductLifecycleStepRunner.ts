import type {
  ProductArtifact,
  ProductLifecycleCoverageResults,
  ProductLifecycleManifestRefs,
  ProductLifecycleStep,
  ProductLifecycleStepResult,
  ProductLifecycleTestResults,
  ExecutionLogger,
} from '../domain/ProductLifecyclePhase.js';

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
  steps?: readonly ProductLifecycleStepResult[];
  artifacts?: readonly AdapterArtifact[];
  testResults?: ProductLifecycleTestResults;
  coverageResults?: ProductLifecycleCoverageResults;
  durationMs?: number;
  failure?: {
    stepId: string;
    message: string;
    cause?: string;
  };
  warnings?: readonly string[];
  stdout?: string;
  stderr?: string;
  manifestRefs?: ProductLifecycleManifestRefs;
  evidenceRefs?: readonly string[];
}

export interface AdapterArtifact {
  id?: string;
  surface?: string;
  type?: string;
  path: string;
  fingerprint?: string;
  producedBy?: string;
  sizeBytes?: number;
  metadata?: Record<string, unknown>;
  image?: string;
  tag?: string;
  digest?: string;
  localImageId?: string;
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

      const required = context.required ?? true;

      // Fail closed: a non-dry-run required step must not silently skip.
      if (result.status === 'skipped' && !context.dryRun && required) {
        const stepResult: ProductLifecycleStepResult = {
          stepId: step.id,
          phase: step.phase,
          surface: step.surface,
          adapter: step.adapter,
          status: 'failed',
          exitCode: 1,
          durationMs: Date.now() - startTime,
          stderr: result.stderr ?? result.failure?.cause ?? `Required step ${step.id} was skipped`,
          artifacts: this.normalizeArtifacts(step, result.artifacts ?? []),
          ...(result.warnings !== undefined ? { warnings: [...result.warnings] } : {}),
          ...(result.manifestRefs !== undefined ? { manifestRefs: result.manifestRefs } : {}),
        };

        context.logger.error(`Step ${step.id} returned skipped for a non-dry-run required step`, {
          phase: step.phase,
          surface: step.surface,
          adapter: step.adapter,
        });

        return stepResult;
      }

      const completedStatus = this.resolveCompletedStatus(result.status);
      const stdout = result.stdout ?? this.summarizeStepOutput(result.steps, 'stdout');
      const stderr =
        result.stderr ??
        result.failure?.cause ??
        result.failure?.message ??
        this.summarizeStepOutput(result.steps, 'stderr');
      const stepResult: ProductLifecycleStepResult = {
        stepId: step.id,
        phase: step.phase,
        surface: step.surface,
        adapter: step.adapter,
        status: completedStatus,
        exitCode: completedStatus === 'failed' ? 1 : 0,
        ...(stdout !== undefined ? { stdout } : {}),
        ...(stderr !== undefined ? { stderr } : {}),
        durationMs: result.durationMs ?? Date.now() - startTime,
        artifacts: this.normalizeArtifacts(step, result.artifacts ?? []),
        ...(result.testResults !== undefined ? { testResults: result.testResults } : {}),
        ...(result.coverageResults !== undefined ? { coverageResults: result.coverageResults } : {}),
        ...(result.manifestRefs !== undefined ? { manifestRefs: result.manifestRefs } : {}),
        ...(result.warnings !== undefined ? { warnings: [...result.warnings] } : {}),
        ...(result.evidenceRefs !== undefined ? { evidenceRefs: [...result.evidenceRefs] } : {}),
        ...(result.failure !== undefined
          ? { errors: [result.failure.message, ...(result.failure.cause !== undefined ? [result.failure.cause] : [])] }
          : {}),
      };

      context.logger.info(`Step ${step.id} completed`, {
        status: stepResult.status,
        durationMs: stepResult.durationMs,
      });

      return stepResult;
    } catch (error) {
      const stepResult: ProductLifecycleStepResult = {
        stepId: step.id,
        phase: step.phase,
        surface: step.surface,
        adapter: step.adapter,
        status: 'failed',
        exitCode: 1,
        stderr: error instanceof Error ? error.message : String(error),
        durationMs: Date.now() - startTime,
        errors: [error instanceof Error ? error.message : String(error)],
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

  private resolveCompletedStatus(status: AdapterResult['status']): ProductLifecycleStepResult['status'] {
    return status;
  }

  private summarizeStepOutput(
    steps: readonly ProductLifecycleStepResult[] | undefined,
    key: 'stdout' | 'stderr',
  ): string | undefined {
    const output = steps
      ?.map((step) => step[key])
      .filter((value): value is string => typeof value === 'string' && value.length > 0)
      .join('\n');
    return output && output.length > 0 ? output : undefined;
  }

  private normalizeArtifacts(
    step: ProductLifecycleStep,
    artifacts: readonly AdapterArtifact[],
  ): ProductArtifact[] {
    return artifacts.map((artifact, index) => ({
      id: artifact.id ?? `${step.id}-artifact-${index}`,
      surface: artifact.surface ?? step.surface,
      type: artifact.type ?? this.inferArtifactType(step),
      path: artifact.path,
      fingerprint: artifact.fingerprint ?? artifact.digest ?? artifact.path,
      producedBy: artifact.producedBy ?? step.adapter,
      ...(artifact.sizeBytes !== undefined ? { sizeBytes: artifact.sizeBytes } : {}),
      ...(artifact.metadata !== undefined ? { metadata: artifact.metadata } : {}),
      ...(artifact.image !== undefined ? { image: artifact.image } : {}),
      ...(artifact.tag !== undefined ? { tag: artifact.tag } : {}),
      ...(artifact.digest !== undefined ? { digest: artifact.digest } : {}),
      ...(artifact.localImageId !== undefined ? { localImageId: artifact.localImageId } : {}),
    }));
  }

  private inferArtifactType(step: ProductLifecycleStep): string {
    if (step.stepKind === 'package') {
      return 'container-image';
    }
    if (step.stepKind === 'deploy') {
      return 'deployment-manifest';
    }
    if (step.stepKind === 'verify') {
      return 'verify-health-report';
    }
    if (step.phase === 'test') {
      return 'test-report';
    }
    return step.surface === 'web' ? 'static-web-bundle' : 'jvm-service';
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
  required?: boolean;
}
