/**
 * MSW Request Handlers
 *
 * <p>Defines all HTTP request interceptors for Mock Service Worker.
 * Used in development (browser) and tests (Node / Vitest) to mock
 * the Data Cloud backend without a running server.
 *
 * <p>Endpoint coverage matches the production API client surface (Option A
 * mapping from DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 2):
 * - /api/v1/entities/dc_collections  (was /collections)
 * - /api/v1/pipelines                (was /workflows)
 * - /api/v1/data-fabric/profiles
 * - /api/v1/data-fabric/connectors
 * - /api/v1/ai/*
 *
 * @doc.type config
 * @doc.purpose MSW request handlers for development and testing
 * @doc.layer frontend
 * @doc.pattern Adapter
 */

import { http, HttpResponse, delay } from 'msw';
import { MOCK_COLLECTIONS, MOCK_WORKFLOWS } from '../lib/mock-data';
import {
  StorageProfileSchema,
  ConnectorSchema,
} from '../contracts/schemas';
import type { z } from 'zod';

// ---------------------------------------------------------------------------
// Shared constants
// ---------------------------------------------------------------------------

const BASE = '/api/v1';
const SIMULATED_DELAY_MS = 80;

// ---------------------------------------------------------------------------
// Contract validation helper — validates MSW mock responses against schemas
// to ensure mock data stays aligned with API contracts.
// ---------------------------------------------------------------------------

