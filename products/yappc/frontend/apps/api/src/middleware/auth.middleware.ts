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

// ============================================================================
// Config
// ============================================================================

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret-change-in-production';

/** Routes that are always publicly accessible */
const PUBLIC_PATHS = new Set([
  '/health',
  '/metrics',
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/graphql', // GraphQL resolvers guard themselves
  '/graphiql',
]);

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
  fastify.addHook(
    'onRequest',
    async (request: FastifyRequest, reply: FastifyReply) => {
      // Skip public paths
      if (
        PUBLIC_PATHS.has(request.routeOptions?.url ?? request.url.split('?')[0])
      ) {
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
        const payload = jwt.verify(token, JWT_SECRET) as JWTUserPayload &
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
