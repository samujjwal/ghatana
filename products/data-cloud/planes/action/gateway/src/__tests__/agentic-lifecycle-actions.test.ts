import { createHmac } from "node:crypto";
import {
  createServer,
  type IncomingMessage,
  type ServerResponse,
} from "node:http";
import type { AddressInfo } from "node:net";
import type { FastifyInstance } from "fastify";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildApp,
  GatewayMetrics,
  type AgentLifecycleTraceLedgerEntry,
} from "../app.js";

const TEST_SECRET = "agentic-lifecycle-test-secret";

function makeJwt(
  payload: Record<string, unknown>,
  secret = TEST_SECRET,
): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const sig = createHmac("sha256", secret)
    .update(`${header}.${body}`)
    .digest("base64url");
  return `${header}.${body}.${sig}`;
}

function validToken(payload: Record<string, unknown> = {}): string {
  return makeJwt({
    sub: "agent-reviewer-1",
    tenantId: "tenant-1",
    workspaceId: "workspace-1",
    projectId: "project-1",
    exp: Math.floor(Date.now() / 1000) + 3600,
    ...payload,
  });
}

const lifecycleActionRequest = {
  schemaVersion: "1.0.0",
  requestId: "action-req-1",
  correlationId: "corr-agentic-1",
  productUnitId: "digital-marketing",
  scope: {
    tenantId: "tenant-1",
    workspaceId: "workspace-1",
    projectId: "project-1",
  },
  requestedByAgent: "aep-planner",
  requestedByAgentVersion: "2026.05.0",
  masteryState: {
    state: "competent",
    stateRef: "datacloud://mastery/aep-planner/2026.05.0",
    evaluatedAt: "2026-05-14T12:00:00.000Z",
  },
  policyDecision: {
    decisionId: "policy:action-req-1",
    decision: "allowed",
    evaluatedAt: "2026-05-14T12:00:00.000Z",
    reasonCodes: ["policy-allowed"],
    evidenceRefs: ["datacloud://evidence/policy-1"],
  },
  toolPermissions: [
    {
      toolId: "kernel.lifecycle.create-plan",
      permissionRef: "datacloud://permissions/kernel-lifecycle-create-plan",
      granted: true,
      allowedActions: ["create-lifecycle-plan"],
    },
  ],
  requestedAction: "create-lifecycle-plan",
  lifecyclePhase: "build",
  proposedPlanRef: "datacloud://plans/plan-1",
  riskLevel: "medium",
  approvalRequired: false,
  requiredApprovals: [],
  requiredVerification: [
    {
      verificationId: "verify-1",
      kind: "policy",
      required: true,
    },
  ],
  inputRefs: ["datacloud://inputs/product-unit-intent-1"],
  outputRefs: ["datacloud://plans/plan-1"],
  verificationProofRefs: ["datacloud://verification/policy-1"],
  evidenceRefs: ["datacloud://evidence/intent-1"],
  rollbackPlanRef: "datacloud://rollback/plan-1",
  fallbackMode: "dry-run",
} as const;

