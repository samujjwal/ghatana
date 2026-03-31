/**
 * @fileoverview Two-Factor Authentication Service
 * TOTP-based 2FA implementation with backup codes
 * 
 * @doc.type service
 * @doc.purpose Manage two-factor authentication for enhanced security
 * @doc.layer domain
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import { authenticator } from 'otplib';
import { randomBytes, createCipheriv, createDecipheriv, scryptSync } from 'crypto';
import { promisify } from 'util';
import QRCode from 'qrcode';
import { Logger } from '../../lib/logger';

const randomBytesAsync = promisify(randomBytes);
const logger = Logger.create({ component: 'TwoFactorService' });

// ============================================================================
// Types
// ============================================================================

export interface TwoFactorSetupResult {
  secret: string;
  qrCodeUrl: string;
  backupCodes: string[];
}

export interface TwoFactorVerificationResult {
  valid: boolean;
  error?: string;
}

export interface TwoFactorStatus {
  enabled: boolean;
  backupCodesRemaining?: number;
}

// ============================================================================
// Configuration
// ============================================================================

const APP_NAME = 'Flashit';
const BACKUP_CODES_COUNT = 10;
const ENCRYPTION_ALGORITHM = 'aes-256-gcm';

// Get encryption key from environment — must be set, no unsafe defaults
const ENCRYPTION_KEY = process.env.TWO_FACTOR_ENCRYPTION_KEY;
if (!ENCRYPTION_KEY) {
  throw new Error('CRITICAL: TWO_FACTOR_ENCRYPTION_KEY environment variable is required. 2FA secrets cannot be encrypted without it.');
}

// ============================================================================
// Encryption Helpers
// ============================================================================

/**
 * Encrypt sensitive data
 * @doc.purpose Encrypt TOTP secrets and backup codes
 */
function encrypt(text: string): string {
  const iv = randomBytes(16);
  const salt = randomBytes(16);
  const key = scryptSync(ENCRYPTION_KEY, salt, 32);
  const cipher = createCipheriv(ENCRYPTION_ALGORITHM, key, iv);
  
  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  
  const authTag = cipher.getAuthTag();
  
  return `${salt.toString('hex')}:${iv.toString('hex')}:${authTag.toString('hex')}:${encrypted}`;
}

/**
 * Decrypt sensitive data
 * @doc.purpose Decrypt TOTP secrets and backup codes
 */
function decrypt(encryptedText: string): string {
  const [saltHex, ivHex, authTagHex, encrypted] = encryptedText.split(':');
  
  const salt = Buffer.from(saltHex, 'hex');
  const iv = Buffer.from(ivHex, 'hex');
  const authTag = Buffer.from(authTagHex, 'hex');
  const key = scryptSync(ENCRYPTION_KEY, salt, 32);
  
  const decipher = createDecipheriv(ENCRYPTION_ALGORITHM, key, iv);
  decipher.setAuthTag(authTag);
  
  let decrypted = decipher.update(encrypted, 'hex', 'utf8');
  decrypted += decipher.final('utf8');
  
  return decrypted;
}

/**
 * Generate backup codes
 * @doc.purpose Create recovery codes for 2FA
 */
