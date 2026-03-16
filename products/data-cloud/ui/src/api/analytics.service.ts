/**
 * Analytics Service
 *
 * Client for the DC analytics query engine endpoints (DC-9).
 * Supports synchronous SQL query submission and result retrieval,
 * plus TanStack Query hooks for reactive integration with InsightsPage.
 *
 * @doc.type service
 * @doc.purpose Analytics SQL query execution client + React Query hooks
 * @doc.layer frontend
 */

import { useQuery, useMutation } from '@tanstack/react-query';

const API_BASE = '/api/v1';

function getTenantId(): string {
  return localStorage.getItem('tenantId') || 'default-tenant';
}

export interface QueryResultData {
  queryId: string;
  queryType: string;
  rowCount: number;
  columnCount: number;
  rows: Record<string, unknown>[];
  executionTimeMs: number;
  optimized: boolean;
  timestamp: string;
}

/**
 * Submits a SQL query to the analytics engine.
 *
 * POST /api/v1/analytics/query
 * Body: {"query": "<sql>", "parameters": {...}}
 * Returns the synchronous result with rows as Record<string, unknown>[].
 *
 * @param sql the SQL query string
 * @param parameters optional named query parameters
 * @throws Error if execution fails or server returns non-2xx
 */
export async function executeAnalyticsQuery(
  sql: string,
  parameters: Record<string, unknown> = {}
): Promise<QueryResultData> {
  const tenantId = getTenantId();
  const response = await fetch(`${API_BASE}/analytics/query`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': tenantId,
    },
    body: JSON.stringify({ query: sql, parameters }),
  });
  if (!response.ok) {
    const msg = await response.text().catch(() => response.statusText);
    throw new Error(msg || `HTTP ${response.status}`);
  }
  return response.json() as Promise<QueryResultData>;
}

// =============================================================================
// TANSACK QUERY HOOKS
// =============================================================================

/**
 * React Query hook for ad-hoc analytics SQL queries.
 *
 * Wraps `executeAnalyticsQuery` as a `useMutation` so callers can trigger
 * execution imperatively (e.g., on button click) and observe loading/error state.
 *
 * @example
 * const { mutate: runQuery, data, isPending, error } = useAnalyticsQuery();
 * runQuery({ sql: 'SELECT COUNT(*) as total FROM orders' });
 */
export function useAnalyticsQuery() {
  return useMutation({
    mutationFn: ({ sql, parameters }: { sql: string; parameters?: Record<string, unknown> }) =>
      executeAnalyticsQuery(sql, parameters),
  });
}

/**
 * Queries entity counts across a supplied list of collection names.
 *
 * Each collection is queried individually via `SELECT COUNT(*) as total FROM
 * <collection>`. Queries run in parallel and failures are silently treated as
 * zero (the collection may simply be empty or temporarily unavailable).
 *
 * Enabled only when the `collections` array is non-empty.
 *
 * @param collections array of collection names to count
 * @returns `{ data: CollectionStat[] | undefined, isLoading, error }`
 */
export interface CollectionStat {
  collection: string;
  count: number;
  executionTimeMs: number;
}

export function useCollectionEntityCounts(collections: string[]) {
  return useQuery<CollectionStat[]>({
    queryKey: ['analytics', 'collection-counts', ...collections],
    queryFn: async () => {
      // Cap at 10 to avoid flooding the analytics engine
      const targets = collections.slice(0, 10);
      const results = await Promise.allSettled(
        targets.map((col) =>
          executeAnalyticsQuery(`SELECT COUNT(*) as total FROM ${col}`)
        )
      );
      return targets.map((col, i): CollectionStat => {
        const r = results[i];
        if (r.status === 'fulfilled') {
          const total = r.value.rows[0]?.total;
          return {
            collection: col,
            count: typeof total === 'number' ? total : 0,
            executionTimeMs: r.value.executionTimeMs,
          };
        }
        return { collection: col, count: 0, executionTimeMs: 0 };
      });
    },
    enabled: collections.length > 0,
    // Refresh counts every 2 minutes to reflect live changes
    staleTime: 120_000,
  });
}
