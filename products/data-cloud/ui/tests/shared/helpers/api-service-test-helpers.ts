/**
 * Test Helpers for Data Cloud API Service Layer
 *
 * Centralized mock factories and fixtures to prevent test duplication across
 * all React/TypeScript test suites. All component tests, integration tests, and
 * E2E tests import these helpers.
 *
 * @doc.type module
 * @doc.purpose Centralized API mocks and fixtures (REUSABLE, NO DUPLICATION)
 * @doc.layer product
 *
 * Usage:
 * ```
 * import { createMockEntity, createMockPipeline, mockApiService } from '@tests/shared/helpers';
 *
 * @Test
 * void myTest() {
 *   const entity = createMockEntity({ name: 'Widget' });
 *   const resp = mockApiService.entities.save(entity);
 *   expect(resp).toEqual({ id: '...', collection: 'products' });
 * }
 * ```
 */

import { z } from 'zod';
import { vi } from 'vitest';

// ─────────────────────────────────────────────────────────────────────────
// Entity Fixtures
// ─────────────────────────────────────────────────────────────────────────

export interface Entity {
  id: string;
  collection: string;
  data: Record<string, unknown>;
  tenantId: string;
  createdBy: string;
  createdAt: string;
  updated?: string;
}

/**
 * Create a mock entity with sensible defaults.
 * Override any field via the optional @param overrides argument.
 */
export function createMockEntity(overrides: Partial<Entity> = {}): Entity {
  const id = overrides.id ?? 'ent-' + Math.random().toString(36).substr(2, 9);
  return {
    id,
    collection: overrides.collection ?? 'products',
    data: overrides.data ?? { name: 'Test Widget', price: 9.99 },
    tenantId: overrides.tenantId ?? 'tenant-default',
    createdBy: overrides.createdBy ?? 'test-user',
    createdAt: overrides.createdAt ?? new Date().toISOString(),
    updated: overrides.updated,
    ...overrides,
  };
}

/**
 * Create multiple mock entities (bulk fixture).
 */
