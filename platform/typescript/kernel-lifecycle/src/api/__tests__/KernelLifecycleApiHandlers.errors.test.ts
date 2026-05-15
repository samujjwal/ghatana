import { describe, expect, it } from "vitest";
import { KernelLifecycleError } from "../../service/KernelLifecycleErrors.js";
import { createKernelLifecycleApiHandlers, type KernelLifecycleApiRequest } from "../KernelLifecycleApiHandlers.js";
import type { KernelLifecycleService } from "../../service/KernelLifecycleService.js";

describe("KernelLifecycleApiHandlers error mapping", () => {
  it("maps ProductUnitNotFoundError to 404", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new KernelLifecycleError({
        reasonCode: "product-unit-not-found",
        message: "Product unit not found",
        correlationId: "corr-1",
      })),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(404);
    expect(response.body).toMatchObject({
      reasonCode: "product-unit-not-found",
      message: "Product unit not found",
    });
  });

  it("maps ProviderUnavailableError to 503", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new KernelLifecycleError({
        reasonCode: "provider-unavailable",
        message: "Provider unavailable",
        correlationId: "corr-1",
      })),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(503);
    expect(response.body).toMatchObject({
      reasonCode: "provider-unavailable",
      message: "Provider unavailable",
    });
  });

  it("maps ApprovalRequiredError to 409", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new KernelLifecycleError({
        reasonCode: "approval-required",
        message: "Approval required",
        correlationId: "corr-1",
      })),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(409);
    expect(response.body).toMatchObject({
      reasonCode: "approval-required",
      message: "Approval required",
    });
  });

  it("maps scope-headers-required to 403", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new KernelLifecycleError({
        reasonCode: "scope-headers-required",
        message: "Scope headers required",
        correlationId: "corr-1",
      })),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: "scope-headers-required",
      message: "Scope headers required",
    });
  });

  it("maps authorization-failed to 403", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new KernelLifecycleError({
        reasonCode: "authorization-failed",
        message: "Authorization failed",
        correlationId: "corr-1",
      })),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: "authorization-failed",
      message: "Authorization failed",
    });
  });

  it("maps validation errors to 400", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new KernelLifecycleError({
        reasonCode: "invalid-request",
        message: "Invalid request",
        correlationId: "corr-1",
      })),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(400);
    expect(response.body).toMatchObject({
      reasonCode: "invalid-request",
      message: "Invalid request",
    });
  });

  it("maps unknown errors to 500", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockServiceThatThrows(new Error("Unknown error")),
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(500);
    expect(response.body).toMatchObject({
      reasonCode: "internal-error",
      message: "Unknown error",
    });
  });
});

function createMockServiceThatThrows(error: Error): KernelLifecycleService {
  return {
    repoRoot: "/test",
    outputRoot: "/test/output",
    registryProvider: {
      providerId: "test-registry",
      version: "1.0.0",
      capabilities: [],
      backingStore: "file",
      listProductUnits: async () => {
        throw error;
      },
      getProductUnit: async () => null,
    },
    sourceProvider: {
      providerId: "test-source",
      version: "1.0.0",
      capabilities: [],
      backingStore: "file",
      getCurrentRef: async () => "main",
      getCommitHash: async () => "abc123",
    },
    planner: {
      providerId: "test-planner",
      version: "1.0.0",
      capabilities: [],
      backingStore: "file",
      plan: async () => ({
        productId: "test",
        runId: "run-1",
        phase: "create",
        status: "planned",
        outputDirectory: "/test/output",
        correlationId: "corr-1",
        providerMode: "bootstrap",
        schemaVersion: "1.0.0",
        phaseMode: "sequential",
        lifecycleProfile: "standard",
        surfaces: [],
        steps: [],
        dependencies: [],
        estimatedDurationMs: 0,
        createdAt: new Date().toISOString(),
        gates: [],
        expectedArtifacts: [],
        requiredManifests: [],
        requiredPlugins: [],
        approvalRequirements: [],
      }),
    },
    executor: undefined,
    pointerStore: {
      readLatestPointers: async () => ({ runId: "run-1", correlationId: "corr-1", providerMode: "bootstrap" }),
      writeLatestPointers: async () => undefined,
    },
    events: undefined,
    artifacts: undefined,
    health: undefined,
    approvals: undefined,
    provenance: undefined,
    memory: undefined,
    runtimeTruth: undefined,
    listProductUnits: async () => {
      throw error;
    },
    getProductUnit: async (id: string) => ({ id, name: "Test Product", description: "", status: "active", labels: [], lifecycle: { currentPhase: "create", phases: [] }, config: {} }),
    createLifecyclePlan: async () => ({
      productId: "test",
      runId: "run-1",
      phase: "create",
      status: "planned",
      outputDirectory: "/test/output",
      correlationId: "corr-1",
      providerMode: "bootstrap",
      schemaVersion: "1.0.0",
      phaseMode: "sequential",
      lifecycleProfile: "standard",
      surfaces: [],
      steps: [],
      dependencies: [],
      estimatedDurationMs: 0,
      createdAt: new Date().toISOString(),
      gates: [],
      expectedArtifacts: [],
      requiredManifests: [],
      requiredPlugins: [],
      approvalRequirements: [],
    }),
    executeLifecyclePlan: async () => ({
      productId: "test",
      status: "succeeded",
      outputDirectory: "/test/output",
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      schemaVersion: "1.0.0",
      runId: "run-1",
      phase: "create",
      steps: [],
      dependencies: [],
      gates: [],
      artifacts: [],
    }),
    runLifecyclePhase: async () => ({
      productId: "test",
      status: "succeeded",
      outputDirectory: "/test/output",
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      schemaVersion: "1.0.0",
      runId: "run-1",
      phase: "create",
      steps: [],
      dependencies: [],
      gates: [],
      artifacts: [],
    }),
    listLifecycleRuns: async () => [],
    getLifecycleRun: async () => ({
      runId: "run-1",
      productUnitId: "test",
      phase: "create",
      status: "succeeded",
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      correlationId: "corr-1",
    }),
    getManifest: async () => ({}),
    requestApproval: async () => ({ approvalId: "approval-1", status: "pending" }),
    submitApprovalDecision: async () => ({ approvalId: "approval-1", status: "approved" }),
    applyProductUnitIntent: async () => ({
      schemaVersion: "1.0.0",
      intentId: "intent-1",
      status: "succeeded",
      productUnitId: "test",
      correlationId: "corr-1",
      providerMode: "bootstrap",
      registryProviderId: "test-registry",
      sourceProviderId: "test-source",
      lifecycleEventRefs: [],
      provenanceRefs: [],
      runtimeTruthRefs: [],
      blockedReasons: [],
      errors: [],
    }),
  } as unknown as KernelLifecycleService;
}
