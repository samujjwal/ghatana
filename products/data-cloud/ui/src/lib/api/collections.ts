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
 * Collection entity
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
    sortBy?: 'name' | 'createdAt' | 'updatedAt' | 'entityCount';
    sortOrder?: 'asc' | 'desc';
}

/**
 * Collections API
 */
export const collectionsApi = {
    /**
     * List all collections
     */
    list: async (params?: CollectionQueryParams): Promise<PaginatedResponse<Collection>> => {
        return apiClient.get<PaginatedResponse<Collection>>('/collections', { params });
    },

    /**
     * Get collection by ID
     */
    get: async (id: string): Promise<Collection> => {
        return apiClient.get<Collection>(`/collections/${id}`);
    },

    /**
     * Create new collection
     */
    create: async (data: CreateCollectionDto): Promise<Collection> => {
        return apiClient.post<Collection, CreateCollectionDto>('/collections', data);
    },

    /**
     * Update collection
     */
    update: async (id: string, data: UpdateCollectionDto): Promise<Collection> => {
        return apiClient.put<Collection, UpdateCollectionDto>(`/collections/${id}`, data);
    },

    /**
     * Delete collection
     */
    delete: async (id: string): Promise<void> => {
        return apiClient.delete(`/collections/${id}`);
    },

    /**
     * Get collection schema
     */
    getSchema: async (id: string): Promise<Record<string, unknown>> => {
        return apiClient.get<Record<string, unknown>>(`/collections/${id}/schema`);
    },

    /**
     * Update collection schema
     */
    updateSchema: async (id: string, schema: Record<string, unknown>): Promise<Collection> => {
        return apiClient.put<Collection>(`/collections/${id}/schema`, schema);
    },

    /**
     * Get collection stats
     */
    getStats: async (id: string): Promise<{
        entityCount: number;
        storageSize: number;
        lastUpdated: string;
    }> => {
        return apiClient.get(`/collections/${id}/stats`);
    },
};

export default collectionsApi;
