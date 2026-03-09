/**
 * Security Middleware
 * 
 * @doc.type middleware
 * @doc.purpose Comprehensive security middleware for rate limiting, 2FA, and session management
 * @doc.layer product
 * @doc.pattern Middleware
 * 
 * @description Provides security middleware including:
 * - Rate limiting with tier-based quotas
 * - 2FA verification
 * - Session validation
 * - Account lockout protection
 * - Security headers
 */

import { FastifyRequest, FastifyReply } from 'fastify';
import { rateLimitService } from '../services/security/rate-limit-service';
import { twoFactorAuthService } from '../services/security/two-factor-service';
import { sessionManagementService } from '../services/security/session-management-service';
import { JwtPayload } from '../lib/auth';

/**
 * Rate limiting middleware
 * Checks tier-based rate limits before allowing request
 */
export async function rateLimitMiddleware(request: FastifyRequest, reply: FastifyReply) {
  const user = request.user as JwtPayload | undefined;
  const userId = user?.userId;
  const ipAddress = request.ip;
  const endpoint = request.url.split('?')[0]; // Remove query params

  const result = await rateLimitService.checkRateLimit({
    userId,
    ipAddress,
    endpoint,
  });

  // Set rate limit headers
  reply.header('X-RateLimit-Limit', result.limit);
  reply.header('X-RateLimit-Remaining', result.remaining);
  reply.header('X-RateLimit-Reset', result.resetIn);
  reply.header('X-RateLimit-Tier', result.tier);

  if (!result.allowed) {
    return reply.code(429).send({
      error: 'Rate limit exceeded',
      message: `Too many requests. Please try again in ${result.resetIn} seconds.`,
      limit: result.limit,
      resetIn: result.resetIn,
      tier: result.tier,
    });
  }
}

/**
 * 2FA verification middleware
 * Requires 2FA code for users with 2FA enabled
 */
export async function require2FA(request: FastifyRequest, reply: FastifyReply) {
  const user = request.user as JwtPayload;
  const twoFactorCode = request.headers['x-2fa-code'] as string | undefined;

  const isEnabled = await twoFactorAuthService.isTwoFactorEnabled(user.userId);

  if (isEnabled) {
    if (!twoFactorCode) {
      return reply.code(403).send({
        error: '2FA required',
        message: 'Two-factor authentication code required',
        requiresTwoFactor: true,
      });
    }

    // Verify TOTP or backup code
    let isValid = await twoFactorAuthService.verifyTOTP(user.userId, twoFactorCode);

    if (!isValid) {
      // Try backup code
      isValid = await twoFactorAuthService.verifyBackupCode(user.userId, twoFactorCode);
    }

    if (!isValid) {
      return reply.code(403).send({
        error: 'Invalid 2FA code',
        message: 'The two-factor authentication code is invalid or expired',
      });
    }
  }
}

/**
 * Session validation middleware
 * Validates active session and updates activity
 */
export async function validateSession(request: FastifyRequest, reply: FastifyReply) {
  const sessionId = request.headers['x-session-id'] as string | undefined;
  const user = request.user as JwtPayload;

  if (sessionId) {
    const session = await sessionManagementService.getSession(sessionId);

    if (!session || !session.isActive || session.userId !== user.userId) {
      return reply.code(401).send({
        error: 'Invalid session',
        message: 'Session has expired or been revoked',
      });
    }

    // Update session activity
    await sessionManagementService.updateSessionActivity(sessionId);
  }
}

/**
 * Security headers middleware
 * Adds security-related HTTP headers
 */
export function securityHeaders(request: FastifyRequest, reply: FastifyReply, done: () => void) {
  // Content Security Policy
  reply.header('Content-Security-Policy', "default-src 'self'");

  // Prevent clickjacking
  reply.header('X-Frame-Options', 'DENY');

  // Prevent MIME type sniffing
  reply.header('X-Content-Type-Options', 'nosniff');

  // Enable XSS protection
  reply.header('X-XSS-Protection', '1; mode=block');

  // Referrer policy
  reply.header('Referrer-Policy', 'strict-origin-when-cross-origin');

  // Permissions policy
  reply.header('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');

  done();
}

/**
 * Account lockout protection
 * Checks if account is locked due to failed login attempts
 */
export async function checkAccountLockout(userId: string): Promise<boolean> {
  const { prisma } = await import('../lib/prisma');

  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { lockedUntil: true },
  });

  if (!user) {
    return false;
  }

  return user.lockedUntil ? user.lockedUntil > new Date() : false;
}

/**
 * Increment failed login attempts
 */
export async function incrementFailedLoginAttempts(userId: string): Promise<number> {
  const { prisma } = await import('../lib/prisma');

  const user = await prisma.user.update({
    where: { id: userId },
    data: {
      failedLoginAttempts: { increment: 1 },
    },
    select: { failedLoginAttempts: true },
  });

  const attempts = user.failedLoginAttempts;

  // Lock account after 5 failed attempts
  if (attempts >= 5) {
    const lockedUntil = new Date();
    lockedUntil.setMinutes(lockedUntil.getMinutes() + 30); // Lock for 30 minutes

    await prisma.user.update({
      where: { id: userId },
      data: { lockedUntil },
    });

    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: 'ACCOUNT_LOCKED',
        severity: 'CRITICAL',
        success: true,
        details: {
          failedAttempts: attempts,
          lockedUntil,
        },
      },
    });
  }

  return attempts;
}

/**
 * Reset failed login attempts
 */
export async function resetFailedLoginAttempts(userId: string): Promise<void> {
  const { prisma } = await import('../lib/prisma');

  await prisma.user.update({
    where: { id: userId },
    data: {
      failedLoginAttempts: 0,
      lockedUntil: null,
    },
  });
}
