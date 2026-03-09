/**
 * Authentication API Routes
 * 
 * Complete JWT-based authentication endpoints for production
 * 
 * @doc.type route
 * @doc.purpose Authentication API endpoints
 * @doc.layer api
 * @doc.pattern REST API
 */

import { FastifyRequest, FastifyReply } from 'fastify';
import { AuthService } from '../services/auth/auth.service';

// Initialize auth service
const authService = new AuthService();

// ============================================================================
// Authentication Routes
// ============================================================================

export async function authRoutes(fastify: unknown) {
  // Register endpoint
  fastify.post('/auth/register', {
    schema: {
      body: {
        type: 'object',
        required: ['email', 'password', 'name'],
        properties: {
          email: { type: 'string', format: 'email' },
          password: { type: 'string', minLength: 8 },
          name: { type: 'string', minLength: 2 },
        },
      },
      response: {
        201: {
          type: 'object',
          properties: {
            user: { $ref: '#/schemas/User' },
            tokens: { $ref: '#/schemas/AuthTokens' },
          },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { email, password, name } = request.body as unknown;
      
      const result = await authService.register({
        email,
        password,
        name,
      });

      reply.code(201).send(result);
    } catch (error) {
      reply.code(400).send({
        error: 'Registration failed',
        message: error.message,
      });
    }
  });

  // Login endpoint
  fastify.post('/auth/login', {
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
            user: { $ref: '#/schemas/User' },
            tokens: { $ref: '#/schemas/AuthTokens' },
          },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { email, password } = request.body as unknown;
      
      const result = await authService.login({
        email,
        password,
      });

      reply.send(result);
    } catch (error) {
      reply.code(401).send({
        error: 'Authentication failed',
        message: error.message,
      });
    }
  });

  // Refresh token endpoint
  fastify.post('/auth/refresh', {
    schema: {
      body: {
        type: 'object',
        required: ['refreshToken'],
        properties: {
          refreshToken: { type: 'string' },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { refreshToken } = request.body as unknown;
      
      const result = await authService.refreshToken(refreshToken);

      reply.send(result);
    } catch (error) {
      reply.code(401).send({
        error: 'Token refresh failed',
        message: error.message,
      });
    }
  });

  // Logout endpoint
  fastify.post('/auth/logout', {
    schema: {
      body: {
        type: 'object',
        required: ['refreshToken'],
        properties: {
          refreshToken: { type: 'string' },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
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
  });

  // Password reset request
  fastify.post('/auth/forgot-password', {
    schema: {
      body: {
        type: 'object',
        required: ['email'],
        properties: {
          email: { type: 'string', format: 'email' },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { email } = request.body as unknown;
      
      await authService.requestPasswordReset(email);

      reply.send({ message: 'Password reset email sent' });
    } catch (error) {
      reply.code(400).send({
        error: 'Password reset request failed',
        message: error.message,
      });
    }
  });

  // Password reset confirmation
  fastify.post('/auth/reset-password', {
    schema: {
      body: {
        type: 'object',
        required: ['token', 'newPassword'],
        properties: {
          token: { type: 'string' },
          newPassword: { type: 'string', minLength: 8 },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { token, newPassword } = request.body as unknown;
      
      await authService.resetPassword(token, newPassword);

      reply.send({ message: 'Password reset successfully' });
    } catch (error) {
      reply.code(400).send({
        error: 'Password reset failed',
        message: error.message,
      });
    }
  });

  // Verify email
  fastify.post('/auth/verify-email', {
    schema: {
      body: {
        type: 'object',
        required: ['token'],
        properties: {
          token: { type: 'string' },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { token } = request.body as unknown;
      
      await authService.verifyEmail(token);

      reply.send({ message: 'Email verified successfully' });
    } catch (error) {
      reply.code(400).send({
        error: 'Email verification failed',
        message: error.message,
      });
    }
  });

  // Get current user
  fastify.get('/auth/me', {
    preHandler: [authenticateToken],
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const user = await authService.getCurrentUser((request as unknown).user.userId);
      
      reply.send(user);
    } catch (error) {
      reply.code(404).send({
        error: 'User not found',
        message: error.message,
      });
    }
  });

  // Change password
  fastify.post('/auth/change-password', {
    preHandler: [authenticateToken],
    schema: {
      body: {
        type: 'object',
        required: ['currentPassword', 'newPassword'],
        properties: {
          currentPassword: { type: 'string' },
          newPassword: { type: 'string', minLength: 8 },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { currentPassword, newPassword } = request.body as unknown;
      const userId = (request as unknown).user.userId;
      
      await authService.changePassword(userId, currentPassword, newPassword);

      reply.send({ message: 'Password changed successfully' });
    } catch (error) {
      reply.code(400).send({
        error: 'Password change failed',
        message: error.message,
      });
    }
  });
}

// ============================================================================
// Authentication Middleware
// ============================================================================

export async function authenticateToken(request: FastifyRequest, reply: FastifyReply) {
  try {
    const authHeader = request.headers.authorization;
    
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return reply.code(401).send({
        error: 'Authentication required',
        message: 'No token provided',
      });
    }

    const token = authHeader.substring(7);
    const user = await authService.verifyToken(token);
    
    (request as unknown).user = user;
  } catch (error) {
    return reply.code(401).send({
      error: 'Invalid token',
      message: error.message,
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
    'VIEWER': 1,
    'EDITOR': 2,
    'ADMIN': 3,
    'OWNER': 4,
  };

  const userLevel = roleHierarchy[userRole as keyof typeof roleHierarchy] || 0;
  const requiredLevel = roleHierarchy[requiredRole as keyof typeof roleHierarchy] || 0;

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
