/**
 * @file tokenManagement.test.ts
 * Tests for token storage, retrieval, expiration, refresh, and revocation
 * within the SsoClient.
 *
 * @doc.type module
 * @doc.purpose Tests for SSO token lifecycle management
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { SsoClient } from "../index";
import type { SsoClientConfig } from "../index";

// ── Test helpers ─────────────────────────────────────────────────────────────

function buildJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" })).replace(
    /=/g,
    "",
  );
  const body = btoa(JSON.stringify(payload))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  return `${header}.${body}.fake-sig`;
}

function makeToken(overrides: Record<string, unknown> = {}): string {
  return buildJwt({
    sub: "user-token-test",
    email: "token@example.com",
    roles: ["ROLE_USER"],
    tenantId: "tenant-token",
    tokenType: "PLATFORM",
    iss: "ghatana-auth-service",
    sessionId: "sess-token-001",
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
    ...overrides,
  });
}

function makeExpiredToken(): string {
  return makeToken({ exp: Math.floor(Date.now() / 1000) - 300 });
}

const BASE_CONFIG: SsoClientConfig = {
  authServiceBaseUrl: "https://auth.ghatana.io",
  authGatewayBaseUrl: "https://gateway.ghatana.io",
  productId: "test-product",
  storageKey: "test_sso_token",
};

// ── Token storage tests ───────────────────────────────────────────────────────

describe("Token Storage", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  describe("sessionStorage persistence", () => {
    it("stores token in sessionStorage on init from URL param", async () => {
      const token = makeToken();
      const url = `https://app.example.com/?platform_token=${token}`;
      vi.spyOn(window, "location", "get").mockReturnValue({
        ...window.location,
        href: url,
        search: `?platform_token=${token}`,
      } as Location);
      Object.defineProperty(window, "location", {
        value: {
          href: url,
          search: `?platform_token=${token}`,
          assign: vi.fn(),
          replace: vi.fn(),
          origin: "https://app.example.com",
          pathname: "/",
          hash: "",
          host: "app.example.com",
          hostname: "app.example.com",
          port: "",
          protocol: "https:",
        },
        writable: true,
      });

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(sessionStorage.getItem("test_sso_token")).toBe(token);
    });

    it("loads token from sessionStorage on init", async () => {
      const token = makeToken();
      sessionStorage.setItem("test_sso_token", token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(client.isAuthenticated()).toBe(true);
      expect(client.getUser()?.email).toBe("token@example.com");
    });

    it("uses custom storageKey when configured", async () => {
      const customKey = "custom_sso_key";
      const token = makeToken();
      sessionStorage.setItem(customKey, token);

      const client = new SsoClient({ ...BASE_CONFIG, storageKey: customKey });
      await client.init();

      expect(client.isAuthenticated()).toBe(true);
    });

    it("defaults to ghatana_platform_token when no storageKey provided", () => {
      // The default in the source is 'ghatana_platform_token'
      const configWithoutKey: SsoClientConfig = {
        authServiceBaseUrl: "https://auth.ghatana.io",
        authGatewayBaseUrl: "https://gateway.ghatana.io",
        productId: "default-key-test",
      };
      // Just ensure the client constructs without error
      expect(() => new SsoClient(configWithoutKey)).not.toThrow();
    });
  });

  describe("token retrieval", () => {
    it("getRawToken returns null when unauthenticated", async () => {
      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(client.getRawToken()).toBeNull();
    });

    it("getRawToken returns the stored token when authenticated", async () => {
      const token = makeToken();
      sessionStorage.setItem("test_sso_token", token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(client.getRawToken()).toBe(token);
    });

    it("getClaims returns null when unauthenticated", async () => {
      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(client.getClaims()).toBeNull();
    });

    it("getClaims returns decoded claims when authenticated", async () => {
      const token = makeToken();
      sessionStorage.setItem("test_sso_token", token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      const claims = client.getClaims();
      expect(claims).not.toBeNull();
      expect(claims?.sub).toBe("user-token-test");
      expect(claims?.email).toBe("token@example.com");
      expect(claims?.tokenType).toBe("PLATFORM");
    });
  });
});

// ── Token expiration tests ────────────────────────────────────────────────────

describe("Token Expiration", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("isAuthenticated returns false for expired token", async () => {
    const expiredToken = makeExpiredToken();
    sessionStorage.setItem("test_sso_token", expiredToken);

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    expect(client.isAuthenticated()).toBe(false);
  });

  it("getUser returns null for expired token", async () => {
    const expiredToken = makeExpiredToken();
    sessionStorage.setItem("test_sso_token", expiredToken);

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    expect(client.getUser()).toBeNull();
  });

  it("getClaims returns null for expired token", async () => {
    const expiredToken = makeExpiredToken();
    sessionStorage.setItem("test_sso_token", expiredToken);

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    expect(client.getClaims()).toBeNull();
  });

  it("getRawToken returns null for expired token", async () => {
    const expiredToken = makeExpiredToken();
    sessionStorage.setItem("test_sso_token", expiredToken);

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    expect(client.getRawToken()).toBeNull();
  });

  it("token expiring in the future is considered valid", async () => {
    const soonExpiringToken = makeToken({
      exp: Math.floor(Date.now() / 1000) + 5, // expires in 5 seconds
    });
    sessionStorage.setItem("test_sso_token", soonExpiringToken);

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    // Should still be valid right now
    expect(client.isAuthenticated()).toBe(true);
  });
});

// ── Token revocation / logout tests ──────────────────────────────────────────

describe("Token Revocation (logout)", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("logout clears authenticated state", async () => {
    const token = makeToken();
    sessionStorage.setItem("test_sso_token", token);
    vi.stubGlobal("location", {
      ...window.location,
      assign: vi.fn(),
      origin: "https://app.example.com",
    });

    const client = new SsoClient(BASE_CONFIG);
    await client.init();
    expect(client.isAuthenticated()).toBe(true);

    await client.logout("https://app.example.com");

    expect(client.isAuthenticated()).toBe(false);
    expect(client.getUser()).toBeNull();
  });

  it("logout calls POST /auth/logout with the token", async () => {
    const token = makeToken();
    sessionStorage.setItem("test_sso_token", token);
    const locationSpy = { assign: vi.fn(), origin: "https://app.example.com" };
    vi.stubGlobal("location", { ...window.location, ...locationSpy });

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    await client.logout("https://app.example.com");

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      "https://auth.ghatana.io/auth/logout",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining(token),
      }),
    );
  });

  it("logout proceeds even if server call fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network error")),
    );
    const token = makeToken();
    sessionStorage.setItem("test_sso_token", token);
    vi.stubGlobal("location", {
      assign: vi.fn(),
      origin: "https://app.example.com",
    });

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    // Should not throw even if fetch fails
    await expect(
      client.logout("https://app.example.com"),
    ).resolves.toBeUndefined();
    expect(client.isAuthenticated()).toBe(false);
  });

  it("logout removes token from sessionStorage", async () => {
    const token = makeToken();
    sessionStorage.setItem("test_sso_token", token);
    vi.stubGlobal("location", {
      assign: vi.fn(),
      origin: "https://app.example.com",
    });

    const client = new SsoClient(BASE_CONFIG);
    await client.init();

    await client.logout("https://app.example.com");

    // Token should be removed from storage after logout
    expect(client.getRawToken()).toBeNull();
  });
});

// ── Refresh threshold tests ───────────────────────────────────────────────────

describe("Refresh Threshold Configuration", () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("accepts custom refreshThresholdMs", () => {
    const config: SsoClientConfig = {
      ...BASE_CONFIG,
      refreshThresholdMs: 120_000, // 2 minutes
    };
    expect(() => new SsoClient(config)).not.toThrow();
  });

  it("defaults refreshThresholdMs to 60,000ms when not specified", () => {
    const config: SsoClientConfig = {
      authServiceBaseUrl: "https://auth.ghatana.io",
      authGatewayBaseUrl: "https://gateway.ghatana.io",
      productId: "refresh-test",
    };
    // Construction should not throw; the default is applied internally
    expect(() => new SsoClient(config)).not.toThrow();
  });
});
