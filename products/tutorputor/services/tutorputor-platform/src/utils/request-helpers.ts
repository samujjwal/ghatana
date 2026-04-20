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
import { canUseTrustedProxyAuth } from "../core/http/trustedProxyAuth.js";

/** Shape of the JWT payload as decoded by @fastify/jwt */
export interface JwtUser {
  sub?: string;
  userId?: string;
  tenantId?: string;
  role?: string;
}

export type RequestWithContext = {
  ip?: string;
  headers: Record<string, string | string[] | undefined>;
  user?: JwtUser | string | object | Buffer;
};

function getTrustedHeader(
  req: RequestWithContext,
  headerName: "x-tenant-id" | "x-user-id" | "x-user-role",
): string | null {
  if (!canUseTrustedProxyAuth(req as never)) {
    return null;
  }

  const headerValue = req.headers[headerName];
  if (!headerValue) {
    return null;
  }

  return Array.isArray(headerValue) ? headerValue[0] ?? null : headerValue;
}

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

  if (user?.tenantId) return user.tenantId as TenantId;

  const tenantId = getTrustedHeader(req, "x-tenant-id");
  if (!tenantId) {
    throw new Error("Missing tenant context");
  }

  return tenantId as TenantId;
}

/**
 * Extract the user ID from JWT claims or gateway headers.
 * JWT claim `sub` takes precedence over `x-user-id` header.
 */
export function getUserId(req: RequestWithContext): UserId {
  const user = getJwtUser(req.user);

  if (user?.sub) return user.sub as UserId;
  if (user?.userId) return user.userId as UserId;

  const userId = getTrustedHeader(req, "x-user-id");
  if (!userId) {
    throw new Error("Missing user context");
  }

  return userId as UserId;
}

/**
 * Assert that the current user has one of the required roles.
 * Checks JWT claim `role` first, then `x-user-role` header.
 * Throws an Error if the role check fails.
 */
export function requireRole(req: RequestWithContext, roles: string[]): void {
  const user = getJwtUser(req.user);

  const jwtRole = user?.role;
  if (jwtRole && roles.includes(jwtRole)) return;

  const role = getTrustedHeader(req, "x-user-role");
  if (!role || !roles.includes(role)) {
    throw new Error("Forbidden: insufficient permissions");
  }
}
