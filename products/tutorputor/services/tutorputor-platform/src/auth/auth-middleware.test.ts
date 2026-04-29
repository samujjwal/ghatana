/**
 * Auth Middleware Tests
 *
 * Tests for authentication middleware security fixes:
 * - Tenant fallback leak prevention
 * - Tenant isolation header trust
 *
 * @doc.type test
 * @doc.purpose Verify auth middleware security fixes
 * @doc.layer core
 * @doc.pattern Unit Test
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import type { FastifyRequest, FastifyReply } from "fastify";
import { AuthMiddleware } from "./index.js";

describe("Auth Middleware Security Fixes", () => {
  let authMiddleware: AuthMiddleware;
  let mockRequest: Partial<FastifyRequest>;
  let mockReply: Partial<FastifyReply>;

  beforeEach(() => {
    // Mock the JWT verification and other dependencies
    vi.mock("jsonwebtoken", () => ({
      default: {
        verify: vi.fn(() => ({
          sub: "user-123",
          tenantId: "tenant-abc",
          email: "user@example.com",
          role: "student",
        })),
      },
    }));

    authMiddleware = new AuthMiddleware();
    mockRequest = {
      headers: {},
    };
    mockReply = {
      code: vi.fn(() => mockReply),
      send: vi.fn(() => mockReply),
    };
  });

  describe("Platform token fallback tenant leak fix", () => {
    it("should reject platform token without x-tenant-id header", async () => {
      mockRequest.headers = {
        authorization: "Bearer platform-token",
      };

      // Mock auth gateway client to return valid platform identity
      vi.mock("../clients/auth-gateway.client.js", () => ({
        authGatewayClient: {
          validate: vi.fn(() => Promise.resolve({
            valid: true,
            userId: "platform-user-123",
            email: "platform@example.com",
          })),
        },
      }));

      // Should fail because x-tenant-id header is missing
      await expect(
        authMiddleware.authenticate(mockRequest as FastifyRequest, mockReply as FastifyReply)
      ).rejects.toThrow("Missing tenant context in header");
    });

    it("should accept platform token with valid x-tenant-id header", async () => {
      mockRequest.headers = {
        authorization: "Bearer platform-token",
        "x-tenant-id": "tenant-abc",
      };

      // Mock auth gateway client to return valid platform identity
      vi.mock("../clients/auth-gateway.client.js", () => ({
        authGatewayClient: {
          validate: vi.fn(() => Promise.resolve({
            valid: true,
            userId: "platform-user-123",
            email: "platform@example.com",
          })),
        },
      }));

      const authContext = await authMiddleware.authenticate(
        mockRequest as FastifyRequest,
        mockReply as FastifyReply
      );

      expect(authContext).toBeDefined();
      expect(authContext.tenantId).toBe("tenant-abc");
    });
  });

  describe("Tenant isolation header trust fix", () => {
    it("should not trust x-tenant-id header in requireTenantAccess", async () => {
      // Mock authenticated request with JWT tenantId
      const mockAuthContext = {
        user: {
          id: "user-123",
          email: "user@example.com",
          tenantId: "tenant-abc",
          roles: [],
          permissions: [],
          isActive: true,
        },
        token: "jwt-token",
        permissions: [],
        tenantId: "tenant-abc",
      };

      (mockRequest as any).authContext = mockAuthContext;
      mockRequest.params = {};

      // Try to access with x-tenant-id header (should be ignored)
      mockRequest.headers = {
        "x-tenant-id": "malicious-tenant",
      };

      const middleware = authMiddleware.requireTenantAccess();
      
      // Should succeed because header is ignored
      await middleware(mockRequest as FastifyRequest, mockReply as FastifyReply);
      
      // Should not have called send (no error)
      expect(mockReply.send).not.toHaveBeenCalled();
    });

    it("should only trust tenantId from request params (JWT claim)", async () => {
      const mockAuthContext = {
        user: {
          id: "user-123",
          email: "user@example.com",
          tenantId: "tenant-abc",
          roles: [],
          permissions: [],
          isActive: true,
        },
        token: "jwt-token",
        permissions: [],
        tenantId: "tenant-abc",
      };

      (mockRequest as any).authContext = mockAuthContext;
      mockRequest.params = {
        tenantId: "tenant-abc",
      };

      const middleware = authMiddleware.requireTenantAccess();
      
      // Should succeed because param tenantId matches JWT tenantId
      await middleware(mockRequest as FastifyRequest, mockReply as FastifyReply);
      
      expect(mockReply.send).not.toHaveBeenCalled();
    });

    it("should reject when param tenantId differs from JWT tenantId", async () => {
      const mockAuthContext = {
        user: {
          id: "user-123",
          email: "user@example.com",
          tenantId: "tenant-abc",
          roles: [],
          permissions: [],
          isActive: true,
        },
        token: "jwt-token",
        permissions: [],
        tenantId: "tenant-abc",
      };

      (mockRequest as any).authContext = mockAuthContext;
      mockRequest.params = {
        tenantId: "different-tenant",
      };

      const middleware = authMiddleware.requireTenantAccess();
      
      // Should fail because param tenantId differs from JWT tenantId
      await middleware(mockRequest as FastifyRequest, mockReply as FastifyReply);
      
      expect(mockReply.code).toHaveBeenCalledWith(403);
    });
  });
});
