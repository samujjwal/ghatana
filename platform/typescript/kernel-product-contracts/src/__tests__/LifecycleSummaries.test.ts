/**
 * Tests for LifecycleSummaries UI-facing contracts (§2.6).
 *
 * Validates:
 * - LifecycleGateSummary parses valid gate evaluation results
 * - LifecycleArtifactSummary parses valid artifact records
 * - LifecycleDeploymentSummary parses valid deployment records
 * - LifecycleHealthSummary parses valid health snapshot records
 * - LifecycleRunSummary composes all nested summaries
 * - parseLifecycleRunSummary rejects invalid payloads
 * - All types are exported from the public index
 */
import { describe, it, expect } from "vitest";
import {
  LifecycleGateSummarySchema,
  LifecycleArtifactSummarySchema,
  LifecycleDeploymentSummarySchema,
  LifecycleHealthSummarySchema,
  LifecycleRunSummarySchema,
  parseLifecycleRunSummary,
} from "../ui-summary/LifecycleSummaries.js";

// Re-export surface check via the public index
import {
  parseLifecycleRunSummary as parseLifecycleRunSummaryFromIndex,
  type LifecycleRunSummary,
  type LifecycleGateSummary,
  type LifecycleArtifactSummary,
  type LifecycleDeploymentSummary,
  type LifecycleHealthSummary,
} from "../index.js";

describe("LifecycleGateSummary", () => {
  it("parses a minimal passing gate summary", () => {
    const result = LifecycleGateSummarySchema.parse({
      gateId: "registry-validation",
      passed: true,
      required: true,
    });
    expect(result.gateId).toBe("registry-validation");
    expect(result.passed).toBe(true);
    expect(result.required).toBe(true);
  });

  it("parses a failing gate summary with reason", () => {
    const result = LifecycleGateSummarySchema.parse({
      gateId: "manifest-validation",
      passed: false,
      required: true,
      failureReason: "missing required field: schemaVersion",
      evidenceRefs: ["products/phr/lifecycle/gate-packs/consent.yaml"],
    });
    expect(result.passed).toBe(false);
    expect(result.failureReason).toBe("missing required field: schemaVersion");
    expect(result.evidenceRefs).toHaveLength(1);
  });

  it("rejects a gate summary with missing required fields", () => {
    expect(() =>
      LifecycleGateSummarySchema.parse({ passed: true }),
    ).toThrow();
  });

  it("accepts optional displayName and evaluatedAt", () => {
    const result = LifecycleGateSummarySchema.parse({
      gateId: "bridge-compliance",
      passed: true,
      required: false,
      displayName: "Bridge Compliance Gate",
      evaluatedAt: "2026-05-26T12:00:00Z",
    });
    expect(result.displayName).toBe("Bridge Compliance Gate");
    expect(result.evaluatedAt).toBe("2026-05-26T12:00:00Z");
  });
});

describe("LifecycleArtifactSummary", () => {
  it("parses a produced artifact summary", () => {
    const result = LifecycleArtifactSummarySchema.parse({
      artifactType: "jvm-service",
      surfaceId: "backend-api",
      packaging: "jar",
      required: true,
      produced: true,
      paths: ["products/phr/api/build/libs/phr-api.jar"],
    });
    expect(result.artifactType).toBe("jvm-service");
    expect(result.produced).toBe(true);
    expect(result.paths).toHaveLength(1);
  });

  it("parses a not-yet-produced artifact", () => {
    const result = LifecycleArtifactSummarySchema.parse({
      artifactType: "container-image",
      required: true,
      produced: false,
    });
    expect(result.produced).toBe(false);
  });

  it("accepts optional digest field", () => {
    const result = LifecycleArtifactSummarySchema.parse({
      artifactType: "static-web-bundle",
      required: true,
      produced: true,
      digest: "sha256:abc123",
    });
    expect(result.digest).toBe("sha256:abc123");
  });

  it("rejects artifact summary missing artifactType", () => {
    expect(() =>
      LifecycleArtifactSummarySchema.parse({ required: true, produced: false }),
    ).toThrow();
  });
});

