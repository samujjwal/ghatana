/**
 * JWT Authentication Middleware
 *
 * Validates Bearer tokens or httpOnly cookies on every incoming request
 * and populates `request.user` with the decoded JWT payload.
 *
 * In development mode auth can be bypassed via `devAuthBypass`; this
 * middleware is intended for production use.
 *
 * Cookie auth is the preferred method for browser clients, while Bearer
 * tokens are supported for API clients and server-to-server calls.
 *
 * @doc.type module
 * @doc.purpose Fastify JWT authentication hook (cookie + Bearer)
 * @doc.layer product
 * @doc.pattern Middleware
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import jwt from 'jsonwebtoken';
import { getJwtAccessSecret } from '../services/auth/jwt-config';
import { isPublicPath } from './public-paths';

// Cookie names used for auth
const ACCESS_TOKEN_COOKIE = 'accessToken';
const REFRESH_TOKEN_COOKIE = 'refreshToken';

// ============================================================================
// Types
// ============================================================================

export interface JWTUserPayload {
  userId: string;
  email: string;
  role: string;
  tenantId?: string;
  workspaceId?: string;
}

// Augment Fastify request so TypeScript knows about `request.user`
declare module 'fastify' {
  interface FastifyRequest {
    user?: JWTUserPayload;
  }
}

// ============================================================================
// Token Extraction
// ============================================================================

/**
 * Extract JWT token from request (cookie or Bearer header)
 */
function extractToken(request: FastifyRequest): string | null {
  // First check for httpOnly cookie
  const cookies = request.cookies;
  if (cookies?.[ACCESS_TOKEN_COOKIE]) {
    return cookies[ACCESS_TOKEN_COOKIE];
  }

  // Fallback to Bearer token
  const authHeader = request.headers.authorization;
  if (authHeader?.startsWith('Bearer ')) {
    return authHeader.slice(7);
  }

  return null;
}

/**
 * Verify JWT token and return payload
 */
function verifyToken(
  token: string,
  jwtSecret: string
): JWTUserPayload | null {
  try {
    const payload = jwt.verify(token, jwtSecret) as JWTUserPayload &
      jwt.JwtPayload;
    return {
      userId: payload.userId,
      email: payload.email,
      role: payload.role,
      tenantId: payload.tenantId,
      workspaceId: payload.workspaceId,
    };
  } catch {
    return null;
  }
}

// ============================================================================
// Plugin
// ============================================================================

/**
 * Fastify plugin that adds a JWT authentication hook.
 * Must be registered BEFORE routes that require authentication.
 *
 * Supports both httpOnly cookies (preferred for browser) and
 * Bearer tokens (for API clients).
 *
 * @doc.type function
 * @doc.purpose Register JWT auth lifecycle hook on Fastify instance
 * @doc.layer product
 * @doc.pattern Middleware
 */
export async function authMiddleware(fastify: FastifyInstance): Promise<void> {
  const jwtSecret = getJwtAccessSecret();

  fastify.addHook(
    'onRequest',
    async (request: FastifyRequest, reply: FastifyReply) => {
      // Skip public paths
      if (isPublicPath(request.routeOptions?.url ?? request.url)) {
        return;
      }

      // Already populated (e.g. by devAuthBypass in development)
      if (request.user) {
        return;
      }

      const token = extractToken(request);

      if (!token) {
        return reply.status(401).send({
          error: 'Unauthorized',
          message: 'Authentication required. Provide Bearer token or cookie.',
        });
      }

      const payload = verifyToken(token, jwtSecret);

      if (!payload) {
        return reply.status(401).send({
          error: 'Unauthorized',
          message: 'Invalid or expired token',
        });
      }

      request.user = payload;
    }
  );
}
