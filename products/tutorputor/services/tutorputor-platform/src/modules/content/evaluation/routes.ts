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
import { SearchSystemValidator } from "./search-validator.js";
import { z } from "zod";

const requestIdParamsSchema = z.object({
  requestId: z.string().trim().min(1),
});

const evaluationIdParamsSchema = z.object({
  evaluationId: z.string().trim().min(1),
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

export function registerEvaluationRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new EvaluationService(deps.prisma);
  const discoveryValidator = new SearchSystemValidator(deps.prisma);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // POST /evaluation/requests/:requestId/evaluate — Run evaluation
  // ---------------------------------------------------------------------------
  app.post<{ Params: { requestId: string } }>(
    "/evaluation/requests/:requestId/evaluate",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid request id");
      }

      const tenantId = getTenantId(request);
      const { requestId } = paramsResult.data;

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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid request id");
      }

      const tenantId = getTenantId(request);
      const { requestId } = paramsResult.data;

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
      const paramsResult = evaluationIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid evaluation id",
        );
      }

      const tenantId = getTenantId(request);
      const { evaluationId } = paramsResult.data;

      const record = await service.getEvaluation(tenantId, evaluationId);

      if (!record) {
        return reply.status(404).send({ error: "Evaluation record not found" });
      }

      return reply.status(200).send(record);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /evaluation/discovery-system/validate — Validate search/recommendation quality
  // ---------------------------------------------------------------------------
  app.get(
    "/evaluation/discovery-system/validate",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const report = await discoveryValidator.validateDiscoverySystem(tenantId);
      return reply.status(200).send({ discovery: report });
    },
  );
}
