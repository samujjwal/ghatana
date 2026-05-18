import { describe, expect, it, vi } from "vitest";
import { ExecutionResultCollector } from "../ExecutionResultCollector.js";
import type { ExecutionLogger } from "../../domain/ProductLifecyclePhase.js";

function createLogger(): ExecutionLogger {
  return {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  };
}

describe("ExecutionResultCollector", () => {
  it("preserves lifecycle truth metadata in collected results", () => {
    const collector = new ExecutionResultCollector(createLogger());
    collector.addStepResult({
      stepId: "build-web",
      status: "succeeded",
      durationMs: 12,
    });

    const result = collector.collect(
      "digital-marketing",
      "build",
      "/tmp/out",
      "run-1",
      {
        correlationId: "corr-1",
        providerMode: "bootstrap",
        productUnitRef: "product-unit://digital-marketing",
        manifestRefs: {
          artifactManifest: "artifact-manifest.json",
        },
        eventsRef: "lifecycle-events.json",
        healthSnapshotRef: "lifecycle-health-snapshot.json",
        approvalRefs: [
          {
            approvalId: "approval-1",
            status: "approved",
            ref: "approval-gates/approval-1.json",
          },
        ],
      },
    );

    expect(result).toMatchObject({
      runId: "run-1",
      correlationId: "corr-1",
      providerMode: "bootstrap",
      productUnitRef: "product-unit://digital-marketing",
      manifestRefs: { artifactManifest: "artifact-manifest.json" },
      eventsRef: "lifecycle-events.json",
      healthSnapshotRef: "lifecycle-health-snapshot.json",
      approvalRefs: [{ approvalId: "approval-1", status: "approved" }],
    });
  });

  it("marks failed step results with adapter failure reason code", () => {
    const collector = new ExecutionResultCollector(createLogger());
    collector.addStepResult({
      stepId: "build-web",
      status: "failed",
      stderr: "adapter exited",
      durationMs: 12,
    });

    const result = collector.collect(
      "digital-marketing",
      "build",
      "/tmp/out",
      "run-1",
    );

    expect(result.status).toBe("failed");
    expect(result.failure).toEqual({
      reasonCode: "adapter-failed",
      stepId: "build-web",
      message: "Step build-web failed",
      cause: "adapter exited",
    });
  });

  it("collects artifact and gate truth while preserving current state access", () => {
    const logger = createLogger();
    const collector = new ExecutionResultCollector(logger);

    collector.addArtifact({
      id: "artifact-1",
      type: "static-web-bundle",
      surface: "studio",
      path: "dist",
      size: 1024,
      checksum: "sha256:abc",
    });
    collector.addGateResult({
      gateId: "typecheck",
      status: "passed",
      reason: "typecheck passed",
      evidence: ["log:typecheck"],
    });

    expect(collector.getCurrentResults()).toMatchObject({
      artifacts: [{ id: "artifact-1" }],
      gateResults: [{ gateId: "typecheck" }],
      steps: [],
    });

    const result = collector.collect(
      "digital-marketing",
      "build",
      "/tmp/out",
      "run-1",
    );
    expect(result.status).toBe("skipped");
    expect(result.artifacts).toHaveLength(1);
    expect(result.gates).toHaveLength(1);
    expect(logger.debug).toHaveBeenCalledWith("Added artifact artifact-1", {
      type: "static-web-bundle",
      surface: "studio",
    });
    expect(logger.debug).toHaveBeenCalledWith(
      "Added gate result for typecheck",
      {
        status: "passed",
      },
    );
  });

  it("resets collected execution state", () => {
    const logger = createLogger();
    const collector = new ExecutionResultCollector(logger);

    collector.addStepResult({
      stepId: "build-web",
      status: "succeeded",
      durationMs: 12,
    });
    collector.reset();

    expect(collector.getCurrentResults()).toEqual({
      steps: [],
      artifacts: [],
      gateResults: [],
    });
    expect(logger.debug).toHaveBeenCalledWith(
      "Reset execution result collector",
    );
  });

  it("omits failure causes when failed steps do not expose stderr", () => {
    const collector = new ExecutionResultCollector(createLogger());

    collector.addStepResult({
      stepId: "build-web",
      status: "failed",
      durationMs: 12,
    });

    expect(
      collector.collect("digital-marketing", "build", "/tmp/out", "run-1")
        .failure,
    ).toEqual({
      reasonCode: "adapter-failed",
      stepId: "build-web",
      message: "Step build-web failed",
    });
  });

  it("marks runs with only skipped steps as skipped", () => {
    const collector = new ExecutionResultCollector(createLogger());

    collector.addStepResult({
      stepId: "build-web",
      status: "skipped",
      durationMs: 0,
    });

    expect(
      collector.collect("digital-marketing", "build", "/tmp/out", "run-1")
        .status,
    ).toBe("skipped");
  });

  // -------------------------------------------------------------------------
  // §2.4 — phase-tracking and lifecycle-profile fields
  // -------------------------------------------------------------------------

  it("propagates lifecycleProfile and environment from metadata into the result", () => {
    const collector = new ExecutionResultCollector(createLogger());
    collector.addStepResult({
      stepId: "build-backend",
      status: "succeeded",
      durationMs: 100,
    });

    const result = collector.collect(
      "digital-marketing",
      "build",
      "/tmp/out",
      "run-1",
      {
        lifecycleProfile: "standard-web-api-product",
        environment: "staging",
      },
    );

    expect(result.lifecycleProfile).toBe("standard-web-api-product");
    expect(result.environment).toBe("staging");
  });

  it("propagates requestedPhases, executedPhases, skippedPhases, blockedPhases", () => {
    const collector = new ExecutionResultCollector(createLogger());
    collector.addStepResult({
      stepId: "build-backend",
      status: "succeeded",
      durationMs: 50,
    });

    const result = collector.collect(
      "digital-marketing",
      "build",
      "/tmp/out",
      "run-1",
      {
        requestedPhases: ["build", "deploy"],
        executedPhases: ["build"],
        skippedPhases: [],
        blockedPhases: ["deploy"],
      },
    );

    expect(result.requestedPhases).toEqual(["build", "deploy"]);
    expect(result.executedPhases).toEqual(["build"]);
    expect(result.skippedPhases).toEqual([]);
    expect(result.blockedPhases).toEqual(["deploy"]);
  });

  it("omits optional phase-tracking fields when not provided", () => {
    const collector = new ExecutionResultCollector(createLogger());
    collector.addStepResult({
      stepId: "build-backend",
      status: "succeeded",
      durationMs: 20,
    });

    const result = collector.collect(
      "digital-marketing",
      "build",
      "/tmp/out",
      "run-1",
    );

    expect(result.lifecycleProfile).toBeUndefined();
    expect(result.environment).toBeUndefined();
    expect(result.requestedPhases).toBeUndefined();
    expect(result.executedPhases).toBeUndefined();
  });
});
