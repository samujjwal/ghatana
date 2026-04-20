import type { FastifyPluginAsync } from "fastify";
import type { TenantId } from "@tutorputor/contracts/v1";
import crypto from "crypto";
import { createSsoService } from "./service.js";
import { getTenantId } from "../../core/http/requestContext.js";
import type { TutorPrismaClient } from "@tutorputor/core/db";

interface AuthJwtClaims {
  sub: string;
  tenantId: string;
  role?: string;
  email?: string;
  name?: string;
  type?: string;
  jti?: string;
}

interface SessionUser {
  id: string;
  email: string;
  displayName: string;
  role: string;
  tenantId: string;
}

interface RefreshSessionRecord {
  userId: string;
  tenantId: string;
  jti: string;
}

type RedisLike = {
  get: (key: string) => Promise<string | null>;
  set: (
    key: string,
    value: string,
    mode: 'EX',
    ttlSeconds: number,
  ) => Promise<unknown>;
  del: (key: string) => Promise<number>;
};

type SsoCallbackResultWithRedirect = {
  success: boolean;
  user?: {
    id: string;
    email: string;
    displayName: string;
    role: string;
  };
  accessToken?: string;
  refreshToken?: string;
  redirectUri?: string;
};

const REFRESH_SESSION_PREFIX = "auth:refresh-session:";
const REFRESH_SESSION_TTL_SECONDS = 7 * 24 * 60 * 60;

function getRefreshSessionKey(jti: string): string {
  return `${REFRESH_SESSION_PREFIX}${jti}`;
}

