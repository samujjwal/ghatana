/**
 * Session Management Service
 * 
 * @doc.type service
 * @doc.purpose Manages user sessions with device tracking and multi-device support
 * @doc.layer product
 * @doc.pattern Service
 * 
 * @description Provides comprehensive session management:
 * - Session creation and tracking
 * - Multi-device support
 * - Active session monitoring
 * - Session revocation (single or all)
 * - Device information extraction
 * - Session activity tracking
 * 
 * @example
 * ```typescript
 * const sessionService = new SessionManagementService();
 * 
 * // Create session
 * await sessionService.createSession({
 *   userId: 'user-id',
 *   refreshTokenId: 'token-id',
 *   userAgent: req.headers['user-agent'],
 *   ipAddress: req.ip
 * });
 * 
 * // Get active sessions
 * const sessions = await sessionService.getActiveSessions(userId);
 * 
 * // Revoke session
 * await sessionService.revokeSession(sessionId, 'USER_LOGOUT');
 * ```
 */

import { prisma } from '../../lib/prisma.js';
import UAParser from 'ua-parser-js';

const SESSION_EXPIRY_DAYS = 30;

export interface SessionData {
  userId: string;
  refreshTokenId?: string;
  userAgent?: string;
  ipAddress?: string;
  location?: string;
}

export interface DeviceInfo {
  deviceName?: string;
  deviceType?: 'MOBILE' | 'WEB' | 'DESKTOP' | 'TABLET';
  osName?: string;
  osVersion?: string;
  browserName?: string;
  browserVersion?: string;
}

export class SessionManagementService {
  /**
   * Parse user agent string to extract device information
   */
  private parseUserAgent(userAgent?: string): DeviceInfo {
    if (!userAgent) {
      return {};
    }

    const parser = new UAParser(userAgent);
    const result = parser.getResult();

    // Determine device type
    let deviceType: DeviceInfo['deviceType'];
    if (result.device.type === 'mobile') {
      deviceType = 'MOBILE';
    } else if (result.device.type === 'tablet') {
      deviceType = 'TABLET';
    } else if (result.browser.name) {
      deviceType = 'WEB';
    } else {
      deviceType = 'DESKTOP';
    }

    return {
      deviceName: result.device.model || result.os.name || 'Unknown Device',
      deviceType,
      osName: result.os.name,
      osVersion: result.os.version,
      browserName: result.browser.name,
      browserVersion: result.browser.version,
    };
  }

  /**
   * Create a new session
   */
  async createSession(data: SessionData) {
    const deviceInfo = this.parseUserAgent(data.userAgent);
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + SESSION_EXPIRY_DAYS);

    // Check max sessions limit
    const userTierSettings = await prisma.userTierSettings.findUnique({
      where: { userId: data.userId },
    });

    const maxSessions = userTierSettings?.maxSessions || 3;

    // Count active sessions
    const activeSessions = await this.getActiveSessions(data.userId);

    // If at max sessions, revoke oldest inactive session
    if (activeSessions.length >= maxSessions) {
      const oldestSession = activeSessions.sort(
        (a, b) => a.lastActivityAt.getTime() - b.lastActivityAt.getTime()
      )[0];

      await this.revokeSession(oldestSession.id, 'MAX_SESSIONS_EXCEEDED');
    }

