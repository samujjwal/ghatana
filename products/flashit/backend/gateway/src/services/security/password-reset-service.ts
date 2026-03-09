/**
 * Password Reset Service
 * 
 * @doc.type service
 * @doc.purpose Secure password reset flow with time-limited tokens and email verification
 * @doc.layer product
 * @doc.pattern Service
 * 
 * @description Implements secure password reset:
 * - Token generation with expiration
 * - Email-based verification
 * - Token usage tracking
 * - Audit logging
 * - Account lockout protection
 * 
 * @example
 * ```typescript
 * const passwordResetService = new PasswordResetService();
 * 
 * // Request password reset
 * const token = await passwordResetService.requestPasswordReset('user@example.com');
 * // Send token via email
 * 
 * // Verify token
 * const isValid = await passwordResetService.verifyResetToken(token);
 * 
 * // Reset password
 * await passwordResetService.resetPassword(token, 'newPassword123');
 * ```
 */

import { prisma } from '../../lib/prisma.js';
import { hashPassword } from '../../lib/auth.js';
import crypto from 'crypto';

const RESET_TOKEN_LENGTH = 32; // bytes, becomes 64 hex chars
const RESET_TOKEN_EXPIRY_MINUTES = 60; // 1 hour
const MAX_RESET_ATTEMPTS_PER_DAY = 5;

export class PasswordResetService {
  /**
   * Generate a secure random reset token
   */
  private generateSecureToken(): string {
    return crypto.randomBytes(RESET_TOKEN_LENGTH).toString('hex');
  }

  /**
   * Hash a token using SHA-256
   */
  private hashToken(token: string): string {
    return crypto.createHash('sha256').update(token).digest('hex');
  }

