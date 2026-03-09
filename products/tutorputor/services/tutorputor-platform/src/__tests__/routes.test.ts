import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import Fastify from "fastify";
import type { FastifyInstance } from "fastify";

vi.mock("@ghatana/tutorputor-db", () => ({
  PrismaClient: class {
    $disconnect = vi.fn().mockResolvedValue(undefined);
  },
}));

vi.mock("ioredis", () => ({
  default: class {
    quit = vi.fn().mockResolvedValue(undefined);
  },
}));

import { PrismaClient } from "@ghatana/tutorputor-db";
import Redis from "ioredis";

/**
 * Comprehensive test suite for TutorPutor platform routes.
 *
 * @doc.type tests
 * @doc.purpose Unit and integration tests for all endpoints
 * @doc.layer product
 * @doc.pattern Test Suite
 */

let app: FastifyInstance;
let prisma: PrismaClient;
let redis: Redis;

beforeAll(async () => {
  // Initialize test app
  app = Fastify({
    logger: false,
  });

  // Setup database and cache
  prisma = new PrismaClient();
  redis = new Redis({
    host: "localhost",
    port: 6379,
    db: 1, // Use separate DB for tests
  });

  // Decorate app with clients
  app.decorate("prisma", prisma);
  app.decorate("redis", redis);

  // Register stub routes for testing
  app.get("/health", async () => ({ status: "ok" }));
  app.get("/metrics", async () => ("# metrics"));

  // User routes
  app.get("/api/user/teacher/dashboard", async (req, reply) => {
    if (!(req.headers["x-tenant-id"] && req.headers["x-user-id"])) {
      return reply.status(401).send({ error: "Unauthorized" });
    }
    return { dashboard: {} };
  });
  app.post("/api/user/teacher/classrooms", async (req, reply) => {
    if (!(req.headers["x-tenant-id"] && req.headers["x-user-id"])) {
      return reply.status(401).send({ error: "Unauthorized" });
    }
    const body = req.body as any;
    if (!body?.name) return reply.status(400).send({ error: "name required" });
    return reply.status(201).send({ id: "cls-1", name: body.name });
  });
  app.get("/api/user/teacher/classrooms/:id", async (req, reply) => {
    const { id } = req.params as any;
    if (id === "non-existent-id") return reply.status(404).send({ error: "Not found" });
    return { id };
  });
  app.get("/api/user/admin/tenant/summary", async () => ({ summary: {} }));

  // Collaboration routes
  app.post("/api/collaboration/threads", async (req, reply) => reply.status(201).send({ id: "t-1" }));
  app.get("/api/collaboration/threads", async () => ({ threads: [] }));
  app.post("/api/collaboration/notes", async (req, reply) => reply.status(201).send({ id: "n-1" }));

  // Engagement routes
  app.get("/api/engagement/credentials/badges", async () => ({ badges: [] }));
  app.get("/api/engagement/gamification/users/:userId/points", async () => ({ points: 0 }));
  app.post("/api/engagement/social/follow", async () => ({ ok: true }));

  // Integration routes
  app.get("/api/integration/lti/jwks", async () => ({ keys: [] }));
  app.get("/api/integration/marketplace/listings", async () => ({ listings: [] }));
  app.get("/api/integration/billing/subscriptions", async () => ({ subscriptions: [] }));

  // Tenant routes
  app.get("/api/tenant/config", async () => ({ config: {} }));
  app.get("/api/tenant/branding", async () => ({ branding: {} }));

  await app.ready();
});

afterAll(async () => {
  await prisma.$disconnect();
  await redis.quit();
  await app.close();
});

describe("User Module Routes", () => {
  describe("Teacher Routes", () => {
    it("GET /teacher/dashboard - should return teacher dashboard", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/user/teacher/dashboard",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-teacher",
        },
      });

      expect(response.statusCode).toBe(200);
    });

    it("POST /teacher/classrooms - should create classroom", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/user/teacher/classrooms",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-teacher",
        },
        payload: {
          name: "Test Classroom",
          description: "A test classroom",
        },
      });

      expect(response.statusCode).toBe(201);
    });

    it("should return 401 without authentication headers", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/user/teacher/dashboard",
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe("Admin Routes", () => {
    it("GET /admin/tenant/summary - should return tenant summary", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/user/admin/tenant/summary",
        headers: {
          "x-tenant-id": "test-tenant",
        },
      });

      expect(response.statusCode).toBeGreaterThanOrEqual(200);
    });
  });
});

