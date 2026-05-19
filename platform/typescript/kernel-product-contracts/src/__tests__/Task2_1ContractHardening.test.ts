/**
 * Task 2.1 — Contract hardening tests for @ghatana/kernel-product-contracts.
 *
 * Spec-required coverage:
 * - Digital Marketing fixture lifecycle plan parses (with productUnitId)
 * - PHR fixture execution result parses (with productUnitId)
 * - Finance disabled product fixture parses but lifecycle plan reflects disabled status
 * - Invalid lifecycle phase (empty / non-canonical) fails plan parsing
 * - productUnitId of empty string fails (min(1) guard) when provided
 * - High-risk AgentLifecycleActionRequest requires approval metadata
 * - LifecycleFailure standalone schema parses correctly
 * - GateResult canonical alias equals GateResultEntry
 * - ApprovalRequirement standalone schema parses correctly
 * - LifecycleResult / LifecycleExecutionContext alias schemas work
 *
 * @doc.type module
 * @doc.purpose Task 2.1 contract hardening tests
 * @doc.layer kernel-product-contracts
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import {
  parseLifecyclePlan,
  parseLifecycleExecutionResult,
  LifecycleResultSchema,
  LifecycleExecutionContextSchema,
  LifecycleFailureSchema,
  LIFECYCLE_FAILURE_REASON_CODES,
} from "../lifecycle/LifecycleContracts.js";
import {
  GateResultEntrySchema,
  GateResultSchema,
  GateResultManifestSchema,
  ApprovalRequirementSchema,
} from "../gate/GateContracts.js";
import {
  parseAgentLifecycleActionRequest,
  AgentLifecycleActionRequestSchema,
} from "../agentic/AgentLifecycleActionRequest.js";

// ---------------------------------------------------------------------------
// Shared test data helpers
// ---------------------------------------------------------------------------

/** Minimal valid LifecyclePlan base suitable for any product. */
function makeLifecyclePlan(overrides: Record<string, unknown> = {}) {
  return {
    schemaVersion: "1.0.0" as const,
    runId: "run-dm-001",
    correlationId: "corr-dm-001",
    createdAt: "2026-06-01T10:00:00.000Z",
    productId: "digital-marketing",
    productUnitId: "digital-marketing",
    phase: "build",
    phaseMode: "sequential" as const,
    lifecycleProfile: "standard-web-api-product",
    environment: "staging",
    outputDirectory: ".lifecycle/run-dm-001",
    estimatedDurationMs: 120000,
    steps: [],
    ...overrides,
  };
}

