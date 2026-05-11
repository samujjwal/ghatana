/**
 * @fileoverview Session management compatibility service for FlashIt.
 *
 * The current Prisma schema stores refresh tokens and user sessions separately.
 * This module keeps the public session helper API aligned with that schema.
 */

import { createHash, randomUUID } from 'crypto';
import { prisma } from './prisma';
import { Logger } from './logger';

const logger = Logger.create({ component: 'SessionService' });

const ACCESS_TOKEN_TTL = 15 * 60 * 1000;
const REFRESH_TOKEN_TTL = 7 * 24 * 60 * 60 * 1000;
const MAX_SESSIONS_PER_USER = 5;

const accessTokenSessions = new Map<string, string>();

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

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

function generateToken(): string {
  return randomUUID().replace(/-/g, '') + randomUUID().replace(/-/g, '');
}

function accessExpiresFrom(lastUsedAt: Date): Date {
  return new Date(lastUsedAt.getTime() + ACCESS_TOKEN_TTL);
}

function toSession(record: {
  id: string;
  userId: string;
  ipAddress?: string | null;
  userAgent?: string | null;
  createdAt: Date;
  lastActivityAt: Date;
  expiresAt: Date;
  isActive: boolean;
}): Session {
  return {
    id: record.id,
    userId: record.userId,
    accessToken: '[REDACTED]',
    refreshToken: '[REDACTED]',
    accessTokenExpiresAt: accessExpiresFrom(record.lastActivityAt),
    refreshTokenExpiresAt: record.expiresAt,
    ipAddress: record.ipAddress ?? null,
    userAgent: record.userAgent ?? null,
    createdAt: record.createdAt,
    lastUsedAt: record.lastActivityAt,
    isValid: record.isActive,
  };
}

export async function createSession(
  userId: string,
  ipAddress: string | null,
  userAgent: string | null,
): Promise<TokenPair> {
  const existingSessions = await prisma.userSession.count({
    where: { userId, isActive: true },
  });

  if (existingSessions >= MAX_SESSIONS_PER_USER) {
    const oldestSession = await prisma.userSession.findFirst({
      where: { userId, isActive: true },
      orderBy: { lastActivityAt: 'asc' },
    });

    if (oldestSession) {
      await invalidateSession(oldestSession.id);
      logger.info('Invalidated oldest session due to limit', { userId, sessionId: oldestSession.id });
    }
  }

  const accessToken = generateToken();
  const refreshToken = generateToken();
  const now = new Date();
  const accessTokenExpiresAt = new Date(now.getTime() + ACCESS_TOKEN_TTL);
  const refreshTokenExpiresAt = new Date(now.getTime() + REFRESH_TOKEN_TTL);

  const refreshTokenRecord = await prisma.refreshToken.create({
    data: {
      userId,
      tokenHash: hashToken(refreshToken),
      ipAddress,
      userAgent,
      expiresAt: refreshTokenExpiresAt,
    },
  });

  const session = await prisma.userSession.create({
    data: {
      userId,
      refreshTokenId: refreshTokenRecord.id,
      ipAddress,
      userAgent,
      lastActivityAt: now,
      expiresAt: refreshTokenExpiresAt,
      isActive: true,
    },
  });

  accessTokenSessions.set(hashToken(accessToken), session.id);
  logger.info('Session created', { userId, ipAddress });

  return {
    accessToken,
    refreshToken,
    accessTokenExpiresAt,
    refreshTokenExpiresAt,
  };
}

export async function validateAccessToken(token: string): Promise<Session | null> {
  const sessionId = accessTokenSessions.get(hashToken(token));
  if (!sessionId) return null;

  const session = await prisma.userSession.findFirst({
    where: {
      id: sessionId,
      isActive: true,
      expiresAt: { gt: new Date() },
      revokedAt: null,
    },
  });

  if (!session || accessExpiresFrom(session.lastActivityAt) <= new Date()) {
    return null;
  }

  const updated = await prisma.userSession.update({
    where: { id: session.id },
    data: { lastActivityAt: new Date() },
  });

  return toSession(updated);
}

