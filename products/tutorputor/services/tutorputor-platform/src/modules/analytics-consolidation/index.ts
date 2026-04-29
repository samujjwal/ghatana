/**
 * Analytics Consolidation Module
 *
 * Merge /analytics, /teacher, and dashboard insights into persona-appropriate single surfaces.
 *
 * @doc.type module
 * @doc.purpose Analytics consolidation module for persona-specific insights
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { AnalyticsConsolidationService } from "./AnalyticsConsolidationService.js";
import { z } from "zod";

const getPersonaAnalyticsSchema = z.object({
  persona: z.enum(["student", "teacher", "admin", "parent"]),
});

export const analyticsConsolidationModule: FastifyPluginAsync = async (app) => {
  const analyticsService = new AnalyticsConsolidationService(app.prisma as any);
  app.decorate("analyticsConsolidationService", analyticsService);

  // GET /api/v1/analytics/persona - Get consolidated analytics for the authenticated user's persona
  app.get("/api/v1/analytics/persona", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const userId = (request as any).userId;
    const user = (request as any).user;

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const query = request.query as { persona?: string };
    const persona = (query.persona || user.role) as "student" | "teacher" | "admin" | "parent";

    const analytics = await analyticsService.getPersonaAnalytics(tenantId, userId, persona);

    return reply.send(analytics);
  });

  // GET /api/v1/analytics/persona/:userId - Get consolidated analytics for a specific user (admin/parent only)
  app.get("/api/v1/analytics/persona/:userId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    // Only admin or parent can view other users' analytics
    if (user.role !== "admin" && user.role !== "parent") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { userId } = request.params as { userId: string };
    const query = request.query as { persona?: string };
    const persona = (query.persona || "student") as "student" | "teacher" | "admin" | "parent";

    const analytics = await analyticsService.getPersonaAnalytics(tenantId, userId, persona);

    return reply.send(analytics);
  });

  // GET /api/v1/analytics/tenant - Get tenant-wide analytics (admin only)
  app.get("/api/v1/analytics/tenant", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const analytics = await analyticsService.getPersonaAnalytics(tenantId, user.id, "admin");

    return reply.send(analytics);
  });

  app.log.info("✅ Analytics consolidation module registered");
};
