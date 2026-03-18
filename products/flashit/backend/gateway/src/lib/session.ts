/**
 * @fileoverview Session Management Service for Flashit
 * Implements secure session handling with refresh tokens
 * 
 * @doc.type service
 * @doc.purpose Manage user sessions with secure token rotation
 * @doc.layer backend
 * @doc.pattern Security Service
 */

import { randomUUID, createHash } from 'crypto';
import { prisma } from './prisma';
import { Logger } from './logger';

const logger = Logger.create({ component: 'SessionService' });

// Configuration
const ACCESS_TOKEN_TTL = 15 * 60 * 1000; // 15 minutes
const REFRESH_TOKEN_TTL = 7 * 24 * 60 * 60 * 1000; // 7 days
const MAX_SESSIONS_PER_USER = 5;

export interface Session {
  id: string;
  userId: string;
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: Date;
  refreshTokenExpiresAt: Date;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: Date;
  lastUsedAt: Date;
  isValid: boolean;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: Date;
  refreshTokenExpiresAt: Date;
}

/**
 * Hash a token for storage (security best practice)
 */
function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

/**
 * Generate cryptographically secure token
 */
function generateToken(): string {
  return randomUUID().replace(/-/g, '') + randomUUID().replace(/-/g, '');
}

/**
 * Create a new session for a user
 * @doc.purpose Initialize authenticated session
 */
export async function createSession(
  userId: string,
  ipAddress: string | null,
  userAgent: string | null
): Promise<TokenPair> {
  // Check session limit
  const existingSessions = await prisma.session.count({
    where: { userId, isValid: true }
  });

  if (existingSessions >= MAX_SESSIONS_PER_USER) {
    // Invalidate oldest session
    const oldestSession = await prisma.session.findFirst({
      where: { userId, isValid: true },
      orderBy: { lastUsedAt: 'asc' }
    });

    if (oldestSession) {
      await invalidateSession(oldestSession.id);
      logger.info('Invalidated oldest session due to limit', { userId, sessionId: oldestSession.id });
    }
  }

  // Generate tokens
  const accessToken = generateToken();
  const refreshToken = generateToken();

  const now = new Date();
  const accessTokenExpiresAt = new Date(now.getTime() + ACCESS_TOKEN_TTL);
  const refreshTokenExpiresAt = new Date(now.getTime() + REFRESH_TOKEN_TTL);

  // Create session
  await prisma.session.create({
    data: {
      userId,
      accessTokenHash: hashToken(accessToken),
      refreshTokenHash: hashToken(refreshToken),
      accessTokenExpiresAt,
      refreshTokenExpiresAt,
      ipAddress,
      userAgent,
      createdAt: now,
      lastUsedAt: now,
      isValid: true,
    },
  });

  logger.info('Session created', { userId, ipAddress });

  return {
    accessToken,
    refreshToken,
    accessTokenExpiresAt,
    refreshTokenExpiresAt,
  };
}

/**
 * Validate access token
 * @doc.purpose Verify session validity
 */
export async function validateAccessToken(token: string): Promise<Session | null> {
  const tokenHash = hashToken(token);

  const session = await prisma.session.findFirst({
    where: {
      accessTokenHash: tokenHash,
      isValid: true,
      accessTokenExpiresAt: { gt: new Date() },
    },
  });

  if (!session) {
    return null;
  }

  // Update last used
  await prisma.session.update({
    where: { id: session.id },
    data: { lastUsedAt: new Date() },
  });

  return session;
}

/**
 * Refresh token pair using refresh token
 * @doc.purpose Rotate tokens for security (refresh token rotation)
 */
