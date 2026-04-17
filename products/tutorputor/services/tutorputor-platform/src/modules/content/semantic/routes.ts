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
import { z } from "zod";
import type { PrismaClient } from "@tutorputor/core/db";
import { SemanticChunkService } from "./chunk-service.js";
import { SemanticSearchService } from "./semantic-search-service.js";

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

const assetIdParamsSchema = z.object({
  assetId: z.string().trim().min(1),
});

const reindexBodySchema = z.object({
  force: z.boolean().optional(),
});

const pendingChunksQuerySchema = z.object({
  limit: z.coerce.number().int().positive().max(500).optional(),
});

const hybridSearchQuerySchema = z.object({
  q: z.string().trim().min(1),
  assetTypes: z.string().trim().min(1).optional(),
  domain: z.string().trim().min(1).optional(),
  limit: z.coerce.number().int().positive().max(200).optional(),
  offset: z.coerce.number().int().min(0).optional(),
  explain: z.enum(["true", "false"]).optional(),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

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
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = reindexBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const { assetId } = paramsResult.data;
      const force = bodyResult.data.force ?? false;

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
      const queryResult = pendingChunksQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(queryResult.error.issues));
      }

      const limit = queryResult.data.limit ?? 100;

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
      const queryResult = hybridSearchQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(queryResult.error.issues));
      }

      const { q, assetTypes, domain, limit, offset, explain } = queryResult.data;

      const result = await searchService.search({
        tenantId,
        query: q,
        ...(assetTypes
          ? {
              assetTypes: assetTypes.split(",") as Array<
                import("../types.js").ContentAssetType
              >,
            }
          : {}),
        ...(domain ? { domain } : {}),
        ...(limit !== undefined ? { limit } : {}),
        ...(offset !== undefined ? { offset } : {}),
        explain: explain === "true",
      });

      return reply.send(result);
    },
  );
}
