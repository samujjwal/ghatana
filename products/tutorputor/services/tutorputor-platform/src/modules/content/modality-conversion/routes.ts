/**
 * Modality Conversion Routes
 *
 * HTTP routes for cross-modal asset transformations.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for modality conversion APIs
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
import { z } from "zod";
import {
  ModalityConversionService,
  type ContentModality,
} from "./service.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { ContentAssetReadService } from "../asset/read-service.js";

const assetIdParamsSchema = z.object({
  assetId: z.string().trim().min(1),
});

const convertBodySchema = z.object({
  targetModality: z.enum(["text", "animation", "simulation", "assessment"]),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

export function registerModalityConversionRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const readService = new ContentAssetReadService(deps.prisma);
  const service = new ModalityConversionService(readService);
  const authGuard = roleGuard([
    "admin",
    "content_creator",
    "superadmin",
    "teacher",
    "student",
  ]);

  app.get<{ Params: { assetId: string } }>(
    "/modality/assets/:assetId/conversions",
    { preHandler: [authGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const tenantId = getTenantId(request);
      const conversions = await service.listAvailableConversions(
        tenantId,
        paramsResult.data.assetId,
      );

      return reply.send({ data: conversions });
    },
  );

  app.post<{
    Params: { assetId: string };
    Body: { targetModality: ContentModality };
  }>(
    "/modality/assets/:assetId/convert",
    { preHandler: [authGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = convertBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const tenantId = getTenantId(request);
      const result = await service.convertAsset(
        tenantId,
        paramsResult.data.assetId,
        bodyResult.data.targetModality as ContentModality,
      );

      return reply.send(result);
    },
  );
}
