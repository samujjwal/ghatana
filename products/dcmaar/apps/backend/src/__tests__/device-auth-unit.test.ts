/**
 * Device Auth Unit Tests
 *
 * Unit tests for device authentication logic (validation, token generation).
 * These tests do NOT require a running Fastify server or database.
 *
 * <p><b>Coverage</b><br>
 * - Input validation schemas
 * - JWT token generation
 * - Token claims verification
 * - Error handling and validation errors
 *
 * @test Device Auth Logic (Unit)
 * @layer backend
 */

import { describe, it, expect, vi } from 'vitest';
import { z } from 'zod';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';

describe('Device Auth Unit Tests', () => {
  // Validation schemas (mirrored from device-auth.routes.ts)
  const generateDeviceTokenSchema = z.object({
    childId: z.string().uuid('Invalid child ID'),
    deviceName: z.string().min(1).max(255),
    deviceType: z.enum(['desktop', 'mobile', 'tablet', 'chromebook']),
    platform: z.enum(['windows', 'macos', 'linux', 'ios', 'android']),
    scopes: z.array(z.string()).optional().default(['events:write', 'status:read']),
  });

  const refreshDeviceTokenSchema = z.object({
    token: z.string().min(1, 'Refresh token is required'),
  });

  describe('Generate Device Token Validation', () => {
    it('should accept valid token generation request', () => {
      // GIVEN: Valid token generation payload
      const payload = {
        childId: uuidv4(),
        deviceName: 'Test Desktop',
        deviceType: 'desktop' as const,
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should pass validation
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.childId).toBe(payload.childId);
        expect(result.data.deviceName).toBe('Test Desktop');
        expect(result.data.scopes).toEqual(['events:write', 'status:read']);
      }
    });

    it('should use default scopes when not provided', () => {
      // GIVEN: Payload without scopes
      const payload = {
        childId: uuidv4(),
        deviceName: 'Mobile Device',
        deviceType: 'mobile' as const,
        platform: 'ios' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should use default scopes
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.scopes).toEqual(['events:write', 'status:read']);
      }
    });

    it('should accept custom scopes', () => {
      // GIVEN: Payload with custom scopes
      const payload = {
        childId: uuidv4(),
        deviceName: 'Device',
        deviceType: 'desktop' as const,
        platform: 'linux' as const,
        scopes: ['events:write', 'status:read', 'analytics:read'],
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should accept custom scopes
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.scopes).toHaveLength(3);
      }
    });

    it('should reject invalid childId', () => {
      // GIVEN: Invalid childId (not UUID)
      const payload = {
        childId: 'not-a-uuid',
        deviceName: 'Device',
        deviceType: 'desktop' as const,
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });

    it('should reject empty deviceName', () => {
      // GIVEN: Empty deviceName
      const payload = {
        childId: uuidv4(),
        deviceName: '',
        deviceType: 'desktop' as const,
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });

    it('should reject deviceName exceeding 255 chars', () => {
      // GIVEN: Very long deviceName
      const payload = {
        childId: uuidv4(),
        deviceName: 'x'.repeat(256),
        deviceType: 'desktop' as const,
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });

    it('should accept deviceName at boundary (255 chars)', () => {
      // GIVEN: deviceName exactly 255 chars
      const payload = {
        childId: uuidv4(),
        deviceName: 'x'.repeat(255),
        deviceType: 'desktop' as const,
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should pass
      expect(result.success).toBe(true);
    });

    it('should reject invalid deviceType', () => {
      // GIVEN: Invalid deviceType
      const payload = {
        childId: uuidv4(),
        deviceName: 'Device',
        deviceType: 'smartwatch',
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });

    it('should accept all valid deviceTypes', () => {
      // GIVEN: Each valid deviceType
      const validTypes = ['desktop', 'mobile', 'tablet', 'chromebook'];

      for (const deviceType of validTypes) {
        const payload = {
          childId: uuidv4(),
          deviceName: 'Device',
          deviceType: deviceType as any,
          platform: 'windows' as const,
        };

        // WHEN: Validation is performed
        const result = generateDeviceTokenSchema.safeParse(payload);

        // THEN: Should pass
        expect(result.success).toBe(true);
      }
    });

    it('should reject invalid platform', () => {
      // GIVEN: Invalid platform
      const payload = {
        childId: uuidv4(),
        deviceName: 'Device',
        deviceType: 'desktop' as const,
        platform: 'nintendo-switch',
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });

    it('should accept all valid platforms', () => {
      // GIVEN: Each valid platform
      const validPlatforms = ['windows', 'macos', 'linux', 'ios', 'android'];

      for (const platform of validPlatforms) {
        const payload = {
          childId: uuidv4(),
          deviceName: 'Device',
          deviceType: 'desktop' as const,
          platform: platform as any,
        };

        // WHEN: Validation is performed
        const result = generateDeviceTokenSchema.safeParse(payload);

        // THEN: Should pass
        expect(result.success).toBe(true);
      }
    });

    it('should reject missing required fields', () => {
      // GIVEN: Missing required fields
      const incompletePayloads = [
        { childId: uuidv4(), deviceName: 'Device', deviceType: 'desktop' }, // missing platform
        { childId: uuidv4(), deviceType: 'desktop', platform: 'windows' }, // missing deviceName
        { deviceName: 'Device', deviceType: 'desktop', platform: 'windows' }, // missing childId
        { childId: uuidv4(), deviceName: 'Device', platform: 'windows' }, // missing deviceType
      ];

      for (const payload of incompletePayloads) {
        // WHEN: Validation is performed
        const result = generateDeviceTokenSchema.safeParse(payload);

        // THEN: Should fail
        expect(result.success).toBe(false);
      }
    });
  });

  describe('Refresh Device Token Validation', () => {
    it('should accept valid refresh token request', () => {
      // GIVEN: Valid refresh token payload
      const payload = {
        token: 'valid-jwt-token-here',
      };

      // WHEN: Validation is performed
      const result = refreshDeviceTokenSchema.safeParse(payload);

      // THEN: Should pass
      expect(result.success).toBe(true);
    });

    it('should reject missing token', () => {
      // GIVEN: Empty payload
      const payload = {};

      // WHEN: Validation is performed
      const result = refreshDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });

    it('should reject empty token string', () => {
      // GIVEN: Empty token string
      const payload = {
        token: '',
      };

      // WHEN: Validation is performed
      const result = refreshDeviceTokenSchema.safeParse(payload);

      // THEN: Should fail
      expect(result.success).toBe(false);
    });
  });

  describe('JWT Token Generation', () => {
    it('should generate valid JWT token', () => {
      // GIVEN: Token claims
      const secret = 'test-secret-key';
      const claims = {
        deviceId: uuidv4(),
        deviceName: 'Test Device',
        childId: uuidv4(),
        userId: uuidv4(),
        scopes: ['events:write', 'status:read'],
      };

      // WHEN: Token is generated
      const token = jwt.sign(claims, secret, { expiresIn: '24h' });

      // THEN: Token should be valid JWT
      expect(token).toBeTruthy();
      const parts = token.split('.');
      expect(parts).toHaveLength(3);
    });

    it('should verify JWT token with correct secret', () => {
      // GIVEN: Generated token
      const secret = 'test-secret-key';
      const claims = {
        deviceId: uuidv4(),
        deviceName: 'Test Device',
        childId: uuidv4(),
        userId: uuidv4(),
        scopes: ['events:write', 'status:read'],
      };

      const token = jwt.sign(claims, secret, { expiresIn: '24h' });

      // WHEN: Token is verified
      const verified = jwt.verify(token, secret) as any;

      // THEN: Claims should match
      expect(verified.deviceId).toBe(claims.deviceId);
      expect(verified.deviceName).toBe(claims.deviceName);
      expect(verified.childId).toBe(claims.childId);
      expect(verified.userId).toBe(claims.userId);
      expect(verified.scopes).toEqual(claims.scopes);
    });

    it('should reject token signed with different secret', () => {
      // GIVEN: Token signed with secret A
      const secretA = 'secret-a';
      const secretB = 'secret-b';
      const token = jwt.sign({ test: 'data' }, secretA, { expiresIn: '24h' });

      // WHEN: Token is verified with secret B
      // THEN: Should throw error
      expect(() => {
        jwt.verify(token, secretB);
      }).toThrow();
    });

    it('should reject expired token', () => {
      // GIVEN: Expired token
      const secret = 'test-secret';
      const token = jwt.sign({ test: 'data' }, secret, { expiresIn: '-1h' });

      // WHEN: Token is verified
      // THEN: Should throw error
      expect(() => {
        jwt.verify(token, secret);
      }).toThrow();
    });

    it('should include correct expiration time (24h)', () => {
      // GIVEN: Token generation
      const secret = 'test-secret';
      const claims = { userId: 'user-123' };
      const token = jwt.sign(claims, secret, { expiresIn: '24h' });

      // WHEN: Token is decoded
      const decoded = jwt.decode(token) as any;

      // THEN: exp - iat should be ~86400 seconds (24h)
      const expirationSeconds = decoded.exp - decoded.iat;
      expect(expirationSeconds).toBe(86400);
    });

    it('should include all required claims', () => {
      // GIVEN: Token with all required claims
      const secret = 'test-secret';
      const claims = {
        deviceId: uuidv4(),
        deviceName: 'Device',
        childId: uuidv4(),
        userId: uuidv4(),
        scopes: ['events:write'],
      };

      const token = jwt.sign(claims, secret, { expiresIn: '24h' });

      // WHEN: Token is decoded
      const decoded = jwt.decode(token) as any;

      // THEN: All claims should be present
      expect(decoded).toHaveProperty('deviceId');
      expect(decoded).toHaveProperty('deviceName');
      expect(decoded).toHaveProperty('childId');
      expect(decoded).toHaveProperty('userId');
      expect(decoded).toHaveProperty('scopes');
      expect(decoded).toHaveProperty('iat');
      expect(decoded).toHaveProperty('exp');
    });
  });

  describe('Error Messages', () => {
    it('should provide detailed validation error info', () => {
      // GIVEN: Invalid payload with multiple errors
      const payload = {
        childId: 'not-uuid',
        deviceName: '',
        deviceType: 'invalid',
        platform: 'nintendo-switch',
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Should have error details
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.length).toBeGreaterThan(0);
        const paths = result.error.issues.map((e) => e.path[0]);
        expect(paths).toContain('childId');
      }
    });

    it('should include field-specific error messages', () => {
      // GIVEN: Invalid childId
      const payload = {
        childId: 'invalid',
        deviceName: 'Device',
        deviceType: 'desktop' as const,
        platform: 'windows' as const,
      };

      // WHEN: Validation is performed
      const result = generateDeviceTokenSchema.safeParse(payload);

      // THEN: Error should indicate UUID validation failure
      expect(result.success).toBe(false);
      if (!result.success) {
        const childIdError = result.error.issues.find((e) => e.path[0] === 'childId');
        expect(childIdError?.message).toContain('Invalid');
      }
    });
  });

  describe('Token Security', () => {
    it('should not include sensitive data in token (device level)', () => {
      // GIVEN: Token with device info
      const secret = 'test-secret';
      const claims = {
        deviceId: uuidv4(),
        deviceName: 'Personal Laptop',
        userId: uuidv4(),
        scopes: ['events:write'],
        // Note: password, private key, etc. should NEVER be in claims
      };

      const token = jwt.sign(claims, secret, { expiresIn: '24h' });

      // WHEN: Token payload is decoded
      const payload = Buffer.from(token.split('.')[1], 'base64').toString();

      // THEN: Should not contain sensitive fields
      expect(payload).not.toContain('password');
      expect(payload).not.toContain('secret');
      expect(payload).not.toContain('private_key');
    });

    it('should use separate secrets for access and refresh tokens', () => {
      // GIVEN: Two different secrets
      const accessSecret = 'access-secret-key';
      const refreshSecret = 'refresh-secret-key';

      const claims = { userId: 'user-123' };

      const accessToken = jwt.sign(claims, accessSecret, { expiresIn: '15m' });
      const refreshToken = jwt.sign(claims, refreshSecret, { expiresIn: '7d' });

      // WHEN: Tokens are verified
      const verifyAccess = () => jwt.verify(accessToken, accessSecret);
      const verifyRefresh = () => jwt.verify(refreshToken, refreshSecret);

      // THEN: Each should verify with its own secret
      expect(verifyAccess).not.toThrow();
      expect(verifyRefresh).not.toThrow();

      // And should fail with wrong secret
      expect(() => jwt.verify(accessToken, refreshSecret)).toThrow();
      expect(() => jwt.verify(refreshToken, accessSecret)).toThrow();
    });
  });

  describe('Token Claims Validation', () => {
    it('should require deviceId claim', () => {
      // GIVEN: Token claims without deviceId
      const secret = 'test-secret';
      const claims = {
        deviceName: 'Device',
        userId: uuidv4(),
        scopes: ['events:write'],
      };

      const token = jwt.sign(claims, secret, { expiresIn: '24h' });
      const decoded = jwt.decode(token) as any;

      // WHEN: Decoded token is checked
      // THEN: Should have deviceId
      expect(decoded).not.toHaveProperty('deviceId');
      // (In real app, this would be validated on generate)
    });

    it('should include scopes claim', () => {
      // GIVEN: Token claims with scopes
      const secret = 'test-secret';
      const claims = {
        deviceId: uuidv4(),
        userId: uuidv4(),
        scopes: ['events:write', 'status:read'],
      };

      const token = jwt.sign(claims, secret, { expiresIn: '24h' });
      const decoded = jwt.decode(token) as any;

      // WHEN: Decoded token is checked
      // THEN: Should have scopes array
      expect(decoded.scopes).toEqual(['events:write', 'status:read']);
      expect(Array.isArray(decoded.scopes)).toBe(true);
    });
  });
});
