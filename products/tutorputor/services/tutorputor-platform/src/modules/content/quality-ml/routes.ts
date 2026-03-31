/**
 * Quality ML Routes
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for quality prediction and batch backfill
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
import { ContentQualityMLPipeline } from "./pipeline.js";

export function registerQualityMLRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const pipeline = new ContentQualityMLPipeline(deps.prisma);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  app.get<{ Params: { assetId: string } }>(
    "/quality-ml/assets/:assetId/predict",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const prediction = await pipeline.predictAssetQuality(
        getTenantId(request),
        request.params.assetId,
      );
      return reply.status(200).send(prediction);
    },
  );

  app.post<{
    Body: { limit?: number; assetIds?: string[] };
  }>(
    "/quality-ml/predict-batch",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const predictions = await pipeline.backfillPredictions(
        getTenantId(request),
        request.body ?? {},
      );
      return reply.status(200).send({
        predictions,
        total: predictions.length,
      });
    },
  );

  app.post<{
    Params: { experienceId: string };
    Body: { limit?: number };
  }>(
    "/quality-ml/experiences/:experienceId/apply",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const predictions = await pipeline.applyPredictionsForExperience(
        getTenantId(request),
        request.params.experienceId,
        request.body ?? {},
      );
      return reply.status(200).send({
        predictions,
        total: predictions.length,
      });
    },
  );

  app.post<{ Params: { assetId: string } }>(
    "/quality-ml/assets/:assetId/apply",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const applied = await pipeline.applyPrediction(
        getTenantId(request),
        request.params.assetId,
      );
      return reply.status(200).send(applied);
    },
  );

  app.post<{
    Body: { limit?: number; assetIds?: string[] };
  }>(
    "/quality-ml/predict-batch/apply",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const predictions = await pipeline.applyPredictionsBatch(
        getTenantId(request),
        request.body ?? {},
      );
      return reply.status(200).send({
        predictions,
        total: predictions.length,
      });
    },
  );
}
