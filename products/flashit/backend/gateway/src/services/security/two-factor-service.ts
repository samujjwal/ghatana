/**
 * Two-Factor Authentication (2FA) Service
 * 
 * @doc.type service
 * @doc.purpose Implements TOTP-based two-factor authentication with backup codes
 * @doc.layer product
 * @doc.pattern Service
 * 
 * @description Provides complete 2FA functionality:
 * - TOTP secret generation
 * - QR code generation for authenticator apps
 * - TOTP code verification
 * - Backup recovery codes
 * - 2FA enable/disable flows
 * 
 * @example
 * ```typescript
 * const twoFactorService = new TwoFactorAuthService();
 * 
 * // Setup 2FA
 * const setup = await twoFactorService.setupTwoFactor(userId, 'user@example.com');
 * // Returns: { secret, qrCodeUrl, backupCodes }
 * 
 * // Verify and enable
 * await twoFactorService.verifyAndEnable(userId, totpCode);
 * 
 * // Verify during login
 * const isValid = await twoFactorService.verifyTOTP(userId, totpCode);
 * ```
 */

import { prisma } from '../../lib/prisma.js';
import { authenticator } from 'otplib';
import crypto from 'crypto';
import QRCode from 'qrcode';

const BACKUP_CODE_LENGTH = 8;
const BACKUP_CODE_COUNT = 10;
const APP_NAME = 'Flashit';

export interface TwoFactorSetup {
  secret: string;
  qrCodeUrl: string;
  backupCodes: string[];
}

export class TwoFactorAuthService {
  constructor() {
    // Configure TOTP settings
    authenticator.options = {
      step: 30, // 30 second time window
      window: 1, // Allow 1 time step before/after
    };
  }

  /**
   * Generate a secure backup code
   */
  private generateBackupCode(): string {
    const code = crypto.randomBytes(BACKUP_CODE_LENGTH).toString('hex').toUpperCase();
    return `${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 12)}-${code.slice(12, 16)}`;
  }

  /**
   * Hash a backup code for storage
   */
  private hashBackupCode(code: string): string {
    return crypto.createHash('sha256').update(code).digest('hex');
  }

  /**
   * Setup 2FA for a user (generate secret and backup codes)
   */
  async setupTwoFactor(userId: string, userEmail: string): Promise<TwoFactorSetup> {
    // Generate TOTP secret
    const secret = authenticator.generateSecret();

    // Generate QR code URL for authenticator apps
    const otpauthUrl = authenticator.keyuri(userEmail, APP_NAME, secret);
    const qrCodeUrl = await QRCode.toDataURL(otpauthUrl);

    // Generate backup codes
    const backupCodes = Array.from({ length: BACKUP_CODE_COUNT }, () =>
      this.generateBackupCode()
    );

    // Hash backup codes before storing
    const hashedBackupCodes = backupCodes.map((code) => this.hashBackupCode(code));

    // Store in database (not enabled yet, waiting for verification)
    await prisma.twoFactorAuth.upsert({
      where: { userId },
      create: {
        userId,
        enabled: false,
        secretKey: secret,
        backupCodes: hashedBackupCodes,
      },
      update: {
        secretKey: secret,
        backupCodes: hashedBackupCodes,
        enabled: false,
        verifiedAt: null,
      },
    });

    return {
      secret,
      qrCodeUrl,
      backupCodes, // Return plain codes to user (they should save them)
    };
  }

