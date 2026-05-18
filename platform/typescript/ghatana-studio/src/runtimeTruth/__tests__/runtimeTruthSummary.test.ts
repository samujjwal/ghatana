import { describe, expect, it } from "vitest";
import type { StudioLifecycleSnapshot } from "../../data/StudioLifecycleDataContext";
import { createStudioRuntimeTruthSummary } from "../runtimeTruthSummary";

const snapshot: StudioLifecycleSnapshot = {
  status: "ready",
  runtimeMode: "configured",
  availableProductUnits: [],
  lifecycleRuns: [],
  pendingApprovals: [],
  selectedRun: {
    runId: "run-1",
    correlationId: "corr-1",
    productUnitId: "digital-marketing",
    phase: "build",
    status: "healthy",
    eventsRef: "restricted://agent-evidence/corr-1",
    manifestRefs: {
      "artifact-manifest": "artifact-manifest://run-1",
      "deployment-manifest": "deployment-manifest://run-1",
    },
  },
  manifestLoadState: {
    gateResultManifest: { status: "loaded" },
    artifactManifest: { status: "loaded" },
    deploymentManifest: { status: "loaded" },
    verifyHealthReport: { status: "loaded" },
  },
  gateResultManifest: {
    schemaVersion: "1.0.0",
    productUnitId: "digital-marketing",
    runId: "run-1",
    gates: [
      {
        gateId: "release-policy",
        status: "failed",
        reason: "security-review-required",
        nextAction: "request-approval",
      },
    ],
  },
  artifactManifest: {
    schemaVersion: "1.0.0",
    productId: "digital-marketing",
    providerMode: "platform",
    phase: "build",
    timestamp: "2026-05-16T00:00:00.000Z",
    artifacts: [
      {
        id: "web-dist",
        path: "dist",
        metadata: {
          type: "static-web-bundle",
          packaging: "static-files",
          version: "1.0.0",
          buildNumber: "1",
          gitCommit: "abc123",
          gitBranch: "main",
          timestamp: "2026-05-16T00:00:00.000Z",
          sizeBytes: 1024,
        },
        fingerprint: {
          algorithm: "sha256",
          hash: "abc123",
        },
        expected: true,
        found: true,
      },
    ],
  },
  deploymentManifest: {
    schemaVersion: "1.0.0",
    productId: "digital-marketing",
    version: "1.0.0",
    environment: "local",
    environmentSafety: "local",
    deploymentId: "deploy-1",
    deployedAt: "2026-05-16T00:00:00.000Z",
    rollbackPlan: {
      strategy: "previous-artifact",
      targetVersion: "0.9.0",
      reason: "Rollback",
      steps: ["restore previous artifact"],
    },
    surfaces: [],
    target: "compose-local",
  },
  verifyHealthReport: {
    schemaVersion: "1.0.0",
    productUnitId: "digital-marketing",
    runId: "run-1",
    status: "healthy",
    checkedAt: "2026-05-16T00:00:00.000Z",
  },
};

describe("createStudioRuntimeTruthSummary", () => {
  it("summarizes lifecycle gates artifacts deployments health and redacted agent evidence", () => {
    const summary = createStudioRuntimeTruthSummary(snapshot);

    expect(summary.lifecycleRun.status).toBe("healthy");
    expect(summary.gates[0]).toMatchObject({
      gateId: "release-policy",
      reason: "security-review-required",
      nextAction: "request-approval",
      state: "blocked",
    });
    expect(summary.artifact.manifestRef).toBe("artifact-manifest://run-1");
    expect(summary.deployment.artifactDigest).toBe("sha256:abc123");
    expect(summary.agentEvidence[0]).toMatchObject({
      evidenceRef: "redacted",
      redacted: true,
      state: "redacted",
    });
  });

  it("marks provider bridge unavailable when any public manifest provider is unavailable", () => {
    const summary = createStudioRuntimeTruthSummary({
      ...snapshot,
      manifestLoadState: {
        ...snapshot.manifestLoadState,
        deploymentManifest: {
          status: "unavailable",
          message: "provider offline",
        },
      },
    });

    expect(summary.providerBridgeHealth).toMatchObject({
      status: "unavailable",
      reason: "provider-unavailable",
      state: "unavailable",
    });
  });
});
