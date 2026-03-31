/**
 * Publish Routes
 *
 * HTTP endpoints for closed-loop publish and reindex (P4.4).
 *
 * @doc.type module
 * @doc.purpose HTTP routes for asset publishing APIs
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import {
  getTenantId,
  getUserId,
  roleGuard,
} from "../../../core/http/requestContext.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { PublishService } from "./publish-service.js";

interface PublishAssetInput {
  assetId: string;
  bypassEvaluationCheck?: boolean;
}

export function registerPublishRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new PublishService(deps.prisma);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // POST /publish/assets/:assetId — Publish a single asset
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { assetId: string };
    Body: Omit<PublishAssetInput, "assetId">;
  }>(
    "/publish/assets/:assetId",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const publishedBy = getUserId(request);
      const { assetId } = request.params;

      const result = await service.publishAsset(tenantId, publishedBy, {
        assetId,
        ...request.body,
      });

      const status = result.published ? 200 : 422;
      return reply.status(status).send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /publish/requests/:requestId/publish-all — Bulk publish a generation
  // ---------------------------------------------------------------------------
  app.post<{ Params: { requestId: string } }>(
    "/publish/requests/:requestId/publish-all",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const publishedBy = getUserId(request);
      const { requestId } = request.params;

      const result = await service.publishByGenerationRequest(
        tenantId,
        publishedBy,
        requestId,
      );

      return reply.status(200).send(result);
    },
  );
}
