/**
 * @file providerIntegration.test.ts
 * Tests for SSO provider integration — token exchange, multi-provider support,
 * and authentication against Auth0, Firebase, Okta, and custom providers.
 *
 * @doc.type module
 * @doc.purpose Tests for SSO provider integration and token exchange
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { SsoClient, decodeJwtPayload } from "../index";
import type { SsoClientConfig } from "../index";

// ── JWT helpers ───────────────────────────────────────────────────────────────

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

function makePlatformToken(overrides: Record<string, unknown> = {}): string {
  return buildJwt({
    sub: "user-provider-test",
    email: "provider@example.com",
    roles: ["ROLE_USER"],
    tenantId: "tenant-provider",
    tokenType: "PLATFORM",
    iss: "ghatana-auth-service",
    sessionId: "sess-provider-001",
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
    ...overrides,
  });
}

function makeProductToken(productId: string): string {
  return buildJwt({
    sub: "user-provider-test",
    email: "provider@example.com",
    roles: ["ROLE_USER"],
    tenantId: "tenant-provider",
    tokenType: "PRODUCT",
    productId,
    iss: "ghatana-auth-gateway",
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
  });
}

// ── Provider configs ───────────────────────────────────────────────────────────

const auth0Config: SsoClientConfig = {
  authServiceBaseUrl: "https://auth.ghatana.io",
  authGatewayBaseUrl: "https://gateway.ghatana.io",
  productId: "auth0-product",
  storageKey: "auth0_test_token",
};

const firebaseConfig: SsoClientConfig = {
  authServiceBaseUrl: "https://auth.ghatana.io",
  authGatewayBaseUrl: "https://gateway.ghatana.io",
  productId: "firebase-product",
  storageKey: "firebase_test_token",
};

const oktaConfig: SsoClientConfig = {
  authServiceBaseUrl: "https://auth.ghatana.io",
  authGatewayBaseUrl: "https://gateway.ghatana.io",
  productId: "okta-product",
  storageKey: "okta_test_token",
};

const customProviderConfig: SsoClientConfig = {
  authServiceBaseUrl: "https://custom-auth.company.io",
  authGatewayBaseUrl: "https://custom-gateway.company.io",
  productId: "custom-product",
  platformTokenParam: "auth_token",
  storageKey: "custom_test_token",
};

// ── Token exchange tests ──────────────────────────────────────────────────────

describe("Token Exchange (exchangeForProductToken)", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("calls POST /auth/exchange with the product JWT", async () => {
    const platformToken = makePlatformToken();
    const productToken = makeProductToken("tutorputor");

    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ platform_token: platformToken }),
      }),
    );

    sessionStorage.setItem("auth0_test_token", platformToken);
    const client = new SsoClient(auth0Config);
    await client.init();

    await client.exchangeForProductToken(productToken);

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      "https://gateway.ghatana.io/auth/exchange",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "Content-Type": "application/json",
        }),
        body: expect.stringContaining(productToken),
      }),
    );
  });

  it("returns the platform token from the exchange response", async () => {
    const platformToken = makePlatformToken();
    const productToken = makeProductToken("tutorputor");

    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ platform_token: platformToken }),
      }),
    );

    sessionStorage.setItem("auth0_test_token", platformToken);
    const client = new SsoClient(auth0Config);
    await client.init();

    const result = await client.exchangeForProductToken(productToken);

    expect(result).toBe(platformToken);
  });

  it("throws when exchange endpoint returns a non-ok response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ message: "Unauthorized" }),
      }),
    );

    const platformToken = makePlatformToken();
    sessionStorage.setItem("auth0_test_token", platformToken);
    const client = new SsoClient(auth0Config);
    await client.init();

    await expect(
      client.exchangeForProductToken(makeProductToken("unknown")),
    ).rejects.toThrow();
  });

  it("throws when network fails during exchange", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network error")),
    );

    const platformToken = makePlatformToken();
    sessionStorage.setItem("auth0_test_token", platformToken);
    const client = new SsoClient(auth0Config);
    await client.init();

    await expect(
      client.exchangeForProductToken(makeProductToken("tutorputor")),
    ).rejects.toThrow("Network error");
  });
});

// ── Auth0 provider integration tests ─────────────────────────────────────────

describe("Auth0 Provider Integration", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("authenticates using Auth0-issued platform token", async () => {
    const auth0Token = makePlatformToken({ iss: "ghatana-auth-service/auth0" });
    sessionStorage.setItem("auth0_test_token", auth0Token);

    const client = new SsoClient(auth0Config);
    await client.init();

    expect(client.isAuthenticated()).toBe(true);
    expect(client.getUser()?.email).toBe("provider@example.com");
  });

  it("decodes multi-role claim from Auth0 token", async () => {
    const token = makePlatformToken({ roles: ["ROLE_USER", "ROLE_ADMIN"] });
    sessionStorage.setItem("auth0_test_token", token);

    const client = new SsoClient(auth0Config);
    await client.init();

    expect(client.getClaims()?.roles).toContain("ROLE_USER");
    expect(client.getClaims()?.roles).toContain("ROLE_ADMIN");
  });

  it("productId is included in login redirect URL", () => {
    const locationSpy = { assign: vi.fn(), origin: "https://app.example.com" };
    vi.stubGlobal("location", locationSpy);

    const client = new SsoClient(auth0Config);
    client.login();

    expect(locationSpy.assign).toHaveBeenCalledWith(
      expect.stringContaining("product_id=auth0-product"),
    );
  });
});

// ── Firebase provider integration tests ────────────────────────────────────────

describe("Firebase Provider Integration", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("authenticates using Firebase-issued platform token", async () => {
    const firebaseToken = makePlatformToken({
      iss: "ghatana-auth-service/firebase",
    });
    sessionStorage.setItem("firebase_test_token", firebaseToken);

    const client = new SsoClient(firebaseConfig);
    await client.init();

    expect(client.isAuthenticated()).toBe(true);
  });

  it("validates Firebase token does not have PLATFORM tokenType mismatch", async () => {
    // A raw Firebase JWT (not a platform token) should not be considered a platform token
    const rawFirebaseToken = buildJwt({
      sub: "12345",
      email: "user@firebase.com",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
      // Note: no tokenType = 'PLATFORM'
    });

    const claims = decodeJwtPayload(rawFirebaseToken);
    expect(claims?.tokenType).toBeUndefined();
  });

  it("Firebase productId in login redirect", () => {
    const locationSpy = { assign: vi.fn(), origin: "https://app.example.com" };
    vi.stubGlobal("location", locationSpy);

    const client = new SsoClient(firebaseConfig);
    client.login();

    expect(locationSpy.assign).toHaveBeenCalledWith(
      expect.stringContaining("product_id=firebase-product"),
    );
  });
});

// ── Okta provider integration tests ────────────────────────────────────────────

describe("Okta Provider Integration", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("authenticates using Okta-issued platform token", async () => {
    const oktaToken = makePlatformToken({ iss: "ghatana-auth-service/okta" });
    sessionStorage.setItem("okta_test_token", oktaToken);

    const client = new SsoClient(oktaConfig);
    await client.init();

    expect(client.isAuthenticated()).toBe(true);
    expect(client.getClaims()?.tenantId).toBe("tenant-provider");
  });

  it("handles Okta tokens with tenantId claim", async () => {
    const token = makePlatformToken({ tenantId: "okta-tenant-xyz" });
    sessionStorage.setItem("okta_test_token", token);

    const client = new SsoClient(oktaConfig);
    await client.init();

    expect(client.getUser()?.tenantId).toBe("okta-tenant-xyz");
  });
});

// ── Custom provider integration tests ─────────────────────────────────────────

describe("Custom Provider Integration", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("reads token from custom platformTokenParam", async () => {
    const token = makePlatformToken();

    // Simulate URL with custom param
    Object.defineProperty(window, "location", {
      value: {
        href: `https://app.example.com/?auth_token=${token}`,
        search: `?auth_token=${token}`,
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

    const client = new SsoClient(customProviderConfig);
    await client.init();

    // Token should be stored in custom storage key
    expect(sessionStorage.getItem("custom_test_token")).toBe(token);
  });

  it("uses custom authServiceBaseUrl in login redirect", () => {
    const locationSpy = { assign: vi.fn(), origin: "https://app.custom.io" };
    vi.stubGlobal("location", locationSpy);

    const client = new SsoClient(customProviderConfig);
    client.login();

    expect(locationSpy.assign).toHaveBeenCalledWith(
      expect.stringContaining("custom-auth.company.io"),
    );
  });

  it("uses custom authGatewayBaseUrl in token exchange", async () => {
    const platformToken = makePlatformToken();
    const productToken = makeProductToken("custom-product");

    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ platform_token: platformToken }),
      }),
    );

    sessionStorage.setItem("custom_test_token", platformToken);
    const client = new SsoClient(customProviderConfig);
    await client.init();

    await client.exchangeForProductToken(productToken);

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      "https://custom-gateway.company.io/auth/exchange",
      expect.any(Object),
    );
  });

  it("uses custom postLoginRedirectUrl when specified", () => {
    const locationSpy = { assign: vi.fn(), origin: "https://app.example.com" };
    vi.stubGlobal("location", locationSpy);

    const configWithRedirect: SsoClientConfig = {
      ...customProviderConfig,
      postLoginRedirectUrl: "https://custom-app.company.io/callback",
    };
    const client = new SsoClient(configWithRedirect);
    client.login();

    expect(locationSpy.assign).toHaveBeenCalledWith(
      expect.stringContaining("custom-app.company.io%2Fcallback"),
    );
  });
});

// ── Provider compatibility tests ──────────────────────────────────────────────

describe("Provider Token Compatibility", () => {
  it("platform token without sessionId is still valid", async () => {
    sessionStorage.clear();
    const token = buildJwt({
      sub: "user-no-session",
      email: "nosession@example.com",
      roles: ["ROLE_USER"],
      tenantId: "tenant-a",
      tokenType: "PLATFORM",
      iss: "ghatana-auth-service",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 900,
      // No sessionId
    });

    sessionStorage.setItem("auth0_test_token", token);
    const client = new SsoClient(auth0Config);
    await client.init();

    expect(client.isAuthenticated()).toBe(true);
    expect(client.getUser()?.sessionId).toBeUndefined();
  });

  it("platform token without tenantId is still processed", async () => {
    sessionStorage.clear();
    const token = buildJwt({
      sub: "user-no-tenant",
      email: "notenant@example.com",
      roles: ["ROLE_SUPER_ADMIN"],
      tokenType: "PLATFORM",
      iss: "ghatana-auth-service",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 900,
      // No tenantId — super-admin tokens may omit it
    });

    sessionStorage.setItem("auth0_test_token", token);
    const client = new SsoClient(auth0Config);
    await client.init();

    expect(client.isAuthenticated()).toBe(true);
    expect(client.getUser()?.tenantId).toBeUndefined();
  });
});
