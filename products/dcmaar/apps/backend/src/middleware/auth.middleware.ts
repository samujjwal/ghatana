/**
 * Authentication and RBAC middleware for JWT token verification and authorization.
 *
 * <p><b>Purpose</b><br>
 * Provides Fastify preHandler middleware for validating JWT access tokens and
 * extracting user identity. Supports both required and optional authentication
 * modes for flexible endpoint security configuration.
 *
 * <p><b>Token Validation Strategy (Priority Order)</b><br>
 * 1. Platform token — validated via the Ghatana auth-gateway service.
 *    Activated when {@code AUTH_GATEWAY_URL} is set and the gateway is reachable.
 *    Enables cross-service SSO without local user accounts.<br>
 * 2. Local JWT — validated with the DCMAAR-specific {@code JWT_SECRET} as a
 *    standalone fallback when the auth-gateway is unavailable or the token was
 *    issued locally.
 *
 * <p><b>RBAC</b><br>
 * {@link requireRole} wraps {@link authenticate} and additionally asserts that
 * the authenticated user holds one of the required roles (resolved from the
 * auth-gateway identity or the local role store).
 *
 * <p><b>Middleware Functions</b><br>
 * - authenticate: Requires valid JWT token (platform or local), returns 401 if missing/invalid
 * - optionalAuthenticate: Attempts JWT validation but allows unauthenticated requests
 * - requireRole: Requires valid token AND a specific role
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Required authentication
 * fastify.get('/profile', { preHandler: authenticate }, handler);
 *
 * // Optional authentication
 * fastify.post('/usage', { preHandler: optionalAuthenticate }, handler);
 *
 * // Role-based access control
 * fastify.delete('/admin/user/:id', { preHandler: requireRole('admin') }, handler);
 * fastify.get('/reports', { preHandler: requireRole('admin', 'analyst') }, handler);
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * Returns 401 Unauthorized for missing/invalid tokens.<br>
 * Returns 403 Forbidden when the role requirement is not satisfied.
 *
 * @doc.type middleware
 * @doc.purpose JWT authentication, platform token validation, and RBAC
 * @doc.layer backend
 * @doc.pattern Middleware
 */
import { FastifyRequest, FastifyReply } from 'fastify';
import { verifyAccessToken } from '../services/auth.service';
import { AuthGatewayClient } from '../services/auth-gateway.client';
import { logger } from '../utils/logger';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Extended Fastify request with authenticated user context.
 */
export interface AuthRequest extends FastifyRequest {
  userId?: string;
  /** True when the token was issued by the Ghatana platform auth-gateway. */
  isPlatformToken?: boolean;
  /** Roles resolves from the platform identity or local role store. */
  roles?: readonly string[];
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Attempts to resolve a user identity from the provided Bearer token.
 *
 * Priority:
 *  1. Platform auth-gateway validation (if configured and reachable)
 *  2. Local DCMAAR JWT verification
 *
 * Returns `null` when neither validation source can verify the token.
 */
async function resolveIdentity(
  token: string,
): Promise<{ userId: string; isPlatformToken: boolean; roles: readonly string[] } | null> {
  // 1 — attempt platform token validation
  try {
    const platformIdentity = await AuthGatewayClient.getInstance().validate(token);
    if (platformIdentity.valid && platformIdentity.userId) {
      logger.debug('Auth-gateway token accepted', { userId: platformIdentity.userId });
      return {
        userId: platformIdentity.userId,
        isPlatformToken: true,
        roles: platformIdentity.roles ?? [],
      };
    }
  } catch (err) {
    // Gateway unreachable — log and fall through to local verification
    logger.warn('Auth-gateway validation failed, falling back to local JWT', {
      error: err instanceof Error ? err.message : String(err),
    });
  }

  // 2 — fall back to local DCMAAR JWT
  const local = verifyAccessToken(token);
  if (local) {
    return { userId: local.userId, isPlatformToken: false, roles: [] };
  }

  return null;
}

// ---------------------------------------------------------------------------
// Middleware
// ---------------------------------------------------------------------------

/**
 * Authentication preHandler — verifies the Bearer token (platform or local JWT).
 * Attaches `userId`, `isPlatformToken`, and `roles` to the request object.
 * Returns 401 when authentication fails.
 */
export async function authenticate(
  request: AuthRequest,
  reply: FastifyReply,
): Promise<void> {
  const authHeader = request.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return reply.status(401).send({ error: 'No token provided' });
  }

  const token = authHeader.substring(7);
  const identity = await resolveIdentity(token);

  if (!identity) {
    return reply.status(401).send({ error: 'Invalid or expired token' });
  }

  request.userId = identity.userId;
  request.isPlatformToken = identity.isPlatformToken;
  request.roles = identity.roles;
}

/**
 * Optional authentication — attaches user context if a valid token is present,
 * but does not reject unauthenticated requests.
 */
export async function optionalAuthenticate(
  request: AuthRequest,
  _reply: FastifyReply,
): Promise<void> {
  try {
    const authHeader = request.headers.authorization;

    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7);
      const identity = await resolveIdentity(token);

      if (identity) {
        request.userId = identity.userId;
        request.isPlatformToken = identity.isPlatformToken;
        request.roles = identity.roles;
      }
    }
  } catch (_error) {
    // Silently ignore errors in optional auth — never block the request
  }
}

/**
 * Role-based access control (RBAC) preHandler factory.
 *
 * Wraps `authenticate` and additionally checks that the authenticated user
 * holds at least one of the specified roles. Returns 403 Forbidden if the
 * role check fails.
 *
 * @param allowedRoles  One or more role strings, e.g. `'admin'`, `'analyst'`.
 *                      The user must have at least one of them.
 *
 * @example
 * ```ts
 * fastify.delete('/admin/user/:id', { preHandler: requireRole('admin') }, handler);
 * fastify.get('/reports', { preHandler: requireRole('admin', 'analyst') }, handler);
 * ```
 */
export function requireRole(
  ...allowedRoles: readonly string[]
): (request: AuthRequest, reply: FastifyReply) => Promise<void> {
  return async (request: AuthRequest, reply: FastifyReply): Promise<void> => {
    // Step 1 — authenticate (resolves identity, rejects with 401 if invalid)
    await authenticate(request, reply);
    if (reply.sent) return; // authenticate already sent a 401

    // Step 2 — authorise
    const userRoles = request.roles ?? [];
    const hasRole = allowedRoles.some((r) => userRoles.includes(r));

    if (!hasRole) {
      logger.warn('Access denied — insufficient role', {
        userId: request.userId,
        required: allowedRoles,
        actual: userRoles,
        path: request.url,
      });
      return reply.status(403).send({
        error: 'Forbidden',
        message: `Requires one of: ${allowedRoles.join(', ')}`,
      });
    }
  };
}

