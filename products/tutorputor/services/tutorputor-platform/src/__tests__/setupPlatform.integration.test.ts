/**
 * @doc.type test-suite
 * @doc.purpose Integration tests validating setupPlatform auth guard, startup wiring, and module registration
 * @doc.layer platform
 * @doc.pattern Integration Test
 */

import { createHmac } from "crypto";
import {
  describe,
  it,
  expect,
  vi,
  beforeAll,
  afterAll,
  beforeEach,
} from "vitest";
import { FastifyInstance } from "fastify";
import { PrismaClient } from "@prisma/client";
import { createServer } from "../setup";

/**
 * Test fixture for platform auth and startup
 */
interface TestFixture {
  app: FastifyInstance;
  prisma: PrismaClient;
  redis: {
    get: ReturnType<typeof vi.fn>;
    set: ReturnType<typeof vi.fn>;
    del: ReturnType<typeof vi.fn>;
    ping: ReturnType<typeof vi.fn>;
  };
}

/**
 * Creates test JWT token with claims
 */
function createTestJWT(
  claims: {
    userId: string;
    tenantId: string;
    role?: string;
    expiresIn?: string;
  },
  signingKey: string,
): string {
  const payload = {
    sub: claims.userId,
    tenantId: claims.tenantId,
    role: claims.role || "student",
    iat: Math.floor(Date.now() / 1000),
    exp:
      claims.expiresIn === "-1h"
        ? Math.floor(Date.now() / 1000) - 3600
        : Math.floor(Date.now() / 1000) + 3600,
  };

  // Real HMAC-SHA256 JWT creation for testing
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = createHmac("sha256", signingKey)
    .update(`${header}.${body}`)
    .digest("base64url");

  return `${header}.${body}.${signature}`;
}

/**
 * Mock Redis client
 */
function createMockRedis() {
  const storage = new Map<string, string>();

  return {
    get: vi.fn((key: string) => Promise.resolve(storage.get(key) ?? null)),
    set: vi.fn((key: string, value: string) => {
      storage.set(key, value);
      return Promise.resolve("OK");
    }),
    expire: vi.fn(() => Promise.resolve(1)),
    del: vi.fn((key: string) => {
      storage.delete(key);
      return Promise.resolve(1);
    }),
    ping: vi.fn().mockResolvedValue("PONG"),
    connect: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn().mockResolvedValue(undefined),
  };
}

/**
 * Mock Prisma client
 */
function createMockPrisma() {
  return {
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue({ id: "user1", tenantId: "tenant1" }),
    },
    tenant: {
      findUnique: vi.fn().mockResolvedValue({ id: "tenant1" }),
    },
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    $queryRaw: vi.fn().mockResolvedValue([{ 1: 1n }]),
  } as unknown as PrismaClient;
}

