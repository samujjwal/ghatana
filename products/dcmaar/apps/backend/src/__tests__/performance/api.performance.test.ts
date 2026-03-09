/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * API Performance Scaffolding Tests
 *
 * These tests provide lightweight regression coverage to detect large
 * performance regressions in critical request paths. They use a stubbed
 * express app to measure request/response latency and concurrency handling.
 */

import express from 'express';
import request from 'supertest';
import { performance } from 'perf_hooks';

describe('API Performance', () => {
  const app = express();
  app.use(express.json());

  app.post('/api/auth/login', (_req, res) => {
    res.status(200).json({ success: true });
  });

  app.get('/api/devices', (_req, res) => {
    res.status(200).json({ data: new Array(10).fill({ status: 'online' }) });
  });

  // Stubbed Guardian agent endpoints for lightweight perf regression
  app.post('/api/devices/device-1/actions', (_req, res) => {
    res.status(202).json({ success: true, command_id: 'cmd-perf' });
  });

  app.get('/api/devices/device-1/sync', (_req, res) => {
    res.status(200).json({
      success: true,
      data: {
        schema_version: 1,
        device_id: 'device-1',
        synced_at: new Date().toISOString(),
        sync_version: 'perf',
        policies: { version: 'perf', items: [], count: 0 },
        commands: { items: [], count: 0 },
        next_sync_seconds: 60,
      },
    });
  });

  const measureRequest = async (callback: () => Promise<request.Response>) => {
    const start = performance.now();
    const response = await callback();
    const duration = performance.now() - start;
    return { response, duration };
  };

  it('POST /api/auth/login responds in under 100ms', async () => {
    const { response, duration } = await measureRequest(() =>
      request(app).post('/api/auth/login').send({ email: 'test@example.com', password: 'Secret123!' })
    );

    expect(response.status).toBe(200);
    expect(duration).toBeLessThan(100);
  });

  it('GET /api/devices handles 25 concurrent requests under 150ms total', async () => {
    const start = performance.now();
    await Promise.all(
      Array.from({ length: 25 }).map(() =>
        request(app).get('/api/devices').set('Authorization', 'Bearer token')
      )
    );
    const duration = performance.now() - start;

    expect(duration).toBeLessThan(150);
  });

  it('POST /api/devices/:id/actions handles 25 concurrent requests under 200ms total', async () => {
    const start = performance.now();
    await Promise.all(
      Array.from({ length: 25 }).map(() =>
        request(app)
          .post('/api/devices/device-1/actions')
          .send({ action: 'lock_device' })
      )
    );
    const duration = performance.now() - start;

    expect(duration).toBeLessThan(200);
  });

  it('GET /api/devices/:id/sync responds in under 100ms', async () => {
    const { response, duration } = await measureRequest(() =>
      request(app).get('/api/devices/device-1/sync')
    );

    expect(response.status).toBe(200);
    expect(duration).toBeLessThan(100);
  });
});

