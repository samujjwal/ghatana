import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  apiRequest,
  createBoundRequest,
  createTutorPutorApiClient,
} from "../index.js";
import {
  UnauthorizedError,
  ForbiddenError,
  NotFoundError,
  ServerError,
} from "../errors.js";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mockResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

const BASE_CONFIG = {
  baseUrl: "https://api.tutorputor.test",
  getAccessToken: () => "test-token",
  timeoutMs: 5000,
};

// ---------------------------------------------------------------------------
// apiRequest tests
// ---------------------------------------------------------------------------

describe("apiRequest", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("returns parsed JSON for a 200 response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(mockResponse(200, { hello: "world" })),
    );

    const result = await apiRequest<{ hello: string }>(BASE_CONFIG, "/api/v1/test");
    expect(result.hello).toBe("world");
  });

  it("returns undefined for a 204 No Content response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 204, json: vi.fn() } as unknown as Response),
    );

    const result = await apiRequest<void>(BASE_CONFIG, "/api/v1/test");
    expect(result).toBeUndefined();
  });

  it("throws UnauthorizedError for 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(401, { error: "Unauthorized" })));
    await expect(apiRequest(BASE_CONFIG, "/api/v1/test")).rejects.toBeInstanceOf(UnauthorizedError);
  });

  it("throws ForbiddenError for 403", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(403, {})));
    await expect(apiRequest(BASE_CONFIG, "/api/v1/test")).rejects.toBeInstanceOf(ForbiddenError);
  });

  it("throws NotFoundError for 404", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(404, {})));
    await expect(apiRequest(BASE_CONFIG, "/api/v1/test")).rejects.toBeInstanceOf(NotFoundError);
  });

  it("throws ServerError for 500", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(500, {})));
    await expect(apiRequest(BASE_CONFIG, "/api/v1/test")).rejects.toBeInstanceOf(ServerError);
  });

  it("retries once on 401 when onUnauthorized returns a new token", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(mockResponse(401, {}))
      .mockResolvedValueOnce(mockResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    const onUnauthorized = vi.fn().mockResolvedValue("new-token");
    const config = { ...BASE_CONFIG, onUnauthorized };

    const result = await apiRequest<{ ok: boolean }>(config, "/api/v1/test");
    expect(result.ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("includes Authorization header when access token is set", async () => {
    let capturedInit: RequestInit | undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((_url: string, init: RequestInit) => {
        capturedInit = init;
        return Promise.resolve(mockResponse(200, {}));
      }),
    );

    await apiRequest(BASE_CONFIG, "/api/v1/test");
    const headers = capturedInit?.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe("Bearer test-token");
  });
});

// ---------------------------------------------------------------------------
// createTutorPutorApiClient tests
// ---------------------------------------------------------------------------

describe("createTutorPutorApiClient", () => {
  it("creates a client with all route sub-clients", () => {
    const client = createTutorPutorApiClient(BASE_CONFIG);
    expect(client.auth).toBeDefined();
    expect(client.learning).toBeDefined();
    expect(client.contentStudio).toBeDefined();
    expect(client.analytics).toBeDefined();
  });

  it("auth.me returns the current user", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(mockResponse(200, { id: "u1", email: "a@b.com", role: "teacher", tenantId: "t1", displayName: "A" })),
    );

    const client = createTutorPutorApiClient(BASE_CONFIG);
    const user = await client.auth.me();
    expect(user.id).toBe("u1");
  });

  it("learning.getDashboard calls the correct endpoint", async () => {
    let capturedUrl = "";
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) => {
        capturedUrl = url;
        return Promise.resolve(mockResponse(200, { user: { id: "u1", email: "a@b.com", displayName: "A" }, currentEnrollments: [], recommendedModules: [] }));
      }),
    );

    const client = createTutorPutorApiClient(BASE_CONFIG);
    await client.learning.getDashboard();
    expect(capturedUrl).toBe("https://api.tutorputor.test/api/v1/learning/dashboard");
  });

  it("createBoundRequest can be used independently", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(200, { value: 42 })));
    const request = createBoundRequest(BASE_CONFIG);
    const result = await request<{ value: number }>("/test");
    expect(result.value).toBe(42);
  });
});
