/**
 * Tests for LifecycleContracts — lifecycle plan, execution, and result schemas.
 *
 * Per §2.2 requirements:
 * - Valid plan passes parsing
 * - Invalid plan (missing adapter) fails parsing
 * - Execution result with failed step preserves runId/correlationId
 * - LifecycleRunId/LifecycleCorrelationId factory functions guard empty strings
 */
import { describe, it, expect } from "vitest";
import {
  createLifecycleRunId,
  createLifecycleCorrelationId,
  parseLifecyclePlan,
  parseLifecycleExecutionRequest,
  parseLifecycleExecutionResult,
  parseLifecycleProfile,
  LIFECYCLE_RUN_STATUSES,
  LifecycleRunStatusSchema,
} from "../LifecycleContracts.js";

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

describe("createLifecycleRunId", () => {
  it("accepts a non-empty string", () => {
    const id = createLifecycleRunId("run-abc-123");
    expect(id).toBe("run-abc-123");
  });

  it("throws for empty string", () => {
    expect(() => createLifecycleRunId("")).toThrow(
      "LifecycleRunId must be a non-empty string",
    );
  });

  it("throws for whitespace-only string", () => {
    expect(() => createLifecycleRunId("   ")).toThrow();
  });
});

describe("createLifecycleCorrelationId", () => {
  it("accepts a non-empty string", () => {
    const id = createLifecycleCorrelationId("corr-xyz");
    expect(id).toBe("corr-xyz");
  });

  it("throws for empty string", () => {
    expect(() => createLifecycleCorrelationId("")).toThrow();
  });
});

// ---------------------------------------------------------------------------
// LifecycleRunStatus
// ---------------------------------------------------------------------------

describe("LIFECYCLE_RUN_STATUSES", () => {
  it("contains the 10 canonical status values", () => {
    const expected = [
      "pending",
      "running",
      "succeeded",
      "failed",
      "blocked",
      "skipped",
      "degraded",
      "requires-approval",
      "requires-verification",
      "unknown",
    ] as const;
    for (const status of expected) {
      expect(LIFECYCLE_RUN_STATUSES).toContain(status);
    }
  });

  it("LifecycleRunStatusSchema validates known statuses", () => {
    expect(() => LifecycleRunStatusSchema.parse("succeeded")).not.toThrow();
    expect(() =>
      LifecycleRunStatusSchema.parse("requires-approval"),
    ).not.toThrow();
  });

  it("LifecycleRunStatusSchema rejects unknown status", () => {
    expect(() => LifecycleRunStatusSchema.parse("complete")).toThrow();
  });
});

// ---------------------------------------------------------------------------
// LifecycleProfile
// ---------------------------------------------------------------------------

