import type { FastifyInstance } from "fastify";
import type { ContentService } from "@tutorputor/contracts/v1/services";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import { getTenantId, getUserId } from "../../core/http/requestContext.js";

interface ContentRouteConfigs {
  contentService: ContentService;
}

export async function registerContentRoutes(
  app: FastifyInstance,
  deps: ContentRouteConfigs,
) {
  app.get("/v1/modules", async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const userId =
      (req as any).user?.sub || (req as any).user?.userId
        ? (getUserId(req) as UserId)
        : undefined;
    const { domain, cursor, query } = (req.query ?? {}) as {
      domain?: string;
      cursor?: string;
      query?: string;
    };

    const result = await deps.contentService.listModules({
      tenantId,
      domain,
      status: "PUBLISHED",
      cursor,
      limit: 20,
      userId,
      query,
    });

    return reply.send(result);
  });

  app.get("/v1/modules/:slug", async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const userId =
      (req as any).user?.sub || (req as any).user?.userId
        ? (getUserId(req) as UserId)
        : undefined;
    const { slug } = req.params as { slug: string };

    const { module, enrollment } = await deps.contentService.getModuleBySlug(
      tenantId,
      slug,
      userId,
    );

    return reply.send({
      module,
      userEnrollment: enrollment ?? null,
    });
  });
}
