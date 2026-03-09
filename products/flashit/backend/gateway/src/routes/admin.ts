/**
 * Admin API Routes
 *
 * Protected routes for platform administrators. Requires ADMIN or SUPER_ADMIN role.
 *
 * Endpoints:
 * - GET  /api/admin/users                    List all users (paginated)
 * - GET  /api/admin/users/:id                Get user details
 * - PATCH /api/admin/users/:id/role          Update user role
 * - PATCH /api/admin/users/:id/suspend       Suspend / unsuspend user
 * - DELETE /api/admin/users/:id              Soft-delete user account
 * - GET  /api/admin/content/flagged          List flagged / reported content
 * - PATCH /api/admin/content/:momentId/moderate  Moderate a moment
 * - GET  /api/admin/stats                    Platform statistics
 *
 * @doc.type routes
 * @doc.purpose Platform administration endpoints
 * @doc.layer product
 * @doc.pattern RestAPI
 */

import { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { requireAuth, type JwtPayload } from '../lib/auth';
import { requireRole, requireMinRole } from '../middleware/require-role';

// ============================================================================
// Schemas
// ============================================================================

const paginationSchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(20),
  search: z.string().optional(),
});

const updateRoleSchema = z.object({
  role: z.enum(['USER', 'OPERATOR', 'ADMIN', 'SUPER_ADMIN']),
});

const suspendSchema = z.object({
  suspended: z.boolean(),
  reason: z.string().min(1).max(500).optional(),
});

const moderateSchema = z.object({
  action: z.enum(['remove', 'flag', 'unflag', 'restore']),
  reason: z.string().min(1).max(500),
});

// ============================================================================
// Routes
// ============================================================================

