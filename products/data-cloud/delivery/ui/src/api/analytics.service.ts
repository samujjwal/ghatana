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

import { useMutation, useQuery } from "@tanstack/react-query";
import {
  AnalyticsExplainResponseSchema,
  AnalyticsSqlQueryResponseSchema,
  AnalyticsSuggestResponseSchema,
  type AnalyticsExplainResponse,
  type AnalyticsSqlQueryResponse,
  type AnalyticsSuggestQuery,
  type AnalyticsSuggestResponse,
} from "../contracts/schemas";
import { apiClient } from "../lib/api/client";
import { collectionsApi } from "../lib/api/collections";
import SessionBootstrap from "../lib/auth/session";
import { isAnalyticsAiEnabled } from "../lib/feature-gates";
import {
  ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE,
  createRuntimeBoundaryError,
} from "../lib/runtime-boundaries";

const _API_BASE = "/api/v1";

function getTenantId(): string {
  return SessionBootstrap.requireTenantId();
}

export interface AnalyticsSqlSuggestionResult {
  suggestions: AnalyticsSuggestQuery[];
  fallback: boolean;
  confidence: number;
  model?: string;
  reasons: string[];
}

export interface QueryPolicyEvaluation {
  verdict: "allow" | "review" | "deny";
  confidence: number;
  reasons: string[];
  requiresApproval: boolean;
  source: "policy-engine" | "heuristic-fallback";
}

export type AnalyticsExplainResult = AnalyticsExplainResponse;

/**
 * Fetches query templates for a natural-language analytics intent.
 *
 * POST /api/v1/analytics/suggest
 */
export async function fetchAnalyticsQuerySuggestions(
  intent: string,
): Promise<AnalyticsSqlSuggestionResult> {
  if (!isAnalyticsAiEnabled()) {
    throw createRuntimeBoundaryError(ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE);
  }
  const tenantId = getTenantId();
  const response = await apiClient.post<AnalyticsSuggestResponse>(
    "/analytics/suggest",
    { intent, limit: 5 },
    { headers: { "X-Tenant-ID": tenantId } },
  );
  const resp = AnalyticsSuggestResponseSchema.parse(response);
  return {
    suggestions: resp.data?.queries ?? [],
    fallback: resp.ai?.fallback ?? false,
    confidence: resp.ai?.confidence ?? 0,
    model: resp.ai?.model,
    reasons: resp.ai?.reasons ?? [],
  };
}

export type QueryResultData = AnalyticsSqlQueryResponse;

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
  parameters: Record<string, unknown> = {},
): Promise<QueryResultData> {
  const tenantId = getTenantId();
  const response = await apiClient.post<AnalyticsSqlQueryResponse>(
    "/analytics/query",
    { query: sql, parameters },
    { headers: { "X-Tenant-ID": tenantId } },
  );
  return AnalyticsSqlQueryResponseSchema.parse(response);
}

/**
 * Explains a SQL query without executing it.
 *
 * POST /api/v1/analytics/explain
 */
export async function explainAnalyticsQuery(
  sql: string,
  parameters: Record<string, unknown> = {},
): Promise<AnalyticsExplainResult> {
  const tenantId = getTenantId();
  const response = await apiClient.post<AnalyticsExplainResponse>(
    "/analytics/explain",
    { query: sql, parameters },
    { headers: { "X-Tenant-ID": tenantId } },
  );
  return AnalyticsExplainResponseSchema.parse(response);
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
  parameters: Record<string, unknown> = {},
): Promise<QueryResultData> {
  const tenantId = getTenantId();
  const response = await apiClient.post<AnalyticsSqlQueryResponse>(
    "/queries/federated",
    { sql, parameters },
    { headers: { "X-Tenant-ID": tenantId } },
  );
  return AnalyticsSqlQueryResponseSchema.parse(response);
}

function fallbackPolicyEvaluation(sql: string): QueryPolicyEvaluation {
  const normalized = sql.toLowerCase();
  const reasons: string[] = [];

  if (/\bdrop\s+table\b|\btruncate\b|\bdelete\s+from\b/.test(normalized)) {
    reasons.push("Potentially destructive command detected.");
    return {
      verdict: "deny",
      confidence: 0.95,
      reasons,
      requiresApproval: true,
      source: "heuristic-fallback",
    };
  }

  if (/\bselect\s+\*/.test(normalized) && !/\blimit\b/.test(normalized)) {
    reasons.push("Wide scan detected without LIMIT.");
  }

  if (!/\bwhere\b/.test(normalized) && /\bfrom\b/.test(normalized)) {
    reasons.push("No filter clause detected; query may scan broad datasets.");
  }

  if (reasons.length > 0) {
    return {
      verdict: "review",
      confidence: 0.74,
      reasons,
      requiresApproval: true,
      source: "heuristic-fallback",
    };
  }

  return {
    verdict: "allow",
    confidence: 0.68,
    reasons: ["No high-risk patterns detected by local policy heuristics."],
    requiresApproval: false,
    source: "heuristic-fallback",
  };
}

/**
 * Evaluate query policy/risk before execution.
 * Attempts backend policy endpoint first, then falls back to deterministic client heuristics.
 */
