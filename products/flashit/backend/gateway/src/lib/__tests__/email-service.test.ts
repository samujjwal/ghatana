/**
 * @fileoverview Email Service Tests
 * Tests for production email delivery with SMTP and SES
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  sendEmail,
  sendTemplatedEmail,
  verifyEmailConfig,
  createSMTPTransport,
  createSESTransport,
  testEmailConfig,
  retryWithBackoff,
} from '../email-service';

describe('Email Service', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    vi.clearAllMocks();
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
    vi.restoreAllMocks();
  });

  describe('verifyEmailConfig', () => {
    it('should return valid:false when EMAIL_PROVIDER is not set', () => {
      delete process.env.EMAIL_PROVIDER;

      const result = verifyEmailConfig();

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('EMAIL_PROVIDER is not set');
    });

    it('should validate SMTP configuration', () => {
      process.env.EMAIL_PROVIDER = 'smtp';
      process.env.SMTP_HOST = 'smtp.example.com';
      process.env.SMTP_PORT = '587';
      process.env.SMTP_USER = 'user@example.com';
      process.env.SMTP_PASS = 'password';
      process.env.EMAIL_FROM = 'noreply@example.com';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(true);
      expect(result.provider).toBe('smtp');
      expect(result.errors).toHaveLength(0);
    });

    it('should detect missing SMTP_HOST', () => {
      process.env.EMAIL_PROVIDER = 'smtp';
      process.env.SMTP_PORT = '587';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('SMTP_HOST is required for SMTP provider');
    });

    it('should detect missing SMTP_PORT', () => {
      process.env.EMAIL_PROVIDER = 'smtp';
      process.env.SMTP_HOST = 'smtp.example.com';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('SMTP_PORT is required for SMTP provider');
    });

    it('should validate SES configuration', () => {
      process.env.EMAIL_PROVIDER = 'ses';
      process.env.AWS_REGION = 'us-east-1';
      process.env.AWS_ACCESS_KEY_ID = 'AKIAIOSFODNN7EXAMPLE';
      process.env.AWS_SECRET_ACCESS_KEY = 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY';
      process.env.EMAIL_FROM = 'noreply@example.com';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(true);
      expect(result.provider).toBe('ses');
    });

    it('should detect missing AWS_REGION for SES', () => {
      process.env.EMAIL_PROVIDER = 'ses';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('AWS_REGION is required for SES provider');
    });

    it('should detect missing EMAIL_FROM', () => {
      process.env.EMAIL_PROVIDER = 'smtp';
      process.env.SMTP_HOST = 'smtp.example.com';
      process.env.SMTP_PORT = '587';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('EMAIL_FROM is required');
    });

    it('should validate stub provider in non-production', () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(true);
      expect(result.provider).toBe('stub');
    });

    it('should reject stub provider in production', () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'production';

      const result = verifyEmailConfig();

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('STUB provider is not allowed in production');
    });
  });

  describe('sendEmail', () => {
    it('should throw error for stub provider in production', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'production';

      await expect(
        sendEmail({
          to: 'test@example.com',
          subject: 'Test',
          text: 'Test message',
        })
      ).rejects.toThrow('STUB email provider cannot be used in production');
    });

    it('should log email for stub provider in development', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

      const result = await sendEmail({
        to: 'test@example.com',
        subject: 'Test',
        text: 'Test message',
      });

      expect(result.messageId).toMatch(/^stub-/);
      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('test@example.com')
      );
    });

    it('should handle missing to address', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      await expect(
        sendEmail({
          to: '',
          subject: 'Test',
          text: 'Test message',
        })
      ).rejects.toThrow('Recipient address is required');
    });

    it('should handle missing subject and body', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      await expect(
        sendEmail({
          to: 'test@example.com',
          subject: '',
          text: '',
          html: '',
        })
      ).rejects.toThrow('Email must have subject and either text or html body');
    });

    it('should validate email format', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      await expect(
        sendEmail({
          to: 'invalid-email',
          subject: 'Test',
          text: 'Test message',
        })
      ).rejects.toThrow('Invalid email address format');
    });
  });

  describe('sendTemplatedEmail', () => {
    it('should throw error for non-existent template', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      await expect(
        sendTemplatedEmail({
          to: 'test@example.com',
          template: 'non-existent-template',
          data: {},
        })
      ).rejects.toThrow('Template "non-existent-template" not found');
    });

    it('should render welcome email template', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      const result = await sendTemplatedEmail({
        to: 'test@example.com',
        template: 'welcome',
        data: {
          userName: 'John Doe',
          verificationLink: 'https://example.com/verify',
        },
      });

      expect(result.messageId).toBeDefined();
    });

    it('should render password reset template', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      const result = await sendTemplatedEmail({
        to: 'test@example.com',
        template: 'password-reset',
        data: {
          userName: 'John Doe',
          resetLink: 'https://example.com/reset',
          expiresIn: '1 hour',
        },
      });

      expect(result.messageId).toBeDefined();
    });

    it('should render 2FA backup codes template', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      const result = await sendTemplatedEmail({
        to: 'test@example.com',
        template: '2fa-backup-codes',
        data: {
          userName: 'John Doe',
          backupCodes: ['12345678', '87654321'],
        },
      });

      expect(result.messageId).toBeDefined();
    });

    it('should render security alert template', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      const result = await sendTemplatedEmail({
        to: 'test@example.com',
        template: 'security-alert',
        data: {
          userName: 'John Doe',
          eventType: 'New device login',
          eventDetails: 'Chrome on Windows, IP: 192.168.1.1',
          actionLink: 'https://example.com/security',
        },
      });

      expect(result.messageId).toBeDefined();
    });
  });

  describe('retryWithBackoff', () => {
    it('should succeed on first attempt', async () => {
      const operation = vi.fn().mockResolvedValue('success');

      const result = await retryWithBackoff(operation, 3, 100);

      expect(result).toBe('success');
      expect(operation).toHaveBeenCalledTimes(1);
    });

    it('should retry on failure and succeed', async () => {
      const operation = vi.fn()
        .mockRejectedValueOnce(new Error('First failure'))
        .mockResolvedValue('success');

      const result = await retryWithBackoff(operation, 3, 10);

      expect(result).toBe('success');
      expect(operation).toHaveBeenCalledTimes(2);
    });

    it('should throw after max retries', async () => {
      const operation = vi.fn().mockRejectedValue(new Error('Persistent failure'));

      await expect(retryWithBackoff(operation, 3, 10)).rejects.toThrow('Persistent failure');
      expect(operation).toHaveBeenCalledTimes(3);
    });

    it('should not retry on non-retryable error', async () => {
      const error = new Error('Invalid credentials');
      (error as any).code = 'EAUTH';
      const operation = vi.fn().mockRejectedValue(error);

      await expect(retryWithBackoff(operation, 3, 10)).rejects.toThrow('Invalid credentials');
      expect(operation).toHaveBeenCalledTimes(1);
    });
  });

  describe('testEmailConfig', () => {
    it('should return success for stub provider', async () => {
      process.env.EMAIL_PROVIDER = 'stub';
      process.env.NODE_ENV = 'development';

      const result = await testEmailConfig('test@example.com');

      expect(result.success).toBe(true);
    });

    it('should return failure for invalid configuration', async () => {
      delete process.env.EMAIL_PROVIDER;

      const result = await testEmailConfig('test@example.com');

      expect(result.success).toBe(false);
      expect(result.error).toContain('EMAIL_PROVIDER is not set');
    });
  });
});
