/**
 * SQL Workspace Page
 *
 * Full-featured SQL workspace for authoring, running, and managing queries.
 * See spec: docs/web-page-specs/14_sql_workspace_page.md
 *
 * Features:
 * - Schema-aware SQL editor
 * - AI-powered query assistance
 * - Natural language to SQL conversion
 * - Query optimization suggestions
 *
 * @doc.type page
 * @doc.purpose SQL query editor with AI-powered assistance
 * @doc.layer frontend
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation } from 'react-router';
import {
  Play,
  Save,
  Download,
  FileText,
  Clock,
  Database,
  ChevronRight,
  Bookmark,
  Sparkles,
  MessageSquare,
  Lightbulb,
  Zap,
  Send,
  Wand2,
  Network,
  AlertTriangle,
} from 'lucide-react';
import {
  cn,
  cardStyles,
  textStyles,
  bgStyles,
  buttonStyles,
  inputStyles,
    tableStyles,
} from '../lib/theme';
import {
  SQL_FEDERATED_QUERY_UNAVAILABLE_DETAIL,
  SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_DETAIL,
  SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_TITLE,
} from '../lib/runtime-boundaries';
import { SavedQueries, type SavedQuery } from '../components/sql/SavedQueries';
import { collectionsApi } from '../lib/api/collections';
import {
  executeAnalyticsQuery,
  explainAnalyticsQuery,
  executeFederatedQuery,
  fetchAnalyticsQuerySuggestions,
  type AnalyticsExplainResult,
  type QueryResultData,
} from '../api/analytics.service';
import { getCapabilitySignal, useCapabilityRegistry } from '../api/capabilities.service';
import { CapabilityTruthPanel } from '../components/capabilities/CapabilityTruthPanel';
import type { AnalyticsSuggestQuery } from '../contracts/schemas';
import {
  getRecentActivity,
  type ContinueWorkingItem as ActivityContinueWorkingItem,
  type UserActivityItem,
} from '../lib/api/user-activity';

interface QueryHistoryItem {
  id: number;
  query: string;
  timestamp: string;
  duration: string;
}

interface SchemaItem {
  name: string;
  tables: string[];
}

export interface InferredQueryScope {
  collectionName: string | null;
  timeWindow: string | null;
  confidence: 'high' | 'medium' | 'low';
  ambiguous: boolean;
  reason: string;
  candidates: string[];
}

export interface QueryExecutionRecommendation {
  path: 'direct' | 'federated' | 'review';
  title: string;
  detail: string;
  requiresReview: boolean;
}

export interface QueryPlanGuardrail {
  severity: 'info' | 'warning' | 'critical';
  title: string;
  detail: string;
}

export function deriveQueryPlanGuardrails(
  sql: string,
  plan: AnalyticsExplainResult | null,
  recommendation: QueryExecutionRecommendation,
): QueryPlanGuardrail[] {
  const normalized = sql.toLowerCase();
  const guardrails: QueryPlanGuardrail[] = [];

  if (recommendation.requiresReview) {
    guardrails.push({
      severity: 'critical',
      title: 'Review-first path required',
      detail: recommendation.detail,
    });
  }

  if (/\bselect\s+\*/.test(normalized) && !/\blimit\b/.test(normalized)) {
    guardrails.push({
      severity: 'warning',
      title: 'Wide scan risk',
      detail: 'The query selects all columns without a LIMIT clause, which can expand cost and expose more data than necessary.',
    });
  }

  if (/\bfrom\b/.test(normalized) && !/\bwhere\b/.test(normalized)) {
    guardrails.push({
      severity: 'warning',
      title: 'Unbounded filter scope',
      detail: 'No WHERE clause was detected. Consider adding a time window or dataset filter before running this in shared environments.',
    });
  }

  if (plan?.dataSources.length && plan.dataSources.length > 1) {
    guardrails.push({
      severity: 'warning',
      title: 'Multiple data sources in plan',
      detail: `The explain plan references ${plan.dataSources.length} data sources (${plan.dataSources.join(', ')}), which usually increases execution risk and review needs.`,
    });
  }

  if (plan && plan.estimatedCost >= 5000) {
    guardrails.push({
      severity: 'warning',
      title: 'Elevated estimated cost',
      detail: `The planner estimated a relative cost of ${Math.round(plan.estimatedCost)}. Review the query path and narrowing filters before executing.`,
    });
  }

  if (plan?.optimized) {
    guardrails.push({
      severity: 'info',
      title: 'Optimizer hints available',
      detail: 'The planner marked this query as optimized, so the current shape is acceptable for the selected execution path.',
    });
  }

  return guardrails;
}

function extractCollectionHints(
  collections: SchemaItem[],
  recentActivity: UserActivityItem[],
  continueWorking: ActivityContinueWorkingItem[],
): string[] {
  const standaloneCollectionHints = continueWorking
    .filter((item) => item.type === 'collection')
    .map((item) => item.name.toLowerCase());
  const recentText = recentActivity.map((item) => item.target.toLowerCase()).join(' ');
  const continueWorkingCollections = continueWorking
    .filter((item) => item.type === 'collection')
    .map((item) => item.name.toLowerCase());

  const schemaBackedHints = collections
    .map((collection) => collection.name)
    .filter((collectionName) => {
      const normalized = collectionName.toLowerCase();
      return recentText.includes(normalized) || continueWorkingCollections.includes(normalized);
    });

  return schemaBackedHints.length > 0
    ? schemaBackedHints
    : Array.from(new Set(standaloneCollectionHints));
}

