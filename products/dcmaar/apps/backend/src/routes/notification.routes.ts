/**
 * Notification Routes
 *
 * Provides endpoints for managing parent notifications.
 *
 * @doc.type routes
 * @doc.purpose Notification API endpoints
 * @doc.layer backend
 * @doc.pattern REST Routes
 */

import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import * as notificationService from '../services/notification.service';
import { logger } from '../utils/logger';

/**
 * Query schema for notifications
 */
const notificationQuerySchema = z.object({
    child_id: z.string().uuid().optional(),
    type: z.enum([
        'block_event',
        'risk_alert',
        'child_request',
        'request_decision',
        'usage_alert',
        'device_offline',
        'policy_violation',
        'system',
    ]).optional(),
    priority: z.enum(['low', 'medium', 'high', 'critical']).optional(),
    is_read: z.coerce.boolean().optional(),
    limit: z.coerce.number().int().min(1).max(100).default(50),
    offset: z.coerce.number().int().min(0).default(0),
});

/**
 * Register notification routes
 */
export const notificationRoutes: FastifyPluginAsync = async (fastify) => {
    // All routes require authentication
    fastify.addHook('preHandler', authenticate);

    /**
     * GET /
     * Get notifications for the authenticated user
     */
    fastify.get('/', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const queryResult = notificationQuerySchema.safeParse(request.query);
            const filters = queryResult.success ? queryResult.data : { limit: 50, offset: 0 };

            const notifications = await notificationService.getNotifications(
                request.userId!,
                filters
            );

            const unreadCount = await notificationService.getUnreadCount(
                request.userId!,
                filters.child_id as string | undefined
            );

            return reply.send({
                success: true,
                data: notifications,
                count: notifications.length,
                unread: unreadCount,
            });
        } catch (error) {
            logger.error('Failed to get notifications', {
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to get notifications', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * GET /unread-count
     * Get unread notification count
     */
    fastify.get('/unread-count', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { child_id } = request.query as { child_id?: string };

            const count = await notificationService.getUnreadCount(
                request.userId!,
                child_id
            );

            return reply.send({
                success: true,
                data: count,
            });
        } catch (error) {
            logger.error('Failed to get unread count', {
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to get unread count', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * POST /:id/read
     * Mark a notification as read
     */
    fastify.post('/:id/read', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { id } = request.params as { id: string };

            const success = await notificationService.markAsRead(id, request.userId!);

            if (!success) {
                return reply.status(404).send({
                    success: false,
                    error: { message: 'Notification not found', code: 'NOT_FOUND' },
                });
            }

            return reply.send({
                success: true,
                data: { id, is_read: true },
            });
        } catch (error) {
            logger.error('Failed to mark notification as read', {
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to mark as read', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * POST /read-all
     * Mark all notifications as read
     */
    fastify.post('/read-all', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { child_id } = request.body as { child_id?: string } || {};

            const count = await notificationService.markAllAsRead(
                request.userId!,
                child_id
            );

            return reply.send({
                success: true,
                data: { marked_read: count },
            });
        } catch (error) {
            logger.error('Failed to mark all as read', {
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to mark all as read', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * GET /digest
     * Get notification digest summary
     */
    fastify.get('/digest', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { hours } = request.query as { hours?: string };
            const hoursBack = parseInt(hours || '24', 10);

            const since = new Date();
            since.setHours(since.getHours() - hoursBack);

            const digest = await notificationService.generateDigest(
                request.userId!,
                since
            );

            return reply.send({
                success: true,
                data: {
                    ...digest,
                    period: {
                        hours: hoursBack,
                        since: since.toISOString(),
                    },
                },
            });
        } catch (error) {
            logger.error('Failed to generate digest', {
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to generate digest', code: 'INTERNAL_ERROR' },
            });
        }
    });
};
