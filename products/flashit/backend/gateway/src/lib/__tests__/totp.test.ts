/**
 * @fileoverview TOTP (2FA) Service Tests
 * Tests for Two-Factor Authentication functionality
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { authenticator } from 'otplib';
import {
  generateTOTPSecret,
  verifyTOTPCode,
  generateBackupCodes,
  verifyBackupCode,
  enable2FA,
  disable2FA,
  is2FAEnabled,
  TOTPVerifyResult,
} from '../totp';
import { prisma } from '../prisma';

// Mock prisma
vi.mock('../prisma', () => ({
  prisma: {
    user: {
      findUnique: vi.fn(),
      update: vi.fn(),
    },
  },
}));

describe('TOTP Service (2FA)', () => {
  const mockUserId = 'user-123';
  const mockSecret = 'ABCDEFGHIJKLMNOP';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('generateTOTPSecret', () => {
    it('should generate a secret and return setup data', async () => {
      const result = await generateTOTPSecret(mockUserId);

      expect(result).toHaveProperty('secret');
      expect(result).toHaveProperty('qrCodeUrl');
      expect(result).toHaveProperty('backupCodes');
      expect(result.backupCodes).toHaveLength(10);
      expect(result.qrCodeUrl).toMatch(/^data:image\/png;base64,/);
    });

    it('should store secret in database with totpEnabled=false', async () => {
      const result = await generateTOTPSecret(mockUserId);

      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: expect.objectContaining({
          totpSecret: result.secret,
          totpEnabled: false,
          totpBackupCodes: expect.any(String),
          totpCreatedAt: expect.any(Date),
        }),
      });
    });

    it('should generate unique backup codes', async () => {
      const result = await generateTOTPSecret(mockUserId);
      const uniqueCodes = new Set(result.backupCodes);
      expect(uniqueCodes.size).toBe(result.backupCodes.length);
    });
  });

  describe('verifyTOTPCode', () => {
    it('should return valid:true for correct TOTP code', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpSecret: mockSecret,
        failedTotpAttempts: 0,
        lockedUntil: null,
      });

      // Generate a valid token using the same secret
      const validToken = authenticator.generate(mockSecret);

      const result = await verifyTOTPCode(mockUserId, validToken);
      expect(result).toBe(true);
    });

    it('should return false for incorrect TOTP code', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpSecret: mockSecret,
        failedTotpAttempts: 0,
        lockedUntil: null,
      });

      const result = await verifyTOTPCode(mockUserId, '000000');
      expect(result).toBe(false);
    });

    it('should throw error if 2FA not enabled', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: false,
        totpSecret: null,
        failedTotpAttempts: 0,
        lockedUntil: null,
      });

      await expect(verifyTOTPCode(mockUserId, '123456')).rejects.toThrow('2FA not enabled');
    });

    it('should throw error if account is locked', async () => {
      const futureDate = new Date(Date.now() + 60000);
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpSecret: mockSecret,
        failedTotpAttempts: 5,
        lockedUntil: futureDate,
      });

      await expect(verifyTOTPCode(mockUserId, '123456')).rejects.toThrow('Account temporarily locked');
    });

    it('should increment failed attempts on wrong code', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpSecret: mockSecret,
        failedTotpAttempts: 2,
        lockedUntil: null,
      });

      await verifyTOTPCode(mockUserId, '000000');

      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: { failedTotpAttempts: 3 },
      });
    });

    it('should lock account after 5 failed attempts', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpSecret: mockSecret,
        failedTotpAttempts: 4,
        lockedUntil: null,
      });

      await verifyTOTPCode(mockUserId, '000000');

      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: expect.objectContaining({
          failedTotpAttempts: 5,
          lockedUntil: expect.any(Date),
        }),
      });
    });

    it('should reset failed attempts on correct code', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpSecret: mockSecret,
        failedTotpAttempts: 3,
        lockedUntil: null,
      });

      const validToken = authenticator.generate(mockSecret);
      await verifyTOTPCode(mockUserId, validToken);

      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: { failedTotpAttempts: 0, lastTotpAt: expect.any(Date) },
      });
    });
  });

  describe('generateBackupCodes', () => {
    it('should generate 10 backup codes', () => {
      const codes = generateBackupCodes();
      expect(codes).toHaveLength(10);
    });

    it('should generate codes of correct format', () => {
      const codes = generateBackupCodes();
      codes.forEach((code: string) => {
        expect(code).toMatch(/^\d{8}$/);
      });
    });

    it('should generate unique codes', () => {
      const codes = generateBackupCodes();
      const uniqueCodes = new Set(codes);
      expect(uniqueCodes.size).toBe(codes.length);
    });
  });

  describe('verifyBackupCode', () => {
    it('should return valid:true for correct backup code', async () => {
      const backupCodes = ['12345678', '87654321'];
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpBackupCodes: JSON.stringify(backupCodes),
      });

      const result = await verifyBackupCode(mockUserId, '12345678');
      expect(result.valid).toBe(true);
    });

    it('should remove used backup code from list', async () => {
      const backupCodes = ['12345678', '87654321'];
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpBackupCodes: JSON.stringify(backupCodes),
      });

      await verifyBackupCode(mockUserId, '12345678');

      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: {
          totpBackupCodes: JSON.stringify(['87654321']),
        },
      });
    });

    it('should return valid:false for incorrect backup code', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpBackupCodes: JSON.stringify(['12345678']),
      });

      const result = await verifyBackupCode(mockUserId, '00000000');
      expect(result.valid).toBe(false);
      expect(result.remainingCodes).toBe(1);
    });

    it('should return remainingAttempts when few codes left', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        totpBackupCodes: JSON.stringify(['11111111', '22222222']),
      });

      const result = await verifyBackupCode(mockUserId, '00000000');
      expect(result.remainingAttempts).toBe(2);
    });
  });

  describe('enable2FA', () => {
    it('should enable 2FA after verification', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: false,
        totpSecret: mockSecret,
        totpVerifiedAt: null,
      });

      const validToken = authenticator.generate(mockSecret);
      const result = await enable2FA(mockUserId, validToken);

      expect(result).toBe(true);
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: {
          totpEnabled: true,
          totpVerifiedAt: expect.any(Date),
        },
      });
    });

    it('should not enable 2FA if code is invalid', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: false,
        totpSecret: mockSecret,
        totpVerifiedAt: null,
      });

      const result = await enable2FA(mockUserId, '000000');

      expect(result).toBe(false);
      expect(prisma.user.update).not.toHaveBeenCalledWith(
        expect.objectContaining({ data: expect.objectContaining({ totpEnabled: true }) })
      );
    });

    it('should throw error if no secret exists', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: false,
        totpSecret: null,
        totpVerifiedAt: null,
      });

      await expect(enable2FA(mockUserId, '123456')).rejects.toThrow('2FA setup not initiated');
    });
  });

  describe('disable2FA', () => {
    it('should disable 2FA and clear secrets', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        passwordHash: 'hashedpassword',
      });

      const result = await disable2FA(mockUserId, 'correctpassword');

      expect(result).toBe(true);
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: mockUserId },
        data: {
          totpEnabled: false,
          totpSecret: null,
          totpBackupCodes: null,
          totpCreatedAt: null,
          totpVerifiedAt: null,
        },
      });
    });

    it('should not disable 2FA with wrong password', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
        passwordHash: 'hashedpassword',
      });

      const result = await disable2FA(mockUserId, 'wrongpassword');

      expect(result).toBe(false);
    });

    it('should return false if 2FA not enabled', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: false,
        passwordHash: 'hashedpassword',
      });

      const result = await disable2FA(mockUserId, 'correctpassword');

      expect(result).toBe(false);
    });
  });

  describe('is2FAEnabled', () => {
    it('should return true when 2FA is enabled', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: true,
      });

      const result = await is2FAEnabled(mockUserId);
      expect(result).toBe(true);
    });

    it('should return false when 2FA is not enabled', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: mockUserId,
        totpEnabled: false,
      });

      const result = await is2FAEnabled(mockUserId);
      expect(result).toBe(false);
    });

    it('should return false when user not found', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue(null);

      const result = await is2FAEnabled(mockUserId);
      expect(result).toBe(false);
    });
  });
});
