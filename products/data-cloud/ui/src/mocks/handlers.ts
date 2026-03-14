/**
 * MSW Request Handlers
 *
 * <p>Defines all HTTP request interceptors for Mock Service Worker.
 * Used in development (browser) and tests (Node / Vitest) to mock
 * the Data Cloud backend without a running server.
 *
 * <p>Endpoint coverage matches the production API client surface:
 * - /api/v1/collections
 * - /api/v1/workflows
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

// ---------------------------------------------------------------------------
// Shared constants
// ---------------------------------------------------------------------------

const BASE = '/api/v1';
const SIMULATED_DELAY_MS = 80;

// ---------------------------------------------------------------------------
// Seed data
// ---------------------------------------------------------------------------

interface Field {
  id: string;
  name: string;
  type: string;
  required: boolean;
  description: string;
}

const mockCollections = [
  {
    id: 'col-001',
    name: 'Products',
    description: 'Product catalog entities',
    schemaType: 'entity',
    status: 'active',
    entityCount: 1450,
    schema: {
      id: 'schema-001',
      name: 'ProductSchema',
      fields: [
        { id: 'f-1', name: 'id', type: 'string', required: true, description: 'Primary identifier' },
        { id: 'f-2', name: 'name', type: 'string', required: true, description: 'Product name' },
        { id: 'f-3', name: 'price', type: 'number', required: true, description: 'Unit price' },
        { id: 'f-4', name: 'category', type: 'string', required: false, description: 'Product category' },
      ] as Field[],
      constraints: [],
    },
    tags: ['catalog', 'commerce'],
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-06-01T08:30:00Z',
    createdBy: 'user-admin',
  },
  {
    id: 'col-002',
    name: 'OrderEvents',
    description: 'Order lifecycle events stream',
    schemaType: 'event',
    status: 'active',
    entityCount: 42000,
    schema: {
      id: 'schema-002',
      name: 'OrderEventSchema',
      fields: [
        { id: 'f-5', name: 'orderId', type: 'string', required: true, description: 'Order reference' },
        { id: 'f-6', name: 'eventType', type: 'string', required: true, description: 'Event type' },
        { id: 'f-7', name: 'timestamp', type: 'timestamp', required: true, description: 'Event time' },
      ] as Field[],
      constraints: [],
    },
    tags: ['orders', 'events'],
    createdAt: '2024-02-10T12:00:00Z',
    updatedAt: '2024-06-15T14:00:00Z',
    createdBy: 'user-admin',
  },
];

const mockWorkflows = [
  {
    id: 'wf-001',
    name: 'Product Sync Pipeline',
    description: 'Syncs product catalog from ERP to data cloud',
    status: 'active',
    nodes: [
      { id: 'n-1', type: 'source', label: 'ERP Source', position: { x: 100, y: 100 }, data: {} },
      { id: 'n-2', type: 'transform', label: 'Map Fields', position: { x: 300, y: 100 }, data: {} },
      { id: 'n-3', type: 'sink', label: 'Data Cloud', position: { x: 500, y: 100 }, data: {} },
    ],
    edges: [
      { id: 'e-1', source: 'n-1', target: 'n-2', label: 'raw' },
      { id: 'e-2', source: 'n-2', target: 'n-3', label: 'mapped' },
    ],
    schedule: '0 */6 * * *',
    tags: ['etl', 'product'],
    createdAt: '2024-03-01T09:00:00Z',
    updatedAt: '2024-06-10T11:00:00Z',
    createdBy: 'user-admin',
    lastExecutedAt: '2024-06-20T00:00:00Z',
  },
  {
    id: 'wf-002',
    name: 'Order Event Processor',
    description: 'Processes and enriches order events in real-time',
    status: 'active',
    nodes: [
      { id: 'n-4', type: 'source', label: 'Kafka Source', position: { x: 100, y: 200 }, data: {} },
      { id: 'n-5', type: 'sink', label: 'Event Store', position: { x: 300, y: 200 }, data: {} },
    ],
    edges: [{ id: 'e-3', source: 'n-4', target: 'n-5', label: '' }],
    tags: ['streaming', 'orders'],
    createdAt: '2024-04-01T10:00:00Z',
    updatedAt: '2024-06-15T09:00:00Z',
    createdBy: 'user-admin',
  },
];

