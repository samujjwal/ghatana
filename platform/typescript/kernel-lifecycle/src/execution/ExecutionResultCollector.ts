import {
  ProductLifecycleResult,
  ProductLifecycleStepResult,
  ProductArtifact,
  ProductGateResult,
  ProductLifecyclePhase,
  ProductLifecycleManifestRefs,
  ProductLifecycleApprovalRef,
} from "../domain/ProductLifecyclePhase.js";
import { ExecutionLogger } from "../domain/ProductLifecyclePhase.js";
import type { KernelProviderMode } from "@ghatana/kernel-product-contracts";

export interface ExecutionResultCollectionMetadata {
  readonly correlationId?: string;
  readonly providerMode?: KernelProviderMode;
  readonly productUnitRef?: string;
  readonly lifecycleProfile?: string;
  readonly environment?: string;
  readonly sourceRef?: string;
  readonly requestedPhases?: readonly ProductLifecyclePhase[];
  readonly executedPhases?: readonly ProductLifecyclePhase[];
  readonly skippedPhases?: readonly ProductLifecyclePhase[];
  readonly blockedPhases?: readonly ProductLifecyclePhase[];
  readonly manifestRefs?: ProductLifecycleManifestRefs;
  readonly eventsRef?: string;
  readonly healthSnapshotRef?: string;
  readonly approvalRefs?: readonly ProductLifecycleApprovalRef[];
}

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
    runId: string = `run-${Date.now()}`,
    metadata: ExecutionResultCollectionMetadata = {},
  ): ProductLifecycleResult {
    const status = this.determineOverallStatus();
    const failure = this.getFailureDetails();

    const result: ProductLifecycleResult = {
      schemaVersion: "1.0.0",
      runId,
      ...(metadata.correlationId !== undefined
        ? { correlationId: metadata.correlationId }
        : {}),
      ...(metadata.providerMode !== undefined
        ? { providerMode: metadata.providerMode }
        : {}),
      productId,
      ...(metadata.productUnitRef !== undefined
        ? { productUnitRef: metadata.productUnitRef }
        : {}),
      phase,
      ...(metadata.lifecycleProfile !== undefined
        ? { lifecycleProfile: metadata.lifecycleProfile }
        : {}),
      ...(metadata.environment !== undefined
        ? { environment: metadata.environment }
        : {}),
      ...(metadata.sourceRef !== undefined
        ? { sourceRef: metadata.sourceRef }
        : {}),
      ...(metadata.requestedPhases !== undefined
        ? { requestedPhases: metadata.requestedPhases }
        : {}),
      ...(metadata.executedPhases !== undefined
        ? { executedPhases: metadata.executedPhases }
        : {}),
      ...(metadata.skippedPhases !== undefined
        ? { skippedPhases: metadata.skippedPhases }
        : {}),
      ...(metadata.blockedPhases !== undefined
        ? { blockedPhases: metadata.blockedPhases }
        : {}),
      status,
      startedAt: this.getStartTime(),
      completedAt: new Date().toISOString(),
      steps: this.results,
      gates: this.gateResults,
      artifacts: this.artifacts,
      ...(metadata.manifestRefs !== undefined
        ? { manifestRefs: metadata.manifestRefs }
        : {}),
      ...(metadata.eventsRef !== undefined
        ? { eventsRef: metadata.eventsRef }
        : {}),
      ...(metadata.healthSnapshotRef !== undefined
        ? { healthSnapshotRef: metadata.healthSnapshotRef }
        : {}),
      ...(metadata.approvalRefs !== undefined
        ? { approvalRefs: metadata.approvalRefs }
        : {}),
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
  private determineOverallStatus(): "succeeded" | "failed" | "skipped" {
    if (this.results.length === 0) {
      return "skipped";
    }

    const hasFailure = this.results.some((r) => r.status === "failed");
    if (hasFailure) {
      return "failed";
    }

    const hasSuccess = this.results.some((r) => r.status === "succeeded");
    if (hasSuccess) {
      return "succeeded";
    }

    return "skipped";
  }

  /**
   * Get failure details
   */
  private getFailureDetails():
    | {
        reasonCode: "adapter-failed";
        stepId: string;
        message: string;
        cause?: string;
      }
    | undefined {
    const failedStep = this.results.find((r) => r.status === "failed");
    if (!failedStep) {
      return undefined;
    }

    const failure: {
      reasonCode: "adapter-failed";
      stepId: string;
      message: string;
      cause?: string;
    } = {
      stepId: failedStep.stepId,
      message: `Step ${failedStep.stepId} failed`,
      reasonCode: "adapter-failed",
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
    const totalDuration = this.results.reduce(
      (sum, r) => sum + r.durationMs,
      0,
    );
    return new Date(Date.now() - totalDuration).toISOString();
  }

  /**
   * Reset collector state
   */
  reset(): void {
    this.results = [];
    this.artifacts = [];
    this.gateResults = [];
    this.logger.debug("Reset execution result collector");
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
