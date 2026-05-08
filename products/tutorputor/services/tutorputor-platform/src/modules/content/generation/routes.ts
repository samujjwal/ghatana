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
import { writeSseEvent } from "../../../core/http/sse.js";
import { z } from "zod";
import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";
import { GenerationPlannerService } from "./planner-service.js";
import {
  GenerationExecutionService,
  getGenerationExecutionChannel,
  type GenerationExecutionStreamMessage,
  type JobExecutionResult,
} from "./execution-service.js";
import {
  workerAuthMiddleware,
  getWorkerAuth,
} from "../../../core/auth/worker-auth.js";
import {
  loadFeatureFlags,
  type ContentGenerationFlags,
} from "../../../config/feature-flags.js";
type GenerationRequestConfig = Record<string, unknown>;
type GenerationExecutionSnapshotPayload = {
  request: { id?: unknown; status?: unknown };
  progress: { completionPercent?: unknown; terminal?: unknown };
};
import { GenerationQueueDispatcher } from "./queue-dispatcher.js";
import { IntentInferenceService } from "./intent-service.js";
import { buildSensitiveOperationAuditEntry } from "../../policy/resource-access-helpers.js";

type RedisPubSubClient = Redis & {
  removeAllListeners(event?: string): void;
  unsubscribe(channel: string): Promise<unknown>;
  disconnect(): void;
  on(event: string, listener: (...args: unknown[]) => void): unknown;
  subscribe(channel: string): Promise<unknown>;
};

const requestIdParamsSchema = z.object({
  requestId: z.string().trim().min(1),
});

const createRequestBodySchema = z.object({
  title: z.string().trim().min(1),
  description: z.string().trim().min(1).optional(),
  domain: z.string().trim().min(1),
  conceptId: z.string().trim().min(1).optional(),
  targetGrades: z.array(z.string().trim().min(1)).optional(),
  requestConfig: z.record(z.string(), z.unknown()).optional(),
});

const listQuerySchema = z.object({
  status: z.string().trim().min(1).optional(),
  limit: z.coerce.number().int().positive().max(500).optional(),
  offset: z.coerce.number().int().min(0).optional(),
});

const streamQuerySchema = z.object({
  includeOutput: z.enum(["true", "false"]).optional(),
});

const resultsBodySchema = z.object({
  results: z.array(
    z.object({
      jobId: z.string().trim().min(1),
      status: z.enum(["completed", "failed"]),
      durationMs: z.number().int().nonnegative(),
      outputAssetId: z.string().trim().min(1).optional(),
      outputData: z.record(z.string(), z.unknown()).optional(),
      diagnostics: z.record(z.string(), z.unknown()).optional(),
      errorMessage: z.string().trim().optional(),
    }).strict(),
  ).min(1),
});

