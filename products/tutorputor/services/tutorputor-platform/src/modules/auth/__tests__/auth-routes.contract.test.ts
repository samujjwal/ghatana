import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createServer } from "../../../setup.js";
import type { FastifyInstance } from "fastify";

describe("Auth routes contract tests", () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  describe("GET /api/v1/auth/me", () => {
    it("should return user profile when authenticated", async () => {
      const token = app.jwt.sign({
        sub: "user-1",
        tenantId: "tenant-1",
        role: "learner",
        email: "user@example.com",
        name: "Test User",
      });

      const response = await app.inject({
        method: "GET",
        url: "/api/v1/auth/me",
        headers: {
          authorization: `Bearer ${token}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = response.json();
      expect(body).toMatchObject({
        id: "user-1",
        tenantId: "tenant-1",
        role: "learner",
        email: "user@example.com",
        displayName: "Test User",
      });
    });

    it("should return 401 when no token provided", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/auth/me",
      });

      expect(response.statusCode).toBe(401);
      expect(response.json()).toMatchObject({
        error: expect.stringContaining("Authorization"),
      });
    });
  });

  describe("POST /api/v1/auth/refresh", () => {
    it("should return new tokens when refresh token is valid", async () => {
      const user = {
        id: "user-1",
        email: "user@example.com",
        displayName: "Test User",
        role: "learner",
        tenantId: "tenant-1",
      };

      const refreshToken = app.jwt.sign(
        {
          sub: user.id,
          tenantId: user.tenantId,
          role: user.role,
          type: "refresh",
          jti: "test-jti-123",
        },
        { expiresIn: "7d" },
      );

      // Store the refresh session in Redis
      const redis = (app as any).redis;
      await redis.set(
        `auth:refresh-session:test-jti-123`,
        JSON.stringify({
          userId: user.id,
          tenantId: user.tenantId,
          jti: "test-jti-123",
          tokenHash: require("crypto")
            .createHash("sha256")
            .update(refreshToken)
            .digest("hex"),
        }),
        "EX",
        7 * 24 * 60 * 60,
      );

      const response = await app.inject({
        method: "POST",
        url: "/api/v1/auth/refresh",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({ refreshToken }),
      });

      expect(response.statusCode).toBe(200);
      const body = response.json();
      expect(body).toHaveProperty("accessToken");
      expect(body).toHaveProperty("refreshToken");
      expect(body).toHaveProperty("user");

      // Verify the new access token can be decoded
      const decoded = app.jwt.verify(body.accessToken) as { sub: string; tenantId: string };
      expect(decoded.sub).toBe(user.id);
      expect(decoded.tenantId).toBe(user.tenantId);
    });

    it("should return 400 when refresh token is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/v1/auth/refresh",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({}),
      });

      expect(response.statusCode).toBe(400);
      expect(response.json()).toMatchObject({
        error: expect.stringContaining("refreshToken"),
      });
    });

    it("should return 401 when refresh token is invalid", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/v1/auth/refresh",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({ refreshToken: "invalid-token" }),
      });

      expect(response.statusCode).toBe(401);
      expect(response.json()).toMatchObject({
        error: expect.stringContaining("invalid"),
      });
    });
  });

  describe("POST /api/v1/auth/logout", () => {
    it("should logout successfully with refresh token", async () => {
      const refreshToken = app.jwt.sign(
        {
          sub: "user-1",
          tenantId: "tenant-1",
          role: "learner",
          type: "refresh",
          jti: "test-jti-logout",
        },
        { expiresIn: "7d" },
      );

      const response = await app.inject({
        method: "POST",
        url: "/api/v1/auth/logout",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({ refreshToken }),
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        success: true,
      });
    });

    it("should logout successfully without refresh token (idempotent)", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/v1/auth/logout",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({}),
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        success: true,
      });
    });
  });

  describe("GET /api/v1/auth/sso/providers", () => {
    it("should return providers when tenantSlug is provided", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/auth/sso/providers?tenantSlug=test-tenant",
      });

      // Might return empty list or actual providers depending on test data
      expect(response.statusCode).toBe(200);
      const body = response.json();
      expect(body).toHaveProperty("providers");
      expect(Array.isArray(body.providers)).toBe(true);
    });

    it("should return 400 when tenantSlug is missing", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/auth/sso/providers",
      });

      expect(response.statusCode).toBe(400);
      expect(response.json()).toMatchObject({
        error: expect.stringContaining("tenantSlug"),
      });
    });
  });

  describe("GET /api/v1/auth/health", () => {
    it("should return health status", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/v1/auth/health",
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        module: "auth",
        status: "active",
      });
    });
  });
});
