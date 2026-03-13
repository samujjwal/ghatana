/**
 * Analytics Service
 *
 * Client for the DC analytics query engine endpoints (DC-9).
 * Supports synchronous SQL query submission and result retrieval.
 *
 * @doc.type service
 * @doc.purpose Analytics SQL query execution client
 * @doc.layer frontend
 */

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
