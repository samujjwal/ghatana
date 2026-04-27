import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createServer } from "../setup.js";
import type { FastifyInstance } from "fastify";

describe("Auth token required integration tests", () => {
  let app: FastifyInstance;
  const originalTrustedHeaders = process.env.TRUST_PROXY_AUTH_HEADERS;
  const originalTrustedSecret = process.env.TRUST_PROXY_AUTH_SHARED_SECRET;
  let savedStripeKey: string | undefined;

  beforeEach(async () => {
    // Ensure trusted proxy auth is NOT configured
    delete process.env.TRUST_PROXY_AUTH_HEADERS;
    delete process.env.TRUST_PROXY_AUTH_SHARED_SECRET;

    savedStripeKey = process.env.STRIPE_SECRET_KEY;
    process.env.STRIPE_SECRET_KEY = "stripe_test_placeholder_secret";

    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
    });
    await app.ready();
  });

  afterEach(async () => {
    await app?.close();
    process.env.TRUST_PROXY_AUTH_HEADERS = originalTrustedHeaders;
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = originalTrustedSecret;
    if (savedStripeKey === undefined) {
      delete process.env.STRIPE_SECRET_KEY;
    } else {
      process.env.STRIPE_SECRET_KEY = savedStripeKey;
    }
  });

  it("should require JWT token for guarded API v1 routes", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
    });

    expect(response.statusCode).toBe(401);
    // Auth module returns its own 401 body; global guard emits a slightly different one.
    // Either way the status code proves the route is gated.
    expect(response.json()).toMatchObject({
      error: expect.any(String),
    });
  });

  it("should require JWT token for content-studio routes", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/content-studio/v1/modules",
    });

    expect(response.statusCode).toBe(401);
    expect(response.json()).toMatchObject({
      error: "Unauthorized",
      message: "A valid Bearer token is required.",
    });
  });

  it("should allow public auth SSO routes without token", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/sso/providers",
    });

    // Should not be 401 - might be 404 or other response depending on implementation
    expect(response.statusCode).not.toBe(401);
  });

  it("should allow auth health endpoint without token", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/health",
    });

    expect(response.statusCode).not.toBe(401);
  });

  it("should allow auth refresh endpoint without token", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/refresh",
    });

    expect(response.statusCode).not.toBe(401);
  });

  it("should allow public LTI routes without token", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/integration/lti/jwks",
    });

    expect(response.statusCode).not.toBe(401);
  });

  it("should allow Stripe webhook endpoint without token", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/api/v1/integration/billing/webhook",
    });

    expect(response.statusCode).not.toBe(401);
  });

  it("should allow content-studio health endpoint without token", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/content-studio/health",
    });

    expect(response.statusCode).not.toBe(401);
  });

  it("should reject trusted proxy headers without configuration", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
    });

    // Should still require JWT since trusted proxy is not configured
    expect(response.statusCode).toBe(401);
  });

  it("should reject trusted proxy headers with wrong secret", async () => {
    process.env.TRUST_PROXY_AUTH_HEADERS = "true";
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = "correct-secret";

    // Recreate app with new env vars
    await app.close();
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
    });
    await app.ready();

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
        "x-trusted-proxy-secret": "wrong-secret",
      },
    });

    expect(response.statusCode).toBe(401);
  });

  it("should accept valid JWT token", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "admin",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    // Should not be 401 - might be 404 or other response
    expect(response.statusCode).not.toBe(401);
  });

  it("should accept trusted proxy headers with correct configuration from private IP", async () => {
    process.env.TRUST_PROXY_AUTH_HEADERS = "true";
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = "test-secret";

    // Recreate app with new env vars
    await app.close();
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
    });
    await app.ready();

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/auth/me",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
        "x-trusted-proxy-secret": "test-secret",
      },
    });

    // Should succeed with trusted proxy auth from private IP (default in test)
    expect(response.statusCode).not.toBe(401);
  });
});