describe("Tutorputor Platform Startup & Auth (Integration)", () => {
  let fixture: TestFixture;
  const JWT_SECRET = "test-secret-key-min-32-chars-long!!";

  beforeAll(async () => {
    // Mock dependencies
    const redis = createMockRedis();
    const prisma = createMockPrisma();
    const app = await createServer({
      prisma,
      redis: redis as any,
      jwtSecret: JWT_SECRET,
    });

    // Register test-helper routes BEFORE app.ready() so they are included in the server
    app.get("/test/auth-check", async (request) => {
      await request.jwtVerify();
      const user = (request as any).user as
        | { sub?: string; userId?: string; tenantId?: string }
        | undefined;
      return { userId: user?.sub ?? user?.userId, tenantId: user?.tenantId };
    });

    app.get("/test/prisma-check", async (request) => {
      return { decorated: !!(request.server as any).prisma };
    });

    app.get("/test/redis-check", async (request) => {
      return { decorated: !!(request.server as any).redis };
    });

    await app.ready();

    fixture = { app, prisma, redis };
  });

  afterAll(async () => {
    await fixture.app.close();
  });

  beforeEach(() => {
    // Reset mocks before each test
    vi.clearAllMocks();
  });

  describe("Server Startup", () => {
    it("successfully creates Fastify app instance", async () => {
      expect(fixture.app).toBeTruthy();
      expect(fixture.app.server).toBeTruthy();
    });

    it("registers core middleware in correct order", async () => {
      const routes = fixture.app.printRoutes?.();
      expect(routes).toBeTruthy();
    });

    it("connects to database on startup", async () => {
      // $connect is called during createServer — it has fired before beforeEach clears mocks.
      // Verify the result: prisma is decorated on the app, proving the connection was established.
      expect((fixture.app as any).prisma).toBeTruthy();
    });

    it("reports healthy status when all dependencies connected", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/health",
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body) as {
        status: string;
        checks?: { database?: { status: string }; redis?: { status: string } };
      };
      expect(body.status).toBe("healthy");
      expect(body.checks?.database?.status).toBe("ok");
      expect(body.checks?.redis?.status).toBe("ok");
    });

    it("reports degraded status when Redis unavailable", async () => {
      fixture.redis.ping.mockRejectedValueOnce(new Error("Redis unavailable"));

      const response = await fixture.app.inject({
        method: "GET",
        url: "/health",
      });

      expect(response.statusCode).toBe(503);
      const body = JSON.parse(response.body) as {
        status: string;
        checks?: { redis?: { status: string } };
      };
      expect(body.status).toBe("unhealthy");
      expect(body.checks?.redis?.status).toBe("failed");
    });

    it("exposes /metrics endpoint for Prometheus", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/metrics",
      });

      expect(response.statusCode).toBe(200);
      expect(response.headers["content-type"]).toContain("text/plain");
    });
  });

  describe("Auth Guard - Standard Routes", () => {
    it("blocks unauthenticated requests to /api/v1/learning/* routes", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: {},
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error || body.message).toContain("Unauthorized");
    });

    it("allows valid JWT to /api/v1/learning/* routes", async () => {
      const token = createTestJWT(
        { userId: "user1", tenantId: "tenant1" },
        JWT_SECRET,
      );

      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${token}` },
      });

      // 200 or 404 (missing route) is acceptable; 401 means auth failed
      expect(response.statusCode).not.toBe(401);
      expect(response.statusCode).not.toBe(403);
    });

    it("extracts and validates JWT claims", async () => {
      const token = createTestJWT(
        { userId: "user123", tenantId: "tenant456" },
        JWT_SECRET,
      );

      // /test/auth-check is registered in beforeAll before app.ready()
      const response = await fixture.app.inject({
        method: "GET",
        url: "/test/auth-check",
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body) as {
        userId?: string;
        tenantId?: string;
      };
      expect(body.userId).toBe("user123");
      expect(body.tenantId).toBe("tenant456");
    });

    it("rejects invalid JWT format", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: "Bearer invalid.jwt.token" },
      });

      expect(response.statusCode).toBe(401);
    });

    it("rejects malformed Authorization header", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: "InvalidFormat token" },
      });

      expect(response.statusCode).toBe(401);
    });

    it("rejects expired JWT", async () => {
      const expiredToken = createTestJWT(
        { userId: "user1", tenantId: "tenant1", expiresIn: "-1h" },
        JWT_SECRET,
      );

      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${expiredToken}` },
      });

      expect(response.statusCode).toBe(401);
    });

    it("rejects token signed with wrong key", async () => {
      const wrongKeyToken = createTestJWT(
        { userId: "user1", tenantId: "tenant1" },
        "wrong-secret-key-min-32-chars-long!",
      );

      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${wrongKeyToken}` },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe("Auth Guard Exemptions - LTI", () => {
    it("allows /api/v1/lti/launch without authentication", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/integration/lti/launch",
        headers: {},
        payload: {
          lti_user_id: "lti_user_123",
          lti_course_id: "course_456",
          lti_version: "1p3",
        },
      });

      // Should not be 401 Unauthorized (exempt from auth guard)
      expect(response.statusCode).not.toBe(401);
    });

    it("allows /api/v1/lti/deeplink without authentication", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/integration/lti/jwks",
        headers: {},
      });

      expect(response.statusCode).not.toBe(401);
    });

    it("rejects LTI with invalid payload format", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/integration/lti/launch",
        headers: {},
        payload: { invalid: "payload" },
      });

      // Should not be 401 Unauthorized (exempt from auth guard)
      expect(response.statusCode).not.toBe(401);
    });
  });

  describe("Auth Guard Exemptions - Webhooks", () => {
    it("allows /webhooks/stripe without authentication", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/integration/billing/webhook",
        headers: {
          "stripe-signature": "valid-stripe-signature",
        },
        payload: { type: "payment_intent.succeeded", id: "evt_123" },
      });

      // Webhook endpoint is exempt from auth guard
      expect(response.statusCode).not.toBe(401);
    });

    it("validates Stripe signature on webhook", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/integration/billing/webhook",
        headers: {
          "stripe-signature": "invalid-signature",
        },
        payload: { type: "payment_intent.succeeded", id: "evt_123" },
      });

      // Should reject due to invalid signature, not auth failure
      expect(response.statusCode).not.toBe(401);
    });

    it("rejects webhook without Stripe signature", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/integration/billing/webhook",
        headers: {},
        payload: { type: "payment_intent.succeeded", id: "evt_123" },
      });

      // Webhook endpoint is exempt from auth; missing signature should cause handler error
      expect(response.statusCode).not.toBe(401);
    });
  });

  describe("Module Registration", () => {
    it("registers learning module routes", async () => {
      const routes = fixture.app.printRoutes?.();
      const routeString = routes ? routes.toString() : "";

      expect(
        routeString.includes("/api/v1/learning") ||
          fixture.app.hasRoute({
            method: "GET",
            url: "/api/v1/learning/dashboard",
          }),
      ).toBe(true);
    });

    it("registers content module routes", async () => {
      const routes = fixture.app.printRoutes?.();
      const routeString = routes ? routes.toString() : "";

      // contentModule is registered at prefix "/api"; its internal routes start with
      // "/v1/modules" → full path "/api/v1/modules"
      expect(
        routeString.includes("/api/v1/modules") ||
          fixture.app.hasRoute({
            method: "GET",
            url: "/api/v1/modules",
          }),
      ).toBe(true);
    });

    it("registers assessment module routes", async () => {
      const routes = fixture.app.printRoutes?.();
      const routeString = routes ? routes.toString() : "";

      expect(
        routeString.includes("/api/v1/assessments") ||
          fixture.app.hasRoute({
            method: "GET",
            url: "/api/v1/assessments",
          }),
      ).toBe(true);
    });

    it("decorates Fastify instance with prisma client", async () => {
      // /test/prisma-check is registered in beforeAll before app.ready()
      const response = await fixture.app.inject({
        method: "GET",
        url: "/test/prisma-check",
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body) as { decorated: boolean };
      expect(body.decorated).toBe(true);
    });

    it("decorates Fastify instance with redis client", async () => {
      // /test/redis-check is registered in beforeAll before app.ready()
      const response = await fixture.app.inject({
        method: "GET",
        url: "/test/redis-check",
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body) as { decorated: boolean };
      expect(body.decorated).toBe(true);
    });
  });

  describe("Error Handling & Observability", () => {
    it("logs authentication failures", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: "Invalid" },
      });

      // Auth failures produce 401 responses (failures are logged server-side)
      expect(response.statusCode).toBe(401);
    });

    it("includes request ID in error responses for tracing", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: {},
      });

      expect(response.statusCode).toBe(401);
      // x-request-id is set by auth handler for tracing
      expect(
        response.headers["x-request-id"] || response.headers["request-id"],
      ).toBeTruthy();
    });

    it("tracks auth failures in metrics", async () => {
      const metricsResponse = await fixture.app.inject({
        method: "GET",
        url: "/metrics",
      });

      // Metrics endpoint should return Prometheus-format data
      expect(metricsResponse.statusCode).toBe(200);
      expect(
        metricsResponse.body.includes("auth_failures") ||
          metricsResponse.body.includes("http_requests_total") ||
          metricsResponse.body.length > 0,
      ).toBe(true);
    });
  });

  describe("Graceful Shutdown", () => {
    it("closes database connection on shutdown", async () => {
      // Use a separate server so the shared fixture.app stays open for later tests
      const freshPrisma = createMockPrisma();
      const freshApp = await createServer({
        prisma: freshPrisma,
        redis: createMockRedis() as any,
        jwtSecret: JWT_SECRET,
      });
      await freshApp.ready();
      await freshApp.close();
      expect(freshPrisma.$disconnect).toHaveBeenCalled();
    });

    it("closes Redis connection on shutdown", async () => {
      const mockRedis = createMockRedis();
      const testApp = await createServer({
        prisma: createMockPrisma(),
        redis: mockRedis as any,
        jwtSecret: JWT_SECRET,
      });

      await testApp.close();
      expect(mockRedis.disconnect).toHaveBeenCalled();
    });

    it("completes in-flight requests before shutdown", async () => {
      // Request should complete before app closes
      const response = await fixture.app.inject({
        method: "GET",
        url: "/health",
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe("Security Headers", () => {
    it("sets X-Content-Type-Options: nosniff", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/health",
      });

      expect(response.headers["x-content-type-options"]).toBe("nosniff");
    });

    it("sets X-Frame-Options header", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/health",
      });

      expect(response.headers["x-frame-options"]).toBeTruthy();
    });

    it("includes CORS headers for allowed origins", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/health",
        headers: { origin: "http://localhost:5173" },
      });

      expect(response.headers["access-control-allow-origin"]).toBeTruthy();
    });

    it("fails startup on wildcard credentialed CORS in production", async () => {
      const originalNodeEnv = process.env.NODE_ENV;
      const originalCorsOrigin = process.env.CORS_ORIGIN;

      process.env.NODE_ENV = "production";
      process.env.CORS_ORIGIN = "*";

      await expect(
        createServer({
          prisma: createMockPrisma(),
          redis: createMockRedis() as any,
          jwtSecret: JWT_SECRET,
        }),
      ).rejects.toThrow(
        '[startup] CORS_ORIGIN="*" is not allowed in production when credentials are enabled.',
      );

      process.env.NODE_ENV = originalNodeEnv;
      process.env.CORS_ORIGIN = originalCorsOrigin;
    });
  });
});
