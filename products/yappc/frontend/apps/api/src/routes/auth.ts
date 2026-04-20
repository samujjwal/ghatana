/**
 * Authentication API Routes
 *
 * Proxies all authentication to the canonical Java lifecycle service.
 * The Node.js API is no longer the auth authority - it delegates to Java.
 *
 * @doc.type route
 * @doc.purpose Authentication API endpoints (proxy to Java service)
 * @doc.layer api
 * @doc.pattern REST API / Proxy
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { proxyAuthService } from '../services/auth/proxy-auth.service';
import type { AuthUser } from '../services/auth/auth.service';
import type { JWTUserPayload } from '../middleware/auth.middleware';
import { getErrorMessage, isRecord } from '../utils/type-guards';

// Use proxy auth service that delegates to Java lifecycle service
const authService = proxyAuthService;

type LoginRequestBody = {
  email: string;
  password: string;
};

type RefreshRequestBody = {
  refreshToken: string;
};

type AuthenticatedRequest = FastifyRequest & {
  user?: JWTUserPayload | AuthUser;
};

function toAuthenticatedUser(user: AuthUser): JWTUserPayload {
  return {
    userId: user.id,
    email: user.email,
    role: user.role,
  };
}

// ============================================================================
// Authentication Routes
// ============================================================================

export async function authRoutes(fastify: FastifyInstance) {
  // Register cookie plugin if available
  // Note: Requires @fastify/cookie to be installed
  // pnpm add @fastify/cookie
  try {
    // @ts-ignore - Cookie plugin may not be registered
    await fastify.register(import('@fastify/cookie'), {
      secret: process.env.COOKIE_SECRET || 'change-me-in-production',
    });
  } catch {
    // Cookie plugin not available, fall back to token-in-response mode
    console.warn('Cookie plugin not available, using token-in-response mode');
  }

  // Login endpoint
  fastify.post(
    '/auth/login',
    {
      schema: {
        body: {
          type: 'object',
          required: ['email', 'password'],
          properties: {
            email: { type: 'string', format: 'email' },
            password: { type: 'string' },
          },
        },
        response: {
          200: {
            type: 'object',
            properties: {
              user: {
                type: 'object',
                properties: {
                  id: { type: 'string' },
                  email: { type: 'string' },
                  name: { type: 'string' },
                  role: { type: 'string' },
                },
              },
              tokens: {
                type: 'object',
                properties: {
                  accessToken: { type: 'string' },
                  refreshToken: { type: 'string' },
                  expiresIn: { type: 'number' },
                },
              },
            },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const { email, password } = request.body as LoginRequestBody;

        const result = await authService.login({
          email,
          password,
        });

        // Set httpOnly cookies if cookie plugin is available
        if (reply.setCookie && typeof reply.setCookie === 'function') {
          const isProduction = process.env.NODE_ENV === 'production';
          
          reply.setCookie('accessToken', result.tokens.accessToken, {
            httpOnly: true,
            secure: isProduction,
            sameSite: 'strict',
            path: '/',
            maxAge: result.tokens.expiresIn * 1000,
          });
          
          reply.setCookie('refreshToken', result.tokens.refreshToken, {
            httpOnly: true,
            secure: isProduction,
            sameSite: 'strict',
            path: '/api/auth/refresh',
            maxAge: 30 * 24 * 60 * 60 * 1000, // 30 days
          });
          
          // Return user info only when using cookies
          reply.send({ user: result.user });
        } else {
          // Fall back to token-in-response mode
          reply.send(result);
        }
      } catch (error: unknown) {
        reply.code(401).send({
          error: 'Authentication failed',
          message:
            error instanceof Error ? error.message : 'Authentication failed',
        });
      }
    }
  );

  // Refresh token endpoint
  fastify.post(
    '/auth/refresh',
    {
      schema: {
        body: {
          type: 'object',
          properties: {
            refreshToken: { type: 'string' },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        // Try to get refresh token from cookie first
        let refreshToken: string | undefined;
        if (request.cookies && typeof request.cookies === 'object') {
          refreshToken = (request.cookies as Record<string, string>).refreshToken;
        }
        
        // Fall back to body if cookie not available
        if (!refreshToken) {
          const body = request.body as RefreshRequestBody;
          refreshToken = body.refreshToken;
        }
        
        if (!refreshToken) {
          reply.code(401).send({
            error: 'Token refresh failed',
            message: 'No refresh token provided',
          });
          return;
        }

        const result = await authService.refreshTokens(refreshToken);

        // Set new cookies if cookie plugin is available
        if (reply.setCookie && typeof reply.setCookie === 'function') {
          const isProduction = process.env.NODE_ENV === 'production';
          
          reply.setCookie('accessToken', result.accessToken, {
            httpOnly: true,
            secure: isProduction,
            sameSite: 'strict',
            path: '/',
            maxAge: result.expiresIn * 1000,
          });
          
          reply.setCookie('refreshToken', result.refreshToken, {
            httpOnly: true,
            secure: isProduction,
            sameSite: 'strict',
            path: '/api/auth/refresh',
            maxAge: 30 * 24 * 60 * 60 * 1000,
          });
          
          // Return user info only when using cookies
          reply.send({ accessToken: result.accessToken, expiresIn: result.expiresIn });
        } else {
          // Fall back to token-in-response mode
          reply.send(result);
        }
      } catch (error: unknown) {
        reply.code(401).send({
          error: 'Token refresh failed',
          message:
            error instanceof Error ? error.message : 'Token refresh failed',
        });
      }
    }
  );

  // Logout endpoint
  fastify.post(
    '/auth/logout',
    {
      schema: {
        body: {
          type: 'object',
          properties: {
            refreshToken: { type: 'string' },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        // Try to get refresh token from cookie first
        let refreshToken: string | undefined;
        if (request.cookies && typeof request.cookies === 'object') {
          refreshToken = (request.cookies as Record<string, string>).refreshToken;
        }
        
        // Fall back to body if cookie not available
        if (!refreshToken) {
          const body = request.body as RefreshRequestBody;
          refreshToken = body.refreshToken;
        }

        if (refreshToken) {
          await authService.logout(refreshToken);
        }

        // Clear cookies if cookie plugin is available
        if (reply.clearCookie && typeof reply.clearCookie === 'function') {
          reply.clearCookie('accessToken', { path: '/' });
          reply.clearCookie('refreshToken', { path: '/api/auth/refresh' });
        }

        reply.send({ message: 'Logged out successfully' });
      } catch (error: unknown) {
        // Always clear cookies even if logout fails
        if (reply.clearCookie && typeof reply.clearCookie === 'function') {
          reply.clearCookie('accessToken', { path: '/' });
          reply.clearCookie('refreshToken', { path: '/api/auth/refresh' });
        }
        
        reply.code(400).send({
          error: 'Logout failed',
          message: getErrorMessage(error),
        });
      }
    }
  );

  // Get current user
  fastify.get(
    '/auth/me',
    {
      preHandler: [authenticateToken],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const user = await authService.getCurrentUser(
          (request as AuthenticatedRequest).user?.userId ?? ''
        );

        reply.send(user);
      } catch (error: unknown) {
        reply.code(404).send({
          error: 'User not found',
          message: getErrorMessage(error),
        });
      }
    }
  );

}

// ============================================================================
// Authentication Middleware
// ============================================================================

export async function authenticateToken(
  request: FastifyRequest,
  reply: FastifyReply
) {
  try {
    let token: string | undefined;

    // Try to get token from cookie first
    if (request.cookies && typeof request.cookies === 'object') {
      token = (request.cookies as Record<string, string>).accessToken;
    }

    // Fall back to Authorization header
    if (!token) {
      const authHeader = request.headers.authorization;
      if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.substring(7);
      }
    }

    if (!token) {
      return reply.code(401).send({
        error: 'Authentication required',
        message: 'No token provided',
      });
    }

    const user = await authService.validateAccessToken(token);

    (request as AuthenticatedRequest).user = toAuthenticatedUser(user);
  } catch (error: unknown) {
    return reply.code(401).send({
      error: 'Invalid token',
      message: error instanceof Error ? error.message : 'Invalid token',
    });
  }
}

export async function requireRole(role: string) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const user = (request as AuthenticatedRequest).user;

      if (!user || !hasRole(user.role, role)) {
        return reply.code(403).send({
          error: 'Insufficient permissions',
          message: `Role ${role} required`,
        });
      }
    } catch (error: unknown) {
      return reply.code(403).send({
        error: 'Authorization failed',
        message: getErrorMessage(error),
      });
    }
  };
}

// ============================================================================
// Helper Functions
// ============================================================================

function hasRole(userRole: string, requiredRole: string): boolean {
  const roleHierarchy = {
    VIEWER: 1,
    EDITOR: 2,
    ADMIN: 3,
    OWNER: 4,
  };

  const userLevel = roleHierarchy[userRole as keyof typeof roleHierarchy] || 0;
  const requiredLevel =
    roleHierarchy[requiredRole as keyof typeof roleHierarchy] || 0;

  return userLevel >= requiredLevel;
}

// ============================================================================
// Schema Definitions
// ============================================================================

export const authSchemas = {
  User: {
    type: 'object',
    properties: {
      id: { type: 'string' },
      email: { type: 'string' },
      name: { type: 'string' },
      role: { type: 'string', enum: ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'] },
      avatar: { type: 'string' },
      workspaces: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            id: { type: 'string' },
            name: { type: 'string' },
            role: { type: 'string' },
          },
        },
      },
    },
  },
  AuthTokens: {
    type: 'object',
    properties: {
      accessToken: { type: 'string' },
      refreshToken: { type: 'string' },
      expiresIn: { type: 'number' },
    },
  },
};
