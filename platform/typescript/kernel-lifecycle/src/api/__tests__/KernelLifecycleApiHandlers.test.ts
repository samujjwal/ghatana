import { describe, expect, it, vi } from "vitest";
import type { ProductUnit } from "@ghatana/kernel-product-contracts";
import type {
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from "../../domain/ProductLifecyclePhase.js";
import { ManifestNotFoundError } from "../../service/KernelLifecycleErrors.js";
import type { KernelLifecycleService } from "../../service/KernelLifecycleService.js";
import {
  KernelLifecycleApiHandlers,
  type StudioSourceAcquisitionArchivePayload,
  type StudioSourceAcquisitionPayloadStore,
  type StudioSourceInventoryRecord,
  type StudioSourceInventoryStore,
  type StudioWorkflowStoreScope,
} from "../KernelLifecycleApiHandlers.js";

describe("KernelLifecycleApiHandlers", () => {
  it("publishes the canonical v1 ProductUnitIntent route metadata", () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.lifecycle.productUnitIntent.mutate",
      method: "POST",
      path: "/api/v1/kernel/lifecycle/product-unit-intents",
      handler: "mutateProductUnitIntent",
    });
  });

  it("publishes Studio workflow persistence route metadata", () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.workflowState.put",
      method: "PUT",
      path: "/api/v1/studio/workflow-state",
      handler: "putStudioWorkflowState",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.workflowEvidence.put",
      method: "PUT",
      path: "/api/v1/studio/workflow-evidence",
      handler: "putStudioWorkflowEvidence",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.workflowEvidence.get",
      method: "GET",
      path: "/api/v1/studio/workflow-evidence",
      handler: "getStudioWorkflowEvidence",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.sourceAcquisition.repository",
      method: "POST",
      path: "/api/v1/studio/source-acquisition/repository",
      handler: "createStudioRepositorySourceAcquisition",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.sourceAcquisition.archive",
      method: "POST",
      path: "/api/v1/studio/source-acquisition/archive",
      handler: "createStudioArchiveSourceAcquisition",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.sourceAcquisition.job.get",
      method: "GET",
      path: "/api/v1/studio/source-acquisition/jobs/:jobId",
      handler: "getStudioSourceAcquisitionJob",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.sourceAcquisition.inventory.get",
      method: "GET",
      path: "/api/v1/studio/source-acquisition/jobs/:jobId/inventory",
      handler: "getStudioSourceAcquisitionInventory",
    });
    expect(handlers.routeMetadata).toContainEqual({
      routeId: "kernel.studio.sourceAcquisition.job.patch",
      method: "PATCH",
      path: "/api/v1/studio/source-acquisition/jobs/:jobId",
      handler: "patchStudioSourceAcquisitionJob",
    });
  });

  it("rejects missing tenant header when scope is required", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireScopeHeaders: true,
      requireAuthentication: false,
    });

    const response = await handlers.listProductUnits({
      headers: { "X-Correlation-Id": "corr-1" },
    });

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: "scope-headers-required",
      correlationId: "corr-1",
      safeDetails: {
        missingHeaders: [
          "X-Ghatana-Tenant-Id",
          "X-Ghatana-Workspace-Id",
          "X-Ghatana-Project-Id",
        ],
      },
    });
  });

  it("returns ProductUnit list from service with propagated scope and correlation ID", async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.listProductUnits({
      headers: scopedHeaders("corr-2"),
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["x-correlation-id"]).toBe("corr-2");
    expect(response.body).toEqual([productUnit]);
    expect(service.listProductUnits).toHaveBeenCalledWith({
      correlationId: "corr-2",
      scope: {
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        projectId: "project-1",
      },
    });
  });

  it("creates lifecycle plans and returns Studio-compatible plan response", async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.createLifecyclePlan({
      params: { productUnitId: "digital-marketing" },
      headers: scopedHeaders("corr-3"),
      body: {
        phase: "build",
        environment: "local",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.body).toMatchObject({
      runId: "run-1",
      productUnitId: "digital-marketing",
      phase: "build",
      status: "planned",
    });
    expect(service.createLifecyclePlan).toHaveBeenCalledWith(
      "digital-marketing",
      "build",
      expect.objectContaining({
        correlationId: "corr-3",
        environment: "local",
      }),
    );
  });

  it("executes dry-run lifecycle phase and returns run response", async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.executeLifecyclePhase({
      params: { productUnitId: "digital-marketing" },
      headers: scopedHeaders("corr-4"),
      body: {
        phase: "build",
        dryRun: true,
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toMatchObject({
      runId: "run-1",
      productUnitId: "digital-marketing",
      status: "healthy",
    });
    expect(service.runLifecyclePhase).toHaveBeenCalledWith(
      "digital-marketing",
      "build",
      expect.objectContaining({ dryRun: true, correlationId: "corr-4" }),
    );
  });

  it("returns manifest-not-found with safe error response", async () => {
    const service = createService({
      getManifest: vi.fn().mockRejectedValue(
        new ManifestNotFoundError("No manifest", {
          correlationId: "corr-5",
          productUnitId: "digital-marketing",
          runId: "run-1",
        }),
      ),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.getArtifactManifest({
      params: { productUnitId: "digital-marketing", runId: "run-1" },
      headers: scopedHeaders("corr-5"),
    });

    expect(response.statusCode).toBe(404);
    expect(response.body).toMatchObject({
      reasonCode: "manifest-not-found",
      correlationId: "corr-5",
      productUnitId: "digital-marketing",
      runId: "run-1",
    });
  });

  it("lists pending approvals via the approval queue endpoint", async () => {
    const service = createService({
      listPendingApprovals: vi.fn().mockResolvedValue([
        {
          approvalId: "approval-1",
          productUnitId: "digital-marketing",
          runId: "run-1",
          requestedBy: "release-manager",
          reason: "Deploy",
          requiredApprovers: ["alice"],
          expiresAt: "2026-05-16T00:00:00.000Z",
        },
      ]),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.listPendingApprovals({
      headers: scopedHeaders("corr-7"),
      query: { productUnitId: "digital-marketing", runId: "run-1" },
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toEqual([
      expect.objectContaining({
        approvalId: "approval-1",
        productUnitId: "digital-marketing",
      }),
    ]);
    expect(service.listPendingApprovals).toHaveBeenCalledWith(
      expect.objectContaining({
        productUnitId: "digital-marketing",
        runId: "run-1",
        correlationId: "corr-7",
      }),
    );
  });

  it("rejects invalid lifecycle phase requests before calling service", async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.createLifecyclePlan({
      params: { productUnitId: "digital-marketing" },
      headers: scopedHeaders("corr-6"),
      body: { phase: "not-a-phase" },
    });

    expect(response.statusCode).toBe(400);
    expect(response.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-6",
    });
    expect(service.createLifecyclePlan).not.toHaveBeenCalled();
  });

  it("mutates ProductUnitIntent in preview mode through the kernel API route", async () => {
    const service = createService({
      applyProductUnitIntent: vi.fn().mockResolvedValue({
        schemaVersion: "1.0.0",
        intentId: "intent:yappc:commerce-studio:corr-1",
        status: "previewed",
        productUnitId: "commerce-studio",
        correlationId: "corr-1",
        providerMode: "bootstrap",
        registryProviderId: "kernel-product-registry",
        sourceProviderId: "yappc-creator",
        previewRef: "registry://preview/commerce-studio",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      }),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service,
      requireAuthentication: false,
    });

    const response = await handlers.mutateProductUnitIntent({
      headers: scopedHeaders("corr-1"),
      body: {
        requestedAction: "preview",
        providerMode: "bootstrap",
        intent: {
          schemaVersion: "1.0.0",
          intentId: "intent:yappc:commerce-studio:corr-1",
          intentType: "promote-candidate",
          scope: {
            tenantId: "tenant-1",
            workspaceId: "workspace-1",
            projectId: "project-1",
          },
          producer: {
            id: "yappc-ui",
            type: "yappc",
            correlationId: "corr-1",
          },
          target: {
            registryProvider: "kernel-product-registry",
            sourceProvider: "yappc-creator",
          },
          productUnit: {
            id: "commerce-studio",
            name: "Commerce Studio",
            kind: "business-product",
            surfaces: [
              {
                id: "web",
                type: "web",
                implementationStatus: "implemented",
                language: "typescript",
                runtime: "browser",
                buildSystem: "pnpm",
              },
            ],
          },
          provenance: {
            sourceSystem: "yappc",
            sourceArtifactRefs: ["artifact://candidate/commerce-studio"],
            createdBy: "yappc-ui",
            createdAt: "2026-05-16T00:00:00.000Z",
            evidenceRefs: ["evidence://candidate/commerce-studio"],
          },
        },
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toMatchObject({ status: "previewed" });
    expect(service.applyProductUnitIntent).toHaveBeenCalledWith(
      expect.objectContaining({
        intentId: "intent:yappc:commerce-studio:corr-1",
      }),
      { mode: "bootstrap", allowWrite: false },
    );
  });

  it("persists and reloads Studio workflow state by tenant/workspace/project scope", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });
    const body = {
      state: {
        model: { modelId: "model-1" },
        previewSource: "export function App() { return null; }",
      },
      audit: {
        persistedAt: "2026-05-21T00:00:00.000Z",
        lastModifiedAt: "2026-05-21T00:00:00.000Z",
        persistenceVersion: 1,
      },
    };

    const putResponse = await handlers.putStudioWorkflowState({
      headers: scopedHeaders("corr-studio-1"),
      body,
    });
    const getResponse = await handlers.getStudioWorkflowState({
      headers: scopedHeaders("corr-studio-2"),
    });

    expect(putResponse.statusCode).toBe(200);
    expect(getResponse.statusCode).toBe(200);
    expect(getResponse.body).toEqual(body);
  });

  it("keeps Studio workflow state isolated between scopes", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });
    const body = {
      state: { model: { modelId: "tenant-1-model" } },
      audit: {
        persistedAt: "2026-05-21T00:00:00.000Z",
        lastModifiedAt: "2026-05-21T00:00:00.000Z",
        persistenceVersion: 1,
      },
    };

    await handlers.putStudioWorkflowState({
      headers: scopedHeaders("corr-studio-3"),
      body,
    });
    const response = await handlers.getStudioWorkflowState({
      headers: {
        ...scopedHeaders("corr-studio-4"),
        "X-Ghatana-Tenant-Id": "tenant-2",
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.body).toMatchObject({
      reasonCode: "studio-workflow-state-not-found",
      correlationId: "corr-studio-4",
    });
  });

  it("rejects Studio workflow state payloads containing secrets", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const response = await handlers.putStudioWorkflowState({
      headers: scopedHeaders("corr-studio-5"),
      body: {
        state: { authToken: "should-not-persist" },
        audit: {
          persistedAt: "2026-05-21T00:00:00.000Z",
          lastModifiedAt: "2026-05-21T00:00:00.000Z",
          persistenceVersion: 1,
        },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-studio-5",
    });
  });

  it("persists Studio evidence immutably and treats identical writes as idempotent", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });
    const evidence = {
      evidenceId: "ev-1",
      createdAt: "2026-05-21T00:00:00.000Z",
      modelId: "model-1",
      label: "Round trip evidence",
      stage: "round-trip",
    };

    const first = await handlers.putStudioWorkflowEvidence({
      headers: {
        ...scopedHeaders("corr-studio-6"),
        "Idempotency-Key": "idem-1",
      },
      body: evidence,
    });
    const second = await handlers.putStudioWorkflowEvidence({
      headers: {
        ...scopedHeaders("corr-studio-7"),
        "Idempotency-Key": "idem-1",
      },
      body: evidence,
    });
    const conflicting = await handlers.putStudioWorkflowEvidence({
      headers: scopedHeaders("corr-studio-8"),
      body: { ...evidence, label: "Changed evidence" },
    });
    const fetched = await handlers.getStudioWorkflowEvidence({
      headers: scopedHeaders("corr-studio-8-get"),
      query: { evidenceId: "ev-1" },
    });
    const crossTenant = await handlers.getStudioWorkflowEvidence({
      headers: {
        ...scopedHeaders("corr-studio-8-cross"),
        "X-Ghatana-Tenant-Id": "tenant-2",
      },
      query: { evidenceId: "ev-1" },
    });
    const missingQuery = await handlers.getStudioWorkflowEvidence({
      headers: scopedHeaders("corr-studio-8-missing"),
    });

    expect(first.statusCode).toBe(201);
    expect(second.statusCode).toBe(201);
    expect(second.body).toEqual(evidence);
    expect(fetched.statusCode).toBe(200);
    expect(fetched.body).toEqual(evidence);
    expect(crossTenant.statusCode).toBe(404);
    expect(crossTenant.body).toMatchObject({
      reasonCode: "studio-workflow-evidence-not-found",
    });
    expect(missingQuery.statusCode).toBe(400);
    expect(missingQuery.body).toMatchObject({ reasonCode: "invalid-request" });
    expect(conflicting.statusCode).toBe(409);
    expect(conflicting.body).toMatchObject({
      reasonCode: "evidence-immutable",
    });
  });

  it("accepts legacy Studio scope header aliases for workflow persistence", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const response = await handlers.putStudioWorkflowState({
      headers: {
        "X-Correlation-Id": "corr-studio-9",
        "x-tenant-id": "tenant-1",
        "x-workspace-id": "workspace-1",
        "x-project-id": "project-1",
      },
      body: {
        state: { previewSource: "ok" },
        audit: {
          persistedAt: "2026-05-21T00:00:00.000Z",
          lastModifiedAt: "2026-05-21T00:00:00.000Z",
          persistenceVersion: 1,
        },
      },
    });

    expect(response.statusCode).toBe(200);
  });

  it("creates scoped Studio repository source acquisition jobs", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const response = await handlers.createStudioRepositorySourceAcquisition({
      headers: scopedHeaders("corr-source-1"),
      body: {
        input: {
          kind: "github-repository",
          repositoryUrl: "https://github.com/samujjwal/ghatana",
          ref: "main",
        },
        options: {
          maxFileSize: 1_000_000,
          allowedExtensions: [".ts", ".tsx"],
          includeHidden: false,
        },
      },
    });

    expect(response.statusCode).toBe(202);
    expect(response.body).toMatchObject({
      sources: [],
      errors: [],
      partial: false,
      descriptor: {
        kind: "github",
        uri: "https://github.com/samujjwal/ghatana",
        ref: "main",
      },
      acquisitionJob: {
        status: "pending",
        correlationId: "corr-source-1",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        descriptor: {
          kind: "github",
          uri: "https://github.com/samujjwal/ghatana",
          ref: "main",
        },
      },
    });

    const jobId = (
      response.body as { readonly acquisitionJob: { readonly jobId: string } }
    ).acquisitionJob.jobId;
    const jobResponse = await handlers.getStudioSourceAcquisitionJob({
      headers: scopedHeaders("corr-source-1-get"),
      params: { jobId },
    });

    expect(jobResponse.statusCode).toBe(200);
    expect(jobResponse.body).toMatchObject({
      jobId,
      status: "pending",
      correlationId: "corr-source-1",
      scope: {
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        projectId: "project-1",
      },
    });
  });

  it("creates scoped Studio archive source acquisition jobs and stores payload bytes outside the API response", async () => {
    const payloadStore = new TestPayloadStore();
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
      studioSourceAcquisitionPayloadStore: payloadStore,
    });

    const response = await handlers.createStudioArchiveSourceAcquisition({
      headers: scopedHeaders("corr-source-2"),
      body: {
        input: {
          kind: "archive-upload",
          file: {
            name: "source.zip",
            size: 5,
            type: "application/zip",
            contentBase64: "UEsDBAo=",
          },
        },
        options: {
          maxFileSize: 1_000_000,
        },
      },
    });

    expect(response.statusCode).toBe(202);
    expect(JSON.stringify(response.body)).not.toContain("UEsDBAo=");
    expect(response.body).toMatchObject({
      acquisitionJob: {
        status: "pending",
        totalBytes: 5,
        correlationId: "corr-source-2",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        descriptor: {
          kind: "archive",
          uri: "archive://source.zip",
        },
      },
    });
    const jobId = (
      response.body as { readonly acquisitionJob: { readonly jobId: string } }
    ).acquisitionJob.jobId;
    const storedPayload = await payloadStore.getArchivePayload(
      {
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        projectId: "project-1",
      },
      jobId,
    );

    expect(storedPayload).toMatchObject({
      jobId,
      fileName: "source.zip",
      size: 5,
      contentType: "application/zip",
      contentBase64: "UEsDBAo=",
      scope: {
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        projectId: "project-1",
      },
    });
  });

  it("retrieves scoped Studio source acquisition inventory after materialization", async () => {
    const inventoryStore = new TestInventoryStore();
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
      studioSourceInventoryStore: inventoryStore,
    });

    const createResponse = await handlers.createStudioRepositorySourceAcquisition({
      headers: scopedHeaders("corr-source-inventory-create"),
      body: {
        input: {
          kind: "github-repository",
          repositoryUrl: "https://github.com/samujjwal/ghatana",
        },
      },
    });
    const jobId = (
      createResponse.body as { readonly acquisitionJob: { readonly jobId: string } }
    ).acquisitionJob.jobId;
    await inventoryStore.putSourceInventory({
      jobId,
      scope: {
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        projectId: "project-1",
      },
      generatedAt: "2026-05-21T00:05:00.000Z",
      localWorkspacePath: ".kernel/source-acquisition/job",
      fileCount: 1,
      totalBytes: 24,
      files: [
        {
          relativePath: "src/App.tsx",
          size: 24,
          contentType: "text/tsx",
          sha256: "a".repeat(64),
        },
      ],
    });

    const response = await handlers.getStudioSourceAcquisitionInventory({
      headers: scopedHeaders("corr-source-inventory-get"),
      params: { jobId },
    });
    const crossTenant = await handlers.getStudioSourceAcquisitionInventory({
      headers: {
        ...scopedHeaders("corr-source-inventory-cross"),
        "X-Ghatana-Tenant-Id": "tenant-2",
      },
      params: { jobId },
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toMatchObject({
      jobId,
      fileCount: 1,
      totalBytes: 24,
      files: [
        {
          relativePath: "src/App.tsx",
          contentType: "text/tsx",
        },
      ],
    });
    expect(crossTenant.statusCode).toBe(404);
    expect(crossTenant.body).toMatchObject({
      reasonCode: "studio-source-acquisition-job-not-found",
    });
  });

  it("returns not found when source acquisition inventory is not materialized yet", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const createResponse = await handlers.createStudioRepositorySourceAcquisition({
      headers: scopedHeaders("corr-source-inventory-pending"),
      body: {
        input: {
          kind: "github-repository",
          repositoryUrl: "https://github.com/samujjwal/ghatana",
        },
      },
    });
    const jobId = (
      createResponse.body as { readonly acquisitionJob: { readonly jobId: string } }
    ).acquisitionJob.jobId;
    const response = await handlers.getStudioSourceAcquisitionInventory({
      headers: scopedHeaders("corr-source-inventory-missing"),
      params: { jobId },
    });

    expect(response.statusCode).toBe(404);
    expect(response.body).toMatchObject({
      reasonCode: "studio-source-acquisition-inventory-not-found",
      correlationId: "corr-source-inventory-missing",
    });
  });

  it("rejects repository source acquisition for mismatched or credentialed hosts", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const mismatchedHost =
      await handlers.createStudioRepositorySourceAcquisition({
        headers: scopedHeaders("corr-source-3"),
        body: {
          input: {
            kind: "github-repository",
            repositoryUrl: "https://gitlab.com/samujjwal/ghatana",
          },
        },
      });
    const credentialedUrl =
      await handlers.createStudioRepositorySourceAcquisition({
        headers: scopedHeaders("corr-source-4"),
        body: {
          input: {
            kind: "gitlab-repository",
            repositoryUrl: "https://token@gitlab.com/samujjwal/ghatana",
          },
        },
      });

    expect(mismatchedHost.statusCode).toBe(400);
    expect(mismatchedHost.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-source-3",
    });
    expect(credentialedUrl.statusCode).toBe(400);
    expect(credentialedUrl.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-source-4",
    });
  });

  it("rejects unsafe or oversized archive source acquisition requests", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const unsafeName = await handlers.createStudioArchiveSourceAcquisition({
      headers: scopedHeaders("corr-source-5"),
      body: {
        input: {
          kind: "archive-upload",
          file: {
            name: "../source.zip",
            size: 4,
            type: "application/zip",
            contentBase64: "UEsDBAo=",
          },
        },
      },
    });
    const oversized = await handlers.createStudioArchiveSourceAcquisition({
      headers: scopedHeaders("corr-source-6"),
      body: {
        input: {
          kind: "archive-upload",
          file: {
            name: "source.zip",
            size: 50 * 1024 * 1024 + 1,
            type: "application/zip",
            contentBase64: "UEsDBAo=",
          },
        },
      },
    });

    expect(unsafeName.statusCode).toBe(400);
    expect(unsafeName.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-source-5",
    });
    expect(oversized.statusCode).toBe(400);
    expect(oversized.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-source-6",
    });
  });

  it("rejects archive source acquisition when declared size does not match decoded payload bytes", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const response = await handlers.createStudioArchiveSourceAcquisition({
      headers: scopedHeaders("corr-source-size-mismatch"),
      body: {
        input: {
          kind: "archive-upload",
          file: {
            name: "source.zip",
            size: 128,
            type: "application/zip",
            contentBase64: "UEsDBAo=",
          },
        },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.body).toMatchObject({
      reasonCode: "invalid-request",
      correlationId: "corr-source-size-mismatch",
    });
  });

  it("keeps Studio source acquisition jobs isolated between scopes", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });

    const response = await handlers.createStudioRepositorySourceAcquisition({
      headers: scopedHeaders("corr-source-7"),
      body: {
        input: {
          kind: "github-repository",
          repositoryUrl: "https://github.com/samujjwal/ghatana",
        },
      },
    });
    const jobId = (
      response.body as { readonly acquisitionJob: { readonly jobId: string } }
    ).acquisitionJob.jobId;
    const crossScope = await handlers.getStudioSourceAcquisitionJob({
      headers: {
        ...scopedHeaders("corr-source-8"),
        "X-Ghatana-Tenant-Id": "tenant-2",
      },
      params: { jobId },
    });

    expect(crossScope.statusCode).toBe(404);
    expect(crossScope.body).toMatchObject({
      reasonCode: "studio-source-acquisition-job-not-found",
      correlationId: "corr-source-8",
    });
  });

  it("updates Studio source acquisition jobs through worker lifecycle states", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });
    const createResponse = await handlers.createStudioArchiveSourceAcquisition({
      headers: scopedHeaders("corr-source-9"),
      body: {
        input: {
          kind: "archive-upload",
          file: {
            name: "source.tar.gz",
            size: 3,
            type: "application/gzip",
            contentBase64: "H4sI",
          },
        },
      },
    });
    const jobId = (
      createResponse.body as {
        readonly acquisitionJob: { readonly jobId: string };
      }
    ).acquisitionJob.jobId;
    const running = await handlers.patchStudioSourceAcquisitionJob({
      headers: scopedHeaders("corr-source-10"),
      params: { jobId },
      body: {
        status: "running",
        startedAt: "2026-05-21T00:00:00.000Z",
      },
    });
    const completed = await handlers.patchStudioSourceAcquisitionJob({
      headers: scopedHeaders("corr-source-11"),
      params: { jobId },
      body: {
        status: "complete",
        completedAt: "2026-05-21T00:01:00.000Z",
        totalBytes: 512,
        fileCount: 2,
        localWorkspacePath: ".kernel/source-acquisition/job-1",
      },
    });

    expect(running.statusCode).toBe(200);
    expect(running.body).toMatchObject({
      jobId,
      status: "running",
      startedAt: "2026-05-21T00:00:00.000Z",
    });
    expect(completed.statusCode).toBe(200);
    expect(completed.body).toMatchObject({
      jobId,
      status: "complete",
      completedAt: "2026-05-21T00:01:00.000Z",
      totalBytes: 512,
      fileCount: 2,
      localWorkspacePath: ".kernel/source-acquisition/job-1",
    });
  });

  it("rejects invalid Studio source acquisition worker transitions and terminal payloads", async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireAuthentication: false,
    });
    const createResponse =
      await handlers.createStudioRepositorySourceAcquisition({
        headers: scopedHeaders("corr-source-12"),
        body: {
          input: {
            kind: "github-repository",
            repositoryUrl: "https://github.com/samujjwal/ghatana",
          },
        },
      });
    const jobId = (
      createResponse.body as {
        readonly acquisitionJob: { readonly jobId: string };
      }
    ).acquisitionJob.jobId;
    const invalidComplete = await handlers.patchStudioSourceAcquisitionJob({
      headers: scopedHeaders("corr-source-13"),
      params: { jobId },
      body: {
        status: "complete",
        completedAt: "2026-05-21T00:01:00.000Z",
      },
    });
    await handlers.patchStudioSourceAcquisitionJob({
      headers: scopedHeaders("corr-source-14"),
      params: { jobId },
      body: {
        status: "running",
        startedAt: "2026-05-21T00:00:00.000Z",
      },
    });
    const failed = await handlers.patchStudioSourceAcquisitionJob({
      headers: scopedHeaders("corr-source-15"),
      params: { jobId },
      body: {
        status: "failed",
        completedAt: "2026-05-21T00:02:00.000Z",
        errorMessage: "clone failed",
      },
    });
    const invalidAfterTerminal = await handlers.patchStudioSourceAcquisitionJob(
      {
        headers: scopedHeaders("corr-source-16"),
        params: { jobId },
        body: {
          status: "running",
          startedAt: "2026-05-21T00:03:00.000Z",
        },
      },
    );

    expect(invalidComplete.statusCode).toBe(400);
    expect(invalidComplete.body).toMatchObject({
      reasonCode: "invalid-request",
    });
    expect(failed.statusCode).toBe(200);
    expect(failed.body).toMatchObject({
      status: "failed",
      errorMessage: "clone failed",
    });
    expect(invalidAfterTerminal.statusCode).toBe(409);
    expect(invalidAfterTerminal.body).toMatchObject({
      reasonCode: "invalid-acquisition-job-transition",
    });
  });
});

