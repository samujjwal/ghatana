/**
 * Content Quality Monitoring Module
 *
 * Post-publish monitoring that flags AI-generated content that degrades against quality baseline.
 *
 * @doc.type module
 * @doc.purpose Content quality regression detection module
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync, FastifyRequest } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { ContentQualityMonitoringService } from "./ContentQualityMonitoringService.js";
import { z } from "zod";

type RouteUser = {
  role?: string;
};

type AuthenticatedRouteRequest = FastifyRequest & {
  tenantId?: string;
  user?: RouteUser;
};

const establishBaselineSchema = z.object({
  contentId: z.string(),
  contentType: z.string(),
  metrics: z.object({
    clarity: z.number(),
    accuracy: z.number(),
    completeness: z.number(),
    engagement: z.number(),
  }),
});

export const contentQualityMonitoringModule: FastifyPluginAsync = async (app) => {
  const monitoringService = new ContentQualityMonitoringService(
    app.prisma as unknown as PrismaClient,
  );
  app.decorate("contentQualityMonitoringService", monitoringService);

  // POST /api/v1/content-quality/baselines - Establish quality baseline
  app.post("/api/v1/content-quality/baselines", async (request, reply) => {
    const { tenantId, user } = request as AuthenticatedRouteRequest;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = establishBaselineSchema.parse(request.body);
    const baseline = await monitoringService.establishBaseline(
      body.contentId,
      body.contentType,
      body.metrics,
    );

    return reply.send({ success: true, baseline });
  });

  // POST /api/v1/content-quality/monitor/:contentId - Monitor content quality
  app.post("/api/v1/content-quality/monitor/:contentId", async (request, reply) => {
    const { tenantId, user } = request as AuthenticatedRouteRequest;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { contentId } = request.params as { contentId: string };
    const alerts = await monitoringService.monitorContentQuality(contentId);

    return reply.send({ alerts });
  });

  // GET /api/v1/content-quality/alerts - Get active quality alerts
  app.get("/api/v1/content-quality/alerts", async (request, reply) => {
    const { tenantId, user } = request as AuthenticatedRouteRequest;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const query = request.query as {
      contentType?: string;
      severity?: "low" | "medium" | "high" | "critical";
      limit?: string;
    };

    const filters: {
      contentType?: string;
      severity?: "low" | "medium" | "high" | "critical";
      limit?: number;
    } = {};
    if (query.contentType !== undefined) {
      filters.contentType = query.contentType;
    }
    if (query.severity !== undefined) {
      filters.severity = query.severity;
    }
    if (query.limit !== undefined) {
      filters.limit = parseInt(query.limit, 10);
    }

    const alerts = await monitoringService.getActiveAlerts(filters);

    return reply.send({ alerts });
  });

  // POST /api/v1/content-quality/alerts/:alertId/resolve - Resolve a quality alert
  app.post("/api/v1/content-quality/alerts/:alertId/resolve", async (request, reply) => {
    const { tenantId, user } = request as AuthenticatedRouteRequest;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { alertId } = request.params as { alertId: string };
    await monitoringService.resolveAlert(alertId);

    return reply.send({ success: true });
  });

  // POST /api/v1/content-quality/batch-monitor - Run batch monitoring
  app.post("/api/v1/content-quality/batch-monitor", async (request, reply) => {
    const { tenantId, user } = request as AuthenticatedRouteRequest;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const result = await monitoringService.runBatchMonitoring();

    return reply.send(result);
  });

  app.log.info("✅ Content quality monitoring module registered");
};
