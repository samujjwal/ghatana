/**
 * @fileoverview Two-Factor Authentication Routes
 * Routes for 2FA setup, verification, and management
 * 
 * @doc.type route
 * @doc.purpose Handle two-factor authentication operations
 * @doc.layer presentation
 * @doc.pattern REST API
 */

import { FastifyPluginAsync } from 'fastify';
import { TwoFactorService } from '../services/auth/two-factor-service';
import { Logger } from '../lib/logger';

const logger = Logger.create({ component: 'Auth2FARoutes' });

/**
 * Two-factor authentication routes plugin
 * @doc.purpose Provide endpoints for 2FA management
 */
export const auth2FARoutes: FastifyPluginAsync = async (fastify) => {
  const twoFactorService = new TwoFactorService(fastify.prisma);

  /**
   * POST /auth/2fa/setup
   * Initialize 2FA setup (returns QR code and backup codes)
   */
  fastify.post('/auth/2fa/setup', {
    onRequest: [fastify.authenticate],
    schema: {
      response: {
        200: {
          type: 'object',
          properties: {
            secret: { type: 'string' },
            qrCodeUrl: { type: 'string' },
            backupCodes: {
              type: 'array',
              items: { type: 'string' },
            },
          },
        },
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const userEmail = request.user.email;

    try {
      const setup = await twoFactorService.enableTwoFactor(userId, userEmail);

      logger.info('2FA setup initiated', { userId });

      return reply.send(setup);
    } catch (error) {
      logger.error('2FA setup failed', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      if (error instanceof Error && error.message.includes('already enabled')) {
        return reply.code(400).send({
          error: 'Two-factor authentication is already enabled',
        });
      }

      return reply.code(500).send({
        error: 'Failed to setup 2FA',
      });
    }
  });

  /**
   * POST /auth/2fa/activate
   * Activate 2FA by verifying initial token
   */
  fastify.post<{
    Body: {
      token: string;
    };
  }>('/auth/2fa/activate', {
    onRequest: [fastify.authenticate],
    schema: {
      body: {
        type: 'object',
        required: ['token'],
        properties: {
          token: { type: 'string', minLength: 6, maxLength: 6 },
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
    const { token } = request.body;

    try {
      const result = await twoFactorService.activateTwoFactor(userId, token);

      if (!result.valid) {
        return reply.code(400).send({
          error: result.error || 'Invalid verification code',
        });
      }

      logger.info('2FA activated', { userId });

      return reply.send({ success: true });
    } catch (error) {
      logger.error('2FA activation failed', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to activate 2FA',
      });
    }
  });

  /**
   * POST /auth/2fa/verify
   * Verify 2FA token during login
   */
  fastify.post<{
    Body: {
      userId: string;
      token: string;
    };
  }>('/auth/2fa/verify', {
    schema: {
      body: {
        type: 'object',
        required: ['userId', 'token'],
        properties: {
          userId: { type: 'string' },
          token: { type: 'string', minLength: 6, maxLength: 6 },
        },
      },
      response: {
        200: {
          type: 'object',
          properties: {
            valid: { type: 'boolean' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const { userId, token } = request.body;

    try {
      const result = await twoFactorService.verifyTwoFactor(userId, token);

      if (result.valid) {
        logger.info('2FA verification successful', { userId });
      } else {
        logger.warn('2FA verification failed', { userId });
      }

      return reply.send({ valid: result.valid });
    } catch (error) {
      logger.error('2FA verification error', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to verify 2FA',
      });
    }
  });

  /**
   * POST /auth/2fa/verify-backup
   * Verify backup code during login
   */
  fastify.post<{
    Body: {
      userId: string;
      code: string;
    };
  }>('/auth/2fa/verify-backup', {
    schema: {
      body: {
        type: 'object',
        required: ['userId', 'code'],
        properties: {
          userId: { type: 'string' },
          code: { type: 'string' },
        },
      },
      response: {
        200: {
          type: 'object',
          properties: {
            valid: { type: 'boolean' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const { userId, code } = request.body;

    try {
      const result = await twoFactorService.verifyBackupCode(userId, code);

      if (result.valid) {
        logger.info('Backup code verification successful', { userId });
      } else {
        logger.warn('Backup code verification failed', { userId });
      }

      return reply.send({ valid: result.valid });
    } catch (error) {
      logger.error('Backup code verification error', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to verify backup code',
      });
    }
  });

  /**
   * POST /auth/2fa/disable
   * Disable 2FA for current user
   */
  fastify.post<{
    Body: {
      password: string;
    };
  }>('/auth/2fa/disable', {
    onRequest: [fastify.authenticate],
    schema: {
      body: {
        type: 'object',
        required: ['password'],
        properties: {
          password: { type: 'string' },
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
    const { password } = request.body;

    try {
      // Verify password before disabling 2FA
      const user = await fastify.prisma.user.findUnique({
        where: { id: userId },
        select: { passwordHash: true },
      });

      if (!user) {
        return reply.code(404).send({
          error: 'User not found',
        });
      }

      const bcrypt = await import('bcrypt');
      const isValidPassword = await bcrypt.compare(password, user.passwordHash);

      if (!isValidPassword) {
        logger.warn('2FA disable attempt with invalid password', { userId });
        return reply.code(401).send({
          error: 'Invalid password',
        });
      }

      await twoFactorService.disableTwoFactor(userId);

      logger.info('2FA disabled', { userId });

      return reply.send({ success: true });
    } catch (error) {
      logger.error('Failed to disable 2FA', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to disable 2FA',
      });
    }
  });

  /**
   * POST /auth/2fa/regenerate-codes
   * Generate new backup codes
   */
  fastify.post('/auth/2fa/regenerate-codes', {
    onRequest: [fastify.authenticate],
    schema: {
      response: {
        200: {
          type: 'object',
          properties: {
            backupCodes: {
              type: 'array',
              items: { type: 'string' },
            },
          },
        },
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const backupCodes = await twoFactorService.regenerateBackupCodes(userId);

      logger.info('Backup codes regenerated', { userId });

      return reply.send({ backupCodes });
    } catch (error) {
      logger.error('Failed to regenerate backup codes', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      if (error instanceof Error && error.message.includes('not enabled')) {
        return reply.code(400).send({
          error: '2FA is not enabled',
        });
      }

      return reply.code(500).send({
        error: 'Failed to regenerate backup codes',
      });
    }
  });

  /**
   * GET /auth/2fa/status
   * Get 2FA status for current user
   */
  fastify.get('/auth/2fa/status', {
    onRequest: [fastify.authenticate],
    schema: {
      response: {
        200: {
          type: 'object',
          properties: {
            enabled: { type: 'boolean' },
            backupCodesRemaining: { type: 'number', nullable: true },
          },
        },
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const status = await twoFactorService.getTwoFactorStatus(userId);

      return reply.send(status);
    } catch (error) {
      logger.error('Failed to get 2FA status', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return reply.code(500).send({
        error: 'Failed to get 2FA status',
      });
    }
  });
};