export async function evaluateQueryPolicy(
  sql: string,
): Promise<QueryPolicyEvaluation> {
  if (!isAnalyticsAiEnabled()) {
    throw createRuntimeBoundaryError(ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE);
  }
  const tenantId = getTenantId();

  try {
    const response = await apiClient.post<{
      verdict?: "allow" | "review" | "deny";
      confidence?: number;
      reasons?: string[];
      requiresApproval?: boolean;
    }>(
      "/analytics/policy-evaluate",
      { query: sql },
      { headers: { "X-Tenant-ID": tenantId } },
    );

    const verdict = response.verdict;
    if (verdict === "allow" || verdict === "review" || verdict === "deny") {
      return {
        verdict,
        confidence:
          typeof response.confidence === "number" ? response.confidence : 0.8,
        reasons:
          Array.isArray(response.reasons) && response.reasons.length > 0
            ? response.reasons
            : ["Policy evaluation completed."],
        requiresApproval: response.requiresApproval ?? verdict !== "allow",
        source: "policy-engine",
      };
    }
  } catch {
    // Policy endpoint is optional in current deployments; fallback is intentional.
  }

  return fallbackPolicyEvaluation(sql);
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
    mutationFn: ({
      sql,
      parameters,
    }: {
      sql: string;
      parameters?: Record<string, unknown>;
    }) => executeAnalyticsQuery(sql, parameters),
  });
}

/**
 * Queries entity counts across a supplied list of collection names.
 *
 * Counts come from the canonical collection registry metadata instead of
 * client-side SQL fan-out, so aggregate stats remain backend-owned.
 *
 * Enabled only when the `collections` array is non-empty.
 *
 * @param collections array of collection names to count
 * @returns `{ data: CollectionStat[] | undefined, isLoading, error }`
 */
export interface CollectionStat {
  collection: string;
  count: number | null;
  executionTimeMs: number;
}

export function useCollectionEntityCounts(collections: string[]) {
  return useQuery<CollectionStat[]>({
    queryKey: ["analytics", "collection-counts", ...collections],
    queryFn: async () => {
      const targets = collections.slice(0, 10);
      const pageSize = Math.max(50, targets.length);
      const registry = await collectionsApi.list({ page: 1, pageSize });
      const countsByName = new Map<string, number>(
        registry.items.map((item) => [
          item.name.toLowerCase(),
          item.entityCount,
        ]),
      );

      return targets.map((col): CollectionStat => {
        const count = countsByName.get(col.toLowerCase());
        return {
          collection: col,
          count:
            typeof count === "number" && Number.isFinite(count) ? count : null,
          executionTimeMs: 0,
        };
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
  type: "optimization" | "anomaly" | "insight" | "warning";
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

/** Fetch AI suggestions by calling POST /api/v1/analytics/suggest */
async function fetchAnalyticsSuggestions(
  tenantId: string,
): Promise<AnalyticsAiSuggestion[]> {
  try {
    const response = await apiClient.post<AnalyticsSuggestResponse>(
      "/analytics/suggest",
      { context: "anomaly_and_optimization", limit: 5 },
      { headers: { "X-Tenant-ID": tenantId } },
    );
    const resp = AnalyticsSuggestResponseSchema.parse(response);
    const raw = resp.data?.queries ?? [];
    const isFallback = resp.ai?.fallback ?? false;
    const confidence = resp.ai?.confidence ?? 0.5;
    return raw.map(
      (s: AnalyticsSuggestQuery, i: number): AnalyticsAiSuggestion => ({
        key: `analytics-${i}`,
        type: mapQuerySuggestionType(s),
        title: s.name ?? "Suggested query",
        description: s.explanation ?? s.template ?? "",
        confidence,
        reasons: s.template ? [s.template] : [],
        fallback: isFallback,
      }),
    );
  } catch {
    // Analytics suggest service offline — return deterministic fallback
    return deterministicAnalyticsFallback();
  }
}

/** Deterministic fallback: shown when AI service is unavailable (E3 requirement). */
function deterministicAnalyticsFallback(): AnalyticsAiSuggestion[] {
  return [
    {
      key: "fallback-0",
      type: "optimization",
      title: "Review high-frequency queries",
      description:
        "Queries running more than 50×/hour may benefit from result caching.",
      confidence: 0.0,
      reasons: ["heuristic"],
      fallback: true,
    },
    {
      key: "fallback-1",
      type: "insight",
      title: "Schema documentation incomplete",
      description:
        "Several collections lack descriptions — adding them improves query suggestions.",
      confidence: 0.0,
      reasons: ["heuristic"],
      fallback: true,
    },
  ];
}

function mapQuerySuggestionType(
  query: AnalyticsSuggestQuery,
): AnalyticsAiSuggestion["type"] {
  const text =
    `${query.name ?? ""} ${query.explanation ?? ""} ${query.template ?? ""}`.toLowerCase();
  if (text.includes("anomaly") || text.includes("alert")) return "anomaly";
  if (text.includes("cache") || text.includes("optim") || text.includes("cost"))
    return "optimization";
  if (
    text.includes("warn") ||
    text.includes("stale") ||
    text.includes("missing")
  )
    return "warning";
  return "insight";
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
  const tenantId = SessionBootstrap.getTenantId();

  return useQuery<AnalyticsAiSuggestion[]>({
    queryKey: ["analytics", "ai-suggestions", tenantId ?? "missing-tenant"],
    queryFn: () =>
      fetchAnalyticsSuggestions(SessionBootstrap.requireTenantId()),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    // Never throw — always resolve to at least the fallback list
    retry: false,
  });
}