describe("POST /api/v1/agentic/lifecycle-actions", () => {
  let app: FastifyInstance | undefined;
  let backend: ReturnType<typeof createServer> | undefined;

  afterEach(async () => {
    if (app) {
      await app.close();
      app = undefined;
    }
    if (backend) {
      await new Promise<void>((resolve) => {
        backend?.close(() => resolve());
      });
      backend = undefined;
    }
  });

  it("routes agentic lifecycle actions through the Kernel service without proxying to raw backend tools", async () => {
    let backendCalls = 0;
    backend = createServer((_req: IncomingMessage, res: ServerResponse) => {
      backendCalls += 1;
      res.writeHead(500, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: "raw backend path should not be used" }));
    });
    await new Promise<void>((resolve) => {
      backend?.listen(0, "127.0.0.1", resolve);
    });
    const backendUrl = `http://127.0.0.1:${(backend.address() as AddressInfo).port}`;
    const handle = vi.fn().mockResolvedValue({
      schemaVersion: "1.0.0",
      resultId: "action-result-1",
      requestId: lifecycleActionRequest.requestId,
      correlationId: lifecycleActionRequest.correlationId,
      productUnitId: lifecycleActionRequest.productUnitId,
      policyDecision: "allowed",
      masteryDecision: "allowed",
      approvalDecision: "not-required",
      lifecycleRunRef: "datacloud://runs/run-1",
      evidenceRefs: [
        "datacloud://evidence/intent-1",
        "datacloud://runs/run-1/manifest",
      ],
      healthStatus: "healthy",
      rollbackReadiness: "ready",
      evaluatedAt: "2026-05-14T12:00:00.000Z",
      request: lifecycleActionRequest,
    });
    const traceEntries: AgentLifecycleTraceLedgerEntry[] = [];

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl,
      allowedOrigins: ["http://localhost:5173"],
      agentLifecycleActionService: { handle },
      agentLifecycleTraceLedger: {
        append: (entry) => {
          traceEntries.push(entry);
        },
      },
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken()}`,
        "x-tenant-id": "tenant-1",
        "x-correlation-id": "corr-agentic-1",
      },
      payload: lifecycleActionRequest,
    });

    expect(res.statusCode).toBe(200);
    expect(handle).toHaveBeenCalledWith(lifecycleActionRequest);
    expect(res.json().policyDecision).toBe("allowed");
    expect(res.headers["x-correlation-id"]).toBe("corr-agentic-1");
    expect(backendCalls).toBe(0);
    expect(traceEntries.map((entry) => entry.outcome)).toEqual([
      "received",
      "accepted",
      "fallback-recorded",
    ]);
    expect(traceEntries).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          correlationId: "corr-agentic-1",
          fallbackMode: "dry-run",
        }),
      ]),
    );
  });

  it("fails closed when the governed Kernel service is not configured", async () => {
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://127.0.0.1:1",
      allowedOrigins: ["http://localhost:5173"],
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken()}`,
        "x-tenant-id": "tenant-1",
        "x-correlation-id": "corr-agentic-missing-service",
      },
      payload: lifecycleActionRequest,
    });

    expect(res.statusCode).toBe(503);
    expect(res.json().message).toContain("governed Kernel lifecycle service");
    expect(res.headers["x-correlation-id"]).toBe(
      "corr-agentic-missing-service",
    );
  });

  it("surfaces service validation failures for raw command attempts", async () => {
    const handle = vi
      .fn()
      .mockRejectedValue(new Error("Raw tool command values are not allowed"));
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://127.0.0.1:1",
      allowedOrigins: ["http://localhost:5173"],
      agentLifecycleActionService: { handle },
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken()}`,
        "x-tenant-id": "tenant-1",
      },
      payload: lifecycleActionRequest,
    });

    expect(res.statusCode).toBe(400);
    expect(handle).toHaveBeenCalledOnce();
    expect(res.json().message).toContain("Raw tool command");
  });

  it("fails schema validation when the request has no matching tool permission", async () => {
    const handle = vi.fn();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://127.0.0.1:1",
      allowedOrigins: ["http://localhost:5173"],
      agentLifecycleActionService: { handle },
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken()}`,
        "x-tenant-id": "tenant-1",
      },
      payload: {
        ...lifecycleActionRequest,
        toolPermissions: [],
      },
    });

    expect(res.statusCode).toBe(400);
    expect(handle).not.toHaveBeenCalled();
    expect(res.json().issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "toolPermissions" }),
      ]),
    );
  });

  it("requires approval evidence for risky lifecycle actions", async () => {
    const handle = vi.fn();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://127.0.0.1:1",
      allowedOrigins: ["http://localhost:5173"],
      agentLifecycleActionService: { handle },
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken({ maxRiskLevel: "critical" })}`,
        "x-tenant-id": "tenant-1",
      },
      payload: {
        ...lifecycleActionRequest,
        riskLevel: "high",
        approvalRequired: false,
        requiredApprovals: [],
      },
    });

    expect(res.statusCode).toBe(400);
    expect(handle).not.toHaveBeenCalled();
    expect(res.json().issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "requiredApprovals" }),
      ]),
    );
  });

  it("rejects raw command bypass attempts before the Kernel service is called", async () => {
    const handle = vi.fn();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://127.0.0.1:1",
      allowedOrigins: ["http://localhost:5173"],
      agentLifecycleActionService: { handle },
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken()}`,
        "x-tenant-id": "tenant-1",
      },
      payload: {
        ...lifecycleActionRequest,
        proposedPlanRef: "./gradlew :products:data-cloud:build",
      },
    });

    expect(res.statusCode).toBe(400);
    expect(handle).not.toHaveBeenCalled();
  });

  it("records observable policy denial when the requested tool is outside the token grant", async () => {
    const handle = vi.fn();
    const metrics = new GatewayMetrics();
    const traceEntries: AgentLifecycleTraceLedgerEntry[] = [];
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://127.0.0.1:1",
      allowedOrigins: ["http://localhost:5173"],
      agentLifecycleActionService: { handle },
      metrics,
      agentLifecycleTraceLedger: {
        append: (entry) => {
          traceEntries.push(entry);
        },
      },
    });
    await app.ready();

    const res = await app.inject({
      method: "POST",
      url: "/api/v1/agentic/lifecycle-actions",
      headers: {
        authorization: `Bearer ${validToken({ allowedTools: ["kernel.lifecycle.verify-health"] })}`,
        "x-tenant-id": "tenant-1",
      },
      payload: lifecycleActionRequest,
    });

    expect(res.statusCode).toBe(403);
    expect(res.json().reasonCode).toBe("tool-not-allowed");
    expect(handle).not.toHaveBeenCalled();
    expect(
      metrics.snapshot().agenticActionsByStatus[
        "policy_denied:tool_not_allowed"
      ],
    ).toBe(1);
    expect(traceEntries).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          correlationId: "corr-agentic-1",
          outcome: "policy-denied",
          reasonCode: "tool-not-allowed",
        }),
      ]),
    );
  });
});
