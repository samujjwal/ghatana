/**
 * Security Hardening Services
 * 
 * Implements 2FA, refresh tokens, session management, and enhanced security
 * 
 * @doc.type service
 * @doc.purpose Security hardening and authentication
 * @doc.layer infrastructure
 * @doc.pattern Service
 */

import crypto from 'crypto';
import { promisify } from 'util';
import { systemLogger } from '../logger';

const randomBytes = promisify(crypto.randomBytes);

export interface RefreshToken {
  id: string;
  token: string;
  userId: string;
  deviceId: string;
  userAgent: string;
  ipAddress: string;
  expiresAt: Date;
  createdAt: Date;
  lastUsedAt: Date;
  isRevoked: boolean;
  familyId: string;
}

export interface Session {
  id: string;
  userId: string;
  deviceId: string;
  userAgent: string;
  ipAddress: string;
  createdAt: Date;
  lastActivityAt: Date;
  expiresAt: Date;
  isActive: boolean;
}

export interface TwoFactorAuth {
  id: string;
  userId: string;
  secret: string;
  backupCodes: string[];
  enabled: boolean;
  verifiedAt?: Date;
  createdAt: Date;
  updatedAt: Date;
}

export interface SecurityConfig {
  jwt: {
    accessTokenExpiry: number; // minutes
    refreshTokenExpiry: number; // days
    issuer: string;
    audience: string;
  };
  twoFactor: {
    issuer: string;
    digits: number;
    period: number; // seconds
    window: number; // seconds
  };
  session: {
    maxSessions: number;
    inactivityTimeout: number; // minutes
    absoluteTimeout: number; // days
  };
  rateLimit: {
    login: {
      maxAttempts: number;
      windowMinutes: number;
      lockoutMinutes: number;
    };
    twoFactor: {
      maxAttempts: number;
      windowMinutes: number;
      lockoutMinutes: number;
    };
  };
}

/**
 * Security Service for enhanced authentication
 */
export class SecurityService {
  private static instance: SecurityService;
  private config: SecurityConfig;
  private loginAttempts: Map<string, { count: number; lockedUntil?: Date }> = new Map();
  private twoFactorAttempts: Map<string, { count: number; lockedUntil?: Date }> = new Map();

  private constructor() {
    this.config = this.loadConfig();
  }

  static getInstance(): SecurityService {
    if (!SecurityService.instance) {
      SecurityService.instance = new SecurityService();
    }
    return SecurityService.instance;
  }

  private loadConfig(): SecurityConfig {
    return {
      jwt: {
        accessTokenExpiry: parseInt(process.env.JWT_EXPIRES_IN || '15'), // 15 minutes
        refreshTokenExpiry: parseInt(process.env.REFRESH_TOKEN_EXPIRY_DAYS || '30'), // 30 days
        issuer: process.env.JWT_ISSUER || 'flashit',
        audience: process.env.JWT_AUDIENCE || 'flashit-users',
      },
      twoFactor: {
        issuer: process.env.TOTP_ISSUER || 'Flashit',
        digits: 6,
        period: 30, // 30 seconds
        window: 1, // 30 seconds window
      },
      session: {
        maxSessions: 5,
        inactivityTimeout: 30, // 30 minutes
        absoluteTimeout: 7, // 7 days
      },
      rateLimit: {
        login: {
          maxAttempts: 5,
          windowMinutes: 15,
          lockoutMinutes: 30,
        },
        twoFactor: {
          maxAttempts: 3,
          windowMinutes: 15,
          lockoutMinutes: 60,
        },
      },
    };
  }

  /**
   * Generate JWT access token
   */
  generateAccessToken(payload: { userId: string; email: string }): string {
    const header = {
      alg: 'HS256',
      typ: 'JWT',
    };

    const now = Math.floor(Date.now() / 1000);
    const payload = {
      sub: payload.userId,
      email: payload.email,
      iat: now,
      exp: now + (this.config.jwt.accessTokenExpiry * 60),
      iss: this.config.jwt.issuer,
      aud: this.config.jwt.audience,
      type: 'access',
    };

    const encodedHeader = Buffer.from(JSON.stringify(header)).toString('base64url').replace(/=/g, '+');
    const encodedPayload = Buffer.from(JSON.stringify(payload)).toString('base64url').replace(/=/g, '+');
    const signature = this.signHMAC(`${encodedHeader}.${encodedPayload}`);

    return `${encodedHeader}.${encodedPayload}.${signature}`;
  }

