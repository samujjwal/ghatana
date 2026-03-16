/**
 * Auth Middleware Tests
 *
 * Tests authentication middleware including:
 * - Bearer token extraction
 * - Token verification (local JWT)
 * - Platform token validation via auth-gateway (mocked)
 * - RBAC role assertions via requireRole()
 * - User ID attachment to request
 * - Error handling
 * - Optional authentication
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import jwt from 'jsonwebtoken';
import Fastify, { FastifyInstance } from 'fastify';
import { authenticate, optionalAuthenticate, requireRole } from '../../middleware/auth.middleware';

const JWT_SECRET = process.env.JWT_SECRET ?? 'development-secret-key';

// Local helpers replace the removed authService.generateAccessToken / generateRefreshToken
function generateAccessToken(userId: string): string {
  return jwt.sign({ userId, type: 'access' }, JWT_SECRET, { expiresIn: '15m' });
}
function generateRefreshToken(userId: string): string {
  return jwt.sign({ userId, type: 'refresh' }, JWT_SECRET, { expiresIn: '7d' });
}

// ---------------------------------------------------------------------------
// Mock auth-gateway client — isolates middleware from network calls
// ---------------------------------------------------------------------------

const mockValidate = vi.fn();

vi.mock('../../services/auth-gateway.client', () => ({
  AuthGatewayClient: {
    getInstance: () => ({ validate: mockValidate }),
  },
}));

// ---------------------------------------------------------------------------
// Test setup
// ---------------------------------------------------------------------------

describe('Auth Middleware', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    // Default: gateway returns invalid so tests fall through to local JWT
    mockValidate.mockResolvedValue({ valid: false });

    app = Fastify();

    app.get('/protected', { preHandler: authenticate }, async (request: any) => {
      return {
        userId: request.userId,
        isPlatformToken: request.isPlatformToken ?? false,
        roles: request.roles ?? [],
      };
    });

    app.get('/optional', { preHandler: optionalAuthenticate }, async (request: any) => {
      return { userId: request.userId ?? null };
    });

    app.get('/admin', { preHandler: requireRole('admin') }, async (request: any) => {
      return { userId: request.userId };
    });

    app.get('/reports', { preHandler: requireRole('admin', 'analyst') }, async (request: any) => {
      return { userId: request.userId };
    });

    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    vi.clearAllMocks();
  });

  // -------------------------------------------------------------------------
  // authenticate — local JWT path
  // -------------------------------------------------------------------------

  describe('authenticate (local JWT)', () => {
    it('should authenticate valid local token', async () => {
      const userId = 'test-user-123';
      const token = generateAccessToken(userId);

      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.userId).toBe(userId);
      expect(body.isPlatformToken).toBe(false);
    });

    it('should reject missing authorization header', async () => {
      const response = await app.inject({ method: 'GET', url: '/protected' });

      expect(response.statusCode).toBe(401);
      expect(JSON.parse(response.body).error).toBe('No token provided');
    });

    it('should reject authorization header without Bearer prefix', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: 'InvalidFormat token' },
      });

      expect(response.statusCode).toBe(401);
      expect(JSON.parse(response.body).error).toBe('No token provided');
    });

    it('should reject invalid token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: 'Bearer invalid-token' },
      });

      expect(response.statusCode).toBe(401);
      expect(JSON.parse(response.body).error).toBe('Invalid or expired token');
    });

    it('should reject refresh token used as access token', async () => {
      const refreshToken = generateRefreshToken('user-123');

      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: `Bearer ${refreshToken}` },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should handle malformed tokens gracefully', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: 'Bearer not.a.jwt' },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  // -------------------------------------------------------------------------
  // authenticate — platform token path
  // -------------------------------------------------------------------------

  describe('authenticate (platform token via auth-gateway)', () => {
    it('should authenticate valid platform token and set isPlatformToken=true', async () => {
      const platformUserId = 'platform-user-abc';
      mockValidate.mockResolvedValue({
        valid: true,
        userId: platformUserId,
        email: 'user@ghatana.com',
        roles: ['analyst'],
      });

      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: 'Bearer platform.issued.token' },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.userId).toBe(platformUserId);
      expect(body.isPlatformToken).toBe(true);
      expect(body.roles).toContain('analyst');
    });

    it('should fall back to local JWT when gateway is unreachable', async () => {
      mockValidate.mockRejectedValue(new Error('ECONNREFUSED'));
      const userId = 'local-user-456';
      const token = generateAccessToken(userId);

      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.userId).toBe(userId);
      expect(body.isPlatformToken).toBe(false);
    });

    it('should return 401 when gateway rejects AND local JWT also invalid', async () => {
      mockValidate.mockResolvedValue({ valid: false, error: 'token expired' });

      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: 'Bearer stale.platform.token' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should fall back to local JWT when gateway returns valid=false', async () => {
      mockValidate.mockResolvedValue({ valid: false });
      const userId = 'user-789';
      const token = generateAccessToken(userId);

      const response = await app.inject({
        method: 'GET',
        url: '/protected',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(JSON.parse(response.body).userId).toBe(userId);
    });
  });

  // -------------------------------------------------------------------------
  // requireRole — RBAC
  // -------------------------------------------------------------------------

  describe('requireRole (RBAC)', () => {
    it('should allow access when user has required role', async () => {
      mockValidate.mockResolvedValue({
        valid: true,
        userId: 'admin-user',
        roles: ['admin'],
      });

      const response = await app.inject({
        method: 'GET',
        url: '/admin',
        headers: { authorization: 'Bearer admin.platform.token' },
      });

      expect(response.statusCode).toBe(200);
    });

    it('should return 403 when user lacks required role', async () => {
      mockValidate.mockResolvedValue({
        valid: true,
        userId: 'basic-user',
        roles: ['viewer'],
      });

      const response = await app.inject({
        method: 'GET',
        url: '/admin',
        headers: { authorization: 'Bearer viewer.token' },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Forbidden');
    });

    it('should allow access when user has any of the allowed roles', async () => {
      mockValidate.mockResolvedValue({
        valid: true,
        userId: 'analyst-user',
        roles: ['analyst'],
      });

      const response = await app.inject({
        method: 'GET',
        url: '/reports',
        headers: { authorization: 'Bearer analyst.token' },
      });

      expect(response.statusCode).toBe(200);
    });

    it('should return 401 from requireRole when no token provided', async () => {
      const response = await app.inject({ method: 'GET', url: '/admin' });
      expect(response.statusCode).toBe(401);
    });

    it('should return 403 for local JWT user (no roles) on role-protected route', async () => {
      // Local JWT users have no roles; requireRole must reject them
      const token = generateAccessToken('no-role-user');

      const response = await app.inject({
        method: 'GET',
        url: '/admin',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  // -------------------------------------------------------------------------
  // optionalAuthenticate
  // -------------------------------------------------------------------------

  describe('optionalAuthenticate', () => {
    it('should attach user ID if valid local token provided', async () => {
      const userId = 'test-user-456';
      const token = generateAccessToken(userId);

      const response = await app.inject({
        method: 'GET',
        url: '/optional',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(JSON.parse(response.body).userId).toBe(userId);
    });

    it('should attach user ID for valid platform token', async () => {
      mockValidate.mockResolvedValue({ valid: true, userId: 'platform-user', roles: [] });

      const response = await app.inject({
        method: 'GET',
        url: '/optional',
        headers: { authorization: 'Bearer platform.token' },
      });

      expect(response.statusCode).toBe(200);
      expect(JSON.parse(response.body).userId).toBe('platform-user');
    });

    it('should proceed without user ID if no token provided', async () => {
      const response = await app.inject({ method: 'GET', url: '/optional' });
      expect(response.statusCode).toBe(200);
      expect(JSON.parse(response.body).userId).toBeNull();
    });

    it('should proceed without user ID if invalid token provided', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/optional',
        headers: { authorization: 'Bearer invalid-token' },
      });
      expect(response.statusCode).toBe(200);
      expect(JSON.parse(response.body).userId).toBeNull();
    });

    it('should not attach user ID for refresh token', async () => {
      const refreshToken = generateRefreshToken('test-user-456');

      const response = await app.inject({
        method: 'GET',
        url: '/optional',
        headers: { authorization: `Bearer ${refreshToken}` },
      });

      expect(response.statusCode).toBe(200);
      expect(JSON.parse(response.body).userId).toBeNull();
    });
  });
});
