/**
 * Block Routes Tests
 *
 * Validates block API endpoints:
 * - POST /api/blocks
 * - GET /api/blocks/device/:deviceId
 * - GET /api/blocks/child/:childId
 * - GET /api/blocks/child/:childId/stats
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import * as deviceService from '../../services/device.service';
import * as blockService from '../../services/block.service';
import * as policyService from '../../services/policy.service';
import { query } from '../../db';
import { createTestUser } from '../fixtures/user.fixtures';

let app: FastifyInstance;

describe('Block Routes', () => {
  let testUserId: string;
  let testAccessToken: string;
  let testChildId: string;
  let testDeviceId: string;
  let testPolicyId: string;

  beforeAll(async () => {
    app = await createTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    const user = await createTestUser({ email: randomEmail() });
    testUserId = user.user.id;
    testAccessToken = user.accessToken;

    const child = await query(
      'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
      [testUserId, 'Test Child', 12]
    );
    testChildId = child[0].id;

    const device = await deviceService.registerDevice(testUserId, {
      device_type: 'mobile',
      device_name: 'Child Phone',
      device_fingerprint: randomString(16),
      child_id: testChildId,
    });
    testDeviceId = device.id;

    const policy = await policyService.createPolicy(testUserId, {
      child_id: testChildId,
      name: 'Block Games',
      policy_type: 'app',
      config: { blockedApps: ['com.games.app'] },
    });
    testPolicyId = policy.id;
  });

  describe('POST /api/blocks', () => {
    it('records a block event', async () => {
      const response = await request(app)
        .post('/api/blocks')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_id: testDeviceId,
          policy_id: testPolicyId,
          event_type: 'app',
          blocked_item: 'com.games.app',
          category: 'games',
          reason: 'policy_enforced',
          timestamp: new Date().toISOString(),
        })
        .expect(201);

      expect(response.body.id).toBeDefined();
      expect(response.body.device_id).toBe(testDeviceId);
      expect(response.body.policy_id).toBe(testPolicyId);
      expect(response.body.reason).toBe('policy_enforced');
    });

    it('rejects missing required fields', async () => {
      const response = await request(app)
        .post('/api/blocks')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_id: testDeviceId,
          // Missing event_type and blocked_item
        })
        .expect(400);

      expect(response.body.error).toContain('Missing required fields');
    });

    it('rejects invalid event type', async () => {
      const response = await request(app)
        .post('/api/blocks')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_id: testDeviceId,
          event_type: 'invalid',
          blocked_item: 'site.com',
        })
        .expect(400);

      expect(response.body.error).toContain('event_type');
    });

    it('returns 404 when device is not found', async () => {
      const response = await request(app)
        .post('/api/blocks')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_id: '00000000-0000-0000-0000-000000000000',
          event_type: 'website',
          blocked_item: 'unknown.com',
        })
        .expect(404);

      expect(response.body.error).toBe('Device not found');
    });

    it('rejects when device belongs to another user', async () => {
      const otherUser = await createTestUser({ email: randomEmail() });
      const otherDevice = await deviceService.registerDevice(otherUser.user.id, {
        device_type: 'mobile',
        device_name: 'Other Device',
        device_fingerprint: randomString(16),
      });

      const response = await request(app)
        .post('/api/blocks')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_id: otherDevice.id,
          event_type: 'website',
          blocked_item: 'denied.com',
        })
        .expect(403);

      expect(response.body.error).toContain('Not authorized');
    });
  });

  describe('GET /api/blocks/device/:deviceId', () => {
    beforeEach(async () => {
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'social.com',
      });
    });

    it('returns block events for the device', async () => {
      const response = await request(app)
        .get(`/api/blocks/device/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(Array.isArray(response.body)).toBe(true);
      expect(response.body.length).toBeGreaterThanOrEqual(1);
      expect(response.body[0].device_id).toBe(testDeviceId);
    });

    it('rejects requests for devices owned by another user', async () => {
      const otherUser = await createTestUser({ email: randomEmail() });
      const otherDevice = await deviceService.registerDevice(otherUser.user.id, {
        device_type: 'mobile',
        device_name: 'Other Device',
        device_fingerprint: randomString(16),
      });

      const response = await request(app)
        .get(`/api/blocks/device/${otherDevice.id}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(403);

      expect(response.body.error).toContain('Not authorized');
    });

    it('returns 404 when device does not exist', async () => {
      const response = await request(app)
        .get('/api/blocks/device/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.error).toBe('Device not found');
    });
  });

  describe('GET /api/blocks/child/:childId', () => {
    let secondDeviceId: string;

    beforeEach(async () => {
      const secondDevice = await deviceService.registerDevice(testUserId, {
        device_type: 'desktop',
        device_name: 'Child Laptop',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      });
      secondDeviceId = secondDevice.id;

      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'games.com',
      });

      await blockService.createBlockEvent({
        device_id: secondDeviceId,
        event_type: 'app',
        blocked_item: 'com.chat.app',
      });
    });

    it('returns block events across all child devices', async () => {
      const response = await request(app)
        .get(`/api/blocks/child/${testChildId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(Array.isArray(response.body)).toBe(true);
      const deviceIds = new Set(response.body.map((event: any) => event.device_id));
      expect(deviceIds.has(testDeviceId)).toBe(true);
      expect(deviceIds.has(secondDeviceId)).toBe(true);
    });

    it('rejects access when child belongs to another user', async () => {
      const otherUser = await createTestUser({ email: randomEmail() });
      const otherChild = await query(
        'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
        [otherUser.user.id, 'Other Child', 9]
      );

      const response = await request(app)
        .get(`/api/blocks/child/${otherChild[0].id}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(403);

      expect(response.body.error).toContain('Not authorized');
    });

    it('returns 404 when child does not exist', async () => {
      const response = await request(app)
        .get('/api/blocks/child/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.error).toBe('Child not found');
    });
  });

  describe('GET /api/blocks/child/:childId/stats', () => {
    beforeEach(async () => {
      const now = new Date();
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'social.com',
        category: 'social',
        timestamp: now,
      });
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'social.com',
        category: 'social',
        timestamp: now,
      });
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: 'app',
        blocked_item: 'com.games.app',
        category: 'games',
        timestamp: now,
      });
    });

    it('returns aggregated block statistics', async () => {
      const response = await request(app)
        .get(`/api/blocks/child/${testChildId}/stats`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .query({
          start_date: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
          end_date: new Date(Date.now() + 60 * 1000).toISOString(),
        })
        .expect(200);

      expect(Array.isArray(response.body)).toBe(true);
      const socialStat = response.body.find((stat: any) => stat.blocked_item === 'social.com');
      expect(socialStat.block_count).toBe(2);
    });

    it('validates required query params', async () => {
      const response = await request(app)
        .get(`/api/blocks/child/${testChildId}/stats`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(400);

      expect(response.body.error).toContain('Missing required query params');
    });
  });
});