  /**
   * Generate refresh token
   */
  async generateRefreshToken(
    userId: string,
    deviceId: string,
    userAgent: string,
    ipAddress: string
  ): Promise<RefreshToken> {
    const tokenId = await randomBytes(32).then(bytes => bytes.toString('hex'));
    const token = await randomBytes(32).then(bytes => bytes.toString('hex'));
    const familyId = await randomBytes(16).then(bytes => bytes.toString('hex'));

    const refreshToken: RefreshToken = {
      id: tokenId,
      token,
      userId,
      deviceId,
      userAgent,
      ipAddress,
      expiresAt: new Date(Date.now() + this.config.jwt.refreshTokenExpiry * 24 * 60 * 60 * 1000),
      createdAt: new Date(),
      lastUsedAt: new Date(),
      isRevoked: false,
      familyId,
    };

    // In a real implementation, save to database
    await this.saveRefreshToken(refreshToken);

    return refreshToken;
  }

  /**
   * Validate refresh token
   */
  async validateRefreshToken(token: string): Promise<RefreshToken | null> {
    // In a real implementation, fetch from database
    const storedToken = await this.getRefreshToken(token);
    
    if (!storedToken) {
      return null;
    }

    if (storedToken.isRevoked) {
      return null;
    }

    if (new Date() > storedToken.expiresAt) {
      return null;
    }

    // Update last used time
    storedToken.lastUsedAt = new Date();
    await this.updateRefreshToken(storedToken);

    return storedToken;
  }

  /**
   * Revoke refresh token
   */
  async revokeRefreshToken(token: string): Promise<boolean> {
    const storedToken = await this.getRefreshToken(token);
    
    if (!storedToken) {
      return false;
    }

    storedToken.isRevoked = true;
    storedToken.updatedAt = new Date();
    
    await this.updateRefreshToken(storedToken);
    
    // Revoke all tokens in the same family
    await this.revokeTokenFamily(storedToken.familyId);

    return true;
  }

  /**
   * Revoke all refresh tokens in a family
   */
  private async revokeTokenFamily(familyId: string): Promise<void> {
    // In a real implementation, update all tokens with this familyId
    systemLogger.info(`Revoking refresh token family: ${familyId}`);
  }

  /**
   * Generate TOTP secret
   */
  async generateTOTPSecret(): Promise<string> {
    return await randomBytes(20).then(bytes => bytes.toString('base64'));
  }

  /**
   * Generate TOTP URI for QR code
   */
  generateTOTPUri(secret: string, email: string): string {
    const issuer = encodeURIComponent(this.config.twoFactor.issuer);
    const label = encodeURIComponent(email);
    const encodedSecret = secret.replace(/[\s=]/g, '+').replace(/\+/g, '-');
    
    return `otpauth://totp/${issuer}:${label}?secret=${encodedSecret}&digits=${this.config.twoFactor.digits}&period=${this.config.twoFactor.period}`;
  }

  /**
   * Verify TOTP token
   */
  verifyTOTPToken(secret: string, token: string): boolean {
    try {
      const speakejs = require('speakejs');
      return speakejs.totp.verify({
        secret,
        encoding: 'base32',
        token,
        digits: this.config.twoFactor.digits,
        window: this.config.twoFactor.window,
      });
    } catch (error) {
      systemLogger.error('TOTP verification error', error);
      return false;
    }
  }

  /**
   * Generate backup codes
   */
  async generateBackupCodes(): Promise<string[]> {
    const codes: string[] = [];
    
    for (let i = 0; i < 10; i++) {
      codes.push(await randomBytes(4).then(bytes => 
        bytes.toString('base32').slice(0, 8).toUpperCase()
      ));
    }
    
    return codes;
  }

