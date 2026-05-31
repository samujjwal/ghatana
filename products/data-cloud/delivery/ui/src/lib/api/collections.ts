/**
 * Collections API
 *
 * API endpoints for first-class collection management.
 * Uses canonical /api/v1/collections endpoints (P3.1).
 *
 * @doc.type service
 * @doc.purpose Collections API endpoints
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { apiClient, PaginatedResponse } from "./client";

// ---------------------------------------------------------------------------
// First-class Collection contract (P3.1)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Transformation helpers for canonical Collection contract (P3.1)
// ---------------------------------------------------------------------------

const collectionSchemaTypes = [
  "entity",
  "event",
  "timeseries",
  "graph",
  "document",
] as const;
const collectionStatuses = [
  "active",
  "draft",
  "archived",
  "processing",
] as const;
const lifecycleStatuses = [
  "DRAFT",
  "PUBLISHED",
  "DEPRECATED",
  "ARCHIVED",
  "UNKNOWN",
] as const;
const operationalStatuses = [
  "healthy",
  "degraded",
  "unavailable",
  "maintenance",
  "unknown",
] as const;

function toStringOrDefault(value: unknown, defaultValue: string): string {
  return typeof value === "string" ? value : defaultValue;
}

function toFiniteNumberOrDefault(value: unknown, defaultValue: number): number {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : defaultValue;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : defaultValue;
  }
  return defaultValue;
}

function toFiniteNumberOrUndefined(value: unknown): number | undefined {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : undefined;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string");
}

function normalizeEnumValue<T extends readonly string[]>(
  value: unknown,
  allowedValues: T,
  fallbackValue: T[number],
): T[number] {
  return typeof value === "string" && allowedValues.includes(value as T[number])
    ? (value as T[number])
    : fallbackValue;
}

function normalizeQualityMetrics(
  value: unknown,
): Record<string, number> | undefined {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return undefined;
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .map(([key, metric]) => [key, toFiniteNumberOrUndefined(metric)] as const)
    .filter(
      (entry): entry is readonly [string, number] =>
        typeof entry[1] === "number",
    );
  if (entries.length === 0) {
    return undefined;
  }
  return Object.fromEntries(entries);
}

// Transform canonical Collection contract to UI model
function canonicalToCollection(data: Record<string, unknown>): Collection {
  const schema = data.schema as CollectionSchema | undefined;
  const storageSizeRaw =
    data.storageSizeBytes ?? data.storageBytes ?? data.storageSize ?? null;
  const storageSizeBytes = toFiniteNumberOrUndefined(storageSizeRaw) ?? null;

  return {
    id: toStringOrDefault(data.id, ""),
    name: toStringOrDefault(data.name, ""),
    description: toStringOrDefault(data.description, ""),
    schemaType: normalizeEnumValue(
      data.schemaType,
      collectionSchemaTypes,
      "entity",
    ),
    status: normalizeEnumValue(data.status, collectionStatuses, "draft"),
    isActive: Boolean(data.isActive ?? data.status === "active"),
    entityCount: toFiniteNumberOrDefault(data.entityCount, 0),
    schema: schema ?? { fields: [] },
    tags: toStringArray(data.tags),
    createdAt: toStringOrDefault(data.createdAt, new Date().toISOString()),
    updatedAt: toStringOrDefault(data.updatedAt, new Date().toISOString()),
    createdBy: toStringOrDefault(data.createdBy, "unknown"),
    // First-class collection registry fields (P3.1)
    lifecycleStatus: normalizeEnumValue(
      data.lifecycleStatus,
      lifecycleStatuses,
      "UNKNOWN",
    ),
    operationalStatus: normalizeEnumValue(
      data.operationalStatus,
      operationalStatuses,
      "unknown",
    ),
    qualityScore: toFiniteNumberOrUndefined(data.qualityScore),
    qualityMetrics: normalizeQualityMetrics(data.qualityMetrics),
    retentionPolicy: data.retentionPolicy as
      | Record<string, unknown>
      | undefined,
    lineage: data.lineage as Record<string, unknown> | undefined,
    owner: toStringOrDefault(data.owner ?? data.createdBy, "unknown"),
    storageSizeBytes: Number.isFinite(storageSizeBytes)
      ? (storageSizeBytes as number)
      : undefined,
  };
}

export interface CollectionSchemaField {
  id?: string;
  name: string;
  type: string;
  required?: boolean;
  description?: string;
  [key: string]: unknown;
}

export interface CollectionSchema {
  id?: string;
  name?: string;
  fields: CollectionSchemaField[];
  constraints?: Array<Record<string, unknown>>;
  [key: string]: unknown;
}

/**
 * Collection entity with first-class registry metadata (P0.2).
 */
export interface Collection {
  id: string;
  name: string;
  description: string;
  schemaType: "entity" | "event" | "timeseries" | "graph" | "document";
  status: "active" | "draft" | "archived" | "processing";
  isActive?: boolean;
  entityCount: number;
  schema: CollectionSchema;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  // Registry metadata (P0.2)
  lifecycleStatus:
    | "DRAFT"
    | "PUBLISHED"
    | "DEPRECATED"
    | "ARCHIVED"
    | "UNKNOWN";
  operationalStatus:
    | "healthy"
    | "degraded"
    | "unavailable"
    | "maintenance"
    | "unknown";
  qualityScore?: number;
  qualityMetrics?: Record<string, number>;
  retentionPolicy?: Record<string, unknown>;
  lineage?: Record<string, unknown>;
  owner: string;
  storageSizeBytes?: number;
}

