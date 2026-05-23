import { describe, expect, it } from "vitest";
import type { KernelLifecycleService } from "../../service/KernelLifecycleService.js";
import { KernelLifecycleApiHandlers } from "../KernelLifecycleApiHandlers.js";

const baseHeaders = {
  "X-Ghatana-Tenant-Id": "tenant-workflow",
  "X-Ghatana-Workspace-Id": "workspace-workflow",
  "X-Ghatana-Project-Id": "project-workflow",
};

const audit = {
  persistedAt: "2026-05-22T00:00:00.000Z",
  lastModifiedAt: "2026-05-22T00:00:00.000Z",
  persistenceVersion: 1,
};

describe("KernelLifecycleApiHandlers workflow persistence API", () => {
  it("requires authentication when production authentication is enabled", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: {} as KernelLifecycleService,
    });

    const response = await handlers.putStudioWorkflowState({
      headers: {
        ...baseHeaders,
        "X-Correlation-Id": "corr-auth-required",
      },
      body: {
        state: { route: "builder" },
        audit,
      },
    });

    expect(response.statusCode).toBe(401);
    expect(response.body).toMatchObject({
      reasonCode: "authentication-required",
      correlationId: "corr-auth-required",
    });
  });

  it("requires tenant, workspace, and project scope headers", async () => {
    const handlers = createHandlers();

    const response = await handlers.putStudioWorkflowState({
      headers: { "X-Correlation-Id": "corr-scope-required" },
      body: {
        state: { route: "builder" },
        audit,
      },
    });

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: "scope-headers-required",
      correlationId: "corr-scope-required",
    });
  });

  it("saves, loads, and deletes workflow state within a scope", async () => {
    const handlers = createHandlers();
    const state = {
      activeSurface: "builder",
      builderDocumentId: "builder-doc-1",
      selectedNodeIds: ["node-1"],
    };

    const putResponse = await handlers.putStudioWorkflowState({
      headers: scopedHeaders("corr-state-put"),
      body: { state, audit },
    });
    const getResponse = await handlers.getStudioWorkflowState({
      headers: scopedHeaders("corr-state-get"),
    });
    const deleteResponse = await handlers.deleteStudioWorkflowState({
      headers: scopedHeaders("corr-state-delete"),
    });
    const missingResponse = await handlers.getStudioWorkflowState({
      headers: scopedHeaders("corr-state-missing"),
    });

    expect(putResponse.statusCode).toBe(200);
    expect(putResponse.body).toEqual({ state, audit });
    expect(getResponse.statusCode).toBe(200);
    expect(getResponse.body).toEqual({ state, audit });
    expect(deleteResponse.statusCode).toBe(200);
    expect(deleteResponse.body).toEqual({ deleted: true });
    expect(missingResponse.statusCode).toBe(404);
    expect(missingResponse.body).toMatchObject({
      reasonCode: "studio-workflow-state-not-found",
      correlationId: "corr-state-missing",
    });
  });

  it("keeps workflow state isolated by tenant, workspace, and project", async () => {
    const handlers = createHandlers();

    await handlers.putStudioWorkflowState({
      headers: scopedHeaders("corr-state-isolation-put"),
      body: {
        state: { activeSurface: "canvas" },
        audit,
      },
    });

    const crossTenantResponse = await handlers.getStudioWorkflowState({
      headers: {
        ...scopedHeaders("corr-state-isolation-cross"),
        "X-Ghatana-Tenant-Id": "tenant-other",
      },
    });

    expect(crossTenantResponse.statusCode).toBe(404);
    expect(crossTenantResponse.body).toMatchObject({
      reasonCode: "studio-workflow-state-not-found",
    });
  });

  it("persists immutable workflow evidence and isolates it by scope", async () => {
    const handlers = createHandlers();
    const evidence = {
      evidenceId: "evidence-builder-1",
      createdAt: "2026-05-22T00:01:00.000Z",
      modelId: "artifact-authoring",
      label: "Builder edit evidence",
      generatedValidation: {
        status: "passed",
      },
    };

    const putResponse = await handlers.putStudioWorkflowEvidence({
      headers: scopedHeaders("corr-evidence-put"),
      body: evidence,
    });
    const idempotentResponse = await handlers.putStudioWorkflowEvidence({
      headers: scopedHeaders("corr-evidence-idempotent"),
      body: evidence,
    });
    const immutableResponse = await handlers.putStudioWorkflowEvidence({
      headers: scopedHeaders("corr-evidence-immutable"),
      body: {
        ...evidence,
        label: "Changed evidence",
      },
    });
    const getResponse = await handlers.getStudioWorkflowEvidence({
      headers: scopedHeaders("corr-evidence-get"),
      query: { evidenceId: evidence.evidenceId },
    });
    const crossTenantResponse = await handlers.getStudioWorkflowEvidence({
      headers: {
        ...scopedHeaders("corr-evidence-cross"),
        "X-Ghatana-Tenant-Id": "tenant-other",
      },
      query: { evidenceId: evidence.evidenceId },
    });

    expect(putResponse.statusCode).toBe(201);
    expect(idempotentResponse.statusCode).toBe(201);
    expect(immutableResponse.statusCode).toBe(409);
    expect(immutableResponse.body).toMatchObject({ reasonCode: "evidence-immutable" });
    expect(getResponse.statusCode).toBe(200);
    expect(getResponse.body).toEqual(evidence);
    expect(crossTenantResponse.statusCode).toBe(404);
    expect(crossTenantResponse.body).toMatchObject({
      reasonCode: "studio-workflow-evidence-not-found",
    });
  });
});

function createHandlers(): KernelLifecycleApiHandlers {
  return new KernelLifecycleApiHandlers({
    service: {} as KernelLifecycleService,
    requireAuthentication: false,
  });
}

function scopedHeaders(correlationId: string): Record<string, string> {
  return {
    ...baseHeaders,
    "X-Correlation-Id": correlationId,
  };
}