/** Minimal valid LifecycleExecutionResult base. */
function makeExecutionResult(overrides: Record<string, unknown> = {}) {
  return {
    schemaVersion: "1.0.0" as const,
    runId: "run-dm-001",
    correlationId: "corr-dm-001",
    createdAt: "2026-06-01T10:00:00.000Z",
    productId: "digital-marketing",
    productUnitId: "digital-marketing",
    phase: "build",
    lifecycleProfile: "standard-web-api-product",
    environment: "staging",
    status: "succeeded" as const,
    startedAt: "2026-06-01T10:00:00.000Z",
    completedAt: "2026-06-01T10:02:00.000Z",
    durationMs: 120000,
    steps: [],
    gateRefs: [],
    artifactRefs: [],
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Digital Marketing fixture
// ---------------------------------------------------------------------------

describe("Digital Marketing lifecycle plan fixture", () => {
  it("parses a valid plan with productUnitId", () => {
    const plan = parseLifecyclePlan(
      makeLifecyclePlan({
        productId: "digital-marketing",
        productUnitId: "digital-marketing",
        phase: "build",
        surfaces: ["digital-marketing-api", "digital-marketing-web"],
        adapterIds: ["docker-build-adapter", "pnpm-build-adapter"],
        gates: ["build-tests-gate", "lint-gate"],
      }),
    );

    expect(plan.productId).toBe("digital-marketing");
    expect(plan.productUnitId).toBe("digital-marketing");
    expect(plan.phase).toBe("build");
    expect(plan.surfaces).toEqual(["digital-marketing-api", "digital-marketing-web"]);
  });

  it("parses a deploy plan with environment and surfaces", () => {
    const plan = parseLifecyclePlan(
      makeLifecyclePlan({
        productId: "digital-marketing",
        productUnitId: "digital-marketing",
        phase: "deploy",
        environment: "compose-local",
        surfaces: ["digital-marketing-api"],
        requiredManifests: ["artifact-manifest.json"],
      }),
    );

    expect(plan.phase).toBe("deploy");
    expect(plan.environment).toBe("compose-local");
  });

  it("parses plan with actionable warnings", () => {
    const plan = parseLifecyclePlan(
      makeLifecyclePlan({
        warnings: ["no-test-coverage-baseline", "registry-drift-detected"],
        blockingReasons: [],
      }),
    );

    expect(plan.warnings).toHaveLength(2);
    expect(plan.blockingReasons).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// PHR fixture
// ---------------------------------------------------------------------------

describe("PHR lifecycle execution result fixture", () => {
  it("parses a valid succeeded PHR execution result with productUnitId", () => {
    const result = parseLifecycleExecutionResult(
      makeExecutionResult({
        productId: "phr",
        productUnitId: "phr",
        phase: "test",
        lifecycleProfile: "health-records-product",
        environment: "dev",
        status: "succeeded",
      }),
    );

    expect(result.productId).toBe("phr");
    expect(result.productUnitId).toBe("phr");
    expect(result.phase).toBe("test");
    expect(result.status).toBe("succeeded");
  });

  it("parses a failed PHR result with structured failure", () => {
    const result = parseLifecycleExecutionResult(
      makeExecutionResult({
        productId: "phr",
        productUnitId: "phr",
        phase: "test",
        status: "failed",
        failure: {
          reasonCode: "gate-failed",
          message: "HIPAA compliance gate failed",
          actionableMessage: "Fix compliance violations before promoting",
          evidenceRefs: ["gate-result-manifest.json"],
          diagnostics: { failedGateId: "hipaa-compliance-gate" },
        },
      }),
    );

    expect(result.status).toBe("failed");
    expect(result.failure?.reasonCode).toBe("gate-failed");
    expect(result.failure?.actionableMessage).toContain("compliance");
    expect(result.failure?.evidenceRefs).toHaveLength(1);
    expect(result.failure?.diagnostics?.["failedGateId"]).toBe("hipaa-compliance-gate");
  });

  it("PHR result preserves top-level reasonCode and actionableMessage", () => {
    const result = parseLifecycleExecutionResult(
      makeExecutionResult({
        productId: "phr",
        productUnitId: "phr",
        phase: "deploy",
        status: "blocked",
        reasonCode: "approval-required",
        actionableMessage: "Waiting for PHR security team approval",
        evidenceRefs: ["approval-request-001.json"],
        diagnostics: { approvalId: "sec-approval-001" },
      }),
    );

    expect(result.reasonCode).toBe("approval-required");
    expect(result.actionableMessage).toContain("approval");
    expect(result.evidenceRefs).toHaveLength(1);
    expect(result.diagnostics?.["approvalId"]).toBe("sec-approval-001");
  });
});

// ---------------------------------------------------------------------------
// Finance disabled product fixture
// ---------------------------------------------------------------------------

describe("Finance disabled product fixture", () => {
  it("parses a lifecycle plan for a disabled finance product", () => {
    // Finance is disabled — the plan itself is a valid document but the
    // execution should fail-closed at runtime. The contract allows parsing.
    const plan = parseLifecyclePlan(
      makeLifecyclePlan({
        productId: "finance",
        productUnitId: "finance",
        phase: "build",
        lifecycleProfile: "standard-web-api-product",
      }),
    );

    expect(plan.productId).toBe("finance");
    expect(plan.productUnitId).toBe("finance");
  });

  it("parses a failed finance execution result with disabled-product reason", () => {
    // When a disabled product is executed, the result carries disabled-product
    const result = parseLifecycleExecutionResult(
      makeExecutionResult({
        productId: "finance",
        productUnitId: "finance",
        phase: "build",
        lifecycleProfile: "standard-web-api-product",
        status: "failed",
        failure: {
          reasonCode: "disabled-product",
          message: "Product 'finance' has lifecycleStatus=disabled. Execution blocked.",
          actionableMessage: "Enable the product before executing lifecycle phases.",
        },
      }),
    );

    expect(result.status).toBe("failed");
    expect(result.failure?.reasonCode).toBe("disabled-product");
  });
});

// ---------------------------------------------------------------------------
// Invalid lifecycle phase validation
// ---------------------------------------------------------------------------

describe("Lifecycle plan phase validation", () => {
  it("rejects an empty-string phase", () => {
    expect(() =>
      parseLifecyclePlan(makeLifecyclePlan({ phase: "" })),
    ).toThrow();
  });

  it("accepts all canonical ProductLifecyclePhase values", () => {
    const canonicalPhases = [
      "create", "bootstrap", "dev", "validate", "test",
      "build", "package", "release", "deploy", "verify",
      "promote", "rollback", "operate", "retire",
    ];

    for (const phase of canonicalPhases) {
      const plan = parseLifecyclePlan(makeLifecyclePlan({ phase }));
      expect(plan.phase).toBe(phase);
    }
  });

  it("rejects a null phase", () => {
    expect(() =>
      parseLifecyclePlan(makeLifecyclePlan({ phase: null })),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// productUnitId guard
// ---------------------------------------------------------------------------

describe("productUnitId validation", () => {
  it("rejects empty-string productUnitId when provided", () => {
    expect(() =>
      parseLifecyclePlan(makeLifecyclePlan({ productUnitId: "" })),
    ).toThrow();
  });

  it("accepts omitted productUnitId (optional field)", () => {
    const plan = parseLifecyclePlan(
      makeLifecyclePlan({ productUnitId: undefined }),
    );
    expect(plan.productUnitId).toBeUndefined();
  });

  it("rejects empty-string productUnitId in execution result", () => {
    expect(() =>
      parseLifecycleExecutionResult(makeExecutionResult({ productUnitId: "" })),
    ).toThrow();
  });

  it("accepts valid productUnitId in GateResultManifest", () => {
    const manifest = GateResultManifestSchema.parse({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T10:00:00.000Z",
      productId: "digital-marketing",
      productUnitId: "digital-marketing",
      phase: "test",
      overallPassed: true,
      gates: [],
    });
    expect(manifest.productUnitId).toBe("digital-marketing");
  });

  it("rejects empty-string productUnitId in GateResultManifest", () => {
    expect(() =>
      GateResultManifestSchema.parse({
        schemaVersion: "1.0.0",
        runId: "r",
        correlationId: "c",
        createdAt: "2026-06-01T10:00:00.000Z",
        productId: "x",
        productUnitId: "",
        phase: "test",
        overallPassed: false,
        gates: [],
      }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// High-risk AgentLifecycleActionRequest requires approval metadata
// ---------------------------------------------------------------------------

/** Minimal valid base request fixture (matches AgentLifecycleAction.test.ts). */
function makeAgentRequest(overrides: Record<string, unknown> = {}) {
  return {
    schemaVersion: "1.0.0" as const,
    requestId: "req-001",
    correlationId: "corr-001",
    productUnitId: "phr",
    scope: { tenantId: "tenant-1", workspaceId: "ws-1", projectId: "phr" },
    requestedByAgent: "agent:release-reviewer",
    requestedByAgentVersion: "2026.06.0",
    masteryState: {
      state: "mastered",
      stateRef: "mastery:agent:release-reviewer:2026.06.0",
      evaluatedAt: "2026-06-01T12:00:00.000Z",
    },
    policyDecision: {
      decisionId: "policy:req-001",
      decision: "requires-approval",
      evaluatedAt: "2026-06-01T12:00:00.000Z",
      reasonCodes: ["high-risk-requires-approval"],
      evidenceRefs: ["evidence:policy:1"],
    },
    toolPermissions: [
      {
        toolId: "kernel.lifecycle.execute-phase",
        permissionRef: "permission:kernel.lifecycle.execute-phase",
        granted: true,
        allowedActions: ["execute-lifecycle-phase"],
      },
    ],
    requestedAction: "execute-lifecycle-phase" as const,
    lifecyclePhase: "deploy" as const,
    proposedPlanRef: "lifecycle-plan:run-001",
    riskLevel: "high" as const,
    approvalRequired: true,
    requiredApprovals: [
      { approvalId: "phr-security-approval", approverRole: "security-team", required: true },
    ],
    requiredVerification: [
      { verificationId: "verify-health-001", kind: "health" as const, required: true },
    ],
    inputRefs: ["input:product-unit-intent:1"],
    outputRefs: ["output:lifecycle-run:run-001"],
    verificationProofRefs: ["verification:health:1"],
    evidenceRefs: ["evidence:policy:1"],
    rollbackPlanRef: "rollback-plan:run-001",
    fallbackMode: "rollback" as const,
    ...overrides,
  };
}

describe("High-risk AgentLifecycleActionRequest", () => {
  it("requires requiredApprovals for high-risk actions", () => {
    const request = parseAgentLifecycleActionRequest(makeAgentRequest({
      riskLevel: "high",
      productUnitId: "phr",
      lifecyclePhase: "deploy",
      requiredApprovals: [
        { approvalId: "phr-security-approval", approverRole: "security-team", required: true },
      ],
    }));

    expect(request.riskLevel).toBe("high");
    expect(request.requiredApprovals).toHaveLength(1);
    expect(request.requiredApprovals[0]?.approvalId).toBe("phr-security-approval");
    expect(request.requiredApprovals[0]?.approverRole).toBe("security-team");
  });

  it("allows critical risk level with multiple required approvals", () => {
    const request = parseAgentLifecycleActionRequest(makeAgentRequest({
      requestId: "req-002",
      correlationId: "corr-002",
      productUnitId: "finance",
      scope: { tenantId: "tenant-1", workspaceId: "ws-1", projectId: "finance" },
      requestedAction: "request-approval",
      lifecyclePhase: "promote",
      toolPermissions: [
        {
          toolId: "kernel.lifecycle.request-approval",
          permissionRef: "permission:kernel.lifecycle.request-approval",
          granted: true,
          allowedActions: ["request-approval"],
        },
      ],
      riskLevel: "critical",
      approvalRequired: true,
      requiredApprovals: [
        { approvalId: "finance-security", approverRole: "security-team", required: true },
        { approvalId: "finance-compliance", approverRole: "compliance-team", required: true },
      ],
    }));

    expect(request.riskLevel).toBe("critical");
    expect(request.requiredApprovals).toHaveLength(2);
  });

  it("rejects high-risk request where ALL approvals are non-required", () => {
    // The superRefine validator rejects high/critical risk with all non-required approvals
    const result = AgentLifecycleActionRequestSchema.safeParse(makeAgentRequest({
      riskLevel: "high",
      requiredApprovals: [
        { approvalId: "optional-approval", approverRole: "team", required: false },
      ],
    }));
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((i) => i.message).join(" ");
      expect(messages).toContain("required approval");
    }
  });
});

// ---------------------------------------------------------------------------
// LifecycleFailure standalone schema
// ---------------------------------------------------------------------------

describe("LifecycleFailure standalone schema", () => {
  it("parses a complete lifecycle failure", () => {
    const failure = LifecycleFailureSchema.parse({
      reasonCode: "gate-failed",
      stepId: "run-tests-step",
      message: "Test gate failed: 12 tests failed",
      actionableMessage: "Fix failing tests before retrying",
      cause: "Jest test runner exit code 1",
      evidenceRefs: ["test-report.json"],
      diagnostics: { failedCount: 12, testSuite: "unit" },
    });

    expect(failure.reasonCode).toBe("gate-failed");
    expect(failure.actionableMessage).toContain("failing tests");
    expect(failure.evidenceRefs).toHaveLength(1);
    expect(failure.diagnostics?.["failedCount"]).toBe(12);
  });

  it("parses minimal lifecycle failure (message only)", () => {
    const failure = LifecycleFailureSchema.parse({ message: "Unexpected error" });
    expect(failure.message).toBe("Unexpected error");
    expect(failure.reasonCode).toBeUndefined();
  });

  it("rejects failure without message", () => {
    expect(() =>
      LifecycleFailureSchema.parse({ reasonCode: "unknown" }),
    ).toThrow();
  });

  it("validates all LIFECYCLE_FAILURE_REASON_CODES", () => {
    for (const code of LIFECYCLE_FAILURE_REASON_CODES) {
      const failure = LifecycleFailureSchema.parse({ reasonCode: code, message: "test" });
      expect(failure.reasonCode).toBe(code);
    }
  });

  it("rejects unknown failure reasonCode", () => {
    expect(() =>
      LifecycleFailureSchema.parse({ reasonCode: "not-a-valid-code", message: "test" }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// GateResult canonical alias
// ---------------------------------------------------------------------------

describe("GateResult canonical alias", () => {
  it("GateResultSchema equals GateResultEntrySchema (same shape)", () => {
    const entry = {
      gateId: "lint-gate",
      phase: "build",
      required: true,
      passed: true,
      evaluatedAt: "2026-06-01T10:00:00.000Z",
      durationMs: 800,
      reason: "All lint rules passed",
      evidenceRefs: ["lint-report.json"],
    };

    const fromEntry = GateResultEntrySchema.parse(entry);
    const fromResult = GateResultSchema.parse(entry);

    expect(fromEntry).toEqual(fromResult);
  });

  it("GateResultManifest accepts productUnitId and diagnostics", () => {
    const manifest = GateResultManifestSchema.parse({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T10:00:00.000Z",
      productId: "phr",
      productUnitId: "phr",
      phase: "test",
      overallPassed: false,
      gates: [],
      reasonCode: "gate-failed",
      actionableMessage: "Fix failing gates",
      evidenceRefs: ["gate-report.json"],
      diagnostics: { failedGates: ["hipaa-gate"] },
    });

    expect(manifest.productUnitId).toBe("phr");
    expect(manifest.reasonCode).toBe("gate-failed");
    expect(manifest.diagnostics?.["failedGates"]).toEqual(["hipaa-gate"]);
  });
});

// ---------------------------------------------------------------------------
// ApprovalRequirement standalone schema
// ---------------------------------------------------------------------------

describe("ApprovalRequirement standalone schema", () => {
  it("parses a valid approval requirement", () => {
    const req = ApprovalRequirementSchema.parse({
      approvalId: "security-approval-001",
      approverRole: "security-team",
      required: true,
      phase: "deploy",
      gateId: "security-approval-gate",
      description: "Required for HIPAA-scoped deployments",
      evidenceRefs: ["security-policy.json"],
    });

    expect(req.approvalId).toBe("security-approval-001");
    expect(req.approverRole).toBe("security-team");
    expect(req.required).toBe(true);
    expect(req.phase).toBe("deploy");
  });

  it("parses a minimal approval requirement", () => {
    const req = ApprovalRequirementSchema.parse({
      approvalId: "basic-approval",
      approverRole: "tech-lead",
      required: false,
    });

    expect(req.approvalId).toBe("basic-approval");
    expect(req.phase).toBeUndefined();
  });

  it("rejects missing required fields", () => {
    expect(() =>
      ApprovalRequirementSchema.parse({ approvalId: "x", required: true }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// LifecycleResult / LifecycleExecutionContext alias schemas
// ---------------------------------------------------------------------------

describe("Schema alias correctness", () => {
  it("LifecycleResultSchema parses execution results identically to LifecycleExecutionResultSchema", () => {
    const raw = makeExecutionResult({
      productId: "digital-marketing",
      productUnitId: "digital-marketing",
      status: "succeeded",
    });

    const fromAlias = LifecycleResultSchema.parse(raw);
    const fromDirect = parseLifecycleExecutionResult(raw);

    expect(fromAlias).toEqual(fromDirect);
  });

  it("LifecycleExecutionContextSchema parses execution requests", () => {
    const ctx = LifecycleExecutionContextSchema.parse({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T10:00:00.000Z",
      productId: "digital-marketing",
      phase: "build",
      dryRun: false,
      outputDirectory: ".lifecycle/run-001",
    });

    expect(ctx.productId).toBe("digital-marketing");
    expect(ctx.dryRun).toBe(false);
  });
});
