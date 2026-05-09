/**
 * JWT Authentication Pre-Handler
 *
 * Global JWT verification pre-handler for protected routes.
 * This must be registered before any protected route handlers.
 *
 * @doc.type module
 * @doc.purpose Global JWT verification for protected routes
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import type { FastifyRequest, FastifyReply } from "fastify";
import { createHttpError } from "./requestContext.js";
import {
  isInPublicAllowlist,
  getRoutePolicy,
} from "./routePolicyRegistry.js";
import { canUseTrustedProxyAuth } from "./trustedProxyAuth.js";

/** Shape of the JWT payload as decoded by @fastify/jwt into req.user */
interface JwtUser {
  id?: string;
  sub?: string;
  userId?: string;
  tenantId?: string;
  role?: string;
  email?: string;
}

/**
 * Global JWT verification pre-handler
 *
 * This pre-handler:
 * 1. Skips public routes (health, readiness, SSO callbacks, JWKS)
 * 2. Verifies JWT token for protected routes
 * 3. Populates req.user with decoded JWT payload
 * 4. Optionally allows trusted proxy auth in non-production environments
 */
export async function jwtAuthPreHandler(
  req: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const method = req.method;
  const path = req.routeOptions.url || req.url;

  // Skip public routes
  if (isInPublicAllowlist(method, path)) {
    return;
  }

  // Check route policy for auth mode
  const policy = getRoutePolicy(method, path);
  if (policy?.authMode === "public") {
    return;
  }

  // Verify JWT token
  try {
    await req.jwtVerify();
  } catch (error) {
    // If JWT verification fails, check if trusted proxy auth is allowed
    if (policy?.authMode === "jwt_or_trusted_proxy" && canUseTrustedProxyAuth(req)) {
      // Trusted proxy auth will populate req.user via headers
      const tenantId = req.headers["x-tenant-id"];
      const userId = req.headers["x-user-id"];
      const role = req.headers["x-user-role"];

      if (!tenantId || !userId) {
        throw createHttpError(
          401,
          "UNAUTHORIZED",
          "Missing tenant or user context from trusted proxy",
        );
      }

      // Populate req.user with trusted proxy data
      (req as FastifyRequest & { user?: JwtUser }).user = {
        tenantId: Array.isArray(tenantId) ? tenantId[0] : tenantId,
        userId: Array.isArray(userId) ? userId[0] : userId,
        role: role ? (Array.isArray(role) ? role[0] : role) : undefined,
      };
      return;
    }

    // JWT verification failed and no trusted proxy fallback
    throw createHttpError(
      401,
      "UNAUTHORIZED",
      "Invalid or missing JWT token",
    );
  }

  // JWT verified successfully, req.user is populated by @fastify/jwt
  const user = (req as FastifyRequest & { user?: JwtUser }).user;

  if (!user || !user.userId || !user.tenantId) {
    throw createHttpError(
      401,
      "UNAUTHORIZED",
      "JWT token missing required claims (userId, tenantId)",
    );
  }
}

/**
 * Worker JWT verification pre-handler
 *
 * Specialized JWT verification for worker callbacks.
 * Requires additional worker-specific claims.
 */
export async function workerJwtAuthPreHandler(
  req: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  try {
    await req.jwtVerify();
  } catch (error) {
    throw createHttpError(
      401,
      "UNAUTHORIZED",
      "Invalid or missing worker JWT token",
    );
  }

  const user = (req as FastifyRequest & { user?: JwtUser }).user;

  if (!user) {
    throw createHttpError(
      401,
      "UNAUTHORIZED",
      "Worker JWT token missing required claims",
    );
  }

  // Verify worker-specific claim
  // Workers should have a workerType claim or similar
  // This is a placeholder - adjust based on actual worker JWT structure
  const workerType = (user as Record<string, unknown>).workerType as
    | string
    | undefined;

  if (!workerType) {
    throw createHttpError(
      403,
      "FORBIDDEN",
      "JWT token is not a valid worker token",
    );
  }
}
