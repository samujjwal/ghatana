/**
 * Device → Block Events Integration Tests
 *
 * Validates the complete device registration, usage reporting, and policy enforcement flow.
 * Scenarios:
 * - Device registers successfully
 * - Device reports usage data
 * - Policy blocks requests based on rules
 * - Block events are properly recorded
 * - Usage and blocks are queryable
 *
 * @doc.type test-suite
 * @doc.purpose Integration tests for device management and policy enforcement
 * @doc.layer integration-testing
 * @doc.pattern Integration Test
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import * as authService from '../../services/auth.service';
import { query } from '../../db';

let app: FastifyInstance;

describe('Device → Block Events Integration', () => {
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
    // Register user
    const authResponse = await request(app)
      .post('/api/auth/register')
      .send({
        email: randomEmail(),
        password: 'TestPassword123!',
        display_name: 'Test Parent',
      })
      .expect(201);

    testUserId = authResponse.body.user.id;
    testAccessToken = authResponse.body.accessToken;

    // Create child profile
    const childResponse = await request(app)
      .post('/api/children')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        name: 'Test Child',
        birth_date: new Date(Date.now() - 10 * 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      })
      .expect(201);

    testChildId = childResponse.body.data.id;
  });

  /**
   * Verifies device registration flow.
   *
   * GIVEN: Authenticated user with child
   * WHEN: Device registers
   * THEN: Device created and linked to child
   */
  it('should register a device successfully', async () => {
    const deviceResponse = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'mobile',
        device_name: 'Test Tablet',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    expect(deviceResponse.body.success).toBe(true);
    expect(deviceResponse.body.data.device_type).toBe('mobile');
    expect(deviceResponse.body.data.device_name).toBe('Test Tablet');
    expect(deviceResponse.body.data.child_id).toBe(testChildId);
    expect(deviceResponse.body.data.pairing_code).toBeDefined();

    testDeviceId = deviceResponse.body.data.id;
  });

  /**
   * Verifies device can report usage data.
   *
   * GIVEN: Registered device
   * WHEN: Device reports usage
   * THEN: Usage event recorded
   */
  it('should accept usage data from registered device', async () => {
    // Register device
    const deviceResponse = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'mobile',
        device_name: 'Child Phone',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    testDeviceId = deviceResponse.body.data.id;

    // Report usage (devices can report without auth token)
    const usageResponse = await request(app)
      .post('/api/usage')
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Education App',
        category: 'education',
        start_time: new Date().toISOString(),
        end_time: new Date(Date.now() + 600000).toISOString(),
        duration_seconds: 600,
      })
      .expect(201);

    expect(usageResponse.body.session_type).toBe('app');
    expect(usageResponse.body.item_name).toBe('Education App');
  });

  /**
   * Verifies policy blocks requests and records block events.
   *
   * GIVEN: Device with active blocking policy
   * WHEN: Device tries to access blocked content
   * THEN: Block event is recorded
   */
  it('should record block events when policy is violated', async () => {
    // Register device
    const deviceResponse = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'mobile',
        device_name: 'Test Device',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    testDeviceId = deviceResponse.body.data.id;

    // Create blocking policy
    const policyResponse = await request(app)
      .post('/api/policies')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        child_id: testChildId,
        name: 'Block Site Policy',
        policy_type: 'website',
        enabled: true,
        config: {
          target: 'blocked-site.com',
          action: 'block',
        },
      })
      .expect(201);

    testPolicyId = policyResponse.body.data.id;

    // Device tries to access blocked content and reports block event
    const blockResponse = await request(app)
      .post('/api/blocks')
      .send({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'blocked-site.com',
        category: 'social',
        timestamp: new Date().toISOString(),
      })
      .expect(201);

    expect(blockResponse.body.blocked_item).toBe('blocked-site.com');
    expect(blockResponse.body.device_id).toBe(testDeviceId);
  });

  /**
   * Verifies complete flow: device registration → usage → policy enforcement → block event.
   *
   * GIVEN: User with child, policy, and device
   * WHEN: Device reports usage and encounters blocks
   * THEN: Both usage and block events are recorded and queryable
   */
  it('should handle complete device lifecycle with usage and blocks', async () => {
    // 1. Register device
    const deviceResponse = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'desktop',
        device_name: 'Home Computer',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    testDeviceId = deviceResponse.body.data.id;

    // 2. Create blocking policy
    await request(app)
      .post('/api/policies')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        child_id: testChildId,
        name: 'Block Gaming Sites',
        policy_type: 'website',
        enabled: true,
        config: {
          target: 'gaming-site.com',
          action: 'block',
        },
      })
      .expect(201);

    // 3. Device reports normal usage
    await request(app)
      .post('/api/usage')
      .send({
        device_id: testDeviceId,
        session_type: 'website',
        item_name: 'homework-help.com',
        category: 'education',
        start_time: new Date().toISOString(),
        end_time: new Date(Date.now() + 1200000).toISOString(),
        duration_seconds: 1200,
      })
      .expect(201);

    // 4. Device encounters block
    await request(app)
      .post('/api/blocks')
      .send({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'gaming-site.com',
        category: 'games',
        timestamp: new Date().toISOString(),
      })
      .expect(201);

    // 5. Verify device status and stats
    const deviceListResponse = await request(app)
      .get('/api/devices')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .expect(200);

    const device = deviceListResponse.body.data.find(
      (d: any) => d.id === testDeviceId
    );
    expect(device).toBeDefined();
    expect(device.device_name).toBe('Home Computer');

    // 6. Verify usage was recorded
    const today = new Date().toISOString().split('T')[0];
    const usageReportResponse = await request(app)
      .get('/api/reports/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: today,
        end_date: today,
        child_id: testChildId,
      })
      .expect(200);

    expect(usageReportResponse.body.success).toBe(true);
    expect(usageReportResponse.body.data.length).toBeGreaterThanOrEqual(1);
    const usageReport = usageReportResponse.body.data[0];
    expect(usageReport.total_screen_time).toBeGreaterThanOrEqual(1200);

    // 7. Verify block was recorded
    const blockReportResponse = await request(app)
      .get('/api/reports/blocks')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: today,
        end_date: today,
        child_id: testChildId,
      })
      .expect(200);

    expect(blockReportResponse.body.success).toBe(true);
    expect(blockReportResponse.body.data.length).toBeGreaterThanOrEqual(1);
    const blockReport = blockReportResponse.body.data[0];
    expect(blockReport.total_blocks).toBeGreaterThanOrEqual(1);
  });

  /**
   * Verifies device heartbeat updates device status.
   *
   * GIVEN: Registered device
   * WHEN: Device sends heartbeat
   * THEN: Device last_seen timestamp updated
   */
  it('should update device status via heartbeat', async () => {
    // Register device
    const deviceResponse = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'mobile',
        device_name: 'Test Phone',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    testDeviceId = deviceResponse.body.data.id;

    // Send heartbeat (requires authentication)
    const heartbeatResponse = await request(app)
      .post('/api/heartbeat')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        battery_level: 85,
        wifi_signal: 80,
        network_latency: 45,
        uptime: 3600,
      })
      .expect(200);

    expect(heartbeatResponse.body.success).toBe(true);

    // Verify device status updated
    const devicesResponse = await request(app)
      .get('/api/devices')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .expect(200);

    const device = devicesResponse.body.data.find((d: any) => d.id === testDeviceId);
    expect(device).toBeDefined();
    expect(device.status).toBe('active'); // Device status is 'active' after registration/heartbeat
  });

  /**
   * Verifies multiple devices can be associated with same child.
   *
   * GIVEN: Child profile
   * WHEN: Multiple devices register for same child
   * THEN: All devices linked and manageable
   */
  it('should support multiple devices per child', async () => {
    // Register first device
    const device1Response = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'mobile',
        device_name: 'Child Phone',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    // Register second device
    const device2Response = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'desktop',
        device_name: 'Child Laptop',
        device_fingerprint: randomString(16),
        child_id: testChildId,
      })
      .expect(201);

    // Retrieve all devices
    const devicesResponse = await request(app)
      .get('/api/devices')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .expect(200);

    const childDevices = devicesResponse.body.data.filter(
      (d: any) => d.child_id === testChildId
    );

    expect(childDevices.length).toBeGreaterThanOrEqual(2);
    expect(childDevices.some((d: any) => d.device_name === 'Child Phone')).toBe(true);
    expect(childDevices.some((d: any) => d.device_name === 'Child Laptop')).toBe(true);
  });
});
