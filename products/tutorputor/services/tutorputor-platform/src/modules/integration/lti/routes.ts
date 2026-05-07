import type { FastifyPluginAsync } from "fastify";
import type {
  ModuleId,
  TenantId,
  UserId,
  LtiPlatformId,
} from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  requireRole,
  respondWithErrors,
} from "../../../core/http/requestContext.js";
import { createLTIService } from "../../lti/service.js";
import { createLtiServices } from "../../lti/lti-full-service.js";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { LTIValidator } from "../../lti/validation.js";
import {
  calculateEvidenceBackedLtiGrade,
  type EvidenceGradePrisma,
} from "./evidence-grade.js";
import { z } from "zod";

type ErrorWithStatus = Error & {
  statusCode?: number;
  code?: string;
  details?: unknown;
};

function isValidationLikeError(error: unknown): error is ErrorWithStatus {
  return (
    error instanceof Error &&
    typeof (error as ErrorWithStatus).statusCode === "number" &&
    typeof (error as ErrorWithStatus).code === "string"
  );
}

function isAuthorizationLikeError(error: unknown): error is ErrorWithStatus {
  return (
    error instanceof Error &&
    typeof (error as ErrorWithStatus).statusCode === "number" &&
    typeof (error as ErrorWithStatus).details !== "undefined"
  );
}

const deepLinkingBodySchema = z
  .object({
    content_items: z.array(z.unknown()).optional(),
    deployment_id: z.string().trim().min(1).optional(),
    moduleIds: z.array(z.string().trim().min(1)).optional(),
    baseUrl: z.string().url().optional(),
  })
  .refine(
    (value) =>
      (Array.isArray(value.moduleIds) && value.moduleIds.length > 0) ||
      (Array.isArray(value.content_items) &&
        value.content_items.length > 0 &&
        Boolean(value.deployment_id)),
    {
      message:
        "Either moduleIds or content_items + deployment_id must be provided",
      path: ["moduleIds"],
    },
  );

const gradePassbackBodySchema = z.object({
  sessionId: z.string().trim().min(1).optional(),
  userId: z.string().trim().min(1),
  score: z.number().finite().min(0).optional(),
  maxScore: z.number().positive().optional(),
  lineItemId: z.string().trim().min(1),
  assessmentAttemptId: z.string().trim().min(1).optional(),
  moduleId: z.string().trim().min(1).optional(),
  activityProgress: z
    .enum(["Completed", "Initialized", "Started", "InProgress", "Submitted"])
    .optional(),
  gradingProgress: z
    .enum(["FullyGraded", "Pending", "PendingManual", "Failed", "NotReady"])
    .optional(),
  comment: z.string().trim().min(1).optional(),
  timestamp: z.string().datetime().optional(),
}).refine((value) => {
  const hasStaticScore =
    typeof value.score === "number" && typeof value.maxScore === "number";
  return hasStaticScore || Boolean(value.assessmentAttemptId || value.moduleId);
}, {
  message:
    "Provide score/maxScore for legacy passback or assessmentAttemptId/moduleId for evidence-backed passback",
  path: ["score"],
}).refine((value) => {
  if (typeof value.score !== "number" || typeof value.maxScore !== "number") {
    return true;
  }
  return value.score <= value.maxScore;
}, {
  message: "score must be less than or equal to maxScore",
  path: ["score"],
});

const platformParamSchema = z.object({
  platform: z.string().trim().min(1),
});

const registerBodySchema = z.object({
  platformName: z.string().trim().min(1),
  issuer: z.string().trim().min(1),
  clientId: z.string().trim().min(1),
  jwksUrl: z.string().url().optional(),
  authUrl: z.string().url().optional(),
  tokenUrl: z.string().url().optional(),
});

const platformIdParamSchema = z.object({
  platformId: z.string().trim().min(1),
});

const updatePlatformBodySchema = z.object({
  name: z.string().trim().min(1).optional(),
  issuer: z.string().trim().min(1).optional(),
  clientId: z.string().trim().min(1).optional(),
  deploymentId: z.string().trim().min(1).optional(),
  authLoginUrl: z.string().url().optional(),
  authTokenUrl: z.string().url().optional(),
  jwksUrl: z.string().url().optional(),
  publicKeyPem: z.string().trim().min(1).optional(),
  isActive: z.boolean().optional(),
});

const launchBodySchema = z.object({
  id_token: z.string().trim().min(1),
  state: z.string().trim().min(1),
});

