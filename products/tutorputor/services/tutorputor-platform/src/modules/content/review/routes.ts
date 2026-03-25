/**
 * Review Routes
 *
 * HTTP routes for generation request review decisions (P3.4).
 *
 * @doc.type module
 * @doc.purpose HTTP routes for admin review decision APIs
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
import { GenerationReviewService } from "./review-service.js";
import type { SubmitReviewDecisionInput } from "@tutorputor/contracts/v1/content-studio";

export function registerReviewRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new GenerationReviewService(deps.prisma);
  const adminGuard = roleGuard(["admin", "superadmin"]);

  // ---------------------------------------------------------------------------
  // POST /review/requests/:requestId/decisions — Submit a review decision
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
    Body: Omit<SubmitReviewDecisionInput, "requestId">;
  }>(
    "/review/requests/:requestId/decisions",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const reviewedBy = getUserId(request);
      const { requestId } = request.params;

      const input: SubmitReviewDecisionInput = {
        requestId,
        ...request.body,
      };

      const decision = await service.submitDecision(tenantId, reviewedBy, input);
      return reply.status(201).send(decision);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /review/requests/:requestId/decisions — List decisions for a request
  // ---------------------------------------------------------------------------
  app.get<{ Params: { requestId: string } }>(
    "/review/requests/:requestId/decisions",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      const decisions = await service.listDecisions(tenantId, requestId);
      return reply.status(200).send({ decisions });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /review/requests/:requestId/decisions/latest — Latest decision
  // ---------------------------------------------------------------------------
  app.get<{ Params: { requestId: string } }>(
    "/review/requests/:requestId/decisions/latest",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { requestId } = request.params;

      const decision = await service.getLatestDecision(tenantId, requestId);
      if (!decision) {
        return reply.status(404).send({ error: "No review decision found" });
      }
      return reply.status(200).send(decision);
    },
  );
}
