/**
 * Tenant/Workspace Scoping Middleware
 *
 * Ensures all database queries are properly scoped by tenant/workspace
 * to prevent cross-tenant data leaks.
 *
 * @doc.type middleware
 * @doc.purpose Enforce tenant/workspace query scoping
 * @doc.layer middleware
 * @doc.pattern Security Middleware
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getPrismaClient } from '../database/client';

// Extend FastifyRequest to include tenant context
declare module 'fastify' {
  interface FastifyRequest {
    tenantContext?: {
      workspaceIds: string[];
      tenantId?: string;
      isAdmin: boolean;
    };
  }
}

/**
 * Middleware to extract and validate tenant/workspace scope from request
 */
export async function tenantScopingMiddleware(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  const user = request.user;
  if (!user) {
    return reply.status(401).send({
      error: 'Unauthorized',
      message: 'Authentication required for tenant scoping',
    });
  }

  try {
    const prisma = getPrismaClient();
    const userId = user.userId;

    // Get all workspaces where user is a member
    const memberships = await prisma.workspaceMember.findMany({
      where: { userId },
      select: { workspaceId: true, role: true },
    });

    const workspaceIds = memberships.map((m) => m.workspaceId);
    const isAdmin = memberships.some((m) => m.role === 'ADMIN');

    // Set tenant context on request
    request.tenantContext = {
      workspaceIds,
      tenantId: user.tenantId,
      isAdmin,
    };

    // Validate workspace access if workspaceId is in params/query
    const targetWorkspaceId =
      (request.params as { workspaceId?: string }).workspaceId ||
      (request.query as { workspaceId?: string }).workspaceId;

    if (targetWorkspaceId && !isAdmin) {
      if (!workspaceIds.includes(targetWorkspaceId)) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to this workspace',
        });
      }
    }
  } catch (error) {
    console.error('[TenantScoping] Error:', error);
    return reply.status(500).send({
      error: 'Internal Server Error',
      message: 'Failed to validate tenant scope',
    });
  }
}

/**
 * Create a Prisma client extension that enforces tenant scoping
 */
export function createScopedPrismaClient(request: FastifyRequest) {
  const prisma = getPrismaClient();
  const tenantContext = request.tenantContext;

  if (!tenantContext) {
    throw new Error('Tenant context not initialized');
  }

  // Return extended Prisma client with automatic scoping
  return prisma.$extends({
    query: {
      // Scope workspace queries
      workspace: {
        async findMany({ args, query }) {
          if (!tenantContext.isAdmin) {
            args.where = {
              ...args.where,
              id: { in: tenantContext.workspaceIds },
            };
          }
          return query(args);
        },
        async findUnique({ args, query }) {
          if (!tenantContext.isAdmin && args.where.id) {
            // Check if user has access to this workspace
            if (!tenantContext.workspaceIds.includes(args.where.id as string)) {
              return null;
            }
          }
          return query(args);
        },
        async count({ args, query }) {
          if (!tenantContext.isAdmin) {
            args.where = {
              ...args.where,
              id: { in: tenantContext.workspaceIds },
            };
          }
          return query(args);
        },
      },

      // Scope project queries
      project: {
        async findMany({ args, query }) {
          if (!tenantContext.isAdmin) {
            args.where = {
              ...args.where,
              OR: [
                { ownerWorkspaceId: { in: tenantContext.workspaceIds } },
                {
                  workspaceProjects: {
                    some: {
                      workspaceId: { in: tenantContext.workspaceIds },
                    },
                  },
                },
              ],
            };
          }
          return query(args);
        },
        async findUnique({ args, query }) {
          const result = await query(args);
          if (
            !tenantContext.isAdmin &&
            result &&
            !hasProjectAccess(result, tenantContext.workspaceIds)
          ) {
            return null;
          }
          return result;
        },
        async count({ args, query }) {
          if (!tenantContext.isAdmin) {
            args.where = {
              ...args.where,
              OR: [
                { ownerWorkspaceId: { in: tenantContext.workspaceIds } },
                {
                  workspaceProjects: {
                    some: {
                      workspaceId: { in: tenantContext.workspaceIds },
                    },
                  },
                },
              ],
            };
          }
          return query(args);
        },
      },

      // Scope canvas document queries
      canvasDocument: {
        async findMany({ args, query }) {
          // Canvas documents are scoped through their projects
          if (!tenantContext.isAdmin) {
            args.where = {
              ...args.where,
              project: {
                OR: [
                  { ownerWorkspaceId: { in: tenantContext.workspaceIds } },
                  {
                    workspaceProjects: {
                      some: {
                        workspaceId: { in: tenantContext.workspaceIds },
                      },
                    },
                  },
                ],
              },
            };
          }
          return query(args);
        },
      },

      // Scope lifecycle artifact queries
      lifecycleArtifact: {
        async findMany({ args, query }) {
          if (!tenantContext.isAdmin) {
            args.where = {
              ...args.where,
              project: {
                OR: [
                  { ownerWorkspaceId: { in: tenantContext.workspaceIds } },
                  {
                    workspaceProjects: {
                      some: {
                        workspaceId: { in: tenantContext.workspaceIds },
                      },
                    },
                  },
                ],
              },
            };
          }
          return query(args);
        },
      },
    },
  });
}

/**
 * Helper to check if user has access to a project
 */
function hasProjectAccess(
  project: { ownerWorkspaceId?: string; workspaceProjects?: Array<{ workspaceId: string }> },
  userWorkspaceIds: string[]
): boolean {
  if (project.ownerWorkspaceId && userWorkspaceIds.includes(project.ownerWorkspaceId)) {
    return true;
  }
  if (project.workspaceProjects) {
    return project.workspaceProjects.some((wp) =>
      userWorkspaceIds.includes(wp.workspaceId)
    );
  }
  return false;
}

/**
 * Apply tenant scoping to Fastify instance
 */
export async function applyTenantScoping(app: FastifyInstance): Promise<void> {
  // Add hook to extract tenant context for all routes
  app.addHook('onRequest', async (request, reply) => {
    // Skip for public paths
    const publicPaths = ['/health', '/live', '/ready', '/metrics', '/api/auth'];
    if (publicPaths.some((path) => request.url.startsWith(path))) {
      return;
    }

    await tenantScopingMiddleware(request, reply);
  });
}

/**
 * Ensure a query is scoped to user's accessible workspaces
 */
export function scopeToWorkspaces(
  whereClause: Record<string, unknown>,
  workspaceIds: string[]
): Record<string, unknown> {
  return {
    ...whereClause,
    workspaceId: { in: workspaceIds },
  };
}

/**
 * Ensure a query is scoped to user's accessible projects
 */
export function scopeToProjects(
  whereClause: Record<string, unknown>,
  workspaceIds: string[]
): Record<string, unknown> {
  return {
    ...whereClause,
    OR: [
      { ownerWorkspaceId: { in: workspaceIds } },
      {
        workspaceProjects: {
          some: {
            workspaceId: { in: workspaceIds },
          },
        },
      },
    ],
  };
}
