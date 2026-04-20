/**
 * A/B Testing Routes
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for experience experimentation
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { getTenantId, roleGuard } from "../../../../core/http/requestContext.js";
import { z } from "zod";
import { ABTestingService } from "./service.js";

const experimentIdParamsSchema = z.object({
  experimentId: z.string().trim().min(1),
});

const createExperimentBodySchema = z.object({
  experienceId: z.string().trim().min(1),
  controlVersion: z.number().int().positive(),
  treatmentVersion: z.number().int().positive(),
  notes: z.string().trim().min(1).optional(),
  priority: z.number().int().min(1).max(10).optional(),
});

const assignVariantBodySchema = z.object({
  userId: z.string().trim().min(1),
});

const observationBodySchema = z.object({
  userId: z.string().trim().min(1),
  sessionId: z.string().trim().min(1).optional(),
  assetId: z.string().trim().min(1).optional(),
  metricValue: z.number(),
  completed: z.boolean().optional(),
  masteryScore: z.number().optional(),
  feedbackScore: z.number().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

const evaluateActiveBodySchema = z.object({
  minSampleSize: z.number().int().positive().optional(),
  autoPromote: z.boolean().optional(),
  maxPValue: z.number().min(0).max(1).optional(),
  minRelativeImprovement: z.number().min(0).optional(),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

export function registerABTestingRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new ABTestingService(deps.prisma);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  app.post<{
    Body: {
      experienceId: string;
      controlVersion: number;
      treatmentVersion: number;
      notes?: string;
      priority?: number;
    };
  }>(
    "/experiments/ab",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const bodyResult = createExperimentBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const experiment = await service.createExperienceExperiment(
        getTenantId(request),
        {
          experienceId: bodyResult.data.experienceId,
          controlVersion: bodyResult.data.controlVersion,
          treatmentVersion: bodyResult.data.treatmentVersion,
          ...(bodyResult.data.notes ? { notes: bodyResult.data.notes } : {}),
          ...(typeof bodyResult.data.priority === 'number'
            ? { priority: bodyResult.data.priority }
            : {}),
        },
      );
      return reply.status(201).send(experiment);
    },
  );

  app.post<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/start",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const experiment = await service.startExperiment(
        getTenantId(request),
        paramsResult.data.experimentId,
      );
      return reply.status(200).send(experiment);
    },
  );

  app.post<{
    Params: { experimentId: string };
    Body: { userId: string };
  }>(
    "/experiments/ab/:experimentId/assign",
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = assignVariantBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const variant = await service.assignVariant(
        getTenantId(request),
        paramsResult.data.experimentId,
        bodyResult.data.userId,
      );
      return reply.status(200).send({ variant });
    },
  );

  app.post<{
    Params: { experimentId: string };
    Body: {
      userId: string;
      sessionId?: string;
      assetId?: string;
      metricValue: number;
      completed?: boolean;
      masteryScore?: number;
      feedbackScore?: number;
      metadata?: Record<string, unknown>;
    };
  }>(
    "/experiments/ab/:experimentId/observe",
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = observationBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const observation = await service.recordObservation(
        getTenantId(request),
        paramsResult.data.experimentId,
        bodyResult.data.userId,
        {
          metricValue: bodyResult.data.metricValue,
          ...(bodyResult.data.sessionId
            ? { sessionId: bodyResult.data.sessionId }
            : {}),
          ...(bodyResult.data.assetId ? { assetId: bodyResult.data.assetId } : {}),
          ...(typeof bodyResult.data.completed === 'boolean'
            ? { completed: bodyResult.data.completed }
            : {}),
          ...(typeof bodyResult.data.masteryScore === 'number'
            ? { masteryScore: bodyResult.data.masteryScore }
            : {}),
          ...(typeof bodyResult.data.feedbackScore === 'number'
            ? { feedbackScore: bodyResult.data.feedbackScore }
            : {}),
          ...(bodyResult.data.metadata ? { metadata: bodyResult.data.metadata } : {}),
        },
      );
      return reply.status(201).send(observation);
    },
  );

  app.get<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/results",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const results = await service.calculateResults(
        getTenantId(request),
        paramsResult.data.experimentId,
      );
      return reply.status(200).send(results);
    },
  );

  app.post<{
    Body: {
      minSampleSize?: number;
      autoPromote?: boolean;
      maxPValue?: number;
      minRelativeImprovement?: number;
    };
  }>(
    "/experiments/ab/evaluate-active",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const bodyResult = evaluateActiveBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const evaluation = await service.evaluateActiveExperiments(
        getTenantId(request),
        {
          ...(typeof bodyResult.data.minSampleSize === 'number'
            ? { minSampleSize: bodyResult.data.minSampleSize }
            : {}),
          ...(typeof bodyResult.data.autoPromote === 'boolean'
            ? { autoPromote: bodyResult.data.autoPromote }
            : {}),
          ...(typeof bodyResult.data.maxPValue === 'number'
            ? { maxPValue: bodyResult.data.maxPValue }
            : {}),
          ...(typeof bodyResult.data.minRelativeImprovement === 'number'
            ? {
                minRelativeImprovement:
                  bodyResult.data.minRelativeImprovement,
              }
            : {}),
        },
      );
      return reply.status(200).send(evaluation);
    },
  );

  app.post<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/complete",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const experiment = await service.completeExperiment(
        getTenantId(request),
        paramsResult.data.experimentId,
      );
      return reply.status(200).send(experiment);
    },
  );

  app.post<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/promote",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const experiment = await service.promoteWinner(
        getTenantId(request),
        paramsResult.data.experimentId,
      );
      return reply.status(200).send(experiment);
    },
  );

  app.get(
    "/experiments/ab",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const experiments = await service.listExperiments(getTenantId(request));
      return reply.status(200).send({ experiments });
    },
  );
}