function inferTimeWindow(intent: string): string | null {
  const normalized = intent.toLowerCase();
  const patterns: Array<{ expression: RegExp; label: string }> = [
    { expression: /\btoday\b/, label: 'today' },
    { expression: /\byesterday\b/, label: 'yesterday' },
    { expression: /\bthis week\b/, label: 'this week' },
    { expression: /\blast week\b|\bpast 7 days\b/, label: 'last 7 days' },
    { expression: /\bthis month\b/, label: 'this month' },
    { expression: /\blast month\b|\bpast 30 days\b/, label: 'last 30 days' },
  ];

  const match = patterns.find((pattern) => pattern.expression.test(normalized));
  return match?.label ?? null;
}

export function inferAnalyticsScope(
  intent: string,
  collections: SchemaItem[],
  recentActivity: UserActivityItem[],
  continueWorking: ActivityContinueWorkingItem[],
): InferredQueryScope {
  const normalizedIntent = intent.toLowerCase();
  const directMatches = collections
    .map((collection) => collection.name)
    .filter((collectionName) => normalizedIntent.includes(collectionName.toLowerCase()));

  if (directMatches.length === 1) {
    return {
      collectionName: directMatches[0],
      timeWindow: inferTimeWindow(intent),
      confidence: 'high',
      ambiguous: false,
      reason: 'Matched the collection name directly in the request.',
      candidates: directMatches,
    };
  }

  if (directMatches.length > 1) {
    return {
      collectionName: directMatches[0],
      timeWindow: inferTimeWindow(intent),
      confidence: 'low',
      ambiguous: true,
      reason: `Multiple collections matched (${directMatches.join(', ')}). Confirm the target collection before running anything expensive.`,
      candidates: directMatches,
    };
  }

  const recentHints = extractCollectionHints(collections, recentActivity, continueWorking);
  if (recentHints.length > 0) {
    return {
      collectionName: recentHints[0],
      timeWindow: inferTimeWindow(intent),
      confidence: recentHints.length === 1 ? 'medium' : 'low',
      ambiguous: recentHints.length > 1,
      reason: recentHints.length === 1
        ? `Using your recent collection context (${recentHints[0]}).`
        : `Recent activity points to several collections (${recentHints.join(', ')}).`,
      candidates: recentHints,
    };
  }

  if (collections.length === 1) {
    return {
      collectionName: collections[0].name,
      timeWindow: inferTimeWindow(intent),
      confidence: 'medium',
      ambiguous: false,
      reason: `Only one collection is available in this workspace (${collections[0].name}).`,
      candidates: [collections[0].name],
    };
  }

  return {
    collectionName: null,
    timeWindow: inferTimeWindow(intent),
    confidence: 'low',
    ambiguous: collections.length > 1,
    reason: collections.length > 1
      ? 'No clear collection could be inferred from the request. Add a collection name if the suggestions look too broad.'
      : 'Collection context is unavailable, so suggestions will use the raw request only.',
    candidates: collections.slice(0, 3).map((collection) => collection.name),
  };
}

function buildScopedIntent(intent: string, scope: InferredQueryScope): string {
  const context: string[] = [];

  if (scope.collectionName) {
    context.push(`Collection context: ${scope.collectionName}`);
  }

  if (scope.timeWindow) {
    context.push(`Time window: ${scope.timeWindow}`);
  }

  return context.length === 0 ? intent : `${intent}\n\n${context.join('\n')}`;
}

export function recommendAnalyticsExecution(
  sql: string,
  federatedAvailable: boolean,
): QueryExecutionRecommendation {
  const normalized = sql.toLowerCase();
  const joinCount = (normalized.match(/\bjoin\b/g) ?? []).length;
  const sourceReferences = Array.from(
    normalized.matchAll(/\b(?:from|join)\s+([a-z0-9_]+)\./g),
    (match) => match[1],
  );
  const uniqueSources = new Set(sourceReferences);
  const requiresFederation =
    normalized.includes('federated') ||
    normalized.includes('cross tier') ||
    uniqueSources.size > 1;
  const heavyQuery =
    joinCount >= 2 ||
    (normalized.includes('group by') && !normalized.includes('limit')) ||
    (normalized.includes('select *') && !normalized.includes('limit'));

  if (requiresFederation) {
    if (federatedAvailable) {
      return {
        path: 'federated',
        title: 'Recommended: federated execution',
        detail: 'The query appears to span multiple sources or tiers. Run it through the federated path to avoid partial results.',
        requiresReview: false,
      };
    }

    return {
      path: 'review',
      title: 'Review required before execution',
      detail: 'This query looks cross-source, but federated execution is unavailable in this deployment. Narrow the scope or wait for Trino-backed execution.',
      requiresReview: true,
    };
  }

  if (heavyQuery) {
    return {
      path: 'review',
      title: 'Review cost and scope before running',
      detail: 'This query is likely expensive. Add a tighter filter or LIMIT unless you need a full aggregation.',
      requiresReview: true,
    };
  }

  return {
    path: 'direct',
    title: 'Recommended: direct analytics execution',
    detail: 'The query fits the standard analytics engine and does not need the federated path.',
    requiresReview: false,
  };
}

