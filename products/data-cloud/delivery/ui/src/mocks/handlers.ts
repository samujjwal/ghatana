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
import {
  COLLECTION_RUNTIME_OPENAPI_PATHS,
  DEPRECATED_COLLECTION_ROUTE_REDIRECTS,
  DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS,
  buildDeprecatedRouteHeaders,
  formatDeprecatedRouteWarning,
  warnDeprecatedRoute,
} from '../../test-fixtures/deprecatedRoutes';
import {
  StorageProfileSchema,
  ConnectorSchema,
  LineageDagResponseSchema,
  LineageImpactResponseSchema,
} from '../contracts/schemas';
import type { z } from 'zod';

// ---------------------------------------------------------------------------
// Shared constants
// ---------------------------------------------------------------------------

const BASE = '/api/v1';
const SIMULATED_DELAY_MS = 80;
const MOCK_TENANT_ID = 'tenant-alpha';

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

const mockPlugins = [
  {
    id: 'core-audit-plugin',
    displayName: 'Core Audit Plugin',
    version: '1.0.0',
    status: 'enabled' as const,
    supportedRecordTypes: ['AUDIT_EVENT', 'PII_EVENT'],
  },
  {
    id: 'entity-sync-plugin',
    displayName: 'Entity Sync Plugin',
    version: '1.0.0',
    status: 'disabled' as const,
    supportedRecordTypes: ['ENTITY', 'FEATURE_VECTOR'],
  },
];
let plugins = [...mockPlugins];

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

