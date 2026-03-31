/**
 * @fileoverview Refresh Token Service
 * Secure refresh token management for session persistence
 * 
 * @doc.type service
 * @doc.purpose Manage refresh tokens for secure session management
 * @doc.layer domain
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import { randomBytes } from 'crypto';
import { promisify } from 'util';
import { Logger } from '../../lib/logger';

const randomBytesAsync = promisify(randomBytes);
const logger = Logger.create({ component: 'RefreshTokenService' });

// ============================================================================
// Types
// ============================================================================

export interface RefreshTokenResult {
  token: string;
  expiresAt: Date;
}

export interface TokenValidationResult {
  valid: boolean;
  userId?: string;
  error?: string;
}

export interface RefreshTokenStats {
  totalActive: number;
  totalExpired: number;
  totalRevoked: number;
}

// ============================================================================
// Configuration
// ============================================================================

const REFRESH_TOKEN_EXPIRY_DAYS = 30;
const MAX_TOKENS_PER_USER = 5; // Limit concurrent sessions

// ============================================================================
// Service
// ============================================================================

/**
 * Refresh Token Service
 * @doc.purpose Manage secure refresh tokens for authentication
 * 
 * @example
 * ```typescript
 * const service = new RefreshTokenService(prisma);
 * const token = await service.generateRefreshToken('user-123');
 * const validation = await service.validateRefreshToken(token);
 * ```
 */