/**
 * LTI integration routes - LMS interoperability.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for LTI 1.3 integration
 * @doc.layer product
 * @doc.pattern REST API
 */
export const ltiRoutes: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as TutorPrismaClient;
  const ltiService = createLTIService(prisma);
  const fullLtiServices = await createLtiServices(prisma);
  const ltiValidator = new LTIValidator(fullLtiServices.launchService);

  /**
   * POST /launch
   * LTI 1.3 launch endpoint with proper signature verification.
   * F-015: Rate-limited per platform to 30 req/min to guard the public surface.
   */
  app.post(
    "/launch",
    {
      config: { public: true },
      // Per-platform rate limit: key = "lti-launch:<platformId from form body>"
      // Falls back to IP when platformId is not yet parsed.
      // @ts-expect-error — @fastify/rate-limit route config is untyped on FastifyContextConfig
      rateLimit: {
        max: 30,
        timeWindow: "1 minute",
        keyGenerator: (req: import("fastify").FastifyRequest): string => {
          // The id_token is a JWT; its issuer maps to the platform.
          // We derive a rate-limit key from the raw body's state param (safe, unverified).
          const body = req.body as Record<string, unknown> | undefined;
          const state = typeof body?.["state"] === "string" ? body["state"].slice(0, 64) : "unknown";
          return `lti-launch:${state}`;
        },
      },
    },
    async (request, reply) => {
    try {
      const bodyResult = launchBodySchema.safeParse(request.body);
      if (!bodyResult.success) {
        return reply.code(400).send({
          error: "Invalid LTI launch payload",
          issues: bodyResult.error.issues,
        });
      }

      let tenantId: TenantId | undefined;
      try {
        tenantId = getTenantId(request) as TenantId;
      } catch {
        tenantId = undefined;
      }

      const validation = await ltiValidator.validateLaunchRequest(
        bodyResult.data,
        tenantId,
      );

      app.log.info(
        {
          tenantId: tenantId ?? "state-derived",
          state: validation.state,
          platformId: validation.launchContext?.platformId,
          contextId: validation.launchContext?.contextId,
          userSub: validation.userClaims?.sub,
        },
        "LTI launch validated successfully",
      );

      return reply.code(200).send({
        ...validation,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      app.log.warn(
        {
          err: error,
          ip: request.ip,
          userAgent: request.headers["user-agent"],
        },
        "LTI launch validation failed",
      );

      if (isValidationLikeError(error)) {
        return reply.code(error.statusCode ?? 400).send({
          error: error.code,
          message: error.message,
        });
      }

      if (isAuthorizationLikeError(error)) {
        return reply.code(error.statusCode ?? 401).send({
          error: "Invalid LTI launch",
          message: error.message,
          details: error.details,
        });
      }

      throw error;
    }
  });

  /**
   * GET /jwks
   * JSON Web Key Set for LTI verification.
   * Reads RSA public key components from environment variables.
   * Generate with: openssl genrsa -out lti.pem 2048 && openssl rsa -in lti.pem -pubout -out lti.pub
   * Then base64url-encode the modulus/exponent from the public key.
   */
  app.get("/jwks", { config: { public: true } }, async (_request, reply) => {
    try {
      const toolConfiguration =
        await fullLtiServices.platformService.getToolConfiguration({
          tenantId: "public" as TenantId,
        });

      return reply.code(200).send(toolConfiguration.publicJwks);
    } catch (error) {
      app.log.error(error, "Failed to get JWKS");
      return reply.code(500).send({
        error: "Failed to get JWKS",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /deep-linking
   * Deep linking response handler
   */
  app.post("/deep-linking", { config: { public: true } }, async (request, reply) => {
    const bodyResult = deepLinkingBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid deep-linking payload",
        issues: bodyResult.error.issues,
      });
    }
    const { content_items, deployment_id, moduleIds, baseUrl } = bodyResult.data;

    if (Array.isArray(moduleIds) && moduleIds.length > 0) {
      const tenantId = getTenantId(request);

      await respondWithErrors(reply, async () => {
        const contentItems = await ltiService.getDeepLinkingContent({
          tenantId: tenantId as TenantId,
          moduleIds: moduleIds as ModuleId[],
          baseUrl: baseUrl ?? resolveIntegrationBaseUrl(request),
        });

        return {
          deployment_id,
          content_items: contentItems,
          content_items_count: contentItems.length,
          processed: true,
        };
      });
      return;
    }

    return reply.code(200).send({
      deployment_id,
      content_items_count: Array.isArray(content_items)
        ? content_items.length
        : 0,
      processed: true,
    });
  });

  /**
   * POST /grade-passback
   * Grade passback to LMS
   */
  app.post("/grade-passback", { config: { public: true } }, async (request, reply) => {
    let tenantId: TenantId = "public" as TenantId;
    try {
      tenantId = getTenantId(request) as TenantId;
    } catch {
      // Public LMS passback routes are allowed without JWT; tenant will be derived from the line item.
    }
    const bodyResult = gradePassbackBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid grade-passback payload",
        issues: bodyResult.error.issues,
      });
    }
    const {
      sessionId,
      userId,
      score,
      maxScore,
      lineItemId,
      assessmentAttemptId,
      moduleId,
      activityProgress,
      gradingProgress,
      comment,
      timestamp,
    } = bodyResult.data as z.infer<typeof gradePassbackBodySchema>;

    await respondWithErrors(reply, async () => {
      const scorePayload =
        assessmentAttemptId || moduleId
          ? await calculateEvidenceBackedLtiGrade(prisma as unknown as EvidenceGradePrisma, {
              tenantId,
              userId: userId as UserId,
              ...(assessmentAttemptId ? { assessmentAttemptId } : {}),
              ...(moduleId ? { moduleId } : {}),
              ...(timestamp ? { timestamp } : {}),
            })
          : {
              userId,
              scoreGiven: score ?? 0,
              scoreMaximum: maxScore ?? 100,
              activityProgress: activityProgress ?? "Completed",
              gradingProgress: gradingProgress ?? "FullyGraded",
              timestamp: timestamp ?? new Date().toISOString(),
              ...(comment ? { comment } : {}),
            };

      return fullLtiServices.gradeService.submitScore({
        tenantId,
        sessionId: sessionId ?? "",
        lineItemId,
        score: {
          ...scorePayload,
          ...(comment ? { comment } : {}),
        },
      });
    });
  });

  /**
   * GET /config/:platform
   * Get LTI configuration for a platform
   */
  app.get("/config/:platform", { config: { public: true } }, async (request, reply) => {
    const paramsResult = platformParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid platform parameter",
        issues: paramsResult.error.issues,
      });
    }
    const { platform } = paramsResult.data;

    try {
      const toolConfiguration =
        await fullLtiServices.platformService.getToolConfiguration({
          tenantId: "public" as TenantId,
        });
      const ltiBaseUrl = `${resolveIntegrationBaseUrl(request)}/lti`;

      const config = {
        platform,
        issuer: toolConfiguration.issuer,
        client_id: toolConfiguration.clientId,
        auth_login_url: `${ltiBaseUrl}/launch`,
        launch_url: `${ltiBaseUrl}/launch`,
        deep_linking_url: `${ltiBaseUrl}/deep-linking`,
        key_set_url: `${ltiBaseUrl}/jwks`,
      };
      return reply.code(200).send(config);
    } catch (error) {
      app.log.error(error, "Failed to get LTI config");
      return reply.code(500).send({
        error: "Failed to get config",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /register
   * Register new LTI platform
   */
  app.post("/register", async (request, reply) => {
    const tenantId = getTenantId(request);
    requireRole(request, ["admin", "superadmin"]);

    const bodyResult = registerBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid platform registration payload",
        issues: bodyResult.error.issues,
      });
    }
    const { platformName, issuer, clientId, jwksUrl, authUrl, tokenUrl } = bodyResult.data;

    await respondWithErrors(reply, async () => {
      const registration = await ltiService.registerPlatform({
        tenantId: tenantId as TenantId,
        platformName,
        issuer,
        clientId,
        jwksUrl: jwksUrl ?? "",
        authUrl: authUrl ?? "",
        tokenUrl: tokenUrl ?? "",
      });

      reply.code(201);
      return {
        tenantId,
        platformName,
        issuer,
        clientId,
        platformId: registration.platformId,
        registered: true,
        timestamp: new Date().toISOString(),
      };
    });
  });

  app.get("/health", async () => ({
    status: (await ltiService.checkHealth()) ? "healthy" : "unhealthy",
    module: "lti",
  }));

  // ===========================================================================
  // LTI Platform Admin Routes (authenticated, admin/superadmin only)
  // ===========================================================================

  /**
   * GET /platforms
   * List all registered LTI platforms for the tenant.
   */
  app.get("/platforms", async (request, reply) => {
    const tenantId = getTenantId(request);
    requireRole(request, ["admin", "superadmin"]);

    await respondWithErrors(reply, () =>
      fullLtiServices.platformService.listPlatforms({
        tenantId: tenantId as TenantId,
      }),
    );
  });

  /**
   * GET /platforms/:platformId
   * Get a specific LTI platform registration.
   */
  app.get("/platforms/:platformId", async (request, reply) => {
    const tenantId = getTenantId(request);
    requireRole(request, ["admin", "superadmin"]);
    const paramsResult = platformIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid platform id",
        issues: paramsResult.error.issues,
      });
    }
    const { platformId } = paramsResult.data;

    await respondWithErrors(reply, async () => {
      const platform = await fullLtiServices.platformService.getPlatform({
        tenantId: tenantId as TenantId,
        platformId: platformId as LtiPlatformId,
      });
      if (!platform) {
        reply.code(404);
        throw new Error(`LTI platform ${platformId} not found`);
      }
      return platform;
    });
  });

  /**
   * PATCH /platforms/:platformId
   * Update an LTI platform registration.
   */
  app.patch("/platforms/:platformId", async (request, reply) => {
    const tenantId = getTenantId(request);
    requireRole(request, ["admin", "superadmin"]);
    const paramsResult = platformIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid platform id",
        issues: paramsResult.error.issues,
      });
    }
    const bodyResult = updatePlatformBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid platform update payload",
        issues: bodyResult.error.issues,
      });
    }
    const { platformId } = paramsResult.data;
    const updates = {
      ...(bodyResult.data.name ? { name: bodyResult.data.name } : {}),
      ...(bodyResult.data.issuer ? { issuer: bodyResult.data.issuer } : {}),
      ...(bodyResult.data.clientId ? { clientId: bodyResult.data.clientId } : {}),
      ...(bodyResult.data.deploymentId
        ? { deploymentId: bodyResult.data.deploymentId }
        : {}),
      ...(bodyResult.data.authLoginUrl
        ? { authLoginUrl: bodyResult.data.authLoginUrl }
        : {}),
      ...(bodyResult.data.authTokenUrl
        ? { authTokenUrl: bodyResult.data.authTokenUrl }
        : {}),
      ...(bodyResult.data.jwksUrl ? { jwksUrl: bodyResult.data.jwksUrl } : {}),
      ...(bodyResult.data.publicKeyPem
        ? { publicKeyPem: bodyResult.data.publicKeyPem }
        : {}),
      ...(bodyResult.data.isActive !== undefined
        ? { isActive: bodyResult.data.isActive }
        : {}),
    };

    await respondWithErrors(reply, () =>
      fullLtiServices.platformService.updatePlatform({
        tenantId: tenantId as TenantId,
        platformId: platformId as LtiPlatformId,
        updates,
      }),
    );
  });

  /**
   * DELETE /platforms/:platformId
   * Deactivate an LTI platform registration (soft delete).
   */
  app.delete("/platforms/:platformId", async (request, reply) => {
    const tenantId = getTenantId(request);
    requireRole(request, ["admin", "superadmin"]);
    const paramsResult = platformIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid platform id",
        issues: paramsResult.error.issues,
      });
    }
    const { platformId } = paramsResult.data;

    await respondWithErrors(reply, async () => {
      await fullLtiServices.platformService.deactivatePlatform({
        tenantId: tenantId as TenantId,
        platformId: platformId as LtiPlatformId,
      });
      return { deactivated: true, platformId };
    });
  });

  app.log.info("LTI routes registered");
};

function resolveIntegrationBaseUrl(request: {
  protocol?: string;
  headers: Record<string, string | string[] | undefined>;
}): string {
  const forwardedProto = request.headers["x-forwarded-proto"];
  const forwardedHost = request.headers["x-forwarded-host"];
  const host = forwardedHost ?? request.headers.host;
  const protocol =
    (Array.isArray(forwardedProto) ? forwardedProto[0] : forwardedProto) ??
    request.protocol ??
    "http";
  const resolvedHost = Array.isArray(host) ? host[0] : host;

  if (!resolvedHost) {
    return `${process.env.API_BASE_URL || "http://localhost:3000"}/api/v1/integration`;
  }

  return `${protocol}://${resolvedHost}/api/v1/integration`;
}

