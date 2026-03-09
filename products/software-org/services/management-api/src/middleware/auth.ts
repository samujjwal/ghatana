/**
 * Authentication middleware for JWT token verification and user identification.
 *
 * <p><b>Purpose</b><br>
 * Provides Fastify preHandler middleware for validating JWT access tokens and
 * extracting user identity for persona management endpoints. Follows Guardian
 * backend pattern for consistent authentication across products.
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
 * fastify.get('/api/personas/:workspaceId', { preHandler: authenticate }, handler);
 * 
 * // Optional authentication
 * fastify.get('/api/public', { preHandler: optionalAuthenticate }, handler);
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * Returns 401 Unauthorized for missing/invalid tokens in authenticate mode.
 * In optionalAuthenticate mode, continues without userId for unauthenticated requests.
 *
 * <p><b>Boundary Compliance</b><br>
 * This is user-facing authentication only. Does NOT handle:
 * - Domain-level authorization (that's Java)
 * - Multi-agent system auth (that's Java)
 * - Event-based access control (that's Java)
 *
 * @doc.type middleware
 * @doc.purpose JWT authentication and user identity extraction
 * @doc.layer product
 * @doc.pattern Middleware
 */
import { FastifyRequest, FastifyReply } from 'fastify';
import jwt from 'jsonwebtoken';
import { appConfig } from '../config/index.js';

/**
 * JWT payload structure
 */
export interface JWTPayload {
    userId: string;
    email: string;
    iat?: number;
    exp?: number;
}

/**
 * Extended Fastify request with userId
 */
export interface AuthRequest extends FastifyRequest {
    userId?: string;
}

/**
 * Verify JWT access token
 * 
 * @param token - JWT token string
 * @returns Decoded payload or null if invalid
 */
export function verifyAccessToken(token: string): JWTPayload | null {
    try {
        const decoded = jwt.verify(token, appConfig.jwt.secret) as JWTPayload;
        return decoded;
    } catch (error) {
        return null;
    }
}

/**
 * Generate JWT access token
 * 
 * @param userId - User identifier
 * @param email - User email
 * @returns Signed JWT token
 */
export function generateAccessToken(userId: string, email: string): string {
    const payload: JWTPayload = { userId, email };
    return jwt.sign(payload, appConfig.jwt.secret, {
        expiresIn: appConfig.jwt.expiresIn
    } as jwt.SignOptions);
}

/**
 * Authentication preHandler - verifies JWT token
 * Use as: { preHandler: authenticate }
 * 
 * @param request - Fastify request with auth header
 * @param reply - Fastify reply for error responses
 */
export async function authenticate(
    request: AuthRequest,
    reply: FastifyReply
): Promise<void> {
    try {
        // Get token from Authorization header
        const authHeader = request.headers.authorization;

        if (!authHeader || !authHeader.startsWith('Bearer ')) {
            return reply.status(401).send({
                success: false,
                error: 'No token provided'
            });
        }

        const token = authHeader.substring(7); // Remove 'Bearer ' prefix

        // Verify token
        const decoded = verifyAccessToken(token);

        if (!decoded) {
            return reply.status(401).send({
                success: false,
                error: 'Invalid or expired token'
            });
        }

        // Attach user ID to request
        request.userId = decoded.userId;
    } catch (_error) {
        return reply.status(401).send({
            success: false,
            error: 'Authentication failed'
        });
    }
}

/**
 * Optional authentication - attaches user if token is valid, but doesn't require it
 * Use as: { preHandler: optionalAuthenticate }
 * 
 * @param request - Fastify request with optional auth header
 * @param _reply - Fastify reply (unused)
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
