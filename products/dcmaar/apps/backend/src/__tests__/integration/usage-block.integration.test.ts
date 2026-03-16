/**
 * Usage & Block Integration Tests
 *
 * Validates that usage/block events flow through to reporting endpoints.
 * Scenarios:
 * - Usage sessions surface in aggregated usage reports
 * - Block events surface in block reports with summaries
 * - Date and child filters restrict report results appropriately
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import * as deviceService from '../../services/device.service';
import { query } from '../../db';
import { createTestUser } from '../fixtures/user.fixtures';

let app: FastifyInstance;

const formatDate = (date: Date): string => date.toISOString().split('T')[0];

describe('Usage & Block Reports Integration', () => {
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
    const user = await createTestUser({ email: randomEmail() });
    testUserId = user.user.id;
    testAccessToken = user.accessToken;

    const child = await query(
      'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
      [testUserId, 'Integration Child', 9]
    );
    testChildId = child[0].id;

    const device = await deviceService.registerDevice(testUserId, {
      device_type: 'mobile',
      device_name: 'Child Tablet',
      device_fingerprint: randomString(16),
      child_id: testChildId,
    });
    testDeviceId = device.id;
  });

  it('surfaces usage sessions in usage reports', async () => {
    // Create a specific date/time in UTC to avoid timezone issues
    const testDate = new Date();
    testDate.setUTCHours(12, 0, 0, 0); // Noon UTC on the same day
    
    const endTime = new Date(testDate.getTime() + 30 * 60 * 1000); // 30 minutes later

    const res1 = await request(app)
      .post('/api/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Reading App',
        category: 'education',
        start_time: testDate.toISOString(),
        end_time: endTime.toISOString(),
        duration_seconds: 1800,
      })
      .expect(201);

    const res2 = await request(app)
      .post('/api/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        session_type: 'website',
        item_name: 'learning.com',
        category: 'education',
        start_time: testDate.toISOString(),
        end_time: endTime.toISOString(),
        duration_seconds: 900,
      })
      .expect(201);

    // Query for the date that contains our test data
    const queryDate = formatDate(testDate);

    const response = await request(app)
      .get('/api/reports/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: queryDate,
        end_date: queryDate,
      })
      .expect(200);

    expect(response.body.success).toBe(true);
    expect(response.body.data.length).toBeGreaterThanOrEqual(1);

    const childReport = response.body.data.find(
      (report: any) => report.child_id === testChildId
    );
    expect(childReport).toBeDefined();
    expect(childReport.total_screen_time).toBe(2700);
    expect(childReport.session_count).toBe(2);
    expect(childReport.top_apps[0].app_name).toBe('Reading App');
  });

  it('surfaces block events in block reports with aggregation', async () => {
    // Use UTC noon to avoid timezone issues
    const testDate = new Date();
    testDate.setUTCHours(14, 0, 0, 0); // 2 PM UTC

    // Multiple blocks for the same domain plus one additional entry
    for (let i = 0; i < 2; i++) {
      await request(app)
        .post('/api/blocks')
        .set('Authorization', `Bearer ${testAccessToken}`)
        .send({
          device_id: testDeviceId,
          event_type: 'website',
          blocked_item: 'social.com',
          category: 'social',
          timestamp: testDate.toISOString(),
        })
        .expect(201);
    }

    await request(app)
      .post('/api/blocks')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        event_type: 'app',
        blocked_item: 'com.games.app',
        category: 'games',
        timestamp: testDate.toISOString(),
      })
      .expect(201);

    const queryDate = formatDate(testDate);

    const response = await request(app)
      .get('/api/reports/blocks')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: queryDate,
        end_date: queryDate,
      })
      .expect(200);

    expect(response.body.success).toBe(true);
    const childReport = response.body.data.find(
      (report: any) => report.child_id === testChildId
    );
    expect(childReport).toBeDefined();
    expect(childReport.total_blocks).toBe(3);
    const topBlocked = childReport.top_blocked.find(
      (item: any) => item.target === 'social.com'
    );
    expect(topBlocked.count).toBe(2);
    expect(childReport.by_category.social).toBe(2);
    expect(childReport.by_category.games).toBe(1);
  });

  it('applies date and child filters across reports', async () => {
    // Use UTC times to avoid timezone issues
    const inRangeDate = new Date();
    inRangeDate.setUTCHours(12, 0, 0, 0); // Noon UTC today
    
    // Use 3 days ago for out-of-range data
    const oldDate = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
    oldDate.setUTCHours(12, 0, 0, 0); // Noon UTC 3 days ago

    // In-range usage and block events
    await request(app)
      .post('/api/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Coding App',
        category: 'education',
        start_time: inRangeDate.toISOString(),
        end_time: new Date(inRangeDate.getTime() + 600_000).toISOString(),
        duration_seconds: 600,
      })
      .expect(201);

    await request(app)
      .post('/api/blocks')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'inrange.com',
        category: 'productivity',
        timestamp: inRangeDate.toISOString(),
      })
      .expect(201);

    // Out-of-range data
    await request(app)
      .post('/api/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        session_type: 'website',
        item_name: 'oldsite.com',
        start_time: oldDate.toISOString(),
        end_time: new Date(oldDate.getTime() + 300_000).toISOString(),
        duration_seconds: 300,
      })
      .expect(201);

    await request(app)
      .post('/api/blocks')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: testDeviceId,
        event_type: 'website',
        blocked_item: 'oldblock.com',
        timestamp: oldDate.toISOString(),
      })
      .expect(201);

    // Additional child to validate filtering
    const otherChild = await query(
      'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
      [testUserId, 'Other Child', 7]
    );
    const otherDevice = await deviceService.registerDevice(testUserId, {
      device_type: 'mobile',
      device_name: 'Other Device',
      device_fingerprint: randomString(16),
      child_id: otherChild[0].id,
    });
    await request(app)
      .post('/api/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_id: otherDevice.id,
        session_type: 'app',
        item_name: 'Other Child App',
        start_time: inRangeDate.toISOString(),
        end_time: new Date(inRangeDate.getTime() + 300_000).toISOString(),
        duration_seconds: 300,
      })
      .expect(201);

    // Query for today only (to include in-range data, exclude old data)
    const queryDate = formatDate(inRangeDate);

    const usageResponse = await request(app)
      .get('/api/reports/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: queryDate,
        end_date: queryDate,
        child_id: testChildId,
      })
      .expect(200);

    const usageReport = usageResponse.body.data[0];
    expect(usageReport.total_screen_time).toBe(600);
    expect(usageReport.session_count).toBe(1);
    expect(
      usageReport.top_apps.some((app: any) => app.app_name === 'oldsite.com')
    ).toBe(false);

    const blockResponse = await request(app)
      .get('/api/reports/blocks')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: queryDate,
        end_date: queryDate,
        child_id: testChildId,
      })
      .expect(200);

    const blockReport = blockResponse.body.data[0];
    expect(blockReport.total_blocks).toBe(1);
    expect(
      blockReport.top_blocked.some((item: any) => item.target === 'oldblock.com')
    ).toBe(false);
  });
});

