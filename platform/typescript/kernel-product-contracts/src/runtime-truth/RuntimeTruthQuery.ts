import type { KernelHealthSnapshot } from "../health/KernelHealthSnapshot.js";

export interface RuntimeTruthScope {
  readonly tenantId: string;
  readonly workspaceId: string;
}

export interface RuntimeTruthQueryIndex {
  getRunTruth(
    scope: RuntimeTruthScope,
    runId: string,
  ): readonly KernelHealthSnapshot[];
  getProductTruth(
    scope: RuntimeTruthScope,
    productUnitId: string,
  ): readonly KernelHealthSnapshot[];
  getDeploymentTruth(
    scope: RuntimeTruthScope,
    deploymentId: string,
  ): readonly KernelHealthSnapshot[];
  getProviderTruth(
    scope: RuntimeTruthScope,
    providerId: string,
  ): readonly KernelHealthSnapshot[];
}

export class ScopedRuntimeTruthIndex implements RuntimeTruthQueryIndex {
  private readonly snapshots: readonly KernelHealthSnapshot[];

  constructor(snapshots: readonly KernelHealthSnapshot[]) {
    this.snapshots = [...snapshots].sort(
      (a, b) =>
        a.observedAt.localeCompare(b.observedAt) ||
        a.snapshotId.localeCompare(b.snapshotId),
    );
  }

  getRunTruth(
    scope: RuntimeTruthScope,
    runId: string,
  ): readonly KernelHealthSnapshot[] {
    return this.visibleTo(scope).filter(
      (snapshot) => "runId" in snapshot && snapshot.runId === runId,
    );
  }

  getProductTruth(
    scope: RuntimeTruthScope,
    productUnitId: string,
  ): readonly KernelHealthSnapshot[] {
    return this.visibleTo(scope).filter(
      (snapshot) =>
        "productUnitId" in snapshot && snapshot.productUnitId === productUnitId,
    );
  }

  getDeploymentTruth(
    scope: RuntimeTruthScope,
    deploymentId: string,
  ): readonly KernelHealthSnapshot[] {
    return this.visibleTo(scope).filter(
      (snapshot) =>
        "deploymentId" in snapshot && snapshot.deploymentId === deploymentId,
    );
  }

  getProviderTruth(
    scope: RuntimeTruthScope,
    providerId: string,
  ): readonly KernelHealthSnapshot[] {
    return this.visibleTo(scope).filter(
      (snapshot) =>
        "providerId" in snapshot && snapshot.providerId === providerId,
    );
  }

  private visibleTo(scope: RuntimeTruthScope): readonly KernelHealthSnapshot[] {
    return this.snapshots.filter(
      (snapshot) =>
        snapshot.tenantId === scope.tenantId &&
        snapshot.workspaceId === scope.workspaceId,
    );
  }
}
