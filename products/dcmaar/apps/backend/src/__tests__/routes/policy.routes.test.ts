/**
 * Policy Routes Tests
 *
 * Tests policy API endpoints including:
 * - POST /api/policies
 * - GET /api/policies
 * - GET /api/policies/stats
 * - GET /api/policies/:id
 * - PUT /api/policies/:id
 * - DELETE /api/policies/:id
 * - POST /api/policies/bulk/toggle
 * - GET /api/policies/device/:deviceId
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import * as authService from '../../services/auth.service';
import * as policyService from '../../services/policy.service';
import * as deviceService from '../../services/device.service';
import { query } from '../../db';

let app: FastifyInstance;

describe('Policy Routes', () => {
  let testUserId: string;
  let testAccessToken: string;
  let testChildId: string;
  let testDeviceId: string;

  beforeAll(async () => {
    app = await createTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    // Create test user
    const user = await authService.register({
      email: randomEmail(),
      password: 'TestPassword123!',
    });
    testUserId = user.user.id;
    testAccessToken = user.accessToken;

    // Create test child
    const child = await query(
      'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
      [testUserId, 'Test Child', 10]
    );
    testChildId = child[0].id;

    // Create test device
    const device = await deviceService.registerDevice(testUserId, {
      device_type: 'mobile',
      device_name: 'Test Device',
      device_fingerprint: randomString(16),
      child_id: testChildId,
    });
    testDeviceId = device.id;
  });

  describe('POST /api/policies', () => {
    it('should create a website blocking policy', async () => {
      const policyData = {
        name: 'Block Social Media',
        policy_type: 'website',
        config: {
          blockedDomains: ['facebook.com', 'twitter.com'],
        },
      };

      const response = await request(app)
        .post('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(policyData)
        .expect(201);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.name).toBe('Block Social Media');
      expect(response.body.data.policy_type).toBe('website');
      expect(response.body.data.user_id).toBe(testUserId);
    });

    it('should create an app blocking policy', async () => {
      const policyData = {
        name: 'Block Games',
        policy_type: 'app',
        config: {
          blockedApps: ['com.game.example'],
        },
      };

      const response = await request(app)
        .post('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(policyData)
        .expect(201);

      expect(response.body.data.policy_type).toBe('app');
    });

    it('should create child-specific policy', async () => {
      const policyData = {
        child_id: testChildId,
        name: 'Child Policy',
        policy_type: 'website',
        config: { blockedDomains: ['child.com'] },
      };

      const response = await request(app)
        .post('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(policyData)
        .expect(201);

      expect(response.body.data.child_id).toBe(testChildId);
    });

    it('should create device-specific policy', async () => {
      const policyData = {
        device_id: testDeviceId,
        name: 'Device Policy',
        policy_type: 'website',
        config: { blockedDomains: ['device.com'] },
      };

      const response = await request(app)
        .post('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(policyData)
        .expect(201);

      expect(response.body.data.device_id).toBe(testDeviceId);
    });

    it('should reject missing required fields', async () => {
      const response = await request(app)
        .post('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          name: 'Incomplete Policy',
          // Missing policy_type and config
        })
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject invalid policy type', async () => {
      const response = await request(app)
        .post('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          name: 'Invalid Policy',
          policy_type: 'invalid-type',
          config: {},
        })
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject unauthenticated request', async () => {
      const response = await request(app)
        .post('/api/policies')
        .send({
          name: 'Test Policy',
          policy_type: 'website',
          config: {},
        })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('GET /api/policies', () => {
    beforeEach(async () => {
      // Create multiple test policies
      await policyService.createPolicy(testUserId, {
        name: 'Website Policy',
        policy_type: 'website',
        child_id: testChildId,
        config: { blockedDomains: ['test.com'] },
      });

      await policyService.createPolicy(testUserId, {
        name: 'App Policy',
        policy_type: 'app',
        config: { blockedApps: ['app'] },
      });

      await policyService.createPolicy(testUserId, {
        name: 'Disabled Policy',
        policy_type: 'website',
        enabled: false,
        config: { blockedDomains: ['disabled.com'] },
      });
    });

    it('should list all user policies', async () => {
      const response = await request(app)
        .get('/api/policies')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.length).toBeGreaterThanOrEqual(3);
      expect(response.body.count).toBeGreaterThanOrEqual(3);
    });

    it('should filter policies by child_id', async () => {
      const response = await request(app)
        .get(`/api/policies?child_id=${testChildId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.every((p: any) => p.child_id === testChildId)).toBe(true);
    });

    it('should filter policies by policy_type', async () => {
      const response = await request(app)
        .get('/api/policies?policy_type=website')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.every((p: any) => p.policy_type === 'website')).toBe(true);
    });

    it('should filter enabled policies', async () => {
      const response = await request(app)
        .get('/api/policies?enabled=true')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.every((p: any) => p.enabled === true)).toBe(true);
    });

    it('should reject unauthenticated request', async () => {
      const response = await request(app)
        .get('/api/policies')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('GET /api/policies/stats', () => {
    beforeEach(async () => {
      await policyService.createPolicy(testUserId, {
        name: 'Policy 1',
        policy_type: 'website',
        config: { blockedDomains: ['test1.com'] },
      });

      await policyService.createPolicy(testUserId, {
        name: 'Policy 2',
        policy_type: 'app',
        enabled: false,
        config: { blockedApps: ['app'] },
      });
    });

    it('should return policy statistics', async () => {
      const response = await request(app)
        .get('/api/policies/stats')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.total).toBeGreaterThanOrEqual(2);
      expect(response.body.data.enabled).toBeDefined();
      expect(response.body.data.disabled).toBeDefined();
      expect(response.body.data.by_type).toBeDefined();
    });
  });

  describe('GET /api/policies/:id', () => {
    let testPolicyId: string;

    beforeEach(async () => {
      const policy = await policyService.createPolicy(testUserId, {
        name: 'Test Policy',
        policy_type: 'website',
        config: { blockedDomains: ['test.com'] },
      });
      testPolicyId = policy.id;
    });

    it('should get policy by ID', async () => {
      const response = await request(app)
        .get(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.id).toBe(testPolicyId);
      expect(response.body.data.name).toBe('Test Policy');
    });

    it('should reject access to another user policy', async () => {
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });

      const response = await request(app)
        .get(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${otherUser.accessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('not found');
    });

    it('should return 404 for non-existent policy', async () => {
      const response = await request(app)
        .get('/api/policies/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('PUT /api/policies/:id', () => {
    let testPolicyId: string;

    beforeEach(async () => {
      const policy = await policyService.createPolicy(testUserId, {
        name: 'Original Policy',
        policy_type: 'website',
        priority: 10,
        config: { blockedDomains: ['original.com'] },
      });
      testPolicyId = policy.id;
    });

    it('should update policy name', async () => {
      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ name: 'Updated Policy' })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.name).toBe('Updated Policy');
    });

    it('should update policy enabled status', async () => {
      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ enabled: false })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.enabled).toBe(false);
    });

    it('should update policy priority', async () => {
      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ priority: 75 })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.priority).toBe(75);
    });

    it('should update policy config', async () => {
      const newConfig = { blockedDomains: ['new.com'] };

      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ config: newConfig })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.config).toEqual(newConfig);
    });

    it('should update multiple fields', async () => {
      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          name: 'Multi Update',
          enabled: false,
          priority: 99,
        })
        .expect(200);

      expect(response.body.data.name).toBe('Multi Update');
      expect(response.body.data.enabled).toBe(false);
      expect(response.body.data.priority).toBe(99);
    });

    it('should reject empty updates', async () => {
      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({})
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject access to another user policy', async () => {
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });

      const response = await request(app)
        .put(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${otherUser.accessToken}`)
        .send({ name: 'Hacked' })
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('DELETE /api/policies/:id', () => {
    let testPolicyId: string;

    beforeEach(async () => {
      const policy = await policyService.createPolicy(testUserId, {
        name: 'To Delete',
        policy_type: 'website',
        config: { blockedDomains: ['delete.com'] },
      });
      testPolicyId = policy.id;
    });

    it('should delete policy', async () => {
      const response = await request(app)
        .delete(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.message).toContain('success');

      // Verify policy is deleted
      const policy = await policyService.getPolicyById(testUserId, testPolicyId);
      expect(policy).toBeNull();
    });

    it('should reject access to another user policy', async () => {
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });

      const response = await request(app)
        .delete(`/api/policies/${testPolicyId}`)
        .set('Authorization', `Bearer ${otherUser.accessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
    });

    it('should return 404 for non-existent policy', async () => {
      const response = await request(app)
        .delete('/api/policies/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('POST /api/policies/bulk/toggle', () => {
    let policyIds: string[];

    beforeEach(async () => {
      policyIds = [];

      for (let i = 0; i < 3; i++) {
        const policy = await policyService.createPolicy(testUserId, {
          name: `Policy ${i}`,
          policy_type: 'website',
          config: { blockedDomains: [`test${i}.com`] },
        });
        policyIds.push(policy.id);
      }
    });

    it('should disable multiple policies', async () => {
      const response = await request(app)
        .post('/api/policies/bulk/toggle')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          policy_ids: policyIds,
          enabled: false,
        })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.count).toBe(3);
      expect(response.body.message).toContain('disabled');

      // Verify policies are disabled
      for (const id of policyIds) {
        const policy = await policyService.getPolicyById(testUserId, id);
        expect(policy?.enabled).toBe(false);
      }
    });

    it('should enable multiple policies', async () => {
      // Disable first
      await policyService.togglePolicies(testUserId, policyIds, false);

      // Then enable
      const response = await request(app)
        .post('/api/policies/bulk/toggle')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          policy_ids: policyIds,
          enabled: true,
        })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.count).toBe(3);
      expect(response.body.message).toContain('enabled');
    });

    it('should reject missing policy_ids', async () => {
      const response = await request(app)
        .post('/api/policies/bulk/toggle')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ enabled: false })
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject missing enabled field', async () => {
      const response = await request(app)
        .post('/api/policies/bulk/toggle')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ policy_ids: policyIds })
        .expect(400);

      expect(response.body.success).toBe(false);
    });
  });

  describe('GET /api/policies/device/:deviceId', () => {
    beforeEach(async () => {
      // Create policies at different levels
      await policyService.createPolicy(testUserId, {
        name: 'Global Policy',
        policy_type: 'website',
        config: { blockedDomains: ['global.com'] },
      });

      await policyService.createPolicy(testUserId, {
        child_id: testChildId,
        name: 'Child Policy',
        policy_type: 'website',
        config: { blockedDomains: ['child.com'] },
      });

      await policyService.createPolicy(testUserId, {
        device_id: testDeviceId,
        name: 'Device Policy',
        policy_type: 'website',
        config: { blockedDomains: ['device.com'] },
      });
    });

    it('should get all applicable policies for device', async () => {
      const response = await request(app)
        .get(`/api/policies/device/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.length).toBeGreaterThanOrEqual(3);
      expect(response.body.count).toBeGreaterThanOrEqual(3);
    });

    it('should return 404 for non-existent device', async () => {
      const response = await request(app)
        .get('/api/policies/device/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('Device not found');
    });

    it('should only return enabled policies', async () => {
      // Create disabled policy
      await policyService.createPolicy(testUserId, {
        name: 'Disabled Policy',
        policy_type: 'website',
        enabled: false,
        config: { blockedDomains: ['disabled.com'] },
      });

      const response = await request(app)
        .get(`/api/policies/device/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.data.every((p: any) => p.enabled === true)).toBe(true);
    });
  });
});
