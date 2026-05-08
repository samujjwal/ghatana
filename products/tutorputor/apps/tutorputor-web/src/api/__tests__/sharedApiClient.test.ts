/**
 * Shared API Client Utilities Tests
 *
 * Tests for canonical header generation, error handling, and request utilities.
 * Ensures consistent auth, tenant, correlation IDs, and error handling across all clients.
 *
 * @doc.type test
 * @doc.purpose Shared API client utilities tests
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  deriveTenantIdFromToken,
  getStandardHeaders,
  handleResponse,
  standardRequest,
} from "../sharedApiClient";

describe("deriveTenantIdFromToken", () => {
  it("should extract tenantId from valid JWT token", () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123", sub: "user-456" }));
    const jwt = `header.${token}.signature`;
    const tenantId = deriveTenantIdFromToken(jwt);
    expect(tenantId).toBe("tenant-123");
  });

  it("should return null for null token", () => {
    const tenantId = deriveTenantIdFromToken(null);
    expect(tenantId).toBeNull();
  });

  it("should return null for token without tenantId", () => {
    const token = btoa(JSON.stringify({ sub: "user-456" }));
    const jwt = `header.${token}.signature`;
    const tenantId = deriveTenantIdFromToken(jwt);
    expect(tenantId).toBeNull();
  });

  it("should return null for malformed token", () => {
    const tenantId = deriveTenantIdFromToken("invalid-token");
    expect(tenantId).toBeNull();
  });
});

describe("getStandardHeaders", () => {
  it("should generate headers with auth, tenant, and correlation ID", () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123" }));
    const jwt = `header.${token}.signature`;
    const headers = getStandardHeaders(jwt) as Record<string, string>;

    expect(headers["Content-Type"]).toBe("application/json");
    expect(headers["X-Tenant-ID"]).toBe("tenant-123");
    expect(headers["Authorization"]).toBe(`Bearer ${jwt}`);
    expect(headers["X-Correlation-ID"]).toBeDefined();
    expect(headers["X-Correlation-ID"]).toMatch(/^[0-9a-f-]{36}$/); // UUID format
  });

  it("should throw when tenantId is missing from token", () => {
    const token = btoa(JSON.stringify({ sub: "user-456" }));
    const jwt = `header.${token}.signature`;

    expect(() => getStandardHeaders(jwt)).toThrow(
      "Authentication required: No tenant context found",
    );
  });

  it("should throw when token is null", () => {
    expect(() => getStandardHeaders(null)).toThrow(
      "Authentication required: No tenant context found",
    );
  });

  it("should allow omitting Content-Type", () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123" }));
    const jwt = `header.${token}.signature`;
    const headers = getStandardHeaders(jwt, { includeContentType: false }) as Record<string, string>;

    expect(headers["Content-Type"]).toBeUndefined();
    expect(headers["X-Tenant-ID"]).toBe("tenant-123");
    expect(headers["Authorization"]).toBe(`Bearer ${jwt}`);
  });

  it("should include additional headers when provided", () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123" }));
    const jwt = `header.${token}.signature`;
    const headers = getStandardHeaders(jwt, {
      additionalHeaders: { "X-Custom-Header": "custom-value" },
    }) as Record<string, string>;

    expect(headers["X-Custom-Header"]).toBe("custom-value");
    expect(headers["X-Tenant-ID"]).toBe("tenant-123");
  });
});

describe("handleResponse", () => {
  it("should parse JSON response for OK status", async () => {
    const mockResponse = {
      ok: true,
      status: 200,
      json: async () => ({ data: "test-data" }),
    } as Response;

    const result = await handleResponse<{ data: string }>(mockResponse);
    expect(result).toEqual({ data: "test-data" });
  });

  it("should return undefined for 204 No Content", async () => {
    const mockResponse = {
      ok: true,
      status: 204,
      json: async () => ({}),
    } as Response;

    const result = await handleResponse<void>(mockResponse);
    expect(result).toBeUndefined();
  });

  it("should throw error with message for non-OK status", async () => {
    const mockResponse = {
      ok: false,
      status: 400,
      statusText: "Bad Request",
      json: async () => ({ message: "Invalid input" }),
    } as Response;

    await expect(handleResponse(mockResponse)).rejects.toThrow("Invalid input");
  });

  it("should throw error with status code attached", async () => {
    const mockResponse = {
      ok: false,
      status: 404,
      statusText: "Not Found",
      json: async () => ({ message: "Resource not found" }),
    } as Response;

    try {
      await handleResponse(mockResponse);
      fail("Should have thrown");
    } catch (error) {
      expect((error as Error & { statusCode: number }).statusCode).toBe(404);
    }
  });

  it("should propagate requestId from error body", async () => {
    const mockResponse = {
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      json: async () => ({ requestId: "req-123", message: "Server error" }),
    } as Response;

    try {
      await handleResponse(mockResponse);
      fail("Should have thrown");
    } catch (error) {
      expect((error as Error & { requestId?: string }).requestId).toBe("req-123");
    }
  });

  it("should propagate traceId from error body", async () => {
    const mockResponse = {
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      json: async () => ({ traceId: "trace-456", error: "Server error" }),
    } as Response;

    try {
      await handleResponse(mockResponse);
      fail("Should have thrown");
    } catch (error) {
      expect((error as Error & { requestId?: string }).requestId).toBe("trace-456");
    }
  });

  it("should use statusText when JSON parsing fails", async () => {
    const mockResponse = {
      ok: false,
      status: 403,
      statusText: "Forbidden",
      json: async () => {
        throw new Error("JSON parse error");
      },
    } as Response;

    await expect(handleResponse(mockResponse)).rejects.toThrow("HTTP 403: Forbidden");
  });
});

describe("standardRequest", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("should make GET request with standard headers", async () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123" }));
    const jwt = `header.${token}.signature`;

    const mockResponse = {
      ok: true,
      status: 200,
      json: async () => ({ result: "success" }),
    } as Response;

    vi.mocked(fetch).mockResolvedValueOnce(mockResponse);

    const result = await standardRequest<{ result: string }>("http://api.example.com/test", {
      method: "GET",
      token: jwt,
    });

    expect(result).toEqual({ result: "success" });
    expect(fetch).toHaveBeenCalledWith(
      "http://api.example.com/test",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          "X-Tenant-ID": "tenant-123",
          "Authorization": `Bearer ${jwt}`,
          "X-Correlation-ID": expect.any(String),
        }),
      }),
    );
  });

  it("should make POST request with body", async () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123" }));
    const jwt = `header.${token}.signature`;

    const mockResponse = {
      ok: true,
      status: 201,
      json: async () => ({ id: "new-123" }),
    } as Response;

    vi.mocked(fetch).mockResolvedValueOnce(mockResponse);

    const result = await standardRequest<{ id: string }>("http://api.example.com/create", {
      method: "POST",
      token: jwt,
      body: { name: "test" },
    });

    expect(result).toEqual({ id: "new-123" });
    expect(fetch).toHaveBeenCalledWith(
      "http://api.example.com/create",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "test" }),
      }),
    );
  });

  it("should handle non-OK responses", async () => {
    const token = btoa(JSON.stringify({ tenantId: "tenant-123" }));
    const jwt = `header.${token}.signature`;

    const mockResponse = {
      ok: false,
      status: 400,
      statusText: "Bad Request",
      json: async () => ({ message: "Invalid request" }),
    } as Response;

    vi.mocked(fetch).mockResolvedValueOnce(mockResponse);

    await expect(
      standardRequest("http://api.example.com/test", {
        method: "GET",
        token: jwt,
      }),
    ).rejects.toThrow("Invalid request");
  });

  it("should throw when tenant context is missing", async () => {
    const token = btoa(JSON.stringify({ sub: "user-456" }));
    const jwt = `header.${token}.signature`;

    await expect(
      standardRequest("http://api.example.com/test", {
        method: "GET",
        token: jwt,
      }),
    ).rejects.toThrow("Authentication required: No tenant context found");
  });
});
