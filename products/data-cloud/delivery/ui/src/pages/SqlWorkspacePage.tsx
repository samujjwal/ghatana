/**
 * SQL Workspace Page
 *
 * Full-featured SQL workspace for authoring, running, and managing queries.
 * See spec: docs/web-page-specs/14_sql_workspace_page.md
 *
 * Features:
 * - Schema-aware SQL editor
 * - Natural language query assistance
 * - Natural language to SQL conversion
 * - Query optimization suggestions
 *
 * @doc.type page
 * @doc.purpose SQL query editor with natural language assistance
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
  Terminal,
} from 'lucide-react';
import { TrustSignalGroup } from '../components/common/TrustSignalGroup';
import type { TrustSignalDescriptor } from '../components/common/TrustSignalGroup';
import { Table, Button } from '@ghatana/design-system';
import {
  cn,
  cardStyles,
  textStyles,
  bgStyles,
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
  evaluateQueryPolicy,
  executeFederatedQuery,
  fetchAnalyticsQuerySuggestions,
  type AnalyticsExplainResult,
  type QueryPolicyEvaluation,
  type QueryResultData,
} from '../api/analytics.service';
import { getCapabilitySignal, useCapabilityRegistry } from '../api/surfaces.service';
import { CapabilityTruthPanel } from '../components/capabilities/CapabilityTruthPanel';
import { aiOperationsService } from '../api/ai-operations.service';
import { UnsupportedRuntimeBoundaryError } from '../lib/runtime-boundaries';
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

export interface QueryRewriteSuggestion {
  original: string;
  rewritten: string;
  reason: string;
  severity: 'suggestion' | 'safety' | 'performance';
}

export function suggestQueryRewrites(
  sql: string,
  inferredScope: InferredQueryScope,
): QueryRewriteSuggestion[] {
  const suggestions: QueryRewriteSuggestion[] = [];
  const normalized = sql.trim();

  // Suggest LIMIT for unbounded SELECT *
  if (/\bselect\s+\*/i.test(normalized) && !/\blimit\b/i.test(normalized)) {
    const rewritten = normalized.replace(/(\bselect\s+\*\s+from)/i, '$1') + ' LIMIT 1000';
    suggestions.push({
      original: normalized,
      rewritten,
      reason: 'Added LIMIT 1000 to prevent wide scans and reduce cost.',
      severity: 'safety',
    });
  }

  // Suggest time window if inferred and not present
  if (inferredScope.timeWindow && !/\bwhere\b/i.test(normalized)) {
    const timeClause = `WHERE timestamp >= NOW() - INTERVAL '${inferredScope.timeWindow}'`;
    const rewritten = normalized.replace(/(\bfrom\s+\w+)/i, `$1 ${timeClause}`);
    suggestions.push({
      original: normalized,
      rewritten,
      reason: `Added time window filter based on inferred intent: ${inferredScope.timeWindow}.`,
      severity: 'suggestion',
    });
  }

  // Suggest column list instead of SELECT *
  if (/\bselect\s+\*/i.test(normalized)) {
    suggestions.push({
      original: normalized,
      rewritten: normalized.replace(/\bselect\s+\*/i, 'SELECT <specific_columns>'),
      reason: 'Replace SELECT * with specific columns to reduce data transfer and improve query performance.',
      severity: 'performance',
    });
  }

  return suggestions;
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
          placeholder="Describe your query with specific details: 'Show me users who signed up in the last 7 days, ordered by registration date descending, limit 100'"
          aria-label="Natural language query description"
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

      {/* P2-1: NLQ clarification guidance */}
      <div className="mb-3 text-xs text-gray-500 dark:text-gray-400">
        <span className="font-medium">Tip:</span> Be specific about columns, filters, sorting, and limits. 
        Example: "Select name, email from users where created_at &gt; '2024-01-01' order by created_at desc limit 50"
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
  const [policyEvaluation, setPolicyEvaluation] = useState<QueryPolicyEvaluation | null>(null);
  const [policyEvaluationPending, setPolicyEvaluationPending] = useState(false);
  const [policyOverrideConfirmed, setPolicyOverrideConfirmed] = useState(false);
  const [expandedSchema, setExpandedSchema] = useState<string | null>(null);
  const [sidebarTab, setSidebarTab] = useState<'schema' | 'saved' | 'history'>('schema');
  const [resultsTab, setResultsTab] = useState<'results' | 'plan' | 'logs'>('results');
  const [showAIAssist, setShowAIAssist] = useState(false); // UX-001: AI assist opened on demand
  const [showSQLEditor, setShowSQLEditor] = useState(false); // UX-001: SQL editor is secondary / progressive disclosure
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
  // DC-UX-021: canonical capability key is 'query_assist'; legacy aliases 'ai_assist'/'aiAssist'/'assist' kept for backward compat
  const aiAssistCapability = getCapabilitySignal(capabilityRegistry?.capabilities, ['query_assist', 'ai_assist', 'aiAssist', 'assist']);
  const capabilitySubset = capabilityRegistry?.capabilities.filter((capability) =>
    // DC-UX-021: canonical key 'query_assist'; legacy aliases included for backward compat
    ['analytics', 'trino', 'federated_query', 'federatedQuery', 'query_assist', 'ai_assist', 'aiAssist', 'assist', 'voice'].includes(capability.key),
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
    }).catch(() => {/* schema load is non-critical */ });
  }, []);

  useEffect(() => {
    const routeState = location.state as { query?: string } | null;
    if (routeState?.query) {
      setQuery(routeState.query);
    }
  }, [location.state]);

  // AI quality advisory for the currently expanded schema collection.
  // Enabled only when the ai_assist capability is active, backend-first, boundary-aware.
  const qualityAdvisoryCollectionId = expandedSchema ?? schemas[0]?.name ?? null;
  const qualityAdvisoryQuery = useQuery({
    queryKey: ['ai', 'advisories', 'quality', qualityAdvisoryCollectionId],
    queryFn: () => aiOperationsService.getQualityAdvisories(qualityAdvisoryCollectionId!),
    staleTime: 5 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
    enabled: aiAssistCapability?.status !== 'unavailable' && qualityAdvisoryCollectionId !== null,
  });
  const qualityAdvisories = qualityAdvisoryQuery.data?.advisories ?? [];
  const qualityAdvisoryBoundary = qualityAdvisoryQuery.error instanceof UnsupportedRuntimeBoundaryError;

  useEffect(() => {
    setQueryPlan(null);
    setQueryPlanError(null);
    setPolicyEvaluation(null);
    setPolicyOverrideConfirmed(false);
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
      setPolicyEvaluationPending(true);
      const evaluation = await evaluateQueryPolicy(query.trim());
      setPolicyEvaluation(evaluation);
      setPolicyEvaluationPending(false);

      if (evaluation.verdict === 'deny') {
        setQueryError('Execution blocked by policy evaluation. Review the policy decision details and modify the query.');
        setQueryResult(null);
        return;
      }

      if (evaluation.verdict === 'review' && !policyOverrideConfirmed) {
        setQueryError('Policy review is required. Acknowledge the risk in the policy panel before running this query.');
        setQueryResult(null);
        return;
      }

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
      setPolicyEvaluationPending(false);
      setQueryError(err instanceof Error ? err.message : 'Query failed');
      setQueryResult(null);
    } finally {
      setPolicyEvaluationPending(false);
      setIsRunning(false);
    }
  }, [query, isFederated, policyOverrideConfirmed]);

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
    setShowSQLEditor(true); // Reveal the SQL editor so the applied query is visible
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
                Ask a question in plain English, or write SQL directly when you need control
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                leadingIcon={<Sparkles className="h-4 w-4" />}
                onClick={() => setShowAIAssist(!showAIAssist)}
                title={aiAssistCapability?.detail ?? 'AI assist status is derived from the capability registry and current deployment behavior.'}
                data-testid="sql-ai-assist-toggle"
                className={cn(showAIAssist && 'border-purple-400 dark:border-purple-600')}
              >
                {showAIAssist ? 'Hide AI Assist' : 'AI Assist'}
              </Button>
              <Button
                variant="outline"
                leadingIcon={<Terminal className="h-4 w-4" />}
                onClick={() => setShowSQLEditor(!showSQLEditor)}
                data-testid="sql-editor-toggle"
                className={cn(showSQLEditor && 'border-blue-400 dark:border-blue-600')}
              >
                {showSQLEditor ? 'Hide SQL Editor' : 'Edit SQL'}
              </Button>
              {/* B13: Federated Trino query toggle */}
              <Button
                variant="outline"
                leadingIcon={<Network className="h-4 w-4" />}
                onClick={() => setIsFederated((f) => !f)}
                title={federatedUnavailable
                  ? federatedCapability?.detail ?? SQL_FEDERATED_QUERY_UNAVAILABLE_DETAIL
                  : isFederated
                    ? 'Using Trino federated query (all tiers)'
                    : 'Using direct analytics engine'}
                disabled={federatedUnavailable}
                data-testid="sql-engine-toggle"
                className={cn(isFederated && 'border-blue-400 dark:border-blue-600 text-blue-700 dark:text-blue-300')}
              >
                {isFederated ? 'Federated' : 'Direct'}
              </Button>
              <Button
                variant="outline"
                leadingIcon={<Save className="h-4 w-4" />}
              >
                Save Query
              </Button>
              <Button
                variant="outline"
                leadingIcon={<FileText className="h-4 w-4" />}
                loading={isExplaining}
                onClick={() => { void handleExplainQuery(); }}
                disabled={isExplaining}
                data-testid="sql-explain-query"
              >
                {isExplaining ? 'Explaining...' : 'Explain'}
              </Button>
              <Button
                variant="solid"
                leadingIcon={<Play className="h-4 w-4" />}
                loading={isRunning}
                onClick={() => { void handleRunQuery(); }}
                disabled={isRunning || policyEvaluationPending}
                data-testid="sql-run-query"
              >
                {isRunning ? 'Running...' : policyEvaluationPending ? 'Evaluating policy...' : 'Run Query'}
              </Button>
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
              <Button
                variant="outline"
                size="sm"
                leadingIcon={<Network className="h-4 w-4" />}
                onClick={() => setIsFederated(executionRecommendation.path === 'federated')}
                data-testid="sql-use-recommended-path"
              >
                Use recommended path
              </Button>
            )}
          </div>
        </div>

        <div className={cn(cardStyles.base, cardStyles.padded, 'mb-6')} data-testid="sql-policy-panel">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className={textStyles.h3}>Policy & Risk Evaluation</h2>
              <p className={textStyles.muted}>
                Query execution is gated by policy checks when available. If the policy backend is unavailable, deterministic heuristics are applied as a fallback.
              </p>
            </div>
            <span className={cn(
              'rounded-full px-2.5 py-1 text-xs font-semibold uppercase tracking-wide',
              policyEvaluation?.verdict === 'deny'
                ? 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300'
                : policyEvaluation?.verdict === 'review'
                  ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'
                  : 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
            )}>
              {policyEvaluation ? policyEvaluation.verdict : 'not evaluated'}
            </span>
          </div>
          <div className="mt-4 space-y-2 text-sm text-gray-700 dark:text-gray-300">
            <p>
              Source: <strong>{policyEvaluation?.source ?? 'pending'}</strong>
              {policyEvaluation?.confidence !== undefined && (
                <span> · Confidence {Math.round(policyEvaluation.confidence * 100)}%</span>
              )}
            </p>
            {policyEvaluation?.reasons && policyEvaluation.reasons.length > 0 ? (
              <ul className="list-disc space-y-1 pl-5">
                {policyEvaluation.reasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-gray-500">Run or explain the query to populate policy reasoning.</p>
            )}
            {policyEvaluation?.verdict === 'review' && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-900 dark:bg-amber-950/30">
                <label className="inline-flex items-center gap-2 text-sm font-medium text-amber-900 dark:text-amber-200">
                  <input
                    type="checkbox"
                    checked={policyOverrideConfirmed}
                    onChange={(event) => setPolicyOverrideConfirmed(event.target.checked)}
                    data-testid="sql-policy-review-ack"
                  />
                  I reviewed this risk and want to proceed with execution.
                </label>
              </div>
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
              <div className="flex-1">
                <h2 className={textStyles.h3}>{SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_TITLE}</h2>
                <div className="space-y-2">
                  {/* P0-3: Explicit degradation indicator for Analytics */}
                  {(analyticsCapability?.status === 'degraded' || analyticsCapability?.status === 'unavailable') && (
                    <div className="text-sm">
                      <span className="font-semibold">Analytics Engine:</span>{' '}
                      <span className={cn(analyticsCapability?.status === 'degraded' ? 'text-amber-700' : 'text-red-700')}>
                        {analyticsCapability?.status === 'degraded' ? 'DEGRADED' : 'UNAVAILABLE'}
                      </span>
                      {analyticsCapability?.detail && (
                        <span className="block text-xs text-amber-800 mt-1">{analyticsCapability.detail}</span>
                      )}
                    </div>
                  )}
                  {/* P0-3: Explicit degradation indicator for Federated Query */}
                  {(federatedCapability?.status === 'degraded' || federatedCapability?.status === 'unavailable') && (
                    <div className="text-sm">
                      <span className="font-semibold">Federated Query:</span>{' '}
                      <span className={cn(federatedCapability?.status === 'degraded' ? 'text-amber-700' : 'text-red-700')}>
                        {federatedCapability?.status === 'degraded' ? 'DEGRADED' : 'UNAVAILABLE'}
                      </span>
                      {federatedCapability?.detail && (
                        <span className="block text-xs text-amber-800 mt-1">{federatedCapability.detail}</span>
                      )}
                    </div>
                  )}
                  {!analyticsCapability?.detail && !federatedCapability?.detail && (
                    <p className={textStyles.muted}>{SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_DETAIL}</p>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* UX-001: Question-first layout — AI Assist spans full width when visible */}
        {showAIAssist && (
          <div className="mb-6 space-y-4" data-testid="sql-ai-assist-primary">
            <AIQueryAssist
              onApply={handleApplyAISql}
              schemas={schemas}
              recentActivity={activityData?.activities ?? []}
              continueWorking={activityData?.continueWorking ?? []}
            />
            {/* AI data quality advisory for the selected collection */}
            {qualityAdvisoryCollectionId !== null && !qualityAdvisoryBoundary && qualityAdvisories.length > 0 && (
              <div
                className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30 p-4"
                data-testid="sql-ai-quality-advisory"
              >
                <p className="text-sm font-medium text-amber-900 dark:text-amber-200 mb-2">
                  Data quality advisories — {qualityAdvisoryCollectionId}
                </p>
                <div className="space-y-1.5">
                  {qualityAdvisories.slice(0, 4).map((advisory) => (
                    <div key={advisory.id} className="flex items-start gap-2 text-sm">
                      <span className="shrink-0 text-xs bg-amber-100 dark:bg-amber-900 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded capitalize">
                        {advisory.type}
                      </span>
                      <span className="text-gray-700 dark:text-gray-300 flex-1">{advisory.description}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
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
            {/* SQL Editor — progressive disclosure, secondary to question-first UX */}
            {showSQLEditor && (
              <div className={cn(cardStyles.base)} data-testid="sql-editor-panel">
                <div className={cn(cardStyles.header, 'flex items-center justify-between')}>
                  <h3 className={textStyles.h4}>Query Editor</h3>
                  <div className="flex gap-2">
                    <Button variant="ghost" size="sm">Format</Button>
                    <Button variant="ghost" size="sm">Clear</Button>
                  </div>
                </div>
                <div className="p-4">
                  <textarea
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="-- Write your SQL query here
SELECT * FROM your_table
LIMIT 100;"
                    aria-label="SQL query editor"
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
            )}

            {/* Trust Signals — query execution context (DC-UX-045: policy-derived via TrustSignalGroup) */}
            <TrustSignalGroup
              accessLevel="tenant"
              data-testid="sql-trust-signals"
              signals={[
                // DC-UX-022: Per-result sensitivity omitted until execution API provides metadata
                ...(executionRecommendation.requiresReview
                  ? [{ status: 'warning' as TrustSignalDescriptor['status'], label: 'Review required before execution' }]
                  : []),
                ...(queryPlan && queryPlan.dataSources.length > 1
                  ? [{ status: 'warning' as TrustSignalDescriptor['status'], label: `Cross-source query (${queryPlan.dataSources.length} sources)` }]
                  : []),
              ]}
            />

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
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setResultsTab('results')}
                    data-testid="sql-results-tab"
                    className={cn(resultsTab === 'results' && 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200')}
                  >
                    Results
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setResultsTab('plan')}
                    data-testid="sql-plan-tab"
                    className={cn(resultsTab === 'plan' && 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200')}
                  >
                    Plan
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setResultsTab('logs')}
                    data-testid="sql-logs-tab"
                    className={cn(resultsTab === 'logs' && 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200')}
                  >
                    Logs
                  </Button>
                  {resultsTab === 'results' && queryResult && (
                    <Button
                      variant="ghost"
                      size="sm"
                      leadingIcon={<Download className="h-3 w-3" />}
                    >
                      Export CSV
                    </Button>
                  )}
                </div>
              </div>

              {resultsTab === 'results' && queryError && (
                <div className="p-4 text-sm text-red-600 bg-red-50 dark:bg-red-900/20 rounded-b-lg" data-testid="sql-query-error">
                  {queryError}
                </div>
              )}

              {resultsTab === 'results' && queryResult ? (
                <div data-testid="sql-query-results">
                  <Table<Record<string, unknown>>
                    columns={
                      queryResult.rows.length > 0
                        ? Object.keys(queryResult.rows[0]).map((col) => ({
                          key: col,
                          header: col,
                          accessor: (row: Record<string, unknown>) => {
                            const cell = row[col];
                            return cell === null || cell === undefined ? (
                              <em className="text-gray-400">null</em>
                            ) : (
                              String(cell)
                            );
                          },
                        }))
                        : []
                    }
                    data={queryResult.rows}
                    size="sm"
                  />
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
                        {/* DC-UX-023: Clearly labelled as advisory/heuristic, not policy-enforced */}
                        <div className="flex items-center justify-between mb-1">
                          <p className="text-xs uppercase tracking-wide text-amber-700 dark:text-amber-200">Query advisories</p>
                          <span className="text-xs text-amber-600 dark:text-amber-300 italic">Local heuristics — not policy-enforced</span>
                        </div>
                        <div className="mt-3 space-y-3">
                          {queryPlanGuardrails.length > 0 ? queryPlanGuardrails.map((guardrail) => (
                            <div key={`${guardrail.severity}-${guardrail.title}`} className="rounded-lg bg-white/80 px-3 py-3 dark:bg-gray-900/40">
                              <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{guardrail.title}</p>
                              <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{guardrail.detail}</p>
                            </div>
                          )) : (
                            <p className="text-sm text-amber-900 dark:text-amber-100">No advisories for the current query shape.</p>
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

