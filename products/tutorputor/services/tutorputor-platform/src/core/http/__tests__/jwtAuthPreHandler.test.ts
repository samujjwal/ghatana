/**
 * JWT Authentication Pre-Handler Tests
 *
 * Tests for global JWT verification pre-handler.
 *
 * @doc.type test
 * @doc.purpose Test JWT authentication pre-handler
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import type { FastifyRequest, FastifyReply } from "fastify";
import { jwtAuthPreHandler } from "../jwtAuthPreHandler.js";
import {
  registerRoutePolicy,
  isInPublicAllowlist,
  getRoutePolicy,
} from "../routePolicyRegistry.js";

describe("JWT Auth Pre-Handler", () => {
  beforeEach(() => {
    // Clear registry before each test
    vi.clearAllMocks();
  });

  describe("Public Routes", () => {
    it("should allow access to health endpoint", async () => {
      const req = {
        method: "GET",
        url: "/health",
        routeOptions: { url: "/health" },
        jwtVerify: vi.fn(),
      } as unknown as FastifyRequest;
      const reply = {} as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).resolves.not.toThrow();
      expect(req.jwtVerify).not.toHaveBeenCalled();
    });

    it("should allow access to readiness endpoint", async () => {
      const req = {
        method: "GET",
        url: "/ready",
        routeOptions: { url: "/ready" },
        jwtVerify: vi.fn(),
      } as unknown as FastifyRequest;
      const reply = {} as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).resolves.not.toThrow();
      expect(req.jwtVerify).not.toHaveBeenCalled();
    });

    it("should allow access to metrics endpoint", async () => {
      const req = {
        method: "GET",
        url: "/metrics",
        routeOptions: { url: "/metrics" },
        jwtVerify: vi.fn(),
      } as unknown as FastifyRequest;
      const reply = {} as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).resolves.not.toThrow();
      expect(req.jwtVerify).not.toHaveBeenCalled();
    });

    it("should allow access to SSO login endpoint", async () => {
      const req = {
        method: "POST",
        url: "/api/v1/auth/sso/login/google",
        routeOptions: { url: "/api/v1/auth/sso/login/:providerId" },
        jwtVerify: vi.fn(),
      } as unknown as FastifyRequest;
      const reply = {} as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).resolves.not.toThrow();
      expect(req.jwtVerify).not.toHaveBeenCalled();
    });
  });

  describe("Protected Routes", () => {
    it("should require JWT for protected routes", async () => {
      const req = {
        method: "GET",
        url: "/api/v1/learning/dashboard",
        routeOptions: { url: "/api/v1/learning/dashboard" },
        jwtVerify: vi.fn().mockRejectedValue(new Error("Invalid token")),
      } as unknown as FastifyRequest;
      const reply = {
        code: vi.fn().mockReturnThis(),
        send: vi.fn().mockReturnThis(),
      } as unknown as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).rejects.toThrow("UNAUTHORIZED");
      expect(req.jwtVerify).toHaveBeenCalled();
    });

    it("should verify valid JWT and populate req.user", async () => {
      const mockUser = {
        userId: "user-123",
        tenantId: "tenant-456",
        role: "learner",
      };

      const req = {
        method: "GET",
        url: "/api/v1/learning/dashboard",
        routeOptions: { url: "/api/v1/learning/dashboard" },
        jwtVerify: vi.fn().mockResolvedValue(undefined),
        user: mockUser,
      } as unknown as FastifyRequest;
      const reply = {} as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).resolves.not.toThrow();
      expect(req.jwtVerify).toHaveBeenCalled();
      expect((req as { user: unknown }).user).toEqual(mockUser);
    });

    it("should reject JWT missing required claims", async () => {
      const req = {
        method: "GET",
        url: "/api/v1/learning/dashboard",
        routeOptions: { url: "/api/v1/learning/dashboard" },
        jwtVerify: vi.fn().mockResolvedValue(undefined),
        user: { userId: "user-123" }, // Missing tenantId
      } as unknown as FastifyRequest;
      const reply = {} as FastifyReply;

      await expect(jwtAuthPreHandler(req, reply)).rejects.toThrow("UNAUTHORIZED");
    });
  });

  describe("Route Policy Registry", () => {
    it("should register and retrieve route policies", () => {
      registerRoutePolicy("GET", "/api/v1/test", {
        authMode: "jwt",
        tenantMode: "required",
        consentMode: "none",
        owner: "test-team",
        testOwner: "test-team",
        inOpenAPI: true,
        hasTypedClient: true,
      });

      const policy = getRoutePolicy("GET", "/api/v1/test");
      expect(policy).toBeDefined();
      expect(policy?.authMode).toBe("jwt");
      expect(policy?.owner).toBe("test-team");
    });

    it("should identify public routes in allowlist", () => {
      expect(isInPublicAllowlist("GET", "/health")).toBe(true);
      expect(isInPublicAllowlist("GET", "/ready")).toBe(true);
      expect(isInPublicAllowlist("GET", "/metrics")).toBe(true);
      expect(isInPublicAllowlist("GET", "/api/v1/learning/dashboard")).toBe(false);
    });

    it("should validate route policy completeness", () => {
      const { validateRoutePolicy } = await import("../routePolicyRegistry.js");

      const validPolicy = {
        authMode: "jwt" as const,
        tenantMode: "required" as const,
        consentMode: "none" as const,
        owner: "test-team",
        testOwner: "test-team",
        inOpenAPI: true,
        hasTypedClient: true,
      };

      expect(() => validateRoutePolicy(validPolicy, "/test")).not.toThrow();

      const invalidPolicy = {
        authMode: "jwt" as const,
        tenantMode: "required" as const,
        consentMode: "none" as const,
        owner: "", // Missing owner
        testOwner: "test-team",
        inOpenAPI: true,
        hasTypedClient: true,
      };

      expect(() => validateRoutePolicy(invalidPolicy, "/test")).toThrow("missing owner");
    });
  });
});
