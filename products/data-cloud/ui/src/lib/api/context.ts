/**
 * Context Layer API Client (P3.1.2)
 *
 * Typed client for the tenant-scoped runtime context layer endpoints:
 * - GET    /api/v1/context          — fetch all context entries for the tenant
 * - PUT    /api/v1/context          — upsert one or many context entries
 * - DELETE /api/v1/context/keys/:key — remove a single context entry by key
 * - GET    /api/v1/context/snapshot  — full versioned context snapshot
 *
 * @doc.type api-client
 * @doc.purpose Tenant-scoped runtime key-value context layer client
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { apiClient } from './client';
import {
    CollectionContextResponseSchema,
    ContextResponseSchema,
    ContextSnapshotSchema,
    UpsertContextRequestSchema,
    UpsertContextResponseSchema,
    type CollectionContextResponse,
    type ContextResponse,
    type ContextSnapshot,
    type UpsertContextRequest,
    type UpsertContextResponse,
} from '../../contracts/schemas';

export type {
    CollectionContextResponse,
    ContextResponse,
    ContextSnapshot,
    UpsertContextResponse,
} from '../../contracts/schemas';

// ─── Types ────────────────────────────────────────────────────────────────────

/**
 * A map of arbitrary context key-value entries.
 * Values may be any JSON-compatible type.
 */
export type ContextEntries = Record<string, unknown>;

export interface CollectionContextOptions {
    depth?: number;
}

// ─── Client functions ─────────────────────────────────────────────────────────

/**
 * Fetches all context entries for the current tenant.
 *
 * @returns Resolved entries, version, and tenant metadata.
 */
export async function getContext(): Promise<ContextResponse> {
    const response = await apiClient.get<ContextResponse>('/context');
    return ContextResponseSchema.parse(response);
}

/**
 * Fetches the unified collection-scoped context document.
 *
 * @param collection - Logical collection name.
 * @returns Live schema, lineage, governance, freshness, and statistical profile.
 */
export async function getCollectionContext(
    collection: string,
    options?: CollectionContextOptions,
): Promise<CollectionContextResponse> {
    const response = await apiClient.get<CollectionContextResponse>(`/context/${encodeURIComponent(collection)}`, {
        params: options?.depth ? { depth: options.depth } : undefined,
    });
    return CollectionContextResponseSchema.parse(response);
}

/**
 * Upserts one or many context entries for the current tenant.
 *
 * @param entries - Key-value pairs to insert or update.
 * @returns Upsert result including the new write version.
 */
export async function putContextEntries(entries: ContextEntries): Promise<UpsertContextResponse> {
    const body = UpsertContextRequestSchema.parse({ entries });
    const response = await apiClient.put<UpsertContextResponse, UpsertContextRequest>('/context', body);
    return UpsertContextResponseSchema.parse(response);
}

/**
 * Removes a single context entry identified by {@code key}.
 *
 * @param key - The context entry key to remove.
 * @returns Resolves on success (HTTP 204); rejects with an API error on failure.
 */
export async function deleteContextKey(key: string): Promise<void> {
    return apiClient.delete<void>(`/context/keys/${encodeURIComponent(key)}`);
}

/**
 * Returns a complete, versioned snapshot of the current tenant's context.
 *
 * Useful for audit trails, client-side caching invalidation checks, and
 * cross-service context propagation.
 *
 * @returns Full context snapshot with version, counts, and timestamps.
 */
export async function getContextSnapshot(): Promise<ContextSnapshot> {
    const response = await apiClient.get<ContextSnapshot>('/context/snapshot');
    return ContextSnapshotSchema.parse(response);
}
