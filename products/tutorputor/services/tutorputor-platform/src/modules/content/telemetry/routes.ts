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
import { assertConsentAllowed, ConsentPolicyError } from "../../compliance/consentPolicy.js";

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
  metadata: z.record(z.string(), z.unknown()).optional(),
  occurredAt: z.string().datetime().optional(),
});

const trackBatchBodySchema = z.object({
  events: z.array(trackEventBodySchema).min(1),
});

const learningTelemetryEventSchema = z.object({
  type: z.string().trim().min(1),
  timestamp: z.string().datetime(),
  actor: z.object({
    id: z.string().trim().min(1),
  }).passthrough(),
  context: z.object({
    tenantId: z.string().trim().min(1),
    learningUnitId: z.string().trim().min(1).optional(),
    claimId: z.string().trim().min(1).optional(),
    sessionId: z.string().trim().min(1),
    platform: z.enum(["web", "mobile", "vr"]),
  }).passthrough(),
  object: z.record(z.string(), z.unknown()),
  result: z.record(z.string(), z.unknown()).optional(),
}).passthrough();

const learningTelemetryBatchBodySchema = z.object({
  events: z.array(learningTelemetryEventSchema).min(1).max(100),
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

function enforceTelemetryConsent(request: { headers: Record<string, unknown> }) {
  const consentHeader = request.headers["x-telemetry-consent"];
  const consentState = Array.isArray(consentHeader) ? consentHeader[0] : consentHeader;
  assertConsentAllowed({
    useCase: "learning_telemetry",
    granted: consentState !== "missing" && consentState !== "revoked" && consentState !== "denied",
    revoked: consentState === "revoked" || consentState === "denied",
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

      const eventInput: TrackExplorerEventInput = {
        eventType: bodyResult.data.eventType,
        ...(bodyResult.data.sessionId ? { sessionId: bodyResult.data.sessionId } : {}),
        ...(bodyResult.data.query ? { query: bodyResult.data.query } : {}),
        ...(bodyResult.data.assetId ? { assetId: bodyResult.data.assetId } : {}),
        ...(bodyResult.data.assetType ? { assetType: bodyResult.data.assetType } : {}),
        ...(bodyResult.data.position !== undefined
          ? { position: bodyResult.data.position }
          : {}),
        ...(bodyResult.data.score !== undefined ? { score: bodyResult.data.score } : {}),
        ...(bodyResult.data.feedbackLabel
          ? { feedbackLabel: bodyResult.data.feedbackLabel }
          : {}),
        ...(bodyResult.data.feedbackScore !== undefined
          ? { feedbackScore: bodyResult.data.feedbackScore }
          : {}),
        ...(bodyResult.data.metadata ? { metadata: bodyResult.data.metadata } : {}),
        ...(bodyResult.data.occurredAt ? { occurredAt: bodyResult.data.occurredAt } : {}),
        ...(userId ? { userId } : {}),
      };

      const event = await service.trackEvent(tenantId, eventInput);

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

  // ---------------------------------------------------------------------------
  // POST /telemetry/learning/batch - Track learning evidence telemetry
  // ---------------------------------------------------------------------------
  app.post(
    "/telemetry/learning/batch",
    { preHandler: [authGuard] },
    async (request, reply) => {
      try {
        enforceTelemetryConsent(request);
      } catch (error) {
        if (error instanceof ConsentPolicyError) {
          return reply.status(403).send({ error: error.message, reason: error.decision.reason });
        }
        throw error;
      }
      const bodyResult = learningTelemetryBatchBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid learning telemetry batch payload",
        );
      }

      const result = await service.ingestLearningTelemetryBatch(
        getTenantId(request),
        getUserId(request),
        bodyResult.data as never,
      );

      return reply.status(202).send(result);
    },
  );
}

