/**
 * LifecycleHealthAggregator - aggregates health snapshots across lifecycle phases and gates.
 *
 * This component aggregates health status across lifecycle operations, providing
 * a unified view of product health including phases, gates, deployments, and surfaces.
 *
 * @doc.type class
 * @doc.purpose Aggregates health snapshots across lifecycle phases and gates
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import type { HealthProvider } from "@ghatana/kernel-product-contracts";
import type {
  HealthStatus,
  LifecycleHealthSnapshot,
  PhaseHealthStatus,
  GateHealthSnapshot,
  GateEvaluationStatus,
  DeploymentHealthSnapshot,
  DeploymentHealthStatus,
  ProductUnitHealthSnapshot,
  SurfaceHealthStatus,
} from "@ghatana/kernel-product-contracts";

/**
 * Health aggregation options.
 */
export interface HealthAggregationOptions {
  /**
   * Health provider for health check data.
   */
  readonly healthProvider?: HealthProvider;
}

/**
 * Lifecycle health aggregator.
 */
export class LifecycleHealthAggregator {
  private readonly healthProvider: HealthProvider | undefined;

  constructor(options: HealthAggregationOptions = {}) {
    this.healthProvider = options.healthProvider;
  }

  private computeOverallStatus(
    statuses: readonly HealthStatus[]
  ): HealthStatus {
    if (statuses.length === 0) {
      return "unknown";
    }

    // Priority order for overall status
    if (statuses.some((s) => s === "blocked")) return "blocked";
    if (statuses.some((s) => s === "quarantined")) return "quarantined";
    if (statuses.some((s) => s === "failed")) return "failed";
    if (statuses.some((s) => s === "requires-approval")) return "requires-approval";
    if (statuses.some((s) => s === "requires-verification")) return "requires-verification";
    if (statuses.some((s) => s === "obsolete")) return "obsolete";
    if (statuses.some((s) => s === "degraded")) return "degraded";
    if (statuses.some((s) => s === "skipped")) return "skipped";
    if (statuses.some((s) => s === "unknown")) return "unknown";
    return "healthy";
  }

  /**
   * Aggregates lifecycle health for a product unit run.
   */
  async aggregateLifecycleHealth(
    productUnitId: string,
    runId: string,
    phases: readonly string[]
  ): Promise<LifecycleHealthSnapshot> {
    const phaseHealthStatuses: PhaseHealthStatus[] = [];

    for (const phase of phases) {
      // If health provider is available, get actual health status
      // Otherwise, use minimal placeholder
      const phaseStatus: PhaseHealthStatus = this.healthProvider
        ? await this.getPhaseHealthFromProvider(productUnitId, runId, phase)
        : this.createPlaceholderPhaseHealth(phase);

      phaseHealthStatuses.push(phaseStatus);
    }

    const overallStatus = this.computeOverallStatus(
      phaseHealthStatuses.map((p) => p.status)
    );
    const totalDuration = phaseHealthStatuses.reduce((sum, p) => sum + p.duration, 0);

    const snapshot: LifecycleHealthSnapshot = {
      productUnitId,
      runId,
      status: overallStatus,
      phases: phaseHealthStatuses,
      totalDuration,
      snapshotAt: new Date().toISOString(),
    };

    // Only add currentPhase if we have a current phase
    // In a real implementation, this would be determined from the lifecycle state
    return snapshot;
  }

  private async getPhaseHealthFromProvider(
    _productUnitId: string,
    _runId: string,
    phase: string
  ): Promise<PhaseHealthStatus> {
    // In a full implementation, this would query the health provider
    // For now, return a placeholder
    return this.createPlaceholderPhaseHealth(phase);
  }

  private createPlaceholderPhaseHealth(phase: string): PhaseHealthStatus {
    return {
      phase,
      status: "unknown",
      message: "Health status not available",
      duration: 0,
      completedAt: new Date().toISOString(),
    };
  }