/**
 * Create collection DTO
 */
export interface CreateCollectionDto {
  name: string;
  description: string;
  schemaType: Collection["schemaType"];
  schema: CollectionSchema;
  tags?: string[];
}

/**
 * Update collection DTO
 */
export interface UpdateCollectionDto {
  name?: string;
  description?: string;
  schema?: CollectionSchema;
  tags?: string[];
  status?: Collection["status"];
}

/**
 * Collection query params
 */
export interface CollectionQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  status?: Collection["status"];
  schemaType?: Collection["schemaType"];
  sortBy?: "name" | "createdAt" | "updatedAt" | "entityCount" | "qualityScore";
  sortOrder?: "asc" | "desc";
}

/**
 * Collections API
 *
 * Routes backed by canonical /api/v1/collections endpoints (P3.1).
 */
export const collectionsApi = {
  /**
   * List all collections.
   * GET /api/v1/collections
   */
  list: async (
    params?: CollectionQueryParams,
  ): Promise<PaginatedResponse<Collection>> => {
    const queryParams: Record<string, unknown> = {};
    if (params?.search) queryParams.search = params.search;
    if (params?.status) queryParams.status = params.status;
    if (params?.schemaType) queryParams.schemaType = params.schemaType;
    if (params?.sortBy) queryParams.sortBy = params.sortBy;
    if (params?.sortOrder) queryParams.sortOrder = params.sortOrder;

    const rawResponse = await apiClient.get<{
      collections: Record<string, unknown>[];
      tenantId: string;
      count: number;
    }>("/collections", {
      params: queryParams,
    });
    const items = (rawResponse.collections ?? []).map(canonicalToCollection);
    const page = params?.page ?? 1;
    const pageSize = params?.pageSize ?? 50;
    const offset = (page - 1) * pageSize;
    return {
      items,
      total: rawResponse.count ?? items.length,
      page,
      pageSize,
      hasMore: offset + items.length < (rawResponse.count ?? items.length),
    };
  },

  /**
   * Get collection by ID.
   * GET /api/v1/collections/:id
   */
  get: async (id: string): Promise<Collection> => {
    const rawResponse = await apiClient.get<Record<string, unknown>>(
      `/collections/${id}`,
    );
    return canonicalToCollection(rawResponse);
  },

  /**
   * Create new collection.
   * POST /api/v1/collections
   */
  create: async (data: CreateCollectionDto): Promise<Collection> => {
    const saved = await apiClient.post<Record<string, unknown>>(
      "/collections",
      {
        ...data,
        lifecycleStatus: "DRAFT",
        operationalStatus: "healthy",
      } as Record<string, unknown>,
    );
    return canonicalToCollection(saved);
  },

  /**
   * Update collection.
   * PUT /api/v1/collections/:id
   */
  update: async (
    id: string,
    data: UpdateCollectionDto,
  ): Promise<Collection> => {
    const updated = await apiClient.put<Record<string, unknown>>(
      `/collections/${id}`,
      data as Record<string, unknown>,
    );
    return canonicalToCollection(updated);
  },

  /**
   * Delete collection.
   * DELETE /api/v1/collections/:id
   */
  delete: async (id: string): Promise<void> => {
    return apiClient.delete(`/collections/${id}`);
  },

  /**
   * Publish collection (DRAFT → PUBLISHED).
   * POST /api/v1/collections/:id/publish
   */
  publish: async (id: string): Promise<Collection> => {
    const updated = await apiClient.post<Record<string, unknown>>(
      `/collections/${id}/publish`,
      {},
    );
    return canonicalToCollection(updated);
  },

  /**
   * Deprecate collection (PUBLISHED → DEPRECATED).
   * POST /api/v1/collections/:id/deprecate
   */
  deprecate: async (id: string): Promise<Collection> => {
    const updated = await apiClient.post<Record<string, unknown>>(
      `/collections/${id}/deprecate`,
      {},
    );
    return canonicalToCollection(updated);
  },

  /**
   * Archive collection (DEPRECATED → ARCHIVED).
   * POST /api/v1/collections/:id/archive
   */
  archive: async (id: string): Promise<Collection> => {
    const updated = await apiClient.post<Record<string, unknown>>(
      `/collections/${id}/archive`,
      {},
    );
    return canonicalToCollection(updated);
  },

  /**
   * Get collection schema.
   */
  getSchema: async (id: string): Promise<Record<string, unknown>> => {
    const col = await collectionsApi.get(id);
    return col.schema as Record<string, unknown>;
  },

  /**
   * Update collection schema.
   */
  updateSchema: async (
    id: string,
    schema: CollectionSchema,
  ): Promise<Collection> => {
    return collectionsApi.update(id, { schema });
  },

  /**
   * Get collection stats.
   */
  getStats: async (
    id: string,
  ): Promise<{
    entityCount: number;
    storageSize: number | null;
    lastUpdated: string;
  }> => {
    const col = await collectionsApi.get(id);
    return {
      entityCount: col.entityCount,
      storageSize: col.storageSizeBytes ?? null,
      lastUpdated: col.updatedAt,
    };
  },
};

export default collectionsApi;