export async function refreshTokenPair(
  refreshToken: string,
  ipAddress: string | null,
  userAgent: string | null,
): Promise<TokenPair | null> {
  const tokenHash = hashToken(refreshToken);
  const refreshTokenRecord = await prisma.refreshToken.findFirst({
    where: {
      tokenHash,
      revokedAt: null,
      expiresAt: { gt: new Date() },
    },
    include: { sessions: true },
  });

  const session = refreshTokenRecord?.sessions.find((candidate) => candidate.isActive && !candidate.revokedAt);
  if (!refreshTokenRecord || !session) {
    logger.warn('Invalid refresh token attempt', { ipAddress });
    return null;
  }

  const newAccessToken = generateToken();
  const newRefreshToken = generateToken();
  const now = new Date();
  const accessTokenExpiresAt = new Date(now.getTime() + ACCESS_TOKEN_TTL);
  const refreshTokenExpiresAt = new Date(now.getTime() + REFRESH_TOKEN_TTL);

  const newRefreshTokenRecord = await prisma.refreshToken.create({
    data: {
      userId: refreshTokenRecord.userId,
      tokenHash: hashToken(newRefreshToken),
      ipAddress,
      userAgent,
      expiresAt: refreshTokenExpiresAt,
    },
  });

  await prisma.refreshToken.update({
    where: { id: refreshTokenRecord.id },
    data: {
      revokedAt: now,
      revokedReason: 'rotated',
      lastUsedAt: now,
    },
  });

  const updatedSession = await prisma.userSession.update({
    where: { id: session.id },
    data: {
      refreshTokenId: newRefreshTokenRecord.id,
      ipAddress,
      userAgent,
      lastActivityAt: now,
      expiresAt: refreshTokenExpiresAt,
    },
  });

  accessTokenSessions.set(hashToken(newAccessToken), updatedSession.id);
  logger.info('Tokens refreshed', { userId: updatedSession.userId, sessionId: updatedSession.id });

  return {
    accessToken: newAccessToken,
    refreshToken: newRefreshToken,
    accessTokenExpiresAt,
    refreshTokenExpiresAt,
  };
}

export async function invalidateSession(sessionId: string): Promise<boolean> {
  try {
    await prisma.userSession.update({
      where: { id: sessionId },
      data: {
        isActive: false,
        revokedAt: new Date(),
        revokedReason: 'invalidated',
      },
    });
    logger.info('Session invalidated', { sessionId });
    return true;
  } catch (error) {
    logger.error('Failed to invalidate session', error, { sessionId });
    return false;
  }
}

export async function invalidateOtherSessions(userId: string, currentSessionId: string): Promise<number> {
  const result = await prisma.userSession.updateMany({
    where: {
      userId,
      id: { not: currentSessionId },
      isActive: true,
    },
    data: {
      isActive: false,
      revokedAt: new Date(),
      revokedReason: 'other-session-invalidated',
    },
  });

  logger.info('Invalidated other sessions', { userId, count: result.count });
  return result.count;
}

export async function invalidateAllUserSessions(userId: string): Promise<number> {
  const result = await prisma.userSession.updateMany({
    where: { userId, isActive: true },
    data: {
      isActive: false,
      revokedAt: new Date(),
      revokedReason: 'all-sessions-invalidated',
    },
  });

  logger.info('All sessions invalidated', { userId, count: result.count });
  return result.count;
}

export async function getUserSessions(userId: string): Promise<Session[]> {
  const sessions = await prisma.userSession.findMany({
    where: { userId, isActive: true, revokedAt: null },
    orderBy: { lastActivityAt: 'desc' },
  });

  return sessions.map(toSession);
}

export async function cleanupExpiredSessions(): Promise<number> {
  const result = await prisma.userSession.updateMany({
    where: {
      OR: [
        { expiresAt: { lt: new Date() } },
        { isActive: false },
      ],
    },
    data: {
      isActive: false,
      revokedAt: new Date(),
      revokedReason: 'expired',
    },
  });

  logger.info('Cleaned up expired sessions', { count: result.count });
  return result.count;
}

export async function getSessionStats(): Promise<{
  totalActive: number;
  totalExpired: number;
  averageSessionsPerUser: number;
}> {
  const [activeCount, expiredCount, userStats] = await Promise.all([
    prisma.userSession.count({ where: { isActive: true } }),
    prisma.userSession.count({ where: { isActive: false } }),
    prisma.userSession.groupBy({
      by: ['userId'],
      where: { isActive: true },
      _count: { id: true },
    }),
  ]);

  return {
    totalActive: activeCount,
    totalExpired: expiredCount,
    averageSessionsPerUser: userStats.length > 0 ? activeCount / userStats.length : 0,
  };
}
