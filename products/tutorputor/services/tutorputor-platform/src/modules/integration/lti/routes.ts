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
import { AuthorizationError, ValidationError } from "@tutorputor/core";
import { LTIValidator } from "../../lti/validation.js";

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
   * LTI 1.3 launch endpoint with proper signature verification
   */
  app.post("/launch", async (request, reply) => {
    try {
      const validation = await ltiValidator.validateLaunchRequest(
        request.body,
        "public" as TenantId,
      );

      app.log.info(
        {
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

      if (error instanceof ValidationError) {
        return reply.code(error.statusCode).send({
          error: error.code,
          message: error.message,
        });
      }

      if (error instanceof AuthorizationError) {
        return reply.code(error.statusCode).send({
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
  app.get("/jwks", async (request, reply) => {
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
  app.post("/deep-linking", async (request, reply) => {
    const { content_items, deployment_id, moduleIds, baseUrl } =
      request.body as {
        content_items: any[];
        deployment_id: string;
        moduleIds?: string[];
        baseUrl?: string;
      };

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

    if (!content_items || !deployment_id) {
      return reply.code(400).send({
        error:
          "Either moduleIds or content items and deployment ID are required",
      });
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
  app.post("/grade-passback", async (request, reply) => {
    let tenantId: TenantId = "public" as TenantId;
    try {
      tenantId = getTenantId(request) as TenantId;
    } catch {
      // Public LMS passback routes are allowed without JWT; tenant will be derived from the line item.
    }
    const {
      sessionId,
      userId,
      score,
      maxScore,
      lineItemId,
      activityProgress,
      gradingProgress,
      comment,
      timestamp,
    } = request.body as {
      sessionId?: string;
      userId: UserId;
      score: number;
      maxScore: number;
      lineItemId: string;
      activityProgress?: string;
      gradingProgress?: string;
      comment?: string;
      timestamp?: string;
    };

    if (!userId || score === undefined || !maxScore || !lineItemId) {
      return reply.code(400).send({
        error: "User ID, score, max score, and line item ID are required",
      });
    }

    const validActivityProgress:
      | "Completed"
      | "Initialized"
      | "Started"
      | "InProgress"
      | "Submitted" = [
      "Completed",
      "Initialized",
      "Started",
      "InProgress",
      "Submitted",
    ].includes(activityProgress || "")
      ? (activityProgress as
          | "Completed"
          | "Initialized"
          | "Started"
          | "InProgress"
          | "Submitted")
      : "Completed";
    const validGradingProgress:
      | "FullyGraded"
      | "Pending"
      | "PendingManual"
      | "Failed"
      | "NotReady" = [
      "FullyGraded",
      "Pending",
      "PendingManual",
      "Failed",
      "NotReady",
    ].includes(gradingProgress || "")
      ? (gradingProgress as
          | "FullyGraded"
          | "Pending"
          | "PendingManual"
          | "Failed"
          | "NotReady")
      : "FullyGraded";

    await respondWithErrors(reply, () =>
      fullLtiServices.gradeService.submitScore({
        tenantId,
        sessionId: sessionId ?? "",
        lineItemId,
        score: {
          userId,
          scoreGiven: score,
          scoreMaximum: maxScore,
          activityProgress: validActivityProgress,
          gradingProgress: validGradingProgress,
          timestamp: timestamp ?? new Date().toISOString(),
          ...(comment ? { comment } : {}),
        },
      }),
    );
  });

  /**
   * GET /config/:platform
   * Get LTI configuration for a platform
   */
  app.get("/config/:platform", async (request, reply) => {
    const { platform } = request.params as { platform: string };

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

    const { platformName, issuer, clientId, jwksUrl, authUrl, tokenUrl } =
      request.body as {
        platformName: string;
        issuer: string;
        clientId: string;
        jwksUrl?: string;
        authUrl?: string;
        tokenUrl?: string;
      };

    if (!platformName || !issuer || !clientId) {
      return reply
        .code(400)
        .send({ error: "Platform name, issuer, and client ID are required" });
    }

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
    const { platformId } = request.params as { platformId: string };

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
    const { platformId } = request.params as { platformId: string };
    const updates = request.body as {
      name?: string;
      issuer?: string;
      clientId?: string;
      deploymentId?: string;
      authLoginUrl?: string;
      authTokenUrl?: string;
      jwksUrl?: string;
      publicKeyPem?: string;
      isActive?: boolean;
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
    const { platformId } = request.params as { platformId: string };

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
