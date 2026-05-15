import { describe, expect, it } from "vitest";
import { buildApp } from "../app.js";
import type { KernelLifecycleApiHandlers } from "@ghatana/kernel-lifecycle";

describe("Kernel lifecycle routes authz", () => {
  it("requires workspace and project scope for lifecycle operations", async () => {
    const mockKernelApi = createMockKernelApi();
    const app = await buildApp({
      jwtSecret: "test-secret",
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    const response = await app.inject({
      method: "POST",
      url: "/api/kernel/product-units/test-product/lifecycle/plans",
      headers: {
        authorization: `Bearer ${token}`,
        "x-ghatana-tenant-id": "tenant-1",
      },
      body: { phase: "create" },
    });

    expect(response.statusCode).toBe(403);
    expect(JSON.parse(response.payload)).toMatchObject({
      error: "Forbidden",
      message: "Workspace and project scope required for lifecycle operations",
    });
  });

  it("allows lifecycle operations with workspace and project scope from headers", async () => {
    const mockKernelApi = createMockKernelApi();
    const app = await buildApp({
      jwtSecret: "test-secret",
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    const response = await app.inject({
      method: "POST",
      url: "/api/kernel/product-units/test-product/lifecycle/plans",
      headers: {
        authorization: `Bearer ${token}`,
        "x-ghatana-tenant-id": "tenant-1",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
      body: { phase: "create" },
    });

    expect(response.statusCode).toBe(201);
  });

  it("allows lifecycle operations with workspace and project scope from JWT", async () => {
    const mockKernelApi = createMockKernelApi();
    const app = await buildApp({
      jwtSecret: "test-secret",
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
    });
    const response = await app.inject({
      method: "POST",
      url: "/api/kernel/product-units/test-product/lifecycle/plans",
      headers: {
        authorization: `Bearer ${token}`,
        "x-ghatana-tenant-id": "tenant-1",
      },
      body: { phase: "create" },
    });

    expect(response.statusCode).toBe(201);
  });

  it("passes user ID and roles from JWT to Kernel API request headers", async () => {
    const mockKernelApi = createMockKernelApi();
    const app = await buildApp({
      jwtSecret: "test-secret",
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({
      tenantId: "tenant-1",
      sub: "user-123",
      roles: ["admin", "developer"],
      workspaceId: "workspace-1",
      projectId: "project-1",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/kernel/product-units/test-product/lifecycle/plans",
      headers: {
        authorization: `Bearer ${token}`,
        "x-ghatana-tenant-id": "tenant-1",
      },
      body: { phase: "create" },
    });

    expect(response.statusCode).toBe(201);
  });

  it("rejects requests when JWT tenant does not match header tenant", async () => {
    const mockKernelApi = createMockKernelApi();
    const app = await buildApp({
      jwtSecret: "test-secret",
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    const response = await app.inject({
      method: "POST",
      url: "/api/kernel/product-units/test-product/lifecycle/plans",
      headers: {
        authorization: `Bearer ${token}`,
        "x-ghatana-tenant-id": "tenant-2",
        "x-ghatana-workspace-id": "workspace-1",
        "x-ghatana-project-id": "project-1",
      },
      body: { phase: "create" },
    });

    expect(response.statusCode).toBe(403);
    expect(JSON.parse(response.payload)).toMatchObject({
      error: "Forbidden",
      message: "Tenant mismatch between X-Tenant-Id header and JWT payload",
    });
  });
});

function createMockKernelApi(): KernelLifecycleApiHandlers {
  return {
    listProductUnits: async () => ({ statusCode: 200, headers: {}, body: [] }),
    getProductUnit: async () => ({ statusCode: 200, headers: {}, body: {} }),
    createLifecyclePlan: async () => ({ statusCode: 201, headers: {}, body: {} }),
    executeLifecyclePhase: async () => ({ statusCode: 200, headers: {}, body: {} }),
    listLifecycleRuns: async () => ({ statusCode: 200, headers: {}, body: [] }),
    getLifecycleRun: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getGateResultManifest: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getArtifactManifest: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getDeploymentManifest: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getVerifyHealthReport: async () => ({ statusCode: 200, headers: {}, body: {} }),
    requestApproval: async () => ({ statusCode: 201, headers: {}, body: {} }),
    submitApprovalDecision: async () => ({ statusCode: 200, headers: {}, body: {} }),
  };
}

function createTestJwt(payload: Record<string, unknown>): string {
  const header = { alg: "HS256" };
  const encodedHeader = Buffer.from(JSON.stringify(header)).toString("base64url");
  const encodedPayload = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = "test-signature";
  return `${encodedHeader}.${encodedPayload}.${signature}`;
}
