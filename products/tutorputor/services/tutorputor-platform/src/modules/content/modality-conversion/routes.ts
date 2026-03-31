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
import {
  ModalityConversionService,
  type ContentModality,
} from "./service.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { ContentAssetReadService } from "../asset/read-service.js";

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
      const tenantId = getTenantId(request);
      const conversions = await service.listAvailableConversions(
        tenantId,
        request.params.assetId,
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
      const tenantId = getTenantId(request);
      const result = await service.convertAsset(
        tenantId,
        request.params.assetId,
        request.body.targetModality,
      );

      return reply.send(result);
    },
  );
}
