/**
 * Notification API Routes
 *
 * CRUD endpoints for in-app notifications.
 *
 * Endpoints:
 * - GET    /api/notifications             List notifications (paginated)
 * - GET    /api/notifications/unread-count Get count of unread notifications
 * - PATCH  /api/notifications/:id/read    Mark a single notification as read
 * - PATCH  /api/notifications/read-all    Mark all notifications as read
 * - DELETE /api/notifications/:id         Dismiss (soft-delete) a notification
 * - DELETE /api/notifications/dismissed   Bulk-dismiss all read notifications
 *
 * @doc.type routes
 * @doc.purpose In-app notification management endpoints
 * @doc.layer product
 * @doc.pattern RestAPI
 */

import { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { requireAuth, type JwtPayload } from '../lib/auth';

// ============================================================================
// Schemas
// ============================================================================

const listQuerySchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(20),
  unreadOnly: z
    .enum(['true', 'false'])
    .transform((v) => v === 'true')
    .default('false'),
  type: z.string().max(100).optional(),
});

const notificationIdSchema = z.object({
  id: z.string().uuid(),
});

// ============================================================================
// Routes
// ============================================================================

const notificationRoutes = async (app: FastifyInstance) => {
  // All routes require authentication
  app.addHook('preHandler', requireAuth);

  // -----------------------------------------------------------------------
  // List notifications (paginated)
  // -----------------------------------------------------------------------

  app.get('/', async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;
    const { page, limit, unreadOnly, type } = listQuerySchema.parse(request.query);
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {
      userId,
      dismissedAt: null,
    };

    if (unreadOnly) {
      where.read = false;
    }

    if (type) {
      where.type = type;
    }

    const [notifications, total] = await Promise.all([
      prisma.notification.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      prisma.notification.count({ where }),
    ]);

    return reply.send({
      success: true,
      data: notifications,
      pagination: {
        page,
        limit,
        total,
        pages: Math.ceil(total / limit),
      },
    });
  });

  // -----------------------------------------------------------------------
  // Unread count
  // -----------------------------------------------------------------------

  app.get('/unread-count', async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;

    const count = await prisma.notification.count({
      where: {
        userId,
        read: false,
        dismissedAt: null,
      },
    });

    return reply.send({ success: true, data: { unreadCount: count } });
  });

  // -----------------------------------------------------------------------
  // Mark single notification as read
  // -----------------------------------------------------------------------

  app.patch('/:id/read', async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;
    const { id } = notificationIdSchema.parse(request.params);

    const notification = await prisma.notification.findFirst({
      where: { id, userId },
    });

    if (!notification) {
      return reply.code(404).send({ error: 'Not Found', message: 'Notification not found' });
    }

    if (notification.read) {
      return reply.send({ success: true, data: notification });
    }

    const updated = await prisma.notification.update({
      where: { id },
      data: { read: true, readAt: new Date() },
    });

    return reply.send({ success: true, data: updated });
  });

  // -----------------------------------------------------------------------
  // Mark ALL unread notifications as read
  // -----------------------------------------------------------------------

  app.patch('/read-all', async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;

    const result = await prisma.notification.updateMany({
      where: { userId, read: false, dismissedAt: null },
      data: { read: true, readAt: new Date() },
    });

    return reply.send({ success: true, data: { updated: result.count } });
  });

  // -----------------------------------------------------------------------
  // Dismiss (soft-delete) a single notification
  // -----------------------------------------------------------------------

  app.delete('/:id', async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;
    const { id } = notificationIdSchema.parse(request.params);

    const notification = await prisma.notification.findFirst({
      where: { id, userId },
    });

    if (!notification) {
      return reply.code(404).send({ error: 'Not Found', message: 'Notification not found' });
    }

    await prisma.notification.update({
      where: { id },
      data: { dismissedAt: new Date() },
    });

    return reply.send({ success: true, message: 'Notification dismissed' });
  });

  // -----------------------------------------------------------------------
  // Bulk-dismiss all read notifications
  // -----------------------------------------------------------------------

  app.delete('/dismissed', async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;

    const result = await prisma.notification.updateMany({
      where: { userId, read: true, dismissedAt: null },
      data: { dismissedAt: new Date() },
    });

    return reply.send({ success: true, data: { dismissed: result.count } });
  });
};

export default notificationRoutes;
