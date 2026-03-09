/**
 * Enhanced Authentication Routes with Security Features
 * 
 * @doc.type routes
 * @doc.purpose Comprehensive auth endpoints with 2FA, refresh tokens, sessions, and password reset
 * @doc.layer product
 * @doc.pattern Routes
 * 
 * @description Provides complete authentication flow:
 * - Registration with email verification
 * - Login with 2FA support
 * - Refresh token rotation
 * - Session management
 * - Password reset flow
 * - 2FA setup and management
 * - Account security settings
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { hashPassword, comparePassword, JwtPayload } from '../lib/auth';
import { refreshTokenService } from '../services/security/refresh-token-service';
import { twoFactorAuthService } from '../services/security/two-factor-service';
import { sessionManagementService } from '../services/security/session-management-service';
import { passwordResetService } from '../services/security/password-reset-service';
import {
  checkAccountLockout,
  incrementFailedLoginAttempts,
  resetFailedLoginAttempts,
} from '../middleware/security';
import {
  sendVerificationEmail,
  verifyEmailToken,
  resendVerificationEmail,
} from '../services/security/email-verification-service';

// Validation schemas
const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8).max(100),
  displayName: z.string().min(1).max(255).optional(),
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string(),
  twoFactorCode: z.string().optional(),
  deviceName: z.string().optional(),
  deviceType: z.enum(['MOBILE', 'WEB', 'DESKTOP', 'TABLET']).optional(),
});

const refreshTokenSchema = z.object({
  refreshToken: z.string(),
});

const passwordResetRequestSchema = z.object({
  email: z.string().email(),
});

const passwordResetSchema = z.object({
  token: z.string(),
  newPassword: z.string().min(8).max(100),
});

const enable2FASchema = z.object({
  totpCode: z.string().length(6),
});

const disable2FASchema = z.object({
  password: z.string(),
});

export const registerAuthRoutesEnhanced = async (app: FastifyInstance) => {
  /**
   * POST /auth/register
   * Register a new user with tier settings
   */
  app.post('/auth/register', async (request, reply) => {
    const body = registerSchema.parse(request.body);

    // Check if user exists
    const existing = await prisma.user.findUnique({
      where: { email: body.email },
    });

    if (existing) {
      return reply.code(409).send({
        error: 'User already exists',
        message: 'A user with this email already exists',
      });
    }

    // Hash password
    const passwordHash = await hashPassword(body.password);

    // Create user with tier settings
    const user = await prisma.user.create({
      data: {
        email: body.email,
        passwordHash,
        displayName: body.displayName,
        tierSettings: {
          create: {
            tier: 'FREE',
            requestsPerMinute: 10,
            requestsPerHour: 100,
            requestsPerDay: 1000,
            maxSessions: 3,
            storageLimitBytes: 1073741824, // 1GB
          },
        },
      },
      select: {
        id: true,
        email: true,
        displayName: true,
        createdAt: true,
      },
    });

    // Create default personal sphere
    await prisma.sphere.create({
      data: {
        userId: user.id,
        name: 'Personal',
        description: 'Your personal thoughts and moments',
        type: 'PERSONAL',
        visibility: 'PRIVATE',
        sphereAccess: {
          create: {
            userId: user.id,
            role: 'OWNER',
            grantedBy: user.id,
          },
        },
      },
    });

    // Generate tokens
    const accessToken = app.jwt.sign({
      userId: user.id,
      email: user.email,
    } as JwtPayload);

    const refreshToken = await refreshTokenService.generateRefreshToken({
      userId: user.id,
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
    });

    // Create session
    const session = await sessionManagementService.createSession({
      userId: user.id,
      refreshTokenId: undefined,
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
    });

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: 'USER_REGISTERED',
        userId: user.id,
        actor: user.email,
        action: 'REGISTER',
        resourceType: 'USER',
        resourceId: user.id,
      },
    });

    // Send verification email (fire-and-forget — don't block registration)
    sendVerificationEmail(user.id, user.email).catch((err) => {
      app.log.error({ err, userId: user.id }, 'Failed to send verification email');
    });

    return reply.code(201).send({
      user,
      accessToken,
      refreshToken: refreshToken.token,
      sessionId: session.id,
      expiresIn: 3600, // 1 hour
      emailVerificationSent: true,
    });
  });

  /**
   * POST /auth/login
   * Login with 2FA support
   */
  app.post('/auth/login', async (request, reply) => {
    const body = loginSchema.parse(request.body);

    // Find user
    const user = await prisma.user.findUnique({
      where: { email: body.email },
      select: {
        id: true,
        email: true,
        displayName: true,
        passwordHash: true,
        twoFactorEnabled: true,
        deletedAt: true,
        lockedUntil: true,
        failedLoginAttempts: true,
      },
    });

    if (!user || user.deletedAt) {
      return reply.code(401).send({
        error: 'Invalid credentials',
        message: 'Email or password is incorrect',
      });
    }

    // Check account lockout
    const isLocked = await checkAccountLockout(user.id);
    if (isLocked) {
      return reply.code(403).send({
        error: 'Account locked',
        message: 'Account is temporarily locked due to too many failed login attempts',
      });
    }

    // Verify password
    const isValid = await comparePassword(body.password, user.passwordHash);
    if (!isValid) {
      await incrementFailedLoginAttempts(user.id);

      await prisma.securityAuditLog.create({
        data: {
          userId: user.id,
          eventType: 'LOGIN_FAILED',
          severity: 'WARNING',
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'],
          success: false,
          details: { reason: 'INVALID_PASSWORD' },
        },
      });

      return reply.code(401).send({
        error: 'Invalid credentials',
        message: 'Email or password is incorrect',
      });
    }

    // Check 2FA if enabled
    if (user.twoFactorEnabled) {
      if (!body.twoFactorCode) {
        return reply.code(403).send({
          error: '2FA required',
          message: 'Two-factor authentication code required',
          requiresTwoFactor: true,
        });
      }

      // Verify TOTP or backup code
      let is2FAValid = await twoFactorAuthService.verifyTOTP(user.id, body.twoFactorCode);

      if (!is2FAValid) {
        is2FAValid = await twoFactorAuthService.verifyBackupCode(user.id, body.twoFactorCode);
      }

      if (!is2FAValid) {
        await incrementFailedLoginAttempts(user.id);

        await prisma.securityAuditLog.create({
          data: {
            userId: user.id,
            eventType: 'LOGIN_FAILED',
            severity: 'WARNING',
            ipAddress: request.ip,
            userAgent: request.headers['user-agent'],
            success: false,
            details: { reason: 'INVALID_2FA_CODE' },
          },
        });

        return reply.code(403).send({
          error: 'Invalid 2FA code',
          message: 'The two-factor authentication code is invalid',
        });
      }
    }

    // Reset failed attempts
    await resetFailedLoginAttempts(user.id);

    // Generate tokens
    const accessToken = app.jwt.sign({
      userId: user.id,
      email: user.email,
    } as JwtPayload);

    const refreshTokenData = await refreshTokenService.generateRefreshToken({
      userId: user.id,
      deviceInfo: {
        name: body.deviceName,
        type: body.deviceType,
      },
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
    });

    // Create session
    const session = await sessionManagementService.createSession({
      userId: user.id,
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
    });

    // Update last login
    await prisma.user.update({
      where: { id: user.id },
      data: { lastLoginAt: new Date() },
    });

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: 'USER_LOGIN',
        userId: user.id,
        actor: user.email,
        action: 'LOGIN',
        resourceType: 'USER',
        resourceId: user.id,
        ipAddress: request.ip,
        userAgent: request.headers['user-agent'],
      },
    });

    await prisma.securityAuditLog.create({
      data: {
        userId: user.id,
        eventType: 'LOGIN_SUCCESS',
        severity: 'INFO',
        ipAddress: request.ip,
        userAgent: request.headers['user-agent'],
        success: true,
      },
    });

    return reply.send({
      user: {
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        twoFactorEnabled: user.twoFactorEnabled,
      },
      accessToken,
      refreshToken: refreshTokenData.token,
      sessionId: session.id,
      expiresIn: 3600,
    });
  });

  /**
   * POST /auth/refresh
   * Rotate refresh token and get new access token
   */
  app.post('/auth/refresh', async (request, reply) => {
    const body = refreshTokenSchema.parse(request.body);

    const newTokenData = await refreshTokenService.rotateRefreshToken(body.refreshToken);

    if (!newTokenData) {
      return reply.code(401).send({
        error: 'Invalid refresh token',
        message: 'The refresh token is invalid or expired',
      });
    }

    const userId = await refreshTokenService.validateRefreshToken(newTokenData.token);
    if (!userId) {
      return reply.code(401).send({
        error: 'Invalid token',
      });
    }

    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, email: true, displayName: true },
    });

    if (!user) {
      return reply.code(404).send({ error: 'User not found' });
    }

    const accessToken = app.jwt.sign({
      userId: user.id,
      email: user.email,
    } as JwtPayload);

    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: 'TOKEN_REFRESH',
        severity: 'INFO',
        ipAddress: request.ip,
        success: true,
      },
    });

    return reply.send({
      accessToken,
      refreshToken: newTokenData.token,
      expiresIn: 3600,
    });
  });

  /**
   * POST /auth/logout
   * Logout and revoke tokens/session
   */
  app.post(
    '/auth/logout',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const user = request.user as JwtPayload;
      const refreshToken = request.headers['x-refresh-token'] as string | undefined;
      const sessionId = request.headers['x-session-id'] as string | undefined;

      // Revoke refresh token if provided
      if (refreshToken) {
        const tokenHash = require('crypto')
          .createHash('sha256')
          .update(refreshToken)
          .digest('hex');
        await refreshTokenService.revokeRefreshToken(tokenHash, 'USER_LOGOUT');
      }

      // Revoke session if provided
      if (sessionId) {
        await sessionManagementService.revokeSession(sessionId, 'USER_LOGOUT');
      }

      await prisma.auditEvent.create({
        data: {
          eventType: 'USER_LOGOUT',
          userId: user.userId,
          actor: user.email,
          action: 'LOGOUT',
          ipAddress: request.ip,
        },
      });

      return reply.send({ message: 'Logged out successfully' });
    }
  );

  /**
   * POST /auth/password-reset/request
   * Request password reset
   */
  app.post('/auth/password-reset/request', async (request, reply) => {
    const body = passwordResetRequestSchema.parse(request.body);

    try {
      const token = await passwordResetService.requestPasswordReset(
        body.email,
        request.ip,
        request.headers['user-agent']
      );

      // Always return success to prevent user enumeration
      return reply.send({
        message: 'If the email exists, a password reset link has been sent',
      });
    } catch (error: any) {
      if (error.message.includes('locked') || error.message.includes('many')) {
        return reply.code(429).send({
          error: 'Too many requests',
          message: error.message,
        });
      }
      throw error;
    }
  });

  /**
   * POST /auth/password-reset/verify
   * Verify reset token
   */
  app.post('/auth/password-reset/verify', async (request, reply) => {
    const { token } = z.object({ token: z.string() }).parse(request.body);

    const isValid = await passwordResetService.verifyResetToken(token);

    return reply.send({ valid: isValid });
  });

  /**
   * POST /auth/password-reset/complete
   * Complete password reset
   */
  app.post('/auth/password-reset/complete', async (request, reply) => {
    const body = passwordResetSchema.parse(request.body);

    const success = await passwordResetService.resetPassword(
      body.token,
      body.newPassword,
      request.ip,
      request.headers['user-agent']
    );

    if (!success) {
      return reply.code(400).send({
        error: 'Invalid token',
        message: 'The reset token is invalid or expired',
      });
    }

    return reply.send({ message: 'Password reset successful' });
  });

  /**
   * POST /auth/2fa/setup
   * Setup 2FA for user
   */
  app.post(
    '/auth/2fa/setup',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const user = request.user as JwtPayload;

      const userData = await prisma.user.findUnique({
        where: { id: user.userId },
        select: { email: true },
      });

      if (!userData) {
        return reply.code(404).send({ error: 'User not found' });
      }

      const setup = await twoFactorAuthService.setupTwoFactor(user.userId, userData.email);

      return reply.send({
        secret: setup.secret,
        qrCode: setup.qrCodeUrl,
        backupCodes: setup.backupCodes,
      });
    }
  );

  /**
   * POST /auth/2fa/enable
   * Enable 2FA after verification
   */
  app.post(
    '/auth/2fa/enable',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const user = request.user as JwtPayload;
      const body = enable2FASchema.parse(request.body);

      const success = await twoFactorAuthService.verifyAndEnable(user.userId, body.totpCode);

      if (!success) {
        return reply.code(400).send({
          error: 'Invalid code',
          message: 'The verification code is invalid',
        });
      }

      return reply.send({ message: '2FA enabled successfully' });
    }
  );

  /**
   * POST /auth/2fa/disable
   * Disable 2FA
   */
  app.post(
    '/auth/2fa/disable',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const user = request.user as JwtPayload;
      const body = disable2FASchema.parse(request.body);

      // Verify password
      const userData = await prisma.user.findUnique({
        where: { id: user.userId },
        select: { passwordHash: true },
      });

      if (!userData) {
        return reply.code(404).send({ error: 'User not found' });
      }

      const isValid = await comparePassword(body.password, userData.passwordHash);
      if (!isValid) {
        return reply.code(401).send({
          error: 'Invalid password',
        });
      }

      await twoFactorAuthService.disableTwoFactor(user.userId);

      return reply.send({ message: '2FA disabled successfully' });
    }
  );

  /**
   * GET /auth/sessions
   * Get active sessions
   */
  app.get(
    '/auth/sessions',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const user = request.user as JwtPayload;

      const sessions = await sessionManagementService.getActiveSessions(user.userId);

      return reply.send({ sessions });
    }
  );

  /**
   * DELETE /auth/sessions/:sessionId
   * Revoke specific session
   */
  app.delete(
    '/auth/sessions/:sessionId',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const { sessionId } = request.params as { sessionId: string };
      const user = request.user as JwtPayload;

      // Verify session belongs to user
      const session = await sessionManagementService.getSession(sessionId);
      if (!session || session.userId !== user.userId) {
        return reply.code(404).send({ error: 'Session not found' });
      }

      await sessionManagementService.revokeSession(sessionId, 'USER_REVOKED');

      return reply.send({ message: 'Session revoked successfully' });
    }
  );

  /**
   * GET /auth/me
   * Get current user with security info
   */
  app.get(
    '/auth/me',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const jwtUser = request.user as JwtPayload;

      const user = await prisma.user.findUnique({
        where: { id: jwtUser.userId },
        select: {
          id: true,
          email: true,
          displayName: true,
          twoFactorEnabled: true,
          lastLoginAt: true,
          createdAt: true,
          updatedAt: true,
          tierSettings: {
            select: {
              tier: true,
              requestsPerMinute: true,
              requestsPerHour: true,
              requestsPerDay: true,
              maxSessions: true,
            },
          },
        },
      });

      if (!user) {
        return reply.code(404).send({ error: 'User not found' });
      }

      const sessionStats = await sessionManagementService.getSessionStats(user.id);
      const twoFactorStatus = await twoFactorAuthService.getTwoFactorStatus(user.id);

      return reply.send({
        user,
        security: {
          twoFactor: twoFactorStatus,
          sessions: sessionStats,
        },
      });
    }
  );

  /**
   * POST /auth/verify-email
   * Verify email address using token from verification email
   */
  app.post('/auth/verify-email', async (request, reply) => {
    const { token } = z.object({ token: z.string().min(1) }).parse(request.body);

    const result = await verifyEmailToken(token);

    if (!result.verified) {
      return reply.code(400).send({
        error: 'Verification failed',
        message: result.reason || 'Invalid verification token',
      });
    }

    return reply.send({
      message: 'Email verified successfully',
      verified: true,
    });
  });

  /**
   * POST /auth/resend-verification
   * Resend email verification link (authenticated, rate-limited)
   */
  app.post(
    '/auth/resend-verification',
    { onRequest: [(app as any).authenticate] },
    async (request, reply) => {
      const user = request.user as JwtPayload;

      const userData = await prisma.user.findUnique({
        where: { id: user.userId },
        select: { email: true, emailVerified: true },
      });

      if (!userData) {
        return reply.code(404).send({ error: 'User not found' });
      }

      if (userData.emailVerified) {
        return reply.send({ message: 'Email is already verified', sent: false });
      }

      const result = await resendVerificationEmail(user.userId, userData.email);

      if (!result.sent) {
        return reply.code(429).send({
          error: 'Rate limited',
          message: result.reason || 'Please wait before requesting again',
        });
      }

      return reply.send({ message: 'Verification email sent', sent: true });
    }
  );
};
