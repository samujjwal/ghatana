/**
 * Collections API
 * 
 * API endpoints for collection management.
 * 
 * @doc.type service
 * @doc.purpose Collections API endpoints
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { apiClient, PaginatedResponse } from './client';
import {
    CollectionEntityListResponseSchema,
    CollectionEntitySchema,
    type CollectionEntity as BackendEntity,
    type CollectionEntityListResponse as BackendEntityListResponse,
} from '../../contracts/schemas';

// ---------------------------------------------------------------------------
// Backend entity response shapes (raw responses from /api/v1/entities/…)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Transformation helpers
// ---------------------------------------------------------------------------

function entityToCollection(e: BackendEntity): Collection {
    const d = e.data as Record<string, unknown>;
    const schema = d.schema as CollectionSchema | undefined;
    const storageSizeRaw =
        d.storageSizeBytes ??
        d.storageBytes ??
        d.storageSize ??
        d.statsStorageBytes ??
        null;
    const storageSizeBytes =
        typeof storageSizeRaw === 'number'
            ? storageSizeRaw
            : typeof storageSizeRaw === 'string' && storageSizeRaw.trim().length > 0
                ? Number(storageSizeRaw)
                : null;
    return {
        id: e.id,
        name: String(d.name ?? ''),
        description: String(d.description ?? ''),
        schemaType: (d.schemaType ?? 'entity') as Collection['schemaType'],
        status: (d.status ?? 'draft') as Collection['status'],
        isActive: Boolean(d.isActive ?? (d.status === 'active')),
        entityCount: Number(d.entityCount ?? 0),
        schema: schema ?? { fields: [] },
        tags: Array.isArray(d.tags) ? (d.tags as string[]) : [],
        createdAt: e.createdAt ?? String(d.createdAt ?? new Date().toISOString()),
        updatedAt: e.updatedAt ?? String(d.updatedAt ?? new Date().toISOString()),
        createdBy: String(d.createdBy ?? 'unknown'),
        // P0.2 first-class collection registry fields
        lifecycleStatus: (d.lifecycleStatus ?? 'DRAFT') as Collection['lifecycleStatus'],
        operationalStatus: (d.operationalStatus ?? 'unknown') as Collection['operationalStatus'],
        qualityScore: d.qualityScore != null ? Number(d.qualityScore) : undefined,
        qualityMetrics: d.qualityMetrics as Record<string, number> | undefined,
        retentionPolicy: d.retentionPolicy as Record<string, unknown> | undefined,
        lineage: d.lineage as Record<string, unknown> | undefined,
        owner: String(d.owner ?? d.createdBy ?? 'unknown'),
        storageSizeBytes: Number.isFinite(storageSizeBytes) ? (storageSizeBytes as number) : undefined,
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
    schemaType: 'entity' | 'event' | 'timeseries' | 'graph' | 'document';
    status: 'active' | 'draft' | 'archived' | 'processing';
    isActive?: boolean;
    entityCount: number;
    schema: CollectionSchema;
    tags: string[];
    createdAt: string;
    updatedAt: string;
    createdBy: string;
    // Registry metadata (P0.2)
    lifecycleStatus: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED' | 'UNKNOWN';
    operationalStatus: 'healthy' | 'degraded' | 'unavailable' | 'maintenance' | 'unknown';
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
    schemaType: Collection['schemaType'];
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
    status?: Collection['status'];
}

/**
 * Collection query params
 */
export interface CollectionQueryParams {
    page?: number;
    pageSize?: number;
    search?: string;
    status?: Collection['status'];
    schemaType?: Collection['schemaType'];
    sortBy?: 'name' | 'createdAt' | 'updatedAt' | 'entityCount' | 'qualityScore';
    sortOrder?: 'asc' | 'desc';
}

/**
 * Collections API
 *
 * Routes now backed by /api/v1/entities/dc_collections (Option A,
 * DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 2).
 */
export const collectionsApi = {
    /**
     * List all collections.
     * GET /api/v1/entities/dc_collections
     */
    list: async (params?: CollectionQueryParams): Promise<PaginatedResponse<Collection>> => {
        const limit = params?.pageSize ?? 50;
        const offset = ((params?.page ?? 1) - 1) * limit;
        const queryParams: Record<string, unknown> = { limit, offset };
        if (params?.search) queryParams.search = params.search;
        if (params?.status) queryParams.status = params.status;
        if (params?.schemaType) queryParams.schemaType = params.schemaType;
        if (params?.sortBy) queryParams.sortBy = params.sortBy;
        if (params?.sortOrder) queryParams.sortOrder = params.sortOrder;
        
        const rawResponse = await apiClient.get<BackendEntityListResponse>('/entities/dc_collections', {
            params: queryParams,
        });
        const raw = CollectionEntityListResponseSchema.parse(rawResponse);
        const items = (raw.entities ?? []).map(entityToCollection);
        const page = params?.page ?? 1;
        return {
            items,
            total: raw.count ?? items.length,
            page,
            pageSize: limit,
            hasMore: offset + items.length < (raw.count ?? items.length),
        };
    },

    /**
     * Get collection by ID.
     * GET /api/v1/entities/dc_collections/:id
     */
    get: async (id: string): Promise<Collection> => {
        const rawResponse = await apiClient.get<BackendEntity>(`/entities/dc_collections/${id}`);
        const raw = CollectionEntitySchema.parse(rawResponse);
        return entityToCollection(raw);
    },

    /**
     * Create new collection.
     * POST /api/v1/entities/dc_collections
     */
    create: async (data: CreateCollectionDto): Promise<Collection> => {
        // The backend entity save returns {id, collection, version, createdAt, timestamp}
        const saved = await apiClient.post<{ id: string; collection: string; createdAt: string; timestamp: string }>(
            '/entities/dc_collections',
            { ...data } as Record<string, unknown>
        );
        return collectionsApi.get(saved.id);
    },

    /**
     * Update collection (upsert via POST with existing ID in payload).
     * POST /api/v1/entities/dc_collections
     */
    update: async (id: string, data: UpdateCollectionDto): Promise<Collection> => {
        await apiClient.post('/entities/dc_collections', { id, ...data } as Record<string, unknown>);
        return collectionsApi.get(id);
    },

    /**
     * Delete collection.
     * DELETE /api/v1/entities/dc_collections/:id
     */
    delete: async (id: string): Promise<void> => {
        return apiClient.delete(`/entities/dc_collections/${id}`);
    },

    /**
     * Get collection schema (stored in the collection entity data.schema field).
     */
    getSchema: async (id: string): Promise<Record<string, unknown>> => {
        const col = await collectionsApi.get(id);
        return col.schema as Record<string, unknown>;
    },

    /**
     * Update collection schema via upsert.
     */
    updateSchema: async (id: string, schema: CollectionSchema): Promise<Collection> => {
        return collectionsApi.update(id, { schema });
    },

    /**
     * Get collection entity count from the collection metadata entity.
     */
    getStats: async (id: string): Promise<{
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
