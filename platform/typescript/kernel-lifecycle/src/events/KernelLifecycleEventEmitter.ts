/**
 * KernelLifecycleEventEmitter - emits lifecycle truth events for observability and governance.
 *
 * This component emits lifecycle events using the TelemetryProvider when available,
 * otherwise logs to console. Events follow the contracts defined in kernel-product-contracts.
 *
 * @doc.type class
 * @doc.purpose Emits lifecycle truth events for observability and governance
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import type {
  LifecycleEventProvider,
  LifecycleProviderResult,
  TelemetryProvider,
} from "@ghatana/kernel-product-contracts";
import type {
  KernelEventMetadata,
  KernelLifecycleEvent,
  KernelLifecycleEventType,
  ProductLifecyclePhase,
} from "@ghatana/kernel-product-contracts";

/**
 * Event emitter options.
 */
export interface EventEmitterOptions {
  /**
   * Telemetry provider for event emission.
   */
  readonly telemetryProvider?: TelemetryProvider;

  /**
   * Lifecycle event provider for durable lifecycle truth.
   */
  readonly lifecycleEventProvider?: LifecycleEventProvider;

  /**
   * Whether to log events to console when telemetry provider is not available.
   */
  readonly enableConsoleLogging?: boolean;

  /**
   * Whether provider writes are required by this emitter.
   */
  readonly lifecycleEventWritesRequired?: boolean;

  readonly logger?: {
    warn(message: string, meta?: Record<string, unknown>): void;
    error(message: string, meta?: Record<string, unknown>): void;
    info?(message: string, meta?: Record<string, unknown>): void;
  };
}

/**
 * Kernel lifecycle event emitter.
 */
export class KernelLifecycleEventEmitter {
  private readonly telemetryProvider: TelemetryProvider | undefined;
  private readonly lifecycleEventProvider: LifecycleEventProvider | undefined;
  private readonly enableConsoleLogging: boolean;
  private readonly lifecycleEventWritesRequired: boolean;
  private readonly logger: NonNullable<EventEmitterOptions["logger"]>;

  constructor(options: EventEmitterOptions = {}) {
    this.telemetryProvider = options.telemetryProvider;
    this.lifecycleEventProvider = options.lifecycleEventProvider;
    this.enableConsoleLogging = options.enableConsoleLogging ?? true;
    this.lifecycleEventWritesRequired = options.lifecycleEventWritesRequired ?? true;
    this.logger = options.logger ?? {
      warn: (message, meta) => {
        if (this.enableConsoleLogging) {
          console.warn(message, meta);
        }
      },
      error: (message, meta) => {
        if (this.enableConsoleLogging) {
          console.error(message, meta);
        }
      },
    };
  }

  private createMetadata(
    eventType: KernelLifecycleEventType,
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    correlationId?: string
  ): KernelEventMetadata {
    return {
      eventId: this.generateEventId(),
      schemaVersion: "1.0.0",
      eventType,
      productUnitId,
      runId,
      phase,
      timestamp: new Date().toISOString(),
      source: "kernel-lifecycle",
      correlationId: correlationId ?? this.generateCorrelationId(),
    };
  }

  private generateEventId(): string {
    return `evt-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
  }

  private generateCorrelationId(): string {
    return `corr-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
  }

