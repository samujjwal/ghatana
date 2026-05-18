import { describe, expect, it } from "vitest";
import { createHmac } from "node:crypto";
import { buildApp, type KernelLifecycleApiPort } from "../app.js";

const TEST_SECRET = "test-secret";

describe("Kernel lifecycle routes error mapping", () => {
  it("returns 503 when kernelLifecycleApi is not configured", async () => {
    const app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: undefined,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    const response = await app.inject({
      method: "GET",
      url: "/api/kernel/product-units",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(503);
    expect(JSON.parse(response.payload)).toMatchObject({
      error: "Service Unavailable",
      message:
        "Kernel lifecycle API requires an injected KernelLifecycleApiHandlers instance",
    });
  });

  it("returns 500 when Kernel API handler throws unexpected error", async () => {
    const mockKernelApi = createMockKernelApiThatThrows(
      new Error("Unexpected error"),
    );
    const app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    const response = await app.inject({
      method: "GET",
      url: "/api/kernel/product-units",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(500);
    expect(JSON.parse(response.payload)).toMatchObject({
      error: "Internal Server Error",
      message: "Kernel lifecycle API handler failed",
    });
  });

  it("propagates Kernel API error responses correctly", async () => {
    const mockKernelApi = createMockKernelApiWithError({
      statusCode: 404,
      headers: { "x-correlation-id": "test-123" },
      body: {
        error: "Not Found",
        message: "Product unit not found",
        reasonCode: "product-unit-not-found",
        correlationId: "test-123",
      },
    });
    const app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    const response = await app.inject({
      method: "GET",
      url: "/api/kernel/product-units/test-product",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(404);
    expect(JSON.parse(response.payload)).toMatchObject({
      error: "Not Found",
      message: "Product unit not found",
      reasonCode: "product-unit-not-found",
    });
  });

  it("records metrics for Kernel lifecycle requests", async () => {
    const mockKernelApi = createMockKernelApi();
    const metrics = await import("../metrics.js").then(
      (m) => new m.GatewayMetrics(),
    );
    const app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: "http://localhost:8080",
      allowedOrigins: ["http://localhost:3000"],
      kernelLifecycleApi: mockKernelApi,
      metrics,
    });

    const token = createTestJwt({ tenantId: "tenant-1" });
    await app.inject({
      method: "GET",
      url: "/api/kernel/product-units",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    const snapshot = metrics.snapshot();
    expect(snapshot.kernelLifecycleRequestsByOperation).toHaveProperty(
      "listProductUnits:200",
    );
  });
});

function createMockKernelApi(): KernelLifecycleApiPort {
  return {
    listProductUnits: async () => ({ statusCode: 200, headers: {}, body: [] }),
    getProductUnit: async () => ({ statusCode: 200, headers: {}, body: {} }),
    createLifecyclePlan: async () => ({
      statusCode: 201,
      headers: {},
      body: {},
    }),
    executeLifecyclePhase: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    listLifecycleRuns: async () => ({ statusCode: 200, headers: {}, body: [] }),
    getLifecycleRun: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getGateResultManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getArtifactManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getDeploymentManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getVerifyHealthReport: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    requestApproval: async () => ({ statusCode: 201, headers: {}, body: {} }),
    submitApprovalDecision: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
  };
}

function createMockKernelApiThatThrows(error: Error): KernelLifecycleApiPort {
  return {
    listProductUnits: async () => {
      throw error;
    },
    getProductUnit: async () => ({ statusCode: 200, headers: {}, body: {} }),
    createLifecyclePlan: async () => ({
      statusCode: 201,
      headers: {},
      body: {},
    }),
    executeLifecyclePhase: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    listLifecycleRuns: async () => ({ statusCode: 200, headers: {}, body: [] }),
    getLifecycleRun: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getGateResultManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getArtifactManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getDeploymentManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getVerifyHealthReport: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    requestApproval: async () => ({ statusCode: 201, headers: {}, body: {} }),
    submitApprovalDecision: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
  };
}

function createMockKernelApiWithError(errorResponse: {
  statusCode: number;
  headers: Record<string, string>;
  body: unknown;
}): KernelLifecycleApiPort {
  return {
    listProductUnits: async () => errorResponse,
    getProductUnit: async () => errorResponse,
    createLifecyclePlan: async () => ({
      statusCode: 201,
      headers: {},
      body: {},
    }),
    executeLifecyclePhase: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    listLifecycleRuns: async () => ({ statusCode: 200, headers: {}, body: [] }),
    getLifecycleRun: async () => ({ statusCode: 200, headers: {}, body: {} }),
    getGateResultManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getArtifactManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getDeploymentManifest: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    getVerifyHealthReport: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
    requestApproval: async () => ({ statusCode: 201, headers: {}, body: {} }),
    submitApprovalDecision: async () => ({
      statusCode: 200,
      headers: {},
      body: {},
    }),
  };
}

function createTestJwt(payload: Record<string, unknown>): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const encodedPayload = Buffer.from(
    JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, ...payload }),
  ).toString("base64url");
  const signature = createHmac("sha256", TEST_SECRET)
    .update(`${header}.${encodedPayload}`)
    .digest("base64url");
  return `${header}.${encodedPayload}.${signature}`;
}
