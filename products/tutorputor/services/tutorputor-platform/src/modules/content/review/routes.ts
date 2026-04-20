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
import { z } from "zod";
import type { PrismaClient } from "@tutorputor/core/db";
import { GenerationReviewService } from "./review-service.js";
import { GenerationQualityLoopService } from "./quality-loop-service.js";

interface SubmitReviewDecisionInput {
  requestId: string;
  status: "approved" | "rejected" | "regeneration_requested";
  decisionNote?: string;
  regenerateJobIds?: string[];
}

const requestIdParamsSchema = z.object({
  requestId: z.string().trim().min(1),
});

const reviewDecisionBodySchema = z.object({
  status: z.enum(["approved", "rejected", "regeneration_requested"]),
  decisionNote: z.string().trim().min(1).optional(),
  regenerateJobIds: z.array(z.string().trim().min(1)).optional(),
});

const processQualityLoopBodySchema = z.object({
  autoPublish: z.boolean().optional(),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

export function registerReviewRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new GenerationReviewService(deps.prisma);
  const qualityLoopService = new GenerationQualityLoopService(deps.prisma);
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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = reviewDecisionBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      const input: SubmitReviewDecisionInput = {
        requestId,
        status: bodyResult.data.status,
        ...(bodyResult.data.decisionNote
          ? { decisionNote: bodyResult.data.decisionNote }
          : {}),
        ...(bodyResult.data.regenerateJobIds
          ? { regenerateJobIds: bodyResult.data.regenerateJobIds }
          : {}),
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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      const decision = await service.getLatestDecision(tenantId, requestId);
      if (!decision) {
        return reply.status(404).send({ error: "No review decision found" });
      }
      return reply.status(200).send(decision);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /review/requests/:requestId/quality-summary — Evaluate and summarize next action
  // ---------------------------------------------------------------------------
  app.get<{ Params: { requestId: string } }>(
    "/review/requests/:requestId/quality-summary",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      const summary = await qualityLoopService.processRequestOutcome(
        tenantId,
        requestId,
        {
          autoPublish: false,
        },
      );
      return reply.status(200).send(summary);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /review/requests/:requestId/process-quality-loop — Apply evaluation outcome
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
    Body: { autoPublish?: boolean };
  }>(
    "/review/requests/:requestId/process-quality-loop",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const reviewedBy = getUserId(request);
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = processQualityLoopBodySchema.safeParse(
        request.body ?? {},
      );
      if (!bodyResult.success) {
        return reply
          .code(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      const summary = await qualityLoopService.processRequestOutcome(
        tenantId,
        requestId,
        {
          autoPublish: bodyResult.data.autoPublish ?? true,
          actorId: reviewedBy,
        },
      );
      return reply.status(200).send(summary);
    },
  );
}
