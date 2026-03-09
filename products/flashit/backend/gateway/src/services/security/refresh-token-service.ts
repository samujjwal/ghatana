/**
 * Refresh Token Service
 * 
 * @doc.type service
 * @doc.purpose Manages JWT refresh tokens for secure token rotation and session management
 * @doc.layer product
 * @doc.pattern Service
 * 
 * @description Implements secure refresh token flow with:
 * - Token generation and validation
 * - Automatic token rotation
 * - Device tracking
 * - Token revocation
 * - Cleanup of expired tokens
 * 
 * @example
 * ```typescript
 * const refreshTokenService = new RefreshTokenService();
 * 
 * // Generate refresh token
 * const { token, tokenHash } = await refreshTokenService.generateRefreshToken({
 *   userId: 'user-id',
 *   deviceInfo: { name: 'iPhone', type: 'MOBILE' },
 *   ipAddress: '127.0.0.1',
 *   userAgent: 'Mozilla/5.0...'
 * });
 * 
 * // Validate and rotate token
 * const newToken = await refreshTokenService.rotateRefreshToken(token);
 * ```
 */

import { prisma } from '../../lib/prisma.js';
import crypto from 'crypto';

const REFRESH_TOKEN_EXPIRY_DAYS = 30;
const REFRESH_TOKEN_LENGTH = 64; // bytes, becomes 128 hex chars

export interface DeviceInfo {
  name?: string;
  type?: 'MOBILE' | 'WEB' | 'DESKTOP' | 'TABLET';
}

export interface RefreshTokenData {
  userId: string;
  deviceInfo?: DeviceInfo;
  ipAddress?: string;
  userAgent?: string;
}

export interface RefreshTokenResult {
  token: string;
  tokenHash: string;
  expiresAt: Date;
}

export class RefreshTokenService {
  /**
   * Generate a secure random refresh token
   */
  private generateSecureToken(): string {
    return crypto.randomBytes(REFRESH_TOKEN_LENGTH).toString('hex');
  }

  /**
   * Hash a token using SHA-256
   */
  private hashToken(token: string): string {
    return crypto.createHash('sha256').update(token).digest('hex');
  }

  /**
   * Generate a new refresh token for a user
   */
  async generateRefreshToken(data: RefreshTokenData): Promise<RefreshTokenResult> {
    const token = this.generateSecureToken();
    const tokenHash = this.hashToken(token);
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + REFRESH_TOKEN_EXPIRY_DAYS);

    await prisma.refreshToken.create({
      data: {
        userId: data.userId,
        tokenHash,
        deviceName: data.deviceInfo?.name,
        deviceType: data.deviceInfo?.type,
        ipAddress: data.ipAddress,
        userAgent: data.userAgent,
        expiresAt,
      },
    });

    return { token, tokenHash, expiresAt };
  }

  /**
   * Validate a refresh token and return user ID if valid
   */
  async validateRefreshToken(token: string): Promise<string | null> {
    const tokenHash = this.hashToken(token);

    const refreshToken = await prisma.refreshToken.findUnique({
      where: { tokenHash },
      include: { user: true },
    });

    // Check if token exists and is valid
    if (!refreshToken) {
      return null;
    }

    // Check if token is expired
    if (refreshToken.expiresAt < new Date()) {
      await this.revokeRefreshToken(tokenHash, 'TOKEN_EXPIRED');
      return null;
    }

    // Check if token is revoked
    if (refreshToken.revokedAt) {
      return null;
    }

    // Check if user is deleted or locked
    if (refreshToken.user.deletedAt || refreshToken.user.lockedUntil) {
      return null;
    }

    // Update last used timestamp
    await prisma.refreshToken.update({
      where: { tokenHash },
      data: { lastUsedAt: new Date() },
    });

    return refreshToken.userId;
  }

  /**
   * Rotate a refresh token (revoke old, issue new)
   * This implements automatic token rotation for enhanced security
   */
  async rotateRefreshToken(
    oldToken: string,
    deviceInfo?: DeviceInfo
  ): Promise<RefreshTokenResult | null> {
    const userId = await this.validateRefreshToken(oldToken);
    if (!userId) {
      return null;
    }

    // Revoke old token
    const oldTokenHash = this.hashToken(oldToken);
    await this.revokeRefreshToken(oldTokenHash, 'TOKEN_ROTATED');

    // Get device info from old token
    const oldTokenData = await prisma.refreshToken.findUnique({
      where: { tokenHash: oldTokenHash },
      select: {
        deviceName: true,
        deviceType: true,
        ipAddress: true,
        userAgent: true,
      },
    });

    // Generate new token
    return this.generateRefreshToken({
      userId,
      deviceInfo: deviceInfo || {
        name: oldTokenData?.deviceName || undefined,
        type: (oldTokenData?.deviceType as DeviceInfo['type']) || undefined,
      },
      ipAddress: oldTokenData?.ipAddress || undefined,
      userAgent: oldTokenData?.userAgent || undefined,
    });
  }

  /**
   * Revoke a refresh token
   */
  async revokeRefreshToken(tokenHash: string, reason?: string): Promise<void> {
    await prisma.refreshToken.updateMany({
      where: { tokenHash },
      data: {
        revokedAt: new Date(),
        revokedReason: reason,
      },
    });
  }

  /**
   * Revoke all refresh tokens for a user
   */
  async revokeAllUserTokens(userId: string, reason?: string): Promise<number> {
    const result = await prisma.refreshToken.updateMany({
      where: {
        userId,
        revokedAt: null,
      },
      data: {
        revokedAt: new Date(),
        revokedReason: reason || 'ALL_TOKENS_REVOKED',
      },
    });

    return result.count;
  }

  /**
   * Get all active refresh tokens for a user
   */
  async getUserRefreshTokens(userId: string) {
    return prisma.refreshToken.findMany({
      where: {
        userId,
        revokedAt: null,
        expiresAt: { gt: new Date() },
      },
      select: {
        id: true,
        deviceName: true,
        deviceType: true,
        ipAddress: true,
        createdAt: true,
        lastUsedAt: true,
        expiresAt: true,
      },
      orderBy: { lastUsedAt: 'desc' },
    });
  }

  /**
   * Clean up expired and revoked refresh tokens
   * Should be run periodically (e.g., daily cron job)
   */
  async cleanupExpiredTokens(): Promise<number> {
    const result = await prisma.refreshToken.deleteMany({
      where: {
        OR: [
          { expiresAt: { lt: new Date() } },
          {
            revokedAt: { not: null, lt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }, // Revoked more than 7 days ago
          },
        ],
      },
    });

    return result.count;
  }

  /**
   * Revoke a specific refresh token by ID
   */
  async revokeRefreshTokenById(tokenId: string, reason?: string): Promise<void> {
    await prisma.refreshToken.update({
      where: { id: tokenId },
      data: {
        revokedAt: new Date(),
        revokedReason: reason,
      },
    });
  }
}

export const refreshTokenService = new RefreshTokenService();
