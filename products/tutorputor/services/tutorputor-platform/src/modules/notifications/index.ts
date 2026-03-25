/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for user notifications and notification preferences
 * @doc.layer product
 * @doc.pattern REST API
 */

import {
  getTenantId,
  getUserId,
  respondWithErrors,
} from "../../core/http/requestContext.js";

type RouteRequest = any;
type RouteReply = any;

/**
 * Notification routes. Registered at prefix /api/v1/notifications.
 */
export const notificationRoutes = async (app: any) => {
  const prisma = app.prisma as any;

  // ===========================================================================
  // Notification Listing & State
  // ===========================================================================

  /**
   * GET /
   * List notifications for the current user.
   * Query: ?unreadOnly=true&page=1&limit=20
   */
  app.get("/", async (req: RouteRequest, reply: RouteReply) => {
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
  app.patch("/:id/read", async (req: RouteRequest, reply: RouteReply) => {
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
  app.patch("/read-all", async (req: RouteRequest, reply: RouteReply) => {
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
  app.delete("/:id", async (req: RouteRequest, reply: RouteReply) => {
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
  app.get("/preferences", async (req: RouteRequest, reply: RouteReply) => {
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
  app.patch("/preferences", async (req: RouteRequest, reply: RouteReply) => {
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

  // ===========================================================================
  // Device Token Management (first-class push notification device registry)
  // ===========================================================================

  /**
   * POST /device-tokens
   * Register or refresh a device token for push notifications.
   * Body: { token, platform, endpoint?, p256dhKey?, authKey? }
   */
  app.post("/device-tokens", async (req: RouteRequest, reply: RouteReply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const body = (req.body ?? {}) as {
      token: string;
      platform: string;
      endpoint?: string;
      p256dhKey?: string;
      authKey?: string;
    };

    if (!body.token || !body.platform) {
      return reply.code(400).send({ error: "token and platform are required" });
    }
    const allowedPlatforms = ["ios", "android", "web"];
    if (!allowedPlatforms.includes(body.platform)) {
      return reply
        .code(400)
        .send({ error: "platform must be one of: ios, android, web" });
    }

    await respondWithErrors(reply, () =>
      prisma.deviceToken.upsert({
        where: { token: body.token },
        create: {
          tenantId,
          userId,
          token: body.token,
          platform: body.platform,
          endpoint: body.endpoint ?? null,
          p256dhKey: body.p256dhKey ?? null,
          authKey: body.authKey ?? null,
          isActive: true,
          lastSeen: new Date(),
        },
        update: {
          tenantId,
          userId,
          isActive: true,
          lastSeen: new Date(),
          ...(body.endpoint !== undefined && { endpoint: body.endpoint }),
          ...(body.p256dhKey !== undefined && { p256dhKey: body.p256dhKey }),
          ...(body.authKey !== undefined && { authKey: body.authKey }),
        },
      }),
    );
  });

  /**
   * GET /device-tokens
   * List all active device tokens for the current user.
   */
  app.get("/device-tokens", async (req: RouteRequest, reply: RouteReply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);

    await respondWithErrors(reply, () =>
      prisma.deviceToken.findMany({
        where: { tenantId, userId, isActive: true },
        select: {
          id: true,
          platform: true,
          lastSeen: true,
          createdAt: true,
          // Exclude raw token and push keys from the list response for security
        },
        orderBy: { lastSeen: "desc" },
      }),
    );
  });

  /**
   * DELETE /device-tokens/:tokenId
   * Deregister a device token (soft-delete via isActive=false).
   */
  app.delete(
    "/device-tokens/:tokenId",
    async (req: RouteRequest, reply: RouteReply) => {
      const tenantId = getTenantId(req);
      const userId = getUserId(req);
      const { tokenId } = req.params as { tokenId: string };

      await respondWithErrors(reply, async () => {
        const record = await prisma.deviceToken.findFirst({
          where: { id: tokenId, tenantId, userId },
        });
        if (!record) {
          reply.code(404);
          throw new Error("Device token not found");
        }
        await prisma.deviceToken.update({
          where: { id: tokenId },
          data: { isActive: false },
        });
        return { deregistered: true };
      });
    },
  );
};
