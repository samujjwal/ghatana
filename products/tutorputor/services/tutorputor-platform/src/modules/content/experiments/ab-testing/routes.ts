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
import { ABTestingService } from "./service.js";

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
      const experiment = await service.createExperienceExperiment(
        getTenantId(request),
        request.body,
      );
      return reply.status(201).send(experiment);
    },
  );

  app.post<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/start",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const experiment = await service.startExperiment(
        getTenantId(request),
        request.params.experimentId,
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
      const variant = await service.assignVariant(
        getTenantId(request),
        request.params.experimentId,
        request.body.userId,
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
      const observation = await service.recordObservation(
        getTenantId(request),
        request.params.experimentId,
        request.body.userId,
        request.body,
      );
      return reply.status(201).send(observation);
    },
  );

  app.get<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/results",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const results = await service.calculateResults(
        getTenantId(request),
        request.params.experimentId,
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
      const evaluation = await service.evaluateActiveExperiments(
        getTenantId(request),
        request.body ?? {},
      );
      return reply.status(200).send(evaluation);
    },
  );

  app.post<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/complete",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const experiment = await service.completeExperiment(
        getTenantId(request),
        request.params.experimentId,
      );
      return reply.status(200).send(experiment);
    },
  );

  app.post<{ Params: { experimentId: string } }>(
    "/experiments/ab/:experimentId/promote",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const experiment = await service.promoteWinner(
        getTenantId(request),
        request.params.experimentId,
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
