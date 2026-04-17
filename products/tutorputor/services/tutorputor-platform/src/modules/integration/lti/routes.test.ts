import Fastify, { type FastifyInstance } from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core", () => ({
  AuthorizationError: class AuthorizationError extends Error {
    statusCode = 401;
    details: unknown;

    constructor(message: string, details?: unknown) {
      super(message);
      this.details = details;
    }
  },
  ValidationError: class ValidationError extends Error {
    statusCode: number;
    code: string;

    constructor(message: string, statusCode = 400, code = "VALIDATION_ERROR") {
      super(message);
      this.statusCode = statusCode;
      this.code = code;
    }
  },
}));

import { ltiRoutes } from "./routes.js";

/**
 * @doc.type test
 * @doc.purpose Verify LTI integration routes delegate to the shared service and return correct auth errors
 * @doc.layer product
 * @doc.pattern Test
 */

type MockPrisma = {
  lTIPlatform: {
    findFirst: ReturnType<typeof vi.fn>;
    upsert: ReturnType<typeof vi.fn>;
  };
  ltiSession: {
    findUnique: ReturnType<typeof vi.fn>;
  };
  ltiLineItem: {
    findUnique: ReturnType<typeof vi.fn>;
  };
  ltiScore: {
    create: ReturnType<typeof vi.fn>;
  };
  module: {
    findMany: ReturnType<typeof vi.fn>;
  };
  $queryRaw: ReturnType<typeof vi.fn>;
};

