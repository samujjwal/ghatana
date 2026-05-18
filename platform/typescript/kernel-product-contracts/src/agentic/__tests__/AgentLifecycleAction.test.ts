import { describe, expect, it } from "vitest";
import {
  AgentLifecycleActionRequestSchema,
  AgentLifecycleActionResultSchema,
  isAgentLifecycleActionRequest,
  isAgentLifecycleActionResult,
  parseAgentLifecycleActionRequest,
  type AgentLifecycleActionRequest,
  type AgentLifecycleActionResult,
} from "../index";

const request: AgentLifecycleActionRequest = {
  schemaVersion: "1.0.0",
  requestId: "agent-request-1",
  correlationId: "corr-agent-1",
  productUnitId: "digital-marketing",
  scope: {
    tenantId: "tenant-1",
    workspaceId: "workspace-1",
    projectId: "digital-marketing",
  },
  requestedByAgent: "agent:release-reviewer",
  requestedByAgentVersion: "2026.05.0",
  masteryState: {
    state: "mastered",
    stateRef: "mastery:agent:release-reviewer:2026.05.0",
    evaluatedAt: "2026-05-14T00:00:00.000Z",
  },
  policyDecision: {
    decisionId: "policy:agent-request-1",
    decision: "requires-approval",
    evaluatedAt: "2026-05-14T00:00:00.000Z",
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
  requestedAction: "execute-lifecycle-phase",
  lifecyclePhase: "deploy",
  proposedPlanRef: "lifecycle-plan:run-1",
  riskLevel: "high",
  approvalRequired: true,
  requiredApprovals: [
    {
      approvalId: "deploy-prod-approval",
      approverRole: "release-manager",
      required: true,
    },
  ],
  requiredVerification: [
    {
      verificationId: "verify-health",
      kind: "health",
      required: true,
    },
  ],
  inputRefs: ["input:product-unit-intent:1"],
  outputRefs: ["output:lifecycle-run:run-1"],
  verificationProofRefs: ["verification:health:1"],
  evidenceRefs: ["evidence:policy:1"],
  rollbackPlanRef: "rollback-plan:run-1",
  fallbackMode: "rollback",
};

const result: AgentLifecycleActionResult = {
  schemaVersion: "1.0.0",
  resultId: "agent-result-1",
  requestId: request.requestId,
  correlationId: request.correlationId,
  productUnitId: request.productUnitId,
  policyDecision: "requires-approval",
  masteryDecision: "allowed",
  approvalDecision: "pending",
  lifecycleRunRef: "lifecycle-run:run-1",
  evidenceRefs: ["evidence:policy:1", "approval:deploy-prod-approval"],
  healthStatus: "degraded",
  rollbackReadiness: "ready",
  evaluatedAt: "2026-05-14T00:00:00.000Z",
  request,
};

describe("AgentLifecycleAction contracts", () => {
  it("accepts governed agent lifecycle action requests", () => {
    expect(isAgentLifecycleActionRequest(request)).toBe(true);
    expect(
      AgentLifecycleActionRequestSchema.parse(request).requestedAction,
    ).toBe("execute-lifecycle-phase");
  });

  it("rejects raw Gradle pnpm Docker and shell command fields", () => {
    const rawCommandRequests = [
      { ...request, command: "./gradlew deploy" },
      { ...request, proposedPlanRef: "pnpm build" },
      { ...request, requestedAction: "pnpm build" },
      { ...request, metadata: { dockerCommand: "docker buildx build ." } },
      { ...request, argv: ["pnpm", "test"] },
    ];

    for (const candidate of rawCommandRequests) {
      const parsed = AgentLifecycleActionRequestSchema.safeParse(candidate);
      expect(parsed.success).toBe(false);
      if (!parsed.success) {
        expect(
          parsed.error.issues.map((issue) => issue.message).join(" "),
        ).toMatch(/raw shell\/tool commands|Unrecognized key|Invalid option/);
      }
    }
  });

  it("rejects missing evidence rollback and invalid lifecycle fields", () => {
    const parsed = AgentLifecycleActionRequestSchema.safeParse({
      ...request,
      lifecyclePhase: "ship-it",
      riskLevel: "extreme",
      evidenceRefs: [],
      rollbackPlanRef: "",
    });

    expect(parsed.success).toBe(false);
  });

  it("rejects missing tool permission and denied policy evidence", () => {
    const missingPermission = AgentLifecycleActionRequestSchema.safeParse({
      ...request,
      toolPermissions: [],
    });
    const deniedPolicy = AgentLifecycleActionRequestSchema.safeParse({
      ...request,
      policyDecision: {
        ...request.policyDecision,
        decision: "denied",
        reasonCodes: ["tenant-scope-denied"],
      },
    });

    expect(missingPermission.success).toBe(false);
    expect(deniedPolicy.success).toBe(false);
  });

  it("returns typed reason codes for invalid requests", () => {
    try {
      parseAgentLifecycleActionRequest({
        ...request,
        proposedPlanRef: "pnpm test",
        evidenceRefs: [],
        rollbackPlanRef: "",
      });
    } catch (error) {
      expect(
        (
          error as {
            readonly issues: readonly { readonly reasonCode: string }[];
          }
        ).issues.map((issue) => issue.reasonCode),
      ).toEqual(
        expect.arrayContaining([
          "raw-command-not-allowed",
          "missing-evidence",
          "missing-rollback-plan",
        ]),
      );
      return;
    }

    throw new Error("expected parseAgentLifecycleActionRequest to throw");
  });

  it("accepts governed agent lifecycle action results", () => {
    expect(isAgentLifecycleActionResult(result)).toBe(true);
    expect(
      AgentLifecycleActionResultSchema.parse(result).rollbackReadiness,
    ).toBe("ready");
  });

  it("accepts structured failure and required next action", () => {
    const parsed = AgentLifecycleActionResultSchema.parse({
      ...result,
      failure: {
        reasonCode: "gate-failed",
        message: "Required gate failed",
        evidenceRefs: ["evidence:gate:1"],
      },
      requiredNextAction: "inspect-failure",
    });

    expect(parsed.failure?.reasonCode).toBe("gate-failed");
    expect(parsed.requiredNextAction).toBe("inspect-failure");
  });

  it("rejects result drift and malformed nested requests", () => {
    const parsed = AgentLifecycleActionResultSchema.safeParse({
      ...result,
      policyDecision: "maybe",
      approvalDecision: "auto-approved",
      evidenceRefs: [],
      request: {
        ...request,
        command: "docker buildx build .",
      },
    });

    expect(parsed.success).toBe(false);
  });

  it("keeps request and result type guards narrow", () => {
    expect(isAgentLifecycleActionRequest(null)).toBe(false);
    expect(
      isAgentLifecycleActionResult({ ...result, evaluatedAt: "not-a-date" }),
    ).toBe(false);
  });
});
