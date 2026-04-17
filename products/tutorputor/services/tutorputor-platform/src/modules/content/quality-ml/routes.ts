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
import { z } from "zod";
import { ContentQualityMLPipeline } from "./pipeline.js";

const assetIdParamsSchema = z.object({
  assetId: z.string().trim().min(1),
});

const experienceIdParamsSchema = z.object({
  experienceId: z.string().trim().min(1),
});

const predictBatchBodySchema = z.object({
  limit: z.number().int().positive().max(500).optional(),
  assetIds: z.array(z.string().trim().min(1)).optional(),
});

const applyExperienceBodySchema = z.object({
  limit: z.number().int().positive().max(500).optional(),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

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
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const prediction = await pipeline.predictAssetQuality(
        getTenantId(request),
        paramsResult.data.assetId,
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
      const bodyResult = predictBatchBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const predictions = await pipeline.backfillPredictions(
        getTenantId(request),
        bodyResult.data,
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
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = applyExperienceBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const predictions = await pipeline.applyPredictionsForExperience(
        getTenantId(request),
        paramsResult.data.experienceId,
        bodyResult.data,
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
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const applied = await pipeline.applyPrediction(
        getTenantId(request),
        paramsResult.data.assetId,
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
      const bodyResult = predictBatchBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const predictions = await pipeline.applyPredictionsBatch(
        getTenantId(request),
        bodyResult.data,
      );
      return reply.status(200).send({
        predictions,
        total: predictions.length,
      });
    },
  );
}
