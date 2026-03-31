/**
 * Content Asset Read Routes
 *
 * Fastify routes for canonical content asset reads. Serves learner
 * discovery, detailed asset views, related asset lookups, and revision
 * history from the unified ContentAsset model.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for Content Asset reads
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
type ContentAssetType = string;
type ContentAssetStatus = string;
import { ContentAssetReadService } from "./read-service.js";
import type { PrismaClient } from "@tutorputor/core/db";

// =============================================================================
// Types
// =============================================================================

type AssetListQuery = {
  assetType?: ContentAssetType;
  status?: ContentAssetStatus;
  domain?: string;
  authorId?: string;
  search?: string;
  limit?: string;
  offset?: string;
};

type AssetIdParams = {
  assetId: string;
};

type RelatedQuery = {
  limit?: string;
};

// =============================================================================
// Route Registration
// =============================================================================

export interface AssetRouteContext {
  prisma: PrismaClient;
}

/**
 * Register Content Asset read routes under the given Fastify instance.
 */
export function registerContentAssetRoutes(
  fastify: FastifyInstance,
  context: AssetRouteContext,
): void {
  const service = new ContentAssetReadService(context.prisma);

  const readGuard = roleGuard([
    "admin",
    "content_creator",
    "superadmin",
    "teacher",
    "student",
  ]);

  // ---------------------------------------------------------------------------
  // GET /assets — List assets with filtering
  // ---------------------------------------------------------------------------
  fastify.get<{ Querystring: AssetListQuery }>(
    "/assets",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetType, status, domain, authorId, search, limit, offset } =
        request.query;

      const result = await service.listAssets({
        tenantId,
        ...(assetType ? { assetType } : {}),
        ...(status ? { status } : {}),
        ...(domain ? { domain } : {}),
        ...(authorId ? { authorId } : {}),
        ...(search ? { search } : {}),
        ...(limit ? { limit: parseInt(limit, 10) } : {}),
        ...(offset ? { offset: parseInt(offset, 10) } : {}),
      });

      return reply.send({
        data: result.assets,
        total: result.total,
      });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId — Asset detail with blocks & manifests
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams }>(
    "/assets/:assetId",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;

      const detail = await service.getAssetDetail(tenantId, assetId);
      if (!detail) {
        return reply.code(404).send({ error: "Asset not found" });
      }

      return reply.send({ data: detail });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/related — Related assets
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams; Querystring: RelatedQuery }>(
    "/assets/:assetId/related",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
      const limit = request.query.limit
        ? parseInt(request.query.limit, 10)
        : 10;

      const related = await service.getRelatedAssets(tenantId, assetId, limit);
      return reply.send({ data: related });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/revisions — Revision history
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams }>(
    "/assets/:assetId/revisions",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;

      const revisions = await service.getRevisionHistory(tenantId, assetId);
      return reply.send({ data: revisions });
    },
  );
}
