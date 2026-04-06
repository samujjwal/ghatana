/**
 * JWT Authentication Middleware
 *
 * Validates Bearer tokens on every incoming request and populates
 * `request.user` with the decoded JWT payload.
 *
 * In development mode auth can be bypassed via `devAuthBypass`; this
 * middleware is intended for production use.
 *
 * @doc.type module
 * @doc.purpose Fastify JWT authentication hook
 * @doc.layer product
 * @doc.pattern Middleware
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import jwt from 'jsonwebtoken';
import { getJwtAccessSecret } from '../services/auth/jwt-config';

// ============================================================================
// Config
// ============================================================================

/** Routes that are always publicly accessible */
const PUBLIC_PATHS = new Set([
  '/health',
  '/metrics',
  '/graphql',
  '/graphiql',
]);

const PUBLIC_AUTH_PATH_SUFFIXES = new Set([
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/verify-email',
]);

function isPublicPath(rawPath: string): boolean {
  const path = rawPath.split('?')[0];
  if (PUBLIC_PATHS.has(path)) {
    return true;
  }

  for (const suffix of PUBLIC_AUTH_PATH_SUFFIXES) {
    if (path.endsWith(suffix)) {
      return true;
    }
  }

  return false;
}

// ============================================================================
// Types
// ============================================================================

export interface JWTUserPayload {
  userId: string;
  email: string;
  role: string;
  workspaceId?: string;
}

// Augment Fastify request so TypeScript knows about `request.user`
declare module 'fastify' {
  interface FastifyRequest {
    user?: JWTUserPayload;
  }
}

// ============================================================================
// Plugin
// ============================================================================

/**
 * Fastify plugin that adds a JWT authentication hook.
 * Must be registered BEFORE routes that require authentication.
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

      const authHeader = request.headers.authorization;

      if (!authHeader?.startsWith('Bearer ')) {
        return reply
          .status(401)
          .send({ error: 'Unauthorized', message: 'Missing Bearer token' });
      }

      const token = authHeader.slice(7);

      try {
        const payload = jwt.verify(token, jwtSecret) as JWTUserPayload &
          jwt.JwtPayload;
        request.user = {
          userId: payload.userId,
          email: payload.email,
          role: payload.role,
          workspaceId: payload.workspaceId,
        };
      } catch (err) {
        const message =
          err instanceof jwt.TokenExpiredError
            ? 'Token expired'
            : 'Invalid token';
        return reply.status(401).send({ error: 'Unauthorized', message });
      }
    }
  );
}