describe("LifecycleDeploymentSummary", () => {
  it("parses a successful deployment summary", () => {
    const result = LifecycleDeploymentSummarySchema.parse({
      environment: "local",
      adapter: "compose-local",
      succeeded: true,
      deployedAt: "2026-05-26T12:00:00Z",
      confirmedServices: ["phr-api", "phr-web"],
    });
    expect(result.environment).toBe("local");
    expect(result.succeeded).toBe(true);
    expect(result.confirmedServices).toHaveLength(2);
  });

  it("parses a failed deployment summary", () => {
    const result = LifecycleDeploymentSummarySchema.parse({
      environment: "local",
      succeeded: false,
      statusDescription: "compose up failed: port already in use",
    });
    expect(result.succeeded).toBe(false);
    expect(result.statusDescription).toBeDefined();
  });

  it("rejects summary missing environment", () => {
    expect(() =>
      LifecycleDeploymentSummarySchema.parse({ succeeded: true }),
    ).toThrow();
  });
});

describe("LifecycleHealthSummary", () => {
  it("parses a healthy health summary", () => {
    const result = LifecycleHealthSummarySchema.parse({
      status: "healthy",
      checkedAt: "2026-05-26T12:00:00Z",
      checks: [
        { surfaceId: "backend-api", healthy: true },
        { surfaceId: "web", healthy: true },
      ],
    });
    expect(result.status).toBe("healthy");
    expect(result.checks).toHaveLength(2);
  });

  it("parses a degraded health summary with detail", () => {
    const result = LifecycleHealthSummarySchema.parse({
      status: "degraded",
      checks: [
        {
          surfaceId: "backend-api",
          healthy: false,
          detail: "HTTP 503 from /health/ready",
        },
      ],
    });
    expect(result.status).toBe("degraded");
    expect(result.checks?.[0]?.healthy).toBe(false);
  });

  it("rejects an invalid status value", () => {
    expect(() =>
      LifecycleHealthSummarySchema.parse({ status: "great" }),
    ).toThrow();
  });
});

describe("LifecycleRunSummary", () => {
  const minimalValidRun = {
    runId: "run-001",
    productUnitId: "phr",
    phase: "validate",
    status: "healthy" as const,
  };

  it("parses a minimal run summary", () => {
    const result = parseLifecycleRunSummary(minimalValidRun);
    expect(result.runId).toBe("run-001");
    expect(result.phase).toBe("validate");
    expect(result.status).toBe("healthy");
  });

  it("parses a full run summary with all nested summaries", () => {
    const result = LifecycleRunSummarySchema.parse({
      runId: "run-002",
      correlationId: "corr-abc",
      productUnitId: "digital-marketing",
      phase: "test",
      status: "failed",
      startedAt: "2026-05-26T11:00:00Z",
      completedAt: "2026-05-26T11:05:00Z",
      failedRequiredGateCount: 1,
      statusDescription: "unit-test-coverage gate failed",
      gates: [
        { gateId: "unit-test-coverage", passed: false, required: true },
        { gateId: "integration-test-coverage", passed: true, required: true },
      ],
      artifacts: [
        { artifactType: "test-report", required: true, produced: true },
      ],
      health: { status: "degraded" },
    });
    expect(result.gates).toHaveLength(2);
    expect(result.failedRequiredGateCount).toBe(1);
    expect(result.health?.status).toBe("degraded");
  });

  it("rejects run summary with invalid status", () => {
    expect(() =>
      parseLifecycleRunSummary({ ...minimalValidRun, status: "awesome" }),
    ).toThrow();
  });

  it("rejects run summary missing required fields", () => {
    expect(() => parseLifecycleRunSummary({ runId: "x" })).toThrow();
  });

  it("is exported from the public index and parses identically", () => {
    const result = parseLifecycleRunSummaryFromIndex(minimalValidRun);
    expect(result.runId).toBe("run-001");
  });
});

// Type-level check — ensure all types are exported from the public index
describe("public index type exports", () => {
  it("exports all UI summary types from the public index", () => {
    // These assignments compile only if the types are correctly exported.
    const _gate: LifecycleGateSummary = {
      gateId: "x",
      passed: true,
      required: false,
    };
    const _artifact: LifecycleArtifactSummary = {
      artifactType: "jar",
      required: true,
      produced: true,
    };
    const _deployment: LifecycleDeploymentSummary = {
      environment: "local",
      succeeded: true,
    };
    const _health: LifecycleHealthSummary = { status: "healthy" };
    const _run: LifecycleRunSummary = {
      runId: "r",
      productUnitId: "phr",
      phase: "build",
      status: "healthy",
    };
    expect(_gate.gateId).toBe("x");
    expect(_artifact.artifactType).toBe("jar");
    expect(_deployment.environment).toBe("local");
    expect(_health.status).toBe("healthy");
    expect(_run.runId).toBe("r");
  });
});
