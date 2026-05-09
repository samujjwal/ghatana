/**
 * JWT Authentication Pre-Handler
 *
 * Global pre-handler for JWT verification on protected routes.
 * Skips public routes, verifies JWT tokens, and populates req.user.
 * Enforces consistent authorization policy including tenant scoping and permissions.
 *
 * @doc.type module
 * @doc.purpose Global JWT verification and authorization for protected routes
 * @doc.layer platform
 * @doc.pattern PreHandler
 */

import type { FastifyRequest, FastifyReply } from "fastify";
import { isInPublicAllowlist, getRoutePolicy } from "./routePolicyRegistry.js";
import { hasPermission } from "../authz/permissionPolicy.js";

/** Shape of the JWT payload as decoded by @fastify/jwt into req.user */
interface JwtUser {
  userId: string;
  tenantId: string;
  role?: string;
}

/**
 * Custom HTTP error with status code
 */
function createHttpError(
  statusCode: number,
  code: string,
  message: string,
): Error & { statusCode: number; code: string } {
  const error = new Error(message) as Error & { statusCode: number; code: string };
  error.statusCode = statusCode;
  error.code = code;
  return error;
}

/**
 * Check if trusted proxy authentication can be used
 * Only allowed in non-production environments
 */
function canUseTrustedProxyAuth(req: FastifyRequest): boolean {
  const env = process.env.NODE_ENV || "development";
  if (env === "production") {
    return false;
  }

  // Check for trusted proxy headers
  return !!(
    req.headers["x-tenant-id"] &&
    req.headers["x-user-id"] &&
    (req.headers["x-forwarded-for"] || req.headers["x-real-ip"])
  );
}

/**
 * Enforce tenant scoping based on route policy
 */
function enforceTenantScoping(
  req: FastifyRequest,
  policy: { tenantMode: "none" | "required" | "optional" },
  user: JwtUser,
): void {
  if (policy.tenantMode === "required" && !user.tenantId) {
    throw createHttpError(
      401,
      "TENANT_REQUIRED",
      "Tenant context is required for this endpoint",
    );
  }
}

/**
 * Global JWT verification pre-handler
 *
 * This pre-handler:
 * 1. Skips public routes (health, readiness, SSO callbacks, JWKS)
 * 2. Verifies JWT token for protected routes
 * 3. Populates req.user with decoded JWT payload
 * 4. Optionally allows trusted proxy auth in non-production environments
 * 5. Enforces tenant scoping and permissions based on route policy
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

  // Enforce tenant scoping based on route policy
  if (policy) {
    enforceTenantScoping(req, policy, user);

    // Enforce permissions if specified in route policy
    if (policy.permissions && policy.permissions.length > 0) {
      const hasRequiredPermission = policy.permissions.some((permission) =>
        hasPermission(user.role, permission),
      );

      if (!hasRequiredPermission) {
        throw createHttpError(
          403,
          "FORBIDDEN",
          `Insufficient permissions. Required: ${policy.permissions.join(", ")}`,
        );
      }
    }
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
