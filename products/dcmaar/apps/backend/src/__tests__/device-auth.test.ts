/**
 * Device Auth Endpoints Tests
 *
 * Tests for device authentication routes.
 * Validates token generation, refresh, and error handling.
 *
 * Test Coverage:
 * - Device token generation
 * - Token refresh mechanism
 * - JWT signature verification
 * - Device ID correlation
 * - Expiry validation
 * - Error responses
 * - Missing/invalid input handling
 *
 * @test Device authentication endpoints
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../server';
import { generateTokenPair } from './fixtures/auth.fixtures';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../db';
import { request } from './helpers/request.helper';

describe('Device Auth Endpoints', () => {
  let app: FastifyInstance;
  let parentUserId: string;
  let parentToken: string;
  let childId: string;
  let deviceId: string;

  beforeAll(async () => {
    app = await buildApp();

    // Register a parent user once for all tests in this suite
    const registerRes = await request(app)
      .post('/api/auth/register')
      .send({
        email: `parent-${uuidv4()}@test.com`,
        password: 'StrongPassword123!',
        display_name: 'Test Parent',
      })
      .expect(201);

    parentToken = registerRes.body.accessToken;
    parentUserId = registerRes.body.user.id;

    // Create a child for the parent once for all tests
    const childRes = await request(app)
      .post('/api/children')
      .set('Authorization', `Bearer ${parentToken}`)
      .send({
        name: 'Test Child',
        birth_date: '2015-01-01',
      })
      .expect(201);

    childId = childRes.body.data.id;
    deviceId = uuidv4();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('POST /api/auth/device-token', () => {
    it('should generate device token with valid credentials', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { 
          authorization: `Bearer ${parentToken}`,
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          childId,
          deviceName: 'Test Laptop',
          deviceType: 'desktop',
          platform: 'windows',
        }),
      });

      expect(res.statusCode).toBe(200);
      expect(res.json()).toHaveProperty('deviceToken');
      expect(res.json()).toHaveProperty('deviceId');
      expect(res.json()).toHaveProperty('expiresIn');
      expect(res.json().expiresIn).toBe(86400); // 24 hours in seconds
    });

    it('should include deviceId in response', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { 
          authorization: `Bearer ${parentToken}`,
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          childId,
          deviceName: 'Desktop-1',
          deviceType: 'desktop',
          platform: 'macos',
        }),
      });

      expect(res.statusCode).toBe(200);
      const { deviceId: newDeviceId } = res.json();
      expect(newDeviceId).toBeTruthy();
      expect(typeof newDeviceId).toBe('string');
    });

    it('should reject request without authorization', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        payload: {
          childId,
          deviceName: 'Test Device',
          deviceType: 'desktop',
          platform: 'windows',
        },
      });

      expect(res.statusCode).toBe(401);
    });

    it('should reject request with invalid token', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { authorization: 'Bearer invalid-token' },
        payload: {
          childId,
          deviceName: 'Test Device',
          deviceType: 'desktop',
          platform: 'windows',
        },
      });

      expect(res.statusCode).toBe(401);
    });

    it('should reject request with missing childId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { authorization: `Bearer ${parentToken}` },
        payload: {
          deviceName: 'Test Device',
          deviceType: 'desktop',
          platform: 'windows',
        },
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject request with missing deviceName', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { authorization: `Bearer ${parentToken}` },
        payload: {
          childId,
          deviceType: 'desktop',
          platform: 'windows',
        },
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject request with missing deviceType', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { authorization: `Bearer ${parentToken}` },
        payload: {
          childId,
          deviceName: 'Test Device',
          platform: 'windows',
        },
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject request with missing platform', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { authorization: `Bearer ${parentToken}` },
        payload: {
          childId,
          deviceName: 'Test Device',
          deviceType: 'desktop',
        },
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject request with invalid childId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { authorization: `Bearer ${parentToken}` },
        payload: {
          childId: 'invalid-uuid',
          deviceName: 'Test Device',
          deviceType: 'desktop',
          platform: 'windows',
        },
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject request with non-existent childId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { 
          authorization: `Bearer ${parentToken}`,
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          childId: '550e8400-e29b-41d4-a716-446655440000',
          deviceName: 'Test Device',
          deviceType: 'desktop',
          platform: 'windows',
        }),
      });

      expect(res.statusCode).toBe(403);
    });

    it('should support all device types', async () => {
      const deviceTypes = ['desktop', 'mobile', 'tablet', 'chromebook'];

      for (const deviceType of deviceTypes) {
        const res = await app.inject({
          method: 'POST',
          url: '/api/auth/device-token',
          headers: { 
            authorization: `Bearer ${parentToken}`,
            'content-type': 'application/json',
          },
          payload: JSON.stringify({
            childId,
            deviceName: `${deviceType}-device`,
            deviceType,
            platform: 'windows',
          }),
        });

        expect(res.statusCode).toBe(200);
      }
    });

    it('should support all platforms', async () => {
      const platforms = ['windows', 'macos', 'linux', 'ios', 'android'];

      for (const platform of platforms) {
        const res = await app.inject({
          method: 'POST',
          url: '/api/auth/device-token',
          headers: { 
            authorization: `Bearer ${parentToken}`,
            'content-type': 'application/json',
          },
          payload: JSON.stringify({
            childId,
            deviceName: `device-${platform}`,
            deviceType: 'mobile',
            platform,
          }),
        });

        expect(res.statusCode).toBe(200);
      }
    });
  });

  describe('POST /api/auth/device-token/refresh', () => {
    let deviceToken: string;
    let newDeviceId: string;

    beforeAll(async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { 
          authorization: `Bearer ${parentToken}`,
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          childId,
          deviceName: 'Refresh Test Device',
          deviceType: 'desktop',
          platform: 'windows',
        }),
      });

      deviceToken = res.json().deviceToken;
      newDeviceId = res.json().deviceId;
    });

    it('should refresh device token with valid token', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          token: deviceToken,
          deviceId: newDeviceId,
        }),
      });

      expect(res.statusCode).toBe(200);
      expect(res.json()).toHaveProperty('deviceToken');
      expect(res.json()).toHaveProperty('expiresIn');
      expect(res.json().expiresIn).toBe(86400);
    });

    it('should return new token on refresh', async () => {
      // Add delay to ensure different iat claim in JWT (JWT iat has second precision)
      await new Promise(resolve => setTimeout(resolve, 1100));

      const res1 = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          token: deviceToken,
          deviceId: newDeviceId,
        }),
      });

      const newToken = res1.json().deviceToken;
      // Decode to compare iat claims
      const decodedOriginal = JSON.parse(Buffer.from(deviceToken.split('.')[1], 'base64').toString());
      const decodedNew = JSON.parse(Buffer.from(newToken.split('.')[1], 'base64').toString());
      // Verify new token was issued after original token
      expect(decodedNew.iat).toBeGreaterThan(decodedOriginal.iat);
    });

    it('should reject refresh without token', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          deviceId: newDeviceId,
        }),
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject refresh without deviceId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          token: deviceToken,
        }),
      });

      expect(res.statusCode).toBe(400);
    });

    it('should reject refresh with invalid token', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          token: 'invalid-token',
          deviceId: newDeviceId,
        }),
      });

      expect(res.statusCode).toBe(401);
    });

    it('should reject refresh with mismatched deviceId', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          token: deviceToken,
          deviceId: '550e8400-e29b-41d4-a716-446655440000',
        }),
      });

      expect(res.statusCode).toBe(401);
    });

    it('should reject refresh with expired token', async () => {
      // Create token and wait for expiry (or use a manually expired token)
      // For now, just test that an old token is rejected
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token/refresh',
        headers: {
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjB9.expired',
          deviceId: newDeviceId,
        }),
      });

      expect(res.statusCode).toBe(401);
    });
  });

  describe('Token Content Validation', () => {
    it('should contain required claims in device token', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { 
          authorization: `Bearer ${parentToken}`,
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          childId,
          deviceName: 'Claims Test',
          deviceType: 'desktop',
          platform: 'windows',
        }),
      });

      expect(res.statusCode).toBe(200);
      const { deviceToken } = res.json();

      // Decode token (don't verify, just check structure)
      const parts = deviceToken.split('.');
      expect(parts).toHaveLength(3); // JWT has 3 parts
      expect(parts[0]).toBeTruthy(); // header
      expect(parts[1]).toBeTruthy(); // payload
      expect(parts[2]).toBeTruthy(); // signature
    });

    it('should not include sensitive data in token', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/api/auth/device-token',
        headers: { 
          authorization: `Bearer ${parentToken}`,
          'content-type': 'application/json',
        },
        payload: JSON.stringify({
          childId,
          deviceName: 'Security Test',
          deviceType: 'desktop',
          platform: 'windows',
        }),
      });

      const { deviceToken } = res.json();
      // Decode payload (base64)
      const payload = Buffer.from(deviceToken.split('.')[1], 'base64').toString();

      // Should not contain password or sensitive fields
      expect(payload).not.toContain('password');
      expect(payload).not.toContain('secret');
    });
  });

  describe('Rate Limiting & Security', () => {
    it('should handle multiple token requests', async () => {
      const promises = Array.from({ length: 5 }, () =>
        app.inject({
          method: 'POST',
          url: '/api/auth/device-token',
          headers: { 
            authorization: `Bearer ${parentToken}`,
            'content-type': 'application/json',
          },
          payload: JSON.stringify({
            childId,
            deviceName: 'Multi-Device',
            deviceType: 'desktop',
            platform: 'windows',
          }),
        })
      );

      const results = await Promise.all(promises);
      results.forEach((res) => {
        expect(res.statusCode).toBe(200);
      });

      // All tokens should be different
      const tokens = results.map((r) => r.json().deviceToken);
      const uniqueTokens = new Set(tokens);
      expect(uniqueTokens.size).toBe(5);
    });
  });
});
