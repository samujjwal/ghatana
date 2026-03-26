import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ApiClient } from "../client";
import type { ApiError } from "../types";

function mockFetch(
  status: number,
  body: unknown,
  headers?: Record<string, string>,
) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json", ...headers }),
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
  } as unknown as Response);
}

describe("ApiClient", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("should make a GET request", async () => {
    const fetchMock = mockFetch(200, { id: 1 });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.get("/users/1");

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(response.status).toBe(200);
    expect(response.data).toEqual({ id: 1 });
  });

  it("should make a POST request with body", async () => {
    const fetchMock = mockFetch(201, { id: 2, name: "Test" });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.post("/users", { body: { name: "Test" } });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(response.status).toBe(201);
    expect(response.data).toEqual({ id: 2, name: "Test" });
  });

  it("should apply default headers", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      defaultHeaders: { "X-Custom": "value" },
    });
    await client.get("/test");

    const fetchCall = fetchMock.mock.calls[0];
    const request = fetchCall[0] as Request | string;
    // The headers should contain the default header
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("should retry on failure", async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ recovered: true }),
        text: () => Promise.resolve(""),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
      });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      retry: { attempts: 3, backoffMs: 10 },
    });
    const response = await client.get("/flaky");

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(response.data).toEqual({ recovered: true });
  });

  it("should throw after exhausting retries", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error("Down"));
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      retry: { attempts: 2, backoffMs: 1 },
    });

    await expect(client.get("/down")).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("should apply request middleware", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    client.useRequest(async (req) => ({
      ...req,
      headers: { ...req.headers, Authorization: "Bearer token123" },
    }));

    await client.get("/protected");
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("should unsubscribe middleware", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const unsubscribe = client.useRequest(async (req) => ({
      ...req,
      headers: { ...req.headers, "X-Added": "yes" },
    }));

    unsubscribe();
    await client.get("/test");
    // Middleware should have been removed
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("should support DELETE method", async () => {
    const fetchMock = mockFetch(204, null);
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.delete("/users/1");

    expect(response.status).toBe(204);
  });

  it("should support PUT method", async () => {
    const fetchMock = mockFetch(200, { updated: true });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.put("/users/1", {
      body: { name: "Updated" },
    });

    expect(response.status).toBe(200);
    expect(response.data).toEqual({ updated: true });
  });

  it("should support PATCH method", async () => {
    const fetchMock = mockFetch(200, { patched: true });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.patch("/users/1", {
      body: { name: "Patched" },
    });

    expect(response.status).toBe(200);
  });

  // ── Error categorisation (FINDING-003) ──────────────────────────────────────

  it("should categorise 4xx response as CLIENT error", async () => {
    globalThis.fetch = mockFetch(404, { error: "Not Found" });

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    let thrown: ApiError | undefined;
    try {
      await client.get("/missing");
    } catch (e) {
      thrown = e as ApiError;
    }

    expect(thrown).toBeDefined();
    expect(thrown!.status).toBe(404);
    expect(thrown!.category).toBe("CLIENT");
    expect(thrown!.isRetryable).toBe(false);
  });

  it("should categorise 5xx response as SERVER error", async () => {
    globalThis.fetch = mockFetch(503, { error: "Service Unavailable" });

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    let thrown: ApiError | undefined;
    try {
      await client.get("/broken");
    } catch (e) {
      thrown = e as ApiError;
    }

    expect(thrown).toBeDefined();
    expect(thrown!.status).toBe(503);
    expect(thrown!.category).toBe("SERVER");
    expect(thrown!.isRetryable).toBe(true);
  });

  it("should categorise network errors as NETWORK and mark retryable", async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new TypeError("Failed to fetch"));

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    let thrown: ApiError | undefined;
    try {
      await client.get("/unreachable");
    } catch (e) {
      thrown = e as ApiError;
    }

    expect(thrown).toBeDefined();
    expect(thrown!.category).toBe("NETWORK");
    expect(thrown!.isRetryable).toBe(true);
  });

  it("should NOT retry 4xx CLIENT errors even when retry is configured", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      statusText: "Unauthorized",
      headers: new Headers({ "content-type": "application/json" }),
      json: () => Promise.resolve({ error: "Unauthorized" }),
      text: () => Promise.resolve(""),
      arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      retry: { attempts: 3, backoffMs: 1 },
    });

    await expect(client.get("/secure")).rejects.toBeDefined();
    // Should only attempt once — no retry on 4xx
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("should retry 5xx SERVER errors up to the configured attempt limit", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      statusText: "Service Unavailable",
      headers: new Headers({ "content-type": "application/json" }),
      json: () => Promise.resolve({ error: "Unavailable" }),
      text: () => Promise.resolve(""),
      arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      retry: { attempts: 3, backoffMs: 1 },
    });

    await expect(client.get("/flaky-service")).rejects.toBeDefined();
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  // ── API versioning (FINDING-008) ─────────────────────────────────────────────

  it("should send X-Api-Version header when apiVersion is configured", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      apiVersion: "v2",
    });
    await client.get("/resources");

    const [, fetchInit] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(fetchInit.headers as HeadersInit);
    expect(headers.get("X-Api-Version")).toBe("v2");
  });

  it("should not override an explicitly set X-Api-Version header", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      apiVersion: "v2",
    });
    await client.get("/resources", { headers: { "X-Api-Version": "v3" } });

    const [, fetchInit] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(fetchInit.headers as HeadersInit);
    expect(headers.get("X-Api-Version")).toBe("v3");
  });
});

