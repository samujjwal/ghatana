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

import { FastifyRequest, FastifyReply } from 'fastify';
import { proxyAuthService } from '../services/auth/proxy-auth.service';

// Use proxy auth service that delegates to Java lifecycle service
const authService = proxyAuthService;

type LoginRequestBody = {
  email: string;
  password: string;
};

type RefreshRequestBody = {
  refreshToken: string;
};

// ============================================================================
// Authentication Routes
// ============================================================================

export async function authRoutes(fastify: unknown) {
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

        reply.send(result);
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
          required: ['refreshToken'],
          properties: {
            refreshToken: { type: 'string' },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const { refreshToken } = request.body as RefreshRequestBody;

        const result = await authService.refreshTokens(refreshToken);

        reply.send(result);
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
          required: ['refreshToken'],
          properties: {
            refreshToken: { type: 'string' },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const { refreshToken } = request.body as unknown;

        await authService.logout(refreshToken);

        reply.send({ message: 'Logged out successfully' });
      } catch (error) {
        reply.code(400).send({
          error: 'Logout failed',
          message: error.message,
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
          (request as unknown).user.userId
        );

        reply.send(user);
      } catch (error) {
        reply.code(404).send({
          error: 'User not found',
          message: error.message,
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
    const authHeader = request.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return reply.code(401).send({
        error: 'Authentication required',
        message: 'No token provided',
      });
    }

    const token = authHeader.substring(7);
    const user = await authService.validateAccessToken(token);

    (request as unknown).user = user;
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
      const user = (request as unknown).user;

      if (!user || !hasRole(user.role, role)) {
        return reply.code(403).send({
          error: 'Insufficient permissions',
          message: `Role ${role} required`,
        });
      }
    } catch (error) {
      return reply.code(403).send({
        error: 'Authorization failed',
        message: error.message,
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