const productUnit: ProductUnit = {
  schemaVersion: "1.0.0",
  id: "digital-marketing",
  name: "Digital Marketing",
  kind: "business-product",
  registryProviderRef: { providerId: "registry" },
  sourceProviderRef: { providerId: "source" },
  surfaces: [
    {
      id: "web",
      type: "web",
      implementationStatus: "implemented",
    },
  ],
};

function scopedHeaders(correlationId: string): Record<string, string> {
  return {
    "X-Correlation-Id": correlationId,
    "X-Ghatana-Tenant-Id": "tenant-1",
    "X-Ghatana-Workspace-Id": "workspace-1",
    "X-Ghatana-Project-Id": "project-1",
  };
}

class TestPayloadStore implements StudioSourceAcquisitionPayloadStore {
  private readonly payloads = new Map<
    string,
    StudioSourceAcquisitionArchivePayload
  >();

  async putArchivePayload(
    payload: StudioSourceAcquisitionArchivePayload,
  ): Promise<void> {
    this.payloads.set(this.key(payload.scope, payload.jobId), payload);
  }

  async getArchivePayload(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceAcquisitionArchivePayload | null> {
    return this.payloads.get(this.key(scope, jobId)) ?? null;
  }

  async deleteArchivePayload(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<void> {
    this.payloads.delete(this.key(scope, jobId));
  }

  private key(scope: StudioWorkflowStoreScope, jobId: string): string {
    return `${scope.tenantId}:${scope.workspaceId}:${scope.projectId}:${jobId}`;
  }
}

class TestInventoryStore implements StudioSourceInventoryStore {
  private readonly inventories = new Map<string, StudioSourceInventoryRecord>();

  async putSourceInventory(
    record: StudioSourceInventoryRecord,
  ): Promise<StudioSourceInventoryRecord> {
    this.inventories.set(this.key(record.scope, record.jobId), record);
    return record;
  }

  async getSourceInventory(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceInventoryRecord | null> {
    return this.inventories.get(this.key(scope, jobId)) ?? null;
  }

  private key(scope: StudioWorkflowStoreScope, jobId: string): string {
    return `${scope.tenantId}:${scope.workspaceId}:${scope.projectId}:${jobId}`;
  }
}

function createService(overrides: Partial<ServiceShape> = {}): ServiceShape {
  return {
    listProductUnits: vi.fn().mockResolvedValue([productUnit]),
    getProductUnit: vi.fn().mockResolvedValue(productUnit),
    createLifecyclePlan: vi.fn().mockResolvedValue(plan),
    runLifecyclePhase: vi.fn().mockResolvedValue(result),
    listLifecycleRuns: vi.fn().mockResolvedValue([]),
    getLifecycleRun: vi.fn().mockResolvedValue({
      runId: "run-1",
      correlationId: "corr-1",
      productUnitId: "digital-marketing",
      phase: "build",
      status: "succeeded",
    }),
    getManifest: vi.fn().mockResolvedValue({ manifest: true }),
    listPendingApprovals: vi.fn().mockResolvedValue([]),
    requestApproval: vi
      .fn()
      .mockResolvedValue({ approvalId: "approval-1", status: "pending" }),
    submitApprovalDecision: vi
      .fn()
      .mockResolvedValue({ approvalId: "approval-1", status: "approved" }),
    applyProductUnitIntent: vi.fn(),
    normalizeError: vi.fn(),
    ...overrides,
  } as unknown as ServiceShape;
}

type ServiceShape = KernelLifecycleService & {
  readonly listProductUnits: ReturnType<typeof vi.fn>;
  readonly getProductUnit: ReturnType<typeof vi.fn>;
  readonly createLifecyclePlan: ReturnType<typeof vi.fn>;
  readonly runLifecyclePhase: ReturnType<typeof vi.fn>;
  readonly listLifecycleRuns: ReturnType<typeof vi.fn>;
  readonly getLifecycleRun: ReturnType<typeof vi.fn>;
  readonly getManifest: ReturnType<typeof vi.fn>;
  readonly listPendingApprovals: ReturnType<typeof vi.fn>;
  readonly requestApproval: ReturnType<typeof vi.fn>;
  readonly submitApprovalDecision: ReturnType<typeof vi.fn>;
  readonly applyProductUnitIntent: ReturnType<typeof vi.fn>;
  readonly normalizeError: ReturnType<typeof vi.fn>;
};

const plan: ProductLifecyclePlan = {
  schemaVersion: "1.0.0",
  runId: "run-1",
  correlationId: "corr-1",
  providerMode: "bootstrap",
  productId: "digital-marketing",
  phase: "build",
  phaseMode: "sequential",
  lifecycleProfile: "standard-web-api-product",
  surfaces: [],
  gates: [],
  steps: [],
  expectedArtifacts: [],
  requiredManifests: [],
  requiredPlugins: [],
  approvalRequirements: [],
  outputDirectory: "/tmp/kernel/run-1",
  estimatedDurationMs: 1,
};

const result: ProductLifecycleResult = {
  schemaVersion: "1.0.0",
  runId: "run-1",
  correlationId: "corr-1",
  providerMode: "bootstrap",
  productId: "digital-marketing",
  phase: "build",
  status: "succeeded",
  startedAt: "2026-05-14T00:00:00.000Z",
  completedAt: "2026-05-14T00:00:01.000Z",
  steps: [],
  gates: [],
  artifacts: [],
  outputDirectory: "/tmp/kernel/run-1",
};
