import type { FastifyPluginAsync } from "fastify";
import { TenantId } from "@ghatana/tutorputor-contracts/v1";
import { createSsoService } from "./service.js";
import { getTenantId } from "../../core/http/requestContext.js";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";

/**
 * Authentication module (OIDC/SSO).
 *
 * @doc.type module
 * @doc.purpose Authentication and SSO routes
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const authModule: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as TutorPrismaClient;
  const jwt = app.jwt; // fastify-jwt used in platform setup

  // Dependencies
  const ssoService = createSsoService({
    prisma,
    baseUrl: process.env.API_BASE_URL || "http://localhost:3000",
    generateAccessToken: (userId, tenantId) => {
      return jwt.sign({ sub: userId, tenantId }, { expiresIn: "1h" });
    },
    generateRefreshToken: (userId, tenantId) => {
      return jwt.sign(
        { sub: userId, tenantId, type: "refresh" },
        { expiresIn: "7d" },
      );
    },
  });

  app.log.info("Auth module initialized with SSO support");

  app.get("/health", async () => ({
    module: "auth",
    status: "active",
  }));

  // =========================================================================
  // Current User Endpoint
  // =========================================================================

  /**
   * Get current authenticated user info.
   * Returns user profile from JWT token.
   */
  app.get(
    "/me",
    {
      schema: {
        description: "Get current user info",
        tags: ["Auth"],
        response: {
          200: {
            type: "object",
            properties: {
              id: { type: "string" },
              email: { type: "string" },
              displayName: { type: "string" },
              role: { type: "string" },
              tenantId: { type: "string" },
            },
          },
          401: {
            type: "object",
            properties: {
              error: { type: "string" },
            },
          },
        },
      },
    },
    async (req, reply) => {
      try {
        // Verify JWT token from Authorization header
        const authHeader = req.headers.authorization;
        if (!authHeader?.startsWith("Bearer ")) {
          return reply
            .code(401)
            .send({ error: "Authorization token required" });
        }

        const token = authHeader.slice(7);
        const decoded = jwt.verify(token) as {
          sub: string;
          tenantId: string;
          email?: string;
          name?: string;
          role?: string;
        };

        // Optionally fetch full user from database
        const user = await prisma.user.findUnique({
          where: { id: decoded.sub },
          select: {
            id: true,
            email: true,
            displayName: true,
            role: true,
            tenantId: true,
          },
        });

        if (user) {
          return reply.send(user);
        }

        // Return token claims if user not in DB (e.g., first login)
        return reply.send({
          id: decoded.sub,
          email: decoded.email || "unknown",
          displayName: decoded.name || "User",
          role: decoded.role || "student",
          tenantId: decoded.tenantId || "default",
        });
      } catch (err) {
        return reply.code(401).send({ error: "Invalid or expired token" });
      }
    },
  );

  // =========================================================================
  // SSO Public Routes
  // =========================================================================

  /**
   * List providers for a tenant (by slug) to render login page.
   * Public endpoint.
   */
  app.get("/sso/providers", async (req, reply) => {
    const { tenantSlug } = req.query as { tenantSlug?: string };
    if (!tenantSlug)
      return reply.code(400).send({ error: "tenantSlug is required" });

    const providers = await ssoService.getLoginProviders({ tenantSlug });
    return reply.send(providers);
  });

  /**
   * Initiate SSO Login
   */
  app.get("/sso/login/:providerId", async (req, reply) => {
    const { providerId } = req.params as { providerId: string };
    const { redirect_uri, state } = req.query as {
      redirect_uri?: string;
      state?: string;
    };

    let tenantId: TenantId;
    try {
      tenantId = getTenantId(req as any) as TenantId;
    } catch {
      return reply
        .code(400)
        .send({ error: "Tenant Context Required (x-tenant-id)" });
    }

    try {
      const result = await ssoService.initiateLogin({
        tenantId,
        providerId,
        redirectUri: redirect_uri,
      });
      // If redirectUrl is returned, we redirect the user
      if (result.redirectUrl) {
        return reply.redirect(result.redirectUrl);
      }
      return reply.send(result);
    } catch (e: any) {
      return reply.code(400).send({ error: e.message });
    }
  });

  /**
   * SSO Callback
   */
  app.get("/sso/callback/:providerId", async (req, reply) => {
    const { providerId } = req.params as { providerId: string };
    const { code, state } = req.query as { code: string; state: string };
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const tenantId = (state?.split(":")[0] || "default") as TenantId;

    try {
      const result = await ssoService.handleCallback({
        tenantId,
        providerId,
        code,
        state,
      });

      const target = (result as any).redirectUri || "/dashboard";
      const separator = target.includes("?") ? "&" : "?";
      return reply.redirect(
        `${target}${separator}accessToken=${result.accessToken}&refreshToken=${result.refreshToken}`,
      );
    } catch (e: any) {
      return reply
        .code(400)
        .send({ error: "Login Failed", details: e.message });
    }
  });
};