  private async emitEvent(event: KernelLifecycleEvent): Promise<LifecycleProviderResult[]> {
    const telemetryPromise = this.emitTelemetryEvent(event);
    const providerResults: LifecycleProviderResult[] = [];
    if (this.lifecycleEventProvider) {
      try {
        const result = await this.lifecycleEventProvider.appendEvent(event, {
          required: this.lifecycleEventWritesRequired,
          correlationId: event.metadata.correlationId,
        });
        providerResults.push(result);
        if (!result.success) {
          const errorMessage = result.error ?? "unknown provider error";
          if (this.lifecycleEventWritesRequired) {
            throw new Error(`Required lifecycle event provider write failed: ${errorMessage}`);
          }
          this.logger.warn("Optional lifecycle event provider write failed", {
            correlationId: event.metadata.correlationId,
            eventId: event.metadata.eventId,
            error: errorMessage,
          });
        }
      } catch (error: unknown) {
        if (this.lifecycleEventWritesRequired) {
          throw error;
        }
        this.logger.warn("Optional lifecycle event provider write rejected", {
          correlationId: event.metadata.correlationId,
          eventId: event.metadata.eventId,
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }
    await telemetryPromise;
    return providerResults;
  }

  private async emitTelemetryEvent(event: KernelLifecycleEvent): Promise<void> {
    if (this.telemetryProvider) {
      // Map KernelLifecycleEvent to TelemetryEvent
      const telemetryEvent = {
        eventId: event.metadata.eventId,
        eventType: event.metadata.eventType,
        timestamp: event.metadata.timestamp,
        productUnitId: event.metadata.productUnitId,
        payload: event.payload as unknown as Record<string, unknown>,
      };
      this.telemetryProvider.emitEvent(telemetryEvent).catch((error: unknown) => {
        this.logger.warn("Failed to emit event via telemetry provider", {
          eventId: event.metadata.eventId,
          error: error instanceof Error ? error.message : String(error),
        });
      });
    } else if (this.enableConsoleLogging) {
      console.log(JSON.stringify(event, null, 2));
    }
  }

  /**
   * Emits a lifecycle phase start event.
   */
  emitLifecyclePhaseStart(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.phase.started",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        phase,
        status: "running",
        startedAt: metadata.timestamp,
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits a lifecycle phase complete event.
   */
  emitLifecyclePhaseComplete(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    status: "succeeded" | "failed" | "skipped",
    duration: number,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.phase.completed",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        phase,
        status,
        durationMs: duration,
        completedAt: metadata.timestamp,
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits a lifecycle step start event.
   */
  emitLifecycleStepStart(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    stepId: string,
    stepKind: string,
    surface: string,
    adapter: string,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.step.started",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        stepId,
        stepKind,
        surface,
        adapter,
        status: "running",
        startedAt: metadata.timestamp,
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits a lifecycle step complete event.
   */
  emitLifecycleStepComplete(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    stepId: string,
    stepKind: string,
    surface: string,
    adapter: string,
    status: "succeeded" | "failed" | "skipped",
    duration: number,
    evidenceRefs: readonly string[],
    correlationId?: string,
    exitCode?: number
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.step.completed",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        stepId,
        stepKind,
        surface,
        adapter,
        status,
        durationMs: duration,
        completedAt: metadata.timestamp,
        ...(exitCode !== undefined ? { exitCode } : {}),
        evidenceRefs,
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits a gate evaluated event.
   */
  emitGateEvaluated(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    gateId: string,
    passed: boolean,
    reason: string,
    evidence: readonly string[],
    duration: number,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.gate.evaluated",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        gateId,
        status: passed ? "passed" : "failed",
        required: true,
        reason,
        evidenceRefs: evidence,
        durationMs: duration,
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits an artifact produced event.
   */
  emitArtifactProduced(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    artifactId: string,
    artifactName: string,
    version: string,
    type: string,
    size: number,
    checksum: string,
    surfaceId: string,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.artifact.recorded",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        artifactId,
        artifactType: type,
        required: true,
        path: artifactName,
        fingerprint: checksum,
        evidenceRefs: [`surface:${surfaceId}`, `version:${version}`, `size:${size}`],
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits a deployment complete event.
   */
  emitDeploymentComplete(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    deploymentId: string,
    environment: string,
    status: "succeeded" | "failed" | "skipped",
    artifactIds: readonly string[],
    endpoints: readonly string[],
    duration: number,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.deployment.completed",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        deploymentId,
        environment,
        status,
        artifactIds,
        endpoints,
        durationMs: duration,
      },
    };

    return this.emitEvent(event);
  }

  /**
   * Emits a health check result event.
   */
  emitHealthCheckResult(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    checkId: string,
    checkName: string,
    status: "healthy" | "degraded" | "blocked" | "failed" | "skipped" | "unknown",
    message: string,
    duration: number,
    deploymentId?: string,
    environment?: string,
    correlationId?: string
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.health.checked",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        checkId,
        checkName,
        status,
        message,
        durationMs: duration,
        ...(deploymentId !== undefined ? { deploymentId } : {}),
        ...(environment !== undefined ? { environment } : {}),
      },
    };
    return this.emitEvent(event);
  }

  /**
   * Emits an agent governance event.
   */
  emitAgentGovernanceEvent(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    agentId: string,
    actionType: string,
    decision: "allowed" | "denied" | "requires-approval",
    reason: string,
    correlationId?: string,
    masteryState?: string,
    executionMode?: string,
    evidenceRefs?: readonly string[]
  ): Promise<LifecycleProviderResult[]> {
    const metadata = this.createMetadata(
      "lifecycle.agent.governance.evaluated",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        agentId,
        actionType,
        decision,
        reason,
        ...(masteryState !== undefined ? { masteryState } : {}),
        ...(executionMode !== undefined ? { executionMode } : {}),
        evidenceRefs: evidenceRefs ?? [],
      },
    };
    return this.emitEvent(event);
  }
}
