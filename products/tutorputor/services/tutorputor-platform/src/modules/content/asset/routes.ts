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
import { z } from "zod";
import type {
  ContentAssetStatus,
  ContentAssetType,
} from "../types.js";
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

const assetListQuerySchema = z.object({
  assetType: z
    .enum([
      "explainer",
      "module",
      "example_set",
      "simulation",
      "animation",
      "assessment",
      "pathway",
      "reference_pack",
    ])
    .optional(),
  status: z
    .enum([
      "draft",
      "validating",
      "review",
      "approved",
      "published",
      "archived",
    ])
    .optional(),
  domain: z.string().trim().min(1).optional(),
  authorId: z.string().trim().min(1).optional(),
  search: z.string().trim().min(1).optional(),
  limit: z.coerce.number().int().positive().max(200).optional(),
  offset: z.coerce.number().int().min(0).optional(),
});

const assetIdParamsSchema = z.object({
  assetId: z.string().trim().min(1),
});

const relatedQuerySchema = z.object({
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

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
      const queryResult = assetListQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(queryResult.error.issues));
      }

      const { assetType, status, domain, authorId, search, limit, offset } =
        queryResult.data;

      const result = await service.listAssets({
        tenantId,
        ...(assetType ? { assetType: assetType as ContentAssetType } : {}),
        ...(status ? { status: status as ContentAssetStatus } : {}),
        ...(domain ? { domain } : {}),
        ...(authorId ? { authorId } : {}),
        ...(search ? { search } : {}),
        ...(limit !== undefined ? { limit } : {}),
        ...(offset !== undefined ? { offset } : {}),
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
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { assetId } = paramsResult.data;

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
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const queryResult = relatedQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(queryResult.error.issues));
      }

      const { assetId } = paramsResult.data;
      const limit = queryResult.data.limit ?? 10;

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
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { assetId } = paramsResult.data;

      const revisions = await service.getRevisionHistory(tenantId, assetId);
      return reply.send({ data: revisions });
    },
  );
}