const adminRoutes = async (app: FastifyInstance) => {
  // All routes require auth + ADMIN role minimum
  app.addHook('preHandler', requireAuth);
  app.addHook('preHandler', requireMinRole('ADMIN'));

  // -----------------------------------------------------------------------
  // Users
  // -----------------------------------------------------------------------

  /**
   * GET /api/admin/users
   * List all users with pagination & optional search
   */
  app.get('/users', async (request, reply) => {
    const { page, limit, search } = paginationSchema.parse(request.query);
    const skip = (page - 1) * limit;

    const where: any = { deletedAt: null };
    if (search) {
      where.OR = [
        { email: { contains: search, mode: 'insensitive' } },
        { displayName: { contains: search, mode: 'insensitive' } },
      ];
    }

    const [users, total] = await Promise.all([
      prisma.user.findMany({
        where,
        select: {
          id: true,
          email: true,
          displayName: true,
          role: true,
          subscriptionTier: true,
          subscriptionStatus: true,
          createdAt: true,
          lastLoginAt: true,
        },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      prisma.user.count({ where }),
    ]);

    return reply.send({
      success: true,
      data: users,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    });
  });

  /**
   * GET /api/admin/users/:id
   * Get detailed user info
   */
  app.get('/users/:id', async (request, reply) => {
    const { id } = request.params as { id: string };

    const user = await prisma.user.findUnique({
      where: { id },
      select: {
        id: true,
        email: true,
        displayName: true,
        role: true,
        subscriptionTier: true,
        subscriptionStatus: true,
        createdAt: true,
        lastLoginAt: true,
        deletedAt: true,
        _count: {
          select: {
            moments: true,
            spheres: true,
            aiInsights: true,
          },
        },
      },
    });

    if (!user) {
      return reply.code(404).send({ error: 'Not Found', message: 'User not found' });
    }

    return reply.send({ success: true, data: user });
  });

  /**
   * PATCH /api/admin/users/:id/role
   * Update a user's system role (SUPER_ADMIN only)
   */
  app.patch('/users/:id/role', {
    preHandler: [requireRole('SUPER_ADMIN')],
  }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const { role } = updateRoleSchema.parse(request.body);
    const actorId = (request.user as JwtPayload).userId;

    if (id === actorId) {
      return reply.code(400).send({
        error: 'Bad Request',
        message: 'Cannot change your own role',
      });
    }

    const user = await prisma.user.update({
      where: { id },
      data: { role },
      select: { id: true, email: true, role: true },
    });

    // Audit
    await prisma.auditEvent.create({
      data: {
        userId: actorId,
        eventType: 'ACCOUNT_UPDATED',
        details: { action: 'role_change', targetUserId: id, newRole: role },
        ipAddress: request.ip,
        userAgent: request.headers['user-agent'] || '',
      },
    });

    return reply.send({ success: true, data: user });
  });

  /**
   * PATCH /api/admin/users/:id/suspend
   * Suspend or unsuspend a user account
   */
  app.patch('/users/:id/suspend', async (request, reply) => {
    const { id } = request.params as { id: string };
    const { suspended, reason } = suspendSchema.parse(request.body);
    const actorId = (request.user as JwtPayload).userId;

    if (id === actorId) {
      return reply.code(400).send({
        error: 'Bad Request',
        message: 'Cannot suspend your own account',
      });
    }

    // Use deletedAt as a soft-suspension flag (or add dedicated suspendedAt later)
    const user = await prisma.user.update({
      where: { id },
      data: { deletedAt: suspended ? new Date() : null },
      select: { id: true, email: true, deletedAt: true },
    });

    await prisma.auditEvent.create({
      data: {
        userId: actorId,
        eventType: 'ACCOUNT_UPDATED',
        details: { action: suspended ? 'suspend' : 'unsuspend', targetUserId: id, reason },
        ipAddress: request.ip,
        userAgent: request.headers['user-agent'] || '',
      },
    });

    return reply.send({ success: true, data: { ...user, suspended } });
  });

  /**
   * DELETE /api/admin/users/:id
   * Soft-delete a user account (SUPER_ADMIN only)
   */
  app.delete('/users/:id', {
    preHandler: [requireRole('SUPER_ADMIN')],
  }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const actorId = (request.user as JwtPayload).userId;

    if (id === actorId) {
      return reply.code(400).send({ error: 'Bad Request', message: 'Cannot delete your own account' });
    }

    await prisma.user.update({
      where: { id },
      data: { deletedAt: new Date() },
    });

    await prisma.auditEvent.create({
      data: {
        userId: actorId,
        eventType: 'ACCOUNT_DELETED',
        details: { action: 'admin_delete', targetUserId: id },
        ipAddress: request.ip,
        userAgent: request.headers['user-agent'] || '',
      },
    });

    return reply.code(204).send();
  });

  // -----------------------------------------------------------------------
  // Content Moderation
  // -----------------------------------------------------------------------

  /**
   * GET /api/admin/content/flagged
   * List moments that have been reported
   */
  app.get('/content/flagged', async (request, reply) => {
    const { page, limit } = paginationSchema.parse(request.query);
    const skip = (page - 1) * limit;

    const [reports, total] = await Promise.all([
      prisma.report.findMany({
        where: { status: 'pending' },
        include: {
          user: { select: { id: true, email: true, displayName: true } },
        },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      prisma.report.count({ where: { status: 'pending' } }),
    ]);

    return reply.send({
      success: true,
      data: reports,
      pagination: { page, limit, total, totalPages: Math.ceil(total / limit) },
    });
  });

  /**
   * PATCH /api/admin/content/:momentId/moderate
   * Take moderation action on a moment
   */
  app.patch('/content/:momentId/moderate', async (request, reply) => {
    const { momentId } = request.params as { momentId: string };
    const { action, reason } = moderateSchema.parse(request.body);
    const actorId = (request.user as JwtPayload).userId;

    const moment = await prisma.moment.findUnique({ where: { id: momentId } });
    if (!moment) {
      return reply.code(404).send({ error: 'Not Found', message: 'Moment not found' });
    }

    switch (action) {
      case 'remove':
        await prisma.moment.update({
          where: { id: momentId },
          data: { deletedAt: new Date() },
        });
        break;
      case 'restore':
        await prisma.moment.update({
          where: { id: momentId },
          data: { deletedAt: null },
        });
        break;
      // 'flag' and 'unflag' would update a moderationStatus field if it existed
      default:
        break;
    }

    // Update associated reports
    await prisma.report.updateMany({
      where: { parameters: { path: ['momentId'], equals: momentId }, status: 'pending' },
      data: { status: action === 'remove' ? 'resolved' : 'dismissed' },
    });

    await prisma.auditEvent.create({
      data: {
        userId: actorId,
        eventType: 'MOMENT_DELETED',
        details: { action: `moderate_${action}`, momentId, reason },
        ipAddress: request.ip,
        userAgent: request.headers['user-agent'] || '',
      },
    });

    return reply.send({ success: true, data: { momentId, action, reason } });
  });

  // -----------------------------------------------------------------------
  // Platform Stats
  // -----------------------------------------------------------------------

  /**
   * GET /api/admin/stats
   * High-level platform statistics
   */
  app.get('/stats', async (_request, reply) => {
    const [
      totalUsers,
      activeUsers,
      totalMoments,
      totalSpheres,
      tierBreakdown,
    ] = await Promise.all([
      prisma.user.count({ where: { deletedAt: null } }),
      prisma.user.count({
        where: {
          deletedAt: null,
          lastLoginAt: { gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) },
        },
      }),
      prisma.moment.count({ where: { deletedAt: null } }),
      prisma.sphere.count({ where: { deletedAt: null } }),
      prisma.user.groupBy({
        by: ['subscriptionTier'],
        _count: true,
        where: { deletedAt: null },
      }),
    ]);

    return reply.send({
      success: true,
      data: {
        totalUsers,
        activeUsersLast30d: activeUsers,
        totalMoments,
        totalSpheres,
        tierBreakdown: tierBreakdown.map(t => ({
          tier: t.subscriptionTier,
          count: t._count,
        })),
        timestamp: new Date().toISOString(),
      },
    });
  });
};

export default adminRoutes;