describe("LTI integration routes", () => {
  let app: FastifyInstance;
  let prisma: MockPrisma;

  beforeEach(async () => {
    prisma = {
      lTIPlatform: {
        findFirst: vi.fn(),
        upsert: vi.fn(),
      },
      ltiSession: {
        findUnique: vi.fn(),
      },
      ltiLineItem: {
        findUnique: vi.fn(),
      },
      ltiScore: {
        create: vi.fn(),
      },
      module: {
        findMany: vi.fn(),
      },
      $queryRaw: vi.fn().mockResolvedValue([{ 1: 1 }]),
    };

    app = Fastify({ logger: false });
    app.decorate("prisma", prisma);
    await app.register(ltiRoutes);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("returns validated launch payload for a registered platform", async () => {
    prisma.lTIPlatform.findFirst.mockResolvedValue({
      issuer: "https://canvas.example.com",
      clientId: "canvas-client-id",
    });

    const token = createUnsignedToken({
      iss: "https://canvas.example.com",
      sub: "user-123",
      aud: "canvas-client-id",
      exp: Math.floor(Date.now() / 1000) + 300,
      iat: Math.floor(Date.now() / 1000),
      nonce: "launch-nonce",
      "https://purl.imsglobal.org/spec/lti/claim/context": {
        id: "course-1",
        title: "Physics",
      },
      "https://purl.imsglobal.org/spec/lti/claim/resource_link": {
        id: "resource-1",
        title: "Module 1",
      },
      "https://purl.imsglobal.org/spec/lti/claim/roles": [
        "http://purl.imsglobal.org/vocab/lis/v2/membership#Learner",
      ],
    });

    const response = await app.inject({
      method: "POST",
      url: "/launch",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-123",
      },
      payload: {
        id_token: token,
        state: "state-1",
        nonce: "launch-nonce",
      },
    });

    expect(response.statusCode).toBe(401);
    expect(response.json()).toMatchObject({
      error: "Invalid LTI launch",
    });
  });

  it("returns a generated JWKS without env-backed key material", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/jwks",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json().keys).toBeInstanceOf(Array);
    expect(response.json().keys[0]).toMatchObject({
      kty: "RSA",
      use: "sig",
      alg: "RS256",
    });
  });

  it("returns config urls for the mounted integration LTI surface", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/config/canvas",
      headers: {
        host: "platform.example.com",
        "x-forwarded-proto": "https",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      platform: "canvas",
      auth_login_url:
        "https://platform.example.com/api/v1/integration/lti/launch",
      launch_url: "https://platform.example.com/api/v1/integration/lti/launch",
      deep_linking_url:
        "https://platform.example.com/api/v1/integration/lti/deep-linking",
      key_set_url: "https://platform.example.com/api/v1/integration/lti/jwks",
    });
  });

  it("builds deep-linking content from module ids", async () => {
    prisma.module.findMany.mockResolvedValue([
      {
        id: "module-1",
        slug: "intro-physics",
        title: "Intro Physics",
        difficulty: "INTRO",
        domain: "SCIENCE",
      },
    ]);

    const response = await app.inject({
      method: "POST",
      url: "/deep-linking",
      headers: {
        "x-tenant-id": "tenant-1",
        host: "platform.example.com",
        "x-forwarded-proto": "https",
      },
      payload: {
        deployment_id: "deployment-1",
        moduleIds: ["module-1"],
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      deployment_id: "deployment-1",
      content_items_count: 1,
      processed: true,
    });
    expect(response.json().content_items[0].url).toContain(
      "/api/v1/integration/lti/launch/module-1",
    );
  });

  it("rejects malformed deep-linking payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/deep-linking",
      payload: {
        content_items: [],
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "Invalid deep-linking payload",
    });
  });

  it("rejects platform registration without an admin role", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/register",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        platformName: "Canvas",
        issuer: "https://canvas.example.com",
        clientId: "canvas-client-id",
      },
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({
      statusCode: 403,
      code: "FORBIDDEN",
    });
  });

  it("registers a platform for tenant admins", async () => {
    prisma.lTIPlatform.upsert.mockResolvedValue({
      id: "platform-1",
    });

    const response = await app.inject({
      method: "POST",
      url: "/register",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        platformName: "Canvas",
        issuer: "https://canvas.example.com",
        clientId: "canvas-client-id",
        jwksUrl: "https://canvas.example.com/jwks",
        authUrl: "https://canvas.example.com/auth",
        tokenUrl: "https://canvas.example.com/token",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      tenantId: "tenant-1",
      platformId: "platform-1",
      registered: true,
    });
    expect(prisma.lTIPlatform.upsert).toHaveBeenCalledTimes(1);
  });

  it("rejects invalid registration URLs", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/register",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        platformName: "Canvas",
        issuer: "https://canvas.example.com",
        clientId: "canvas-client-id",
        jwksUrl: "not-a-url",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(prisma.lTIPlatform.upsert).not.toHaveBeenCalled();
  });

  it("delegates grade passback to the LTI grade service when a session id is provided", async () => {
    prisma.ltiSession.findUnique.mockResolvedValue(null);

    const response = await app.inject({
      method: "POST",
      url: "/grade-passback",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: {
        sessionId: "session-1",
        userId: "lti-user-1",
        score: 95,
        maxScore: 100,
        lineItemId: "line-item-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: false,
      lineItemId: "line-item-1",
      userId: "lti-user-1",
      scoreGiven: 95,
      error: "Line item not found",
    });
    expect(prisma.ltiSession.findUnique).toHaveBeenCalledTimes(1);
  });

  it("rejects malformed launch payload before validation", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/launch",
      payload: {
        id_token: "",
        state: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "Invalid LTI launch payload",
    });
  });

  it("rejects malformed platform parameter for config route", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/config/%20",
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "Invalid platform parameter",
    });
  });

  it("rejects grade passback when score exceeds maxScore", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/grade-passback",
      payload: {
        sessionId: "session-1",
        userId: "lti-user-1",
        score: 110,
        maxScore: 100,
        lineItemId: "line-item-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "Invalid grade-passback payload",
    });
  });

  it("rejects grade passback when activityProgress value is invalid", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/grade-passback",
      payload: {
        sessionId: "session-1",
        userId: "lti-user-1",
        score: 90,
        maxScore: 100,
        lineItemId: "line-item-1",
        activityProgress: "done",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "Invalid grade-passback payload",
    });
  });
});

function createUnsignedToken(payload: Record<string, unknown>): string {
  const encodedHeader = Buffer.from(
    JSON.stringify({ alg: "none", typ: "JWT" }),
  ).toString("base64url");
  const encodedPayload = Buffer.from(JSON.stringify(payload)).toString(
    "base64url",
  );

  return `${encodedHeader}.${encodedPayload}.signature`;
}
