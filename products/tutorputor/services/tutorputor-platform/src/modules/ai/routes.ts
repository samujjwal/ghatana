/**
 * AI Routes - HTTP endpoints for AI functionality
 *
 * @doc.type routes
 * @doc.purpose AI HTTP endpoint handlers
 * @doc.layer platform
 * @doc.pattern Routes
 */
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
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
  AIServiceUnavailableError,
} from "./OllamaAIProxyService.js";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../core/http/requestContext.js";
import { aiRegistryClient as defaultAiRegistryClient } from "../../clients/ai-registry.client.js";
import { aiQuerySchema } from "../../validation/validator.js";
import { validateBody } from "../../validation/middleware/validation.js";
import { createConsentEnforcement } from "../../core/middleware/consent-enforcement.js";
import {
  AIAuditService,
  type AIAuditLogEntry,
} from "../audit/AIAuditService.js";
import {
  assertAIInteractionAllowed,
  buildAIAuditPayload,
  buildAIGovernanceMetadata,
} from "./governance.js";

type AiRegistryClient = typeof defaultAiRegistryClient;

interface AIRouteDeps {
  aiProxyService: AIProxyService & {
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
  /** AI Health Check Service */
  aiHealthCheckService?: {
    getHealthStatus: () => Array<{
      service: string;
      healthy: boolean;
      latency: number;
      error?: string;
      lastCheck: Date;
    }>;
  };
  /** AI Cache Service */
  aiCacheService?: {
    getStats: () => { totalHits: number; totalMisses: number; hitRate: number };
  };
}

const DEFAULT_AI_RATE_LIMIT_MAX = 30;
const DEFAULT_AI_RATE_LIMIT_WINDOW_SECONDS = 60;

function requestAuditMetadata(
  req: FastifyRequest,
): Pick<AIAuditLogEntry, "ipAddress" | "userAgent"> {
  const userAgent = req.headers["user-agent"];
  return {
    ipAddress: req.ip,
    ...(typeof userAgent === "string" ? { userAgent } : {}),
  };
}

function buildAuditEntry(args: {
  req: FastifyRequest;
  tenantId: TenantId;
  userId: UserId;
  modelId: string;
  modelVersion?: string;
  endpoint: string;
  requestPayload: string;
  responsePayload?: string;
  success: boolean;
  errorMessage?: string;
  failureReason?: "policy_blocked" | "service_unavailable" | "rate_limited" | "validation_error" | "service_error";
  latencyMs: number;
}): AIAuditLogEntry {
  const policyDecision = args.success
    ? "allowed"
    : args.failureReason === "policy_blocked"
      ? "blocked"
      : args.failureReason === "rate_limited"
        ? "rate_limited"
        : args.failureReason === "validation_error"
          ? "validation_failed"
          : "service_error";

  const result: AIAuditLogEntry = {
    tenantId: String(args.tenantId),
    userId: String(args.userId),
    modelId: args.modelId,
    endpoint: args.endpoint,
    requestPayload: args.requestPayload,
    policyDecision,
    latencyMs: args.latencyMs,
    success: args.success,
    ...requestAuditMetadata(args.req),
    ...(args.modelVersion ? { modelVersion: args.modelVersion } : {}),
    ...(args.responsePayload ? { responsePayload: args.responsePayload } : {}),
    ...(args.errorMessage ? { errorMessage: args.errorMessage } : {}),
  };

  if (args.failureReason) {
    result.failureReason = args.failureReason;
  }

  return result;
}

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
    // Fail-closed: if Redis is unavailable, reject the request
    app.log.error(
      { routeKey },
      "AI tenant rate-limit guard failed: Redis unavailable - rejecting request (fail-closed)",
    );
    reply.code(503).send({
      error: "Service Unavailable",
      code: "AI_RATE_LIMIT_SERVICE_UNAVAILABLE",
      message: "AI service rate limiter is unavailable. Please try again later.",
    });
    return false;
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
    // Fail-closed: if Redis operation fails, reject the request
    app.log.error(
      { err: error, routeKey, tenantId },
      "AI tenant rate-limit guard failed: Redis error - rejecting request (fail-closed)",
    );
    reply.code(503).send({
      error: "Service Unavailable",
      code: "AI_RATE_LIMIT_ERROR",
      message: "AI service rate limiter encountered an error. Please try again later.",
    });
    return false;
  }
}