  /**
   * Verify TOTP code and enable 2FA
   */
  async verifyAndEnable(userId: string, totpCode: string): Promise<boolean> {
    const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
      where: { userId },
    });

    if (!twoFactorAuth || !twoFactorAuth.secretKey) {
      throw new Error('2FA not set up');
    }

    // Verify TOTP code
    const isValid = authenticator.verify({
      token: totpCode,
      secret: twoFactorAuth.secretKey,
    });

    if (!isValid) {
      return false;
    }

    // Enable 2FA
    await prisma.twoFactorAuth.update({
      where: { userId },
      data: {
        enabled: true,
        verifiedAt: new Date(),
      },
    });

    // Update user table
    await prisma.user.update({
      where: { id: userId },
      data: { twoFactorEnabled: true },
    });

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: '2FA_ENABLED',
        severity: 'INFO',
        success: true,
        details: { method: 'TOTP' },
      },
    });

    return true;
  }

  /**
   * Verify TOTP code during login
   */
  async verifyTOTP(userId: string, totpCode: string): Promise<boolean> {
    const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
      where: { userId },
    });

    if (!twoFactorAuth || !twoFactorAuth.enabled || !twoFactorAuth.secretKey) {
      return false;
    }

    const isValid = authenticator.verify({
      token: totpCode,
      secret: twoFactorAuth.secretKey,
    });

    // Log verification attempt
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: '2FA_VERIFICATION',
        severity: isValid ? 'INFO' : 'WARNING',
        success: isValid,
      },
    });

    return isValid;
  }

  /**
   * Verify backup code
   */
  async verifyBackupCode(userId: string, backupCode: string): Promise<boolean> {
    const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
      where: { userId },
    });

    if (!twoFactorAuth || !twoFactorAuth.enabled) {
      return false;
    }

    const hashedCode = this.hashBackupCode(backupCode);

    // Check if code exists in backup codes
    const codeIndex = twoFactorAuth.backupCodes.indexOf(hashedCode);
    if (codeIndex === -1) {
      await prisma.securityAuditLog.create({
        data: {
          userId,
          eventType: 'BACKUP_CODE_VERIFICATION_FAILED',
          severity: 'WARNING',
          success: false,
        },
      });
      return false;
    }

    // Remove used backup code
    const updatedBackupCodes = twoFactorAuth.backupCodes.filter(
      (code) => code !== hashedCode
    );

    await prisma.twoFactorAuth.update({
      where: { userId },
      data: { backupCodes: updatedBackupCodes },
    });

    // Log successful verification
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: 'BACKUP_CODE_USED',
        severity: 'INFO',
        success: true,
        details: { remainingCodes: updatedBackupCodes.length },
      },
    });

    return true;
  }

  /**
   * Disable 2FA for a user
   */
  async disableTwoFactor(userId: string): Promise<void> {
    await prisma.twoFactorAuth.update({
      where: { userId },
      data: {
        enabled: false,
        secretKey: null,
        backupCodes: [],
      },
    });

    await prisma.user.update({
      where: { id: userId },
      data: { twoFactorEnabled: false },
    });

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: '2FA_DISABLED',
        severity: 'WARNING',
        success: true,
      },
    });
  }

  /**
   * Generate new backup codes
   */
  async regenerateBackupCodes(userId: string): Promise<string[]> {
    const backupCodes = Array.from({ length: BACKUP_CODE_COUNT }, () =>
      this.generateBackupCode()
    );

    const hashedBackupCodes = backupCodes.map((code) => this.hashBackupCode(code));

    await prisma.twoFactorAuth.update({
      where: { userId },
      data: { backupCodes: hashedBackupCodes },
    });

    // Log security event
    await prisma.securityAuditLog.create({
      data: {
        userId,
        eventType: 'BACKUP_CODES_REGENERATED',
        severity: 'INFO',
        success: true,
      },
    });

    return backupCodes;
  }

  /**
   * Check if user has 2FA enabled
   */
  async isTwoFactorEnabled(userId: string): Promise<boolean> {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { twoFactorEnabled: true },
    });

    return user?.twoFactorEnabled || false;
  }

  /**
   * Get 2FA status for user
   */
  async getTwoFactorStatus(userId: string) {
    const twoFactorAuth = await prisma.twoFactorAuth.findUnique({
      where: { userId },
      select: {
        enabled: true,
        verifiedAt: true,
        backupCodes: true,
      },
    });

    if (!twoFactorAuth) {
      return {
        enabled: false,
        verifiedAt: null,
        remainingBackupCodes: 0,
      };
    }

    return {
      enabled: twoFactorAuth.enabled,
      verifiedAt: twoFactorAuth.verifiedAt,
      remainingBackupCodes: twoFactorAuth.backupCodes.length,
    };
  }
}

export const twoFactorAuthService = new TwoFactorAuthService();