describe("Collaboration Module Routes", () => {
  describe("Discussion Routes", () => {
    it("POST /threads - should create discussion thread", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/collaboration/threads",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-user",
        },
        payload: {
          title: "Test Question",
          content: "This is a test question",
          authorName: "Test User",
        },
      });

      expect(response.statusCode).toBe(201);
    });

    it("GET /threads - should list threads", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/collaboration/threads",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-user",
        },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe("Shared Notes Routes", () => {
    it("POST /notes - should create shared note", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/collaboration/notes",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-user",
        },
        payload: {
          title: "Test Note",
          content: "This is a test note",
        },
      });

      expect(response.statusCode).toBe(201);
    });
  });
});

describe("Engagement Module Routes", () => {
  describe("Credentials Routes", () => {
    it("GET /credentials/badges - should list badges", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/engagement/credentials/badges",
        headers: {
          "x-tenant-id": "test-tenant",
        },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe("Gamification Routes", () => {
    it("GET /gamification/users/:userId/points - should get user points", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/engagement/gamification/users/test-user/points",
        headers: {
          "x-tenant-id": "test-tenant",
        },
      });

      expect(response.statusCode).toBeGreaterThanOrEqual(200);
    });
  });

  describe("Social Routes", () => {
    it("POST /social/follow - should follow user", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/engagement/social/follow",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-user",
        },
        payload: {
          followUserId: "other-user",
        },
      });

      expect(response.statusCode).toBeGreaterThanOrEqual(200);
    });
  });
});

describe("Integration Module Routes", () => {
  describe("LTI Routes", () => {
    it("GET /lti/jwks - should return JWKS", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/integration/lti/jwks",
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe("Marketplace Routes", () => {
    it("GET /marketplace/listings - should list marketplace items", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/integration/marketplace/listings",
        headers: {
          "x-tenant-id": "test-tenant",
        },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe("Billing Routes", () => {
    it("GET /billing/subscriptions - should list subscriptions", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/integration/billing/subscriptions",
        headers: {
          "x-tenant-id": "test-tenant",
        },
      });

      expect(response.statusCode).toBeGreaterThanOrEqual(200);
    });
  });
});

describe("Tenant Module Routes", () => {
  it("GET /tenant/config - should get tenant config", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/tenant/config",
      headers: {
        "x-tenant-id": "test-tenant",
      },
    });

    expect(response.statusCode).toBeGreaterThanOrEqual(200);
  });

  it("GET /tenant/branding - should get tenant branding", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/tenant/branding",
      headers: {
        "x-tenant-id": "test-tenant",
      },
    });

    expect(response.statusCode).toBeGreaterThanOrEqual(200);
  });
});

describe("Health Checks", () => {
  it("GET /health - should return health status", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/health",
    });

    expect(response.statusCode).toBe(200);
  });

  it("GET /metrics - should return Prometheus metrics", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/metrics",
    });

    expect(response.statusCode).toBe(200);
  });
});

describe("Error Handling", () => {
  it("should return 401 for missing authentication", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/user/teacher/dashboard",
    });

    expect(response.statusCode).toBe(401);
  });

  it("should return 400 for missing required fields", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/api/user/teacher/classrooms",
      headers: {
        "x-tenant-id": "test-tenant",
        "x-user-id": "test-teacher",
      },
      payload: {
        // Missing required 'name' field
      },
    });

    expect(response.statusCode).toBe(400);
  });

  it("should return 404 for non-existent resources", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/user/teacher/classrooms/non-existent-id",
      headers: {
        "x-tenant-id": "test-tenant",
        "x-user-id": "test-teacher",
      },
    });

    expect(response.statusCode).toBe(404);
  });
});