export async function registerAIRoutes(
  app: FastifyInstance,
  deps: AIRouteDeps,
): Promise<void> {
  const aiContentService = new AIContentGenerationService(deps.aiProxyService);
  const aiAuditService = new AIAuditService(app.prisma as PrismaClient);

  // Create consent enforcement middleware for AI routes
  // AI processing requires explicit user consent for data processing
  const consentEnforcement = createConsentEnforcement({
    prisma: app.prisma as PrismaClient,
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
        moduleId: ModuleId;
        question: string;
        claimIds: string[];
        currentSimulationState: Record<string, unknown>;
        recentAttempts: Array<{
          attemptId: string;
          taskId?: string;
          correct?: boolean;
          confidence?: "low" | "medium" | "high";
          misconceptionId?: string;
        }>;
        misconceptions: string[];
        allowedHelpMode: "hint" | "explain" | "socratic";
        locale?: string;
      };
      const {
        claimIds,
        currentSimulationState,
        recentAttempts,
        misconceptions,
        allowedHelpMode,
      } = req.body as {
        claimIds: string[];
        currentSimulationState: Record<string, unknown>;
        recentAttempts: Array<{
          attemptId: string;
          taskId?: string;
          correct?: boolean;
          confidence?: "low" | "medium" | "high";
          misconceptionId?: string;
        }>;
        misconceptions: string[];
        allowedHelpMode: "hint" | "explain" | "socratic";
      };
      let modelId: string | undefined;
      let success = true;
      let errorMessage: string | undefined;
      let response: unknown = null;

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

        const governance = await buildAIGovernanceMetadata({
          prisma: app.prisma as PrismaClient,
          tenantId,
          userId,
          contentToFilter: question,
          learnerContextScope: moduleId ? "module" : "none",
          promptVersion: "tutorputor-tutor-query-v1",
          modelVersion: modelId || "unknown",
          retrievedContentIds: moduleId ? [String(moduleId)] : [],
        });
        assertAIInteractionAllowed(governance);

        app.log.info(
          {
            tenantId: String(tenantId),
            userId: String(userId),
            moduleId: moduleId ? String(moduleId) : undefined,
          },
          "[AI] Tutor query accepted",
        );

        response = await deps.aiProxyService.handleTutorQuery({
          tenantId,
          userId,
          question,
          moduleId,
          claimIds,
          currentSimulationState,
          recentAttempts,
          misconceptions,
          allowedHelpMode,
          ...(locale ? { locale } : {}),
        });

        if (modelId) {
          void reply.header("x-active-model-id", modelId);
        }

        // Add provenance metadata to response
        const provenance = {
          modelId: modelId || "unknown",
          modelVersion: modelId || "unknown",
          promptVersion: governance.promptVersion,
          safetyFilterResult: governance.safetyFilterResult,
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
        
        // Handle AIServiceUnavailableError with 503 status
        if (error instanceof AIServiceUnavailableError) {
          return reply.status(503).send({
            error: "AI Service Unavailable",
            message: error.message,
            code: "AI_SERVICE_UNAVAILABLE",
            retryable: error.isRetryable,
          });
        }
        
        const contextErrorReply = sendRequestContextError(reply, error);
        if (contextErrorReply) {
          return contextErrorReply;
        }
        throw error;
      } finally {
        // Log AI inference for audit
        const latencyMs = Date.now() - startTime;
        const governance = await buildAIGovernanceMetadata({
          consentState: success ? "granted" : "missing",
          learnerContextScope: moduleId ? "module" : "none",
          promptVersion: "tutorputor-tutor-query-v1",
          modelVersion: modelId || "unknown",
          retrievedContentIds: moduleId ? [String(moduleId)] : [],
          safetyFilterResult: success ? "passed" : "blocked",
          latencyMs,
        });
        const auditPayload = buildAIAuditPayload({
          endpoint: "tutor/query",
          useCase: "tutor",
          governance,
          request: {
            question,
            moduleId,
            claimIds,
            currentSimulationState,
            recentAttempts,
            misconceptions,
            allowedHelpMode,
            locale,
          },
          ...(response ? { response: { modelId } } : {}),
        });
        const failureReason = success ? undefined : errorMessage?.includes("rate limit") ? "rate_limited" : errorMessage?.includes("validation") ? "validation_error" : "service_unavailable";
        const auditEntry = buildAuditEntry({
          req,
          tenantId,
          userId,
          modelId: modelId || "unknown",
          modelVersion: modelId || "unknown",
          endpoint: "tutor/query",
          requestPayload: auditPayload.requestPayload,
          latencyMs,
          success,
          ...(failureReason ? { failureReason } : {}),
          ...(errorMessage ? { errorMessage } : {}),
          ...(auditPayload.responsePayload
            ? { responsePayload: auditPayload.responsePayload }
            : {}),
        });
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
      let response: unknown = null;

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

        // Handle AIServiceUnavailableError with 503 status
        if (error instanceof AIServiceUnavailableError) {
          return reply.status(503).send({
            error: "AI Service Unavailable",
            message: error.message,
            code: "AI_SERVICE_UNAVAILABLE",
            retryable: error.isRetryable,
            questions: [],
          });
        }

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
        const failureReason = success ? undefined : errorMessage?.includes("rate limit") ? "rate_limited" : errorMessage?.includes("validation") ? "validation_error" : "service_unavailable";
        const auditEntry = buildAuditEntry({
          req,
          tenantId,
          userId,
          modelId: "unknown",
          endpoint: "generate-questions",
          requestPayload: JSON.stringify({ moduleId, count, difficulty }),
          latencyMs,
          success,
          ...(failureReason ? { failureReason } : {}),
          ...(errorMessage ? { errorMessage } : {}),
          ...(success && response
            ? { responsePayload: JSON.stringify(response) }
            : {}),
        });
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
      let response: unknown = null;

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

        // Handle AIServiceUnavailableError with 503 status
        if (error instanceof AIServiceUnavailableError) {
          return reply.status(503).send({
            error: "AI Service Unavailable",
            message: error.message,
            code: "AI_SERVICE_UNAVAILABLE",
            retryable: error.isRetryable,
          });
        }

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
        const failureReason = success ? undefined : errorMessage?.includes("rate limit") ? "rate_limited" : errorMessage?.includes("validation") ? "validation_error" : "service_unavailable";
        const auditEntry = buildAuditEntry({
          req,
          tenantId,
          userId,
          modelId: "unknown",
          endpoint: "generate-concept",
          requestPayload: JSON.stringify({ conceptName, domain }),
          latencyMs,
          success,
          ...(failureReason ? { failureReason } : {}),
          ...(errorMessage ? { errorMessage } : {}),
          ...(success && response
            ? { responsePayload: JSON.stringify(response) }
            : {}),
        });
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
      let response: unknown = null;

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

        // Handle AIServiceUnavailableError with 503 status
        if (error instanceof AIServiceUnavailableError) {
          return reply.status(503).send({
            error: "AI Service Unavailable",
            message: error.message,
            code: "AI_SERVICE_UNAVAILABLE",
            retryable: error.isRetryable,
          });
        }

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
        const auditEntry = buildAuditEntry({
          req,
          tenantId,
          userId,
          modelId: "unknown",
          endpoint: "generate-simulation",
          requestPayload: JSON.stringify({ description, conceptName, domain }),
          latencyMs,
          success,
          ...(errorMessage ? { errorMessage } : {}),
          ...(success && response
            ? { responsePayload: JSON.stringify(response) }
            : {}),
        });
        void aiAuditService.logInference(auditEntry);
      }
    },
  );

  // AI health check endpoint - relative to module prefix
  app.get("/health", async (request, reply) => {
    if (!deps.aiHealthCheckService) {
      return reply.status(503).send({
        healthy: false,
        error: "Health check service not configured",
      });
    }
    const healthStatus = deps.aiHealthCheckService.getHealthStatus();
    return reply.send({
      healthy: healthStatus.every(s => s.healthy),
      services: healthStatus,
    });
  });

  // AI cache stats endpoint - relative to module prefix
  app.get("/cache/stats", async (request, reply) => {
    if (!deps.aiCacheService) {
      return reply.status(503).send({
        error: "Cache service not configured",
      });
    }
    const stats = deps.aiCacheService.getStats();
    return reply.send(stats);
  });

  app.log.info(
    "[AI] Routes registered: /tutor/query, /generate-questions, /generate-concept, /generate-simulation, /health, /cache/stats",
  );
}