export async function refreshTokenPair(
  refreshToken: string,
  ipAddress: string | null,
  userAgent: string | null
): Promise<TokenPair | null> {
  const tokenHash = hashToken(refreshToken);

  const session = await prisma.session.findFirst({
    where: {
      refreshTokenHash: tokenHash,
      isValid: true,
      refreshTokenExpiresAt: { gt: new Date() },
    },
  });

  if (!session) {
    logger.warn('Invalid refresh token attempt', { ipAddress });
    return null;
  }

  // Generate new tokens (token rotation)
  const newAccessToken = generateToken();
  const newRefreshToken = generateToken();

  const now = new Date();
  const accessTokenExpiresAt = new Date(now.getTime() + ACCESS_TOKEN_TTL);
  const refreshTokenExpiresAt = new Date(now.getTime() + REFRESH_TOKEN_TTL);

  // Update session with new tokens
  await prisma.session.update({
    where: { id: session.id },
    data: {
      accessTokenHash: hashToken(newAccessToken),
      refreshTokenHash: hashToken(newRefreshToken),
      accessTokenExpiresAt,
      refreshTokenExpiresAt,
      lastUsedAt: now,
      ipAddress, // Update IP if changed
      userAgent, // Update UA if changed
    },
  });

  logger.info('Tokens refreshed', { userId: session.userId, sessionId: session.id });

  return {
    accessToken: newAccessToken,
    refreshToken: newRefreshToken,
    accessTokenExpiresAt,
    refreshTokenExpiresAt,
  };
}

/**
 * Invalidate a specific session
 * @doc.purpose Logout from specific device
 */
export async function invalidateSession(sessionId: string): Promise<boolean> {
  try {
    await prisma.session.update({
      where: { id: sessionId },
      data: { isValid: false },
    });

    logger.info('Session invalidated', { sessionId });
    return true;
  } catch (error) {
    logger.error('Failed to invalidate session', { sessionId, error });
    return false;
  }
}

/**
 * Invalidate all user sessions except current
 * @doc.purpose Logout from all other devices
 */
export async function invalidateOtherSessions(userId: string, currentSessionId: string): Promise<number> {
  const result = await prisma.session.updateMany({
    where: {
      userId,
      id: { not: currentSessionId },
      isValid: true,
    },
    data: { isValid: false },
  });

  logger.info('Invalidated other sessions', { userId, count: result.count });
  return result.count;
}

/**
 * Invalidate all sessions for a user
 * @doc.purpose Account logout / security incident
 */
export async function invalidateAllUserSessions(userId: string): Promise<number> {
  const result = await prisma.session.updateMany({
    where: { userId, isValid: true },
    data: { isValid: false },
  });

  logger.info('All sessions invalidated', { userId, count: result.count });
  return result.count;
}

/**
 * Get active sessions for a user
 * @doc.purpose Display active devices
 */
export async function getUserSessions(userId: string): Promise<Session[]> {
  const sessions = await prisma.session.findMany({
    where: { userId, isValid: true },
    orderBy: { lastUsedAt: 'desc' },
  });

  // Map to Session interface (excluding hashed tokens)
  return sessions.map(s => ({
    id: s.id,
    userId: s.userId,
    accessToken: '[REDACTED]',
    refreshToken: '[REDACTED]',
    accessTokenExpiresAt: s.accessTokenExpiresAt,
    refreshTokenExpiresAt: s.refreshTokenExpiresAt,
    ipAddress: s.ipAddress,
    userAgent: s.userAgent,
    createdAt: s.createdAt,
    lastUsedAt: s.lastUsedAt,
    isValid: s.isValid,
  }));
}

/**
 * Cleanup expired sessions
 * @doc.purpose Scheduled maintenance task
 */
export async function cleanupExpiredSessions(): Promise<number> {
  const result = await prisma.session.deleteMany({
    where: {
      OR: [
        { refreshTokenExpiresAt: { lt: new Date() } },
        { isValid: false },
      ],
    },
  });

  logger.info('Cleaned up expired sessions', { count: result.count });
  return result.count;
}

/**
 * Get session statistics
 * @doc.purpose Analytics and monitoring
 */
export async function getSessionStats(): Promise<{
  totalActive: number;
  totalExpired: number;
  averageSessionsPerUser: number;
}> {
  const [activeCount, expiredCount, userStats] = await Promise.all([
    prisma.session.count({ where: { isValid: true } }),
    prisma.session.count({ where: { isValid: false } }),
    prisma.session.groupBy({
      by: ['userId'],
      where: { isValid: true },
      _count: { id: true },
    }),
  ]);

  const averageSessionsPerUser = userStats.length > 0
    ? activeCount / userStats.length
    : 0;

  return {
    totalActive: activeCount,
    totalExpired: expiredCount,
    averageSessionsPerUser,
  };
}
