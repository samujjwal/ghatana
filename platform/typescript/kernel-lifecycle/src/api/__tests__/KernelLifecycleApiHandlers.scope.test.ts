import { describe, expect, it } from "vitest";
import { createKernelLifecycleApiHandlers, type KernelLifecycleApiRequest } from "../KernelLifecycleApiHandlers.js";
import type { KernelLifecycleService } from "../../service/KernelLifecycleService.js";

describe("KernelLifecycleApiHandlers scope validation", () => {
  it("requires scope headers when requireScopeHeaders is true", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: true,
      requireAuthentication: false,
    });

    const request: KernelLifecycleApiRequest = {
      headers: {},
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: "scope-headers-required",
      message: "Kernel lifecycle API requires tenant, workspace, and project headers",
    });
  });

  it("allows requests with complete scope headers", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: true,
      requireAuthentication: false,
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(200);
  });

  it("allows unscoped requests when allowUnscopedLocalDevelopment is true", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: true,
      allowUnscopedLocalDevelopment: true,
      requireAuthentication: false,
    });

    const request: KernelLifecycleApiRequest = {
      headers: {},
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(200);
  });

  it("allows unscoped requests when requireScopeHeaders is false", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: false,
      requireAuthentication: false,
    });

    const request: KernelLifecycleApiRequest = {
      headers: {},
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(200);
  });

  it("reports missing scope headers in safeDetails", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: true,
      requireAuthentication: false,
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-ghatana-tenant-id": "tenant-1",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      safeDetails: {
        missingHeaders: ["X-Ghatana-Workspace-Id", "X-Ghatana-Project-Id"],
      },
    });
  });

  it("includes correlationId from headers in error response", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: true,
      requireAuthentication: false,
    });

    const request: KernelLifecycleApiRequest = {
      headers: {
        "x-correlation-id": "test-correlation-123",
      },
    };

    const response = await handlers.listProductUnits(request);

    expect(response.statusCode).toBe(403);
    expect((response.body as { readonly correlationId: string }).correlationId).toBe("test-correlation-123");
  });

  it("requires scope headers for Studio source acquisition even when generic scope enforcement is disabled", async () => {
    const handlers = createKernelLifecycleApiHandlers({
      service: createMockService(),
      requireScopeHeaders: false,
      requireAuthentication: false,
    });

    const response = await handlers.createStudioRepositorySourceAcquisition({
      headers: { "x-correlation-id": "source-scope-required" },
      body: {
        input: {
          kind: "github-repository",
          repositoryUrl: "https://github.com/samujjwal/ghatana",
        },
      },
    });

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: "scope-headers-required",
      correlationId: "source-scope-required",
    });
  });

  describe("tenant/workspace isolation", () => {
    it("allows requests when tenant and workspace match", async () => {
      const handlers = createKernelLifecycleApiHandlers({
        service: createMockService(),
        requireScopeHeaders: true,
        requireAuthentication: false,
      });

      const request: KernelLifecycleApiRequest = {
        headers: {
          "x-ghatana-tenant-id": "tenant-1",
          "x-ghatana-workspace-id": "workspace-1",
          "x-ghatana-project-id": "project-1",
        },
      };

      const response = await handlers.listProductUnits(request);

      expect(response.statusCode).toBe(200);
    });

    it.skip("documents expected project ID scope validation (not yet implemented) // GH-1001", async () => {
      // This test documents that project ID scope should be validated in plan creation
      // TODO: Implement project ID scope validation in plan creation
    });

    it.skip("documents expected tenant mismatch behavior (not yet implemented) // GH-1002", async () => {
      // This test documents the expected behavior for tenant isolation
      // The actual implementation should validate that the requested product unit
      // belongs to the tenant specified in the scope headers
      // TODO: Implement tenant mismatch validation
    });

    it.skip("documents expected workspace mismatch behavior (not yet implemented) // GH-1003", async () => {
      // This test documents the expected behavior for workspace isolation
      // The actual implementation should validate that lifecycle execution
      // is scoped to the workspace specified in the headers
      // TODO: Implement workspace mismatch validation
    });
  });

  describe("scope header validation edge cases", () => {
    it("normalizes header names case-insensitively", async () => {
      const handlers = createKernelLifecycleApiHandlers({
        service: createMockService(),
        requireScopeHeaders: true,
        requireAuthentication: false,
      });

      const request: KernelLifecycleApiRequest = {
        headers: {
          "X-GHATANA-TENANT-ID": "tenant-1",
          "x-ghatana-workspace-id": "workspace-1",
          "X-GHATANA-PROJECT-ID": "project-1",
        },
      };

      const response = await handlers.listProductUnits(request);

      expect(response.statusCode).toBe(200);
    });

    it.skip("documents expected empty tenant ID validation (not yet implemented) // GH-1004", async () => {
      // This test documents that empty tenant IDs should be rejected
      // TODO: Implement empty tenant ID validation
    });

    it.skip("documents expected malformed tenant ID validation (not yet implemented) // GH-1005", async () => {
      // This test documents that tenant IDs with special characters should be rejected
      // TODO: Implement malformed tenant ID validation
    });
  });
});

function createMockService(): KernelLifecycleService {
  return {
    repoRoot: "/test",
    outputRoot: "/test/output",
    registryProvider: {
      providerId: "test-registry",
      version: "1.0.0",
      capabilities: [],
      backingStore: "file",
      listProductUnits: async () => [],
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
    listProductUnits: async () => [],
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
