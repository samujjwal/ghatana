/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for user notifications and notification preferences
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from "fastify";
import {
  getTenantId,
  getUserId,
  respondWithErrors,
} from "../../core/http/requestContext.js";

/**
 * Notification routes. Registered at prefix /api/v1/notifications.
 */
export const notificationRoutes: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as any;

  // ===========================================================================
  // Notification Listing & State
  // ===========================================================================

  /**
   * GET /
   * List notifications for the current user.
   * Query: ?unreadOnly=true&page=1&limit=20
   */
  app.get("/", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const query = (req.query ?? {}) as any;
    const unreadOnly = query.unreadOnly === "true";
    const page = query.page ? Number(query.page) : 1;
    const limit = Math.min(query.limit ? Number(query.limit) : 20, 100);

    await respondWithErrors(reply, async () => {
      const where: any = { tenantId, userId };
      if (unreadOnly) where.isRead = false;

      const [notifications, total] = await Promise.all([
        prisma.socialNotification.findMany({
          where,
          orderBy: { createdAt: "desc" },
          skip: (page - 1) * limit,
          take: limit,
        }),
        prisma.socialNotification.count({ where }),
      ]);

      return {
        notifications,
        pagination: {
          page,
          limit,
          total,
          totalPages: Math.ceil(total / limit),
        },
      };
    });
  });

  /**
   * PATCH /:id/read
   * Mark a single notification as read.
   */
  app.patch("/:id/read", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const { id } = req.params as { id: string };

    await respondWithErrors(reply, async () => {
      const notification = await prisma.socialNotification.findFirst({
        where: { id, tenantId, userId },
      });
      if (!notification) {
        reply.code(404);
        throw new Error("Notification not found");
      }

      return prisma.socialNotification.update({
        where: { id },
        data: { isRead: true, readAt: new Date() },
      });
    });
  });

  /**
   * PATCH /read-all
   * Mark all notifications for the current user as read.
   */
  app.patch("/read-all", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);

    await respondWithErrors(reply, async () => {
      const result = await prisma.socialNotification.updateMany({
        where: { tenantId, userId, isRead: false },
        data: { isRead: true, readAt: new Date() },
      });
      return { updated: result.count };
    });
  });

  /**
   * DELETE /:id
   * Delete a single notification.
   */
  app.delete("/:id", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const { id } = req.params as { id: string };

    await respondWithErrors(reply, async () => {
      const notification = await prisma.socialNotification.findFirst({
        where: { id, tenantId, userId },
      });
      if (!notification) {
        reply.code(404);
        throw new Error("Notification not found");
      }

      await prisma.socialNotification.delete({ where: { id } });
      return { success: true };
    });
  });

  // ===========================================================================
  // Notification Preferences
  // ===========================================================================

  /**
   * GET /preferences
   * Get notification preferences for the current user.
   */
  app.get("/preferences", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);

    await respondWithErrors(reply, async () => {
      const prefs = await prisma.notificationPreference.findUnique({
        where: { tenantId_userId: { tenantId, userId } },
      });

      // Return defaults if no record exists yet
      return (
        prefs ?? {
          tenantId,
          userId,
          emailEnabled: true,
          pushEnabled: true,
          preferences: null,
        }
      );
    });
  });

  /**
   * PATCH /preferences
   * Upsert notification preferences for the current user.
   */
  app.patch("/preferences", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const body = (req.body ?? {}) as any;

    await respondWithErrors(reply, () =>
      prisma.notificationPreference.upsert({
        where: { tenantId_userId: { tenantId, userId } },
        create: {
          tenantId,
          userId,
          emailEnabled: body.emailEnabled ?? true,
          pushEnabled: body.pushEnabled ?? true,
          preferences: body.preferences
            ? JSON.stringify(body.preferences)
            : null,
        },
        update: {
          ...(body.emailEnabled !== undefined && {
            emailEnabled: body.emailEnabled,
          }),
          ...(body.pushEnabled !== undefined && {
            pushEnabled: body.pushEnabled,
          }),
          ...(body.preferences !== undefined && {
            preferences: JSON.stringify(body.preferences),
          }),
        },
      }),
    );
  });
};
