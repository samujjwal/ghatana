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

// ─── Types ────────────────────────────────────────────────────────────────────

/**
 * A map of arbitrary context key-value entries.
 * Values may be any JSON-compatible type.
 */
export type ContextEntries = Record<string, unknown>;

/**
 * Response returned by GET /api/v1/context.
 */
export interface ContextResponse {
    /** Tenant identifier owning this context. */
    tenantId: string;
    /** All current context entries for the tenant. */
    entries: ContextEntries;
    /** Number of entries currently stored. */
    count: number;
    /** Current write version, incremented on every upsert or delete. */
    version: number;
    /** Correlation/request ID for tracing. */
    requestId: string;
}

/**
 * Request body for PUT /api/v1/context.
 *
 * Use the {@code entries} wrapper for explicit semantics:
 * ```ts
 * { entries: { "feature.dark-mode": true, locale: "en-US" } }
 * ```
 * A flat object is also accepted and treated as entries wholesale.
 */
export interface UpsertContextRequest {
    entries: ContextEntries;
}

/**
 * Response returned by PUT /api/v1/context.
 */
export interface UpsertContextResponse {
    /** Tenant identifier owning this context. */
    tenantId: string;
    /** Number of entries that were inserted or updated. */
    upserted: number;
    /** New write version after the upsert. */
    version: number;
    /** ISO-8601 timestamp of the update. */
    updatedAt: string;
    /** Correlation/request ID for tracing. */
    requestId: string;
}

/**
 * Versioned snapshot returned by GET /api/v1/context/snapshot.
 */
export interface ContextSnapshot {
    /** Tenant identifier owning this snapshot. */
    tenantId: string;
    /** Monotonically increasing version — incremented by every write. */
    version: number;
    /** Number of entries in the snapshot. */
    count: number;
    /** ISO-8601 timestamp when context was first created for this tenant. */
    createdAt: string;
    /** ISO-8601 timestamp when this snapshot was generated. */
    snapshotAt: string;
    /** All entries captured at {@code snapshotAt}. */
    entries: ContextEntries;
    /** Correlation/request ID for tracing. */
    requestId: string;
}

// ─── Client functions ─────────────────────────────────────────────────────────

/**
 * Fetches all context entries for the current tenant.
 *
 * @returns Resolved entries, version, and tenant metadata.
 */
export async function getContext(): Promise<ContextResponse> {
    return apiClient.get<ContextResponse>('/context');
}

/**
 * Upserts one or many context entries for the current tenant.
 *
 * @param entries - Key-value pairs to insert or update.
 * @returns Upsert result including the new write version.
 */
export async function putContextEntries(entries: ContextEntries): Promise<UpsertContextResponse> {
    const body: UpsertContextRequest = { entries };
    return apiClient.put<UpsertContextResponse, UpsertContextRequest>('/context', body);
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
    return apiClient.get<ContextSnapshot>('/context/snapshot');
}
