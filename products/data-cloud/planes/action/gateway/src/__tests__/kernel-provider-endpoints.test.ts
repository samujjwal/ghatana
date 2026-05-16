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
import { signJwt } from '../jwt.js';
import { InMemoryProviderStore } from '../provider-store.js';

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
  let providerStore: InMemoryProviderStore;

  beforeAll(async () => {
    providerStore = new InMemoryProviderStore();
    app = await buildApp({
      jwtSecret: JWT_SECRET,
      backendUrl: 'http://localhost:9999',
      allowedOrigins: ['http://localhost:3000'],
      logger: false,
      providerStore,
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
          metadata: {
            eventId: 'evt-1',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.plan.created',
            productUnitId: 'pu-1',
            runId: 'run-1',
            phase: 'build',
            timestamp: '2024-01-01T00:00:00Z',
            source: 'test',
            correlationId: 'corr-1',
          },
          payload: {
            planRunId: 'plan-1',
            phase: 'build',
            providerMode: 'bootstrap',
            createdAt: '2024-01-01T00:00:00Z',
          },
        },
      });
      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.payload);
      expect(body.error).toContain('Unauthorized');
      expect(body.message).toContain('Missing Bearer token');
    });

    it('requires tenant/workspace/project scope', async () => {
      const token = signJwt({ sub: TEST_USER_ID }, JWT_SECRET, '1h');
      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: {
            eventId: 'evt-1',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.plan.created',
            productUnitId: 'pu-1',
            runId: 'run-1',
            phase: 'build',
            timestamp: '2024-01-01T00:00:00Z',
            source: 'test',
            correlationId: 'corr-1',
          },
          payload: {
            planRunId: 'plan-1',
            phase: 'build',
            providerMode: 'bootstrap',
            createdAt: '2024-01-01T00:00:00Z',
          },
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
          payload: {},
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
          metadata: {
            eventId: 'evt-1',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.plan.created',
            productUnitId: 'pu-1',
            runId: 'run-1',
            phase: 'build',
            timestamp: '2024-01-01T00:00:00Z',
            source: 'test',
            correlationId: 'corr-1',
          },
          payload: {
            planRunId: 'plan-1',
            phase: 'build',
            providerMode: 'bootstrap',
            createdAt: '2024-01-01T00:00:00Z',
          },
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
      const productUnitId = 'pu-list-1';
      const runId = 'run-list-1';
      
      // Create two events
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: {
            eventId: 'evt-1',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.plan.created',
            productUnitId,
            runId,
            phase: 'build',
            timestamp: '2024-01-01T00:00:00Z',
            source: 'test',
            correlationId: 'corr-1',
          },
          payload: {
            planRunId: 'plan-1',
            phase: 'build',
            providerMode: 'bootstrap',
            createdAt: '2024-01-01T00:00:00Z',
          },
        },
      });
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/events',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          metadata: {
            eventId: 'evt-2',
            schemaVersion: '1.0.0',
            eventType: 'lifecycle.plan.created',
            productUnitId: 'pu-list-2',
            runId: 'run-list-2',
            phase: 'build',
            timestamp: '2024-01-01T00:00:00Z',
            source: 'test',
            correlationId: 'corr-2',
          },
          payload: {
            planRunId: 'plan-2',
            phase: 'build',
            providerMode: 'bootstrap',
            createdAt: '2024-01-01T00:00:00Z',
          },
        },
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/v1/kernel/providers/events?productUnitId=${productUnitId}&runId=${runId}`,
        headers: { authorization: `Bearer ${token}` },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.items).toHaveLength(1);
      expect(body.items[0].metadata.productUnitId).toBe(productUnitId);
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
          runId: 'run-1',
          manifestPath: '/path/to/manifest.json',
          artifactCount: 1,
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
          runId: 'run-1',
          snapshotPath: '/path/to/snapshot-1.json',
          status: 'healthy',
          snapshotAt: '2024-01-01T00:00:00Z',
        },
      });
      await new Promise(resolve => setTimeout(resolve, 10)); // Ensure different timestamps
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/health',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          productUnitId: 'pu-1',
          runId: 'run-2',
          snapshotPath: '/path/to/snapshot-2.json',
          status: 'degraded',
          snapshotAt: '2024-01-01T00:01:00Z',
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
          runId: 'run-1',
          kind: 'runtime-truth',
          privacyClassification: 'restricted',
          retention: { policyId: 'default', retentionDays: 1, expiresAt },
          recordedAt: '2024-01-01T00:00:00Z',
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
      const productUnitId = 'pu-memory-list-1';
      const runId = 'run-memory-list-1';
      
      // Create expired memory
      await app.inject({
        method: 'POST',
        url: '/api/v1/kernel/providers/memory',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          memoryId: 'mem-expired',
          contentRef: '/path/to/expired.json',
          productUnitId,
          runId,
          kind: 'runtime-truth',
          retention: { policyId: 'default', retentionDays: 1, expiresAt: pastExpiration },
          recordedAt: '2024-01-01T00:00:00Z',
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
          productUnitId,
          runId,
          kind: 'runtime-truth',
          recordedAt: '2024-01-01T00:00:00Z',
        },
      });

      const response = await app.inject({
        method: 'GET',
        url: `/api/v1/kernel/providers/memory?productUnitId=${productUnitId}&runId=${runId}`,
        headers: { authorization: `Bearer ${token}` },
      });
      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.payload);
      expect(body.success).toBe(true);
      expect(body.items).toHaveLength(1);
      expect(body.items[0].memoryId).toBe('mem-valid');
    });
  });
});

describe('Kernel Provider Endpoints without provider store', () => {
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

  it('fails closed when provider storage is not configured', async () => {
    const token = createTestToken();
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/kernel/providers/events',
      headers: { authorization: `Bearer ${token}` },
      payload: {
        metadata: {
          eventId: 'evt-1',
          schemaVersion: '1.0.0',
          eventType: 'lifecycle.plan.created',
          productUnitId: 'pu-1',
          runId: 'run-1',
          phase: 'build',
          timestamp: '2024-01-01T00:00:00Z',
          source: 'test',
          correlationId: 'corr-1',
        },
        payload: {
          planRunId: 'plan-1',
          phase: 'build',
          providerMode: 'bootstrap',
          createdAt: '2024-01-01T00:00:00Z',
        },
      },
    });

    expect(response.statusCode).toBe(503);
    const body = JSON.parse(response.payload);
    expect(body.success).toBe(false);
    expect(body.reasonCode).toBe('PROVIDER_STORE_UNAVAILABLE');
  });
});