const mockStorageProfiles = [
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
// Collection handlers
// ---------------------------------------------------------------------------

const collectionHandlers = [
  // GET /api/v1/collections
  http.get(`${BASE}/collections`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 1);
    const pageSize = Number(url.searchParams.get('pageSize') ?? 20);
    const search = url.searchParams.get('search') ?? '';
    const filtered = search
      ? collections.filter(
          (c) =>
            c.name.toLowerCase().includes(search.toLowerCase()) ||
            c.description.toLowerCase().includes(search.toLowerCase())
        )
      : collections;
    return HttpResponse.json(paginate(filtered, page, pageSize));
  }),

  // POST /api/v1/collections
  http.post(`${BASE}/collections`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const body = (await request.json()) as Partial<(typeof collections)[0]>;
    const now = new Date().toISOString();
    const created = {
      id: generateId('col'),
      entityCount: 0,
      tags: [],
      status: 'draft' as const,
      createdAt: now,
      updatedAt: now,
      createdBy: 'mock-user',
      ...body,
    } as (typeof collections)[0];
    collections = [...collections, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  // GET /api/v1/collections/:id
  http.get(`${BASE}/collections/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const col = collections.find((c) => c.id === params.id);
    if (!col) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Collection not found' }, { status: 404 });
    }
    return HttpResponse.json(col);
  }),

  // PUT /api/v1/collections/:id
  http.put(`${BASE}/collections/:id`, async ({ params, request }) => {
    await delay(SIMULATED_DELAY_MS);
    const idx = collections.findIndex((c) => c.id === params.id);
    if (idx === -1) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Collection not found' }, { status: 404 });
    }
    const body = (await request.json()) as Partial<(typeof collections)[0]>;
    const updated = { ...collections[idx], ...body, updatedAt: new Date().toISOString() };
    collections = collections.map((c, i) => (i === idx ? updated : c));
    return HttpResponse.json(updated);
  }),

  // DELETE /api/v1/collections/:id
  http.delete(`${BASE}/collections/:id`, async ({ params }) => {
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
// Workflow handlers
// ---------------------------------------------------------------------------

const workflowHandlers = [
  // GET /api/v1/workflows
  http.get(`${BASE}/workflows`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 1);
    const pageSize = Number(url.searchParams.get('pageSize') ?? 20);
    return HttpResponse.json(paginate(workflows, page, pageSize));
  }),

  // POST /api/v1/workflows
  http.post(`${BASE}/workflows`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const body = (await request.json()) as Partial<(typeof workflows)[0]>;
    const now = new Date().toISOString();
    const created = {
      id: generateId('wf'),
      nodes: [],
      edges: [],
      tags: [],
      status: 'draft' as const,
      createdAt: now,
      updatedAt: now,
      createdBy: 'mock-user',
      ...body,
    } as (typeof workflows)[0];
    workflows = [...workflows, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  // GET /api/v1/workflows/:id
  http.get(`${BASE}/workflows/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const wf = workflows.find((w) => w.id === params.id);
    if (!wf) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Workflow not found' }, { status: 404 });
    }
    return HttpResponse.json(wf);
  }),

  // PUT /api/v1/workflows/:id
  http.put(`${BASE}/workflows/:id`, async ({ params, request }) => {
    await delay(SIMULATED_DELAY_MS);
    const idx = workflows.findIndex((w) => w.id === params.id);
    if (idx === -1) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Workflow not found' }, { status: 404 });
    }
    const body = (await request.json()) as Partial<(typeof workflows)[0]>;
    const updated = { ...workflows[idx], ...body, updatedAt: new Date().toISOString() };
    workflows = workflows.map((w, i) => (i === idx ? updated : w));
    return HttpResponse.json(updated);
  }),

  // DELETE /api/v1/workflows/:id
  http.delete(`${BASE}/workflows/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const exists = workflows.some((w) => w.id === params.id);
    if (!exists) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Workflow not found' }, { status: 404 });
    }
    workflows = workflows.filter((w) => w.id !== params.id);
    return new HttpResponse(null, { status: 204 });
  }),

  // POST /api/v1/workflows/:id/execute
  http.post(`${BASE}/workflows/:id/execute`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const wf = workflows.find((w) => w.id === params.id);
    if (!wf) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Workflow not found' }, { status: 404 });
    }
    const execution = {
      id: generateId('exec'),
      workflowId: params.id,
      status: 'running',
      startedAt: new Date().toISOString(),
      nodeExecutions: wf.nodes.map((n) => ({
        nodeId: n.id,
        status: 'pending',
      })),
      triggeredBy: 'manual',
    };
    return HttpResponse.json(execution, { status: 202 });
  }),

  // GET /api/v1/workflows/:id/executions
  http.get(`${BASE}/workflows/:id/executions`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const now = new Date();
    const executions = [
      {
        id: generateId('exec'),
        workflowId: params.id,
        status: 'completed',
        startedAt: new Date(now.getTime() - 3600000).toISOString(),
        completedAt: new Date(now.getTime() - 3540000).toISOString(),
        duration: 60000,
        nodeExecutions: [],
        triggeredBy: 'schedule',
      },
    ];
    return HttpResponse.json(paginate(executions));
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
