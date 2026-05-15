/**
 * Tests for Kernel Provider Endpoints
 *
 * Validates that:
 * - Provider endpoints require authentication and scope validation
 * - Request bodies are validated per provider schema
 * - Returns strict provider result with success/error and reason codes
 * - List endpoints support filtering and pagination
 * - Latest endpoints return most recent records
 * - Retention/redaction hooks work correctly
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../app.js';
import type { FastifyInstance } from 'fastify';
import { signJwt } from './jwt.js';

const JWT_SECRET = 'test-secret';
const TEST_TENANT_ID = 'test-tenant';
const TEST_WORKSPACE_ID = 'test-workspace';
const TEST_PROJECT_ID = 'test-project';
const TEST_USER_ID = 'test-user';

function createTestToken(overrides: Record<string, unknown> = {}): string {
  return signJwt(
    {
      tenantId: TEST_TENANT_ID,
      workspaceId: TEST_WORKSPACE_ID,
      projectId: TEST_PROJECT_ID,
      sub: TEST_USER_ID,
      roles: ['ADMIN'],
      ...overrides,
    },
    JWT_SECRET,
    '1h',
  );
}

describe('Kernel Provider Endpoints', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await buildApp({
      jwtSecret: JWT_SECRET,
      backendUrl: 'http://localhost:9999',
      allowedOrigins: ['http://localhost:3000'],
      logger: false,
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('POST /api/v1/kernel/providers/events', () => {
    it('requires authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        payload: {
          metadata: { eventId: 'evt-1', timestamp: '2024-01-01T00:00:00Z', eventType: 'plan-created' },
          productUnitId: 'pu-1',
        },
      });
      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(false);
      expect(body.error).toContain('Unauthorized');
    });

    it('requires tenant/workspace/project scope', async () => {
      const token = signJwt({ sub: TEST_USER_ID }, JWT_SECRET, '1h');
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: { eventId: 'evt-1', timestamp: '2024-01-01T00:00:00Z', eventType: 'plan-created' },
          productUnitId: 'pu-1',
        },
      });
      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(false);
      expect(body.reasonCode).toBe('SCOPE_DENIED');
    });

    it('validates event schema', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: { eventId: 'evt-1' }, // Missing required fields
          productUnitId: 'pu-1',
        },
      });
      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(false);
      expect(body.reasonCode).toBe('INVALID_SCHEMA');
      expect(body.issues).toBeDefined();
    });

    it('returns success with ref on valid request', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: { eventId: 'evt-1', timestamp: '2024-01-01T00:00:00Z', eventType: 'plan-created' },
          productUnitId: 'pu-1',
          runId: 'run-1',
        },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.ref).toMatch(/^ref-/);
      expect(body.correlationId).toBeDefined();
    });
  });

  describe('GET /api/v1/kernel/providers/events (list)', () => {
    it('requires authentication and scope', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/events',
      });
      expect(response.statusCode).toBe(401);
    });

    it('filters by productUnitId and runId', async () => {
      const token = createTestToken();
      
      // Create two events
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: { eventId: 'evt-1', timestamp: '2024-01-01T00:00:00Z', eventType: 'plan-created' },
          productUnitId: 'pu-1',
          runId: 'run-1',
        },
      });
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: { eventId: 'evt-2', timestamp: '2024-01-01T00:00:00Z', eventType: 'plan-created' },
          productUnitId: 'pu-2',
          runId: 'run-2',
        },
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/events?productUnitId=pu-1&runId=run-1',
        headers: { authorization: `Bearer ${token}` },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.items).toHaveLength(1);
      expect(body.items[0].productUnitId).toBe('pu-1');
    });
  });

  describe('POST /api/v1/kernel/providers/artifacts', () => {
    it('validates artifact manifest schema', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/artifacts',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          productUnitId: 'pu-1',
          // Missing required fields
        },
      });
      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(false);
      expect(body.reasonCode).toBe('INVALID_SCHEMA');
    });

    it('returns success with ref on valid request', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/artifacts',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          productUnitId: 'pu-1',
          manifestPath: '/path/to/manifest.json',
          fingerprint: { algorithm: 'sha256', hash: 'abc123' },
        },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.ref).toMatch(/^ref-/);
    });
  });

  describe('GET /api/v1/kernel/providers/health/:productUnitId/latest', () => {
    it('returns 404 when no snapshot exists', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/health/nonexistent/latest',
        headers: { authorization: `Bearer ${token}` },
      });
      expect(response.statusCode).toBe(404);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(false);
      expect(body.reasonCode).toBe('NOT_FOUND');
    });

    it('returns most recent snapshot', async () => {
      const token = createTestToken();
      
      // Create two health snapshots
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/health',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          productUnitId: 'pu-1',
          snapshotPath: '/path/to/snapshot-1.json',
          status: 'healthy',
          observedAt: '2024-01-01T00:00:00Z',
        },
      });
      await new Promise(resolve => setTimeout(resolve, 10)); // Ensure different timestamps
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/health',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          productUnitId: 'pu-1',
          snapshotPath: '/path/to/snapshot-2.json',
          status: 'degraded',
          observedAt: '2024-01-01T00:01:00Z',
        },
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/health/pu-1/latest',
        headers: { authorization: `Bearer ${token}` },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.status).toBe('degraded');
    });
  });

  describe('POST /api/v1/kernel/providers/memory with retention', () => {
    it('stores privacy classification and expiration', async () => {
      const token = createTestToken();
      const expiresAt = new Date(Date.now() + 3600000).toISOString();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/memory',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          memoryId: 'mem-1',
          contentRef: '/path/to/memory.json',
          productUnitId: 'pu-1',
          privacyClassification: 'restricted',
          retention: { expiresAt },
        },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.ref).toMatch(/^ref-/);
    });

    it('filters expired records in list', async () => {
      const token = createTestToken();
      const pastExpiration = new Date(Date.now() - 3600000).toISOString();
      
      // Create expired memory
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/memory',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          memoryId: 'mem-expired',
          contentRef: '/path/to/expired.json',
          productUnitId: 'pu-1',
          retention: { expiresAt: pastExpiration },
        },
      });

      // Create non-expired memory
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/memory',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          memoryId: 'mem-valid',
          contentRef: '/path/to/valid.json',
          productUnitId: 'pu-1',
        },
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/memory?productUnitId=pu-1',
        headers: { authorization: `Bearer ${token}` },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.items).toHaveLength(1);
      expect(body.items[0].memoryId).toBe('mem-valid');
    });
  });

  describe('Redaction of sensitive fields', () => {
    it('redacts authToken in stored data', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: { eventId: 'evt-1', timestamp: '2024-01-01T00:00:00Z', eventType: 'plan-created' },
          productUnitId: 'pu-1',
          authToken: 'secret-token',
        },
      });
      expect(response.statusCode).toBe(200);
      
      // Retrieve the event
      const listResponse = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/events?productUnitId=pu-1',
        headers: { authorization: `Bearer ${token}` },
      });
      const listBody = JSON.parse(listResponse.payload);
      expect(listBody.items[0].authToken).toBe('[REDACTED]');
    });

    it('redacts content when privacy classification is restricted', async () => {
      const token = createTestToken();
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/memory',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          memoryId: 'mem-1',
          contentRef: '/path/to/sensitive.json',
          productUnitId: 'pu-1',
          privacyClassification: 'restricted',
        },
      });
      expect(response.statusCode).toBe(200);
      
      // Retrieve the memory
      const listResponse = await app.inject({
        method: 'GET',
        url: '/api/v1/kernel/providers/memory?productUnitId=pu-1',
        headers: { authorization: `Bearer ${token}` },
      });
      const listBody = JSON.parse(listResponse.payload);
      expect(listBody.items[0].contentRef).toBe('[REDACTED]');
    });
  });
});
