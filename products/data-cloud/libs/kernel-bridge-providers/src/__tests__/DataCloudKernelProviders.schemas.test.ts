import { describe, expect, it } from "vitest";
import {
  DataCloudLifecycleEventProvider,
  DataCloudArtifactProvider,
  DataCloudHealthProvider,
  DataCloudApprovalProvider,
  DataCloudProvenanceProvider,
  DataCloudMemoryProvider,
  DataCloudRuntimeTruthProvider,
  DataCloudKernelProviderClient,
  type DataCloudKernelProviderInstrumentation,
} from "../index.js";

describe("DataCloud Kernel Providers schema validation", () => {
  it("invalid list item fails with structured validation error", async () => {
    const mockFetch = async () => ({
      ok: true,
      json: async () => ({ items: [{ invalid: "data" }] }),
    });
    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    await expect(
      provider.listEvents({ productUnitId: "test", correlationId: "corr-1" }),
    ).rejects.toThrow("Data Cloud provider list item has invalid shape");
  });

  it("missing correlation ID is handled gracefully", async () => {
    const mockFetch = async () => ({
      ok: true,
      json: async () => ({ items: [] }),
    });
    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    const result = await provider.listEvents({ productUnitId: "test" });
    expect(result).toEqual([]);
  });

  it("provider error includes reasonCode", async () => {
    const mockFetch = async () => ({
      ok: false,
      status: 400,
      json: async () => ({ error: "Bad request", reasonCode: "invalid-request", correlationId: "corr-1" }),
    });
    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    await expect(
      provider.appendEvent({ metadata: { eventId: "event-1" } }, { correlationId: "corr-1", required: true }),
    ).resolves.toEqual({
      success: false,
      error: "Bad request [reasonCode: invalid-request] [correlationId: corr-1]",
    });
  });

  it("privacy/retention metadata preserved in write options", async () => {
    const mockFetch = async () => ({
      ok: true,
      json: async () => ({ success: true, ref: "ref-123" }),
    });
    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    const result = await provider.appendEvent(
      { metadata: { eventId: "event-1" } },
      {
        correlationId: "corr-1",
        privacyClassification: "confidential",
        retention: { expiresAt: "2025-12-31" },
      },
    );

    expect(result.success).toBe(true);
    expect(result.ref).toBe("ref-123");
  });

  it("timeout returns provider failure", async () => {
    const mockFetch = async (_input: unknown, init?: RequestInit) => {
      await new Promise((resolve) => setTimeout(resolve, 100));
      if (init?.signal?.aborted) {
        throw new DOMException("The operation was aborted.", "AbortError");
      }
      return { ok: true, json: async () => ({ success: true }) };
    };
    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      timeoutMs: 10,
      fetchImpl: mockFetch,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    const result = await provider.appendEvent(
      { metadata: { eventId: "event-1" } },
      { correlationId: "corr-1", required: false },
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("optional Data Cloud provider write skipped");
  });
});

describe("DataCloud Kernel Providers instrumentation", () => {
  it("instrumentation hooks are called on successful request", async () => {
    const startCalls: Array<{ providerId: string; operation: string; method: string; path: string }> = [];
    const completeCalls: Array<{ providerId: string; operation: string; method: string; path: string; statusCode: number; durationMs: number }> = [];
    const failureCalls: Array<{ providerId: string; operation: string; method: string; path: string; durationMs: number; error: string }> = [];

    const mockInstrumentation: DataCloudKernelProviderInstrumentation = {
      recordRequestStart: (params) => startCalls.push(params),
      recordRequestComplete: (params) => completeCalls.push(params),
      recordRequestFailure: (params) => failureCalls.push(params),
    };

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ items: [] }),
    });

    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
      instrumentation: mockInstrumentation,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    await provider.listEvents({ productUnitId: "test", correlationId: "corr-1" });

    expect(startCalls).toHaveLength(1);
    expect(startCalls[0].providerId).toBe("data-cloud-kernel-provider");
    expect(startCalls[0].operation).toContain("events");
    expect(startCalls[0].method).toBe("GET");

    expect(completeCalls).toHaveLength(1);
    expect(completeCalls[0].providerId).toBe("data-cloud-kernel-provider");
    expect(completeCalls[0].statusCode).toBe(200);
    expect(completeCalls[0].durationMs).toBeGreaterThanOrEqual(0);

    expect(failureCalls).toHaveLength(0);
  });

  it("instrumentation hooks are called on failed request", async () => {
    const startCalls: Array<{ providerId: string; operation: string; method: string; path: string }> = [];
    const completeCalls: Array<{ providerId: string; operation: string; method: string; path: string; statusCode: number; durationMs: number }> = [];
    const failureCalls: Array<{ providerId: string; operation: string; method: string; path: string; durationMs: number; error: string }> = [];

    const mockInstrumentation: DataCloudKernelProviderInstrumentation = {
      recordRequestStart: (params) => startCalls.push(params),
      recordRequestComplete: (params) => completeCalls.push(params),
      recordRequestFailure: (params) => failureCalls.push(params),
    };

    const mockFetch = async () => ({
      ok: false,
      status: 500,
      json: async () => ({ error: "Internal server error" }),
    });

    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
      instrumentation: mockInstrumentation,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    await expect(
      provider.appendEvent({ metadata: { eventId: "event-1" } }, { correlationId: "corr-1", required: true }),
    ).resolves.toEqual({ success: false, error: "Internal server error" });

    expect(startCalls).toHaveLength(1);
    expect(failureCalls.length).toBeGreaterThanOrEqual(1);
    expect(failureCalls[0].providerId).toBe("data-cloud-kernel-provider");
    expect(failureCalls[0].error).toContain("Internal server error");
    expect(failureCalls[0].durationMs).toBeGreaterThanOrEqual(0);

    expect(completeCalls).toHaveLength(0);
  });

  it("instrumentation is optional and works without it", async () => {
    const mockFetch = async () => ({
      ok: true,
      json: async () => ({ items: [] }),
    });

    const client = new DataCloudKernelProviderClient({
      baseUrl: "http://localhost",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
      fetchImpl: mockFetch,
      instrumentation: undefined,
    });
    const provider = new DataCloudLifecycleEventProvider(client);

    const result = await provider.listEvents({ productUnitId: "test" });
    expect(result).toEqual([]);
  });
});