export function createMockEntities(count: number, overrides?: Partial<Entity>): Entity[] {
  return Array.from({ length: count }, (_, i) =>
    createMockEntity({ ...overrides, id: `ent-${i}` })
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Pipeline Fixtures
// ─────────────────────────────────────────────────────────────────────────

export interface Pipeline {
  id: string;
  name: string;
  description?: string;
  status: 'draft' | 'active' | 'paused' | 'archived';
  tenantId: string;
  createdAt: string;
  version: number;
}

/**
 * Create a mock pipeline with sensible defaults.
 */
export function createMockPipeline(overrides: Partial<Pipeline> = {}): Pipeline {
  const id = overrides.id ?? 'pipeline-' + Math.random().toString(36).substr(2, 9);
  return {
    id,
    name: overrides.name ?? 'Default ETL Pipeline',
    description: overrides.description,
    status: overrides.status ?? 'draft',
    tenantId: overrides.tenantId ?? 'tenant-default',
    createdAt: overrides.createdAt ?? new Date().toISOString(),
    version: overrides.version ?? 1,
    ...overrides,
  };
}

/**
 * Create multiple mock pipelines (bulk fixture).
 */
export function createMockPipelines(count: number, overrides?: Partial<Pipeline>): Pipeline[] {
  return Array.from({ length: count }, (_, i) =>
    createMockPipeline({ ...overrides, id: `pipeline-${i}` })
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Event Fixtures
// ─────────────────────────────────────────────────────────────────────────

export interface Event {
  offset: number;
  type: string;
  data: Record<string, unknown>;
  timestamp: string;
  tenantId: string;
}

/**
 * Create a mock event (append-log style).
 */
export function createMockEvent(overrides: Partial<Event> = {}): Event {
  return {
    offset: overrides.offset ?? 1,
    type: overrides.type ?? 'ENTITY_CREATED',
    data: overrides.data ?? { entityId: 'ent-1', collection: 'products' },
    timestamp: overrides.timestamp ?? new Date().toISOString(),
    tenantId: overrides.tenantId ?? 'tenant-default',
    ...overrides,
  };
}

/**
 * Create multiple mock events (stream fixture).
 */
export function createMockEvents(count: number, overrides?: Partial<Event>): Event[] {
  return Array.from({ length: count }, (_, i) =>
    createMockEvent({ ...overrides, offset: i })
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Memory & Brain Fixtures
// ─────────────────────────────────────────────────────────────────────────

export interface MemoryEntry {
  id: string;
  agentId: string;
  tier: 'episodic' | 'semantic' | 'procedural';
  content: string;
  salience: number;
  tenantId: string;
  createdAt: string;
}

/**
 * Create a mock memory entry (agent memory fixture).
 */
export function createMockMemoryEntry(overrides: Partial<MemoryEntry> = {}): MemoryEntry {
  return {
    id: overrides.id ?? 'mem-' + Math.random().toString(36).substr(2, 9),
    agentId: overrides.agentId ?? 'agent-ai-001',
    tier: overrides.tier ?? 'episodic',
    content: overrides.content ?? 'Test memory content',
    salience: overrides.salience ?? 0.8,
    tenantId: overrides.tenantId ?? 'tenant-default',
    createdAt: overrides.createdAt ?? new Date().toISOString(),
    ...overrides,
  };
}

// ─────────────────────────────────────────────────────────────────────────
// API Service Mocks (Vitest)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Centralized mock API service.
 * Use this in all component tests to ensure consistent mocking patterns.
 *
 * Example:
 * ```
 * const { data: entities } = mockApiService.entities.list();
 * expect(entities).toHaveLength(0);
 * ```
 */
export const mockApiService = {
  // Entity CRUD
  entities: {
    save: vi.fn((entity: Entity) => Promise.resolve({ ...entity, id: 'ent-123' })),
    get: vi.fn((collection: string, id: string) =>
      Promise.resolve(createMockEntity({ collection, id }))
    ),
    list: vi.fn((collection: string) =>
      Promise.resolve(createMockEntities(3, { collection }))
    ),
    delete: vi.fn(() => Promise.resolve(undefined)),
    query: vi.fn((collection: string, filter: unknown) =>
      Promise.resolve(createMockEntities(2, { collection }))
    ),
  },

  // Pipeline Operations
  pipelines: {
    create: vi.fn((pipeline: Pipeline) =>
      Promise.resolve({ ...pipeline, id: 'pipeline-123' })
    ),
    get: vi.fn((id: string) => Promise.resolve(createMockPipeline({ id }))),
    list: vi.fn(() => Promise.resolve(createMockPipelines(3))),
    update: vi.fn((id: string, updates: Partial<Pipeline>) =>
      Promise.resolve(createMockPipeline({ id, ...updates }))
    ),
    delete: vi.fn(() => Promise.resolve(undefined)),
    execute: vi.fn((id: string) =>
      Promise.resolve({ pipelineId: id, status: 'running' })
    ),
  },

  // Event Stream
  events: {
    append: vi.fn((data: Record<string, unknown>) =>
      Promise.resolve({ offset: 1, type: 'ENTITY_CREATED' })
    ),
    read: vi.fn((fromOffset: number) =>
      Promise.resolve(createMockEvents(5, { offset: fromOffset }))
    ),
    stream: vi.fn((fromOffset: number) =>
      Promise.resolve(createMockEvents(10, { offset: fromOffset }))
    ),
  },

  // Brain/AI Interface
  brain: {
    query: vi.fn((question: string) =>
      Promise.resolve({ answer: 'Mock AI response', confidence: 0.95 })
    ),
    learn: vi.fn((data: unknown) => Promise.resolve({ learned: true })),
    explain: vi.fn((decision: unknown) =>
      Promise.resolve({ explanation: 'Mock explanation' })
    ),
  },

  // Memory Management
  memory: {
    store: vi.fn((entry: MemoryEntry) =>
      Promise.resolve({ ...entry, id: 'mem-123' })
    ),
    retrieve: vi.fn((agentId: string, tier: string) =>
      Promise.resolve(createMockMemoryEntry({ agentId, tier }))
    ),
    list: vi.fn(() => Promise.resolve([createMockMemoryEntry()])),
    forget: vi.fn(() => Promise.resolve(undefined)),
  },

  // Analytics
  analytics: {
    query: vi.fn((metric: string) =>
      Promise.resolve({ metric, value: 42, timestamp: new Date().toISOString() })
    ),
    report: vi.fn((type: string) =>
      Promise.resolve({ type, data: {}, generatedAt: new Date().toISOString() })
    ),
  },

  // Health & Status
  health: {
    check: vi.fn(() =>
      Promise.resolve({
        status: 'healthy',
        timestamp: new Date().toISOString(),
        uptime: 3600,
      })
    ),
  },
};

// ─────────────────────────────────────────────────────────────────────────
// Request/Response Validators (Zod Schemas)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Schema: Create Entity Request
 * Used for input validation in tests and stories.
 */
export const CreateEntityRequestSchema = z.object({
  collection: z.string().min(1),
  data: z.record(z.unknown()),
  tenantId: z.string().optional(),
});

export type CreateEntityRequest = z.infer<typeof CreateEntityRequestSchema>;

/**
 * Schema: Create Pipeline Request
 */
export const CreatePipelineRequestSchema = z.object({
  name: z.string().min(1).max(100),
  description: z.string().optional(),
  status: z.enum(['draft', 'active', 'paused', 'archived']).default('draft'),
});

export type CreatePipelineRequest = z.infer<typeof CreatePipelineRequestSchema>;

/**
 * Schema: Append Event Request
 */
export const AppendEventRequestSchema = z.object({
  type: z.string(),
  data: z.record(z.unknown()),
});

export type AppendEventRequest = z.infer<typeof AppendEventRequestSchema>;

// ─────────────────────────────────────────────────────────────────────────
// Query Builders (composable test assertion helpers)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Build a query filter for entity searches.
 * Example: buildEntityQuery().where('price', '>', 10).and('status', 'active')
 */
export function buildEntityQuery() {
  const conditions: Array<{ field: string; operator: string; value: unknown }> = [];

  return {
    where: (field: string, operator: string, value: unknown) => {
      conditions.push({ field, operator, value });
      return buildEntityQuery();
    },
    and: (field: string, operator: string, value: unknown) => {
      conditions.push({ field, operator, value });
      return buildEntityQuery();
    },
    build: () => ({ filters: conditions }),
  };
}

/**
 * Build a mock response for testing error scenarios.
 */
export function buildErrorResponse(status: number, message: string) {
  return {
    status,
    error: message,
    timestamp: new Date().toISOString(),
  };
}

// ─────────────────────────────────────────────────────────────────────────
// Test Data Constants (no duplication)
// ─────────────────────────────────────────────────────────────────────────

export const TEST_DATA = {
  // Authentication
  VALID_TOKEN: 'test-token-valid',
  INVALID_TOKEN: 'test-token-invalid',

  // Tenants
  TENANT_DEFAULT: 'tenant-default',
  TENANT_ALPHA: 'tenant-alpha',
  TENANT_BETA: 'tenant-beta',

  // Collections
  COLLECTION_PRODUCTS: 'products',
  COLLECTION_CUSTOMERS: 'customers',

  // Users
  USER_ADMIN: 'user-admin',
  USER_REGULAR: 'user-regular',

  // HTTP Status
  HTTP_OK: 200,
  HTTP_CREATED: 201,
  HTTP_BAD_REQUEST: 400,
  HTTP_UNAUTHORIZED: 401,
  HTTP_FORBIDDEN: 403,
  HTTP_NOT_FOUND: 404,
  HTTP_CONFLICT: 409,
  HTTP_INTERNAL_ERROR: 500,
};

// ─────────────────────────────────────────────────────────────────────────
// Reset Mocks Helper
// ─────────────────────────────────────────────────────────────────────────

/**
 * Reset all mocks before each test.
 * Call this in your test setup:
 * @beforeEach(() => resetAllMocks());
 */
export function resetAllMocks(): void {
  vi.clearAllMocks();
  // Reset all mock function responses to default if needed
  Object.values(mockApiService).forEach((section) => {
    if (typeof section === 'object') {
      Object.values(section).forEach((fn) => {
        if (typeof fn === 'function' && 'mockReset' in fn) {
          (fn as { mockReset: () => void }).mockReset();
        }
      });
    }
  });
}