export class RefreshTokenService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Generate a new refresh token for a user
   * @doc.purpose Create secure refresh token with 30-day expiry
   */
  async generateRefreshToken(
    userId: string,
    deviceInfo?: {
      userAgent?: string;
      ipAddress?: string;
      deviceName?: string;
    }
  ): Promise<RefreshTokenResult> {
    try {
      // Generate cryptographically secure token
      const tokenBuffer = await randomBytesAsync(32);
      const token = tokenBuffer.toString('hex');

      // Calculate expiry date
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + REFRESH_TOKEN_EXPIRY_DAYS);

      // Clean up old tokens if user has too many
      await this.cleanupOldTokens(userId);

      // Create refresh token record
      await this.prisma.refreshToken.create({
        data: {
          userId,
          token,
          expiresAt,
          userAgent: deviceInfo?.userAgent,
          ipAddress: deviceInfo?.ipAddress,
          deviceName: deviceInfo?.deviceName,
        },
      });

      logger.info('Refresh token generated', {
        userId,
        expiresAt: expiresAt.toISOString(),
        deviceName: deviceInfo?.deviceName,
      });

      return { token, expiresAt };
    } catch (error) {
      logger.error('Failed to generate refresh token', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to generate refresh token');
    }
  }

  /**
   * Validate refresh token and return user ID
   * @doc.purpose Check token validity and expiration
   */
  async validateRefreshToken(token: string): Promise<TokenValidationResult> {
    try {
      const refreshToken = await this.prisma.refreshToken.findUnique({
        where: { token },
        include: {
          user: {
            select: {
              id: true,
              email: true,
              isActive: true,
            },
          },
        },
      });

      // Token not found
      if (!refreshToken) {
        logger.warn('Refresh token not found', { token: token.substring(0, 8) });
        return { valid: false, error: 'Invalid token' };
      }

      // Token revoked
      if (refreshToken.revokedAt) {
        logger.warn('Refresh token revoked', {
          userId: refreshToken.userId,
          revokedAt: refreshToken.revokedAt.toISOString(),
        });
        return { valid: false, error: 'Token revoked' };
      }

      // Token expired
      if (refreshToken.expiresAt < new Date()) {
        logger.warn('Refresh token expired', {
          userId: refreshToken.userId,
          expiresAt: refreshToken.expiresAt.toISOString(),
        });
        return { valid: false, error: 'Token expired' };
      }

      // User account inactive
      if (!refreshToken.user.isActive) {
        logger.warn('User account inactive', { userId: refreshToken.userId });
        return { valid: false, error: 'Account inactive' };
      }

      // Update last used timestamp
      await this.prisma.refreshToken.update({
        where: { token },
        data: { lastUsedAt: new Date() },
      });

      logger.info('Refresh token validated', { userId: refreshToken.userId });

      return {
        valid: true,
        userId: refreshToken.userId,
      };
    } catch (error) {
      logger.error('Token validation failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return { valid: false, error: 'Validation failed' };
    }
  }

  /**
   * Revoke a specific refresh token
   * @doc.purpose Invalidate token on logout
   */
  async revokeRefreshToken(token: string): Promise<void> {
    try {
      const result = await this.prisma.refreshToken.updateMany({
        where: {
          token,
          revokedAt: null,
        },
        data: {
          revokedAt: new Date(),
        },
      });

      if (result.count > 0) {
        logger.info('Refresh token revoked', { token: token.substring(0, 8) });
      }
    } catch (error) {
      logger.error('Failed to revoke refresh token', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to revoke token');
    }
  }

  /**
   * Revoke all refresh tokens for a user
   * @doc.purpose Security measure for password change or account compromise
   */
  async revokeAllUserTokens(userId: string): Promise<number> {
    try {
      const result = await this.prisma.refreshToken.updateMany({
        where: {
          userId,
          revokedAt: null,
        },
        data: {
          revokedAt: new Date(),
        },
      });

      logger.info('All user tokens revoked', {
        userId,
        count: result.count,
      });

      return result.count;
    } catch (error) {
      logger.error('Failed to revoke user tokens', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to revoke user tokens');
    }
  }

  /**
   * Get active tokens for a user
   * @doc.purpose List user's active sessions
   */
  async getUserTokens(userId: string): Promise<Array<{
    id: string;
    deviceName?: string;
    ipAddress?: string;
    createdAt: Date;
    lastUsedAt?: Date;
    expiresAt: Date;
  }>> {
    try {
      const tokens = await this.prisma.refreshToken.findMany({
        where: {
          userId,
          revokedAt: null,
          expiresAt: {
            gt: new Date(),
          },
        },
        select: {
          id: true,
          deviceName: true,
          ipAddress: true,
          createdAt: true,
          lastUsedAt: true,
          expiresAt: true,
        },
        orderBy: {
          lastUsedAt: 'desc',
        },
      });

      return tokens;
    } catch (error) {
      logger.error('Failed to get user tokens', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to get user tokens');
    }
  }

  /**
   * Clean up old tokens for a user
   * @doc.purpose Remove oldest tokens when limit exceeded
   */
  private async cleanupOldTokens(userId: string): Promise<void> {
    try {
      const activeTokens = await this.prisma.refreshToken.findMany({
        where: {
          userId,
          revokedAt: null,
          expiresAt: {
            gt: new Date(),
          },
        },
        orderBy: {
          createdAt: 'desc',
        },
      });

      // If user has too many tokens, revoke oldest ones
      if (activeTokens.length >= MAX_TOKENS_PER_USER) {
        const tokensToRevoke = activeTokens.slice(MAX_TOKENS_PER_USER - 1);
        const tokenIds = tokensToRevoke.map((t) => t.id);

        await this.prisma.refreshToken.updateMany({
          where: {
            id: {
              in: tokenIds,
            },
          },
          data: {
            revokedAt: new Date(),
          },
        });

        logger.info('Old tokens cleaned up', {
          userId,
          count: tokenIds.length,
        });
      }
    } catch (error) {
      logger.error('Failed to cleanup old tokens', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      // Don't throw - this is a cleanup operation
    }
  }

  /**
   * Clean up expired tokens (scheduled job)
   * @doc.purpose Remove expired tokens from database
   */
  async cleanupExpiredTokens(): Promise<number> {
    try {
      const result = await this.prisma.refreshToken.deleteMany({
        where: {
          expiresAt: {
            lt: new Date(),
          },
        },
      });

      logger.info('Expired tokens cleaned up', { count: result.count });
      return result.count;
    } catch (error) {
      logger.error('Failed to cleanup expired tokens', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to cleanup expired tokens');
    }
  }

  /**
   * Get refresh token statistics
   * @doc.purpose Monitor token usage and health
   */
  async getTokenStats(): Promise<RefreshTokenStats> {
    try {
      const now = new Date();

      const [totalActive, totalExpired, totalRevoked] = await Promise.all([
        this.prisma.refreshToken.count({
          where: {
            revokedAt: null,
            expiresAt: {
              gt: now,
            },
          },
        }),
        this.prisma.refreshToken.count({
          where: {
            expiresAt: {
              lt: now,
            },
          },
        }),
        this.prisma.refreshToken.count({
          where: {
            revokedAt: {
              not: null,
            },
          },
        }),
      ]);

      return {
        totalActive,
        totalExpired,
        totalRevoked,
      };
    } catch (error) {
      logger.error('Failed to get token stats', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to get token stats');
    }
  }
}
