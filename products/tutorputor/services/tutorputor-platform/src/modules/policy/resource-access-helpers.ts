/**
 * Resource Access Helpers
 *
 * Central helpers for tenant-scoped lookups, self-or-privileged checks,
 * and sensitive-operation audit payload construction.
 *
 * @doc.type util
 * @doc.purpose Reusable policy enforcement primitives for resource access
 * @doc.layer product
 * @doc.pattern Helper
 */

import type { PolicyDecision } from "./abac-policy-engine.js";

export type PrivilegedRole = "admin" | "superadmin";

export interface ResourceActor {
  userId: string;
  tenantId: string;
  roles: string[];
}

export interface SensitiveOperationAuditEntry {
  actorId: string;
  actorTenantId: string;
  targetResourceType: string;
  targetResourceId: string;
  operation: string;
  decision: PolicyDecision;
  reason: string;
  correlationId: string | undefined;
  metadata: Record<string, string | number | boolean>;
  timestamp: string;
}

/**
 * Build a safe Prisma-like `where` clause that always includes tenant binding.
 */
export function buildTenantScopedWhere(
  tenantId: string,
  resourceId: string,
): { id: string; tenantId: string } {
  return {
    id: resourceId,
    tenantId,
  };
}

/**
 * Returns true if actor can mutate/read a user-owned resource by self ownership
 * or by privileged role (admin/superadmin).
 */
export function isSelfOrPrivileged(
  actor: ResourceActor,
  ownerUserId: string,
): boolean {
  if (actor.userId === ownerUserId) {
    return true;
  }

  return actor.roles.some((role) => role === "admin" || role === "superadmin");
}

/**
 * Throws when actor attempts access outside tenant boundary.
 */
export function assertSameTenant(
  actorTenantId: string,
  resourceTenantId: string,
  operation: string,
): void {
  if (actorTenantId !== resourceTenantId) {
    throw new Error(`Cross-tenant ${operation} denied`);
  }
}

/**
 * Construct a normalized sensitive-operation audit entry that can be persisted.
 */
export function buildSensitiveOperationAuditEntry(args: {
  actorId: string;
  actorTenantId: string;
  targetResourceType: string;
  targetResourceId: string;
  operation: string;
  decision: PolicyDecision;
  reason: string;
  correlationId: string | undefined;
  metadata: Record<string, string | number | boolean>;
}): SensitiveOperationAuditEntry {
  return {
    actorId: args.actorId,
    actorTenantId: args.actorTenantId,
    targetResourceType: args.targetResourceType,
    targetResourceId: args.targetResourceId,
    operation: args.operation,
    decision: args.decision,
    reason: args.reason,
    correlationId: args.correlationId,
    metadata: args.metadata,
    timestamp: new Date().toISOString(),
  };
}
