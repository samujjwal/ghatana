/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for VR lab creation, sessions, and analytics
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  requireRole,
  requireSelfOrRole,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import {
  VRLabServiceImpl,
  VRSessionServiceImpl,
  VRAnalyticsServiceImpl,
} from "./index.js";
import { FeatureFlagService } from "../feature-flags/FeatureFlagService.js";

function asTenantId(value: string): TenantId {
  return value as TenantId;
}

function asUserId(value: string): UserId {
  return value as UserId;
}

type StringQuery = Record<string, string | undefined>;

function asStringQuery(query: unknown): StringQuery {
  return query && typeof query === "object"
    ? (query as StringQuery)
    : {};
}

/**
 * VR module routes. Registered at prefix /api/v1/vr.
 */

export const vrRoutes = async (app: FastifyInstance) => {
  const prisma = app.prisma;
  const labService = new VRLabServiceImpl(prisma);
  const sessionService = new VRSessionServiceImpl(prisma);
  const analyticsService = new VRAnalyticsServiceImpl(prisma);
  const featureFlags = new FeatureFlagService();

  // Guard all VR routes via a preHandler hook at the plugin level.
  app.addHook("preHandler", async (req, reply) => {
    const userId = getUserId(req);
    if (!featureFlags.isEnabled("vr_webxr", userId)) {
      void reply.status(404).send({ error: "VR features are not available." });
    }
  });

  // ===========================================================================
  // VR Labs
  // ===========================================================================

  /**
   * POST /labs
   * Create a new VR lab. Admin or content_creator only.
   */
  app.post("/labs", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));
    requireRole(req, ["admin", "content_creator", "superadmin"]);
    const body = req.body as Record<string, unknown>;

    await respondWithErrors(reply, async () => {
      const lab = await labService.createLab({ tenantId, userId, data: body });
      reply.code(201);
      return lab;
    });
  });

  /**
   * GET /labs
   * List VR labs for the current tenant.
   */
  app.get("/labs", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const query = asStringQuery(req.query);

    await respondWithErrors(reply, () =>
      labService.listLabs({
        tenantId,
        params: {
          category: query.category,
          difficulty: query.difficulty,
          isPublished:
            query.isPublished !== undefined
              ? query.isPublished === "true"
              : undefined,
          search: query.search,
          page: query.page ? Number(query.page) : 1,
          limit: query.limit ? Number(query.limit) : 20,
        },
      }),
    );
  });

  /**
   * GET /labs/:labId
   * Get a single VR lab by ID.
   */
  app.get("/labs/:labId", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const { labId } = req.params as { labId: string };

    const lab = await labService.getLabById({ tenantId, labId });
    if (!lab) return reply.code(404).send({ error: "VR lab not found" });
    return reply.send(lab);
  });

  /**
   * PUT /labs/:labId
   * Update VR lab metadata. Admin or content_creator only.
   */
  app.put("/labs/:labId", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));
    requireRole(req, ["admin", "content_creator", "superadmin"]);
    const { labId } = req.params as { labId: string };

    await respondWithErrors(reply, () =>
      labService.updateLab({ tenantId, userId, labId, data: req.body as Record<string, unknown> }),
    );
  });

  /**
   * DELETE /labs/:labId
   * Delete a VR lab. Admin only.
   */
  app.delete("/labs/:labId", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));
    requireRole(req, ["admin", "superadmin"]);
    const { labId } = req.params as { labId: string };

    await respondWithErrors(reply, async () => {
      await labService.deleteLab({ tenantId, userId, labId });
      return { success: true };
    });
  });

  /**
   * POST /labs/:labId/publish
   * Publish a VR lab. Admin or content_creator only.
   */
  app.post("/labs/:labId/publish", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));
    requireRole(req, ["admin", "content_creator", "superadmin"]);
    const { labId } = req.params as { labId: string };

    await respondWithErrors(reply, () =>
      labService.publishLab({ tenantId, userId, labId }),
    );
  });

  // ===========================================================================
  // VR Scenes (nested under labs)
  // ===========================================================================

  /**
   * POST /labs/:labId/scenes
   * Add a scene to a lab. Admin or content_creator only.
   */
  app.post("/labs/:labId/scenes", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));
    requireRole(req, ["admin", "content_creator", "superadmin"]);
    const { labId } = req.params as { labId: string };

    await respondWithErrors(reply, async () => {
      const scene = await labService.addScene({
        tenantId,
        userId,
        data: { ...(req.body as Record<string, unknown>), labId },
      });
      reply.code(201);
      return scene;
    });
  });

  /**
   * PUT /labs/:labId/scenes/:sceneId
   * Update a scene. Admin or content_creator only.
   */
  app.put(
    "/labs/:labId/scenes/:sceneId",
    async (req: FastifyRequest, reply: FastifyReply) => {
      const tenantId = asTenantId(getTenantId(req));
      const userId = asUserId(getUserId(req));
      requireRole(req, ["admin", "content_creator", "superadmin"]);
      const { sceneId } = req.params as { sceneId: string };

      await respondWithErrors(reply, () =>
        labService.updateScene({
          tenantId,
          userId,
          sceneId,
          data: req.body as Parameters<typeof labService.updateScene>[0]["data"],
        }),
      );
    },
  );

  /**
   * DELETE /labs/:labId/scenes/:sceneId
   * Remove a scene. Admin only.
   */
  app.delete(
    "/labs/:labId/scenes/:sceneId",
    async (req: FastifyRequest, reply: FastifyReply) => {
      const tenantId = asTenantId(getTenantId(req));
      const userId = asUserId(getUserId(req));
      requireRole(req, ["admin", "superadmin"]);
      const { sceneId } = req.params as { sceneId: string };

      await respondWithErrors(reply, async () => {
        await labService.deleteScene({ tenantId, userId, sceneId });
        return { success: true };
      });
    },
  );

  // ===========================================================================
  // VR Analytics
  // ===========================================================================

  /**
   * GET /labs/:labId/analytics
   * Get analytics for a VR lab. Admin or content_creator only.
   * Query: ?period=day|week|month|all (default: all)
   */
  app.get("/labs/:labId/analytics", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    requireRole(req, ["admin", "content_creator", "superadmin"]);
    const { labId } = req.params as { labId: string };
    const { period = "all" } = (req.query ?? {}) as {
      period?: "day" | "week" | "month" | "all";
    };

    await respondWithErrors(reply, () =>
      analyticsService.getLabAnalytics({ tenantId, labId, period }),
    );
  });

  // ===========================================================================
  // VR Sessions
  // ===========================================================================

  /**
   * POST /sessions
   * Start a new VR session for the current user.
   */
  app.post("/sessions", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));

    await respondWithErrors(reply, async () => {
      const session = await sessionService.startSession({
        tenantId,
        userId,
        data: req.body as Parameters<typeof sessionService.startSession>[0]["data"],
      });
      reply.code(201);
      return session;
    });
  });

  /**
   * GET /sessions
   * List sessions for the current user.
   * Query: ?labId=&status=&page=&limit=
   */
  app.get("/sessions", async (req: FastifyRequest, reply: FastifyReply) => {
    const tenantId = asTenantId(getTenantId(req));
    const userId = asUserId(getUserId(req));
    const query = asStringQuery(req.query);

    await respondWithErrors(reply, () =>
      sessionService.listUserSessions({
        tenantId,
        userId,
        labId: query.labId,
        status: query.status,
        pagination: {
          page: query.page ? Number(query.page) : 1,
          limit: query.limit ? Number(query.limit) : 20,
        },
      }),
    );
  });

  /**
   * GET /sessions/:sessionId
   * Get a single VR session. Access restricted to session owner or admin/superadmin.
   */
  app.get(
    "/sessions/:sessionId",
    async (req: FastifyRequest, reply: FastifyReply) => {
      const tenantId = asTenantId(getTenantId(req));
      const userId = asUserId(getUserId(req));
      const { sessionId } = req.params as { sessionId: string };

      const session = await sessionService.getSession({ tenantId, sessionId });
      if (!session)
        return reply.code(404).send({ error: "VR session not found" });

      try {
        requireSelfOrRole(req, session.userId, ["admin", "superadmin"]);
      } catch {
        return reply.code(403).send({
          error: "Forbidden",
          message: "You are not allowed to access this VR session",
        });
      }

      void userId; // userId verified via requireSelfOrRole above
      return reply.send(session);
    },
  );

  /**
   * PATCH /sessions/:sessionId
   * Update session progress (scene change, interaction log, performance metrics).
   */
  app.patch(
    "/sessions/:sessionId",
    async (req: FastifyRequest, reply: FastifyReply) => {
      const tenantId = asTenantId(getTenantId(req));
      const userId = asUserId(getUserId(req));
      const { sessionId } = req.params as { sessionId: string };

      await respondWithErrors(reply, () =>
        sessionService.updateSession({
          tenantId,
          userId,
          sessionId,
          data: req.body as Parameters<typeof sessionService.updateSession>[0]["data"],
        }),
      );
    },
  );

  /**
   * POST /sessions/:sessionId/end
   * End an active VR session.
   */
  app.post(
    "/sessions/:sessionId/end",
    async (req: FastifyRequest, reply: FastifyReply) => {
      const tenantId = asTenantId(getTenantId(req));
      const userId = asUserId(getUserId(req));
      const { sessionId } = req.params as { sessionId: string };

      await respondWithErrors(reply, () =>
        sessionService.endSession({ tenantId, userId, sessionId }),
      );
    },
  );
};
