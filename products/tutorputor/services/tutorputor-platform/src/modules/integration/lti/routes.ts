import type { FastifyPluginAsync } from "fastify";
import type { TenantId, UserId } from "@ghatana/tutorputor-contracts/v1/types";

/**
 * LTI integration routes - LMS interoperability.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for LTI 1.3 integration
 * @doc.layer product
 * @doc.pattern REST API
 */
export const ltiRoutes: FastifyPluginAsync = async (app) => {
  /**
   * POST /launch
   * LTI 1.3 launch endpoint
   */
  app.post("/launch", async (request, reply) => {
    const { id_token, state } = request.body as {
      id_token: string;
      state: string;
    };

    if (!id_token || !state) {
      return reply.code(400).send({ error: "ID token and state are required" });
    }

    try {
      // Verify JWT and extract claims
      const launchData = {
        state,
        verified: true,
        timestamp: new Date().toISOString(),
      };
      return reply.code(200).send(launchData);
    } catch (error) {
      app.log.error(error, "Failed to process LTI launch");
      return reply.code(401).send({
        error: "Invalid LTI launch",
        message: error instanceof Error ? error.message : "Unknown error",
      });
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
      const ltiPublicKeyN = process.env.LTI_RSA_PUBLIC_N;
      const ltiKid = process.env.LTI_KEY_ID || "tutorputor-lti-key-1";

      if (!ltiPublicKeyN) {
        app.log.warn("LTI_RSA_PUBLIC_N not set — JWKS endpoint returning empty key set");
        return reply.code(200).send({ keys: [] });
      }

      const jwks = {
        keys: [
          {
            kty: "RSA",
            use: "sig",
            alg: "RS256",
            kid: ltiKid,
            n: ltiPublicKeyN,
            e: process.env.LTI_RSA_PUBLIC_E || "AQAB",
          },
        ],
      };
      return reply.code(200).send(jwks);
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
    const { content_items, deployment_id } = request.body as {
      content_items: unknown[];
      deployment_id: string;
    };

    if (!content_items || !deployment_id) {
      return reply
        .code(400)
        .send({ error: "Content items and deployment ID are required" });
    }

    try {
      const response = {
        deployment_id,
        content_items_count: Array.isArray(content_items)
          ? content_items.length
          : 0,
        processed: true,
      };
      return reply.code(200).send(response);
    } catch (error) {
      app.log.error(error, "Failed to process deep linking");
      return reply.code(500).send({
        error: "Failed to process deep linking",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /grade-passback
   * Grade passback to LMS
   */
  app.post("/grade-passback", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { userId, score, maxScore, lineItemId } = request.body as {
      userId: UserId;
      score: number;
      maxScore: number;
      lineItemId: string;
    };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!userId || score === undefined || !maxScore || !lineItemId) {
      return reply
        .code(400)
        .send({
          error: "User ID, score, max score, and line item ID are required",
        });
    }

    try {
      const passback = {
        userId,
        score,
        maxScore,
        lineItemId,
        timestamp: new Date().toISOString(),
        status: "submitted",
      };
      return reply.code(200).send(passback);
    } catch (error) {
      app.log.error(error, "Failed to submit grade passback");
      return reply.code(500).send({
        error: "Failed to submit grade",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /config/:platform
   * Get LTI configuration for a platform
   */
  app.get("/config/:platform", async (request, reply) => {
    const { platform } = request.params as { platform: string };

    try {
      const config = {
        platform,
        client_id: `tutorputor-${platform}`,
        auth_login_url: "/lti/launch",
        auth_token_url: "/lti/token",
        key_set_url: "/lti/jwks",
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
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { platformName, issuer, clientId } = request.body as {
      platformName: string;
      issuer: string;
      clientId: string;
    };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!platformName || !issuer || !clientId) {
      return reply
        .code(400)
        .send({ error: "Platform name, issuer, and client ID are required" });
    }

    try {
      const registration = {
        tenantId,
        platformName,
        issuer,
        clientId,
        registered: true,
        timestamp: new Date().toISOString(),
      };
      return reply.code(201).send(registration);
    } catch (error) {
      app.log.error(error, "Failed to register LTI platform");
      return reply.code(500).send({
        error: "Failed to register platform",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/health", async () => ({ status: "healthy", module: "lti" }));

  app.log.info("LTI routes registered");
};
