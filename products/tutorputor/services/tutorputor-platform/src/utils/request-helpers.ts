/**
 * Shared Request Helpers
 *
 * Utility functions for extracting typed request metadata
 * (tenant, user, role) from Fastify request context.
 *
 * SECURITY: Reads from JWT claims (req.user) set by @fastify/jwt preHandler.
 * Falls back to X-* headers only for trusted API-gateway deployments where
 * the gateway strips the Bearer token and injects headers after verification.
 *
 * @doc.type module
 * @doc.purpose Centralized request context extraction
 * @doc.layer platform
 * @doc.pattern Utility
 */

import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";

/** Shape of the JWT payload as decoded by @fastify/jwt */
export interface JwtUser {
  sub?: string;
  userId?: string;
  tenantId?: string;
  role?: string;
}

export type RequestWithContext = {
  headers: Record<string, string | string[] | undefined>;
  user?: JwtUser | string | object | Buffer;
};

function getJwtUser(user: RequestWithContext["user"]): JwtUser | undefined {
  if (!user || typeof user !== "object" || Buffer.isBuffer(user)) {
    return undefined;
  }

  return user as JwtUser;
}

/**
 * Extract the tenant ID from JWT claims or gateway headers.
 * JWT claim `tenantId` takes precedence over `x-tenant-id` header.
 */
export function getTenantId(req: RequestWithContext): TenantId {
  const user = getJwtUser(req.user);

  // Prefer JWT claim (set by global auth guard)
  if (user?.tenantId) return user.tenantId as TenantId;

  // Fallback: gateway-injected header (trusted proxy only)
  const tenantId = req.headers["x-tenant-id"];
  const value = Array.isArray(tenantId) ? tenantId[0] : tenantId;
  return (value || "default") as TenantId;
}

/**
 * Extract the user ID from JWT claims or gateway headers.
 * JWT claim `sub` takes precedence over `x-user-id` header.
 */
export function getUserId(req: RequestWithContext): UserId {
  const user = getJwtUser(req.user);

  // Prefer JWT subject claim (set by global auth guard)
  if (user?.sub) return user.sub as UserId;
  if (user?.userId) return user.userId as UserId;

  // Fallback: gateway-injected header (trusted proxy only)
  const userId = req.headers["x-user-id"];
  const value = Array.isArray(userId) ? userId[0] : userId;
  return (value || "anonymous") as UserId;
}

/**
 * Assert that the current user has one of the required roles.
 * Checks JWT claim `role` first, then `x-user-role` header.
 * Throws an Error if the role check fails.
 */
export function requireRole(req: RequestWithContext, roles: string[]): void {
  const user = getJwtUser(req.user);

  // Prefer JWT claim
  const jwtRole = user?.role;
  if (jwtRole && roles.includes(jwtRole)) return;

  // Fallback: gateway-injected header
  const userRole = req.headers["x-user-role"];
  const role = Array.isArray(userRole) ? userRole[0] : userRole;
  if (!role || !roles.includes(role)) {
    throw new Error("Forbidden: insufficient permissions");
  }
}
