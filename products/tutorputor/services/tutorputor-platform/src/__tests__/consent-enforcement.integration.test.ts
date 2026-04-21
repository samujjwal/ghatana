import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createServer } from "../setup.js";
import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";

describe("Consent enforcement integration tests", () => {
  let app: FastifyInstance;
  let prisma: PrismaClient & {
    userConsent: {
      create: (args: any) => Promise<any>;
      createMany: (args: any) => Promise<any>;
    };
  };

  beforeEach(async () => {
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
    });
    await app.ready();
    prisma = (app as any).prisma as PrismaClient & {
      userConsent: {
        create: (args: any) => Promise<any>;
        createMany: (args: any) => Promise<any>;
      };
    };
  });

  afterEach(async () => {
    await app.close();
  });

  it("should block AI endpoints without ai_processing consent", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    // Should return 451 (Unavailable For Legal Reasons) for missing consent
    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["ai_processing"]),
    });
  });

  it("should block analytics endpoints without analytics consent", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/analytics/dashboard",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["analytics"]),
    });
  });

  it("should allow AI endpoints with ai_processing consent", async () => {
    // Create user consent record
    await prisma.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-1",
        category: "ai_processing",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    // Should not be 451 (consent error) - might be 404 or other response depending on implementation
    expect(response.statusCode).not.toBe(451);
  });

  it("should allow analytics endpoints with analytics consent", async () => {
    await prisma.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-1",
        category: "analytics",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/analytics/dashboard",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).not.toBe(451);
  });

  it("should exempt admin roles from consent requirements", async () => {
    const token = app.jwt.sign({
      sub: "admin-user",
      tenantId: "tenant-1",
      role: "admin",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    // Admin should not be blocked by consent requirements
    expect(response.statusCode).not.toBe(451);
  });

  it("should skip consent check for unauthenticated requests", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/analytics/dashboard",
    });

    // Should be 401 (unauthorized) not 451 (consent required)
    expect(response.statusCode).toBe(401);
  });

  it("should allow routes without consent requirements", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/modules",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    // Should not be 451 - modules route doesn't require consent
    expect(response.statusCode).not.toBe(451);
  });

  it("should require multiple consent categories for recommendations", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/recommendations/next",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining([
        "analytics",
        "ai_processing",
      ]),
    });
  });

  it("should allow recommendations with all required consents", async () => {
    await prisma.userConsent.createMany({
      data: [
        {
          tenantId: "tenant-1",
          userId: "user-1",
          category: "analytics",
          granted: true,
        },
        {
          tenantId: "tenant-1",
          userId: "user-1",
          category: "ai_processing",
          granted: true,
        },
      ],
    });

    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/recommendations/next",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).not.toBe(451);
  });

  it("should use JWT sub claim for user identity", async () => {
    await prisma.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-with-sub",
        category: "ai_processing",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "user-with-sub",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    expect(response.statusCode).not.toBe(451);
  });
});
