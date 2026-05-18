import { describe, expect, it } from "vitest";
import type { KernelHealthSnapshot } from "../../health/KernelHealthSnapshot.js";
import { ScopedRuntimeTruthIndex } from "../RuntimeTruthQuery.js";

describe("ScopedRuntimeTruthIndex", () => {
  it("queries run, product, deployment, and provider truth within tenant scope", () => {
    const snapshots: readonly KernelHealthSnapshot[] = [
      lifecycle("life-1", "tenant-a", "workspace-a", "product-1", "run-1"),
      deployment(
        "deploy-1",
        "tenant-a",
        "workspace-a",
        "product-1",
        "run-1",
        "deployment-1",
      ),
      provider(
        "provider-1",
        "tenant-a",
        "workspace-a",
        "data-cloud-runtime-truth",
      ),
      lifecycle("life-other", "tenant-b", "workspace-a", "product-1", "run-2"),
    ];
    const index = new ScopedRuntimeTruthIndex(snapshots);
    const scope = { tenantId: "tenant-a", workspaceId: "workspace-a" };

    expect(
      index.getRunTruth(scope, "run-1").map((snapshot) => snapshot.snapshotId),
    ).toEqual(["life-1", "deploy-1"]);
    expect(
      index
        .getProductTruth(scope, "product-1")
        .map((snapshot) => snapshot.snapshotId),
    ).toEqual(["life-1", "deploy-1"]);
    expect(
      index
        .getDeploymentTruth(scope, "deployment-1")
        .map((snapshot) => snapshot.snapshotId),
    ).toEqual(["deploy-1"]);
    expect(
      index
        .getProviderTruth(scope, "data-cloud-runtime-truth")
        .map((snapshot) => snapshot.snapshotId),
    ).toEqual(["provider-1"]);
  });

  it("enforces tenant scope for product truth", () => {
    const index = new ScopedRuntimeTruthIndex([
      lifecycle("life-a", "tenant-a", "workspace-a", "product-1", "run-1"),
      lifecycle("life-b", "tenant-b", "workspace-a", "product-1", "run-2"),
    ]);

    expect(
      index.getProductTruth(
        { tenantId: "tenant-a", workspaceId: "workspace-a" },
        "product-1",
      ),
    ).toHaveLength(1);
    expect(
      index.getProductTruth(
        { tenantId: "tenant-a", workspaceId: "workspace-a" },
        "product-1",
      )[0]?.snapshotId,
    ).toBe("life-a");
  });
});

function lifecycle(
  snapshotId: string,
  tenantId: string,
  workspaceId: string,
  productUnitId: string,
  runId: string,
): KernelHealthSnapshot {
  return {
    kind: "lifecycle",
    snapshotId,
    tenantId,
    workspaceId,
    productUnitId,
    runId,
    phase: "build",
    observedAt: "2026-01-01T00:00:00.000Z",
    status: "healthy",
    evidenceRefs: [`lifecycle://${runId}`],
    links: { lifecycleRunRef: `lifecycle://${runId}` },
  };
}

function deployment(
  snapshotId: string,
  tenantId: string,
  workspaceId: string,
  productUnitId: string,
  runId: string,
  deploymentId: string,
): KernelHealthSnapshot {
  return {
    kind: "deployment",
    snapshotId,
    tenantId,
    workspaceId,
    productUnitId,
    runId,
    deploymentId,
    observedAt: "2026-01-01T00:01:00.000Z",
    status: "healthy",
    evidenceRefs: [`deployment://${deploymentId}`],
    links: { deploymentManifestRef: `deployment://${deploymentId}` },
  };
}

function provider(
  snapshotId: string,
  tenantId: string,
  workspaceId: string,
  providerId: string,
): KernelHealthSnapshot {
  return {
    kind: "provider",
    snapshotId,
    tenantId,
    workspaceId,
    providerId,
    mode: "platform",
    observedAt: "2026-01-01T00:02:00.000Z",
    status: "healthy",
    evidenceRefs: [`provider://${providerId}`],
    links: {},
  };
}
