/**
 * Candidate Routes
 *
 * HTTP endpoints for managing regeneration candidates (P4.3).
 *
 * @doc.type module
 * @doc.purpose HTTP routes for regeneration candidate APIs
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
import { RegenerationCandidateService } from "./candidate-service.js";
import { z } from "zod";

interface CreateRegenerationCandidateInput {
  assetId: string;
  assetType?: string;
  trigger: string;
  severity?: string;
  reason: string;
  evidence?: Record<string, unknown>;
  priority?: number;
}

const createCandidateBodySchema = z.object({
  assetId: z.string().trim().min(1),
  assetType: z.string().trim().min(1).optional(),
  trigger: z.string().trim().min(1),
  severity: z.string().trim().min(1).optional(),
  reason: z.string().trim().min(1),
  evidence: z.record(z.string(), z.unknown()).optional(),
  priority: z.number().int().min(1).max(10).optional(),
});

const listCandidatesQuerySchema = z.object({
  assetId: z.string().trim().min(1).optional(),
  trigger: z.string().trim().min(1).optional(),
});

const candidateIdParamsSchema = z.object({
  candidateId: z.string().trim().min(1),
});

const queueCandidateBodySchema = z.object({
  generationRequestId: z.string().trim().min(1),
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

export function registerCandidateRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new RegenerationCandidateService(deps.prisma);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // POST /candidates — Create a regeneration candidate
  // ---------------------------------------------------------------------------
  app.post<{ Body: CreateRegenerationCandidateInput }>(
    "/candidates",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const bodyResult = createCandidateBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid candidate payload",
        );
      }

      const tenantId = getTenantId(request);

      const candidateInput: CreateRegenerationCandidateInput = {
        assetId: bodyResult.data.assetId,
        trigger: bodyResult.data.trigger,
        reason: bodyResult.data.reason,
        ...(bodyResult.data.assetType ? { assetType: bodyResult.data.assetType } : {}),
        ...(bodyResult.data.severity ? { severity: bodyResult.data.severity } : {}),
        ...(bodyResult.data.evidence ? { evidence: bodyResult.data.evidence } : {}),
        ...(bodyResult.data.priority !== undefined
          ? { priority: bodyResult.data.priority }
          : {}),
      };

      const candidate = await service.createCandidate(tenantId, candidateInput);
      return reply.status(201).send(candidate);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /candidates — List open regeneration candidates
  // ---------------------------------------------------------------------------
  app.get<{ Querystring: { assetId?: string; trigger?: string } }>(
    "/candidates",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const queryResult = listCandidatesQuerySchema.safeParse(request.query ?? {});
      if (!queryResult.success) {
        return sendValidationError(
          reply,
          queryResult.error,
          "Invalid candidate filter query",
        );
      }

      const tenantId = getTenantId(request);
      const { assetId, trigger } = queryResult.data;

      const candidates = await service.listOpenCandidates(tenantId, {
        ...(assetId ? { assetId } : {}),
        ...(trigger ? { trigger } : {}),
      });
      return reply.status(200).send({ candidates });
    },
  );

  // ---------------------------------------------------------------------------
  // POST /candidates/:candidateId/dismiss — Dismiss a candidate
  // ---------------------------------------------------------------------------
  app.post<{ Params: { candidateId: string } }>(
    "/candidates/:candidateId/dismiss",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = candidateIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid candidate id",
        );
      }

      const tenantId = getTenantId(request);
      const resolvedBy = getUserId(request);
      const { candidateId } = paramsResult.data;

      const candidate = await service.dismissCandidate(tenantId, candidateId, resolvedBy);
      return reply.status(200).send(candidate);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /candidates/:candidateId/queue — Queue a candidate for regeneration
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { candidateId: string };
    Body: { generationRequestId: string };
  }>(
    "/candidates/:candidateId/queue",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = candidateIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid candidate id",
        );
      }

      const bodyResult = queueCandidateBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid queue payload",
        );
      }

      const tenantId = getTenantId(request);
      const { candidateId } = paramsResult.data;
      const { generationRequestId } = bodyResult.data;

      const candidate = await service.queueCandidate(
        tenantId,
        candidateId,
        generationRequestId,
      );
      return reply.status(200).send(candidate);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /candidates/detect-from-feedback — Auto-detect from telemetry
  // ---------------------------------------------------------------------------
  app.post(
    "/candidates/detect-from-feedback",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);

      const count = await service.detectFromFeedback(tenantId);
      return reply.status(200).send({ created: count });
    },
  );
}