function buildCollectionCostReport(collection: (typeof collections)[0]) {
  const totalSizeGb = Math.max(0.25, Number((collection.entityCount / 50_000).toFixed(2)));
  const totalCostDccPerDay = Number((totalSizeGb * 1.75).toFixed(2));
  return {
    collectionId: collection.id,
    tenantId: MOCK_TENANT_ID,
    totalSizeGb,
    totalCostDccPerDay,
    currency: 'DCC',
    tiers: [
      {
        tier: 'HOT' as const,
        sizeGb: totalSizeGb,
        costDccPerDay: totalCostDccPerDay,
        backend: 'rocksdb',
      },
    ],
    note: 'Mock cost report derived from collection volume for UI tests.',
  };
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
      tenantId: MOCK_TENANT_ID,
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

  // Deprecated CRUD alias: GET /api/v1/collections
  http.get(`${BASE}/collections`, async () => {
    await delay(SIMULATED_DELAY_MS);
    const legacyPath = `${BASE}/collections`;
    const canonicalPath = `${BASE}/entities/dc_collections`;
    warnDeprecatedRoute(legacyPath, canonicalPath);
    return new HttpResponse(null, {
      status: 308,
      headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
    });
  }),

  // Deprecated CRUD alias: POST /api/v1/collections
  http.post(`${BASE}/collections`, async () => {
    await delay(SIMULATED_DELAY_MS);
    const legacyPath = `${BASE}/collections`;
    const canonicalPath = `${BASE}/entities/dc_collections`;
    warnDeprecatedRoute(legacyPath, canonicalPath);
    return new HttpResponse(null, {
      status: 308,
      headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
    });
  }),

  // Deprecated CRUD alias: GET /api/v1/collections/:id
  http.get(`${BASE}/collections/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const legacyPath = `${BASE}/collections/${params.id}`;
    const canonicalPath = `${BASE}/entities/dc_collections/${params.id}`;
    warnDeprecatedRoute(legacyPath, canonicalPath);
    return new HttpResponse(null, {
      status: 308,
      headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
    });
  }),

  // Deprecated CRUD alias: PUT /api/v1/collections/:id
  http.put(`${BASE}/collections/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const legacyPath = `${BASE}/collections/${params.id}`;
    const canonicalPath = `${BASE}/entities/dc_collections/${params.id}`;
    warnDeprecatedRoute(legacyPath, canonicalPath);
    return new HttpResponse(null, {
      status: 308,
      headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
    });
  }),

  // Deprecated CRUD alias: DELETE /api/v1/collections/:id
  http.delete(`${BASE}/collections/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const legacyPath = `${BASE}/collections/${params.id}`;
    const canonicalPath = `${BASE}/entities/dc_collections/${params.id}`;
    warnDeprecatedRoute(legacyPath, canonicalPath);
    return new HttpResponse(null, {
      status: 308,
      headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
    });
  }),

  // Canonical operator route: GET /api/v1/collections/:id/cost-report
  http.get(`${BASE}/collections/:id/cost-report`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const col = collections.find((c) => c.id === params.id);
    if (!col) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Collection not found' }, { status: 404 });
    }
    return HttpResponse.json(buildCollectionCostReport(col));
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

  // GET /api/v1/lineage/:collection
  http.get(`${BASE}/lineage/:collection`, async ({ params, request }) => {
    await delay(SIMULATED_DELAY_MS);
    const collection = String(params.collection);
    const direction = new URL(request.url).searchParams.get('direction') ?? 'BOTH';

    return contractJson(LineageDagResponseSchema, {
      collection,
      tenantId: MOCK_TENANT_ID,
      direction,
      timestamp: new Date().toISOString(),
      dag: {
        nodes: [
          { id: collection, type: 'DATASET', name: collection, role: 'root', metadata: {} },
          { id: `${collection}-source`, type: 'DATASET', name: `${collection} Source`, role: 'upstream', metadata: {} },
          { id: `${collection}-consumer`, type: 'DATASET', name: `${collection} Consumer`, role: 'downstream', metadata: {} },
        ],
        edges: [
          { source: `${collection}-source`, target: collection, type: 'DERIVES_FROM' },
          { source: collection, target: `${collection}-consumer`, type: 'FEEDS_INTO' },
        ],
      },
      upstreamCount: 1,
      downstreamCount: 1,
    });
  }),

  // GET /api/v1/lineage/:collection/impact
  http.get(`${BASE}/lineage/:collection/impact`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const collection = String(params.collection);

    return contractJson(LineageImpactResponseSchema, {
      collection,
      tenantId: MOCK_TENANT_ID,
      impactLevel: 'MEDIUM',
      affectedCount: 1,
      affectedCollections: [`${collection}-consumer`],
      timestamp: new Date().toISOString(),
    });
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
    tenantId: MOCK_TENANT_ID,
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
      tenantId: MOCK_TENANT_ID,
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
      tenantId: MOCK_TENANT_ID,
      timestamp: new Date().toISOString(),
    });
  }),

  // POST /api/v1/pipelines/:pipelineId/execute
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

const pluginHandlers = [
  http.get(`${BASE}/plugins`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({ plugins, total: plugins.length });
  }),

  http.get(`${BASE}/plugins/:id`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const plugin = plugins.find((entry) => entry.id === params.id);
    if (!plugin) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Plugin not found' }, { status: 404 });
    }
    return HttpResponse.json(plugin);
  }),

  http.post(`${BASE}/plugins/:id/enable`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    plugins = plugins.map((plugin) =>
      plugin.id === params.id ? { ...plugin, status: 'enabled' as const } : plugin,
    );
    return HttpResponse.json({ success: true });
  }),

  http.post(`${BASE}/plugins/:id/disable`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    plugins = plugins.map((plugin) =>
      plugin.id === params.id ? { ...plugin, status: 'disabled' as const } : plugin,
    );
    return HttpResponse.json({ success: true });
  }),

  http.post(`${BASE}/plugins/:id/upgrade`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    plugins = plugins.map((plugin) =>
      plugin.id === params.id
        ? { ...plugin, version: plugin.version === '1.0.0' ? '1.0.1' : plugin.version }
        : plugin,
    );
    return HttpResponse.json({ success: true, reloaded: true });
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
  // GET /api/v1/data-fabric/metrics
  http.get(`${BASE}/data-fabric/metrics`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      tiers: [
        {
          tier: 'HOT',
          label: 'HOT Tier (Redis)',
          throughputEps: 1523.4,
          latencyP99Ms: 2.8,
          errorRate: 0.002,
          queueDepth: 18,
          status: 'healthy',
          instanceCount: 3,
        },
        {
          tier: 'WARM',
          label: 'WARM Tier (PostgreSQL)',
          throughputEps: 845.2,
          latencyP99Ms: 10.3,
          errorRate: 0.003,
          queueDepth: 24,
          status: 'healthy',
          instanceCount: 2,
          storageGb: 62.4,
        },
        {
          tier: 'COOL',
          label: 'COOL Tier (Iceberg)',
          throughputEps: 234.1,
          latencyP99Ms: 58.7,
          errorRate: 0.004,
          queueDepth: 12,
          status: 'warning',
          instanceCount: 1,
          storageGb: 245.8,
        },
        {
          tier: 'COLD',
          label: 'COLD Tier (S3/Archive)',
          throughputEps: 78.3,
          latencyP99Ms: 245.2,
          errorRate: 0.001,
          queueDepth: 5,
          status: 'healthy',
          instanceCount: 1,
          storageGb: 612.3,
        },
      ],
      totalEventsPerSec: 2681.0,
      totalStorageGb: 920.5,
      lastUpdated: new Date().toISOString(),
    });
  }),

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
// Brain, learning, user activity, and lightweight observability handlers
// ---------------------------------------------------------------------------

const runtimeCapabilitySchema = {
  version: '1.0.0',
  metadata: {
    description: 'Runtime capability schema for Data Cloud UI gating and compatibility checks',
    last_updated: '2026-05-08',
    generators: ['msw-runtime-capability-schema'],
  },
  kernel_capabilities: [] as Array<Record<string, unknown>>,
  data_cloud_capabilities: [] as Array<Record<string, unknown>>,
  aep_capabilities: [] as Array<Record<string, unknown>>,
  ui_feature_gates: [] as Array<Record<string, unknown>>,
  status_definitions: {
    stable: {
      description: 'Production-ready with full support',
      ui_indicator: 'green',
      allowed_in_production: true,
    },
    preview: {
      description: 'Preview/demo-only, not production-ready',
      ui_indicator: 'amber',
      allowed_in_production: false,
    },
    deprecated: {
      description: 'Deprecated and scheduled for removal',
      ui_indicator: 'red',
      allowed_in_production: false,
    },
    experimental: {
      description: 'Experimental capability behavior',
      ui_indicator: 'purple',
      allowed_in_production: false,
    },
  },
};

const supportHandlers = [
  http.get(`${BASE}/surfaces`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      data: {
        capabilities: {},
        generatedAt: new Date().toISOString(),
      },
      meta: {
        requestId: 'req-surfaces-msw',
        tenantId: MOCK_TENANT_ID,
        timestamp: new Date().toISOString(),
        apiVersion: 'v1',
      },
    });
  }),

  // DC-P1.12: Removed /capabilities and /capabilities/schema handlers - compatibility aliases no longer supported
  // Use canonical /surfaces and /surfaces/schema endpoints only

  http.get(`${BASE}/surfaces/schema`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      data: runtimeCapabilitySchema,
      meta: {
        requestId: 'req-surfaces-schema-msw',
        tenantId: MOCK_TENANT_ID,
        timestamp: new Date().toISOString(),
        apiVersion: 'v1',
      },
    });
  }),

  // DC-P1.12: Removed /capabilities/schema handler - compatibility alias no longer supported
  // Use canonical /surfaces/schema endpoint only

  http.post(`${BASE}/analytics/query`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      queryId: 'query-msw-1',
      queryType: 'ANALYTICS',
      rowCount: 1,
      columnCount: 1,
      rows: [{ total: 42 }],
      executionTimeMs: 18,
      optimized: true,
      timestamp: new Date().toISOString(),
    });
  }),

  http.post(`${BASE}/queries/federated`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      queryId: 'query-fed-msw-1',
      queryType: 'FEDERATED_FALLBACK',
      rowCount: 1,
      columnCount: 1,
      rows: [{ region: 'global' }],
      executionTimeMs: 32,
      optimized: true,
      timestamp: new Date().toISOString(),
      warning: 'Trino not configured — query executed via local analytics engine.',
    });
  }),

  http.post(`${BASE}/analytics/suggest`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      data: {
        queries: [
          {
            name: 'Cache repeated revenue queries',
            template: 'SELECT date_trunc(\'day\', created_at) AS day, SUM(total) FROM revenue GROUP BY 1',
            explanation: 'Frequent revenue lookups can be served faster from a cached aggregate.',
          },
        ],
      },
      ai: {
        confidence: 0.88,
        fallback: false,
      },
    });
  }),

  http.get(`${BASE}/brain/workspace`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      status: 'active',
      brainId: 'brain-msw',
      note: 'Detailed spotlight items available via GET /api/v1/brain/stats',
      timestamp: new Date().toISOString(),
    });
  }),

  http.get(`${BASE}/brain/stats`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      totalRecordsProcessed: 482,
      activePatterns: 7,
      activeRules: 12,
      hotTierRecords: 43,
      warmTierRecords: 128,
      avgProcessingTimeMs: 18.4,
      uptimeSeconds: 86400,
      tenantId: MOCK_TENANT_ID,
      timestamp: new Date().toISOString(),
    });
  }),

  http.get(`${BASE}/autonomy/logs`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get('limit') ?? 10);
    const logs = Array.from({ length: Math.min(limit, 2) }, (_, index) => ({
      id: `autonomy-${index + 1}`,
      actionType: index === 0 ? 'quality' : 'optimization',
      tenantId: MOCK_TENANT_ID,
      level: index === 0 ? 'SUGGEST' : 'NOTIFY',
      decision: index === 0 ? 'ADVISORY' : 'ALLOWED',
      confidence: 0.84,
      context: { source: 'msw' },
      timestamp: new Date(Date.now() - index * 15 * 60 * 1000).toISOString(),
    }));

    return HttpResponse.json({
      logs,
      count: logs.length,
      globalOverride: 'NONE',
      timestamp: new Date().toISOString(),
    });
  }),

  http.get(`${BASE}/autonomy/domains/:domain`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    const domain = String(params.domain ?? 'optimization');
    return HttpResponse.json({
      domain,
      state: {
        actionType: domain,
        tenantId: MOCK_TENANT_ID,
        currentLevel: domain === 'security' ? 'SUGGEST' : 'NOTIFY',
        effectiveMaxLevel: 'AUTONOMOUS',
        confidence: 0.78,
        lastActionAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
      },
      timestamp: new Date().toISOString(),
    });
  }),

  http.get(`${BASE}/learning/status`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const tenantId = url.searchParams.get('tenantId');
    return HttpResponse.json({
      running: false,
      lastRunTime: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
      nextScheduledRun: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
      intervalMinutes: 60,
      pendingReviews: 2,
      lastResult: {
        status: 'COMPLETED',
        tenantId: tenantId ?? MOCK_TENANT_ID,
        manual: false,
        durationMs: 1200,
        patternsDiscovered: 3,
        patternsUpdated: 1,
        recordsAnalyzed: 24,
        ranAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
      },
      timestamp: new Date().toISOString(),
    });
  }),

  http.get(`${BASE}/user-activity/recent`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      activities: [
        {
          id: 'activity-1',
          action: 'navigate',
          target: 'Data Quality',
          timestamp: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
          type: 'query',
          resourceType: 'navigation',
          resourceId: '/data?view=quality',
        },
      ],
      continueWorking: [
        {
          id: 'continue-1',
          name: 'Customer Events',
          type: 'collection',
          lastAccessed: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
          path: '/data/col-001',
        },
      ],
    });
  }),

  http.post(`${BASE}/user-activity/log`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/executions/summary', async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({ running: 1, failed: 0 });
  }),

  http.get('/api/metrics/system', async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({ cpu: 32, memory: 58, latency: 14, throughput: 1200, activeConnections: 18, queueDepth: 2 });
  }),

  http.get(`${BASE}/metrics/system`, async () => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({ cpu: 32, memory: 58, throughput: 1200, latency: 14, activeConnections: 18, queueDepth: 2 });
  }),

  http.get(`${BASE}/executions`, async ({ request }) => {
    await delay(SIMULATED_DELAY_MS);
    const url = new URL(request.url);
    const statuses = (url.searchParams.get('status') ?? '').split(',').filter(Boolean);
    const executions = [
      {
        id: 'exec-1',
        pipelineId: 'wf-001',
        pipelineName: 'Nightly Customer Sync',
        status: 'running',
        startTime: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        progress: 62,
        nodesCurrent: 5,
        nodesTotal: 8,
      },
      {
        id: 'exec-2',
        pipelineId: 'wf-002',
        pipelineName: 'Governance Backfill',
        status: 'completed',
        startTime: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
        endTime: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
        duration: 1800,
      },
    ];

    return HttpResponse.json(
      statuses.length > 0
        ? executions.filter((execution) => statuses.includes(execution.status))
        : executions,
    );
  }),

  http.get(`${BASE}/executions/:executionId`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({
      id: params.executionId,
      pipelineId: 'wf-001',
      pipelineName: 'Nightly Customer Sync',
      status: 'completed',
      startTime: new Date(Date.now() - 60 * 1000).toISOString(),
      endTime: new Date().toISOString(),
      completedNodes: 2,
      totalNodes: 2,
      nodes: [
        { id: 'node-1', name: 'Start', status: 'completed', startTime: new Date(Date.now() - 60 * 1000).toISOString(), endTime: new Date(Date.now() - 55 * 1000).toISOString(), duration: 5 },
        { id: 'node-2', name: 'End', status: 'completed', startTime: new Date(Date.now() - 55 * 1000).toISOString(), endTime: new Date().toISOString(), duration: 55 },
      ],
    });
  }),

  http.get(`${BASE}/executions/:executionId/logs`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json([
      { timestamp: new Date(Date.now() - 60 * 1000).toISOString(), level: 'info', message: `Execution ${params.executionId} started` },
      { timestamp: new Date().toISOString(), level: 'info', message: `Execution ${params.executionId} completed` },
    ]);
  }),

  http.post(`${BASE}/executions/:executionId/cancel`, async ({ params }) => {
    await delay(SIMULATED_DELAY_MS);
    return HttpResponse.json({ executionId: params.executionId, cancelled: true });
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
  ...pluginHandlers,
  ...storageFabricHandlers,
  ...connectorHandlers,
  ...supportHandlers,
  ...aiHandlers,
];

/**
 * Reset in-memory stores to seed data. Call in beforeEach for isolated tests.
 */
export function resetMockData() {
  collections = [...mockCollections];
  workflows = [...mockWorkflows];
  plugins = [...mockPlugins];
  storageProfiles = [...mockStorageProfiles];
  connectors = [...mockConnectors];
}
