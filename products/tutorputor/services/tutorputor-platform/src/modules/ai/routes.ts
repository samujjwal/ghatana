/**
 * AI Routes - HTTP endpoints for AI functionality
 *
 * @doc.type routes
 * @doc.purpose AI HTTP endpoint handlers
 * @doc.layer platform
 * @doc.pattern Routes
 */
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { AIProxyService } from "@tutorputor/contracts/v1/services";
import type {
  ModuleId,
  TenantId,
  UserId,
} from "@tutorputor/contracts/v1/types";
import {
  AIContentGenerationError,
  AIContentGenerationService,
} from "./AIContentGenerationService.js";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../core/http/requestContext.js";
import { aiRegistryClient as defaultAiRegistryClient } from "../../clients/ai-registry.client.js";
import { aiQuerySchema } from "../../validation/validator.js";
import { validateBody } from "../../validation/middleware/validation.js";
import { createConsentEnforcement } from "../../core/middleware/consent-enforcement.js";
import { AIAuditService } from "../audit/AIAuditService.js";

type AiRegistryClient = typeof defaultAiRegistryClient;

interface AIRouteDeps {
  aiProxyService: AIProxyService & {
    [key: string]: unknown;
    generateQuestionsFromContent?: (args: {
      tenantId: string;
      moduleId: string;
      count: number;
      difficulty: "easy" | "medium" | "hard";
    }) => Promise<
      Array<{
        question: string;
        options?: string[];
        correctAnswer: string;
        explanation: string;
      }>
    >;
  };
  /** Optional: Platform AI Registry client for model version discovery */
  aiRegistryClient?: AiRegistryClient | null;
}

const DEFAULT_AI_RATE_LIMIT_MAX = 30;
const DEFAULT_AI_RATE_LIMIT_WINDOW_SECONDS = 60;

async function enforceAiTenantRateLimit(
  app: FastifyInstance,
  req: FastifyRequest,
  reply: FastifyReply,
  routeKey: string,
): Promise<boolean> {
  const redis = (
    app as FastifyInstance & {
      redis?: {
        incr: (key: string) => Promise<number>;
        expire: (key: string, seconds: number) => Promise<number>;
      };
    }
  ).redis;
  if (!redis) {
    return true;
  }

  const tenantId = String(getTenantId(req));
  const maxPerWindow = Math.max(
    1,
    Number.parseInt(
      process.env.AI_RATE_LIMIT_MAX_PER_WINDOW ??
        String(DEFAULT_AI_RATE_LIMIT_MAX),
      10,
    ),
  );
  const windowSeconds = Math.max(
    1,
    Number.parseInt(
      process.env.AI_RATE_LIMIT_WINDOW_SECONDS ??
        String(DEFAULT_AI_RATE_LIMIT_WINDOW_SECONDS),
      10,
    ),
  );

  const now = Date.now();
  const windowBucket = Math.floor(now / (windowSeconds * 1000));
  const key = `tutorputor:ai-rate-limit:${tenantId}:${routeKey}:${windowBucket}`;

  try {
    const count = await redis.incr(key);
    if (count === 1) {
      await redis.expire(key, windowSeconds);
    }

    reply.header("X-AI-RateLimit-Limit", maxPerWindow);
    reply.header("X-AI-RateLimit-Remaining", Math.max(0, maxPerWindow - count));

    if (count > maxPerWindow) {
      reply.header("Retry-After", windowSeconds);
      reply.code(429).send({
        error: "Too Many Requests",
        code: "AI_RATE_LIMIT_EXCEEDED",
        message: `AI request quota exceeded for tenant ${tenantId}.`,
        retryAfterSeconds: windowSeconds,
      });
      return false;
    }

    return true;
  } catch (error) {
    app.log.warn(
      { err: error, routeKey, tenantId },
      "AI tenant rate-limit guard failed open",
    );
    return true;
  }
}

