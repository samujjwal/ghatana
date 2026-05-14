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

import type { TelemetryProvider } from "@ghatana/kernel-product-contracts";
import type {
  KernelEventMetadata,
  KernelLifecycleEvent,
  KernelGateEvent,
  GateEventPayload,
  KernelArtifactEvent,
  ArtifactEventPayload,
  KernelDeploymentEvent,
  DeploymentEventPayload,
  KernelHealthEvent,
  HealthEventPayload,
  KernelAgentGovernanceEvent,
  AgentGovernanceEventPayload,
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
   * Whether to log events to console when telemetry provider is not available.
   */
  readonly enableConsoleLogging?: boolean;
}

/**
 * Kernel lifecycle event emitter.
 */
export class KernelLifecycleEventEmitter {
  private readonly telemetryProvider: TelemetryProvider | undefined;
  private readonly enableConsoleLogging: boolean;

  constructor(options: EventEmitterOptions = {}) {
    this.telemetryProvider = options.telemetryProvider;
    this.enableConsoleLogging = options.enableConsoleLogging ?? true;
  }

  private createMetadata(
    eventType: string,
    productUnitId: string,
    runId: string,
    phase: string,
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

  private emitEvent(event: KernelLifecycleEvent): void {
    if (this.telemetryProvider) {
      // Map KernelLifecycleEvent to TelemetryEvent
      const telemetryEvent = {
        eventId: event.metadata.eventId,
        eventType: event.metadata.eventType,
        timestamp: event.metadata.timestamp,
        productUnitId: event.metadata.productUnitId,
        payload: event.payload as Record<string, unknown>,
      };
      this.telemetryProvider.emitEvent(telemetryEvent).catch((error: unknown) => {
        console.error("Failed to emit event via telemetry provider:", error);
        // Fallback to console logging if provider fails
        if (this.enableConsoleLogging) {
          console.log(JSON.stringify(event, null, 2));
        }
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
    phase: string,
    correlationId?: string
  ): void {
    const metadata = this.createMetadata(
      "lifecycle.phase.start",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const event: KernelLifecycleEvent = {
      metadata,
      payload: {
        phase,
        status: "started",
        timestamp: metadata.timestamp,
      },
    };

    this.emitEvent(event);
  }

  /**
   * Emits a lifecycle phase complete event.
   */
  emitLifecyclePhaseComplete(
    productUnitId: string,
    runId: string,
    phase: string,
    status: "succeeded" | "failed" | "skipped",
    duration: number,
    correlationId?: string
  ): void {
    const metadata = this.createMetadata(
      "lifecycle.phase.complete",
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
        duration,
        timestamp: metadata.timestamp,
      },
    };

    this.emitEvent(event);
  }

  /**
   * Emits a gate evaluated event.
   */
  emitGateEvaluated(
    productUnitId: string,
    runId: string,
    phase: string,
    gateId: string,
    passed: boolean,
    reason: string,
    evidence: readonly string[],
    duration: number,
    correlationId?: string
  ): void {
    const metadata = this.createMetadata(
      "gate.evaluated",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const payload: GateEventPayload = {
      gateId,
      passed,
      reason,
      evidence,
      duration,
    };

    const event: KernelGateEvent = { metadata, payload };
    this.emitEvent(event);
  }

  /**
   * Emits an artifact produced event.
   */
  emitArtifactProduced(
    productUnitId: string,
    runId: string,
    phase: string,
    artifactId: string,
    artifactName: string,
    version: string,
    type: string,
    size: number,
    checksum: string,
    surfaceId: string,
    correlationId?: string
  ): void {
    const metadata = this.createMetadata(
      "artifact.produced",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const payload: ArtifactEventPayload = {
      artifactId,
      artifactName,
      version,
      type,
      size,
      checksum,
      surfaceId,
    };

    const event: KernelArtifactEvent = { metadata, payload };
    this.emitEvent(event);
  }

  /**
   * Emits a deployment complete event.
   */
  emitDeploymentComplete(
    productUnitId: string,
    runId: string,
    phase: string,
    deploymentId: string,
    environment: string,
    status: string,
    artifactIds: readonly string[],
    endpoints: readonly string[],
    duration: number,
    correlationId?: string
  ): void {
    const metadata = this.createMetadata(
      "deployment.complete",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const payload: DeploymentEventPayload = {
      deploymentId,
      environment,
      status,
      artifactIds,
      endpoints,
      duration,
    };

    const event: KernelDeploymentEvent = { metadata, payload };
    this.emitEvent(event);
  }

  /**
   * Emits a health check result event.
   */
  emitHealthCheckResult(
    productUnitId: string,
    runId: string,
    phase: string,
    checkId: string,
    checkName: string,
    status: "healthy" | "degraded" | "blocked" | "failed" | "skipped" | "unknown",
    message: string,
    duration: number,
    deploymentId?: string,
    environment?: string,
    correlationId?: string
  ): void {
    const metadata = this.createMetadata(
      "health.check.result",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const payload: HealthEventPayload = {
      checkId,
      checkName,
      status,
      message,
      duration,
    };

    // Conditionally add optional fields
    if (deploymentId !== undefined) {
      (payload as HealthEventPayload & { deploymentId: string }).deploymentId = deploymentId;
    }
    if (environment !== undefined) {
      (payload as HealthEventPayload & { environment: string }).environment = environment;
    }

    const event: KernelHealthEvent = { metadata, payload };
    this.emitEvent(event);
  }

  /**
   * Emits an agent governance event.
   */
  emitAgentGovernanceEvent(
    productUnitId: string,
    runId: string,
    phase: string,
    agentId: string,
    actionType: string,
    decision: string,
    reason: string,
    correlationId?: string,
    masteryState?: string,
    executionMode?: string,
    evidenceRefs?: readonly string[]
  ): void {
    const metadata = this.createMetadata(
      "agent.governance",
      productUnitId,
      runId,
      phase,
      correlationId
    );

    const payload: AgentGovernanceEventPayload = {
      agentId,
      actionType,
      decision,
      reason,
    };

    // Conditionally add optional fields
    if (masteryState !== undefined) {
      (payload as AgentGovernanceEventPayload & { masteryState: string }).masteryState = masteryState;
    }
    if (executionMode !== undefined) {
      (payload as AgentGovernanceEventPayload & { executionMode: string }).executionMode = executionMode;
    }
    if (evidenceRefs !== undefined) {
      (payload as AgentGovernanceEventPayload & { evidenceRefs: readonly string[] }).evidenceRefs = evidenceRefs;
    }

    const event: KernelAgentGovernanceEvent = { metadata, payload };
    this.emitEvent(event);
  }
}
