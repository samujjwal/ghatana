/**
 * Telemetry Routes
 *
 * HTTP routes for recording explorer interaction events (P4.1).
 *
 * @doc.type module
 * @doc.purpose HTTP routes for explorer telemetry ingestion APIs
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
import { TelemetryService } from "./telemetry-service.js";

interface TrackExplorerEventInput {
  userId?: string;
  sessionId?: string;
  eventType: string;
  query?: string;
  assetId?: string;
  assetType?: string;
  position?: number;
  score?: number;
  feedbackLabel?: string;
  feedbackScore?: number;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

interface TrackBatchEventsInput {
  events: TrackExplorerEventInput[];
}

export function registerTelemetryRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new TelemetryService(deps.prisma);
  // Allow any authenticated user to submit events
  const authGuard = roleGuard(["admin", "content_creator", "superadmin", "learner"]);

  // ---------------------------------------------------------------------------
  // POST /telemetry/events — Track a single explorer event
  // ---------------------------------------------------------------------------
  app.post<{ Body: Omit<TrackExplorerEventInput, "userId"> }>(
    "/telemetry/events",
    { preHandler: [authGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);

      const event = await service.trackEvent(tenantId, {
        ...request.body,
        ...(userId ? { userId } : {}),
      });

      return reply.status(201).send(event);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /telemetry/events/batch — Track a batch of explorer events
  // ---------------------------------------------------------------------------
  app.post<{ Body: TrackBatchEventsInput }>(
    "/telemetry/events/batch",
    { preHandler: [authGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);

      const result = await service.trackBatch(
        tenantId,
        request.body as TrackBatchEventsInput,
      );

      return reply.status(200).send(result);
    },
  );
}