export async function registerAIRoutes(
  app: FastifyInstance,
  deps: AIRouteDeps,
): Promise<void> {
  const aiContentService = new AIContentGenerationService(deps.aiProxyService);
  const aiAuditService = new AIAuditService(app.prisma as any);

  // Create consent enforcement middleware for AI routes
  // AI processing requires explicit user consent for data processing
  const consentEnforcement = createConsentEnforcement({
    prisma: app.prisma as any,
  });

  function sendRequestContextError(
    reply: FastifyReply,
    error: unknown,
  ): FastifyReply | null {
    const statusCode =
      typeof (error as { statusCode?: unknown })?.statusCode === "number"
        ? ((error as { statusCode: number }).statusCode as number)
        : null;
    const message =
      error instanceof Error ? error.message : "Request context unavailable";

    if (statusCode === null) {
      return null;
    }

    return reply.status(statusCode).send({
      error: message,
      code:
        typeof (error as { code?: unknown })?.code === "string"
          ? (error as { code: string }).code
          : "REQUEST_CONTEXT_ERROR",
    });
  }

  // AI Tutor query endpoint - requires ai_processing consent
  app.post(
    "/tutor/query",
    { preHandler: [validateBody(aiQuerySchema), consentEnforcement.preHandler] },
    async (req, reply) => {
      const startTime = Date.now();
      const tenantId = getTenantId(req) as TenantId;
      const userId = getUserId(req) as UserId;
      const { moduleId, question, locale } = req.body as {
        moduleId?: ModuleId;
        question: string;
        locale?: string;
      };
      let modelId: string | undefined;
      let success = true;
      let errorMessage: string | undefined;
      let response: any = null;

      try {
        if (!(await enforceAiTenantRateLimit(app, req, reply, "tutor-query"))) {
          success = false;
          errorMessage = "Rate limit exceeded";
          throw new Error("Rate limit exceeded");
        }

        if (!question || question.trim().length === 0) {
          success = false;
          errorMessage = "Question is required";
          return reply.status(400).send({ error: "Question is required" });
        }

        // Resolve active model from platform AI Registry for observability & routing
        if (deps.aiRegistryClient) {
          try {
            const model = await deps.aiRegistryClient.findActiveModel(
              String(tenantId),
              "tutoring-llm",
            );
            if (model) {
              modelId = model.id;
              app.log.debug(
                `[AI] Active model resolved: ${model.id} (${model.name})`,
              );
            }
          } catch {
            app.log.warn("[AI] Could not resolve active model from registry");
          }
        }

        app.log.info(
          `[AI] Tutor query from user ${String(userId)}: ${question.substring(0, 50)}...`,
        );

        const response = await deps.aiProxyService.handleTutorQuery({
          tenantId,
          userId,
          question,
          ...(moduleId ? { moduleId } : {}),
          ...(locale ? { locale } : {}),
        });

        if (modelId) {
          void reply.header("x-active-model-id", modelId);
        }

        // Add provenance metadata to response
        const provenance = {
          modelId: modelId || "unknown",
          timestamp: new Date().toISOString(),
          tenantId: String(tenantId),
          userId: String(userId),
          ...(moduleId ? { moduleId: String(moduleId) } : {}),
          ...(locale ? { locale } : {}),
        };

        return reply.send({ response, provenance });
      } catch (error) {
        success = false;
        errorMessage = error instanceof Error ? error.message : "Unknown error";
        const contextErrorReply = sendRequestContextError(reply, error);
        if (contextErrorReply) {
          return contextErrorReply;
        }
        throw error;
      } finally {
        // Log AI inference for audit
        const latencyMs = Date.now() - startTime;
        const auditEntry: any = {
          tenantId: String(tenantId),
          userId: String(userId),
          modelId: modelId || "unknown",
          endpoint: "tutor/query",
          requestPayload: JSON.stringify({ question, moduleId, locale }),
          policyDecision: success ? "allowed" : "blocked",
          latencyMs,
          success,
          errorMessage,
          ipAddress: (req as any).ip,
          userAgent: (req as any).headers["user-agent"],
        };
        if (success && response) {
          auditEntry.responsePayload = JSON.stringify({ modelId });
        }
        void aiAuditService.logInference(auditEntry);
      }
    },
  );

  // AI-generated questions from module content — teacher/admin only (resource-intensive)
  // Requires ai_processing consent
  app.post(
    "/generate-questions",
    { preHandler: consentEnforcement.preHandler },
    async (req, reply) => {
      const startTime = Date.now();
      const tenantId = getTenantId(req) as TenantId;
      const userId = getUserId(req) as UserId;
      const {
        moduleId,
        count = 5,
        difficulty = "medium",
      } = req.body as {
        moduleId: ModuleId;
        count?: number;
        difficulty?: "easy" | "medium" | "hard";
      };
      let success = true;
      let errorMessage: string | undefined;
      let response: any = null;

      try {
        requireRole(req, ["teacher", "admin", "superadmin"]);

        if (
          !(await enforceAiTenantRateLimit(app, req, reply, "generate-questions"))
        ) {
          success = false;
          errorMessage = "Rate limit exceeded";
          throw new Error("Rate limit exceeded");
        }

        if (!moduleId) {
          success = false;
          errorMessage = "moduleId is required";
          return reply.status(400).send({ error: "moduleId is required" });
        }

        if (!deps.aiProxyService.generateQuestionsFromContent) {
          success = false;
          errorMessage = "Question generation not available";
          return reply.status(501).send({
            error: "Question generation not available",
            questions: [],
          });
        }

        const questions = await deps.aiProxyService.generateQuestionsFromContent({
          tenantId: String(tenantId),
          moduleId: String(moduleId),
          count: Math.min(count, 10),
          difficulty,
        });

        response = { questions };
        return reply.send({ questions });
      } catch (error) {
        success = false;
        errorMessage = error instanceof Error ? error.message : String(error);

        const message = errorMessage;

        // Return structured error codes for client handling
        if (message.startsWith("AI_NOT_CONFIGURED:")) {
          return reply.status(503).send({
            error: "AI service not configured",
            code: "AI_NOT_CONFIGURED",
            details: message.replace("AI_NOT_CONFIGURED: ", ""),
            questions: [],
          });
        }

        if (message.startsWith("NO_CONTENT:")) {
          return reply.status(422).send({
            error: "No content available for question generation",
            code: "NO_CONTENT",
            details: message.replace("NO_CONTENT: ", ""),
            questions: [],
          });
        }

        if (message.startsWith("GENERATION_FAILED:")) {
          return reply.status(500).send({
            error: "Question generation failed",
            code: "GENERATION_FAILED",
            details: message.replace("GENERATION_FAILED: ", ""),
            questions: [],
          });
        }

        // Unknown error
        const contextErrorReply = sendRequestContextError(reply, error);
        if (contextErrorReply) {
          return contextErrorReply;
        }

        return reply.status(500).send({
          error: message,
          code: "UNKNOWN_ERROR",
          questions: [],
        });
      } finally {
        // Log AI inference for audit
        const latencyMs = Date.now() - startTime;
        const auditEntry: any = {
          tenantId: String(tenantId),
          userId: String(userId),
          modelId: "unknown",
          endpoint: "generate-questions",
          requestPayload: JSON.stringify({ moduleId, count, difficulty }),
          policyDecision: success ? "allowed" : "blocked",
          latencyMs,
          success,
          errorMessage,
          ipAddress: (req as any).ip,
          userAgent: (req as any).headers["user-agent"],
        };
        if (success && response) {
          auditEntry.responsePayload = JSON.stringify(response);
        }
        void aiAuditService.logInference(auditEntry);
      }
    },
  );

  // AI-powered concept generation from name - requires ai_processing consent
  app.post(
    "/generate-concept",
    { preHandler: consentEnforcement.preHandler },
    async (req, reply) => {
      const startTime = Date.now();
      const tenantId = getTenantId(req) as TenantId;
      const userId = getUserId(req) as UserId;
      const { conceptName, domain } = req.body as {
        conceptName: string;
        domain: string;
      };
      let success = true;
      let errorMessage: string | undefined;
      let response: any = null;

      try {
        if (
          !(await enforceAiTenantRateLimit(app, req, reply, "generate-concept"))
        ) {
          success = false;
          errorMessage = "Rate limit exceeded";
          throw new Error("Rate limit exceeded");
        }

        requireRole(req, ["admin"]);

        if (!conceptName || !domain) {
          success = false;
          errorMessage = "conceptName and domain are required";
          return reply
            .status(400)
            .send({ error: "conceptName and domain are required" });
        }

        app.log.info(`[AI] Generating concept: ${conceptName} (${domain})`);

        const generated = await aiContentService.generateConceptFromName(
          conceptName,
          domain,
          String(tenantId),
        );

        response = { concept: generated };
        return reply.send({ success: true, concept: generated });
      } catch (error) {
        success = false;
        errorMessage = error instanceof Error ? error.message : "Unknown error";

        if (
          (error as { statusCode?: number }).statusCode === 403 ||
          errorMessage.includes("Forbidden")
        ) {
          return reply.status(403).send({ error: errorMessage });
        }

        if (error instanceof AIContentGenerationError) {
          return reply.status(422).send({
            error: error.message,
            code: error.code,
          });
        }

        const contextErrorReply = sendRequestContextError(reply, error);
        if (contextErrorReply) {
          return contextErrorReply;
        }

        app.log.error(`[AI] Concept generation failed: ${errorMessage}`);
        return reply.status(500).send({ error: errorMessage });
      } finally {
        // Log AI inference for audit
        const latencyMs = Date.now() - startTime;
        const auditEntry: any = {
          tenantId: String(tenantId),
          userId: String(userId),
          modelId: "unknown",
          endpoint: "generate-concept",
          requestPayload: JSON.stringify({ conceptName, domain }),
          policyDecision: success ? "allowed" : "blocked",
          latencyMs,
          success,
          errorMessage,
          ipAddress: (req as any).ip,
          userAgent: (req as any).headers["user-agent"],
        };
        if (success && response) {
          auditEntry.responsePayload = JSON.stringify(response);
        }
        void aiAuditService.logInference(auditEntry);
      }
    },
  );

  // AI-powered simulation manifest generation - requires ai_processing consent
  app.post(
    "/generate-simulation",
    { preHandler: consentEnforcement.preHandler },
    async (req, reply) => {
      const startTime = Date.now();
      const tenantId = getTenantId(req) as TenantId;
      const userId = getUserId(req) as UserId;
      const { description, conceptName, domain } = req.body as {
        description: string;
        conceptName: string;
        domain: string;
      };
      let success = true;
      let errorMessage: string | undefined;
      let response: any = null;

      try {
        if (
          !(await enforceAiTenantRateLimit(
            app,
            req,
            reply,
            "generate-simulation",
          ))
        ) {
          success = false;
          errorMessage = "Rate limit exceeded";
          throw new Error("Rate limit exceeded");
        }

        requireRole(req, ["admin"]);

        if (!description || !conceptName || !domain) {
          success = false;
          errorMessage = "description, conceptName, and domain are required";
          return reply.status(400).send({
            error: "description, conceptName, and domain are required",
          });
        }

        app.log.info(
          `[AI] Generating simulation: ${description} for concept ${conceptName}`,
        );

        const generated = await aiContentService.generateSimulationManifest(
          description,
          conceptName,
          domain,
          String(tenantId),
        );

        response = { simulation: generated };
        return reply.send({ success: true, simulation: generated });
      } catch (error) {
        success = false;
        errorMessage = error instanceof Error ? error.message : "Unknown error";

        if (
          (error as { statusCode?: number }).statusCode === 403 ||
          errorMessage.includes("Forbidden")
        ) {
          return reply.status(403).send({ error: errorMessage });
        }

        const contextErrorReply = sendRequestContextError(reply, error);
        if (contextErrorReply) {
          return contextErrorReply;
        }

        app.log.error(`[AI] Simulation generation failed: ${errorMessage}`);
        return reply.status(500).send({ error: errorMessage });
      } finally {
        // Log AI inference for audit
        const latencyMs = Date.now() - startTime;
        const auditEntry: any = {
          tenantId: String(tenantId),
          userId: String(userId),
          modelId: "unknown",
          endpoint: "generate-simulation",
          requestPayload: JSON.stringify({ description, conceptName, domain }),
          policyDecision: success ? "allowed" : "blocked",
          latencyMs,
          success,
          errorMessage,
          ipAddress: (req as any).ip,
          userAgent: (req as any).headers["user-agent"],
        };
        if (success && response) {
          auditEntry.responsePayload = JSON.stringify(response);
        }
        void aiAuditService.logInference(auditEntry);
      }
    },
  );

  app.log.info(
    "[AI] Routes registered: /tutor/query, /generate-questions, /generate-concept, /generate-simulation",
  );
}
