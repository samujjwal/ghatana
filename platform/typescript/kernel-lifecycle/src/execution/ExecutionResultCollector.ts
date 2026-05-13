import { ProductLifecycleResult, ProductLifecycleStepResult, ProductArtifact, ProductGateResult, ProductLifecyclePhase } from '../domain/ProductLifecyclePhase.js';
import { ExecutionLogger } from '../domain/ProductLifecyclePhase.js';

/**
 * Execution result collector
 */
export class ExecutionResultCollector {
  private results: ProductLifecycleStepResult[] = [];
  private artifacts: ProductArtifact[] = [];
  private gateResults: ProductGateResult[] = [];
  private logger: ExecutionLogger;

  constructor(logger: ExecutionLogger) {
    this.logger = logger;
  }

  /**
   * Add a step result
   */
  addStepResult(result: ProductLifecycleStepResult): void {
    this.results.push(result);
    this.logger.debug(`Added step result for ${result.stepId}`, {
      status: result.status,
      durationMs: result.durationMs,
    });
  }

  /**
   * Add an artifact
   */
  addArtifact(artifact: ProductArtifact): void {
    this.artifacts.push(artifact);
    this.logger.debug(`Added artifact ${artifact.id}`, {
      type: artifact.type,
      surface: artifact.surface,
    });
  }

  /**
   * Add a gate result
   */
  addGateResult(result: ProductGateResult): void {
    this.gateResults.push(result);
    this.logger.debug(`Added gate result for ${result.gateId}`, {
      status: result.status,
    });
  }

  /**
   * Collect final result
   */
  collect(
    productId: string,
    phase: ProductLifecyclePhase,
    outputDirectory: string,
  ): ProductLifecycleResult {
    const status = this.determineOverallStatus();
    const failure = this.getFailureDetails();

    const result: ProductLifecycleResult = {
      schemaVersion: '1.0.0',
      productId,
      phase,
      status,
      startedAt: this.getStartTime(),
      completedAt: new Date().toISOString(),
      steps: this.results,
      gates: this.gateResults,
      artifacts: this.artifacts,
      outputDirectory,
    };

    if (failure) {
      result.failure = failure;
    }

    return result;
  }

  /**
   * Determine overall status
   */
  private determineOverallStatus(): 'succeeded' | 'failed' | 'skipped' {
    if (this.results.length === 0) {
      return 'skipped';
    }

    const hasFailure = this.results.some((r) => r.status === 'failed');
    if (hasFailure) {
      return 'failed';
    }

    const hasSuccess = this.results.some((r) => r.status === 'succeeded');
    if (hasSuccess) {
      return 'succeeded';
    }

    return 'skipped';
  }

  /**
   * Get failure details
   */
  private getFailureDetails():
    | { stepId: string; message: string; cause?: string }
    | undefined {
    const failedStep = this.results.find((r) => r.status === 'failed');
    if (!failedStep) {
      return undefined;
    }

    const failure: { stepId: string; message: string; cause?: string } = {
      stepId: failedStep.stepId,
      message: `Step ${failedStep.stepId} failed`,
    };

    if (failedStep.stderr !== undefined) {
      failure.cause = failedStep.stderr;
    }

    return failure;
  }

  /**
   * Get start time (estimated from first result)
   */
  private getStartTime(): string {
    if (this.results.length === 0) {
      return new Date().toISOString();
    }

    // Estimate start time by subtracting total duration from now
    const totalDuration = this.results.reduce((sum, r) => sum + r.durationMs, 0);
    return new Date(Date.now() - totalDuration).toISOString();
  }

  /**
   * Reset collector state
   */
  reset(): void {
    this.results = [];
    this.artifacts = [];
    this.gateResults = [];
    this.logger.debug('Reset execution result collector');
  }

  /**
   * Get current results
   */
  getCurrentResults(): {
    steps: ProductLifecycleStepResult[];
    artifacts: ProductArtifact[];
    gateResults: ProductGateResult[];
  } {
    return {
      steps: this.results,
      artifacts: this.artifacts,
      gateResults: this.gateResults,
    };
  }
}