    const session = await prisma.userSession.create({
      data: {
        userId: data.userId,
        refreshTokenId: data.refreshTokenId,
        deviceName: deviceInfo.deviceName,
        deviceType: deviceInfo.deviceType,
        osName: deviceInfo.osName,
        osVersion: deviceInfo.osVersion,
        browserName: deviceInfo.browserName,
        browserVersion: deviceInfo.browserVersion,
        ipAddress: data.ipAddress,
        location: data.location,
        expiresAt,
      },
    });

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId: data.userId,
        eventType: 'SESSION_CREATED',
        severity: 'INFO',
        ipAddress: data.ipAddress,
        userAgent: data.userAgent,
        deviceType: deviceInfo.deviceType,
        location: data.location,
        success: true,
        details: {
          sessionId: session.id,
          deviceName: deviceInfo.deviceName,
        },
      },
    });

    return session;
  }

  /**
   * Update session activity timestamp
   */
  async updateSessionActivity(sessionId: string): Promise<void> {
    await prisma.userSession.update({
      where: { id: sessionId },
      data: { lastActivityAt: new Date() },
    });
  }

  /**
   * Get all active sessions for a user
   */
  async getActiveSessions(userId: string) {
    return prisma.userSession.findMany({
      where: {
        userId,
        isActive: true,
        revokedAt: null,
        expiresAt: { gt: new Date() },
      },
      select: {
        id: true,
        deviceName: true,
        deviceType: true,
        osName: true,
        browserName: true,
        ipAddress: true,
        location: true,
        lastActivityAt: true,
        createdAt: true,
        expiresAt: true,
      },
      orderBy: { lastActivityAt: 'desc' },
    });
  }

  /**
   * Get session details by ID
   */
  async getSession(sessionId: string) {
    return prisma.userSession.findUnique({
      where: { id: sessionId },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            displayName: true,
          },
        },
      },
    });
  }

  /**
   * Revoke a specific session
   */
  async revokeSession(sessionId: string, reason?: string): Promise<void> {
    const session = await prisma.userSession.findUnique({
      where: { id: sessionId },
      select: { userId: true, deviceName: true, refreshTokenId: true },
    });

    if (!session) {
      throw new Error('Session not found');
    }

    // Revoke session
    await prisma.userSession.update({
      where: { id: sessionId },
      data: {
        isActive: false,
        revokedAt: new Date(),
        revokedReason: reason,
      },
    });

    // Also revoke associated refresh token if exists
    if (session.refreshTokenId) {
      await prisma.refreshToken.update({
        where: { id: session.refreshTokenId },
        data: {
          revokedAt: new Date(),
          revokedReason: reason || 'SESSION_REVOKED',
        },
      });
    }

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId: session.userId,
        eventType: 'SESSION_REVOKED',
        severity: 'INFO',
        success: true,
        details: {
          sessionId,
          reason,
          deviceName: session.deviceName,
        },
      },
    });
  }

  /**
   * Revoke all sessions for a user except current
   */
  async revokeOtherSessions(
    userId: string,
    currentSessionId: string,
    reason?: string
  ): Promise<number> {
    const sessions = await prisma.userSession.findMany({
      where: {
        userId,
        id: { not: currentSessionId },
        isActive: true,
        revokedAt: null,
      },
      select: { id: true, refreshTokenId: true },
    });

    // Revoke all sessions
    await prisma.userSession.updateMany({
      where: {
        userId,
        id: { not: currentSessionId },
        isActive: true,
      },
      data: {
        isActive: false,
        revokedAt: new Date(),
        revokedReason: reason || 'OTHER_SESSIONS_REVOKED',
      },
    });

    // Revoke associated refresh tokens
    const refreshTokenIds = sessions
      .filter((s) => s.refreshTokenId)
      .map((s) => s.refreshTokenId!);

    if (refreshTokenIds.length > 0) {
      await prisma.refreshToken.updateMany({
        where: { id: { in: refreshTokenIds } },
        data: {
          revokedAt: new Date(),
          revokedReason: reason || 'SESSION_REVOKED',
        },
      });
    }

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: 'OTHER_SESSIONS_REVOKED',
        severity: 'WARNING',
        success: true,
        details: {
          revokedCount: sessions.length,
          reason,
        },
      },
    });

    return sessions.length;
  }

  /**
   * Revoke all sessions for a user
   */
  async revokeAllSessions(userId: string, reason?: string): Promise<number> {
    const sessions = await prisma.userSession.findMany({
      where: {
        userId,
        isActive: true,
        revokedAt: null,
      },
      select: { id: true, refreshTokenId: true },
    });

    // Revoke all sessions
    await prisma.userSession.updateMany({
      where: {
        userId,
        isActive: true,
      },
      data: {
        isActive: false,
        revokedAt: new Date(),
        revokedReason: reason || 'ALL_SESSIONS_REVOKED',
      },
    });

    // Revoke associated refresh tokens
    const refreshTokenIds = sessions
      .filter((s) => s.refreshTokenId)
      .map((s) => s.refreshTokenId!);

    if (refreshTokenIds.length > 0) {
      await prisma.refreshToken.updateMany({
        where: { id: { in: refreshTokenIds } },
        data: {
          revokedAt: new Date(),
          revokedReason: reason || 'SESSION_REVOKED',
        },
      });
    }

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: 'ALL_SESSIONS_REVOKED',
        severity: 'WARNING',
        success: true,
        details: {
          revokedCount: sessions.length,
          reason,
        },
      },
    });

    return sessions.length;
  }

  /**
   * Clean up expired sessions
   * Should be run periodically (e.g., daily cron job)
   */
  async cleanupExpiredSessions(): Promise<number> {
    const result = await prisma.userSession.updateMany({
      where: {
        expiresAt: { lt: new Date() },
        isActive: true,
      },
      data: {
        isActive: false,
        revokedAt: new Date(),
        revokedReason: 'SESSION_EXPIRED',
      },
    });

    return result.count;
  }

  /**
   * Get session statistics for a user
   */
  async getSessionStats(userId: string) {
    const [activeSessions, totalSessions, recentActivity] = await Promise.all([
      prisma.userSession.count({
        where: {
          userId,
          isActive: true,
          revokedAt: null,
          expiresAt: { gt: new Date() },
        },
      }),
      prisma.userSession.count({ where: { userId } }),
      prisma.userSession.findFirst({
        where: { userId, isActive: true },
        select: { lastActivityAt: true },
        orderBy: { lastActivityAt: 'desc' },
      }),
    ]);

    return {
      activeSessions,
      totalSessions,
      lastActivity: recentActivity?.lastActivityAt,
    };
  }
}

export const sessionManagementService = new SessionManagementService();