/**
 * AI Query suggestions
 */
const AI_SUGGESTIONS = [
  {
    id: '1',
    type: 'optimization' as const,
    message: 'Add an index on timestamp column for faster queries',
    action: 'CREATE INDEX idx_timestamp ON events(timestamp);',
  },
  {
    id: '2',
    type: 'suggestion' as const,
    message: 'Consider using BETWEEN for date ranges',
    action: "WHERE timestamp BETWEEN '2024-01-01' AND '2024-01-31'",
  },
];

/**
 * AI Query Assist Panel
 */
function AIQueryAssist({
  onApply,
  schemas,
  recentActivity,
  continueWorking,
}: {
  onApply: (sql: string) => void;
  schemas: SchemaItem[];
  recentActivity: UserActivityItem[];
  continueWorking: ActivityContinueWorkingItem[];
}) {
  const [naturalQuery, setNaturalQuery] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [suggestions, setSuggestions] = useState<AnalyticsSuggestQuery[]>([]);
  const [generationMessage, setGenerationMessage] = useState<string | null>(null);
  const [suggestionConfidence, setSuggestionConfidence] = useState<number | null>(null);
  const [suggestionFallback, setSuggestionFallback] = useState(false);
  const [inferredScope, setInferredScope] = useState<InferredQueryScope | null>(null);

  const handleClarifyScope = useCallback((candidate: string) => {
    setNaturalQuery((currentQuery) => {
      if (currentQuery.toLowerCase().includes(candidate.toLowerCase())) {
        return currentQuery;
      }
      return `${currentQuery.trim()} for ${candidate}`.trim();
    });
  }, []);

  const handleGenerate = useCallback(async () => {
    if (!naturalQuery.trim()) return;
    setIsGenerating(true);
    setGenerationMessage(null);
    setSuggestions([]);
    try {
      const scope = inferAnalyticsScope(naturalQuery.trim(), schemas, recentActivity, continueWorking);
      setInferredScope(scope);
      if (scope.ambiguous) {
        setGenerationMessage(scope.reason);
      }

      const result = await fetchAnalyticsQuerySuggestions(buildScopedIntent(naturalQuery.trim(), scope));
      setSuggestionConfidence(result.confidence);
      setSuggestionFallback(result.fallback);
      setSuggestions(result.suggestions);
      if (result.suggestions.length === 0) {
        setGenerationMessage('No suggested SQL templates were returned for this intent. Refine the question or continue authoring SQL manually.');
      }
    } catch (error) {
      setSuggestionConfidence(null);
      setSuggestionFallback(true);
      setSuggestions([]);
      setGenerationMessage(error instanceof Error ? error.message : 'Unable to fetch analytics suggestions.');
    } finally {
      setIsGenerating(false);
    }
  }, [naturalQuery]);

  return (
    <div className={cn(
      'bg-gradient-to-br from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20',
      'border border-purple-200 dark:border-purple-800',
      'rounded-xl p-4 mb-4'
    )} data-testid="sql-ai-assist-panel">
      <div className="flex items-center gap-2 mb-3">
        <Sparkles className="h-5 w-5 text-purple-500" />
        <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
          AI Query Assist
        </span>
      </div>

      {/* Natural language input */}
      <div className="flex gap-2 mb-3">
        <input
          type="text"
          value={naturalQuery}
          onChange={(e) => setNaturalQuery(e.target.value)}
          placeholder="Describe what you want to query... e.g., 'Show me top users by event count this week'"
          className={cn(
            'flex-1 px-3 py-2 rounded-lg text-sm',
            'bg-white dark:bg-gray-800',
            'border border-purple-200 dark:border-purple-700',
            'text-gray-900 dark:text-gray-100',
            'placeholder-gray-400',
            'focus:ring-2 focus:ring-purple-500 focus:border-transparent',
            'outline-none'
          )}
          data-testid="sql-ai-assist-input"
          onKeyDown={(e) => e.key === 'Enter' && handleGenerate()}
        />
        <button
          onClick={handleGenerate}
          disabled={!naturalQuery.trim() || isGenerating}
          data-testid="sql-ai-assist-generate"
          className={cn(
            'px-4 py-2 rounded-lg',
            'bg-purple-600 hover:bg-purple-700',
            'text-white text-sm font-medium',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            'transition-colors',
            'flex items-center gap-2'
          )}
        >
          {isGenerating ? (
            <>
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Generating...
            </>
          ) : (
            <>
              <Wand2 className="h-4 w-4" />
              Generate
            </>
          )}
        </button>
      </div>

      {generationMessage && (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200" data-testid="sql-ai-generation-message">
          {generationMessage}
        </div>
      )}

      {inferredScope && (inferredScope.collectionName || inferredScope.timeWindow || inferredScope.reason) && (
        <div className="mt-3 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-200" data-testid="sql-inferred-scope">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">Inferred scope</span>
            {inferredScope.collectionName && (
              <span className="rounded-full bg-white px-2 py-0.5 text-xs text-blue-900 dark:bg-gray-900 dark:text-blue-100">
                collection {inferredScope.collectionName}
              </span>
            )}
            {inferredScope.timeWindow && (
              <span className="rounded-full bg-white px-2 py-0.5 text-xs text-blue-900 dark:bg-gray-900 dark:text-blue-100">
                {inferredScope.timeWindow}
              </span>
            )}
            <span className="text-xs uppercase tracking-wide text-blue-700 dark:text-blue-300">
              {inferredScope.confidence} confidence
            </span>
          </div>
          <p className="mt-1 text-xs text-blue-800 dark:text-blue-200">{inferredScope.reason}</p>
        </div>
      )}

      {inferredScope?.ambiguous && inferredScope.candidates.length > 0 && (
        <div className="mt-3 rounded-lg border border-purple-200 bg-white px-3 py-3 text-sm text-gray-900 dark:border-purple-800 dark:bg-gray-950 dark:text-gray-100" data-testid="sql-clarification-prompt">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-medium">Clarification recommended</p>
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
                The request maps to more than one collection context. Pick a likely target to refine the generated SQL templates.
              </p>
            </div>
            <MessageSquare className="h-4 w-4 text-purple-500" />
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {inferredScope.candidates.map((candidate) => (
              <button
                key={candidate}
                onClick={() => handleClarifyScope(candidate)}
                data-testid={`sql-clarify-${candidate.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`}
                className={cn(
                  'rounded-full border border-purple-200 bg-purple-50 px-3 py-1 text-xs font-medium text-purple-800 transition-colors',
                  'hover:bg-purple-100 dark:border-purple-700 dark:bg-purple-900/30 dark:text-purple-100 dark:hover:bg-purple-900/50'
                )}
              >
                Use {candidate}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Suggested SQL */}
      {suggestions.length > 0 && (
        <div className="mt-3">
          <div className="mb-2 flex items-center justify-between gap-3">
            <span className="text-xs text-gray-500">
              Suggested SQL templates{suggestionConfidence != null ? ` • confidence ${Math.round(suggestionConfidence * 100)}%` : ''}
            </span>
            {suggestionFallback && (
              <span className="rounded-full bg-amber-100 px-2 py-1 text-[11px] font-medium text-amber-900 dark:bg-amber-900/30 dark:text-amber-200">
                Heuristic fallback
              </span>
            )}
          </div>
          <div className="space-y-3">
            {suggestions.map((suggestion) => (
              <div
                key={`${suggestion.name}-${suggestion.template}`}
                className="rounded-lg border border-purple-200 bg-white p-3 dark:border-purple-800 dark:bg-gray-900"
              >
                <div className="mb-2 flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-medium text-gray-900 dark:text-gray-100">{suggestion.name}</div>
                    <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{suggestion.explanation}</p>
                  </div>
                  <button
                    onClick={() => onApply(suggestion.template)}
                    data-testid="sql-apply-suggestion"
                    className={cn(
                      'text-xs px-2 py-1 rounded',
                      'bg-purple-600 hover:bg-purple-700',
                      'text-white',
                      'transition-colors'
                    )}
                  >
                    Apply to Editor
                  </button>
                </div>
                <pre className={cn(
                  'text-xs font-mono p-3 rounded-lg overflow-x-auto',
                  'bg-gray-900 text-gray-100'
                )}>
                  {suggestion.template}
                </pre>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Quick suggestions */}
      <div className="mt-3 flex flex-wrap gap-2">
        {['Show top 10 users', 'Count events by type', 'Recent orders today'].map((suggestion) => (
          <button
            key={suggestion}
            onClick={() => setNaturalQuery(suggestion)}
            className={cn(
              'text-xs px-2 py-1 rounded-full',
              'bg-white dark:bg-gray-800',
              'border border-gray-200 dark:border-gray-700',
              'text-gray-600 dark:text-gray-400',
              'hover:border-purple-300 dark:hover:border-purple-700',
              'transition-colors'
            )}
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  );
}

/**
 * AI Suggestions Sidebar
 */
function AISuggestionsSidebar({ suggestions }: { suggestions: typeof AI_SUGGESTIONS }) {
  return (
    <div className={cn(cardStyles.base, 'overflow-hidden')}>
      <div className={cn(cardStyles.header, 'flex items-center gap-2')}>
        <Lightbulb className="h-4 w-4 text-amber-500" />
        <span className={textStyles.h4}>AI Suggestions</span>
      </div>
      <div className="p-2 space-y-2">
        {suggestions.map((suggestion) => (
          <div
            key={suggestion.id}
            className={cn(
              'p-3 rounded-lg',
              'bg-gray-50 dark:bg-gray-800',
              'border border-gray-100 dark:border-gray-700'
            )}
          >
            <div className="flex items-start gap-2">
              {suggestion.type === 'optimization' ? (
                <Zap className="h-4 w-4 text-amber-500 mt-0.5" />
              ) : (
                <MessageSquare className="h-4 w-4 text-blue-500 mt-0.5" />
              )}
              <div className="flex-1">
                <p className="text-sm text-gray-700 dark:text-gray-300">{suggestion.message}</p>
                <code className="text-xs text-gray-500 dark:text-gray-400 mt-1 block truncate">
                  {suggestion.action}
                </code>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Mock result data
 */
/**
 * SQL Workspace Page Component
 *
 * @returns JSX element
 */
export function SqlWorkspacePage(): React.ReactElement {
  const location = useLocation();
  const [query, setQuery] = useState('SELECT * FROM events\nWHERE timestamp > NOW() - INTERVAL 1 DAY\nLIMIT 100;');
  const [isRunning, setIsRunning] = useState(false);
  const [isExplaining, setIsExplaining] = useState(false);
  const [queryResult, setQueryResult] = useState<QueryResultData | null>(null);
  const [queryPlan, setQueryPlan] = useState<AnalyticsExplainResult | null>(null);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [queryPlanError, setQueryPlanError] = useState<string | null>(null);
  const [expandedSchema, setExpandedSchema] = useState<string | null>(null);
  const [sidebarTab, setSidebarTab] = useState<'schema' | 'saved' | 'history'>('schema');
  const [resultsTab, setResultsTab] = useState<'results' | 'plan' | 'logs'>('results');
  const [showAIAssist, setShowAIAssist] = useState(false);
  const [isFederated, setIsFederated] = useState(false); // B13: Federated Trino query toggle
  const [schemas, setSchemas] = useState<SchemaItem[]>([]);
  const [queryHistory, setQueryHistory] = useState<QueryHistoryItem[]>([]);
  const { data: capabilityRegistry } = useCapabilityRegistry();
  const { data: activityData } = useQuery({
    queryKey: ['user-activity', 'sql-workspace'],
    queryFn: () => getRecentActivity(),
    staleTime: 30_000,
    retry: false,
  });

  const analyticsCapability = getCapabilitySignal(capabilityRegistry?.capabilities, ['analytics']);
  const federatedCapability = getCapabilitySignal(capabilityRegistry?.capabilities, ['trino', 'federated_query', 'federatedQuery']);
  const aiAssistCapability = getCapabilitySignal(capabilityRegistry?.capabilities, ['ai_assist', 'aiAssist', 'assist']);
  const capabilitySubset = capabilityRegistry?.capabilities.filter((capability) =>
    ['analytics', 'trino', 'federated_query', 'federatedQuery', 'ai_assist', 'aiAssist', 'assist', 'voice'].includes(capability.key),
  ) ?? [];
  const federatedUnavailable = federatedCapability?.status !== 'active';
  const executionRecommendation = useMemo(
    () => recommendAnalyticsExecution(query, !federatedUnavailable),
    [query, federatedUnavailable],
  );
  const queryPlanGuardrails = useMemo(
    () => deriveQueryPlanGuardrails(query, queryPlan, executionRecommendation),
    [query, queryPlan, executionRecommendation],
  );

  useEffect(() => {
    collectionsApi.list().then((res) => {
      const items: SchemaItem[] = res.items.map((col) => ({
        name: col.name,
        tables: [col.name],
      }));
      setSchemas(items);
      if (items.length > 0) setExpandedSchema(items[0].name);
    }).catch(() => {/* schema load is non-critical */});
  }, []);

  useEffect(() => {
    const routeState = location.state as { query?: string } | null;
    if (routeState?.query) {
      setQuery(routeState.query);
    }
  }, [location.state]);

  useEffect(() => {
    setQueryPlan(null);
    setQueryPlanError(null);
  }, [query]);

  const handleSelectSavedQuery = (savedQuery: SavedQuery) => {
    setQuery(savedQuery.sql);
  };

  const handleRunQuery = useCallback(async () => {
    if (!query.trim()) return;
    setIsRunning(true);
    setResultsTab('results');
    setQueryError(null);
    try {
      // B13: Route through federated Trino connector when toggle is active
      const result = isFederated
          ? await executeFederatedQuery(query.trim())
          : await executeAnalyticsQuery(query.trim());
      setQueryResult(result);
      setQueryHistory((prev) => [
        {
          id: Date.now(),
          query: query.trim(),
          timestamp: 'just now',
          duration: `${result.executionTimeMs}ms`,
        },
        ...prev.slice(0, 19),
      ]);
    } catch (err) {
      setQueryError(err instanceof Error ? err.message : 'Query failed');
      setQueryResult(null);
    } finally {
      setIsRunning(false);
    }
  }, [query, isFederated]);

  const handleExplainQuery = useCallback(async () => {
    if (!query.trim()) {
      return;
    }

    setIsExplaining(true);
    setResultsTab('plan');
    setQueryPlanError(null);

    try {
      const plan = await explainAnalyticsQuery(query.trim());
      setQueryPlan(plan);
    } catch (error) {
      setQueryPlan(null);
      setQueryPlanError(error instanceof Error ? error.message : 'Explain plan failed');
    } finally {
      setIsExplaining(false);
    }
  }, [query]);

  const handleApplyAISql = useCallback((sql: string) => {
    setQuery(sql);
    setShowAIAssist(false);
  }, []);

  return (
    <div className={cn('min-h-screen', bgStyles.page)} data-testid="sql-workspace-page">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        {/* Header */}
        <div className={cn(cardStyles.base, cardStyles.padded, 'mb-6')} data-testid="sql-workspace-header">
          <div className="flex items-center justify-between">
            <div>
              <h1 className={textStyles.h1}>SQL Workspace</h1>
              <p className={textStyles.muted}>
                Run ad-hoc queries against your datasets with schema-aware assistance
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setShowAIAssist(!showAIAssist)}
                title={aiAssistCapability?.detail ?? 'AI assist status is derived from the capability registry and current deployment behavior.'}
                data-testid="sql-ai-assist-toggle"
                className={cn(
                  buttonStyles.secondary,
                  'flex items-center gap-2',
                  showAIAssist && 'bg-purple-100 dark:bg-purple-900/30 border-purple-300 dark:border-purple-700'
                )}
              >
                <Sparkles className="h-4 w-4" />
                AI Assist
              </button>
              {/* B13: Federated Trino query toggle */}
              <button
                onClick={() => setIsFederated((f) => !f)}
                title={federatedUnavailable
                  ? federatedCapability?.detail ?? SQL_FEDERATED_QUERY_UNAVAILABLE_DETAIL
                  : isFederated
                    ? 'Using Trino federated query (all tiers)'
                    : 'Using direct analytics engine'}
                disabled={federatedUnavailable}
                data-testid="sql-engine-toggle"
                className={cn(
                  buttonStyles.secondary,
                  'flex items-center gap-2',
                  isFederated && 'bg-blue-100 dark:bg-blue-900/30 border-blue-400 dark:border-blue-600 text-blue-700 dark:text-blue-300',
                  federatedUnavailable && 'opacity-50 cursor-not-allowed'
                )}
              >
                <Network className="h-4 w-4" />
                {isFederated ? 'Federated' : 'Direct'}
              </button>
              <button className={cn(buttonStyles.secondary, 'flex items-center gap-2')}>
                <Save className="h-4 w-4" />
                Save Query
              </button>
              <button
                onClick={() => { void handleExplainQuery(); }}
                disabled={isExplaining}
                data-testid="sql-explain-query"
                className={cn(buttonStyles.secondary, 'flex items-center gap-2')}
              >
                <FileText className="h-4 w-4" />
                {isExplaining ? 'Explaining...' : 'Explain'}
              </button>
              <button
                onClick={() => { void handleRunQuery(); }}
                disabled={isRunning}
                data-testid="sql-run-query"
                className={cn(buttonStyles.primary, 'flex items-center gap-2')}
              >
                <Play className="h-4 w-4" />
                {isRunning ? 'Running...' : 'Run Query'}
              </button>
            </div>
          </div>
        </div>

        <div className={cn(cardStyles.base, cardStyles.padded, 'mb-6')} data-testid="sql-recommendation-panel">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className={textStyles.h3}>{executionRecommendation.title}</h2>
              <p className={textStyles.muted}>{executionRecommendation.detail}</p>
            </div>
            {!executionRecommendation.requiresReview && (
              <button
                onClick={() => setIsFederated(executionRecommendation.path === 'federated')}
                data-testid="sql-use-recommended-path"
                className={cn(buttonStyles.secondary, 'flex items-center gap-2 self-start lg:self-auto')}
              >
                <Network className="h-4 w-4" />
                Use recommended path
              </button>
            )}
          </div>
        </div>

        {capabilitySubset.length > 0 && (
          <div className="mb-6" data-testid="sql-capability-truth-panel">
            <CapabilityTruthPanel
              title="Query Runtime Truth"
              description="This workspace reads the live capability registry before enabling optional query paths."
              capabilities={capabilitySubset}
              compact
            />
          </div>
        )}

        {(analyticsCapability?.status === 'degraded' || analyticsCapability?.status === 'unavailable' || federatedCapability?.status === 'degraded' || federatedCapability?.status === 'unavailable') && (
          <div className={cn(cardStyles.base, cardStyles.padded, 'mb-6 border-amber-300 bg-amber-50 text-amber-900')} data-testid="sql-optional-dependencies-warning">
            <div className="flex items-start gap-3">
              <AlertTriangle className="mt-0.5 h-5 w-5" />
              <div>
                <h2 className={textStyles.h3}>{SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_TITLE}</h2>
                <p className={textStyles.muted}>
                  {analyticsCapability?.detail ?? federatedCapability?.detail ?? SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_DETAIL}
                </p>
              </div>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Schema Browser Sidebar */}
          <div className="lg:col-span-1 space-y-4">
            {/* Sidebar Tabs */}
            <div className={cn(cardStyles.base, 'overflow-hidden')} data-testid="sql-sidebar-panel">
              <div className="flex border-b border-gray-200 dark:border-gray-700">
                <button
                  onClick={() => setSidebarTab('schema')}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium',
                    sidebarTab === 'schema'
                      ? 'bg-white dark:bg-gray-800 border-b-2 border-blue-500 text-blue-600'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  <Database className="h-4 w-4" />
                  Schema
                </button>
                <button
                  onClick={() => setSidebarTab('saved')}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium',
                    sidebarTab === 'saved'
                      ? 'bg-white dark:bg-gray-800 border-b-2 border-blue-500 text-blue-600'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  <Bookmark className="h-4 w-4" />
                  Saved
                </button>
                <button
                  onClick={() => setSidebarTab('history')}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium',
                    sidebarTab === 'history'
                      ? 'bg-white dark:bg-gray-800 border-b-2 border-blue-500 text-blue-600'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  <Clock className="h-4 w-4" />
                  History
                </button>
              </div>

              {/* Schema Tab */}
              {sidebarTab === 'schema' && (
                <div className="p-2">
                  {schemas.map((schema) => (
                    <div key={schema.name} className="mb-1">
                      <button
                        onClick={() => setExpandedSchema(expandedSchema === schema.name ? null : schema.name)}
                        className={cn(
                          'w-full flex items-center gap-2 px-2 py-1.5 rounded text-left text-sm',
                          'hover:bg-gray-100 dark:hover:bg-gray-700'
                        )}
                      >
                        <ChevronRight
                          className={cn(
                            'h-4 w-4 transition-transform',
                            expandedSchema === schema.name && 'rotate-90'
                          )}
                        />
                        <span className={textStyles.h4}>{schema.name}</span>
                      </button>
                      {expandedSchema === schema.name && (
                        <div className="ml-6 mt-1 space-y-1">
                          {schema.tables.map((table) => (
                            <button
                              key={table}
                              onClick={() => setQuery((q) => q + `\n-- ${schema.name}.${table}`)}
                              className={cn(
                                'w-full text-left px-2 py-1 text-sm rounded',
                                'hover:bg-blue-50 dark:hover:bg-blue-900/30',
                                textStyles.small
                              )}
                            >
                              📋 {table}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* Saved Queries Tab */}
              {sidebarTab === 'saved' && (
                <SavedQueries
                  onSelect={handleSelectSavedQuery}
                  currentSql={query}
                  className="h-[400px]"
                />
              )}

              {/* History Tab */}
              {sidebarTab === 'history' && (
                <div className="p-2 space-y-1">
                  {queryHistory.length === 0 ? (
                    <p className={cn(textStyles.xs, 'p-3 text-center')}>No queries run yet.</p>
                  ) : queryHistory.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => setQuery(item.query)}
                      className={cn(
                        'w-full text-left p-2 rounded text-sm',
                        'hover:bg-gray-100 dark:hover:bg-gray-700'
                      )}
                    >
                      <p className={cn(textStyles.mono, 'truncate text-xs')}>{item.query}</p>
                      <p className={cn(textStyles.xs, 'mt-1')}>
                        {item.timestamp} • {item.duration}
                      </p>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Editor and Results */}
          <div className="lg:col-span-3 space-y-6">
            {/* AI Assist Panel */}
            {showAIAssist && (
              <AIQueryAssist
                onApply={handleApplyAISql}
                schemas={schemas}
                recentActivity={activityData?.activities ?? []}
                continueWorking={activityData?.continueWorking ?? []}
              />
            )}

            {/* SQL Editor */}
            <div className={cn(cardStyles.base)} data-testid="sql-editor-panel">
              <div className={cn(cardStyles.header, 'flex items-center justify-between')}>
                <h3 className={textStyles.h4}>Query Editor</h3>
                <div className="flex gap-2">
                  <button className={cn(buttonStyles.ghost, buttonStyles.sm)}>Format</button>
                  <button className={cn(buttonStyles.ghost, buttonStyles.sm)}>Clear</button>
                </div>
              </div>
              <div className="p-4">
                <textarea
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="-- Write your SQL query here
SELECT * FROM your_table
LIMIT 100;"
                  className={cn(
                    'w-full h-48 font-mono text-sm p-4 rounded-lg resize-y',
                    'bg-gray-50 dark:bg-gray-900',
                    'border border-gray-200 dark:border-gray-700',
                    'text-gray-900 dark:text-white',
                    'focus:ring-2 focus:ring-blue-500 focus:border-transparent'
                  )}
                  data-testid="sql-editor"
                />
              </div>
            </div>

            {/* Results */}
            <div className={cn(cardStyles.base)} data-testid="sql-results-panel">
              <div className={cn(cardStyles.header, 'flex items-center justify-between')}>
                <div className="flex items-center gap-4">
                  <h3 className={textStyles.h4}>Results</h3>
                  {resultsTab === 'results' && queryResult && (
                    <span className={textStyles.xs}>
                      {queryResult.rowCount} rows • {queryResult.executionTimeMs}ms
                    </span>
                  )}
                  {resultsTab === 'plan' && queryPlan && (
                    <span className={textStyles.xs}>
                      {queryPlan.queryType} • cost {Math.round(queryPlan.estimatedCost)}
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setResultsTab('results')}
                    data-testid="sql-results-tab"
                    className={cn(buttonStyles.ghost, buttonStyles.sm, resultsTab === 'results' && 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200')}
                  >
                    Results
                  </button>
                  <button
                    onClick={() => setResultsTab('plan')}
                    data-testid="sql-plan-tab"
                    className={cn(buttonStyles.ghost, buttonStyles.sm, resultsTab === 'plan' && 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200')}
                  >
                    Plan
                  </button>
                  <button
                    onClick={() => setResultsTab('logs')}
                    data-testid="sql-logs-tab"
                    className={cn(buttonStyles.ghost, buttonStyles.sm, resultsTab === 'logs' && 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200')}
                  >
                    Logs
                  </button>
                  {resultsTab === 'results' && queryResult && (
                    <button className={cn(buttonStyles.ghost, buttonStyles.sm, 'flex items-center gap-1')}>
                      <Download className="h-3 w-3" />
                      Export CSV
                    </button>
                  )}
                </div>
              </div>

              {resultsTab === 'results' && queryError && (
                <div className="p-4 text-sm text-red-600 bg-red-50 dark:bg-red-900/20 rounded-b-lg" data-testid="sql-query-error">
                  {queryError}
                </div>
              )}

              {resultsTab === 'results' && queryResult ? (
                <div className={tableStyles.container} data-testid="sql-query-results">
                  <table className={tableStyles.table}>
                    <thead className={tableStyles.thead}>
                      <tr>
                        {queryResult.rows.length > 0
                          ? Object.keys(queryResult.rows[0]).map((col) => (
                              <th key={col} className={tableStyles.th}>{col}</th>
                            ))
                          : null}
                      </tr>
                    </thead>
                    <tbody className={tableStyles.tbody}>
                      {queryResult.rows.map((row, i) => (
                        <tr key={i} className={tableStyles.tr}>
                          {Object.values(row).map((cell, j) => (
                            <td key={j} className={cn(tableStyles.td, 'font-mono text-xs')}>
                              {cell === null || cell === undefined ? <em className="text-gray-400">null</em> : String(cell)}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : !queryError ? (
                <div className={cn(textStyles.muted, 'text-center py-12')}>
                  {isRunning ? (
                    <div className="flex items-center justify-center gap-2">
                      <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                      <span>Executing query...</span>
                    </div>
                  ) : (
                    'Run a query to see results'
                  )}
                </div>
              ) : null}

              {resultsTab === 'plan' && (
                <div className="p-4 space-y-4">
                  {queryPlanError && (
                    <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-200" data-testid="sql-query-plan-error">
                      {queryPlanError}
                    </div>
                  )}

                  {queryPlan ? (
                    <div className="space-y-4" data-testid="sql-query-plan">
                      <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                        <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-900/40">
                          <p className="text-xs uppercase tracking-wide text-gray-500">Query type</p>
                          <p className="mt-2 text-sm font-semibold text-gray-900 dark:text-gray-100">{queryPlan.queryType}</p>
                        </div>
                        <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-900/40">
                          <p className="text-xs uppercase tracking-wide text-gray-500">Estimated cost</p>
                          <p className="mt-2 text-sm font-semibold text-gray-900 dark:text-gray-100">{Math.round(queryPlan.estimatedCost)}</p>
                        </div>
                        <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-900/40">
                          <p className="text-xs uppercase tracking-wide text-gray-500">Optimized</p>
                          <p className="mt-2 text-sm font-semibold text-gray-900 dark:text-gray-100">{queryPlan.optimized ? 'Yes' : 'No'}</p>
                        </div>
                        <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-900/40">
                          <p className="text-xs uppercase tracking-wide text-gray-500">Planned at</p>
                          <p className="mt-2 text-sm font-semibold text-gray-900 dark:text-gray-100">{queryPlan.timestamp}</p>
                        </div>
                      </div>

                      <div className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-900/40">
                        <p className="text-xs uppercase tracking-wide text-gray-500">Data sources</p>
                        <div className="mt-3 flex flex-wrap gap-2">
                          {queryPlan.dataSources.map((source) => (
                            <span key={source} className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-200">
                              {source}
                            </span>
                          ))}
                        </div>
                      </div>

                      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-900/40 dark:bg-amber-900/10" data-testid="sql-query-plan-guardrails">
                        <p className="text-xs uppercase tracking-wide text-amber-700 dark:text-amber-200">Execution guardrails</p>
                        <div className="mt-3 space-y-3">
                          {queryPlanGuardrails.length > 0 ? queryPlanGuardrails.map((guardrail) => (
                            <div key={`${guardrail.severity}-${guardrail.title}`} className="rounded-lg bg-white/80 px-3 py-3 dark:bg-gray-900/40">
                              <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{guardrail.title}</p>
                              <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{guardrail.detail}</p>
                            </div>
                          )) : (
                            <p className="text-sm text-amber-900 dark:text-amber-100">No additional guardrails fired for the current plan.</p>
                          )}
                        </div>
                      </div>
                    </div>
                  ) : !queryPlanError ? (
                    <div className={cn(textStyles.muted, 'py-10 text-center')}>
                      {isExplaining ? 'Explaining query shape...' : 'Explain this query to inspect the planned path, cost, and review guardrails.'}
                    </div>
                  ) : null}
                </div>
              )}

              {resultsTab === 'logs' && (
                <div className="p-4 space-y-4" data-testid="sql-query-logs">
                  <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
                    <p className="text-xs uppercase tracking-wide text-gray-500">Current recommendation</p>
                    <p className="mt-2 text-sm font-semibold text-gray-900 dark:text-gray-100">{executionRecommendation.title}</p>
                    <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{executionRecommendation.detail}</p>
                  </div>

                  <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
                    <p className="text-xs uppercase tracking-wide text-gray-500">Recent query trail</p>
                    <div className="mt-3 space-y-2">
                      {queryHistory.length > 0 ? queryHistory.slice(0, 5).map((item) => (
                        <div key={item.id} className="rounded-lg bg-white px-3 py-3 dark:bg-gray-900/40">
                          <p className="text-xs font-mono text-gray-700 dark:text-gray-200">{item.query}</p>
                          <p className="mt-1 text-xs text-gray-500">{item.timestamp} • {item.duration}</p>
                        </div>
                      )) : (
                        <p className="text-sm text-gray-500">Run or explain a query to build an operational trail.</p>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

