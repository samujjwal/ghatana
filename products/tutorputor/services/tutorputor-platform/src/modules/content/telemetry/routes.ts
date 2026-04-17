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
import { z } from "zod";

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

const trackEventBodySchema = z.object({
  sessionId: z.string().trim().min(1).optional(),
  eventType: z.string().trim().min(1),
  query: z.string().trim().min(1).optional(),
  assetId: z.string().trim().min(1).optional(),
  assetType: z.string().trim().min(1).optional(),
  position: z.number().int().nonnegative().optional(),
  score: z.number().optional(),
  feedbackLabel: z.string().trim().min(1).optional(),
  feedbackScore: z.number().optional(),
  metadata: z.record(z.unknown()).optional(),
  occurredAt: z.string().datetime().optional(),
});

const trackBatchBodySchema = z.object({
  events: z.array(trackEventBodySchema).min(1),
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

export function registerTelemetryRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient },
): void {
  const service = new TelemetryService(deps.prisma);
  // Allow any authenticated user to submit events
  const authGuard = roleGuard(["admin", "content_creator", "superadmin", "learner"]);

  // ---------------------------------------------------------------------------
  // POST /telemetry/events â€” Track a single explorer event
  // ---------------------------------------------------------------------------
  app.post<{ Body: Omit<TrackExplorerEventInput, "userId"> }>(
    "/telemetry/events",
    { preHandler: [authGuard] },
    async (request, reply) => {
      const bodyResult = trackEventBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid telemetry event payload",
        );
      }

      const tenantId = getTenantId(request);
      const userId = getUserId(request);

      const event = await service.trackEvent(tenantId, {
        ...bodyResult.data,
        ...(userId ? { userId } : {}),
      });

      return reply.status(201).send(event);
    },
  );

  // ---------------------------------------------------------------------------
  // POST /telemetry/events/batch â€” Track a batch of explorer events
  // ---------------------------------------------------------------------------
  app.post<{ Body: TrackBatchEventsInput }>(
    "/telemetry/events/batch",
    { preHandler: [authGuard] },
    async (request, reply) => {
      const bodyResult = trackBatchBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid telemetry batch payload",
        );
      }

      const tenantId = getTenantId(request);

      const result = await service.trackBatch(
        tenantId,
        bodyResult.data as TrackBatchEventsInput,
      );

      return reply.status(200).send(result);
    },
  );
}

