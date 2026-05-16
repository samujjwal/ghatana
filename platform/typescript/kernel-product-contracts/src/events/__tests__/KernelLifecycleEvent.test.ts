import { describe, expect, it } from "vitest";
import {
  KERNEL_LIFECYCLE_EVENT_TYPES,
  isKernelLifecycleEvent,
  validateKernelLifecycleEvent,
  type KernelLifecycleEvent,
  type KernelLifecycleEventType,
} from "../KernelLifecycleEvent";

function baseEvent(
  eventType: KernelLifecycleEventType,
  payload: KernelLifecycleEvent["payload"]
): KernelLifecycleEvent {
  return {
    metadata: {
      eventId: `event-${eventType}`,
      schemaVersion: "1.0.0",
      eventType,
      productUnitId: "digital-marketing",
      runId: "run-1",
      phase: "build",
      timestamp: "2026-05-14T00:00:00.000Z",
      source: "kernel-lifecycle",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      correlationId: "corr-1",
    },
    payload,
  };
}

const payloadsByType: Record<KernelLifecycleEventType, KernelLifecycleEvent["payload"]> = {
  "product-unit.intent.created": {
    intentId: "intent-1",
    intentType: "create",
    producerId: "yappc",
    producerType: "yappc",
    productUnitDraftId: "digital-marketing",
  },
  "product-unit.intent.validated": {
    intentId: "intent-1",
    valid: true,
    errors: [],
  },
  "product-unit.intent.applied": {
    intentId: "intent-1",
    productUnitId: "digital-marketing",
    applied: true,
    changedFiles: ["config/canonical-product-registry.json"],
  },
  "lifecycle.plan.created": {
    planRunId: "run-1",
    phase: "build",
    providerMode: "bootstrap",
    environment: "local",
    dryRun: false,
    createdAt: "2026-05-14T00:00:00.000Z",
  },
  "lifecycle.phase.started": {
    phase: "build",
    status: "running",
    startedAt: "2026-05-14T00:00:00.000Z",
  },
  "lifecycle.phase.completed": {
    phase: "build",
    status: "succeeded",
    durationMs: 42,
    completedAt: "2026-05-14T00:00:42.000Z",
  },
  "lifecycle.step.started": {
    stepId: "build-web",
    stepKind: "surface",
    surface: "web",
    adapter: "pnpm-vite-react",
    status: "running",
    startedAt: "2026-05-14T00:00:00.000Z",
  },
  "lifecycle.step.completed": {
    stepId: "build-web",
    stepKind: "surface",
    surface: "web",
    adapter: "pnpm-vite-react",
    status: "succeeded",
    durationMs: 42,
    completedAt: "2026-05-14T00:00:42.000Z",
    exitCode: 0,
    evidenceRefs: ["artifact:web-dist"],
  },
  "lifecycle.gate.evaluated": {
    gateId: "typecheck",
    status: "passed",
    required: true,
    reason: "typecheck passed",
    evidenceRefs: ["evidence:typecheck"],
    durationMs: 12,
  },
  "lifecycle.artifact.recorded": {
    artifactId: "artifact-1",
    artifactType: "static-web-bundle",
    required: true,
    path: "dist",
    fingerprint: "sha256:abc",
    evidenceRefs: ["evidence:artifact"],
  },
  "lifecycle.manifest.written": {
    manifestType: "artifact-manifest",
    path: ".kernel/out/run-1/artifact-manifest.json",
    required: true,
    status: "written",
  },
  "lifecycle.deployment.completed": {
    deploymentId: "deployment-1",
    environment: "staging",
    status: "succeeded",
    artifactIds: ["artifact-1"],
    endpoints: ["https://example.test"],
    durationMs: 420,
  },
  "lifecycle.health.checked": {
    checkId: "smoke",
    checkName: "Smoke test",
    status: "healthy",
    message: "smoke test passed",
    durationMs: 23,
    deploymentId: "deployment-1",
    environment: "staging",
  },
  "lifecycle.agent.governance.evaluated": {
    agentId: "agent-1",
    actionType: "apply-plan",
    decision: "allowed",
    reason: "policy matched",
    masteryState: "supervised",
    executionMode: "plan",
    evidenceRefs: ["evidence:policy"],
  },
  "lifecycle.approval.requested": {
    approvalId: "approval-1",
    action: "deploy",
    riskLevel: "high",
    requestedBy: "user:release",
    evidenceRefs: ["evidence:risk"],
  },
  "lifecycle.approval.decided": {
    approvalId: "approval-1",
    decision: "approved",
    decidedBy: "user:approver",
    reason: "release window approved",
  },
};

describe("KernelLifecycleEvent", () => {
  it("validates every canonical lifecycle event payload", () => {
    for (const eventType of KERNEL_LIFECYCLE_EVENT_TYPES) {
      expect(isKernelLifecycleEvent(baseEvent(eventType, payloadsByType[eventType]))).toBe(true);
    }
  });

  it("rejects unknown event types", () => {
    const event = {
      ...baseEvent("lifecycle.gate.evaluated", payloadsByType["lifecycle.gate.evaluated"]),
      metadata: {
        ...baseEvent("lifecycle.gate.evaluated", payloadsByType["lifecycle.gate.evaluated"]).metadata,
        eventType: "lifecycle.unknown",
      },
    };

    const result = validateKernelLifecycleEvent(event);
    expect(result.valid).toBe(false);
    expect(result.errors.some((error) => error.includes("metadata.eventType"))).toBe(true);
  });

  it("rejects payloads that do not match event type", () => {
    const result = validateKernelLifecycleEvent(
      baseEvent("lifecycle.gate.evaluated", {
        artifactId: "artifact-1",
      } as KernelLifecycleEvent["payload"])
    );

    expect(result.valid).toBe(false);
    expect(result.errors.some((error) => error.includes("payload.gateId"))).toBe(true);
  });

  it("rejects missing trace metadata", () => {
    const event = {
      ...baseEvent("lifecycle.manifest.written", payloadsByType["lifecycle.manifest.written"]),
      metadata: {
        ...baseEvent("lifecycle.manifest.written", payloadsByType["lifecycle.manifest.written"]).metadata,
        correlationId: "",
      },
    };

    expect(validateKernelLifecycleEvent(event).errors).toContain(
      "metadata.correlationId: Too small: expected string to have >=1 characters"
    );
  });
});
