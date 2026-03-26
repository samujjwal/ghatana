/**
 * RBAC Enforcement Middleware
 *
 * Provides a Fastify pre-handler factory that verifies the caller holds the
 * required permission before the route handler executes.
 *
 * Usage:
 *   fastify.get('/workspaces/:id', {
 *     preHandler: requirePermission('workspace', 'read'),
 *   }, handler);
 *
 * @doc.type module
 * @doc.purpose Fastify RBAC pre-handler factory
 * @doc.layer product
 * @doc.pattern Middleware
 */

import type {
  FastifyRequest,
  FastifyReply,
  preHandlerHookHandler,
} from 'fastify';
import {
  type ResourceType,
  type ActionType,
  isAllowed,
} from '../services/auth/permissions';
import type { UserRole } from '../services/auth/permissions';

// ============================================================================
// Pre-handler factory
// ============================================================================

/**
 * Returns a Fastify pre-handler that enforces the given permission.
 * The caller's role is read from `request.user.role` (populated by authMiddleware).
 *
 * @doc.type function
 * @doc.purpose Create a Fastify pre-handler that enforces RBAC
 * @doc.layer product
 * @doc.pattern Middleware
 */
export function requirePermission(
  resource: ResourceType,
  action: ActionType
): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const role = user.role as UserRole;

    if (!isAllowed(role, resource, action)) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: `Role '${role}' is not allowed to '${action}' on '${resource}'`,
      });
    }
  };
}

/**
 * Returns a Fastify pre-handler that requires the user to hold at least
 * the given role level (i.e. VIEWER < EDITOR < ADMIN < OWNER).
 *
 * @doc.type function
 * @doc.purpose Create minimum-role enforcement pre-handler
 * @doc.layer product
 * @doc.pattern Middleware
 */
export function requireRole(minimumRole: UserRole): preHandlerHookHandler {
  const roleRank: Record<UserRole, number> = {
    VIEWER: 1,
    EDITOR: 2,
    ADMIN: 3,
    OWNER: 4,
  };

  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const rank = roleRank[user.role as UserRole] ?? 0;
    const required = roleRank[minimumRole] ?? 999;

    if (rank < required) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: `Role '${user.role}' does not meet minimum required role '${minimumRole}'`,
      });
    }
  };
}