function hashToken(token: string): string {
  return crypto.createHash("sha256").update(token).digest("hex");
}

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
  const redis = app.redis as unknown as RedisLike;
  const jwt = (app as typeof app & { jwt: { sign: (payload: object, options?: { expiresIn?: string }) => string; verify: (token: string) => AuthJwtClaims } }).jwt;

  async function fetchSessionUser(claims: AuthJwtClaims): Promise<SessionUser> {
    const user = await prisma.user.findUnique({
      where: { id: claims.sub },
      select: {
        id: true,
        email: true,
        displayName: true,
        role: true,
        tenantId: true,
      },
    });

    if (user) {
      return user;
    }

    return {
      id: claims.sub,
      email: claims.email || "unknown",
      displayName: claims.name || "User",
      role: claims.role || "student",
      tenantId: claims.tenantId || "default",
    };
  }

  async function storeRefreshSession(
    refreshToken: string,
    claims: AuthJwtClaims,
  ): Promise<void> {
    if (!claims.jti) {
      return;
    }

    const record: RefreshSessionRecord = {
      userId: claims.sub,
      tenantId: claims.tenantId,
      jti: claims.jti,
    };

    await redis.set(
      getRefreshSessionKey(claims.jti),
      JSON.stringify({ ...record, tokenHash: hashToken(refreshToken) }),
      'EX',
      REFRESH_SESSION_TTL_SECONDS,
    );
  }

  async function readRefreshSession(jti: string): Promise<
    (RefreshSessionRecord & { tokenHash: string }) | null
  > {
    const raw = await redis.get(getRefreshSessionKey(jti));
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as RefreshSessionRecord & { tokenHash: string };
    } catch {
      return null;
    }
  }

  async function deleteRefreshSession(jti: string | undefined): Promise<void> {
    if (!jti) {
      return;
    }

    await redis.del(getRefreshSessionKey(jti));
  }

  function signAccessToken(user: SessionUser): string {
    return jwt.sign(
      {
        sub: user.id,
        tenantId: user.tenantId,
        role: user.role,
        email: user.email,
        name: user.displayName,
      },
      { expiresIn: "1h" },
    );
  }

  function signRefreshToken(user: SessionUser): string {
    return jwt.sign(
      {
        sub: user.id,
        tenantId: user.tenantId,
        role: user.role,
        type: "refresh",
        jti: crypto.randomUUID(),
      },
      { expiresIn: "7d" },
    );
  }

  // Dependencies
  const ssoService = createSsoService({
    prisma,
    baseUrl: process.env.API_BASE_URL || "http://localhost:3000",
    redis: {
      get: (key) => redis.get(key),
      set: (key, value) => redis.set(key, value, 'EX', REFRESH_SESSION_TTL_SECONDS),
      expire: async (key, seconds) => {
        const value = await redis.get(key);
        if (value == null) {
          return 0;
        }

        await redis.set(key, value, 'EX', seconds);
        return 1;
      },
      del: (key) => redis.del(key),
    },
    generateAccessToken: (userId, tenantId) => {
      return jwt.sign({ sub: userId, tenantId }, { expiresIn: "1h" });
    },
    generateRefreshToken: (userId, tenantId) => {
      return jwt.sign(
        { sub: userId, tenantId, type: "refresh", jti: crypto.randomUUID() },
        { expiresIn: "7d" },
      );
    },
    onUserAuthenticated: async ({ tenantId, userId, isNewUser }) => {
      if (!isNewUser) {
        return;
      }

      const learnerProfileService = app.learnerProfileService;
      if (!learnerProfileService) {
        throw new Error(
          "Learner profile service unavailable during SSO provisioning",
        );
      }

      await learnerProfileService.getOrCreateProfile(tenantId, userId);
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
        const claims = (req as typeof req & { user?: AuthJwtClaims }).user;
        if (!claims?.sub || !claims.tenantId) {
          return reply.code(401).send({ error: "Authorization token required" });
        }

        const user = await fetchSessionUser(claims);
        return reply.send(user);
      } catch {
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
    return reply.send({ providers });
  });

  /**
   * Initiate SSO Login
   */
  app.get("/sso/login/:providerId", async (req, reply) => {
    const { providerId } = req.params as { providerId: string };
    const { redirect_uri, tenantSlug } = req.query as {
      redirect_uri?: string;
      tenantSlug?: string;
    };

    let tenantId: TenantId;
    try {
      tenantId = getTenantId(req) as TenantId;
    } catch {
      if (tenantSlug) {
        const tenant = await prisma.tenant.findFirst({
          where: { subdomain: tenantSlug },
          select: { id: true },
        });

        if (tenant?.id) {
          tenantId = tenant.id as TenantId;
        } else {
          const provider = await prisma.identityProvider.findUnique({
            where: { id: providerId },
            select: { tenantId: true },
          });

          if (!provider?.tenantId) {
            return reply.code(400).send({ error: "Tenant context could not be resolved" });
          }

          tenantId = provider.tenantId as TenantId;
        }
      } else {
        const provider = await prisma.identityProvider.findUnique({
          where: { id: providerId },
          select: { tenantId: true },
        });

        if (!provider?.tenantId) {
          return reply.code(400).send({ error: "Tenant context could not be resolved" });
        }

        tenantId = provider.tenantId as TenantId;
      }
    }

    try {
      const result = await ssoService.initiateLogin({
        tenantId,
        providerId,
        ...(redirect_uri ? { redirectUri: redirect_uri } : {}),
      });
      // If redirectUrl is returned, we redirect the user
      if (result.redirectUrl) {
        return reply.redirect(result.redirectUrl);
      }
      return reply.send(result);
    } catch (e: unknown) {
      return reply.code(400).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });

  /**
   * SSO Callback
   */
  app.get("/sso/callback/:providerId", async (req, reply) => {
    const { providerId } = req.params as { providerId: string };
    const { code, state } = req.query as { code: string; state: string };
    const tenantId = (state?.split(":")[0] || "default") as TenantId;

    try {
      const result = await ssoService.handleCallback({
        tenantId,
        providerId,
        code,
        state,
      }) as SsoCallbackResultWithRedirect;

      const callbackUser = result.user;
      if (!result.success || !callbackUser || !result.accessToken || !result.refreshToken) {
        return reply.code(400).send({ error: 'Login Failed', details: 'SSO callback did not return a complete session' });
      }

      const refreshClaims = jwt.verify(result.refreshToken) as AuthJwtClaims;
      await storeRefreshSession(result.refreshToken, refreshClaims);

      const target = result.redirectUri || "/dashboard";
      const separator = target.includes("?") ? "&" : "?";
      return reply.redirect(
        `${target}${separator}accessToken=${encodeURIComponent(result.accessToken)}&refreshToken=${encodeURIComponent(result.refreshToken)}`,
      );
    } catch (e: unknown) {
      return reply
        .code(400)
        .send({ error: "Login Failed", details: e instanceof Error ? e.message : String(e) });
    }
  });

  app.post("/refresh", async (req, reply) => {
    const { refreshToken } = (req.body as { refreshToken?: string }) ?? {};

    if (!refreshToken) {
      return reply.code(400).send({ error: "refreshToken is required" });
    }

    try {
      const claims = jwt.verify(refreshToken) as AuthJwtClaims;
      if (claims.type !== "refresh" || !claims.jti) {
        return reply.code(401).send({ error: "Invalid refresh token" });
      }

      const session = await readRefreshSession(claims.jti);
      if (!session || session.tokenHash !== hashToken(refreshToken)) {
        return reply.code(401).send({ error: "Refresh token invalid or expired" });
      }

      const user = await fetchSessionUser(claims);
      await deleteRefreshSession(claims.jti);

      const nextAccessToken = signAccessToken(user);
      const nextRefreshToken = signRefreshToken(user);
      const nextRefreshClaims = jwt.verify(nextRefreshToken) as AuthJwtClaims;
      await storeRefreshSession(nextRefreshToken, nextRefreshClaims);

      return reply.send({
        accessToken: nextAccessToken,
        refreshToken: nextRefreshToken,
        user,
      });
    } catch {
      return reply.code(401).send({ error: "Refresh token invalid or expired" });
    }
  });

  app.post("/logout", async (req, reply) => {
    const refreshToken = (req.body as { refreshToken?: string } | undefined)?.refreshToken;

    if (refreshToken) {
      try {
        const claims = jwt.verify(refreshToken) as AuthJwtClaims;
        await deleteRefreshSession(claims.jti);
      } catch {
        // Logout stays idempotent.
      }
    }

    return reply.send({ success: true });
  });
};