const inferIntentBodySchema = z.object({
  topic: z.string().trim().min(1).max(200),
  preferredDomain: z.string().trim().min(1).optional(),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

function toJobExecutionResults(
  results: Array<Record<string, unknown>>,
): JobExecutionResult[] {
  return results.map((result) => ({
    jobId: String(result.jobId ?? ""),
    status: result.status === "failed" ? "failed" : "completed",
    ...(typeof result.outputAssetId === "string"
      ? { outputAssetId: result.outputAssetId }
      : {}),
    ...(result.outputData && typeof result.outputData === "object"
      ? { outputData: result.outputData as Record<string, unknown> }
      : {}),
    ...(result.diagnostics && typeof result.diagnostics === "object"
      ? { diagnostics: result.diagnostics as Record<string, unknown> }
      : {}),
    ...(typeof result.errorMessage === "string"
      ? { errorMessage: result.errorMessage }
      : {}),
    durationMs:
      typeof result.durationMs === "number" && Number.isFinite(result.durationMs)
        ? result.durationMs
        : 0,
  }));
}

function getCorrelationId(request: { headers: Record<string, unknown> }): string | undefined {
  const header = request.headers["x-correlation-id"];
  return typeof header === "string" && header.length > 0 ? header : undefined;
}

function getRequestRole(request: { user?: unknown }): string {
  if (!request.user || typeof request.user !== "object") {
    return "unknown";
  }

  const role = (request.user as { role?: unknown }).role;
  return typeof role === "string" ? role : "unknown";
}

function logSensitiveOperation(
  app: FastifyInstance,
  args: {
    actorId: string;
    actorTenantId: string;
    targetResourceType: string;
    targetResourceId: string;
    operation: string;
    decision: "ALLOW" | "DENY";
    reason: string;
    correlationId: string | undefined;
    metadata: Record<string, string | number | boolean>;
  },
): void {
  const audit = buildSensitiveOperationAuditEntry(args);
  if (audit.decision === "ALLOW") {
    app.log.info({ audit }, "Sensitive operation allowed");
    return;
  }

  app.log.warn({ audit }, "Sensitive operation denied");
}

// =============================================================================
// Register
// =============================================================================

export function registerGenerationRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient; redis?: Redis },
): void {
  const featureFlags = loadFeatureFlags();
  const service = new GenerationPlannerService(deps.prisma, featureFlags, deps.redis);
  const executionService = new GenerationExecutionService(
    deps.prisma,
    deps.redis,
  );
  const dispatcher = new GenerationQueueDispatcher(deps.prisma);
  const intentService = new IntentInferenceService(deps.prisma);

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
      requestConfig?: GenerationRequestConfig;
    };
  }>(
    "/generation/requests",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const requestedBy = getUserId(request);
      const bodyResult = createRequestBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const {
        title,
        description,
        domain,
        conceptId,
        targetGrades,
        requestConfig,
      } = bodyResult.data;

      const result = await service.createRequest({
        tenantId,
        title,
        domain,
        requestedBy,
        ...(description ? { description } : {}),
        ...(conceptId ? { conceptId } : {}),
        ...(targetGrades ? { targetGrades } : {}),
        ...(requestConfig ? { requestConfig } : {}),
      });

      logSensitiveOperation(app, {
        actorId: requestedBy,
        actorTenantId: tenantId,
        targetResourceType: "generation_request",
        targetResourceId: result.id,
        operation: "create",
        decision: "ALLOW",
        reason: "Generation request created",
        correlationId: getCorrelationId(request),
        metadata: {
          role: getRequestRole(request),
          domain,
          hasTargetGrades: Boolean(targetGrades && targetGrades.length > 0),
        },
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
      const queryResult = listQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(queryResult.error.issues));
      }

      const { status, limit, offset } = queryResult.data;

      const result = await service.listRequests(tenantId, {
        ...(status ? { status } : {}),
        ...(limit !== undefined ? { limit } : {}),
        ...(offset !== undefined ? { offset } : {}),
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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      const result = await service.getRequest(tenantId, requestId);
      if (!result) {
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "read",
          decision: "DENY",
          reason: "Generation request not found for tenant scope",
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
          },
        });
        return reply
          .status(404)
          .send({ error: "Generation request not found" });
      }

      logSensitiveOperation(app, {
        actorId: getUserId(request),
        actorTenantId: tenantId,
        targetResourceType: "generation_request",
        targetResourceId: requestId,
        operation: "read",
        decision: "ALLOW",
        reason: "Generation request retrieved",
        correlationId: getCorrelationId(request),
        metadata: {
          role: getRequestRole(request),
        },
      });

      return reply.send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // GET /generation/requests/:requestId/stream — Stream execution snapshot
  // ---------------------------------------------------------------------------
  app.get<{
    Params: { requestId: string };
    Querystring: { includeOutput?: string };
  }>(
    "/generation/requests/:requestId/stream",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const queryResult = streamQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(queryResult.error.issues));
      }

      const { requestId } = paramsResult.data;
      const includeOutput = queryResult.data.includeOutput !== "false";

      const snapshot = await executionService.getExecutionSnapshot(
        tenantId,
        requestId,
      );

      if (!snapshot) {
        return reply
          .status(404)
          .send({ error: "Generation request not found" });
      }

      reply.hijack();
      reply.raw.statusCode = 200;
      reply.raw.setHeader("Content-Type", "text/event-stream");
      reply.raw.setHeader("Cache-Control", "no-cache, no-transform");
      reply.raw.setHeader("Connection", "keep-alive");
      reply.raw.setHeader("X-Accel-Buffering", "no");

      // Support reconnection with Last-Event-ID
      const lastEventId = request.headers["last-event-id"] as string | undefined;
      let messageSequence = 0;

      writeSseEvent(reply.raw, "connected", {
        requestId,
        at: new Date().toISOString(),
        messageSequence: messageSequence++,
        ...(lastEventId ? { resumedFrom: lastEventId } : {}),
      });

      writeSseEvent(reply.raw, "snapshot", {
        request: snapshot.request,
        progress: snapshot.progress,
        messageSequence: messageSequence++,
      });

      for (const event of snapshot.events) {
        writeSseEvent(reply.raw, "event", {
          ...event,
          messageSequence: messageSequence++,
        });
      }

      for (const job of snapshot.request.jobs) {
        writeSseEvent(reply.raw, "job", {
          id: job.id,
          jobType: job.jobType,
          status: job.status,
          progress: job.progress,
          targetRef: job.targetRef,
          messageSequence: messageSequence++,
          ...(includeOutput
            ? {
                outputData: job.outputData,
                diagnostics: job.diagnostics,
                errorMessage: job.errorMessage,
              }
            : {}),
        });
      }

      if (!deps.redis || snapshot.progress.terminal) {
        writeSseEvent(reply.raw, "done", {
          requestId: snapshot.request.id,
          status: snapshot.request.status,
          completionPercent: snapshot.progress.completionPercent,
          terminal: snapshot.progress.terminal,
          messageSequence: messageSequence++,
        });
        reply.raw.end();
        return;
      }

      const subscriber = ((deps.redis as Redis & { duplicate?: () => Redis }).duplicate?.() ??
        deps.redis) as RedisPubSubClient;
      const channel = getGenerationExecutionChannel(requestId);

      // Heartbeat with sequence number for reconnection
      const heartbeat = setInterval(() => {
        try {
          writeSseEvent(reply.raw, "heartbeat", {
            requestId,
            at: new Date().toISOString(),
            messageSequence: messageSequence++,
          });
        } catch (error) {
          clearInterval(heartbeat);
          void cleanup();
        }
      }, 15000);

      // Connection health monitoring
      let lastActivity = Date.now();
      const activityCheck = setInterval(() => {
        const idleTime = Date.now() - lastActivity;
        if (idleTime > 60000) { // 1 minute of inactivity
          void cleanup();
          reply.raw.end();
        }
      }, 30000);

      const cleanup = async () => {
        clearInterval(heartbeat);
        clearInterval(activityCheck);
        subscriber.removeAllListeners("message");
        await subscriber.unsubscribe(channel).catch(() => undefined);
        subscriber.disconnect();
      };

      reply.raw.on("close", () => {
        void cleanup();
      });

      reply.raw.on("error", (error) => {
        writeSseEvent(reply.raw, "error", {
          message: "Stream error",
          error: error instanceof Error ? error.message : String(error),
          at: new Date().toISOString(),
        });
        void cleanup();
        reply.raw.end();
      });

      subscriber.on("message", async (...args: unknown[]) => {
        try {
          lastActivity = Date.now();
          const rawMessage = typeof args[1] === "string" ? args[1] : "";
          const message = JSON.parse(
            rawMessage,
          ) as GenerationExecutionStreamMessage;

          // Validate message structure
          if (!message.kind || !message.requestId) {
            writeSseEvent(reply.raw, "error", {
              message: "Invalid message format",
              at: new Date().toISOString(),
            });
            return;
          }

          if (message.requestId !== requestId) {
            return; // Ignore messages for other requests
          }

          if (message.kind === "snapshot" && message.snapshot) {
            const snapshot =
              message.snapshot as unknown as GenerationExecutionSnapshotPayload;
            writeSseEvent(reply.raw, "snapshot", {
              request: snapshot.request,
              progress: snapshot.progress,
              messageSequence: messageSequence++,
            });

            if (snapshot.progress?.terminal) {
              writeSseEvent(reply.raw, "done", {
                requestId: snapshot.request?.id,
                status: snapshot.request?.status,
                completionPercent: snapshot.progress?.completionPercent,
                terminal: snapshot.progress?.terminal,
                messageSequence: messageSequence++,
              });
              void cleanup();
              reply.raw.end();
            }
          } else if (message.kind === "job_result" && message.jobResult) {
            writeSseEvent(reply.raw, "job_result", {
              ...message.jobResult,
              messageSequence: messageSequence++,
            });
          } else if (message.kind === "summary" && message.summary) {
            writeSseEvent(reply.raw, "summary", {
              ...message.summary,
              messageSequence: messageSequence++,
            });
            void cleanup();
            reply.raw.end();
          } else if (message.kind === "telemetry" && message.telemetry) {
            writeSseEvent(reply.raw, "telemetry", {
              ...message.telemetry,
              messageSequence: messageSequence++,
            });
          }
        } catch (error) {
          writeSseEvent(reply.raw, "error", {
            message: "Failed to process message",
            error: error instanceof Error ? error.message : String(error),
            at: new Date().toISOString(),
          });
        }
      });
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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      try {
        const result = await service.planRequest(tenantId, requestId);
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "plan",
          decision: "ALLOW",
          reason: "Generation request planned",
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
            totalJobs: result.totalJobs,
          },
        });
        return reply.send(result);
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "Planning failed";
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "plan",
          decision: "DENY",
          reason: message,
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
          },
        });
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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      try {
        const result = await service.cancelRequest(tenantId, requestId);
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "cancel",
          decision: "ALLOW",
          reason: "Generation request cancelled",
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
          },
        });
        return reply.send(result);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Cancellation failed";
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "cancel",
          decision: "DENY",
          reason: message,
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
          },
        });
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
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const { requestId } = paramsResult.data;

      try {
        await executionService.startExecution(tenantId, requestId);
        const dispatch = await dispatcher.dispatchReadyJobs(
          tenantId,
          requestId,
        );
        const updated = await service.getRequest(tenantId, requestId);
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "execute",
          decision: "ALLOW",
          reason: "Generation request execution started",
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
            queuedJobs: dispatch.queuedJobs.length,
          },
        });
        return reply.send({
          request: updated,
          dispatch,
        });
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Execution start failed";
        logSensitiveOperation(app, {
          actorId: getUserId(request),
          actorTenantId: tenantId,
          targetResourceType: "generation_request",
          targetResourceId: requestId,
          operation: "execute",
          decision: "DENY",
          reason: message,
          correlationId: getCorrelationId(request),
          metadata: {
            role: getRequestRole(request),
          },
        });
        return reply.status(400).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/requests/:requestId/results — Record batch results (worker-authenticated)
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
    Body: { results: JobExecutionResult[] };
  }>(
    "/generation/requests/:requestId/results",
    { preHandler: [workerAuthMiddleware] },
    async (request, reply) => {
      const workerAuth = getWorkerAuth(request);
      const paramsResult = requestIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(paramsResult.error.issues));
      }

      const bodyResult = resultsBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const { requestId } = paramsResult.data;
      const { results } = bodyResult.data;

      // Verify worker type is content-generation
      if (workerAuth.workerType !== "content-generation") {
        return reply.status(403).send({
          error: "Forbidden",
          message: "Invalid worker type for this endpoint",
        });
      }

      try {
        // Fetch the request to verify tenant ownership
        const request = await executionService.getExecutionSnapshot(
          workerAuth.tenantId,
          requestId,
        );

        if (!request) {
          return reply.status(404).send({
            error: "Not Found",
            message: "Generation request not found",
          });
        }

        // Verify tenant matches the request's tenant (already validated by getExecutionSnapshot)
        // This ensures the worker can only submit results for requests in their tenant

        const summary = await executionService.recordBatchResults(
          requestId,
          results.map((r) => ({
            jobId: r.jobId,
            status: r.status,
            durationMs: r.durationMs,
            ...(r.outputAssetId ? { outputAssetId: r.outputAssetId } : {}),
            ...(r.outputData ? { outputData: r.outputData } : {}),
            ...(r.diagnostics ? { diagnostics: r.diagnostics } : {}),
            ...(r.errorMessage ? { errorMessage: r.errorMessage } : {}),
          } satisfies JobExecutionResult)),
          workerAuth.tenantId,
          workerAuth.workerId,
        );
        return reply.send(summary);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Recording results failed";
        return reply.status(400).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/infer-intent — AI-powered intent inference from topic
  // ---------------------------------------------------------------------------
  app.post<{
    Body: { topic: string; preferredDomain?: string };
  }>(
    "/generation/infer-intent",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const bodyResult = inferIntentBodySchema.safeParse(request.body);

      if (!bodyResult.success) {
        return reply
          .status(400)
          .send(validationErrorResponse(bodyResult.error.issues));
      }

      const { topic, preferredDomain } = bodyResult.data;

      try {
        const intent = await intentService.inferIntent({
          tenantId,
          userId,
          topic,
          preferredDomain,
        });

        return reply.send({ data: intent });
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Intent inference failed";
        return reply.status(500).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // GET /generation/notifications/stream — SSE stream for job status updates
  // ---------------------------------------------------------------------------
  app.get(
    "/generation/notifications/stream",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const userId = getUserId(request);

      reply.raw.writeHead(200, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        Connection: "keep-alive",
      });

      // Send initial connection message
      reply.raw.write(`data: ${JSON.stringify({ type: "connected", userId })}\n\n`);

      // Keep connection alive with ping every 30 seconds
      const keepAlive = setInterval(() => {
        reply.raw.write(`: ping\n\n`);
      }, 30000);

      // Clean up on close
      request.raw.on("close", () => {
        clearInterval(keepAlive);
      });

      return reply;
    },
  );
}
