/**
 * @fileoverview Refresh Token Authentication Routes
 * Routes for refresh token management and token refresh flow
 * 
 * @doc.type route
 * @doc.purpose Handle refresh token operations
 * @doc.layer presentation
 * @doc.pattern REST API
 */

import { FastifyPluginAsync } from 'fastify';
import { RefreshTokenService } from '../services/auth/refresh-token-service';
import { generateToken } from '../lib/auth';
import { Logger } from '../lib/logger';

const logger = Logger.create({ component: 'AuthRefreshRoutes' });

/**
 * Refresh token routes plugin
 * @doc.purpose Provide endpoints for token refresh and session management
 */
export const authRefreshRoutes: FastifyPluginAsync = async (fastify) => {
  const refreshTokenService = new RefreshTokenService(fastify.prisma);

  /**
   * POST /auth/refresh
   * Refresh access token using refresh token
   */
  fastify.post<{
    Body: {
      refreshToken: string;
    };
  }>('/auth/refresh', {
    schema: {
      body: {
        type: 'object',
        required: ['refreshToken'],
        properties: {
          refreshToken: { type: 'string' },
        },
      },
      response: {
        200: {
          type: 'object',
          properties: {
            accessToken: { type: 'string' },
            refreshToken: { type: 'string' },
            expiresIn: { type: 'number' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const { refreshToken } = request.body;

    try {
      // Validate refresh token
      const validation = await refreshTokenService.validateRefreshToken(refreshToken);

      if (!validation.valid || !validation.userId) {
        logger.warn('Invalid refresh token attempt', {
          ip: request.ip,
          userAgent: request.headers['user-agent'],
        });
        return reply.code(401).send({
          error: 'Invalid or expired refresh token',
        });
      }

      // Get user details
      const user = await fastify.prisma.user.findUnique({
        where: { id: validation.userId },
        select: {
          id: true,
          email: true,
          role: true,
          isActive: true,
        },
      });

      if (!user || !user.isActive) {
        return reply.code(401).send({
          error: 'User account not found or inactive',
        });
      }

      // Generate new access token
      const accessToken = generateToken({
        userId: user.id,
        email: user.email,
        role: user.role,
      });

      // Generate new refresh token
      const deviceInfo = {
        userAgent: request.headers['user-agent'],
        ipAddress: request.ip,
      };

      const newRefreshToken = await refreshTokenService.generateRefreshToken(
        user.id,
        deviceInfo
      );

      // Revoke old refresh token
      await refreshTokenService.revokeRefreshToken(refreshToken);

      logger.info('Token refreshed successfully', {
        userId: user.id,
      });

      return reply.send({
        accessToken,
        refreshToken: newRefreshToken.token,
        expiresIn: 604800, // 7 days in seconds
      });
    } catch (error) {
      logger.error('Token refresh failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to refresh token',
      });
    }
  });

  /**
   * POST /auth/logout
   * Logout and revoke refresh token
   */
  fastify.post<{
    Body: {
      refreshToken: string;
    };
  }>('/auth/logout', {
    schema: {
      body: {
        type: 'object',
        required: ['refreshToken'],
        properties: {
          refreshToken: { type: 'string' },
        },
      },
      response: {
        200: {
          type: 'object',
          properties: {
            success: { type: 'boolean' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const { refreshToken } = request.body;

    try {
      await refreshTokenService.revokeRefreshToken(refreshToken);

      logger.info('User logged out', {
        ip: request.ip,
      });

      return reply.send({ success: true });
    } catch (error) {
      logger.error('Logout failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to logout',
      });
    }
  });

  /**
   * POST /auth/logout-all
   * Logout from all devices (revoke all refresh tokens)
   */
  fastify.post('/auth/logout-all', {
    onRequest: [fastify.authenticate],
    schema: {
      response: {
        200: {
          type: 'object',
          properties: {
            success: { type: 'boolean' },
            revokedCount: { type: 'number' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const count = await refreshTokenService.revokeAllUserTokens(userId);

      logger.info('All user tokens revoked', {
        userId,
        count,
      });

      return reply.send({
        success: true,
        revokedCount: count,
      });
    } catch (error) {
      logger.error('Logout all failed', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to logout from all devices',
      });
    }
  });

  /**
   * GET /auth/sessions
   * Get active sessions for current user
   */
  fastify.get('/auth/sessions', {
    onRequest: [fastify.authenticate],
    schema: {
      response: {
        200: {
          type: 'object',
          properties: {
            sessions: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  id: { type: 'string' },
                  deviceName: { type: 'string', nullable: true },
                  ipAddress: { type: 'string', nullable: true },
                  createdAt: { type: 'string' },
                  lastUsedAt: { type: 'string', nullable: true },
                  expiresAt: { type: 'string' },
                },
              },
            },
          },
        },
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const tokens = await refreshTokenService.getUserTokens(userId);

      const sessions = tokens.map((token) => ({
        id: token.id,
        deviceName: token.deviceName || null,
        ipAddress: token.ipAddress || null,
        createdAt: token.createdAt.toISOString(),
        lastUsedAt: token.lastUsedAt?.toISOString() || null,
        expiresAt: token.expiresAt.toISOString(),
      }));

      return reply.send({ sessions });
    } catch (error) {
      logger.error('Failed to get sessions', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to get sessions',
      });
    }
  });

  /**
   * DELETE /auth/sessions/:sessionId
   * Revoke specific session
   */
  fastify.delete<{
    Params: {
      sessionId: string;
    };
  }>('/auth/sessions/:sessionId', {
    onRequest: [fastify.authenticate],
    schema: {
      params: {
        type: 'object',
        required: ['sessionId'],
        properties: {
          sessionId: { type: 'string' },
        },
      },
      response: {
        200: {
          type: 'object',
          properties: {
            success: { type: 'boolean' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { sessionId } = request.params;

    try {
      // Get token to verify it belongs to user
      const token = await fastify.prisma.refreshToken.findUnique({
        where: { id: sessionId },
        select: { userId: true, token: true },
      });

      if (!token || token.userId !== userId) {
        return reply.code(404).send({
          error: 'Session not found',
        });
      }

      await refreshTokenService.revokeRefreshToken(token.token);

      logger.info('Session revoked', {
        userId,
        sessionId,
      });

      return reply.send({ success: true });
    } catch (error) {
      logger.error('Failed to revoke session', {
        userId,
        sessionId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to revoke session',
      });
    }
  });
};