  /**
   * Request a password reset for an email
   */
  async requestPasswordReset(
    email: string,
    ipAddress?: string,
    userAgent?: string
  ): Promise<string | null> {
    // Find user by email
    const user = await prisma.user.findUnique({
      where: { email },
      select: {
        id: true,
        email: true,
        deletedAt: true,
        lockedUntil: true,
      },
    });

    // Don't reveal if user exists or not (security best practice)
    if (!user || user.deletedAt) {
      // Still generate a token but don't store it
      // This prevents timing attacks to enumerate users
      this.generateSecureToken();
      return null;
    }

    // Check if account is locked
    if (user.lockedUntil && user.lockedUntil > new Date()) {
      throw new Error('Account is locked. Please try again later.');
    }

    // Check rate limiting - max attempts per day
    const oneDayAgo = new Date();
    oneDayAgo.setDate(oneDayAgo.getDate() - 1);

    const recentRequests = await prisma.passwordResetToken.count({
      where: {
        userId: user.id,
        createdAt: { gte: oneDayAgo },
      },
    });

    if (recentRequests >= MAX_RESET_ATTEMPTS_PER_DAY) {
      // Log suspicious activity
      await prisma.securityAuditLog.create({
        data: {
          userId: user.id,
          eventType: 'PASSWORD_RESET_RATE_LIMIT_EXCEEDED',
          severity: 'WARNING',
          ipAddress,
          userAgent,
          success: false,
          details: { attemptCount: recentRequests },
        },
      });

      throw new Error('Too many password reset requests. Please try again later.');
    }

    // Invalidate any existing unused tokens for this user
    await prisma.passwordResetToken.updateMany({
      where: {
        userId: user.id,
        usedAt: null,
        expiresAt: { gt: new Date() },
      },
      data: {
        usedAt: new Date(), // Mark as used to invalidate
      },
    });

    // Generate new token
    const token = this.generateSecureToken();
    const tokenHash = this.hashToken(token);
    const expiresAt = new Date();
    expiresAt.setMinutes(expiresAt.getMinutes() + RESET_TOKEN_EXPIRY_MINUTES);

    // Store token
    await prisma.passwordResetToken.create({
      data: {
        userId: user.id,
        tokenHash,
        expiresAt,
        ipAddress,
        userAgent,
      },
    });

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId: user.id,
        eventType: 'PASSWORD_RESET_REQUESTED',
        severity: 'INFO',
        ipAddress,
        userAgent,
        success: true,
        details: { email: user.email },
      },
    });

    return token;
  }

  /**
   * Verify a reset token is valid
   */
  async verifyResetToken(token: string): Promise<boolean> {
    const tokenHash = this.hashToken(token);

    const resetToken = await prisma.passwordResetToken.findUnique({
      where: { tokenHash },
      include: { user: true },
    });

    if (!resetToken) {
      return false;
    }

    // Check if token is expired
    if (resetToken.expiresAt < new Date()) {
      return false;
    }

    // Check if token has been used
    if (resetToken.usedAt) {
      return false;
    }

    // Check if user is deleted or locked
    if (resetToken.user.deletedAt || resetToken.user.lockedUntil) {
      return false;
    }

    return true;
  }

  /**
   * Reset password using a valid token
   */
  async resetPassword(
    token: string,
    newPassword: string,
    ipAddress?: string,
    userAgent?: string
  ): Promise<boolean> {
    const tokenHash = this.hashToken(token);

    const resetToken = await prisma.passwordResetToken.findUnique({
      where: { tokenHash },
      include: { user: true },
    });

    if (!resetToken) {
      return false;
    }

    // Verify token is still valid
    if (
      resetToken.expiresAt < new Date() ||
      resetToken.usedAt ||
      resetToken.user.deletedAt ||
      resetToken.user.lockedUntil
    ) {
      await prisma.securityAuditLog.create({
        data: {
          userId: resetToken.userId,
          eventType: 'PASSWORD_RESET_FAILED',
          severity: 'WARNING',
          ipAddress,
          userAgent,
          success: false,
          details: { reason: 'INVALID_TOKEN' },
        },
      });
      return false;
    }

    // Hash new password
    const passwordHash = await hashPassword(newPassword);

    // Update user password
    await prisma.user.update({
      where: { id: resetToken.userId },
      data: {
        passwordHash,
        failedLoginAttempts: 0, // Reset failed login attempts
        lockedUntil: null, // Unlock account if it was locked
      },
    });

    // Mark token as used
    await prisma.passwordResetToken.update({
      where: { tokenHash },
      data: { usedAt: new Date() },
    });

    // Revoke all existing refresh tokens and sessions (security best practice)
    await prisma.refreshToken.updateMany({
      where: {
        userId: resetToken.userId,
        revokedAt: null,
      },
      data: {
        revokedAt: new Date(),
        revokedReason: 'PASSWORD_RESET',
      },
    });

    await prisma.userSession.updateMany({
      where: {
        userId: resetToken.userId,
        isActive: true,
      },
      data: {
        isActive: false,
        revokedAt: new Date(),
        revokedReason: 'PASSWORD_RESET',
      },
    });

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId: resetToken.userId,
        eventType: 'PASSWORD_RESET_COMPLETED',
        severity: 'INFO',
        ipAddress,
        userAgent,
        success: true,
      },
    });

    return true;
  }

  /**
   * Clean up expired reset tokens
   * Should be run periodically (e.g., daily cron job)
   */
  async cleanupExpiredTokens(): Promise<number> {
    const result = await prisma.passwordResetToken.deleteMany({
      where: {
        expiresAt: { lt: new Date() },
      },
    });

    return result.count;
  }

  /**
   * Get reset token info (without revealing if it exists)
   */
  async getResetTokenInfo(token: string) {
    const tokenHash = this.hashToken(token);

    const resetToken = await prisma.passwordResetToken.findUnique({
      where: { tokenHash },
      select: {
        expiresAt: true,
        usedAt: true,
        createdAt: true,
      },
    });

    if (!resetToken) {
      return null;
    }

    return {
      expiresAt: resetToken.expiresAt,
      isExpired: resetToken.expiresAt < new Date(),
      isUsed: !!resetToken.usedAt,
      createdAt: resetToken.createdAt,
    };
  }
}

export const passwordResetService = new PasswordResetService();
