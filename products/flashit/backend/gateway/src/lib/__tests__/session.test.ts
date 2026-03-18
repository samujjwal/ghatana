/**
 * @fileoverview Session Management Tests
 * Tests for secure session handling with refresh tokens
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  createSession,
  validateAccessToken,
  refreshTokenPair,
  invalidateSession,
  invalidateOtherSessions,
  invalidateAllUserSessions,
  getUserSessions,
  cleanupExpiredSessions,
  getSessionStats,
  hashToken,
  generateToken,
} from '../session';
import { prisma } from '../prisma';

// Mock prisma
vi.mock('../prisma', () => ({
  prisma: {
    session: {
      count: vi.fn(),
      findFirst: vi.fn(),
      findMany: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      updateMany: vi.fn(),
      deleteMany: vi.fn(),
      groupBy: vi.fn(),
    },
  },
}));

describe('Session Management', () => {
  const mockUserId = 'user-123';
  const mockIpAddress = '192.168.1.1';
  const mockUserAgent = 'Mozilla/5.0';
  const mockSessionId = 'session-123';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Token Utilities', () => {
    describe('hashToken', () => {
      it('should hash a token consistently', () => {
        const token = 'test-token-123';
        const hash1 = hashToken(token);
        const hash2 = hashToken(token);
        expect(hash1).toBe(hash2);
        expect(hash1).not.toBe(token);
      });

      it('should produce different hashes for different tokens', () => {
        const hash1 = hashToken('token1');
        const hash2 = hashToken('token2');
        expect(hash1).not.toBe(hash2);
      });
    });

    describe('generateToken', () => {
      it('should generate a token of correct length', () => {
        const token = generateToken();
        expect(token.length).toBeGreaterThan(32);
      });

      it('should generate unique tokens', () => {
        const token1 = generateToken();
        const token2 = generateToken();
        expect(token1).not.toBe(token2);
      });
    });
  });

  describe('createSession', () => {
    it('should create a session with access and refresh tokens', async () => {
      vi.mocked(prisma.session.count).mockResolvedValue(0);

      const result = await createSession(mockUserId, mockIpAddress, mockUserAgent);

      expect(result).toHaveProperty('accessToken');
      expect(result).toHaveProperty('refreshToken');
      expect(result).toHaveProperty('accessTokenExpiresAt');
      expect(result).toHaveProperty('refreshTokenExpiresAt');
      expect(result.accessToken).not.toBe(result.refreshToken);
    });

    it('should store hashed tokens in database', async () => {
      vi.mocked(prisma.session.count).mockResolvedValue(0);

      await createSession(mockUserId, mockIpAddress, mockUserAgent);

      expect(prisma.session.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          userId: mockUserId,
          accessTokenHash: expect.any(String),
          refreshTokenHash: expect.any(String),
          ipAddress: mockIpAddress,
          userAgent: mockUserAgent,
          isValid: true,
        }),
      });
    });

    it('should invalidate oldest session when max sessions reached', async () => {
      const oldestSession = {
        id: 'oldest-session',
        userId: mockUserId,
        lastUsedAt: new Date('2024-01-01'),
      };

      vi.mocked(prisma.session.count).mockResolvedValue(5);
      vi.mocked(prisma.session.findFirst).mockResolvedValue(oldestSession);

      await createSession(mockUserId, mockIpAddress, mockUserAgent);

      expect(prisma.session.update).toHaveBeenCalledWith({
        where: { id: 'oldest-session' },
        data: { isValid: false },
      });
    });

    it('should set correct expiration times', async () => {
      vi.mocked(prisma.session.count).mockResolvedValue(0);
      const beforeCreate = new Date();

      const result = await createSession(mockUserId, mockIpAddress, mockUserAgent);

      const afterCreate = new Date();
      const accessExpiry = new Date(result.accessTokenExpiresAt);
      const refreshExpiry = new Date(result.refreshTokenExpiresAt);

      // Access token should expire in ~15 minutes
      expect(accessExpiry.getTime() - beforeCreate.getTime()).toBeGreaterThan(14 * 60 * 1000);
      expect(accessExpiry.getTime() - afterCreate.getTime()).toBeLessThan(16 * 60 * 1000);

      // Refresh token should expire in ~7 days
      expect(refreshExpiry.getTime() - beforeCreate.getTime()).toBeGreaterThan(6 * 24 * 60 * 60 * 1000);
      expect(refreshExpiry.getTime() - afterCreate.getTime()).toBeLessThan(8 * 24 * 60 * 60 * 1000);
    });
  });

  describe('validateAccessToken', () => {
    it('should return session for valid token', async () => {
      const mockSession = {
        id: mockSessionId,
        userId: mockUserId,
        accessTokenHash: hashToken('valid-token'),
        refreshTokenHash: hashToken('refresh-token'),
        accessTokenExpiresAt: new Date(Date.now() + 60000),
        refreshTokenExpiresAt: new Date(Date.now() + 86400000),
        ipAddress: mockIpAddress,
        userAgent: mockUserAgent,
        createdAt: new Date(),
        lastUsedAt: new Date(),
        isValid: true,
      };

      vi.mocked(prisma.session.findFirst).mockResolvedValue(mockSession);

      const result = await validateAccessToken('valid-token');

      expect(result).not.toBeNull();
      expect(result?.userId).toBe(mockUserId);
    });

    it('should update lastUsedAt timestamp', async () => {
      const mockSession = {
        id: mockSessionId,
        userId: mockUserId,
        accessTokenHash: hashToken('valid-token'),
        accessTokenExpiresAt: new Date(Date.now() + 60000),
        lastUsedAt: new Date('2024-01-01'),
        isValid: true,
      };

      vi.mocked(prisma.session.findFirst).mockResolvedValue(mockSession);

      await validateAccessToken('valid-token');

      expect(prisma.session.update).toHaveBeenCalledWith({
        where: { id: mockSessionId },
        data: { lastUsedAt: expect.any(Date) },
      });
    });

    it('should return null for expired token', async () => {
      vi.mocked(prisma.session.findFirst).mockResolvedValue(null);

      const result = await validateAccessToken('expired-token');

      expect(result).toBeNull();
    });

    it('should return null for invalid token', async () => {
      vi.mocked(prisma.session.findFirst).mockResolvedValue(null);

      const result = await validateAccessToken('invalid-token');

      expect(result).toBeNull();
    });
  });

  describe('refreshTokenPair', () => {
    it('should generate new token pair on valid refresh', async () => {
      const mockSession = {
        id: mockSessionId,
        userId: mockUserId,
        refreshTokenHash: hashToken('valid-refresh-token'),
        refreshTokenExpiresAt: new Date(Date.now() + 86400000),
        ipAddress: mockIpAddress,
        userAgent: mockUserAgent,
        isValid: true,
      };

      vi.mocked(prisma.session.findFirst).mockResolvedValue(mockSession);

      const result = await refreshTokenPair('valid-refresh-token', mockIpAddress, mockUserAgent);

      expect(result).not.toBeNull();
      expect(result).toHaveProperty('accessToken');
      expect(result).toHaveProperty('refreshToken');
      expect(result?.accessToken).not.toBe(result?.refreshToken);
    });

    it('should update session with new tokens (token rotation)', async () => {
      const mockSession = {
        id: mockSessionId,
        userId: mockUserId,
        refreshTokenHash: hashToken('valid-refresh-token'),
        refreshTokenExpiresAt: new Date(Date.now() + 86400000),
        ipAddress: mockIpAddress,
        userAgent: mockUserAgent,
        isValid: true,
      };

      vi.mocked(prisma.session.findFirst).mockResolvedValue(mockSession);

      await refreshTokenPair('valid-refresh-token', mockIpAddress, mockUserAgent);

      expect(prisma.session.update).toHaveBeenCalledWith({
        where: { id: mockSessionId },
        data: expect.objectContaining({
          accessTokenHash: expect.any(String),
          refreshTokenHash: expect.any(String),
          lastUsedAt: expect.any(Date),
        }),
      });
    });

    it('should return null for invalid refresh token', async () => {
      vi.mocked(prisma.session.findFirst).mockResolvedValue(null);

      const result = await refreshTokenPair('invalid-token', mockIpAddress, mockUserAgent);

      expect(result).toBeNull();
    });

    it('should return null for expired refresh token', async () => {
      vi.mocked(prisma.session.findFirst).mockResolvedValue(null);

      const result = await refreshTokenPair('expired-token', mockIpAddress, mockUserAgent);

      expect(result).toBeNull();
    });
  });

  describe('invalidateSession', () => {
    it('should invalidate a specific session', async () => {
      vi.mocked(prisma.session.update).mockResolvedValue({ id: mockSessionId, isValid: false });

      const result = await invalidateSession(mockSessionId);

      expect(result).toBe(true);
      expect(prisma.session.update).toHaveBeenCalledWith({
        where: { id: mockSessionId },
        data: { isValid: false },
      });
    });

    it('should return false on error', async () => {
      vi.mocked(prisma.session.update).mockRejectedValue(new Error('Database error'));

      const result = await invalidateSession(mockSessionId);

      expect(result).toBe(false);
    });
  });

  describe('invalidateOtherSessions', () => {
    it('should invalidate all sessions except current', async () => {
      const mockResult = { count: 3 };
      vi.mocked(prisma.session.updateMany).mockResolvedValue(mockResult);

      const result = await invalidateOtherSessions(mockUserId, mockSessionId);

      expect(result).toBe(3);
      expect(prisma.session.updateMany).toHaveBeenCalledWith({
        where: {
          userId: mockUserId,
          id: { not: mockSessionId },
          isValid: true,
        },
        data: { isValid: false },
      });
    });

    it('should return 0 if no other sessions', async () => {
      vi.mocked(prisma.session.updateMany).mockResolvedValue({ count: 0 });

      const result = await invalidateOtherSessions(mockUserId, mockSessionId);

      expect(result).toBe(0);
    });
  });

  describe('invalidateAllUserSessions', () => {
    it('should invalidate all user sessions', async () => {
      vi.mocked(prisma.session.updateMany).mockResolvedValue({ count: 5 });

      const result = await invalidateAllUserSessions(mockUserId);

      expect(result).toBe(5);
      expect(prisma.session.updateMany).toHaveBeenCalledWith({
        where: { userId: mockUserId, isValid: true },
        data: { isValid: false },
      });
    });
  });

  describe('getUserSessions', () => {
    it('should return active sessions for user', async () => {
      const mockSessions = [
        {
          id: 'session-1',
          userId: mockUserId,
          accessTokenHash: 'hash1',
          refreshTokenHash: 'hash2',
          accessTokenExpiresAt: new Date(),
          refreshTokenExpiresAt: new Date(),
          ipAddress: '192.168.1.1',
          userAgent: 'Chrome',
          createdAt: new Date(),
          lastUsedAt: new Date(),
          isValid: true,
        },
      ];

      vi.mocked(prisma.session.findMany).mockResolvedValue(mockSessions);

      const result = await getUserSessions(mockUserId);

      expect(result).toHaveLength(1);
      expect(result[0].userId).toBe(mockUserId);
      expect(result[0].accessToken).toBe('[REDACTED]');
      expect(result[0].refreshToken).toBe('[REDACTED]');
    });

    it('should return empty array if no active sessions', async () => {
      vi.mocked(prisma.session.findMany).mockResolvedValue([]);

      const result = await getUserSessions(mockUserId);

      expect(result).toHaveLength(0);
    });
  });

  describe('cleanupExpiredSessions', () => {
    it('should delete expired and invalid sessions', async () => {
      vi.mocked(prisma.session.deleteMany).mockResolvedValue({ count: 10 });

      const result = await cleanupExpiredSessions();

      expect(result).toBe(10);
      expect(prisma.session.deleteMany).toHaveBeenCalledWith({
        where: {
          OR: [
            { refreshTokenExpiresAt: { lt: expect.any(Date) } },
            { isValid: false },
          ],
        },
      });
    });
  });

  describe('getSessionStats', () => {
    it('should return session statistics', async () => {
      vi.mocked(prisma.session.count).mockResolvedValueOnce(50); // active
      vi.mocked(prisma.session.count).mockResolvedValueOnce(20); // expired
      vi.mocked(prisma.session.groupBy).mockResolvedValue([
        { userId: 'user1', _count: { id: 2 } },
        { userId: 'user2', _count: { id: 3 } },
      ]);

      const result = await getSessionStats();

      expect(result).toEqual({
        totalActive: 50,
        totalExpired: 20,
        averageSessionsPerUser: 25, // 50 / 2 users
      });
    });

    it('should handle zero users', async () => {
      vi.mocked(prisma.session.count).mockResolvedValueOnce(0);
      vi.mocked(prisma.session.count).mockResolvedValueOnce(0);
      vi.mocked(prisma.session.groupBy).mockResolvedValue([]);

      const result = await getSessionStats();

      expect(result.averageSessionsPerUser).toBe(0);
    });
  });
});
