import type { FastifyInstance } from "fastify";
import type { ContentService } from "@tutorputor/contracts/v1/services";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import { getTenantId, getUserId } from "../../core/http/requestContext.js";
import { z } from "zod";

interface ContentRouteConfigs {
  contentService: ContentService;
}

const moduleListQuerySchema = z.object({
  domain: z.string().trim().min(1).optional(),
  cursor: z.string().trim().min(1).optional(),
  query: z.string().trim().min(1).optional(),
});

const moduleSlugParamsSchema = z.object({
  slug: z.string().trim().min(1),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

function maybeGetUserId(req: {
  user?: { sub?: string; userId?: string };
}): UserId | undefined {
  return req.user?.sub || req.user?.userId ? (getUserId(req as never) as UserId) : undefined;
}

export async function registerContentRoutes(
  app: FastifyInstance,
  deps: ContentRouteConfigs,
) {
  app.get("/v1/modules", async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const queryResult = moduleListQuerySchema.safeParse(req.query ?? {});
    if (!queryResult.success) {
      return reply.code(400).send(validationErrorResponse(queryResult.error.issues));
    }

    const userId = maybeGetUserId(req as never);
    const { domain, cursor, query } = queryResult.data;

    const result = await deps.contentService.listModules({
      tenantId,
      ...(domain ? { domain } : {}),
      status: "PUBLISHED",
      ...(cursor ? { cursor } : {}),
      limit: 20,
      ...(userId ? { userId } : {}),
      ...(query ? { query } : {}),
    });

    return reply.send(result);
  });

  app.get("/v1/modules/:slug", async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const paramsResult = moduleSlugParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply.code(400).send(validationErrorResponse(paramsResult.error.issues));
    }

    const userId = maybeGetUserId(req as never);
    const { slug } = paramsResult.data;

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
