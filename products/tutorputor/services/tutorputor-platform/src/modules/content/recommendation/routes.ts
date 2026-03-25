/**
 * Recommendation Routes
 *
 * Fastify routes for related assets, prerequisites, and next-step
 * suggestions. Serves the learner-facing discovery rails and admin
 * bootstrap actions.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for recommendation and next-step APIs
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { RecommendationService } from "./recommendation-service.js";

// =============================================================================
// Types
// =============================================================================

type AssetIdParams = {
  assetId: string;
};

type LimitQuery = {
  limit?: string;
};

// =============================================================================
// Route Registration
// =============================================================================

export interface RecommendationRouteContext {
  prisma: PrismaClient;
}

export function registerRecommendationRoutes(
  fastify: FastifyInstance,
  context: RecommendationRouteContext,
): void {
  const service = new RecommendationService(context.prisma);

  const readGuard = roleGuard([
    "admin",
    "content_creator",
    "superadmin",
    "teacher",
    "student",
  ]);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/recommendations — All related assets grouped by type
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams; Querystring: LimitQuery }>(
    "/assets/:assetId/recommendations",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
      const limit = request.query.limit
        ? parseInt(request.query.limit, 10)
        : 10;

      const result = await service.getRelatedAssets(tenantId, assetId, limit);
      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/next-steps — Next-step suggestions
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams; Querystring: LimitQuery }>(
    "/assets/:assetId/next-steps",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
      const limit = request.query.limit ? parseInt(request.query.limit, 10) : 5;

      const suggestions = await service.getNextSteps(tenantId, assetId, limit);
      return reply.send({ data: suggestions });
    },
  );

  // ---------------------------------------------------------------------------
  // POST /assets/:assetId/bootstrap-edges — Bootstrap recommendation edges
  // ---------------------------------------------------------------------------
  fastify.post<{ Params: AssetIdParams }>(
    "/assets/:assetId/bootstrap-edges",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;

      const result = await service.bootstrapEdges(tenantId, assetId);
      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // POST /recommendations/recompute — Outcome-aware recommendation refresh
  // ---------------------------------------------------------------------------
  fastify.post<{
    Body: { sourceAssetId?: string; limit?: number };
  }>(
    "/recommendations/recompute",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await service.recomputeOutcomeAwareEdges(tenantId, {
        sourceAssetId: request.body?.sourceAssetId,
        limit: request.body?.limit,
      });

      return reply.send({ data: result });
    },
  );
}