  /**
   * Enable 2FA for user
   */
  async enableTwoFactorAuth(userId: string): Promise<TwoFactorAuth> {
    const secret = await this.generateTOTPSecret();
    const backupCodes = await this.generateBackupCodes();

    const twoFactorAuth: TwoFactorAuth = {
      id: crypto.randomUUID(),
      userId,
      secret,
      backupCodes,
      enabled: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    // In a real implementation, save to database
    await this.saveTwoFactorAuth(twoFactorAuth);

    return twoFactorAuth;
  }

  /**
   * Verify 2FA during login
   */
  async verifyTwoFactorAuth(userId: string, token: string): Promise<boolean> {
    const twoFactorAuth = await this.getTwoFactorAuth(userId);
    
    if (!twoFactorAuth || !twoFactorAuth.enabled) {
      return false;
    }

    // Check rate limiting
    if (this.isTwoFactorRateLimited(userId)) {
      return false;
    }

    const isValid = this.verifyTOTPToken(twoFactorAuth.secret, token);
    
    if (isValid) {
      twoFactorAuth.verifiedAt = new Date();
      twoFactorAuth.updatedAt = new Date();
      await this.updateTwoFactorAuth(twoFactorAuth);
      
      // Reset rate limiting on success
      this.resetTwoFactorRateLimit(userId);
    } else {
      // Increment rate limiting on failure
      this.incrementTwoFactorRateLimit(userId);
    }

    return isValid;
  }

  /**
   * Create user session
   */
  async createSession(
    userId: string,
    deviceId: string,
    userAgent: string,
    ipAddress: string
  ): Promise<Session> {
    const session: Session = {
      id: crypto.randomUUID(),
      userId,
      deviceId,
      userAgent,
      ipAddress,
      createdAt: new Date(),
      lastActivityAt: new Date(),
      expiresAt: new Date(Date.now() + this.config.session.absoluteTimeout * 24 * 60 * 60 * 1000),
      isActive: true,
    };

    // In a real implementation, save to database
    await this.saveSession(session);

    // Clean up old sessions
    await this.cleanupOldSessions(userId);

    return session;
  }

  /**
   * Validate session
   */
  async validateSession(sessionId: string): Promise<Session | null> {
    const session = await this.getSession(sessionId);
    
    if (!session || !session.isActive) {
      return null;
    }

    const now = new Date();
    
    // Check absolute timeout
    if (now > session.expiresAt) {
      session.isActive = false;
      await this.updateSession(session);
      return null;
    }

    // Check inactivity timeout
    const inactivityTime = now.getTime() - session.lastActivityAt.getTime();
    if (inactivityTime > this.config.session.inactivityTimeout * 60 * 1000) {
      session.isActive = false;
      await this.updateSession(session);
      return null;
    }

    // Update last activity
    session.lastActivityAt = now;
    await this.updateSession(session);

    return session;
  }

  /**
   * Revoke session
   */
  async revokeSession(sessionId: string): Promise<boolean> {
    const session = await this.getSession(sessionId);
    
    if (!session) {
      return false;
    }

    session.isActive = false;
    session.updatedAt = new Date();
    
    await this.updateSession(session);
    return true;
  }

  /**
   * Get user sessions
   */
  async getUserSessions(userId: string): Promise<Session[]> {
    // In a real implementation, fetch from database
    return [];
  }

  /**
   * Revoke all user sessions
   */
  async revokeAllSessions(userId: string): Promise<number> {
    const sessions = await this.getUserSessions(userId);
    let revokedCount = 0;
    
    for (const session of sessions) {
      if (session.isActive) {
        await this.revokeSession(session.id);
        revokedCount++;
      }
    }
    
    return revokedCount;
  }

  /**
   * Check login rate limiting
   */
  isLoginRateLimited(email: string): boolean {
    const attempts = this.loginAttempts.get(email);
    
    if (!attempts) {
      return false;
    }

    if (attempts.lockedUntil && new Date() < attempts.lockedUntil) {
      return true;
    }

    return attempts.count >= this.config.rateLimit.login.maxAttempts;
  }

  /**
   * Increment login attempts
   */
  incrementLoginAttempts(email: string): void {
    const attempts = this.loginAttempts.get(email) || { count: 0 };
    attempts.count++;
    
    if (attempts.count >= this.config.rateLimit.login.maxAttempts) {
      attempts.lockedUntil = new Date(
        Date.now() + this.config.rateLimit.login.lockoutMinutes * 60 * 1000
      );
    }
    
    this.loginAttempts.set(email, attempts);
  }

  /**
   * Reset login attempts
   */
  resetLoginAttempts(email: string): void {
    this.loginAttempts.delete(email);
  }

  /**
   * Check 2FA rate limiting
   */
  isTwoFactorRateLimited(userId: string): boolean {
    const attempts = this.twoFactorAttempts.get(userId);
    
    if (!attempts) {
      return false;
    }

    if (attempts.lockedUntil && new Date() < attempts.lockedUntil) {
      return true;
    }

    return attempts.count >= this.config.rateLimit.twoFactor.maxAttempts;
  }

  /**
   * Increment 2FA attempts
   */
  incrementTwoFactorRateLimit(userId: string): void {
    const attempts = this.twoFactorAttempts.get(userId) || { count: 0 };
    attempts.count++;
    
    if (attempts.count >= this.config.rateLimit.twoFactor.maxAttempts) {
      attempts.lockedUntil = new Date(
        Date.now() + this.config.rateLimit.twoFactor.lockoutMinutes * 60 * 1000
      );
    }
    
    this.twoFactorAttempts.set(userId, attempts);
  }

  /**
   * Reset 2FA rate limiting
   */
  resetTwoFactorRateLimit(userId: string): void {
    this.twoFactorAttempts.delete(userId);
  }

  /**
   * Check password strength
   */
  checkPasswordStrength(password: string): {
    score: number;
    feedback: string[];
    isStrong: boolean;
  } {
    const feedback: string[] = [];
    let score = 0;

    // Length check
    if (password.length >= 12) {
      score += 25;
    } else if (password.length >= 8) {
      score += 15;
    } else {
      feedback.push('Password should be at least 8 characters long');
    }

    // Complexity checks
    if (/[a-z]/.test(password)) score += 15;
    else feedback.push('Include lowercase letters');

    if (/[A-Z]/.test(password)) score += 15;
    else feedback.push('Include uppercase letters');

    if (/\d/.test(password)) score += 15;
    else feedback.push('Include numbers');

    if (/[^a-zA-Z0-9]/.test(password)) score += 20;
    else feedback.push('Include special characters');

    // Common patterns check
    if (/(.)\1{2,}/.test(password)) {
      score -= 10;
      feedback.push('Avoid repeated characters');
    }

    if (/123|password|qwerty|abc/i.test(password)) {
      score -= 20;
      feedback.push('Avoid common passwords');
    }

    const isStrong = score >= 70 && feedback.length === 0;

    return { score, feedback, isStrong };
  }

  /**
   * Generate secure random string
   */
  async generateSecureRandom(length: number = 32): Promise<string> {
    return await randomBytes(length).then(bytes => 
      bytes.toString('base64').slice(0, length)
    );
  }

  /**
   * Hash password securely
   */
  async hashPassword(password: string): Promise<string> {
    const bcrypt = require('bcrypt');
    const saltRounds = 12;
    return await bcrypt.hash(password, saltRounds);
  }

  /**
   * Verify password
   */
  async verifyPassword(password: string, hash: string): Promise<boolean> {
    const bcrypt = require('bcrypt');
    return bcrypt.compare(password, hash);
  }

  /**
   * Generate device fingerprint
   */
  generateDeviceFingerprint(userAgent: string, ipAddress: string): string {
    const crypto = require('crypto');
    const hash = crypto.createHash('sha256');
    hash.update(userAgent);
    hash.update(ipAddress);
    hash.update(Date.now().toString());
    return hash.digest('hex');
  }

  // Database methods (mock implementations - would connect to actual database)
  private async saveRefreshToken(token: RefreshToken): Promise<void> {
    // Mock implementation
    systemLogger.debug('Saving refresh token', { tokenId: token.id });
  }

  private async getRefreshToken(token: string): Promise<RefreshToken | null> {
    // Mock implementation
    return null;
  }

  private async updateRefreshToken(token: RefreshToken): Promise<void> {
    // Mock implementation
    systemLogger.debug('Updating refresh token', { tokenId: token.id });
  }

  private async saveTwoFactorAuth(twoFactor: TwoFactorAuth): Promise<void> {
    // Mock implementation
    systemLogger.debug('Saving 2FA config', { userId: twoFactor.userId });
  }

  private async getTwoFactorAuth(userId: string): Promise<TwoFactorAuth | null> {
    // Mock implementation
    return null;
  }

  private async updateTwoFactorAuth(twoFactor: TwoFactorAuth): Promise<void> {
    // Mock implementation
    systemLogger.debug('Updating 2FA config', { userId: twoFactor.userId });
  }

  private async saveSession(session: Session): Promise<void> {
    // Mock implementation
    systemLogger.debug('Saving session', { sessionId: session.id });
  }

  private async getSession(sessionId: string): Promise<Session | null> {
    // Mock implementation
    return null;
  }

  private async updateSession(session: Session): Promise<void> {
    // Mock implementation
    systemLogger.debug('Updating session', { sessionId: session.id });
  }

  private async cleanupOldSessions(userId: string): Promise<void> {
    // Mock implementation
    systemLogger.debug('Cleaning up old sessions', { userId });
  }

  private signHMAC(data: string): string {
    const crypto = require('crypto');
    const key = process.env.JWT_SECRET || 'test-secret';
    return crypto.createHmac('sha256', key).update(data).digest('hex');
  }
}

/**
 * Global security service instance
 */
export const securityService = SecurityService.getInstance();

/**
 * Security middleware for Fastify
 */
export const securityMiddleware = {
  // Rate limiting middleware
  rateLimit: (maxRequests: number, windowMs: number) => {
    const requests = new Map<string, { count: number; resetTime: number }>();
    
    return async (request: any, reply: any) => {
      const key = request.ip || 'unknown';
      const now = Date.now();
      const record = requests.get(key) || { count: 0, resetTime: now };
      
      if (now - record.resetTime > windowMs) {
        record.count = 0;
        record.resetTime = now;
      }
      
      if (record.count >= maxRequests) {
        reply.status(429).send({
          error: 'Too many requests',
          retryAfter: Math.ceil(windowMs / 1000),
        });
        return;
      }
      
      record.count++;
      requests.set(key, record);
    };
  },

  // Security headers middleware
  securityHeaders: async (request: any, reply: any) => {
    reply.header('X-Content-Type-Options', 'nosniff');
    reply.header('X-Frame-Options', 'DENY');
    reply.header('X-XSS-Protection', '1; mode=block');
    reply.header('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
    reply.header('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  },
};

export default {
  securityService,
  securityMiddleware,
};