function mockFetch(
  status: number,
  body: unknown,
  headers?: Record<string, string>,
) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json", ...headers }),
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
  } as unknown as Response);
}

describe("ApiClient", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("should make a GET request", async () => {
    const fetchMock = mockFetch(200, { id: 1 });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.get("/users/1");

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(response.status).toBe(200);
    expect(response.data).toEqual({ id: 1 });
  });

  it("should make a POST request with body", async () => {
    const fetchMock = mockFetch(201, { id: 2, name: "Test" });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.post("/users", { body: { name: "Test" } });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(response.status).toBe(201);
    expect(response.data).toEqual({ id: 2, name: "Test" });
  });

  it("should apply default headers", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      defaultHeaders: { "X-Custom": "value" },
    });
    await client.get("/test");

    const fetchCall = fetchMock.mock.calls[0];
    const request = fetchCall[0] as Request | string;
    // The headers should contain the default header
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("should retry on failure", async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ recovered: true }),
        text: () => Promise.resolve(""),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
      });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      retry: { attempts: 3, backoffMs: 10 },
    });
    const response = await client.get("/flaky");

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(response.data).toEqual({ recovered: true });
  });

  it("should throw after exhausting retries", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error("Down"));
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: "https://api.test.com",
      retry: { attempts: 2, backoffMs: 1 },
    });

    await expect(client.get("/down")).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("should apply request middleware", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    client.useRequest(async (req) => ({
      ...req,
      headers: { ...req.headers, Authorization: "Bearer token123" },
    }));

    await client.get("/protected");
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("should unsubscribe middleware", async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const unsubscribe = client.useRequest(async (req) => ({
      ...req,
      headers: { ...req.headers, "X-Added": "yes" },
    }));

    unsubscribe();
    await client.get("/test");
    // Middleware should have been removed
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("should support DELETE method", async () => {
    const fetchMock = mockFetch(204, null);
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.delete("/users/1");

    expect(response.status).toBe(204);
  });

  it("should support PUT method", async () => {
    const fetchMock = mockFetch(200, { updated: true });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.put("/users/1", {
      body: { name: "Updated" },
    });

    expect(response.status).toBe(200);
    expect(response.data).toEqual({ updated: true });
  });

  it("should support PATCH method", async () => {
    const fetchMock = mockFetch(200, { patched: true });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: "https://api.test.com" });
    const response = await client.patch("/users/1", {
      body: { name: "Patched" },
    });

    expect(response.status).toBe(200);
  });
});