function contractJson<T extends z.ZodTypeAny>(
  schema: T,
  data: unknown,
  init?: { status?: number }
): ReturnType<typeof HttpResponse.json> {
  const result = schema.safeParse(data);
  if (!result.success) {
    console.warn(
      '[MSW contract violation]',
      result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`)
    );
  }
  return HttpResponse.json(data as Record<string, unknown>, init);
}

// ---------------------------------------------------------------------------
// Seed data — derived from the canonical mock-data library so tests and
// handlers share a single source of truth.
// ---------------------------------------------------------------------------

const mockCollections = MOCK_COLLECTIONS.map((c) => ({
  id: c.id,
  name: c.name,
  description: c.description,
  schemaType: 'entity' as const,
  status: (c.isActive ? 'active' : 'draft') as 'active' | 'draft',
  isActive: c.isActive,
  entityCount: c.entityCount,
  schema: {
    id: c.schema.id,
    name: c.schema.name,
    fields: c.schema.fields.map((f) => ({
      id: f.id,
      name: f.name,
      type: f.type as string,
      required: f.required,
      description: f.description ?? '',
    })),
    constraints: c.schema.constraints.map((con) => ({ ...con } as Record<string, unknown>)),
  },
  tags: [] as string[],
  createdAt: c.createdAt,
  updatedAt: c.updatedAt,
  createdBy: 'mock-user',
}));

const mockWorkflows = MOCK_WORKFLOWS.map((w) => ({
  id: w.id,
  name: w.name,
  description: w.description,
  status: w.status as 'draft' | 'active' | 'paused' | 'archived',
  nodes: w.nodes.map((n) => ({
    id: n.id,
    type: n.type,
    label: n.label,
    position: n.position,
    data: { ...n.data } as Record<string, unknown>,
  })),
  edges: w.edges.map((e) => ({ id: e.id, source: e.source, target: e.target, label: e.label ?? '' })),
  tags: [] as string[],
  createdAt: w.createdAt,
  updatedAt: w.updatedAt,
  createdBy: 'mock-user',
  lastExecutedAt: w.lastExecutedAt,
}));

const mockStorageProfiles: Array<{
  id: string;
  name: string;
  type: string;
  isDefault: boolean;
  status: string;
  config: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}> = [
  {
    id: 'sp-001',
    name: 'Primary RocksDB',
    type: 'rocksdb',
    isDefault: true,
    status: 'healthy',
    config: { path: '/data/rocksdb', maxWriteBufferSize: '64MB' },
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-06-01T00:00:00Z',
  },
  {
    id: 'sp-002',
    name: 'ClickHouse Analytics',
    type: 'clickhouse',
    isDefault: false,
    status: 'healthy',
    config: { host: 'clickhouse.internal', port: 8123, database: 'datacloud' },
    createdAt: '2024-01-10T00:00:00Z',
    updatedAt: '2024-05-15T00:00:00Z',
  },
];

const mockConnectors = [
  {
    id: 'dc-001',
    name: 'ERP Connector',
    type: 'jdbc',
    storageProfileId: 'sp-001',
    status: 'active',
    config: { url: 'jdbc:postgresql://erp.internal/erp_db' },
    createdAt: '2024-02-01T00:00:00Z',
    updatedAt: '2024-06-01T00:00:00Z',
  },
];

// In-memory stores for mutation handlers
let collections = [...mockCollections];
let workflows = [...mockWorkflows];
let storageProfiles = [...mockStorageProfiles];
let connectors = [...mockConnectors];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function paginate<T>(items: T[], page = 1, pageSize = 20) {
  const start = (page - 1) * pageSize;
  const slice = items.slice(start, start + pageSize);
  return {
    items: slice,
    total: items.length,
    page,
    pageSize,
    hasMore: start + slice.length < items.length,
  };
}

function generateId(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
}

// ---------------------------------------------------------------------------
// Collection handlers  (Option A — DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 2)
//
// The UI collectionsApi now calls /api/v1/entities/dc_collections, which is
// the real backend entity CRUD route.  Responses follow the BackendEntity /
// BackendEntityListResponse shape consumed by entityToCollection() in
// collections.ts.  The old /api/v1/collections route no longer exists in the
// production backend.
// ---------------------------------------------------------------------------

function collectionToEntity(c: (typeof collections)[0]) {
  return {
    id: c.id,
    collection: 'dc_collections',
    data: {
      name: c.name,
      description: c.description,
      schemaType: c.schemaType,
      status: c.status,
      isActive: c.isActive,
      entityCount: c.entityCount,
      schema: c.schema,
      tags: c.tags,
      createdBy: c.createdBy,
    },
    version: 1,
    createdAt: c.createdAt,
    updatedAt: c.updatedAt,
  };
}

const collectionHandlers = [
  // GET /api/v1/entities/dc_collections
  http.get(`${BASE}/entities/dc_collections`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get('limit') ?? 50);
    const offset = Number(url.searchParams.get('offset') ?? 0);
    const search = url.searchParams.get('search') ?? '';
    const filtered = search
      ? collections.filter(
          (c) =>
            c.name.toLowerCase().includes(search.toLowerCase()) ||
            c.description.toLowerCase().includes(search.toLowerCase())
        )
      : collections;
    const slice = filtered.slice(offset, offset + limit);
    return HttpResponse.json({
      entities: slice.map(collectionToEntity),
      count: filtered.length,
      tenantId: 'default',
      timestamp: new Date().toISOString(),
    });
  }),

  // POST /api/v1/entities/dc_collections  (create)
  http.post(`${BASE}/entities/dc_collections`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const body = (await request.json()) as Record<string, unknown>;
    const now = new Date().toISOString();
    // If an id is present it is an upsert (update); otherwise create.
    const existingIdx = body.id ? collections.findIndex((c) => c.id === body.id) : -1;
    if (existingIdx >= 0) {
      const updated = {
        ...collections[existingIdx],
        ...(body as Partial<(typeof collections)[0]>),
        updatedAt: now,
      };
      collections = collections.map((c, i) => (i === existingIdx ? updated : c));
      return HttpResponse.json({
        id: updated.id,
        collection: 'dc_collections',
        version: 2,
        createdAt: updated.createdAt,
        timestamp: now,
      });
    }
    const created = {
      id: generateId('col'),
      entityCount: 0,
      tags: [] as string[],
      status: 'draft' as const,
      schemaType: 'entity' as const,
      isActive: false,
      schema: { fields: [] as (typeof collections)[0]['schema']['fields'], constraints: [] as (typeof collections)[0]['schema']['constraints'] },
      createdAt: now,
      updatedAt: now,
      createdBy: 'mock-user',
      name: '',
      description: '',
      ...(body as Partial<(typeof collections)[0]>),
    } as (typeof collections)[0];
    collections = [...collections, created];
    return HttpResponse.json(
      { id: created.id, collection: 'dc_collections', version: 1, createdAt: created.createdAt, timestamp: now },
      { status: 201 }
    );
  }),

  // GET /api/v1/entities/dc_collections/:id
  http.get(`${BASE}/entities/dc_collections/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const col = collections.find((c) => c.id === params.id);
    if (!col) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Collection not found' }, { status: 404 });
    }
    return HttpResponse.json(collectionToEntity(col));
  }),

  // DELETE /api/v1/entities/dc_collections/:id
  http.delete(`${BASE}/entities/dc_collections/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const exists = collections.some((c) => c.id === params.id);
    if (!exists) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Collection not found' }, { status: 404 });
    }
    collections = collections.filter((c) => c.id !== params.id);
    return new HttpResponse(null, { status: 204 });
  }),
];

// ---------------------------------------------------------------------------
// Pipeline handlers  (Option A — DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 2)
//
// The backend exposes /api/v1/pipelines (not /api/v1/workflows).
// GET /pipelines  → { tenantId, pipelines: [...flatPipeline], count, timestamp }
// GET /pipelines/:pipelineId → { id, tenantId, ...dataFields }
// POST / PUT → save/update pipeline; { id, tenantId, ...dataFields }
// DELETE → { deleted, pipelineId, tenantId, timestamp }
// ---------------------------------------------------------------------------

function workflowToPipeline(w: (typeof workflows)[0]) {
  return {
    id: w.id,
    tenantId: 'default',
    name: w.name,
    description: w.description,
    status: w.status,
    nodes: w.nodes,
    edges: w.edges,
    tags: w.tags,
    createdAt: w.createdAt,
    updatedAt: w.updatedAt,
    createdBy: w.createdBy,
    lastExecutedAt: w.lastExecutedAt,
  };
}

const workflowHandlers = [
  // GET /api/v1/pipelines
  http.get(`${BASE}/pipelines`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get('limit') ?? 500);
    return HttpResponse.json({
      tenantId: 'default',
      pipelines: workflows.slice(0, limit).map(workflowToPipeline),
      count: workflows.length,
      timestamp: new Date().toISOString(),
    });
  }),

  // POST /api/v1/pipelines  (create)
  http.post(`${BASE}/pipelines`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const body = (await request.json()) as Partial<(typeof workflows)[0]>;
    const now = new Date().toISOString();
    const created = {
      id: generateId('wf'),
      nodes: [],
      edges: [],
      tags: [] as string[],
      status: 'draft' as const,
      createdAt: now,
      updatedAt: now,
      createdBy: 'mock-user',
      name: '',
      description: '',
      ...body,
    } as (typeof workflows)[0];
    workflows = [...workflows, created];
    return HttpResponse.json(workflowToPipeline(created), { status: 201 });
  }),

  // GET /api/v1/pipelines/:pipelineId
  http.get(`${BASE}/pipelines/:pipelineId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const wf = workflows.find((w) => w.id === params.pipelineId);
    if (!wf) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Pipeline not found' }, { status: 404 });
    }
    return HttpResponse.json(workflowToPipeline(wf));
  }),

  // PUT /api/v1/pipelines/:pipelineId
  http.put(`${BASE}/pipelines/:pipelineId`, async ({ params, request }) => {
    await delay(SIMULATED_DELAY_MS);
    const idx = workflows.findIndex((w) => w.id === params.pipelineId);
    if (idx === -1) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Pipeline not found' }, { status: 404 });
    }
    const body = (await request.json()) as Partial<(typeof workflows)[0]>;
    const updated = { ...workflows[idx], ...body, updatedAt: new Date().toISOString() };
    workflows = workflows.map((w, i) => (i === idx ? updated : w));
    return HttpResponse.json(workflowToPipeline(updated));
  }),

  // DELETE /api/v1/pipelines/:pipelineId
  http.delete(`${BASE}/pipelines/:pipelineId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const exists = workflows.some((w) => w.id === params.pipelineId);
    if (!exists) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Pipeline not found' }, { status: 404 });
    }
    workflows = workflows.filter((w) => w.id !== params.pipelineId);
    return HttpResponse.json({
      deleted: true,
      pipelineId: params.pipelineId,
      tenantId: 'default',
      timestamp: new Date().toISOString(),
    });
  }),

  // POST /api/v1/pipelines/:pipelineId/execute  (no backend equivalent; stub for dev)
  http.post(`${BASE}/pipelines/:pipelineId/execute`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const wf = workflows.find((w) => w.id === params.pipelineId);
    if (!wf) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Pipeline not found' }, { status: 404 });
    }
    return HttpResponse.json({
      id: generateId('exec'),
      workflowId: params.pipelineId,
      status: 'running',
      startedAt: new Date().toISOString(),
      nodeExecutions: wf.nodes.map((n) => ({ nodeId: n.id, status: 'pending' })),
      triggeredBy: 'manual',
    }, { status: 202 });
  }),
];

// ---------------------------------------------------------------------------
// Data Fabric — Storage Profile handlers
// ---------------------------------------------------------------------------

const storageFabricHandlers = [
  // GET /api/v1/data-fabric/profiles
  http.get(`${BASE}/data-fabric/profiles`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json(storageProfiles);
  }),

  // POST /api/v1/data-fabric/profiles
  http.post(`${BASE}/data-fabric/profiles`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const body = (await request.json()) as Partial<(typeof storageProfiles)[0]>;
    const now = new Date().toISOString();
    const created = {
      id: generateId('sp'),
      isDefault: false,
      status: 'healthy',
      createdAt: now,
      updatedAt: now,
      config: {},
      ...body,
    } as (typeof storageProfiles)[0];
    storageProfiles = [...storageProfiles, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  // GET /api/v1/data-fabric/profiles/:profileId
  http.get(`${BASE}/data-fabric/profiles/:profileId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const profile = storageProfiles.find((p) => p.id === params.profileId);
    if (!profile) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Profile not found' }, { status: 404 });
    }
    return HttpResponse.json(profile);
  }),

  // PUT /api/v1/data-fabric/profiles/:profileId
  http.put(`${BASE}/data-fabric/profiles/:profileId`, async ({ params, request }) => {
    await delay(SIMULATED_DELAY_MS);
    const idx = storageProfiles.findIndex((p) => p.id === params.profileId);
    if (idx === -1) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Profile not found' }, { status: 404 });
    }
    const body = (await request.json()) as Partial<(typeof storageProfiles)[0]>;
    const updated = { ...storageProfiles[idx], ...body, updatedAt: new Date().toISOString() };
    storageProfiles = storageProfiles.map((p, i) => (i === idx ? updated : p));
    return HttpResponse.json(updated);
  }),

  // DELETE /api/v1/data-fabric/profiles/:profileId
  http.delete(`${BASE}/data-fabric/profiles/:profileId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    storageProfiles = storageProfiles.filter((p) => p.id !== params.profileId);
    return new HttpResponse(null, { status: 204 });
  }),

  // POST /api/v1/data-fabric/profiles/:profileId/set-default
  http.post(`${BASE}/data-fabric/profiles/:profileId/set-default`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    storageProfiles = storageProfiles.map((p) => ({ ...p, isDefault: p.id === params.profileId }));
    const profile = storageProfiles.find((p) => p.id === params.profileId);
    if (!profile) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Profile not found' }, { status: 404 });
    }
    return HttpResponse.json(profile);
  }),

  // GET /api/v1/data-fabric/profiles/:profileId/metrics
  http.get(`${BASE}/data-fabric/profiles/:profileId/metrics`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      storageUsedBytes: 2_147_483_648,
      storageTotalBytes: 107_374_182_400,
      readOpsPerSec: 1200,
      writeOpsPerSec: 340,
      latencyP99Ms: 4.2,
      lastUpdated: new Date().toISOString(),
    });
  }),
];

// ---------------------------------------------------------------------------
// Data Fabric — Connector handlers
// ---------------------------------------------------------------------------

const connectorHandlers = [
  // GET /api/v1/data-fabric/connectors
  http.get(`${BASE}/data-fabric/connectors`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const profileId = url.searchParams.get('profileId');
    const filtered = profileId
      ? connectors.filter((c) => c.storageProfileId === profileId)
      : connectors;
    return HttpResponse.json(filtered);
  }),

  // POST /api/v1/data-fabric/connectors
  http.post(`${BASE}/data-fabric/connectors`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const body = (await request.json()) as Partial<(typeof connectors)[0]>;
    const now = new Date().toISOString();
    const created = {
      id: generateId('dc'),
      status: 'pending',
      config: {},
      createdAt: now,
      updatedAt: now,
      ...body,
    } as (typeof connectors)[0];
    connectors = [...connectors, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  // GET /api/v1/data-fabric/connectors/:connectorId
  http.get(`${BASE}/data-fabric/connectors/:connectorId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const connector = connectors.find((c) => c.id === params.connectorId);
    if (!connector) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Connector not found' }, { status: 404 });
    }
    return HttpResponse.json(connector);
  }),

  // PUT /api/v1/data-fabric/connectors/:connectorId
  http.put(`${BASE}/data-fabric/connectors/:connectorId`, async ({ params, request }) => {
    await delay(SIMULATED_DELAY_MS);
    const idx = connectors.findIndex((c) => c.id === params.connectorId);
    if (idx === -1) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Connector not found' }, { status: 404 });
    }
    const body = (await request.json()) as Partial<(typeof connectors)[0]>;
    const updated = { ...connectors[idx], ...body, updatedAt: new Date().toISOString() };
    connectors = connectors.map((c, i) => (i === idx ? updated : c));
    return HttpResponse.json(updated);
  }),

  // DELETE /api/v1/data-fabric/connectors/:connectorId
  http.delete(`${BASE}/data-fabric/connectors/:connectorId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    connectors = connectors.filter((c) => c.id !== params.connectorId);
    return new HttpResponse(null, { status: 204 });
  }),

  // POST /api/v1/data-fabric/connectors/:connectorId/test
  http.post(`${BASE}/data-fabric/connectors/:connectorId/test`, async () => {
    await delay(SIMULATED_DELAY_MS * 3);
    return HttpResponse.json({ success: true, message: 'Connection successful', latencyMs: 24 });
  }),

  // POST /api/v1/data-fabric/connectors/:connectorId/sync
  http.post(`${BASE}/data-fabric/connectors/:connectorId/sync`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({ jobId: generateId('job') }, { status: 202 });
  }),

  // GET /api/v1/data-fabric/connectors/:connectorId/statistics
  http.get(`${BASE}/data-fabric/connectors/:connectorId/statistics`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      lastSyncAt: new Date(Date.now() - 3600000).toISOString(),
      totalRecordsSynced: 125000,
      recordsSyncedLastRun: 1200,
      syncDurationMs: 45000,
      errorCount: 0,
    });
  }),
];

// ---------------------------------------------------------------------------
// AI handlers
// ---------------------------------------------------------------------------

const aiHandlers = [
  // POST /api/v1/ai/nlq
  http.post(`${BASE}/ai/nlq`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS * 4);
    const body = (await request.json()) as { query: string };
    return HttpResponse.json({
      sql: `SELECT * FROM ${body.query?.split(' ').pop() ?? 'entities'} LIMIT 100`,
      confidence: 0.92,
      explanation: 'Translated natural language to SQL using schema context.',
      suggestions: [],
    });
  }),

  // POST /api/v1/ai/schema-suggestions
  http.post(`${BASE}/ai/schema-suggestions`, async () => {
    await delay(SIMULATED_DELAY_MS * 3);
    return HttpResponse.json({
      suggestions: [
        { field: 'createdAt', type: 'timestamp', reason: 'Common audit field' },
        { field: 'updatedAt', type: 'timestamp', reason: 'Common audit field' },
        { field: 'tenantId', type: 'string', reason: 'Multi-tenancy support' },
      ],
    });
  }),

  // POST /api/v1/ai/semantic-search
  http.post(`${BASE}/ai/semantic-search`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS * 2);
    const body = (await request.json()) as { query: string };
    return HttpResponse.json({
      query: body.query,
      results: [],
      totalResults: 0,
      searchTimeMs: 12,
    });
  }),
];

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

/**
 * All MSW handlers. Used by both browser worker and Node server.
 */
export const handlers = [
  ...collectionHandlers,
  ...workflowHandlers,
  ...storageFabricHandlers,
  ...connectorHandlers,
  ...aiHandlers,
];

/**
 * Reset in-memory stores to seed data. Call in beforeEach for isolated tests.
 */
export function resetMockData() {
  collections = [...mockCollections];
  workflows = [...mockWorkflows];
  storageProfiles = [...mockStorageProfiles];
  connectors = [...mockConnectors];
}
