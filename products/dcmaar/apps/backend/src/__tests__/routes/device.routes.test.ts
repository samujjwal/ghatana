/**
 * Device Routes Tests
 *
 * Tests device API endpoints including:
 * - POST /api/devices/register
 * - GET /api/devices
 * - GET /api/devices/:id
 * - PUT /api/devices/:id
 * - DELETE /api/devices/:id
 * - POST /api/devices/:id/pair
 * - POST /api/devices/:id/unpair
 * - POST /api/devices/pairing/generate
 * - POST /api/devices/:id/pair-with-code
 * - GET /api/devices/pairing/:childId
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import * as authService from '../../services/auth.service';
import * as deviceService from '../../services/device.service';
import { query } from '../../db';

let app: FastifyInstance;

describe('Device Routes', () => {
  let testUserId: string;
  let testAccessToken: string;
  let testChildId: string;

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
      displayName: 'Test User',
    });
    testUserId = user.user.id;
    testAccessToken = user.accessToken;

    // Create test child
    const child = await query(
      `INSERT INTO children (user_id, name, age, avatar_url)
       VALUES ($1, $2, $3, $4) RETURNING *`,
      [testUserId, 'Test Child', 10, null]
    );
    testChildId = child[0].id;
  });

  describe('POST /api/devices/register', () => {
    it('should register a mobile device', async () => {
      const deviceData = {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      };

      const response = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(deviceData)
        .expect(201);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.device_type).toBe('mobile');
      expect(response.body.data.device_name).toBe('iPhone 13');
      expect(response.body.data.user_id).toBe(testUserId);
    });

    it('should register a desktop device', async () => {
      const deviceData = {
        device_type: 'desktop',
        device_name: 'MacBook Pro',
        device_fingerprint: randomString(16),
      };

      const response = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(deviceData)
        .expect(201);

      expect(response.body.data.device_type).toBe('desktop');
    });

    it('should register a browser extension', async () => {
      const deviceData = {
        device_type: 'extension',
        device_name: 'Chrome Extension',
        device_fingerprint: randomString(16),
      };

      const response = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send(deviceData)
        .expect(201);

      expect(response.body.data.device_type).toBe('extension');
    });

    it('should reject missing required fields', async () => {
      const response = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_type: 'mobile',
          // Missing device_name
        })
        .expect(400);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toBeDefined();
    });

    it('should reject invalid device type', async () => {
      const response = await request(app)
        .post('/api/devices/register')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_type: 'invalid-type',
          device_name: 'Test Device',
        })
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject unauthenticated request', async () => {
      const response = await request(app)
        .post('/api/devices/register')
        .send({
          device_type: 'mobile',
          device_name: 'Test Device',
        })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('GET /api/devices', () => {
    beforeEach(async () => {
      // Register multiple devices
      await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      await deviceService.registerDevice(testUserId, {
        device_type: 'desktop',
        device_name: 'MacBook Pro',
        device_fingerprint: randomString(16),
      });
    });

    it('should list all user devices', async () => {
      const response = await request(app)
        .get('/api/devices')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.length).toBeGreaterThanOrEqual(2);
      expect(response.body.count).toBeGreaterThanOrEqual(2);
    });

    it('should filter devices by type', async () => {
      const response = await request(app)
        .get('/api/devices?device_type=mobile')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.every((d: any) => d.device_type === 'mobile')).toBe(true);
    });

    it('should filter devices by child', async () => {
      // Pair one device with child
      const devices = await deviceService.getDevices(testUserId);
      await deviceService.pairDeviceWithChild(testUserId, devices[0].id, testChildId);

      const response = await request(app)
        .get(`/api/devices?child_id=${testChildId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.every((d: any) => d.child_id === testChildId)).toBe(true);
    });

    it('should filter active devices', async () => {
      const response = await request(app)
        .get('/api/devices?is_active=true')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.every((d: any) => d.is_active === true)).toBe(true);
    });

    it('should reject unauthenticated request', async () => {
      const response = await request(app)
        .get('/api/devices')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('GET /api/devices/:id', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
    });

    it('should get device by ID', async () => {
      const response = await request(app)
        .get(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.id).toBe(testDeviceId);
      expect(response.body.data.device_name).toBe('iPhone 13');
    });

    it('should reject access to another user device', async () => {
      // Create another user and their device
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });
      const otherDevice = await deviceService.registerDevice(otherUser.user.id, {
        device_type: 'mobile',
        device_name: 'Other Device',
        device_fingerprint: randomString(16),
      });

      const response = await request(app)
        .get(`/api/devices/${otherDevice.id}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('not found');
    });

    it('should return 404 for non-existent device', async () => {
      const response = await request(app)
        .get('/api/devices/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('PUT /api/devices/:id', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
    });

    it('should update device name', async () => {
      const response = await request(app)
        .put(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ device_name: 'Updated iPhone' })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.device_name).toBe('Updated iPhone');
    });

    it('should pair device with child', async () => {
      const response = await request(app)
        .put(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: testChildId })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.child_id).toBe(testChildId);
    });

    it('should update device active status', async () => {
      const response = await request(app)
        .put(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ is_active: false })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.is_active).toBe(false);
    });

    it('should update multiple fields', async () => {
      const response = await request(app)
        .put(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_name: 'New Name',
          child_id: testChildId,
          is_active: false,
        })
        .expect(200);

      expect(response.body.data.device_name).toBe('New Name');
      expect(response.body.data.child_id).toBe(testChildId);
      expect(response.body.data.is_active).toBe(false);
    });

    it('should reject empty updates', async () => {
      const response = await request(app)
        .put(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({})
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject access to another user device', async () => {
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });
      const otherDevice = await deviceService.registerDevice(otherUser.user.id, {
        device_type: 'mobile',
        device_name: 'Other Device',
        device_fingerprint: randomString(16),
      });

      const response = await request(app)
        .put(`/api/devices/${otherDevice.id}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ device_name: 'Hacked Name' })
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('DELETE /api/devices/:id', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
    });

    it('should soft delete device', async () => {
      const response = await request(app)
        .delete(`/api/devices/${testDeviceId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.message).toContain('success');

      // Verify device is soft deleted
      const devices = await deviceService.getDevices(testUserId);
      expect(devices.find(d => d.id === testDeviceId)).toBeUndefined();
    });

    it('should reject access to another user device', async () => {
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });
      const otherDevice = await deviceService.registerDevice(otherUser.user.id, {
        device_type: 'mobile',
        device_name: 'Other Device',
        device_fingerprint: randomString(16),
      });

      const response = await request(app)
        .delete(`/api/devices/${otherDevice.id}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
    });

    it('should return 404 for non-existent device', async () => {
      const response = await request(app)
        .delete('/api/devices/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('POST /api/devices/:id/pair', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
    });

    it('should pair device with child', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: testChildId })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.child_id).toBe(testChildId);
      expect(response.body.message).toContain('success');
    });

    it('should reject missing child ID', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({})
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject non-existent child', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: '00000000-0000-0000-0000-000000000000' })
        .expect(404);

      expect(response.body.success).toBe(false);
    });

    it('should reject pairing another user child', async () => {
      // Create another user and their child
      const otherUser = await authService.register({
        email: randomEmail(),
        password: 'OtherPassword123!',
      });
      const otherChild = await query(
        `INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *`,
        [otherUser.user.id, 'Other Child', 8]
      );

      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: otherChild[0].id })
        .expect(404);

      expect(response.body.success).toBe(false);
    });
  });

  describe('POST /api/devices/:id/unpair', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
      // Pair device with child
      await deviceService.pairDeviceWithChild(testUserId, testDeviceId, testChildId);
    });

    it('should unpair device from child', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/unpair`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.child_id).toBeNull();
      expect(response.body.message).toContain('success');
    });

    it('should succeed even if device not paired', async () => {
      // Unpair first
      await deviceService.unpairDevice(testUserId, testDeviceId);

      // Try to unpair again
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/unpair`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.child_id).toBeNull();
    });
  });

  describe('POST /api/devices/pairing/generate', () => {
    it('should generate pairing code', async () => {
      const response = await request(app)
        .post('/api/devices/pairing/generate')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: testChildId })
        .expect(201);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.code).toBeDefined();
      expect(response.body.data.code.length).toBe(6);
      expect(response.body.data.expiresAt).toBeDefined();
    });

    it('should reject missing child ID', async () => {
      const response = await request(app)
        .post('/api/devices/pairing/generate')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({})
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should replace existing active code', async () => {
      // Generate first code
      const response1 = await request(app)
        .post('/api/devices/pairing/generate')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: testChildId })
        .expect(201);

      const firstCode = response1.body.data.code;

      // Generate second code
      const response2 = await request(app)
        .post('/api/devices/pairing/generate')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ child_id: testChildId })
        .expect(201);

      const secondCode = response2.body.data.code;

      expect(firstCode).not.toBe(secondCode);

      // First code should be invalidated
      const activeCode = await deviceService.getActivePairingCode(testUserId, testChildId);
      expect(activeCode?.code).toBe(secondCode);
    });
  });

  describe('POST /api/devices/:id/pair-with-code', () => {
    let testDeviceId: string;
    let pairingCode: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'iPhone 13',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;

      const code = await deviceService.generateDevicePairingCode(testUserId, testChildId);
      pairingCode = code.code;
    });

    it('should verify and pair device with valid code', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair-with-code`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          pairing_code: pairingCode,
        })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data.child_id).toBe(testChildId);
    });

    it('should reject invalid pairing code', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair-with-code`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          pairing_code: '999999',
        })
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject missing pairing code', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair-with-code`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({})
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('should reject expired pairing code', async () => {
      // Manually expire the code in database
      await query(
        `UPDATE device_pairing_requests
         SET expires_at = NOW() - INTERVAL '1 hour'
         WHERE pairing_code = $1`,
        [pairingCode]
      );

      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/pair-with-code`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          pairing_code: pairingCode,
        })
        .expect(400);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('expired');
    });
  });

  describe('GET /api/devices/pairing/:childId', () => {
    it('should get active pairing code for child', async () => {
      // Generate pairing code
      await deviceService.generateDevicePairingCode(testUserId, testChildId);

      const response = await request(app)
        .get(`/api/devices/pairing/${testChildId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.code).toBeDefined();
      expect(response.body.data.code.length).toBe(6);
    });

    it('should return 404 when no active code', async () => {
      const response = await request(app)
        .get(`/api/devices/pairing/${testChildId}`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('No active pairing code');
    });
  });

  describe('Device actions and commands', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'Command Test Device',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
    });

    it('should enqueue a lock_device command and expose it via /commands', async () => {
      const actionResponse = await request(app)
        .post(`/api/devices/${testDeviceId}/actions`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          action: 'lock_device',
          params: { reason: 'test' },
        })
        .expect(202);

      expect(actionResponse.body.success).toBe(true);
      expect(actionResponse.body.command_id).toBeDefined();

      const commandId = actionResponse.body.command_id as string;

      const commandsResponse = await request(app)
        .get(`/api/devices/${testDeviceId}/commands`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(commandsResponse.body.success).toBe(true);
      expect(Array.isArray(commandsResponse.body.data)).toBe(true);

      const commands = commandsResponse.body.data as any[];
      const command = commands.find((c) => c.command_id === commandId);

      expect(command).toBeDefined();
      expect(command.kind).toBe('immediate_action');
      expect(command.action).toBe('lock_device');
      expect(command.target.device_id).toBe(testDeviceId);
      expect(command.issued_by.actor_type).toBe('parent');
      expect(command.issued_by.user_id).toBe(testUserId);
    });

    it('should acknowledge a device command', async () => {
      const actionResponse = await request(app)
        .post(`/api/devices/${testDeviceId}/actions`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          action: 'lock_device',
        })
        .expect(202);

      const commandId = actionResponse.body.command_id as string;

      const ackResponse = await request(app)
        .post(`/api/devices/${testDeviceId}/commands/ack`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          command_id: commandId,
          status: 'processed',
        })
        .expect(200);

      expect(ackResponse.body.success).toBe(true);
      expect(ackResponse.body.message).toContain('Command acknowledgement received');
    });

    it('should support new immediate actions (unlock_device, sound_alarm)', async () => {
      // Test unlock_device
      const unlockResponse = await request(app)
        .post(`/api/devices/${testDeviceId}/actions`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          action: 'unlock_device',
          params: { reason: 'test unlock' },
        })
        .expect(202);

      expect(unlockResponse.body.success).toBe(true);
      expect(unlockResponse.body.command_id).toBeDefined();

      // Test sound_alarm
      const alarmResponse = await request(app)
        .post(`/api/devices/${testDeviceId}/actions`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          action: 'sound_alarm',
          params: { duration_seconds: 10 },
        })
        .expect(202);

      expect(alarmResponse.body.success).toBe(true);
      expect(alarmResponse.body.command_id).toBeDefined();
    });

    it('should reject non-immediate actions via /actions endpoint', async () => {
      const response = await request(app)
        .post(`/api/devices/${testDeviceId}/actions`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          action: 'extend_session',
          params: { minutes_granted: 30 },
        })
        .expect(400);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('immediate actions');
      expect(response.body.supported_actions).toBeDefined();
    });
  });

  describe('Agent sync endpoint', () => {
    let testDeviceId: string;

    beforeEach(async () => {
      const device = await deviceService.registerDevice(testUserId, {
        device_type: 'mobile',
        device_name: 'Sync Test Device',
        device_fingerprint: randomString(16),
      });
      testDeviceId = device.id;
    });

    it('should return unified sync payload', async () => {
      const response = await request(app)
        .get(`/api/devices/${testDeviceId}/sync`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toBeDefined();
      expect(response.body.data.schema_version).toBe(1);
      expect(response.body.data.device_id).toBe(testDeviceId);
      expect(response.body.data.synced_at).toBeDefined();
      expect(response.body.data.sync_version).toBeDefined();
      expect(response.body.data.policies).toBeDefined();
      expect(response.body.data.commands).toBeDefined();
      expect(response.body.data.next_sync_seconds).toBeGreaterThan(0);
    });

    it('should include pending commands in sync', async () => {
      // Enqueue a command first
      await request(app)
        .post(`/api/devices/${testDeviceId}/actions`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({ action: 'lock_device' })
        .expect(202);

      const response = await request(app)
        .get(`/api/devices/${testDeviceId}/sync`)
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(200);

      expect(response.body.data.commands.count).toBe(1);
      expect(response.body.data.commands.items[0].action).toBe('lock_device');
      // Should recommend shorter sync interval with pending commands
      expect(response.body.data.next_sync_seconds).toBe(30);
    });

    it('should return 404 for non-existent device', async () => {
      const response = await request(app)
        .get('/api/devices/00000000-0000-0000-0000-000000000000/sync')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .expect(404);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('Device not found');
    });
  });
});
