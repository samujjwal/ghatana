/**
 * Shared Request Helpers
 *
 * Utility functions for extracting typed request metadata
 * (tenant, user, role) from Fastify request headers.
 *
 * @doc.type module
 * @doc.purpose Centralized request context extraction
 * @doc.layer platform
 * @doc.pattern Utility
 */

import type { TenantId, UserId } from "@ghatana/tutorputor-contracts/v1/types";

/**
 * Extract the tenant ID from request headers with a typed result.
 */
export function getTenantId(req: {
  headers: Record<string, string | string[] | undefined>;
}): TenantId {
  const tenantId = req.headers["x-tenant-id"];
  const value = Array.isArray(tenantId) ? tenantId[0] : tenantId;
  return (value || "default") as TenantId;
}

/**
 * Extract the user ID from request headers with a typed result.
 */
export function getUserId(req: {
  headers: Record<string, string | string[] | undefined>;
}): UserId {
  const userId = req.headers["x-user-id"];
  const value = Array.isArray(userId) ? userId[0] : userId;
  return (value || "anonymous") as UserId;
}

/**
 * Assert that the request has one of the required roles.
 * Throws an Error if the role check fails.
 */
export function requireRole(
  req: { headers: Record<string, string | string[] | undefined> },
  roles: string[],
): void {
  const userRole = req.headers["x-user-role"];
  const role = Array.isArray(userRole) ? userRole[0] : userRole;
  if (!role || !roles.includes(role)) {
    throw new Error("Forbidden: insufficient permissions");
  }
}
