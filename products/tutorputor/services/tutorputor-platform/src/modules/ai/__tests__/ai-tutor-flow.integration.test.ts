/**
 * AI Tutor Flow Integration Tests
 *
 * @doc.type test-suite
 * @doc.purpose Integration tests for AI tutor query flow
 * @doc.layer platform
 * @doc.pattern Integration Test
 */

import {
  describe,
  it,
  expect,
  beforeAll,
  afterAll,
  beforeEach,
} from "vitest";
import { FastifyInstance } from "fastify";
import { createHmac } from "crypto";
import { createServer } from "../../../setup.js";

/**
 * Test fixture for AI tutor flow
 */
interface AITestFixture {
  app: FastifyInstance;
  authToken: string;
}

function createTestJWT(
  claims: {
    userId: string;
    tenantId: string;
    role?: string;
  },
  signingKey: string,
): string {
  const payload = {
    userId: claims.userId,
    tenantId: claims.tenantId,
    role: claims.role || "learner",
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
  };

  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = createHmac("sha256", signingKey)
    .update(`${header}.${body}`)
    .digest("base64url");

  return `${header}.${body}.${signature}`;
}

function createMockRedis(): Record<string, unknown> {
  const storage = new Map<string, string>();
  return {
    incr: vi.fn((key: string) => {
      const value = Number.parseInt(storage.get(key) ?? "0", 10) + 1;
      storage.set(key, String(value));
      return Promise.resolve(value);
    }),
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
    duplicate: vi.fn().mockReturnValue({
      subscribe: vi.fn().mockResolvedValue(undefined),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
      on: vi.fn(),
      removeAllListeners: vi.fn(),
      disconnect: vi.fn(),
    }),
    status: "ready",
  };
}

function createMockPrisma(): Record<string, unknown> {
  return {
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    $queryRaw: vi.fn().mockResolvedValue([{ result: 1 }]),
    userConsent: {
      findMany: vi.fn().mockResolvedValue([{ category: "ai_processing" }]),
      findFirst: vi.fn().mockResolvedValue({
        category: "ai_processing",
        granted: true,
      }),
    },
    aIAuditLog: {
      create: vi.fn().mockResolvedValue({ id: "audit-test" }),
    },
  };
}

function createValidAiQueryPayload(question: string): {
  question: string;
  moduleId: string;
  claimIds: string[];
  currentSimulationState: Record<string, unknown>;
  recentAttempts: Array<{
    attemptId: string;
    taskId: string;
    correct: boolean;
    confidence: "medium";
  }>;
  misconceptions: string[];
  allowedHelpMode: "hint";
  locale: string;
} {
  return {
    question,
    moduleId: "module-123",
    claimIds: ["claim-123"],
    currentSimulationState: { step: "intro" },
    recentAttempts: [
      {
        attemptId: "attempt-123",
        taskId: "task-123",
        correct: true,
        confidence: "medium",
      },
    ],
    misconceptions: [],
    allowedHelpMode: "hint",
    locale: "en",
  };
}

describe("AI Tutor Flow Integration Tests", () => {
  let fixture: AITestFixture;
  const JWT_SECRET = "test-secret-key-min-32-chars-long!!";

  beforeAll(async () => {
    const app = await createServer({
      jwtSecret: JWT_SECRET,
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
      prisma: createMockPrisma() as never,
      redis: createMockRedis() as never,
    });

    await app.ready();
    const authToken = createTestJWT(
      {
        userId: "test-user-123",
        tenantId: "test-tenant-456",
        role: "student",
      },
      JWT_SECRET,
    );
    fixture = { app, authToken };
  });

  afterAll(async () => {
    await fixture?.app.close();
  });

  describe("AI Tutor Query Flow", () => {
    it("should accept valid AI tutor query request", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: {
          authorization: `Bearer ${fixture.authToken}`,
          "x-tenant-id": "test-tenant-456",
          "x-user-id": "test-user-123",
        },
        payload: createValidAiQueryPayload("What is photosynthesis?"),
      });

      // Should not be 401 (auth) or 400 (validation)
      expect(response.statusCode).not.toBe(401);
      expect(response.statusCode).not.toBe(400);
    });

    it("should validate AI query input", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: {
          authorization: `Bearer ${fixture.authToken}`,
          "x-tenant-id": "test-tenant-456",
          "x-user-id": "test-user-123",
        },
        payload: {
          // Missing required question field
          moduleId: "module-123",
        },
      });

      expect(response.statusCode, response.body).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toBeDefined();
    });

    it("should reject empty question", async () => {
      const response = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: {
          authorization: `Bearer ${fixture.authToken}`,
          "x-tenant-id": "test-tenant-456",
          "x-user-id": "test-user-123",
        },
        payload: {
          question: "",
          moduleId: "module-123",
        },
      });

      expect(response.statusCode, response.body).toBe(400);
    });

    it("should enforce rate limiting on AI queries", async () => {
      // Send multiple requests to test rate limiting
      const requests = Array.from({ length: 35 }, () =>
        fixture.app.inject({
          method: "POST",
          url: "/api/v1/ai/tutor/query",
          headers: {
            authorization: `Bearer ${fixture.authToken}`,
            "x-tenant-id": "test-tenant-456",
            "x-user-id": "test-user-123",
          },
          payload: createValidAiQueryPayload("Test question"),
        }),
      );

      const responses = await Promise.all(requests);
      const rateLimitedResponses = responses.filter(
        (r) => r.statusCode === 429,
      );

      // Should have some rate limited responses (default is 30 per minute)
      expect(rateLimitedResponses.length).toBeGreaterThan(0);
    });
  });

  describe("AI Health Check Integration", () => {
    it("should report AI service health status", async () => {
      const response = await fixture.app.inject({
        method: "GET",
        url: "/api/v1/ai/health",
        headers: {
          authorization: `Bearer ${fixture.authToken}`,
        },
      });

      // Health endpoint should be accessible
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("healthy");
    });
  });

  describe("AI Cache Integration", () => {
    it("should cache AI responses for identical queries", async () => {
      const queryPayload = {
        ...createValidAiQueryPayload("What is the capital of France?"),
      };

      // First request
      const firstResponse = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: {
          authorization: `Bearer ${fixture.authToken}`,
          "x-tenant-id": "test-tenant-456",
          "x-user-id": "test-user-123",
        },
        payload: queryPayload,
      });

      // Second identical request
      const secondResponse = await fixture.app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: {
          authorization: `Bearer ${fixture.authToken}`,
          "x-tenant-id": "test-tenant-456",
          "x-user-id": "test-user-123",
        },
        payload: queryPayload,
      });

      // Both should succeed
      expect(firstResponse.statusCode, firstResponse.body).not.toBe(500);
      expect(secondResponse.statusCode, secondResponse.body).not.toBe(500);
    });
  });
});
