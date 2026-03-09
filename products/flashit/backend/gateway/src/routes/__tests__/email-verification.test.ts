/**
 * Email Verification Route Tests
 *
 * Tests for POST /auth/verify-email and POST /auth/resend-verification.
 * Covers token validation, expiry, already-verified, and resend rate limiting.
 *
 * @doc.type test
 * @doc.purpose Test email verification flow
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock email verification service
const mockSendVerificationEmail = vi.fn().mockResolvedValue(undefined);
const mockVerifyEmailToken = vi.fn();
const mockResendVerificationEmail = vi.fn();

vi.mock('../../services/security/email-verification-service', () => ({
  sendVerificationEmail: (...args: any[]) => mockSendVerificationEmail(...args),
  verifyEmailToken: (...args: any[]) => mockVerifyEmailToken(...args),
  resendVerificationEmail: (...args: any[]) => mockResendVerificationEmail(...args),
}));

// Mock prisma
vi.mock('../../lib/prisma', () => ({
  prisma: {
    user: {
      findUnique: vi.fn().mockResolvedValue({
        id: 'user-1',
        email: 'test@example.com',
        emailVerified: false,
        passwordHash: '$2b$12$dummyhash',
        displayName: 'Test User',
        twoFactorEnabled: false,
        deletedAt: null,
        lockedUntil: null,
        failedLoginAttempts: 0,
      }),
      create: vi.fn().mockResolvedValue({
        id: 'user-new',
        email: 'new@example.com',
        displayName: 'New User',
        createdAt: new Date(),
      }),
      update: vi.fn().mockResolvedValue({}),
    },
    sphere: { create: vi.fn().mockResolvedValue({}) },
    sphereAccess: { create: vi.fn().mockResolvedValue({}) },
    auditEvent: { create: vi.fn().mockResolvedValue({}) },
    securityAuditLog: { create: vi.fn().mockResolvedValue({}) },
    userTierSettings: { create: vi.fn().mockResolvedValue({}) },
  },
}));

// Mock security middleware
vi.mock('../../middleware/security', () => ({
  checkAccountLockout: vi.fn().mockResolvedValue(false),
  incrementFailedLoginAttempts: vi.fn().mockResolvedValue(undefined),
  resetFailedLoginAttempts: vi.fn().mockResolvedValue(undefined),
}));

// Mock security services
vi.mock('../../services/security/refresh-token-service', () => ({
  refreshTokenService: {
    generateRefreshToken: vi.fn().mockResolvedValue({ token: 'rt_mock', id: 'rtid-1' }),
    rotateRefreshToken: vi.fn(),
    validateRefreshToken: vi.fn(),
    revokeRefreshToken: vi.fn(),
  },
}));

vi.mock('../../services/security/two-factor-service', () => ({
  twoFactorAuthService: {
    setupTwoFactor: vi.fn(),
    verifyTOTP: vi.fn(),
    verifyAndEnable: vi.fn(),
    disableTwoFactor: vi.fn(),
    verifyBackupCode: vi.fn(),
    getTwoFactorStatus: vi.fn().mockResolvedValue({ enabled: false }),
  },
}));

vi.mock('../../services/security/session-management-service', () => ({
  sessionManagementService: {
    createSession: vi.fn().mockResolvedValue({ id: 'session-1' }),
    getActiveSessions: vi.fn().mockResolvedValue([]),
    getSession: vi.fn(),
    revokeSession: vi.fn(),
    getSessionStats: vi.fn().mockResolvedValue({ active: 1, total: 1 }),
  },
}));

vi.mock('../../services/security/password-reset-service', () => ({
  passwordResetService: {
    requestPasswordReset: vi.fn(),
    verifyResetToken: vi.fn(),
    resetPassword: vi.fn(),
  },
}));

vi.mock('../../lib/auth', async () => {
  const actual = await vi.importActual('../../lib/auth');
  return {
    ...actual,
    hashPassword: vi.fn().mockResolvedValue('$2b$12$mockhashedpassword'),
    comparePassword: vi.fn().mockResolvedValue(true),
    requireAuth: vi.fn().mockImplementation(async () => {}),
  };
});

import { registerAuthRoutesEnhanced } from '../auth-enhanced';

describe('Email Verification Routes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify();
    await app.register(jwt, { secret: 'test-secret-key' });

    app.decorate('authenticate', async (request: any, reply: any) => {
      try {
        await request.jwtVerify();
      } catch {
        reply.code(401).send({ error: 'Unauthorized' });
      }
    });

    await app.register(registerAuthRoutesEnhanced);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('POST /auth/verify-email', () => {
    it('should verify email with valid token', async () => {
      mockVerifyEmailToken.mockResolvedValueOnce({ verified: true, userId: 'user-1' });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/verify-email',
        payload: { token: 'valid-token-hex' },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.verified).toBe(true);
      expect(body.message).toBe('Email verified successfully');
    });

    it('should reject invalid verification token', async () => {
      mockVerifyEmailToken.mockResolvedValueOnce({
        verified: false,
        reason: 'Invalid verification token',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/verify-email',
        payload: { token: 'invalid-token' },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Verification failed');
    });

    it('should reject expired verification token', async () => {
      mockVerifyEmailToken.mockResolvedValueOnce({
        verified: false,
        reason: 'Verification token has expired',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/verify-email',
        payload: { token: 'expired-token' },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('expired');
    });

    it('should reject already-used verification token', async () => {
      mockVerifyEmailToken.mockResolvedValueOnce({
        verified: false,
        reason: 'Token has already been used',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/verify-email',
        payload: { token: 'used-token' },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('already been used');
    });

    it('should fail with missing token field', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/verify-email',
        payload: {},
      });

      // Zod validation should fail
      expect(response.statusCode).toBeGreaterThanOrEqual(400);
    });
  });

  describe('POST /auth/resend-verification', () => {
    it('should resend verification email for unverified user', async () => {
      mockResendVerificationEmail.mockResolvedValueOnce({ sent: true });

      const token = app.jwt.sign({ userId: 'user-1', email: 'test@example.com' });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/resend-verification',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.sent).toBe(true);
    });

    it('should return sent:false if already verified', async () => {
      const { prisma } = await import('../../lib/prisma');
      (prisma.user.findUnique as any).mockResolvedValueOnce({
        id: 'user-1',
        email: 'test@example.com',
        emailVerified: true,
      });

      const token = app.jwt.sign({ userId: 'user-1', email: 'test@example.com' });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/resend-verification',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.sent).toBe(false);
    });

    it('should rate-limit resend requests', async () => {
      mockResendVerificationEmail.mockResolvedValueOnce({
        sent: false,
        reason: 'Verification email was sent recently. Please wait a few minutes before requesting again.',
      });

      const token = app.jwt.sign({ userId: 'user-1', email: 'test@example.com' });

      const response = await app.inject({
        method: 'POST',
        url: '/auth/resend-verification',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(429);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('wait');
    });

    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/resend-verification',
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('POST /auth/register (verification email)', () => {
    it('should send verification email on registration', async () => {
      mockSendVerificationEmail.mockClear();

      const response = await app.inject({
        method: 'POST',
        url: '/auth/register',
        payload: {
          email: 'newuser@example.com',
          password: 'SecurePass123!',
          displayName: 'New User',
        },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.emailVerificationSent).toBe(true);

      // Verify sendVerificationEmail was called (fire-and-forget)
      // Give it a tick to fire
      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(mockSendVerificationEmail).toHaveBeenCalled();
    });
  });
});
