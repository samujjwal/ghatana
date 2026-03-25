/**
 * Evaluation Routes
 *
 * @doc.type module
 * @doc.purpose HTTP routes for evaluation and guardrail scorecard APIs
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { EvaluationService } from "./evaluation-service.js";

export function registerEvaluationRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new EvaluationService(deps.prisma);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // POST /evaluation/requests/:requestId/evaluate — Run evaluation
  // ---------------------------------------------------------------------------
  app.post<{ Params: { requestId: string } }>(
    "/evaluation/requests/:requestId/evaluate",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      const scorecard = await service.evaluateGenerationRequest(
        tenantId,
        requestId,
      );

      return reply.status(200).send(scorecard);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /evaluation/requests/:requestId/evaluations — List for request
  // ---------------------------------------------------------------------------
  app.get<{ Params: { requestId: string } }>(
    "/evaluation/requests/:requestId/evaluations",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      const records = await service.getEvaluationsByRequest(
        tenantId,
        requestId,
      );

      return reply.status(200).send({ evaluations: records });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /evaluation/records/:evaluationId — Get single evaluation record
  // ---------------------------------------------------------------------------
  app.get<{ Params: { evaluationId: string } }>(
    "/evaluation/records/:evaluationId",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { evaluationId } = request.params;

      const record = await service.getEvaluation(tenantId, evaluationId);

      if (!record) {
        return reply.status(404).send({ error: "Evaluation record not found" });
      }

      return reply.status(200).send(record);
    },
  );
}
