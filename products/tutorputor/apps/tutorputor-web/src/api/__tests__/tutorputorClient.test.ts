/**
 * Test suite for TutorPutorApiClient
 *
 * @doc.type tests
 * @doc.purpose Unit tests for the API client
 * @doc.layer product
 * @doc.pattern Test Suite
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { TutorPutorApiClient, apiClient } from "../tutorputorClient";

describe("TutorPutorApiClient", () => {
  let client: TutorPutorApiClient;
  let fetchMock: ReturnType<typeof vi.fn>;
  let randomUuidSpy: ReturnType<typeof vi.spyOn>;

  const jsonResponse = (
    data: unknown,
    ok: boolean = true,
    status: number = 200,
    statusText: string = "OK",
  ) => ({
    ok,
    status,
    statusText,
    json: vi.fn().mockResolvedValue(data),
  });

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();

    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    randomUuidSpy = vi
      .spyOn(crypto, "randomUUID")
      .mockReturnValue("11111111-1111-4111-8111-111111111111");
    client = new TutorPutorApiClient("/api");
  });

  afterEach(() => {
    vi.clearAllMocks();
    randomUuidSpy.mockRestore();
    vi.unstubAllGlobals();
  });

  it("uses fetch with auth and tenant headers for dashboard requests", async () => {
    window.localStorage.setItem("auth_token", "token-123");
    window.localStorage.setItem("tenant_id", "tenant-42");
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        user: { id: "1", email: "test@test.com", displayName: "Test" },
        currentEnrollments: [],
        recommendedModules: [],
      }),
    );

    await client.getDashboard();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/learning/dashboard",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer token-123",
          "Content-Type": "application/json",
          "X-Correlation-ID": "11111111-1111-4111-8111-111111111111",
          "X-Tenant-ID": "tenant-42",
        }),
      }),
    );
  });

  it("returns fallback dashboard data when the backend is unavailable", async () => {
    fetchMock.mockRejectedValueOnce(new Error("Network error"));

    const result = await client.getDashboard();

    expect(result.user.displayName).toBe("Student User");
    expect(result.currentEnrollments.length).toBeGreaterThan(0);
    expect(result.recommendedModules.length).toBeGreaterThan(0);
  });

  it("serializes query parameters for module listing", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [], nextCursor: null }));

    await client.listModules("physics");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/modules?domain=physics",
      expect.any(Object),
    );
  });

  it("returns fallback module lists when listing fails", async () => {
    fetchMock.mockRejectedValueOnce(new Error("API error"));

    const result = await client.listModules();

    expect(result.items.length).toBeGreaterThan(0);
  });

  it("posts enrollment payloads with fetch", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ enrollment: { id: "enroll-1" } }));

    await client.enrollInModule("mod-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/enrollments",
      expect.objectContaining({
        body: JSON.stringify({ moduleId: "mod-1" }),
        method: "POST",
      }),
    );
  });

  it("throws on non-fallback write failures", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: "bad" }, false, 500, "Server Error"),
    );

    await expect(client.enrollInModule("mod-1")).rejects.toThrow(
      "HTTP 500: Server Error",
    );
  });

  it("falls back to empty search results when search fails", async () => {
    fetchMock.mockRejectedValueOnce(new Error("Search unavailable"));

    const result = await client.search("vectors", { domain: "math" });

    expect(result).toEqual({ results: [], total: 0, facets: {} });
  });

  it("exports a shared default API client instance", () => {
    expect(apiClient).toBeInstanceOf(TutorPutorApiClient);
  });
});