describe("parseLifecycleProfile", () => {
  it("accepts a valid profile", () => {
    const profile = parseLifecycleProfile({
      schemaVersion: "1.0.0",
      profileId: "standard",
      displayName: "Standard",
      defaultPhases: ["build", "test"],
    });
    expect(profile.profileId).toBe("standard");
  });

  it("rejects missing required fields", () => {
    expect(() =>
      parseLifecycleProfile({ schemaVersion: "1.0.0", profileId: "x" }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// LifecyclePlan
// ---------------------------------------------------------------------------

const VALID_PLAN = {
  schemaVersion: "1.0.0" as const,
  runId: "run-001",
  correlationId: "corr-001",
  createdAt: "2026-06-01T10:00:00.000Z",
  productId: "digital-marketing",
  phase: "build",
  phaseMode: "sequential" as const,
  lifecycleProfile: "standard",
  outputDirectory: "/tmp/lifecycle/run-001",
  estimatedDurationMs: 60000,
  steps: [
    {
      stepId: "step-build-web",
      stepKind: "surface" as const,
      phase: "build",
      surface: "web",
      adapter: "nextjs-build",
      description: "Build the web surface",
      dependsOn: [],
      estimatedDurationMs: 30000,
    },
  ],
};

describe("parseLifecyclePlan", () => {
  it("accepts a valid Digital Marketing build plan", () => {
    const plan = parseLifecyclePlan(VALID_PLAN);
    expect(plan.productId).toBe("digital-marketing");
    expect(plan.runId).toBe("run-001");
    expect(plan.correlationId).toBe("corr-001");
    expect(plan.steps).toHaveLength(1);
  });

  it("accepts a plan with no steps (empty-phase plan)", () => {
    const plan = parseLifecyclePlan({ ...VALID_PLAN, steps: [] });
    expect(plan.steps).toHaveLength(0);
  });

  it("rejects a plan where a step has an empty adapter", () => {
    expect(() =>
      parseLifecyclePlan({
        ...VALID_PLAN,
        steps: [{ ...VALID_PLAN.steps[0], adapter: "" }],
      }),
    ).toThrow();
  });

  it("rejects a plan with missing schemaVersion", () => {
    const { schemaVersion: _sv, ...rest } = VALID_PLAN;
    expect(() => parseLifecyclePlan(rest)).toThrow();
  });

  it("rejects a plan with wrong schemaVersion", () => {
    expect(() =>
      parseLifecyclePlan({ ...VALID_PLAN, schemaVersion: "2.0.0" }),
    ).toThrow();
  });

  it("preserves semanticArtifactRefs when provided", () => {
    const plan = parseLifecyclePlan({
      ...VALID_PLAN,
      semanticArtifactRefs: ["ref:agent-runtime-v3"],
    });
    expect(plan.semanticArtifactRefs).toContain("ref:agent-runtime-v3");
  });
});

// ---------------------------------------------------------------------------
// LifecycleExecutionRequest
// ---------------------------------------------------------------------------

describe("parseLifecycleExecutionRequest", () => {
  it("accepts a valid dry-run request", () => {
    const req = parseLifecycleExecutionRequest({
      schemaVersion: "1.0.0",
      runId: "run-dry-001",
      correlationId: "corr-dry-001",
      createdAt: "2026-06-01T10:00:00.000Z",
      productId: "digital-marketing",
      phase: "build",
      dryRun: true,
      outputDirectory: "/tmp/lifecycle/run-dry-001",
    });
    expect(req.dryRun).toBe(true);
  });

  it("defaults dryRun to false when omitted", () => {
    const req = parseLifecycleExecutionRequest({
      schemaVersion: "1.0.0",
      runId: "run-002",
      correlationId: "corr-002",
      createdAt: "2026-06-01T10:00:00.000Z",
      productId: "digital-marketing",
      phase: "test",
      outputDirectory: "/tmp/lifecycle/run-002",
    });
    expect(req.dryRun).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// LifecycleExecutionResult
// ---------------------------------------------------------------------------

describe("parseLifecycleExecutionResult", () => {
  it("accepts a successful result preserving runId and correlationId", () => {
    const result = parseLifecycleExecutionResult({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T10:05:00.000Z",
      productId: "digital-marketing",
      phase: "build",
      lifecycleProfile: "standard",
      status: "succeeded",
      startedAt: "2026-06-01T10:00:00.000Z",
      completedAt: "2026-06-01T10:05:00.000Z",
      durationMs: 300000,
      steps: [],
      gateRefs: [],
      artifactRefs: ["artifact/web-bundle-v1.tar.gz"],
    });
    expect(result.runId).toBe("run-001");
    expect(result.correlationId).toBe("corr-001");
    expect(result.status).toBe("succeeded");
  });

  it("accepts a failed result with failure reason code", () => {
    const result = parseLifecycleExecutionResult({
      schemaVersion: "1.0.0",
      runId: "run-002",
      correlationId: "corr-002",
      createdAt: "2026-06-01T11:00:00.000Z",
      productId: "digital-marketing",
      phase: "build",
      lifecycleProfile: "standard",
      status: "failed",
      startedAt: "2026-06-01T10:50:00.000Z",
      completedAt: "2026-06-01T11:00:00.000Z",
      durationMs: 600000,
      steps: [
        {
          stepId: "step-build-web",
          status: "failed",
          startedAt: "2026-06-01T10:50:00.000Z",
          completedAt: "2026-06-01T11:00:00.000Z",
          durationMs: 600000,
          exitCode: 1,
          failureReasonCode: "adapter-failed",
          failureMessage: "nextjs build exited with code 1",
        },
      ],
      gateRefs: [],
      artifactRefs: [],
      failure: {
        reasonCode: "adapter-failed",
        stepId: "step-build-web",
        message: "Surface build failed",
      },
    });
    expect(result.status).toBe("failed");
    expect(result.failure?.reasonCode).toBe("adapter-failed");
    expect(result.steps[0]?.failureReasonCode).toBe("adapter-failed");
  });

  it("rejects invalid status", () => {
    expect(() =>
      parseLifecycleExecutionResult({
        schemaVersion: "1.0.0",
        runId: "r",
        correlationId: "c",
        createdAt: "2026-06-01T10:00:00.000Z",
        productId: "x",
        phase: "build",
        lifecycleProfile: "standard",
        status: "completed", // not in enum
        startedAt: "2026-06-01T10:00:00.000Z",
        completedAt: "2026-06-01T10:05:00.000Z",
        durationMs: 0,
        steps: [],
        gateRefs: [],
        artifactRefs: [],
      }),
    ).toThrow();
  });
});
