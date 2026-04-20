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
import { z } from "zod";

interface PublishAssetInput {
  assetId: string;
  bypassEvaluationCheck?: boolean;
}

const publishAssetParamsSchema = z.object({
  assetId: z.string().trim().min(1),
});

const publishAssetBodySchema = z.object({
  bypassEvaluationCheck: z.boolean().optional(),
});

const publishAllParamsSchema = z.object({
  requestId: z.string().trim().min(1),
});

function sendValidationError(
  reply: { status: (code: number) => { send: (body: unknown) => unknown } },
  error: z.ZodError,
  message: string,
) {
  return reply.status(400).send({
    error: message,
    issues: error.issues,
  });
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
      const paramsResult = publishAssetParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid asset id");
      }

      const bodyResult = publishAssetBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid publish payload",
        );
      }

      const tenantId = getTenantId(request);
      const publishedBy = getUserId(request);
      const { assetId } = paramsResult.data;

      const publishInput: PublishAssetInput = {
        assetId,
        ...(bodyResult.data.bypassEvaluationCheck !== undefined
          ? { bypassEvaluationCheck: bodyResult.data.bypassEvaluationCheck }
          : {}),
      };

      const result = await service.publishAsset(tenantId, publishedBy, publishInput);

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
      const paramsResult = publishAllParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid request id");
      }

      const tenantId = getTenantId(request);
      const publishedBy = getUserId(request);
      const { requestId } = paramsResult.data;

      const result = await service.publishByGenerationRequest(
        tenantId,
        publishedBy,
        requestId,
      );

      return reply.status(200).send(result);
    },
  );
}
