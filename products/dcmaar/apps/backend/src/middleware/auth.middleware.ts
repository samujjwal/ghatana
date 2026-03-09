/**
 * Authentication middleware for JWT token verification and user identification.
 *
 * <p><b>Purpose</b><br>
 * Provides Fastify preHandler middleware for validating JWT access tokens and
 * extracting user identity. Supports both required and optional authentication
 * modes for flexible endpoint security configuration.
 *
 * <p><b>Middleware Functions</b><br>
 * - authenticate: Requires valid JWT token, returns 401 if missing/invalid
 * - optionalAuthenticate: Attempts JWT validation but allows unauthenticated requests
 *
 * <p><b>Token Validation</b><br>
 * Extracts Bearer token from Authorization header, verifies signature and expiration,
 * and injects userId into request object for downstream handlers to use.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Required authentication
 * fastify.get('/profile', { preHandler: authenticate }, handler);
 * 
 * // Optional authentication (devices can report without auth)
 * fastify.post('/usage', { preHandler: optionalAuthenticate }, handler);
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * Returns 401 Unauthorized for missing/invalid tokens in authenticate mode.
 * In optionalAuthenticate mode, continues without userId for unauthenticated requests.
 *
 * @doc.type middleware
 * @doc.purpose JWT authentication and user identity extraction
 * @doc.layer backend
 * @doc.pattern Middleware
 */
import { FastifyRequest, FastifyReply } from 'fastify';
import { verifyAccessToken } from '../services/auth.service';

/**
 * Extended Fastify request with userId
 */
export interface AuthRequest extends FastifyRequest {
  userId?: string;
}

/**
 * Authentication preHandler - verifies JWT token
 * Use as: { preHandler: authenticate }
 */
export async function authenticate(
  request: AuthRequest,
  reply: FastifyReply
): Promise<void> {
  try {
    // Get token from Authorization header
    const authHeader = request.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'No token provided' });
    }

    const token = authHeader.substring(7); // Remove 'Bearer ' prefix

    // Verify token
    const decoded = verifyAccessToken(token);

    if (!decoded) {
      return reply.status(401).send({ error: 'Invalid or expired token' });
    }

    // Attach user ID to request
    request.userId = decoded.userId;
  } catch (_error) {
    return reply.status(401).send({ error: 'Authentication failed' });
  }
}

/**
 * Optional authentication - attaches user if token is valid, but doesn't require it
 * Use as: { preHandler: optionalAuthenticate }
 */
export async function optionalAuthenticate(
  request: AuthRequest,
  _reply: FastifyReply
): Promise<void> {
  try {
    const authHeader = request.headers.authorization;

    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7);
      const decoded = verifyAccessToken(token);

      if (decoded) {
        request.userId = decoded.userId;
      }
    }
  } catch (_error) {
    // Silently fail for optional auth
  }
}
