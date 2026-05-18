import { describe, expect, it } from "vitest";
import {
  KernelHealthSnapshotSchema,
  KernelLifecycleHealthSnapshotSchema,
} from "../KernelHealthSnapshot.js";

describe("KernelHealthSnapshot", () => {
  it("validates lifecycle, product, provider, gate, and deployment snapshots", () => {
    const common = {
      tenantId: "tenant-a",
      workspaceId: "workspace-a",
      observedAt: "2026-01-01T00:00:00.000Z",
      status: "healthy",
      evidenceRefs: ["datacloud://evidence/run-1"],
    };

    expect(
      KernelHealthSnapshotSchema.parse({
        ...common,
        snapshotId: "life-1",
        kind: "lifecycle",
        productUnitId: "product-1",
        runId: "run-1",
        phase: "build",
      }).kind,
    ).toBe("lifecycle");
    expect(
      KernelHealthSnapshotSchema.parse({
        ...common,
        snapshotId: "product-1",
        kind: "product",
        productUnitId: "product-1",
      }).kind,
    ).toBe("product");
    expect(
      KernelHealthSnapshotSchema.parse({
        ...common,
        snapshotId: "provider-1",
        kind: "provider",
        providerId: "data-cloud-runtime-truth",
        mode: "platform",
      }).kind,
    ).toBe("provider");
    expect(
      KernelHealthSnapshotSchema.parse({
        ...common,
        snapshotId: "gate-1",
        kind: "gate",
        productUnitId: "product-1",
        runId: "run-1",
        gateId: "security",
      }).kind,
    ).toBe("gate");
    expect(
      KernelHealthSnapshotSchema.parse({
        ...common,
        snapshotId: "deploy-1",
        kind: "deployment",
        productUnitId: "product-1",
        runId: "run-1",
        deploymentId: "deployment-1",
      }).kind,
    ).toBe("deployment");
  });

  it("requires degraded lifecycle snapshots to include a reason code", () => {
    expect(() =>
      KernelLifecycleHealthSnapshotSchema.parse({
        snapshotId: "life-1",
        tenantId: "tenant-a",
        workspaceId: "workspace-a",
        observedAt: "2026-01-01T00:00:00.000Z",
        status: "degraded",
        kind: "lifecycle",
        productUnitId: "product-1",
        runId: "run-1",
        phase: "verify",
      }),
    ).toThrow(/reason code/i);
  });

  it("links snapshots to lifecycle run and artifact evidence", () => {
    const snapshot = KernelLifecycleHealthSnapshotSchema.parse({
      snapshotId: "life-1",
      tenantId: "tenant-a",
      workspaceId: "workspace-a",
      observedAt: "2026-01-01T00:00:00.000Z",
      status: "healthy",
      kind: "lifecycle",
      productUnitId: "product-1",
      runId: "run-1",
      phase: "verify",
      links: {
        lifecycleRunRef: "lifecycle://run-1",
        artifactManifestRef: "artifact-manifest://run-1",
        verifyHealthReportRef: "health://run-1",
      },
    });

    expect(snapshot.links.lifecycleRunRef).toBe("lifecycle://run-1");
    expect(snapshot.links.artifactManifestRef).toBe(
      "artifact-manifest://run-1",
    );
  });
});
