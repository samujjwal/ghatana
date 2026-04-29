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
  del: (...keys: string[]) => Promise<number>;
  sadd: (key: string, ...members: string[]) => Promise<number>;
  srem: (key: string, ...members: string[]) => Promise<number>;
  smembers: (key: string) => Promise<string[]>;
  expire: (key: string, seconds: number) => Promise<number>;
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

const USER_SESSION_INDEX_PREFIX = "auth:user-sessions:";

function getUserSessionIndexKey(tenantId: string, userId: string): string {
  return `${USER_SESSION_INDEX_PREFIX}${tenantId}:${userId}`;
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

    // Fail closed if user not found - do not default to "default" tenant
    throw new Error(`User not found: ${claims.sub}`);
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

    const sessionKey = getRefreshSessionKey(claims.jti);
    await redis.set(
      sessionKey,
      JSON.stringify({ ...record, tokenHash: hashToken(refreshToken) }),
      'EX',
      REFRESH_SESSION_TTL_SECONDS,
    );

    // F-003: maintain a per-user set of active JTIs for bulk revocation
    const indexKey = getUserSessionIndexKey(claims.tenantId, claims.sub);
    await redis.sadd(indexKey, claims.jti);
    // Keep the index alive as long as the longest-lived refresh token could be
    await redis.expire(indexKey, REFRESH_SESSION_TTL_SECONDS);
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

  async function deleteRefreshSession(jti: string | undefined, session?: RefreshSessionRecord): Promise<void> {
    if (!jti) {
      return;
    }

    await redis.del(getRefreshSessionKey(jti));

    // F-003: clean up from the user index if we know the owner
    if (session) {
      await redis.srem(getUserSessionIndexKey(session.tenantId, session.userId), jti);
    }
  }

  /**
   * F-003: Revoke all active refresh sessions for a user.
   * Returns the number of sessions that were revoked.
   */
  async function revokeAllUserSessions(tenantId: string, userId: string): Promise<number> {
    const indexKey = getUserSessionIndexKey(tenantId, userId);
    const jtis = await redis.smembers(indexKey);
    if (jtis.length === 0) return 0;

    const sessionKeys = jtis.map(getRefreshSessionKey);
    await redis.del(...sessionKeys);
    await redis.del(indexKey);
    return jtis.length;
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

  app.get("/health", { config: { public: true } }, async () => ({
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
  app.get("/sso/providers", { config: { public: true } }, async (req, reply) => {
    const { tenantSlug } = req.query as { tenantSlug?: string };
    if (!tenantSlug)
      return reply.code(400).send({ error: "tenantSlug is required" });

    const providers = await ssoService.getLoginProviders({ tenantSlug });
    return reply.send({ providers });
  });

  /**
   * Initiate SSO Login
   */
  app.get("/sso/login/:providerId", { config: { public: true } }, async (req, reply) => {
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
   * SSO Callback (F-002)
   *
   * After the OAuth provider redirects back, we exchange the code via the SSO
   * service, store the resulting tokens in Redis under a short-lived (30s) code,
   * and redirect the browser to the frontend with only that opaque `sso_code`
   * param — never the raw tokens.  The frontend must call POST /sso/exchange to
   * retrieve the actual access + refresh tokens.
   */
  app.get("/sso/callback/:providerId", { config: { public: true } }, async (req, reply) => {
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

      if (!result.success || !result.accessToken || !result.refreshToken) {
        return reply.code(400).send({ error: "Login Failed", details: "SSO callback did not return a complete session" });
      }

      const refreshClaims = jwt.verify(result.refreshToken) as AuthJwtClaims;
      await storeRefreshSession(result.refreshToken, refreshClaims);

      // Store tokens in Redis under a short-lived opaque code.  The browser
      // never sees the raw token values in the URL (F-002).
      const ssoCode = crypto.randomUUID();
      const SSO_EXCHANGE_TTL = 30; // seconds
      await redis.set(
        `auth:sso-exchange:${ssoCode}`,
        JSON.stringify({ accessToken: result.accessToken, refreshToken: result.refreshToken }),
        "EX",
        SSO_EXCHANGE_TTL,
      );

      const target = result.redirectUri || "/dashboard";
      const separator = target.includes("?") ? "&" : "?";
      return reply.redirect(`${target}${separator}sso_code=${encodeURIComponent(ssoCode)}`);
    } catch (e: unknown) {
      return reply
        .code(400)
        .send({ error: "Login Failed", details: e instanceof Error ? e.message : String(e) });
    }
  });

  /**
   * POST /sso/exchange (F-002)
   *
   * Exchange the short-lived opaque `ssoCode` for an access + refresh token
   * pair.  The code is single-use and expires in 30 seconds.
   */
  app.post("/sso/exchange", { config: { public: true } }, async (req, reply) => {
    const { z } = await import("zod");
    const bodyResult = z.object({ ssoCode: z.string().uuid() }).safeParse(req.body);
    if (!bodyResult.success) {
      return reply.code(400).send({ error: "ssoCode is required and must be a valid UUID" });
    }

    const redisKey = `auth:sso-exchange:${bodyResult.data.ssoCode}`;
    const raw = await redis.get(redisKey);
    if (!raw) {
      return reply.code(401).send({ error: "SSO code invalid or expired" });
    }

    // Delete immediately — single-use
    await redis.del(redisKey);

    let parsed: { accessToken: string; refreshToken: string };
    try {
      parsed = JSON.parse(raw) as { accessToken: string; refreshToken: string };
    } catch {
      return reply.code(500).send({ error: "Internal error during SSO exchange" });
    }

    return reply.send({
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
    });
  });

  app.post("/refresh", { config: { public: true } }, async (req, reply) => {
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
      await deleteRefreshSession(claims.jti, session);

      const nextAccessToken = signAccessToken(user);
      const nextRefreshToken = signRefreshToken(user);
      const nextRefreshClaims = jwt.verify(nextRefreshToken) as AuthJwtClaims;
      await storeRefreshSession(nextRefreshToken, nextRefreshClaims);

      return reply.send({
        accessToken: nextAccessToken,
        refreshToken: nextRefreshToken,
        user,
      });
    } catch (error) {
      // Ensure we don't leak sensitive error details
      return reply.code(401).send({ error: "Refresh token invalid or expired" });
    }
  });

  // GET /consents - Get user consent status
  app.get("/consents", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      const user = await fetchSessionUser(claims);

      const userWithConsents = await prisma.user.findUnique({
        where: { id: claims.sub },
        select: {
          id: true,
          email: true,
          tenantId: true,
          role: true,
          consents: true,
        },
      });

      if (!userWithConsents) {
        return reply.code(404).send({ error: "User not found" });
      }

      const consentCategories = (userWithConsents.consents as any[]).map((c) => c.category) || [];

      const consentList = [
        {
          id: "ai_processing",
          name: "AI Processing",
          description: "Allow AI to process your data for personalized learning experiences",
          required: false,
          granted: consentCategories.includes("ai_processing") || false,
          grantedAt: consentCategories.includes("ai_processing") ? new Date().toISOString() : undefined,
        },
        {
          id: "analytics",
          name: "Analytics",
          description: "Allow anonymous usage analytics to improve the platform",
          required: false,
          granted: consentCategories.includes("analytics") || false,
          grantedAt: consentCategories.includes("analytics") ? new Date().toISOString() : undefined,
        },
        {
          id: "essential",
          name: "Essential",
          description: "Essential cookies and data for platform functionality",
          required: true,
          granted: true,
          grantedAt: new Date().toISOString(),
        },
      ];

      return reply.send({ consents: consentList });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  // POST /consents - Update user consent
  app.post("/consents", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      const { consentId, granted } = req.body as {
        consentId: string;
        granted: boolean;
      };

      const user = await prisma.user.findUnique({
        where: { id: claims.sub },
        select: { consents: true },
      });

      if (!user) {
        return reply.code(404).send({ error: "User not found" });
      }

      const currentConsents = (user.consents as any[]).map((c) => c.category) || [];
      let updatedConsents: string[];

      if (granted) {
        updatedConsents = [...new Set([...currentConsents, consentId])];
      } else {
        updatedConsents = currentConsents.filter((c) => c !== consentId);
      }

      // Update consents by creating/deleting UserConsent records
      const existingConsent = await prisma.userConsent.findFirst({
        where: { userId: claims.sub, category: consentId },
      });

      if (granted) {
        if (!existingConsent) {
          await prisma.userConsent.create({
            data: {
              userId: claims.sub,
              tenantId: claims.tenantId,
              category: consentId,
              granted: true,
              grantedAt: new Date(),
            },
          });
        } else {
          await prisma.userConsent.update({
            where: { id: existingConsent.id },
            data: { granted: true, grantedAt: new Date(), revokedAt: null },
          });
        }
      } else {
        if (existingConsent) {
          await prisma.userConsent.update({
            where: { id: existingConsent.id },
            data: { granted: false, revokedAt: new Date() },
          });
        }
      }

      return reply.send({ success: true, consents: updatedConsents });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  app.post("/logout", async (req, reply) => {
    const refreshToken = (req.body as { refreshToken?: string } | undefined)?.refreshToken;

    if (refreshToken) {
      try {
        const claims = jwt.verify(refreshToken) as AuthJwtClaims;
        const session = claims.jti ? await readRefreshSession(claims.jti) : null;
        await deleteRefreshSession(claims.jti, session ?? undefined);
      } catch {
        // Logout stays idempotent - don't leak error details
      }
    }

    return reply.send({ success: true });
  });

  // GET /admin/users/with-roles - Get all users with their roles and permissions
  app.get("/admin/users/with-roles", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      if (claims.role !== "admin" && claims.role !== "superadmin") {
        return reply.code(403).send({ error: "Forbidden" });
      }

      const users = await prisma.user.findMany({
        where: { tenantId: claims.tenantId },
        select: {
          id: true,
          email: true,
          displayName: true,
          roles: true,
          userRoles: {
            include: {
              role: {
                include: {
                  rolePermissions: {
                    include: {
                      permission: true,
                    },
                  },
                },
              },
            },
          },
        },
      });

      const usersWithRoles = users.map((user) => ({
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        roles: user.userRoles.map((ur) => ur.role),
        permissions: user.userRoles.flatMap((ur) =>
          ur.role.rolePermissions.map((rp) => rp.permission),
        ),
      }));

      return reply.send({ users: usersWithRoles });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  // GET /admin/roles - Get all roles
  app.get("/admin/roles", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      if (claims.role !== "admin" && claims.role !== "superadmin") {
        return reply.code(403).send({ error: "Forbidden" });
      }

      const roles = await prisma.role.findMany({
        where: { tenantId: claims.tenantId },
        include: {
          rolePermissions: {
            include: {
              permission: true,
            },
          },
        },
      });

      return reply.send({ roles });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  // GET /admin/permissions - Get all permissions
  app.get("/admin/permissions", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      if (claims.role !== "admin" && claims.role !== "superadmin") {
        return reply.code(403).send({ error: "Forbidden" });
      }

      const permissions = await prisma.permission.findMany();

      return reply.send({ permissions });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  // POST /admin/users/roles - Assign role to user
  app.post("/admin/users/roles", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      if (claims.role !== "admin" && claims.role !== "superadmin") {
        return reply.code(403).send({ error: "Forbidden" });
      }

      const { userId, roleId } = req.body as { userId: string; roleId: string };
      const tenantId = claims.tenantId;

      await prisma.userRole.create({
        data: {
          userId,
          roleId,
          tenantId,
        },
      });

      return reply.send({ success: true });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  // DELETE /admin/users/roles - Remove role from user
  app.delete("/admin/users/roles", async (req, reply) => {
    try {
      const claims = jwt.verify(
        (req.headers["authorization"] as string)?.replace("Bearer ", "") || "",
      ) as AuthJwtClaims;

      if (claims.role !== "admin" && claims.role !== "superadmin") {
        return reply.code(403).send({ error: "Forbidden" });
      }

      const { userId, roleId } = req.body as { userId: string; roleId: string };

      await prisma.userRole.deleteMany({
        where: {
          userId,
          roleId,
        },
      });

      return reply.send({ success: true });
    } catch {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  });

  /**
   * POST /revoke-all-sessions (F-003)
   *
   * Revokes all refresh sessions for the authenticated user.
   * Admin users may specify a different userId via request body to revoke another
   * user's sessions.  Non-admin callers can only revoke their own sessions.
   */
  app.post("/revoke-all-sessions", async (req, reply) => {
    const caller = (req as typeof req & { user?: AuthJwtClaims }).user;
    if (!caller?.sub || !caller.tenantId) {
      return reply.code(401).send({ error: "Unauthorized" });
    }

    const body = (req.body ?? {}) as { userId?: string };
    const targetUserId = body.userId;

    // Only admin/superadmin can revoke other users' sessions
    if (targetUserId && targetUserId !== caller.sub && caller.role !== "admin" && caller.role !== "superadmin") {
      return reply.code(403).send({ error: "Forbidden: insufficient role to revoke another user's sessions" });
    }

    const userId = targetUserId ?? caller.sub;
    const revoked = await revokeAllUserSessions(caller.tenantId, userId);

    return reply.send({ revoked });
  });
};
