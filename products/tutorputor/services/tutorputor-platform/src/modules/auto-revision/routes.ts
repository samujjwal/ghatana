/**
 * @doc.type module
 * @doc.purpose Auto-Revision API routes with admin-only authorization
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance, FastifyRequest } from "fastify";
import type { AutoRevisionService } from "./service";
import { getTenantId, roleGuard } from "../../core/http/requestContext.js";
import { z } from "zod";

const experienceIdParamsSchema = z.object({
  experienceId: z.string().min(1),
});

const experimentIdParamsSchema = z.object({
  experimentId: z.string().min(1),
});

const createABExperimentBodySchema = z.object({
  treatmentVersion: z.coerce.number().int().positive().optional(),
});

export interface AutoRevisionRoutes {
  // Drift detection endpoints
  getDriftSignals: (
    request: FastifyRequest<{ Params: { experienceId: string } }>,
  ) => Promise<unknown>;
  monitorAllDrift: (request: FastifyRequest) => Promise<unknown>;

  // Regeneration endpoints
  queueRegeneration: (
    request: FastifyRequest<{ Params: { experienceId: string } }>,
  ) => Promise<unknown>;
  processRegenerationQueue: (request: FastifyRequest) => Promise<unknown>;

  // A/B testing endpoints
  createABExperiment: (
    request: FastifyRequest<{ Params: { experienceId: string } }>,
  ) => Promise<unknown>;
  evaluateABExperiments: (request: FastifyRequest) => Promise<unknown>;

  // Analytics endpoints
  getRegenerationHistory: (
    request: FastifyRequest<{ Params: { experienceId: string } }>,
  ) => Promise<unknown>;
  getABExperimentResults: (
    request: FastifyRequest<{ Params: { experimentId: string } }>,
  ) => Promise<unknown>;
}

const adminGuard = roleGuard(["admin", "superadmin", "content_creator"]);

export function registerAutoRevisionRoutes(
  fastify: FastifyInstance,
  autoRevisionService: AutoRevisionService,
): void {
  // Get drift signals for a specific experience (admin only)
  fastify.get(
    "/experiences/:experienceId/drift",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid experience id",
          issues: paramsResult.error.issues,
        };
      }
      const { experienceId } = paramsResult.data;
      void getTenantId(request);

      try {
        const signals = await autoRevisionService.detectDrift(experienceId);
        return { success: true, data: signals };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to detect drift signals" };
      }
    },
  );

  // Monitor drift for all active experiences (admin only)
  fastify.post(
    "/monitor-drift",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      void getTenantId(request);

      try {
        const candidates = await autoRevisionService.monitorDrift();
        return { success: true, data: candidates };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to monitor drift" };
      }
    },
  );

  // Queue experience for regeneration (admin only)
  fastify.post(
    "/experiences/:experienceId/regenerate",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid experience id",
          issues: paramsResult.error.issues,
        };
      }
      const { experienceId } = paramsResult.data;
      void getTenantId(request);

      try {
        const signals = await autoRevisionService.detectDrift(experienceId);
        await autoRevisionService.queueExperienceRegeneration(
          experienceId,
          signals,
        );
        return { success: true, message: "Regeneration queued" };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to queue regeneration" };
      }
    },
  );

  // Process regeneration queue (admin/superadmin only - system-level operation)
  fastify.post(
    "/process-queue",
    { preHandler: [roleGuard(["superadmin"])] },
    async (request, reply) => {
      void getTenantId(request);

      try {
        await autoRevisionService.processRegenerationQueue();
        return { success: true, message: "Regeneration queue processed" };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return {
          success: false,
          error: "Failed to process regeneration queue",
        };
      }
    },
  );

  // Create A/B experiment (admin only)
  fastify.post(
    "/experiences/:experienceId/ab-experiment",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid experience id",
          issues: paramsResult.error.issues,
        };
      }
      const bodyResult = createABExperimentBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid A/B experiment payload",
          issues: bodyResult.error.issues,
        };
      }
      const { experienceId } = paramsResult.data;
      const { treatmentVersion } = bodyResult.data;
      void getTenantId(request);

      try {
        const experiment = await autoRevisionService.createABExperiment(
          experienceId,
          typeof treatmentVersion === "number" ? treatmentVersion : 2,
        );
        return { success: true, data: experiment };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to create A/B experiment" };
      }
    },
  );

  // Evaluate A/B experiments (admin/superadmin only - system-level operation)
  fastify.post(
    "/evaluate-ab-experiments",
    { preHandler: [roleGuard(["superadmin"])] },
    async (request, reply) => {
      void getTenantId(request);

      try {
        await autoRevisionService.evaluateABExperiments();
        return { success: true, message: "A/B experiments evaluated" };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to evaluate A/B experiments" };
      }
    },
  );

  // Get regeneration history for an experience (admin only)
  fastify.get(
    "/experiences/:experienceId/history",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid experience id",
          issues: paramsResult.error.issues,
        };
      }
      const { experienceId } = paramsResult.data;
      void getTenantId(request);

      try {
        const history =
          await autoRevisionService.getRegenerationHistory(experienceId);
        return { success: true, data: history };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to get regeneration history" };
      }
    },
  );

  // Get A/B experiment results (admin only)
  fastify.get(
    "/ab-experiments/:experimentId/results",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experimentIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid experiment id",
          issues: paramsResult.error.issues,
        };
      }
      const { experimentId } = paramsResult.data;
      void getTenantId(request);

      try {
        const result =
          await autoRevisionService.getABExperimentResults(experimentId);
        return { success: true, data: result };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to get experiment results" };
      }
    },
  );
}
