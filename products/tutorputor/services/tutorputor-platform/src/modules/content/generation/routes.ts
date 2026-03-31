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
import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";
import { GenerationPlannerService } from "./planner-service.js";
import {
  GenerationExecutionService,
  getGenerationExecutionChannel,
  type JobExecutionResult,
  type GenerationExecutionStreamMessage,
} from "./execution-service.js";
type GenerationRequestConfig = Record<string, unknown>;
import { GenerationQueueDispatcher } from "./queue-dispatcher.js";

// =============================================================================
// Register
// =============================================================================

export function registerGenerationRoutes(
  app: FastifyInstance,
  deps: { prisma: PrismaClient; redis?: Redis },
): void {
  const service = new GenerationPlannerService(deps.prisma, deps.redis);
  const executionService = new GenerationExecutionService(deps.prisma, deps.redis);
  const dispatcher = new GenerationQueueDispatcher(deps.prisma);

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
      const {
        title,
        description,
        domain,
        conceptId,
        targetGrades,
        requestConfig,
      } =
        request.body;

      if (!title || !domain) {
        return reply
          .status(400)
          .send({ error: "title and domain are required" });
      }

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
      const { status, limit, offset } = request.query;

      const result = await service.listRequests(tenantId, {
        ...(status ? { status } : {}),
        ...(limit ? { limit: parseInt(limit, 10) } : {}),
        ...(offset ? { offset: parseInt(offset, 10) } : {}),
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
      const { requestId } = request.params;

      const result = await service.getRequest(tenantId, requestId);
      if (!result) {
        return reply
          .status(404)
          .send({ error: "Generation request not found" });
      }

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
      const { requestId } = request.params;
      const includeOutput = request.query.includeOutput !== "false";

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

      writeSseEvent(reply.raw, "snapshot", {
        request: snapshot.request,
        progress: snapshot.progress,
      });

      for (const event of snapshot.events) {
        writeSseEvent(reply.raw, "event", event);
      }

      for (const job of snapshot.request.jobs) {
        writeSseEvent(reply.raw, "job", {
          id: job.id,
          jobType: job.jobType,
          status: job.status,
          progress: job.progress,
          targetRef: job.targetRef,
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
        });
        reply.raw.end();
        return;
      }

      const subscriber = (deps.redis as any).duplicate() as any;
      const channel = getGenerationExecutionChannel(requestId);
      const heartbeat = setInterval(() => {
        writeSseEvent(reply.raw, "heartbeat", {
          requestId,
          at: new Date().toISOString(),
        });
      }, 15000);

      const cleanup = async () => {
        clearInterval(heartbeat);
        subscriber.removeAllListeners("message");
        await subscriber.unsubscribe(channel).catch(() => undefined);
        subscriber.disconnect();
      };

      reply.raw.on("close", () => {
        void cleanup();
      });

      subscriber.on("message", async (_channel: string, rawMessage: string) => {
        const message = JSON.parse(
          rawMessage,
        ) as GenerationExecutionStreamMessage;

        if (message.kind === "snapshot" && message.snapshot) {
          const snapshot = message.snapshot as any;
          writeSseEvent(reply.raw, "snapshot", {
            request: snapshot.request,
            progress: snapshot.progress,
          });

          if (snapshot.progress?.terminal) {
            writeSseEvent(reply.raw, "done", {
              requestId: snapshot.request?.id,
              status: snapshot.request?.status,
              completionPercent: snapshot.progress?.completionPercent,
              terminal: true,
            });
            await cleanup();
            reply.raw.end();
          }
          return;
        }

        if (message.kind === "job_result" && message.jobResult) {
          writeSseEvent(reply.raw, "job_result", message.jobResult);
          return;
        }

        if (message.kind === "telemetry" && message.telemetry) {
          writeSseEvent(reply.raw, "telemetry", message.telemetry);
          return;
        }

        if (message.kind === "summary" && message.summary) {
          writeSseEvent(reply.raw, "summary", message.summary);
        }
      });

      await subscriber.subscribe(channel);
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
      const { requestId } = request.params;

      try {
        const result = await service.planRequest(tenantId, requestId);
        return reply.send(result);
      } catch (err: any) {
        const message = err instanceof Error ? err.message : "Planning failed";
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
      const { requestId } = request.params;

      try {
        const result = await service.cancelRequest(tenantId, requestId);
        return reply.send(result);
      } catch (err: any) {
        const message =
          err instanceof Error ? err.message : "Cancellation failed";
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
      const { requestId } = request.params;

      try {
        await executionService.startExecution(
          tenantId,
          requestId,
        );
        const dispatch = await dispatcher.dispatchReadyJobs(tenantId, requestId);
        const updated = await service.getRequest(tenantId, requestId);
        return reply.send({
          request: updated,
          dispatch,
        });
      } catch (err: any) {
        const message =
          err instanceof Error ? err.message : "Execution start failed";
        return reply.status(400).send({ error: message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // POST /generation/requests/:requestId/results — Record batch results
  // ---------------------------------------------------------------------------
  app.post<{
    Params: { requestId: string };
    Body: { results: JobExecutionResult[] };
  }>(
    "/generation/requests/:requestId/results",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const { requestId } = request.params;
      const { results } = request.body;

      if (!results || !Array.isArray(results)) {
        return reply.status(400).send({ error: "results array is required" });
      }

      try {
        const summary = await executionService.recordBatchResults(
          requestId,
          results,
        );
        return reply.send(summary);
      } catch (err: any) {
        const message =
          err instanceof Error ? err.message : "Recording results failed";
        return reply.status(400).send({ error: message });
      }
    },
  );
}
