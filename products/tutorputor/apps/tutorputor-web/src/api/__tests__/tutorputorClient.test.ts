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
    // tenantId is derived from JWT token, no longer stored in localStorage
    // Set a valid token with tenantId instead
    const tokenPayload = btoa(JSON.stringify({ tenantId: "tenant-42", sub: "user-123" }));
    const jwt = `header.${tokenPayload}.signature`;
    window.localStorage.setItem("auth_token", jwt);

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

  it("rejects requests when no tenant context is stored", async () => {
    window.localStorage.removeItem("auth_token");

    await expect(client.getDashboard()).rejects.toThrow(
      "Authentication required: No tenant context found",
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns fallback dashboard data when the backend is unavailable", async () => {
    fetchMock.mockRejectedValueOnce(new Error("Network error"));

    await expect(client.getDashboard()).rejects.toThrow("Network error");
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

    await expect(client.listModules()).rejects.toThrow("API error");
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

    await expect(client.enrollInModule("mod-1")).rejects.toThrow("bad");
  });

  it("falls back to empty search results when search fails", async () => {
    fetchMock.mockRejectedValueOnce(new Error("Search unavailable"));

    await expect(client.search("vectors", { domain: "math" })).rejects.toThrow(
      "Search unavailable",
    );
  });

  it("fetches assessment by ID with GET /v1/assessments/:id", async () => {
    const assessmentPayload = {
      id: "assessment-1",
      title: "Kinematics Quiz",
      items: [{ id: "item-1", stem: "What is velocity?", type: "multiple_choice", choices: [] }],
    };
    fetchMock.mockResolvedValueOnce(jsonResponse(assessmentPayload));

    const result = await client.getAssessment("assessment-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/assessments/assessment-1",
      expect.objectContaining({
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
      }),
    );
    expect(result).toEqual(assessmentPayload);
  });

  it("starts an assessment attempt with POST /v1/assessments/:id/attempts", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: "attempt-99" }));

    const result = await client.startAssessmentAttempt("assessment-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/assessments/assessment-1/attempts",
      expect.objectContaining({ method: "POST" }),
    );
    expect(result).toEqual({ id: "attempt-99" });
  });

  it("submits an assessment attempt with POST /v1/assessment-attempts/:id/submit", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}));

    const responses: Record<string, { type: string; selectedChoiceIds?: string[] }> = {
      "item-1": { type: "multiple_choice", selectedChoiceIds: ["choice-a"] },
    };
    await client.submitAssessmentAttempt("attempt-99", responses);

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/assessment-attempts/attempt-99/submit",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ responses }),
      }),
    );
  });

  it("exports a shared default API client instance", () => {
    expect(apiClient).toBeInstanceOf(TutorPutorApiClient);
  });
});