async function generateBackupCodes(count: number = BACKUP_CODES_COUNT): Promise<string[]> {
  const codes: string[] = [];
  
  for (let i = 0; i < count; i++) {
    const buffer = await randomBytesAsync(4);
    const code = buffer.toString('hex').toUpperCase();
    codes.push(code);
  }
  
  return codes;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Two-Factor Authentication Service
 * @doc.purpose Manage TOTP-based 2FA with backup codes
 * 
 * @example
 * ```typescript
 * const service = new TwoFactorService(prisma);
 * const setup = await service.enableTwoFactor('user-123', 'user@example.com');
 * const verified = await service.verifyTwoFactor('user-123', '123456');
 * ```
 */
export class TwoFactorService {
  constructor(private prisma: PrismaClient) {
    // Configure TOTP
    authenticator.options = {
      window: 1, // Allow 1 step before/after for clock drift
    };
  }

  /**
   * Enable two-factor authentication for a user
   * @doc.purpose Generate secret, QR code, and backup codes
   */
  async enableTwoFactor(
    userId: string,
    userEmail: string
  ): Promise<TwoFactorSetupResult> {
    try {
      // Check if 2FA already enabled
      const existing = await this.prisma.twoFactorAuth.findUnique({
        where: { userId },
      });

      if (existing && existing.enabled) {
        throw new Error('Two-factor authentication is already enabled');
      }

      // Generate TOTP secret
      const secret = authenticator.generateSecret();

      // Generate QR code
      const otpauth = authenticator.keyuri(userEmail, APP_NAME, secret);
      const qrCodeUrl = await QRCode.toDataURL(otpauth);

      // Generate backup codes
      const backupCodes = await generateBackupCodes();

      // Encrypt secret and backup codes
      const encryptedSecret = encrypt(secret);
      const encryptedBackupCodes = backupCodes.map((code) => encrypt(code));

      // Store in database (not enabled yet - requires verification)
      if (existing) {
        await this.prisma.twoFactorAuth.update({
          where: { userId },
          data: {
            secret: encryptedSecret,
            backupCodes: encryptedBackupCodes,
            enabled: false,
          },
        });
      } else {
        await this.prisma.twoFactorAuth.create({
          data: {
            userId,
            secret: encryptedSecret,
            backupCodes: encryptedBackupCodes,
            enabled: false,
          },
        });
      }

      logger.info('2FA setup initiated', { userId });

      return {
        secret,
        qrCodeUrl,
        backupCodes,
      };
    } catch (error) {
      logger.error('Failed to enable 2FA', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw error;
    }
  }

  /**
   * Verify and activate two-factor authentication
   * @doc.purpose Confirm 2FA setup with initial token verification
   */
  async activateTwoFactor(
    userId: string,
    token: string
  ): Promise<TwoFactorVerificationResult> {
    try {
      const twoFactor = await this.prisma.twoFactorAuth.findUnique({
        where: { userId },
      });

      if (!twoFactor) {
        return { valid: false, error: '2FA not set up' };
      }

      if (twoFactor.enabled) {
        return { valid: false, error: '2FA already enabled' };
      }

      // Decrypt secret
      const secret = decrypt(twoFactor.secret);

      // Verify token
      const isValid = authenticator.verify({ token, secret });

      if (!isValid) {
        logger.warn('2FA activation failed - invalid token', { userId });
        return { valid: false, error: 'Invalid verification code' };
      }

      // Activate 2FA
      await this.prisma.twoFactorAuth.update({
        where: { userId },
        data: { enabled: true },
      });

      logger.info('2FA activated', { userId });

      return { valid: true };
    } catch (error) {
      logger.error('Failed to activate 2FA', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return { valid: false, error: 'Activation failed' };
    }
  }

  /**
   * Verify two-factor authentication token
   * @doc.purpose Validate TOTP token during login
   */
  async verifyTwoFactor(
    userId: string,
    token: string
  ): Promise<TwoFactorVerificationResult> {
    try {
      const twoFactor = await this.prisma.twoFactorAuth.findUnique({
        where: { userId },
      });

      if (!twoFactor || !twoFactor.enabled) {
        return { valid: false, error: '2FA not enabled' };
      }

      // Decrypt secret
      const secret = decrypt(twoFactor.secret);

      // Verify token
      const isValid = authenticator.verify({ token, secret });

      if (isValid) {
        logger.info('2FA verification successful', { userId });
        return { valid: true };
      }

      logger.warn('2FA verification failed', { userId });
      return { valid: false, error: 'Invalid code' };
    } catch (error) {
      logger.error('Failed to verify 2FA', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return { valid: false, error: 'Verification failed' };
    }
  }

  /**
   * Verify backup code
   * @doc.purpose Allow recovery access with backup code
   */
  async verifyBackupCode(
    userId: string,
    code: string
  ): Promise<TwoFactorVerificationResult> {
    try {
      const twoFactor = await this.prisma.twoFactorAuth.findUnique({
        where: { userId },
      });

      if (!twoFactor || !twoFactor.enabled) {
        return { valid: false, error: '2FA not enabled' };
      }

      // Decrypt and check backup codes
      const backupCodes = twoFactor.backupCodes.map((encrypted) =>
        decrypt(encrypted)
      );

      const codeIndex = backupCodes.findIndex(
        (backupCode) => backupCode.toUpperCase() === code.toUpperCase()
      );

      if (codeIndex === -1) {
        logger.warn('Invalid backup code', { userId });
        return { valid: false, error: 'Invalid backup code' };
      }

      // Remove used backup code
      const updatedCodes = [...twoFactor.backupCodes];
      updatedCodes.splice(codeIndex, 1);

      await this.prisma.twoFactorAuth.update({
        where: { userId },
        data: { backupCodes: updatedCodes },
      });

      logger.info('Backup code used', {
        userId,
        remainingCodes: updatedCodes.length,
      });

      return { valid: true };
    } catch (error) {
      logger.error('Failed to verify backup code', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      return { valid: false, error: 'Verification failed' };
    }
  }

  /**
   * Disable two-factor authentication
   * @doc.purpose Turn off 2FA (requires password confirmation)
   */
  async disableTwoFactor(userId: string): Promise<void> {
    try {
      await this.prisma.twoFactorAuth.delete({
        where: { userId },
      });

      logger.info('2FA disabled', { userId });
    } catch (error) {
      logger.error('Failed to disable 2FA', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to disable 2FA');
    }
  }

  /**
   * Regenerate backup codes
   * @doc.purpose Create new backup codes (invalidates old ones)
   */
  async regenerateBackupCodes(userId: string): Promise<string[]> {
    try {
      const twoFactor = await this.prisma.twoFactorAuth.findUnique({
        where: { userId },
      });

      if (!twoFactor || !twoFactor.enabled) {
        throw new Error('2FA not enabled');
      }

      // Generate new backup codes
      const backupCodes = await generateBackupCodes();
      const encryptedBackupCodes = backupCodes.map((code) => encrypt(code));

      // Update database
      await this.prisma.twoFactorAuth.update({
        where: { userId },
        data: { backupCodes: encryptedBackupCodes },
      });

      logger.info('Backup codes regenerated', { userId });

      return backupCodes;
    } catch (error) {
      logger.error('Failed to regenerate backup codes', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw error;
    }
  }

  /**
   * Get two-factor authentication status
   * @doc.purpose Check if 2FA is enabled for user
   */
  async getTwoFactorStatus(userId: string): Promise<TwoFactorStatus> {
    try {
      const twoFactor = await this.prisma.twoFactorAuth.findUnique({
        where: { userId },
      });

      if (!twoFactor) {
        return { enabled: false };
      }

      return {
        enabled: twoFactor.enabled,
        backupCodesRemaining: twoFactor.backupCodes.length,
      };
    } catch (error) {
      logger.error('Failed to get 2FA status', {
        userId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      throw new Error('Failed to get 2FA status');
    }
  }
}
