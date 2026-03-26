/**
 * RBAC Service
 *
 * Centralised role-based access control service.
 * Evaluates permission requests against the static permission matrix and,
 * when workspace context is available, the caller's dynamic workspace role.
 *
 * @doc.type class
 * @doc.purpose Centralised RBAC enforcement
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';
import {
  type UserRole,
  type ResourceType,
  type ActionType,
  isAllowed,
  roleAtLeast,
} from './permissions';

// ============================================================================
// Types
// ============================================================================

export interface AccessCheckInput {
  userId: string;
  workspaceId?: string;
  resource: ResourceType;
  action: ActionType;
}

export interface AccessCheckResult {
  allowed: boolean;
  role: UserRole | null;
  reason?: string;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Evaluates RBAC permission requests.
 *
 * For workspace-scoped checks, the user's WorkspaceMember role is resolved
 * from the database.  For global checks, the user's system role is used.
 *
 * @doc.type class
 * @doc.purpose Role-based access control evaluation
 * @doc.layer product
 * @doc.pattern Service
 */
export class RBACService {
  private prisma: PrismaClient;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  /**
   * Resolve the effective role for a user in an optional workspace context.
   */
  async resolveRole(
    userId: string,
    workspaceId?: string
  ): Promise<UserRole | null> {
    if (workspaceId) {
      const member = await this.prisma.workspaceMember.findUnique({
        where: { userId_workspaceId: { userId, workspaceId } },
      });
      return (member?.role as UserRole) ?? null;
    }

    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { role: true },
    });
    return (user?.role as UserRole) ?? null;
  }

  /**
   * Check whether a user is authorised to perform an action on a resource.
   */
  async check(input: AccessCheckInput): Promise<AccessCheckResult> {
    const role = await this.resolveRole(input.userId, input.workspaceId);

    if (!role) {
      return {
        allowed: false,
        role: null,
        reason: 'User not found or not a workspace member',
      };
    }

    const allowed = isAllowed(role, input.resource, input.action);

    return {
      allowed,
      role,
      reason: allowed
        ? undefined
        : `Role '${role}' does not have '${input.action}' on '${input.resource}'`,
    };
  }

  /**
   * Throw an error if the user is not authorised.  Convenience wrapper for resolvers.
   */
  async enforce(input: AccessCheckInput): Promise<void> {
    const result = await this.check(input);
    if (!result.allowed) {
      throw new Error(`Forbidden: ${result.reason}`);
    }
  }

  /**
   * Returns true if the user has at least the required role in a workspace.
   */
  async hasMinimumRole(
    userId: string,
    workspaceId: string,
    minimumRole: UserRole
  ): Promise<boolean> {
    const role = await this.resolveRole(userId, workspaceId);
    if (!role) return false;
    return roleAtLeast(role, minimumRole);
  }
}

// Lazy singleton
let _instance: RBACService | null = null;

/**
 * Returns the singleton RBACService instance.
 *
 * @doc.type function
 * @doc.purpose Lazy-initialise RBACService singleton
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getRBACService(): RBACService {
  if (!_instance) {
    _instance = new RBACService();
  }
  return _instance;
}
