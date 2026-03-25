/**
 * Generation Routes
 *
 * Fastify routes for the generation control plane. Serves creation,
 * planning, listing, detail, and cancellation of generation requests.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for generation planner APIs
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
import { GenerationPlannerService } from "./planner-service.js";
import {
  GenerationExecutionService,
  type JobExecutionResult,
} from "./execution-service.js";

// =============================================================================
// Register
// =============================================================================

export function registerGenerationRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new GenerationPlannerService(deps.prisma);
  const executionService = new GenerationExecutionService(deps.prisma);

  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // POST /generation/requests — Create a new generation request
  // ---------------------------------------------------------------------------
  app.post<{
    Body: {
      title: string;
      description?: string;
      domain: string;
      conceptId?: string;
      targetGrades?: string[];
    };
  }>(
    "/generation/requests",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const requestedBy = getUserId(request);
      const { title, description, domain, conceptId, targetGrades } =
        request.body;

      if (!title || !domain) {
        return reply
          .status(400)
          .send({ error: "title and domain are required" });
      }

      const result = await service.createRequest({
        tenantId,
        title,
        description,
        domain,
        conceptId,
        targetGrades,
        requestedBy,
      });

      return reply.status(201).send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /generation/requests — List generation requests
  // ---------------------------------------------------------------------------
  app.get<{
    Querystring: { status?: string; limit?: string; offset?: string };
  }>(
    "/generation/requests",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { status, limit, offset } = request.query;

      const result = await service.listRequests(tenantId, {
        status,
        limit: limit ? parseInt(limit, 10) : undefined,
        offset: offset ? parseInt(offset, 10) : undefined,
      });

      return reply.send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /generation/requests/:requestId — Get request with jobs
  // ---------------------------------------------------------------------------
  app.get<{
    Params: { requestId: string };
  }>(
    "/generation/requests/:requestId",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      const result = await service.getRequest(tenantId, requestId);
      if (!result) {
        return reply
          .status(404)
          .send({ error: "Generation request not found" });
      }

      return reply.send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/requests/:requestId/plan — Run planning phase
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
  }>(
    "/generation/requests/:requestId/plan",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      try {
        const result = await service.planRequest(tenantId, requestId);
        return reply.send(result);
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "Planning failed";
        return reply.status(400).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/requests/:requestId/cancel — Cancel a request
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
  }>(
    "/generation/requests/:requestId/cancel",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      try {
        const result = await service.cancelRequest(tenantId, requestId);
        return reply.send(result);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Cancellation failed";
        return reply.status(400).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/requests/:requestId/execute — Start execution
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
  }>(
    "/generation/requests/:requestId/execute",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      try {
        const result = await executionService.startExecution(
          tenantId,
          requestId,
        );
        return reply.send(result);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Execution start failed";
        return reply.status(400).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/requests/:requestId/results — Record batch results
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
    Body: { results: JobExecutionResult[] };
  }>(
    "/generation/requests/:requestId/results",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const { requestId } = request.params;
      const { results } = request.body;

      if (!results || !Array.isArray(results)) {
        return reply.status(400).send({ error: "results array is required" });
      }

      try {
        const summary = await executionService.recordBatchResults(
          requestId,
          results,
        );
        return reply.send(summary);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Recording results failed";
        return reply.status(400).send({ error: message });
      }
    },
  );
}
