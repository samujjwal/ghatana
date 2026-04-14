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
import { apiClient } from '../lib/api/client';

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

/**
 * Submits a federated SQL query routed through the Trino connector (B13).
 *
 * POST /api/v1/queries/federated
 * Body: {"sql": "<sql>", "parameters": {...}}
 * Federated queries span all storage tiers in a single request via Trino.
 *
 * @param sql    the SQL query string
 * @param parameters optional named query parameters
 */
export async function executeFederatedQuery(
  sql: string,
  parameters: Record<string, unknown> = {}
): Promise<QueryResultData> {
  const tenantId = getTenantId();
  const response = await fetch(`${API_BASE}/queries/federated`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': tenantId,
    },
    body: JSON.stringify({ sql, parameters }),
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

// =============================================================================
// AI SUGGESTIONS FOR ANALYTICS PAGE  (E3 — Pervasive AI/ML)
// =============================================================================

/**
 * Shape of a single AI-derived analytics suggestion returned by
 * POST /api/v1/analytics/suggest  and  GET /api/v1/brain/workspace.
 */
export interface AnalyticsAiSuggestion {
  /** Stable client-side key (not from server — prevents key churn on refetch). */
  key: string;
  /** Suggestion category — drives icon and colour choices. */
  type: 'optimization' | 'anomaly' | 'insight' | 'warning';
  /** Short, action-oriented title. */
  title: string;
  /** Single-sentence description with enough context to act. */
  description: string;
  /** 0-1 confidence score from the AI model. */
  confidence: number;
  /** Human-readable reason codes / model labels. */
  reasons: string[];
  /** Whether this was produced by the deterministic fallback path. */
  fallback: boolean;
}

/** Internal response shape expected from POST /api/v1/analytics/suggest */
interface AnalyticsSuggestResponse {
  data?: {
    suggestions?: Array<{
      type?: string;
      title?: string;
      description?: string;
      reasons?: string[];
    }>;
  };
  ai?: { confidence?: number; fallback?: boolean };
}

/** Fetch AI suggestions by calling POST /api/v1/analytics/suggest */
async function fetchAnalyticsSuggestions(tenantId: string): Promise<AnalyticsAiSuggestion[]> {
  try {
    const resp = await apiClient.post<AnalyticsSuggestResponse>(
      '/api/v1/analytics/suggest',
      { context: 'anomaly_and_optimization', limit: 5 },
      { headers: { 'X-Tenant-ID': tenantId } },
    );
    const raw = resp.data?.suggestions ?? [];
    const isFallback = resp.ai?.fallback ?? false;
    const confidence = resp.ai?.confidence ?? 0.5;
    return raw.map((s: { type?: string; title?: string; description?: string; reasons?: string[] }, i: number): AnalyticsAiSuggestion => ({
      key: `analytics-${i}`,
      type: mapSuggestionType(s.type),
      title: s.title ?? 'Suggestion',
      description: s.description ?? '',
      confidence,
      reasons: s.reasons ?? [],
      fallback: isFallback,
    }));
  } catch {
    // Analytics suggest service offline — return deterministic fallback
    return deterministicAnalyticsFallback();
  }
}

/** Deterministic fallback: shown when AI service is unavailable (E3 requirement). */
function deterministicAnalyticsFallback(): AnalyticsAiSuggestion[] {
  return [
    {
      key: 'fallback-0',
      type: 'optimization',
      title: 'Review high-frequency queries',
      description: 'Queries running more than 50×/hour may benefit from result caching.',
      confidence: 0.0,
      reasons: ['heuristic'],
      fallback: true,
    },
    {
      key: 'fallback-1',
      type: 'insight',
      title: 'Schema documentation incomplete',
      description: 'Several collections lack descriptions — adding them improves query suggestions.',
      confidence: 0.0,
      reasons: ['heuristic'],
      fallback: true,
    },
  ];
}

function mapSuggestionType(raw?: string): AnalyticsAiSuggestion['type'] {
  switch (raw?.toLowerCase()) {
    case 'optimization': return 'optimization';
    case 'anomaly':
    case 'anomaly_hint': return 'anomaly';
    case 'warning': return 'warning';
    default: return 'insight';
  }
}

/**
 * React Query hook: AI-generated analytics suggestions.
 *
 * Calls POST /api/v1/analytics/suggest.  Falls back deterministically when
 * the service is unavailable so the panel is never empty.
 *
 * Refresh every 5 minutes; do not refetch on window focus to avoid distraction.
 *
 * @example
 * const { data: suggestions, isLoading, isFallback } = useAnalyticsAiSuggestions();
 */
export function useAnalyticsAiSuggestions() {
  const tenantId =
    typeof localStorage !== 'undefined' ? (localStorage.getItem('tenantId') ?? 'default-tenant') : 'default-tenant';

  return useQuery<AnalyticsAiSuggestion[]>({
    queryKey: ['analytics', 'ai-suggestions', tenantId],
    queryFn: () => fetchAnalyticsSuggestions(tenantId),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    // Never throw — always resolve to at least the fallback list
    retry: false,
  });
}

