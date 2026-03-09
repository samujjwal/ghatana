/**
 * Usage → Analytics Integration Tests
 *
 * Validates that usage data flows through to analytics aggregation correctly.
 * Scenarios:
 * - Device reports usage data
 * - Usage data is aggregated correctly
 * - Analytics endpoints return proper aggregations
 * - Time-series data is queryable
 * - Category breakdowns are accurate
 *
 * @doc.type test-suite
 * @doc.purpose Integration tests for usage tracking and analytics aggregation
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

// Analytics aggregation API tests
describe('Usage → Analytics Integration', () => {
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
    // Setup user, child, and device
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

    const childResponse = await request(app)
      .post('/api/children')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        name: 'Test Child',
        birth_date: new Date(Date.now() - 12 * 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      })
      .expect(201);

    testChildId = childResponse.body.data.id;

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
  });

  /**
   * Verifies single usage session appears in analytics.
   *
   * GIVEN: Device reports single usage session
   * WHEN: Analytics endpoint queried
   * THEN: Usage session appears in aggregated data
   */
  it('should aggregate single usage session', async () => {
    const startTime = new Date();
    startTime.setUTCHours(10, 0, 0, 0);
    const endTime = new Date(startTime.getTime() + 900000); // 15 minutes

    // Report usage
    await request(app)
      .post('/api/usage')
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Reading App',
        category: 'education',
        start_time: startTime.toISOString(),
        end_time: endTime.toISOString(),
        duration_seconds: 900,
      })
      .expect(201);

    // Query analytics
    const today = startTime.toISOString().split('T')[0];
    const analyticsResponse = await request(app)
      .get('/api/analytics')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: today,
        end_date: today,
      })
      .expect(200);

    expect(analyticsResponse.body.success).toBe(true);
    expect(analyticsResponse.body.data.total_screen_time).toBeGreaterThanOrEqual(900);
    expect(analyticsResponse.body.data.total_sessions).toBeGreaterThanOrEqual(1);
  });

  /**
   * Verifies multiple usage sessions are aggregated correctly.
   *
   * GIVEN: Device reports multiple usage sessions
   * WHEN: Analytics endpoint queried
   * THEN: Total screen time and session count are correct
   */
  it('should aggregate multiple usage sessions', async () => {
    const baseTime = new Date();
    baseTime.setUTCHours(9, 0, 0, 0);

    // Report multiple usage sessions
    const sessions = [
      { name: 'Math App', category: 'education', duration: 1200 }, // 20 min
      { name: 'Science App', category: 'education', duration: 1800 }, // 30 min
      { name: 'Game App', category: 'games', duration: 600 }, // 10 min
    ];

    for (let i = 0; i < sessions.length; i++) {
      const session = sessions[i];
      const startTime = new Date(baseTime.getTime() + i * 3600000); // 1 hour apart
      const endTime = new Date(startTime.getTime() + session.duration * 1000);

      await request(app)
        .post('/api/usage')
        .send({
          device_id: testDeviceId,
          session_type: 'app',
          item_name: session.name,
          category: session.category,
          start_time: startTime.toISOString(),
          end_time: endTime.toISOString(),
          duration_seconds: session.duration,
        })
        .expect(201);
    }

    // Query analytics
    const today = baseTime.toISOString().split('T')[0];
    const analyticsResponse = await request(app)
      .get('/api/analytics')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: today,
        end_date: today,
      })
      .expect(200);

    expect(analyticsResponse.body.success).toBe(true);
    expect(analyticsResponse.body.data.total_screen_time).toBeGreaterThanOrEqual(3600); // 60 minutes
    expect(analyticsResponse.body.data.total_sessions).toBeGreaterThanOrEqual(3);
  });

  /**
   * Verifies category breakdown in analytics.
   *
   * GIVEN: Usage sessions in different categories
   * WHEN: Analytics endpoint queried
   * THEN: Category breakdown shows correct distribution
   */
  it('should provide category breakdown in analytics', async () => {
    const baseTime = new Date();
    baseTime.setUTCHours(13, 0, 0, 0);

    // Report usage in different categories
    const categories = [
      { name: 'education', duration: 2400 }, // 40 min
      { name: 'games', duration: 1200 }, // 20 min
      { name: 'social', duration: 600 }, // 10 min
    ];

    for (const cat of categories) {
      const startTime = new Date(baseTime);
      const endTime = new Date(startTime.getTime() + cat.duration * 1000);

      await request(app)
        .post('/api/usage')
        .send({
          device_id: testDeviceId,
          session_type: 'app',
          item_name: `${cat.name} app`,
          category: cat.name,
          start_time: startTime.toISOString(),
          end_time: endTime.toISOString(),
          duration_seconds: cat.duration,
        })
        .expect(201);
    }

    // Query analytics with category breakdown
    const today = baseTime.toISOString().split('T')[0];
    const analyticsResponse = await request(app)
      .get('/api/analytics')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: today,
        end_date: today,
      })
      .expect(200);

    expect(analyticsResponse.body.success).toBe(true);
    expect(analyticsResponse.body.data.by_category).toBeDefined();
    
    // Verify education has the most time
    const byCategory = analyticsResponse.body.data.by_category;
    if (byCategory) {
      expect(byCategory.education).toBeGreaterThanOrEqual(2400);
    }
  });

  /**
   * Verifies top apps/websites appear in analytics.
   *
   * GIVEN: Usage across multiple apps
   * WHEN: Analytics endpoint queried
   * THEN: Top apps listed with correct durations
   */
  it('should identify top apps and websites', async () => {
    const baseTime = new Date();
    baseTime.setUTCHours(15, 0, 0, 0);

    // Report usage for various apps
    const appsList = [
      { name: 'Popular App', duration: 3000 }, // 50 min - should be top
      { name: 'Medium App', duration: 1800 }, // 30 min
      { name: 'Low Use App', duration: 600 }, // 10 min
    ];

    for (const appItem of appsList) {
      const startTime = new Date(baseTime);
      const endTime = new Date(startTime.getTime() + appItem.duration * 1000);

      await request(app)
        .post('/api/usage')
        .send({
          device_id: testDeviceId,
          session_type: 'app',
          item_name: appItem.name,
          category: 'productivity',
          start_time: startTime.toISOString(),
          end_time: endTime.toISOString(),
          duration_seconds: appItem.duration,
        })
        .expect(201);
    }

    // Query usage report which includes top apps
    const today = baseTime.toISOString().split('T')[0];
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
    const report = usageReportResponse.body.data[0];
    expect(report.top_apps).toBeDefined();
    expect(report.top_apps.length).toBeGreaterThan(0);

    // Top app should be "Popular App"
    const topApp = report.top_apps[0];
    expect(topApp.app_name).toBe('Popular App');
    expect(topApp.duration).toBeGreaterThanOrEqual(3000);
  });

  /**
   * Verifies time-series data is available for trending.
   *
   * GIVEN: Usage data over multiple days
   * WHEN: Analytics endpoint queried with date range
   * THEN: Daily breakdowns available
   */
  it('should provide time-series data for trending', async () => {
    // Create usage data for "today" and "yesterday"
    const today = new Date();
    today.setUTCHours(12, 0, 0, 0);
    
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    // Today's usage
    await request(app)
      .post('/api/usage')
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Today App',
        category: 'education',
        start_time: today.toISOString(),
        end_time: new Date(today.getTime() + 1800000).toISOString(),
        duration_seconds: 1800,
      })
      .expect(201);

    // Yesterday's usage
    await request(app)
      .post('/api/usage')
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Yesterday App',
        category: 'education',
        start_time: yesterday.toISOString(),
        end_time: new Date(yesterday.getTime() + 1200000).toISOString(),
        duration_seconds: 1200,
      })
      .expect(201);

    // Query analytics for both days
    const startDate = yesterday.toISOString().split('T')[0];
    const endDate = today.toISOString().split('T')[0];

    const analyticsResponse = await request(app)
      .get('/api/analytics')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: startDate,
        end_date: endDate,
      })
      .expect(200);

    expect(analyticsResponse.body.success).toBe(true);
    expect(analyticsResponse.body.data.total_screen_time).toBeGreaterThanOrEqual(3000);
    expect(analyticsResponse.body.data.total_sessions).toBeGreaterThanOrEqual(2);
  });

  /**
   * Verifies child-specific analytics filtering.
   *
   * GIVEN: Multiple children with usage data
   * WHEN: Analytics queried for specific child
   * THEN: Only that child's data returned
   */
  it('should filter analytics by child', async () => {
    // Create second child
    const child2Response = await request(app)
      .post('/api/children')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        name: 'Second Child',
        birth_date: new Date(Date.now() - 8 * 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      })
      .expect(201);

    const child2Id = child2Response.body.data.id;

    // Create device for second child
    const device2Response = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .send({
        device_type: 'mobile',
        device_name: 'Second Device',
        device_fingerprint: randomString(16),
        child_id: child2Id,
      })
      .expect(201);

    const device2Id = device2Response.body.data.id;

    const baseTime = new Date();
    baseTime.setUTCHours(14, 0, 0, 0);

    // Usage for first child
    await request(app)
      .post('/api/usage')
      .send({
        device_id: testDeviceId,
        session_type: 'app',
        item_name: 'Child 1 App',
        category: 'education',
        start_time: baseTime.toISOString(),
        end_time: new Date(baseTime.getTime() + 1200000).toISOString(),
        duration_seconds: 1200,
      })
      .expect(201);

    // Usage for second child
    await request(app)
      .post('/api/usage')
      .send({
        device_id: device2Id,
        session_type: 'app',
        item_name: 'Child 2 App',
        category: 'games',
        start_time: baseTime.toISOString(),
        end_time: new Date(baseTime.getTime() + 2400000).toISOString(),
        duration_seconds: 2400,
      })
      .expect(201);

    // Query analytics for first child only
    const today = baseTime.toISOString().split('T')[0];
    const child1AnalyticsResponse = await request(app)
      .get('/api/reports/usage')
      .set('Authorization', `Bearer ${testAccessToken}`)
      .query({
        start_date: today,
        end_date: today,
        child_id: testChildId,
      })
      .expect(200);

    const child1Report = child1AnalyticsResponse.body.data.find(
      (r: any) => r.child_id === testChildId
    );
    expect(child1Report).toBeDefined();
    expect(child1Report.total_screen_time).toBe(1200);

    // Verify second child's data is not included
    const hasChild2Data = child1AnalyticsResponse.body.data.some(
      (r: any) => r.child_id === child2Id
    );
    expect(hasChild2Data).toBe(false);
  });
});
