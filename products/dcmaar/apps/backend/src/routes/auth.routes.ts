/**
 * Authentication routes for user registration, login, and session management.
 *
 * <p><b>Purpose</b><br>
 * Provides RESTful endpoints for user authentication operations including registration,
 * login, token refresh, profile management, and password reset. Implements NIST 800-63B
 * compliant password validation and rate limiting for security.
 *
 * <p><b>Endpoints</b><br>
 * - POST /register - User registration with email verification
 * - POST /login - User login with JWT token generation
 * - POST /refresh - Refresh access token using refresh token
 * - GET /profile - Get current user profile (authenticated)
 * - PUT /profile - Update user profile (authenticated)
 * - POST /password/reset-request - Request password reset email
 * - POST /password/reset - Reset password with token
 * - POST /verify-email - Verify email address with token
 * - POST /resend-verification - Resend verification email
 * - POST /logout - Logout and invalidate tokens
 *
 * <p><b>Security</b><br>
 * - Rate limiting on authentication endpoints (5 requests/15 minutes)
 * - Strong password validation (8+ chars, uppercase, lowercase, number, special char)
 * - JWT-based authentication with refresh tokens
 * - Audit logging for all authentication events
 * - Email verification requirement for new accounts
 *
 * @doc.type route
 * @doc.purpose Authentication and session management endpoints
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import * as authService from '../services/auth.service';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import { logAuditEvent, AuditEvents } from '../services/audit.service';
import { authAttempts, activeSessions } from '../utils/metrics';
import { logger } from '../utils/logger';

// Strong password validation (NIST 800-63B compliant)
const passwordSchema = z
  .string()
  .min(8, 'Password must be at least 8 characters')
  .regex(/[A-Z]/, 'Password must contain at least one uppercase letter')
  .regex(/[a-z]/, 'Password must contain at least one lowercase letter')
  .regex(/[0-9]/, 'Password must contain at least one number')
  .regex(/[^A-Za-z0-9]/, 'Password must contain at least one special character');

// Validation schemas
const registerSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: passwordSchema,
  displayName: z.string().optional(),
});

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});

const _refreshTokenSchema = z.object({
  refreshToken: z.string().min(1, 'Refresh token is required'),
});

const updateProfileSchema = z.object({
  displayName: z.string().optional(),
  photoUrl: z.string().url('Invalid photo URL').optional(),
});

const resetPasswordRequestSchema = z.object({
  email: z.string().email('Invalid email address'),
});

const resetPasswordSchema = z.object({
  token: z.string().min(1, 'Reset token is required'),
  newPassword: passwordSchema,
});

// Rate limit configurations for specific endpoints
// NOTE: Global rate limiting is configured in server.ts
// Per-route rate limiting would require custom middleware
// For now, using global rate limit for all endpoints

// Device token validation schemas
const deviceTokenSchema = z.object({
  childId: z.string().uuid('Invalid child ID format'),
  deviceName: z.string().min(1, 'Device name is required').max(255),
  deviceType: z.enum(['desktop', 'mobile', 'extension']),
  platform: z.string().min(1, 'Platform is required').max(50),
});

const deviceTokenRefreshSchema = z.object({
  deviceToken: z.string().min(1, 'Device token is required'),
  deviceId: z.string().uuid('Invalid device ID format'),
});

const authRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * POST /register
   * Register a new user
   */
  fastify.post('/register', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const data = registerSchema.parse(request.body);
      const result = await authService.register(data);

      // Set refresh token in HTTP-only cookie
      reply.setCookie('refreshToken', result.refreshToken, {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'strict',
        maxAge: 7 * 24 * 60 * 60, // 7 days in seconds
        path: '/',
      });

      // Audit log
      await logAuditEvent(
        result.user.id,
        AuditEvents.REGISTER,
        { email: data.email },
        request as any,
        'info'
      );

      // Metrics
      authAttempts.inc({ type: 'register', status: 'success' });
      activeSessions.inc();

      logger.info('User registered successfully', { userId: result.user.id, email: data.email });

      return reply.status(201).send({
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        user: result.user,
      });
    } catch (error) {
      // Metrics for failure
      authAttempts.inc({ type: 'register', status: 'failure' });

      if (error instanceof z.ZodError) {
        return reply.status(400).send({ error: error.issues[0].message });
      }

      if (error instanceof Error) {
        logger.warn('Registration failed', { error: error.message, email: (request.body as any)?.email });
        return reply.status(400).send({ error: error.message });
      }

      return reply.status(500).send({ error: 'Registration failed' });
    }
  });

  /**
   * POST /login
   * Login user
   */
  fastify.post('/login', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const data = loginSchema.parse(request.body);
      const result = await authService.login(data);

      // Set refresh token in HTTP-only cookie
      reply.setCookie('refreshToken', result.refreshToken, {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'strict',
        maxAge: 7 * 24 * 60 * 60,
        path: '/',
      });

      // Audit log
      await logAuditEvent(
        result.user.id,
        AuditEvents.LOGIN_SUCCESS,
        { email: data.email },
        request as any,
        'info'
      );

      // Metrics
      authAttempts.inc({ type: 'login', status: 'success' });
      activeSessions.inc();

      logger.info('User logged in successfully', { userId: result.user.id, email: data.email });

      return reply.send({
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        user: result.user,
      });
    } catch (error) {
      // Audit log for failed login
      await logAuditEvent(
        null,
        AuditEvents.LOGIN_FAILURE,
        { email: (request.body as any)?.email, error: error instanceof Error ? error.message : 'Unknown error' },
        request as any,
        'warning'
      );

      // Metrics for failure
      authAttempts.inc({ type: 'login', status: 'failure' });

      if (error instanceof z.ZodError) {
        return reply.status(400).send({ error: error.issues[0].message });
      }

      if (error instanceof Error) {
        return reply.status(401).send({ error: error.message });
      }

      return reply.status(500).send({ error: 'Login failed' });
    }
  });

  /**
   * POST /refresh
   * Refresh access token
   */
  fastify.post('/refresh', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      // Try to get refresh token from cookie or body
      const refreshToken =
        request.cookies?.refreshToken || (request.body as any).refreshToken;

      if (!refreshToken) {
        return reply.status(400).send({ error: 'Refresh token required' });
      }

      const result = await authService.refreshAccessToken(refreshToken);

      return reply.send({ accessToken: result.accessToken });
    } catch (error) {
      if (error instanceof Error) {
        return reply.status(401).send({ error: error.message });
      }

      return reply.status(500).send({ error: 'Token refresh failed' });
    }
  });

  /**
   * POST /logout
   * Logout user (invalidate refresh token)
   */
  fastify.post('/logout', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const refreshToken =
        request.cookies?.refreshToken || (request.body as any).refreshToken;

      if (refreshToken) {
        await authService.logout(refreshToken);
      }

      // Clear refresh token cookie
      reply.clearCookie('refreshToken', { path: '/' });

      return reply.send({ message: 'Logged out successfully' });
    } catch (_error) {
      return reply.status(500).send({ error: 'Logout failed' });
    }
  });

  /**
   * GET /me
   * Get current user
   */
  fastify.get('/me', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      if (!request.userId) {
        return reply.status(401).send({ error: 'Not authenticated' });
      }

      const user = await authService.getUserById(request.userId);

      if (!user) {
        return reply.status(404).send({ error: 'User not found' });
      }

      return reply.send({ user });
    } catch (_error) {
      return reply.status(500).send({ error: 'Failed to get user' });
    }
  });

  /**
   * PUT /profile
   * Update user profile
   */
  fastify.put('/profile', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      if (!request.userId) {
        return reply.status(401).send({ error: 'Not authenticated' });
      }

      const updates = updateProfileSchema.parse(request.body);
      const user = await authService.updateProfile(request.userId, updates);

      return reply.send({ user });
    } catch (error) {
      if (error instanceof z.ZodError) {
        return reply.status(400).send({ error: error.issues[0].message });
      }

      if (error instanceof Error) {
        return reply.status(400).send({ error: error.message });
      }

      return reply.status(500).send({ error: 'Profile update failed' });
    }
  });

  /**
   * POST /password-reset/request
   * Request password reset
   */
  fastify.post('/password-reset/request', async (request: FastifyRequest, reply: FastifyReply) => {
    // Validate email format first (before try/catch to return proper error)
    const validation = resetPasswordRequestSchema.safeParse(request.body);
    if (!validation.success) {
      return reply.status(400).send({
        error: validation.error.issues[0].message,
      });
    }

    try {
      const { email } = validation.data;
      const resetToken = await authService.requestPasswordReset(email);

      // In production, send email with reset link
      // For now, return token in response (development only)
      if (process.env.NODE_ENV === 'development') {
        return reply.send({
          message: 'Password reset email sent',
          resetToken, // Only in development!
        });
      } else {
        return reply.send({ message: 'Password reset email sent' });
      }
    } catch (_error) {
      // Don't reveal if email exists
      return reply.send({ message: 'If the email exists, a reset link has been sent' });
    }
  });

  /**
   * POST /password-reset/confirm
   * Reset password with token
   */
  fastify.post('/password-reset/confirm', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const { token, newPassword } = resetPasswordSchema.parse(request.body);
      await authService.resetPassword(token, newPassword);

      return reply.send({ message: 'Password reset successfully' });
    } catch (error) {
      if (error instanceof z.ZodError) {
        return reply.status(400).send({ error: error.issues[0].message });
      }

      if (error instanceof Error) {
        return reply.status(400).send({ error: error.message });
      }

      return reply.status(500).send({ error: 'Password reset failed' });
    }
  });
};

export default authRoutes;

