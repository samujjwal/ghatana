/**
 * Role-based Access Control (RBAC) Middleware
 *
 * Provides route-level authorization based on the UserRole enum defined
 * in the Prisma schema. Works alongside the existing requireAuth/authenticate
 * guards.
 *
 * @doc.type middleware
 * @doc.purpose Role-based route authorization
 * @doc.layer product
 * @doc.pattern Middleware
 *
 * @example
 * ```ts
 * app.get('/admin/users', {
 *   preHandler: [requireAuth, requireRole('ADMIN', 'SUPER_ADMIN')]
 * }, handler);
 * ```
 */

import { FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../lib/prisma';

/**
 * Mirrors the Prisma `UserRole` enum.
 * Keep in sync with schema.prisma.
 */
export type UserRole = 'USER' | 'OPERATOR' | 'ADMIN' | 'SUPER_ADMIN';

/** Ordered hierarchy — higher index = more privilege. */
const ROLE_HIERARCHY: readonly UserRole[] = [
  'USER',
  'OPERATOR',
  'ADMIN',
  'SUPER_ADMIN',
] as const;

/**
 * Returns a Fastify preHandler that rejects requests from users whose
 * role is not in the supplied allow-list.
 *
 * The middleware reads the user's role from the database on every request
 * (a cache layer can be added later). It expects `request.user.userId` to
 * be populated by a preceding auth guard (requireAuth / jwtVerify).
 */
export function requireRole(...allowedRoles: UserRole[]) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const payload = request.user as { userId?: string } | undefined;

    if (!payload?.userId) {
      return reply.code(401).send({
        error: 'Unauthorized',
        message: 'Authentication required',
      });
    }

    const user = await prisma.user.findUnique({
      where: { id: payload.userId },
      select: { role: true },
    });

    if (!user) {
      return reply.code(401).send({
        error: 'Unauthorized',
        message: 'User not found',
      });
    }

    const userRole = (user.role ?? 'USER') as UserRole;

    if (!allowedRoles.includes(userRole)) {
      return reply.code(403).send({
        error: 'Forbidden',
        message: `This action requires one of the following roles: ${allowedRoles.join(', ')}`,
      });
    }

    // Attach role to request for downstream handlers
    (request as any).userRole = userRole;
  };
}

/**
 * Returns a Fastify preHandler that requires the caller's role
 * to be **at least** the given minimum level in the hierarchy.
 *
 * @example
 * ```ts
 * app.delete('/admin/users/:id', {
 *   preHandler: [requireAuth, requireMinRole('ADMIN')]
 * }, handler);
 * ```
 */
export function requireMinRole(minRole: UserRole) {
  const minIndex = ROLE_HIERARCHY.indexOf(minRole);

  return async (request: FastifyRequest, reply: FastifyReply) => {
    const payload = request.user as { userId?: string } | undefined;

    if (!payload?.userId) {
      return reply.code(401).send({
        error: 'Unauthorized',
        message: 'Authentication required',
      });
    }

    const user = await prisma.user.findUnique({
      where: { id: payload.userId },
      select: { role: true },
    });

    if (!user) {
      return reply.code(401).send({
        error: 'Unauthorized',
        message: 'User not found',
      });
    }

    const userRole = (user.role ?? 'USER') as UserRole;
    const userIndex = ROLE_HIERARCHY.indexOf(userRole);

    if (userIndex < minIndex) {
      return reply.code(403).send({
        error: 'Forbidden',
        message: `This action requires at least ${minRole} role`,
      });
    }

    (request as any).userRole = userRole;
  };
}
