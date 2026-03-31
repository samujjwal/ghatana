/**
 * Semantic Indexing & Hybrid Search Routes
 *
 * Fastify routes for semantic chunk extraction, reindex orchestration,
 * and hybrid search ranking. Heavy embedding generation is dispatched
 * to Java services; these routes own the control-plane trigger and status.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for semantic indexing and hybrid search
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { SemanticChunkService } from "./chunk-service.js";
import { SemanticSearchService } from "./semantic-search-service.js";

type ContentAssetType = string;

// =============================================================================
// Types
// =============================================================================

type AssetIdParams = {
  assetId: string;
};

type ReindexBody = {
  force?: boolean;
};

type PendingChunksQuery = {
  limit?: string;
};

type HybridSearchQuery = {
  q: string;
  assetTypes?: string;
  domain?: string;
  limit?: string;
  offset?: string;
  explain?: string;
};

// =============================================================================
// Route Registration
// =============================================================================

export interface SemanticRouteContext {
  prisma: PrismaClient;
}

/**
 * Register Semantic Indexing routes under the given Fastify instance.
 */
export function registerSemanticRoutes(
  fastify: FastifyInstance,
  context: SemanticRouteContext,
): void {
  const service = new SemanticChunkService(context.prisma);
  const searchService = new SemanticSearchService(context.prisma);

  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);
  const readGuard = roleGuard([
    "admin",
    "content_creator",
    "superadmin",
    "teacher",
    "student",
  ]);

  // ---------------------------------------------------------------------------
  // POST /assets/:assetId/reindex — Trigger semantic reindex for an asset
  // ---------------------------------------------------------------------------
  fastify.post<{ Params: AssetIdParams; Body: ReindexBody }>(
    "/assets/:assetId/reindex",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const { assetId } = request.params;
      const force = request.body?.force ?? false;

      const result = await service.indexAsset(assetId, { force });

      if (
        result.chunksCreated === 0 &&
        result.chunksUpdated === 0 &&
        result.chunksStale === 0
      ) {
        // Asset not found or no content to chunk
        return reply.code(404).send({ error: "Asset not found or empty" });
      }

      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /semantic/pending — Get chunks awaiting embedding (for Java dispatch)
  // ---------------------------------------------------------------------------
  fastify.get<{ Querystring: PendingChunksQuery }>(
    "/semantic/pending",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const limit = request.query.limit
        ? parseInt(request.query.limit, 10)
        : 100;

      const chunks = await service.getPendingChunks(tenantId, limit);
      return reply.send({ data: chunks, count: chunks.length });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /search — Hybrid search across canonical assets
  // ---------------------------------------------------------------------------
  fastify.get<{ Querystring: HybridSearchQuery }>(
    "/search",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { q, assetTypes, domain, limit, offset, explain } = request.query;

      if (!q || q.trim().length === 0) {
        return reply
          .code(400)
          .send({ error: "Query parameter 'q' is required" });
      }

      const result = await searchService.search({
        tenantId,
        query: q,
        ...(assetTypes
          ? { assetTypes: assetTypes.split(",") as ContentAssetType[] }
          : {}),
        ...(domain ? { domain } : {}),
        ...(limit ? { limit: parseInt(limit, 10) } : {}),
        ...(offset ? { offset: parseInt(offset, 10) } : {}),
        explain: explain === "true",
      });

      return reply.send(result);
    },
  );
}
