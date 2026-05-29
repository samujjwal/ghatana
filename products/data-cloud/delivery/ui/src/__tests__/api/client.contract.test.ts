import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "@/lib/api/client";
import SessionBootstrap from "@/lib/auth/session";
import { TokenStorage } from "@/lib/auth/tokenStorage";

interface MockResponseInit {
  ok?: boolean;
  status?: number;
  statusText?: string;
  body?: unknown;
}

function createJsonResponse(init: MockResponseInit = {}): Response {
  const body = init.body;
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    statusText: init.statusText ?? "OK",
    headers: new Headers({ "content-type": "application/json" }),
    json: vi.fn().mockResolvedValue(body),
    text: vi
      .fn()
      .mockResolvedValue(
        typeof body === "string" ? body : JSON.stringify(body ?? {}),
      ),
    blob: vi.fn(),
  } as unknown as Response;
}

describe("canonical apiClient tenant propagation", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    sessionStorage.clear();
    localStorage.clear();
  });

  it("forwards the bootstrapped tenant header on canonical entity requests", async () => {
    SessionBootstrap.setTenantId("tenant-contract");
    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        body: {
          entities: [],
          count: 0,
        },
      }),
    );

    await apiClient.get("/entities/dc_collections", {
      params: { limit: 5 },
    });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers as HeadersInit);
    expect(headers.get("X-Tenant-ID")).toBe("tenant-contract");
  });

  it("surfaces canonical launcher error envelopes with status and nested error details", async () => {
    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        ok: false,
        status: 403,
        statusText: "Forbidden",
        body: {
          error: {
            code: "INVALID_CONFIRMATION_TOKEN",
            message: "Confirmation token is invalid for this purge request",
            details: {
              confirmationToken: "mismatch",
            },
          },
          meta: {
            requestId: "req-123",
          },
        },
      }),
    );

    await expect(apiClient.get("/governance/purge")).rejects.toMatchObject({
      code: "INVALID_CONFIRMATION_TOKEN",
      message: "Confirmation token is invalid for this purge request",
      status: 403,
      details: {
        confirmationToken: "mismatch",
      },
    });
  });

  it("keeps supporting legacy top-level error bodies during transition", async () => {
    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        ok: false,
        status: 400,
        statusText: "Bad Request",
        body: {
          code: "MISSING_COLLECTION",
          message: "Collection is required",
        },
      }),
    );

    await expect(apiClient.get("/entities")).rejects.toMatchObject({
      code: "MISSING_COLLECTION",
      message: "Collection is required",
      status: 400,
    });
  });

  it("defaults browser requests to credentialed cookie sessions without injecting bearer headers", async () => {
    SessionBootstrap.setTenantId("tenant-cookie");
    TokenStorage.enableCookieSession();
    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        body: {
          entities: [],
          count: 0,
        },
      }),
    );

    await apiClient.get("/entities/dc_collections");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers as HeadersInit);
    expect(init.credentials).toBe("include");
    expect(headers.get("Authorization")).toBeNull();
    expect(headers.get("X-Tenant-ID")).toBe("tenant-cookie");
  });

  it("does not forward X-Tenant-ID in production-like profiles", async () => {
    vi.stubEnv("VITE_DATACLOUD_PROFILE", "production");
    SessionBootstrap.setTenantId("tenant-prod");
    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        body: {
          entities: [],
          count: 0,
        },
      }),
    );

    await apiClient.get("/entities/dc_collections");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers as HeadersInit);
    expect(headers.get("X-Tenant-ID")).toBeNull();
  });

  it("falls back to canonical auth error codes for 401 responses without explicit error codes", async () => {
    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        ok: false,
        status: 401,
        statusText: "Unauthorized",
        body: {
          message: "Authentication required",
        },
      }),
    );

    await expect(apiClient.get("/entities")).rejects.toMatchObject({
      code: "AUTH_REQUIRED",
      message: "Authentication required",
      status: 401,
    });
  });
});