  /**
   * Aggregates gate health for a lifecycle phase.
   */
  async aggregateGateHealth(
    productUnitId: string,
    runId: string,
    phase: string,
    gateIds: readonly string[]
  ): Promise<GateHealthSnapshot> {
    const gateStatuses: GateEvaluationStatus[] = [];

    for (const gateId of gateIds) {
      // If health provider is available, get actual gate evaluation status
      // Otherwise, use minimal placeholder
      const gateStatus: GateEvaluationStatus = this.healthProvider
        ? await this.getGateEvaluationFromProvider(productUnitId, runId, phase, gateId)
        : this.createPlaceholderGateEvaluation(gateId);

      gateStatuses.push(gateStatus);
    }

    const overallStatus = this.computeOverallStatus(
      gateStatuses.map((g) => (g.passed ? "healthy" : "failed"))
    );

    return {
      productUnitId,
      runId,
      phase,
      status: overallStatus,
      gates: gateStatuses,
      snapshotAt: new Date().toISOString(),
    };
  }

  private async getGateEvaluationFromProvider(
    _productUnitId: string,
    _runId: string,
    _phase: string,
    gateId: string
  ): Promise<GateEvaluationStatus> {
    // In a full implementation, this would query the health provider
    // For now, return a placeholder
    return this.createPlaceholderGateEvaluation(gateId);
  }

  private createPlaceholderGateEvaluation(gateId: string): GateEvaluationStatus {
    return {
      gateId,
      passed: false,
      reason: "Gate evaluation not available",
      evaluatedAt: new Date().toISOString(),
      duration: 0,
    };
  }

  /**
   * Aggregates deployment health for an environment.
   */
  async aggregateDeploymentHealth(
    productUnitId: string,
    runId: string,
    deploymentId: string,
    environment: string
  ): Promise<DeploymentHealthSnapshot> {
    // If health provider is available, get actual deployment health
    // Otherwise, use minimal placeholder
    const deploymentStatus: DeploymentHealthStatus = this.healthProvider
      ? await this.getDeploymentHealthFromProvider(deploymentId, environment)
      : this.createPlaceholderDeploymentHealth(deploymentId, environment);

    const overallStatus = deploymentStatus.status;

    return {
      productUnitId,
      runId,
      status: overallStatus,
      deployments: [deploymentStatus],
      snapshotAt: new Date().toISOString(),
    };
  }

  private async getDeploymentHealthFromProvider(
    deploymentId: string,
    environment: string
  ): Promise<DeploymentHealthStatus> {
    // In a full implementation, this would query the health provider
    // For now, return a placeholder
    return this.createPlaceholderDeploymentHealth(deploymentId, environment);
  }

  private createPlaceholderDeploymentHealth(
    deploymentId: string,
    environment: string
  ): DeploymentHealthStatus {
    return {
      deploymentId,
      environment,
      status: "unknown",
      message: "Deployment health not available",
      deployedAt: new Date().toISOString(),
      endpoints: [],
    };
  }

  /**
   * Aggregates product unit health across all surfaces.
   */
  async aggregateProductUnitHealth(
    productUnitId: string,
    surfaces: readonly { id: string; type: string }[]
  ): Promise<ProductUnitHealthSnapshot> {
    const surfaceHealthStatuses: SurfaceHealthStatus[] = [];

    for (const surface of surfaces) {
      // If health provider is available, get actual surface health
      // Otherwise, use minimal placeholder
      const surfaceStatus: SurfaceHealthStatus = this.healthProvider
        ? await this.getSurfaceHealthFromProvider(productUnitId, surface.id)
        : this.createPlaceholderSurfaceHealth(surface.id, surface.type);

      surfaceHealthStatuses.push(surfaceStatus);
    }

    const overallStatus = this.computeOverallStatus(
      surfaceHealthStatuses.map((s) => s.status)
    );

    const snapshot: ProductUnitHealthSnapshot = {
      productUnitId,
      status: overallStatus,
      surfaces: surfaceHealthStatuses,
      lifecycleStatus: "unknown",
      snapshotAt: new Date().toISOString(),
    };

    // Only add lastLifecycleRun if we have actual data
    // In a real implementation, this would be determined from the lifecycle history
    return snapshot;
  }

  private async getSurfaceHealthFromProvider(
    _productUnitId: string,
    surfaceId: string
  ): Promise<SurfaceHealthStatus> {
    // In a full implementation, this would query the health provider
    // For now, return a placeholder
    return this.createPlaceholderSurfaceHealth(surfaceId, "unknown");
  }

  private createPlaceholderSurfaceHealth(
    surfaceId: string,
    _surfaceType: string
  ): SurfaceHealthStatus {
    return {
      surfaceId,
      status: "unknown",
      message: "Surface health not available",
      lastUpdated: new Date().toISOString(),
    };
  }
}
