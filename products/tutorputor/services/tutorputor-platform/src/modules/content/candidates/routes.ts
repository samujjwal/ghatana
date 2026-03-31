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

interface CreateRegenerationCandidateInput {
  assetId: string;
  assetType?: string;
  trigger: string;
  severity?: string;
  reason: string;
  evidence?: Record<string, unknown>;
  priority?: number;
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
      const tenantId = getTenantId(request);

      const candidate = await service.createCandidate(tenantId, request.body);
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
      const tenantId = getTenantId(request);
      const { assetId, trigger } = request.query;

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
      const tenantId = getTenantId(request);
      const resolvedBy = getUserId(request);
      const { candidateId } = request.params;

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
      const tenantId = getTenantId(request);
      const { candidateId } = request.params;
      const { generationRequestId } = request.body;

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
