/**
 * Insights Page
 *
 * Unified operator insights surface combining:
 * - AI brain status and heuristics
 * - Analytics summaries and assisted query flows
 * - Cost optimization review
 *
 * @doc.type page
 * @doc.purpose Unified analytics and AI insights
 * @doc.layer frontend
 */

import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import SessionBootstrap, { type SessionSnapshot } from '../lib/auth/session';
import {
  INSIGHTS_CAPABILITY_SNAPSHOT_NOTE,
  INSIGHTS_REGISTRY_REQUEST_NOTE,
} from '../lib/runtime-boundaries';
import {
  getCapabilitySignal,
  useCapabilityRegistry,
  type CapabilitySignal,
} from '../api/capabilities.service';
import { brainService, type BrainStats } from '../api/brain.service';
import { costService, type CostBreakdown } from '../api/cost.service';
import { workflowsApi } from '../lib/api/workflows';
import {
  useCollectionEntityCounts,
  useAnalyticsQuery,
  useAnalyticsAiSuggestions,
  type AnalyticsAiSuggestion,
} from '../api/analytics.service';
import {
  useAiQualitySummary,
  type AiQualitySummaryResult,
} from '../api/ai-observability.service';
import { collectionsApi } from '../lib/api/collections';
import {
  Brain,
  BarChart3,
  DollarSign,
  Activity,
  TrendingUp,
  TrendingDown,
  Sparkles,
  Zap,
  AlertTriangle,
  CheckCircle,
  Clock,
  RefreshCw,
  Play,
  Database,
  Table2,
  Layers,
  Loader2,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { CommandBar, CommandBarTrigger, ContextSidebar } from '../components/core';
import { PageHeader, PageContent, ContextPanel, StatCard, SuggestionCard } from '../components/layout/PageLayout';
import { Tabs } from '@ghatana/design-system';
import { SpotlightRing } from '../components/brain/SpotlightRing';
import { AutonomyTimeline } from '../components/brain/AutonomyTimeline';
import { CapabilityTruthPanel } from '../components/capabilities/CapabilityTruthPanel';

// =============================================================================
// TYPES
// =============================================================================

type TabType = 'overview' | 'diagnostics' | 'analytics' | 'cost';

interface CollectionSummary {
  id: string;
  name?: string;
}

interface OverviewActivity {
  action: string;
  target: string;
  time: string;
  status: 'success' | 'warning' | 'error';
}

function toSpotlightItems(suggestions: AnalyticsAiSuggestion[]): AnalyticsAiSuggestion[] {
  return suggestions.slice(0, 3);
}

/** Type guard for analytics suggestion entry with typed sort comparators. */
interface SuggestionEntry {
  requestCount: number;
  fallbackRate: number;
}

function getDatasetBreakdown(costBreakdown?: Partial<CostBreakdown>): CostBreakdown['byDataset'] {
  return costBreakdown?.byDataset ?? [];
}

function toOverviewActivities(costBreakdown?: Partial<CostBreakdown>): OverviewActivity[] {
  const datasets = getDatasetBreakdown(costBreakdown);
  if (datasets.length === 0) {
    return [];
  }

  return [...datasets]
    .sort((left, right) => right.cost - left.cost)
    .slice(0, 3)
    .map((dataset) => ({
      action: dataset.percentage >= 50 ? 'Cost hotspot detected' : 'Cost profile refreshed',
      target: dataset.datasetName,
      time: `${Math.max(1, Math.round(dataset.percentage))}% of spend`,
      status: dataset.percentage >= 50 ? 'warning' : 'success',
    }));
}

function formatCurrencyValue(amount: number, currency: string): string {
  const roundedAmount = Math.round(amount * 100) / 100;
  return `${currency} ${roundedAmount.toLocaleString(undefined, {
    minimumFractionDigits: roundedAmount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })}`;
}

function describeSuggestionType(type: AnalyticsAiSuggestion['type']): 'optimization' | 'warning' | 'insight' {
  if (type === 'optimization') {
    return 'optimization';
  }
  if (type === 'warning' || type === 'anomaly') {
    return 'warning';
  }
  return 'insight';
}

function normalizeCollectionsResponse(
  response: { data: CollectionSummary[] } | { items: CollectionSummary[] } | CollectionSummary[] | undefined,
): CollectionSummary[] {
  if (!response) {
    return [];
  }
  if (Array.isArray(response)) {
    return response;
  }
  return 'items' in response ? response.items : response.data;
}

function formatInsightTimestamp(timestamp?: string): string | null {
  if (!timestamp) {
    return null;
  }

  const parsed = new Date(timestamp);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }

  return parsed.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

function OperatorDiagnosticsPanel({
  sessionSnapshot,
  capabilityRegistry,
}: {
  sessionSnapshot: SessionSnapshot;
  capabilityRegistry?: {
    requestId: string;
    generatedAt: string;
    capabilities: CapabilitySignal[];
  };
}) {
  const degradedCount = capabilityRegistry?.capabilities.filter((capability) => capability.status === 'degraded').length ?? 0;
  const unavailableCount = capabilityRegistry?.capabilities.filter((capability) => capability.status === 'unavailable').length ?? 0;
  const generatedAt = formatInsightTimestamp(capabilityRegistry?.generatedAt);

  return (
    <section className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
      <div className="flex items-start justify-between gap-4 mb-4">
        <div>
          <h2 className="text-sm font-medium text-gray-900 dark:text-white">Operator Diagnostics</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            One place to confirm tenant bootstrap, auth session state, and capability boundary truth before investigating deeper product behavior.
          </p>
        </div>
        {generatedAt && <span className="text-xs text-gray-400 whitespace-nowrap">Snapshot {generatedAt}</span>}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
        <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Tenant Context</div>
          <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">
            {sessionSnapshot.tenantId ?? 'Missing tenant context'}
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {sessionSnapshot.requiresTenantBootstrap ? 'Tenant selection required before runtime-backed flows.' : 'Tenant session is explicitly resolved.'}
          </p>
        </article>

        <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Auth Bootstrap</div>
          <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">
            {sessionSnapshot.isAuthenticated
              ? sessionSnapshot.authMode === 'cookie-session'
                ? 'Cookie-backed session present'
                : 'Header-token session present'
              : 'No authenticated session detected'}
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {sessionSnapshot.authMode === 'cookie-session'
              ? 'Browser-managed credentials are enabled; the UI does not inject bearer headers when the cookie-backed session is active.'
              : 'Bearer fallback remains centralized in one auth store instead of per-page localStorage reads.'}
          </p>
        </article>

        <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Capability Boundaries</div>
          <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">
            {unavailableCount} unavailable / {degradedCount} degraded
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {INSIGHTS_CAPABILITY_SNAPSHOT_NOTE}
          </p>
        </article>

        <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Registry Request</div>
          <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">
            {capabilityRegistry?.requestId ?? 'Unavailable'}
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {INSIGHTS_REGISTRY_REQUEST_NOTE}
          </p>
        </article>
      </div>
    </section>
  );
}

function ModelTelemetryPanel({
  qualitySummary,
}: {
  qualitySummary?: AiQualitySummaryResult;
}) {
  const topTypes = qualitySummary?.types
    .slice()
    .sort((left: SuggestionEntry, right: SuggestionEntry) => {
      if (right.requestCount !== left.requestCount) {
        return right.requestCount - left.requestCount;
      }
      return right.fallbackRate - left.fallbackRate;
    })
    .slice(0, 4) ?? [];

  return (
    <section className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4" data-testid="insights-model-telemetry-panel">
      <div className="flex items-start justify-between gap-4 mb-4">
        <div>
          <h2 className="text-sm font-medium text-gray-900 dark:text-white">Model Telemetry</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Launcher-process fallback and confidence telemetry for the AI routes behind query suggestions, workflow drafting, and explanation flows.
          </p>
        </div>
        {qualitySummary && (
          <span className="text-xs text-gray-400 whitespace-nowrap">
            {qualitySummary.summary.fallbackCount}/{qualitySummary.summary.requestCount} fallbacks
          </span>
        )}
      </div>

      {!qualitySummary ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">Assistance quality telemetry is not available yet for this launcher session.</p>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4">
            <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
              <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Assistance Requests</div>
              <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">{qualitySummary.summary.requestCount}</div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Observed by the current launcher process since startup.</p>
            </article>

            <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
              <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Fallback Rate</div>
              <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">{Math.round(qualitySummary.summary.fallbackRate * 100)}%</div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Heuristic responses versus live model-backed completions.</p>
            </article>

            <article className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-4 py-3">
              <div className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">LLM Wiring</div>
              <div className="mt-2 text-sm font-medium text-gray-900 dark:text-white">{qualitySummary.summary.llmConfigured ? 'Configured' : 'Heuristic only'}</div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Workflow drafts still expose provenance even when the handler falls back.</p>
            </article>
          </div>

          <div className="space-y-3">
            {topTypes.map((entry: SuggestionEntry & { type: string; label: string; route: string; provenanceMode: string; reviewGuidance: string; meanConfidence: number; fallbackCount: number }) => (
              <article key={entry.type} className="rounded-xl border border-gray-200 dark:border-gray-700 px-4 py-3" data-testid={`insights-ai-type-${entry.type}`}>
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="text-sm font-medium text-gray-900 dark:text-white">{entry.label}</div>
                    <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">{entry.route} • {entry.provenanceMode}</div>
                  </div>
                  <div className="text-right text-xs text-gray-500 dark:text-gray-400">
                    <div>{entry.requestCount} requests</div>
                    <div>{Math.round(entry.fallbackRate * 100)}% fallback</div>
                  </div>
                </div>

                <div className="mt-3 flex flex-wrap gap-2 text-xs">
                  <span className="rounded-full bg-blue-50 px-2.5 py-1 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200">
                    Mean confidence {Math.round(entry.meanConfidence * 100)}%
                  </span>
                  <span className="rounded-full bg-amber-50 px-2.5 py-1 text-amber-700 dark:bg-amber-900/30 dark:text-amber-200">
                    {entry.fallbackCount} fallback responses
                  </span>
                </div>

                <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">{entry.reviewGuidance}</p>
              </article>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

// =============================================================================
// OVERVIEW TAB
// =============================================================================

function OverviewTab({
  brainStats,
  activePipelines,
  monthlyCost,
  costBreakdown,
  aiSuggestions,
  capabilities,
  insightTimestamp,
  sessionSnapshot,
  capabilityRegistry,
  aiQualitySummary,
}: {
  brainStats?: BrainStats;
  activePipelines?: number;
  monthlyCost?: number;
  costBreakdown?: Partial<CostBreakdown>;
  aiSuggestions: AnalyticsAiSuggestion[];
  capabilities: CapabilitySignal[];
  insightTimestamp: string | null;
  sessionSnapshot: SessionSnapshot;
  capabilityRegistry?: {
    requestId: string;
    generatedAt: string;
    capabilities: CapabilitySignal[];
  };
  aiQualitySummary?: AiQualitySummaryResult;
}) {
  const spotlightItems = toSpotlightItems(aiSuggestions);
  const overviewActivities = toOverviewActivities(costBreakdown);
  const topDataset = getDatasetBreakdown(costBreakdown)[0];

  return (
    <div className="space-y-6">
      <OperatorDiagnosticsPanel
        sessionSnapshot={sessionSnapshot}
        capabilityRegistry={capabilityRegistry}
      />

      <ModelTelemetryPanel qualitySummary={aiQualitySummary} />

      <CapabilityTruthPanel
        title="Runtime Capability Truth"
        description="Live capability registration from the launcher. Operators can confirm which optional subsystems are active, degraded, or unavailable without inferring from UI behavior."
        capabilities={capabilities}
      />

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Records Processed"
          value={brainStats?.totalRecordsProcessed || 0}
          icon={<Sparkles className="h-5 w-5" />}
          color="purple"
        />
        <StatCard
          label="Active Patterns"
          value={brainStats?.activePatterns || 0}
          icon={<Brain className="h-5 w-5" />}
          color="blue"
        />
        <StatCard
          label="Active Pipelines"
          value={activePipelines ?? '–'}
          icon={<Activity className="h-5 w-5" />}
          color="green"
        />
        <StatCard
          label="Est. Monthly Cost"
          value={monthlyCost != null ? `$${monthlyCost.toLocaleString()}` : '–'}
          icon={<DollarSign className="h-5 w-5" />}
          color="yellow"
        />
      </div>

      {/* Two Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Assistance Spotlight */}
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-medium text-gray-900 dark:text-white flex items-center gap-2">
              <Zap className="h-4 w-4 text-yellow-500" />
              Assistance Spotlight
            </h2>
            <div className="text-right">
              <span className="block text-xs text-gray-500">
                {brainStats?.hotTierRecords || 0} hot-tier records
              </span>
              {insightTimestamp && (
                <span className="block text-[11px] text-gray-400">
                  Updated {insightTimestamp}
                </span>
              )}
            </div>
          </div>
          <div className="space-y-3">
            {spotlightItems.length > 0 ? (
              spotlightItems.map((suggestion) => (
                <SpotlightItem
                  key={suggestion.key}
                  title={suggestion.title}
                  description={suggestion.description}
                  type={describeSuggestionType(suggestion.type)}
                />
              ))
            ) : (
              <SpotlightItem
                title="No active insights"
                description="The analytics suggestion service has no current recommendations for this tenant."
                type="insight"
              />
            )}
          </div>
        </div>

        {/* Recent Activity */}
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-medium text-gray-900 dark:text-white flex items-center gap-2">
              <Activity className="h-4 w-4 text-blue-500" />
              Recent Actions
            </h2>
            <button className="text-xs text-primary-600 dark:text-primary-400 hover:underline">
              View all
            </button>
          </div>
          <div className="space-y-3">
            {overviewActivities.length > 0 ? (
              overviewActivities.map((activity) => (
                <ActivityItem
                  key={`${activity.action}-${activity.target}`}
                  action={activity.action}
                  target={activity.target}
                  time={activity.time}
                  status={activity.status}
                />
              ))
            ) : (
              <ActivityItem
                action="Awaiting telemetry"
                target="No cost activity available"
                time="pending"
                status="warning"
              />
            )}
          </div>
        </div>
      </div>

      {/* Cost Savings Banner */}
      <div className="bg-gradient-to-r from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 border border-green-200 dark:border-green-800 rounded-xl p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-green-100 dark:bg-green-900/50 rounded-lg">
              <TrendingDown className="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p className="font-medium text-green-900 dark:text-green-100">
                {topDataset
                  ? `Top cost driver: ${topDataset.datasetName}`
                  : 'Cost analysis is ready once collection reports are available'}
              </p>
              <p className="text-sm text-green-700 dark:text-green-300">
                {topDataset
                  ? `${Math.round(topDataset.percentage)}% of tenant spend is currently attributed to this dataset.`
                  : 'Create or hydrate collections so the insights surface can compute dataset-level spend.'}
              </p>
            </div>
          </div>
          {topDataset && (
            <button className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors text-sm">
              {formatCurrencyValue(topDataset.cost, costBreakdown?.currency ?? 'DCC')}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// DIAGNOSTICS TAB — Operator-facing system diagnostics
// =============================================================================

function DiagnosticsTab() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
      {/* Left Column: Spotlight Ring */}
      <div className="lg:col-span-1">
        <SpotlightRing maxItems={5} autoRefresh={true} />
      </div>

      {/* Right Column: Autonomy Timeline */}
      <div className="lg:col-span-2">
        <AutonomyTimeline maxItems={10} showFilters={true} />
      </div>
    </div>
  );
}

// =============================================================================
// ANALYTICS TAB
// =============================================================================

/**
 * Inline SQL console for quick interactive queries within the Insights page.
 * Calls POST /api/v1/analytics/query and renders the result table.
 */
function QuickQueryConsole() {
  const [sql, setSql] = useState('SELECT COUNT(*) as total FROM orders');
  const { mutate: runQuery, data: result, isPending, error, reset } = useAnalyticsQuery();

  const handleRun = useCallback(() => {
    if (!sql.trim()) return;
    runQuery({ sql: sql.trim() });
  }, [sql, runQuery]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') handleRun();
    },
    [handleRun]
  );

  const columnKeys =
    result && result.rows.length > 0 ? Object.keys(result.rows[0]) : [];

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
        <span className="text-sm font-medium text-gray-900 dark:text-white flex items-center gap-2">
          <Database className="h-4 w-4 text-blue-500" />
          Quick Analytics Query
        </span>
        <span className="text-xs text-gray-400">Ctrl+Enter to run</span>
      </div>

      <div className="p-4 space-y-3">
        <textarea
          value={sql}
          onChange={(e) => { setSql(e.target.value); reset(); }}
          onKeyDown={handleKeyDown}
          rows={4}
          className="w-full font-mono text-sm p-3 border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-white resize-y focus:outline-none focus:ring-2 focus:ring-primary-500"
          placeholder="SELECT COUNT(*) as total FROM my_collection"
        />

        <button
          onClick={handleRun}
          disabled={isPending || !sql.trim()}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm"
        >
          <Play className="h-3.5 w-3.5" />
          {isPending ? 'Running…' : 'Run Query'}
        </button>

        {error && (
          <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-300">
            {error instanceof Error ? error.message : 'Query failed'}
          </div>
        )}

        {result && columnKeys.length > 0 && (
          <div className="overflow-auto rounded-lg border border-gray-200 dark:border-gray-700 max-h-64">
            <div className="flex items-center justify-between px-3 py-2 bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
              <span className="text-xs text-gray-500">
                {result.rowCount} row{result.rowCount !== 1 ? 's' : ''} · {result.executionTimeMs}ms
                {result.optimized && ' · cached'}
              </span>
            </div>
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-gray-50 dark:bg-gray-900">
                  {columnKeys.map((col) => (
                    <th
                      key={col}
                      className="px-3 py-2 text-left font-medium text-gray-600 dark:text-gray-400 whitespace-nowrap"
                    >
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {result.rows.map((row, i) => (
                  <tr
                    key={i}
                    className={cn(
                      'border-t border-gray-100 dark:border-gray-700',
                      i % 2 === 0 ? '' : 'bg-gray-50/50 dark:bg-gray-900/30'
                    )}
                  >
                    {columnKeys.map((col) => (
                      <td
                        key={col}
                        className="px-3 py-2 text-gray-900 dark:text-white font-mono whitespace-nowrap"
                      >
                        {String(row[col] ?? '')}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {result && result.rows.length === 0 && (
          <p className="text-sm text-gray-400 text-center py-4">No rows returned</p>
        )}
      </div>
    </div>
  );
}

/**
 * AnalyticsTab — live data visualization connected to the analytics query engine.
 *
 * Fetches entity counts per collection via POST /api/v1/analytics/query and
 * renders a distribution chart alongside summary metrics and an interactive
 * SQL console. Replaces the previous static placeholder analytics cards.
 *
 * Also renders an AI anomaly hints panel (E3: Pervasive AI/ML) driven by the
 * same {@link useAnalyticsAiSuggestions} hook used by the sidebar — no extra
 * network round-trips, deduped by TanStack Query's cache key.
 */
function AnalyticsTab({ collections }: { collections: string[] }) {
  const { data: stats, isLoading: statsLoading } = useCollectionEntityCounts(collections);
  const { data: suggestions, isLoading: suggestionsLoading } = useAnalyticsAiSuggestions();

  const totalEntities = stats?.reduce((sum, s) => sum + s.count, 0) ?? 0;
  const maxCount = stats ? Math.max(...stats.map((s) => s.count), 1) : 1;

  // Surface anomaly and warning suggestions inline within the analytics tab
  const anomalySuggestions = (suggestions ?? []).filter(
    (s) => s.type === 'anomaly' || s.type === 'warning'
  );

  return (
    <div className="space-y-6">
      {/* Anomaly Hints Panel (E3 sprint-3 acceptance) */}
      {(suggestionsLoading || anomalySuggestions.length > 0) && (
        <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <AlertTriangle className="h-4 w-4 text-amber-600" />
            <h3 className="text-sm font-medium text-amber-900 dark:text-amber-100">
              Anomaly &amp; Warning Hints
            </h3>
            {!suggestionsLoading && anomalySuggestions.some((s) => s.fallback) && (
              <span className="text-xs text-amber-600 italic">(heuristic — assistance offline)</span>
            )}
          </div>
          {suggestionsLoading ? (
            <div className="flex items-center gap-2 text-sm text-amber-600">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Analyzing patterns…
            </div>
          ) : (
            <div className="space-y-2">
              {anomalySuggestions.map((s) => (
                <div key={s.key} className="flex items-start gap-3">
                  <SuggestionIcon type={s.type} />
                  <div className="flex-1">
                    <p className="text-sm font-medium text-amber-900 dark:text-amber-100">
                      {s.title}
                      {s.confidence > 0 && (
                        <span className="ml-2 text-xs text-amber-600">
                          {Math.round(s.confidence * 100)}% confidence
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-amber-700 dark:text-amber-300">{s.description}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
      {/* Live Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Total Collections"
          value={collections.length || '–'}
          icon={<Layers className="h-5 w-5" />}
          color="blue"
        />
        <StatCard
          label="Total Entities"
          value={statsLoading ? '…' : totalEntities.toLocaleString()}
          icon={<Table2 className="h-5 w-5" />}
          color="green"
        />
        <StatCard
          label="Avg per Collection"
          value={
            statsLoading || collections.length === 0
              ? '–'
              : Math.round(totalEntities / Math.max(collections.length, 1)).toLocaleString()
          }
          icon={<BarChart3 className="h-5 w-5" />}
          color="purple"
        />
      </div>

      {/* Entity Distribution */}
      {!statsLoading && stats && stats.length > 0 && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
          <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <BarChart3 className="h-4 w-4 text-blue-500" />
            Entity Distribution by Collection
          </h3>
          <div className="space-y-3">
            {stats.map(({ collection, count }) => (
              <div key={collection}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-gray-600 dark:text-gray-400 font-mono truncate max-w-[60%]">
                    {collection}
                  </span>
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    {count.toLocaleString()}
                  </span>
                </div>
                <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-blue-500 rounded-full transition-all duration-500"
                    style={{ width: `${Math.round((count / maxCount) * 100)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {statsLoading && collections.length > 0 && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-8 text-center">
          <div className="inline-flex items-center gap-2 text-sm text-gray-500">
            <RefreshCw className="h-4 w-4 animate-spin" />
            Loading entity counts…
          </div>
        </div>
      )}

      {!statsLoading && collections.length === 0 && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 border-dashed rounded-xl p-8 text-center">
          <Database className="h-8 w-8 text-gray-400 mx-auto mb-3" />
          <p className="text-sm text-gray-500 dark:text-gray-400">
            No collections found. Create a collection to see analytics.
          </p>
        </div>
      )}

      {/* Interactive SQL Console */}
      <QuickQueryConsole />
    </div>
  );
}

function CapabilityUnavailableState({
  title,
  message,
}: {
  title: string;
  message: string;
}) {
  return (
    <div className="bg-white dark:bg-gray-800 border border-dashed border-gray-300 dark:border-gray-700 rounded-xl p-8 text-center">
      <AlertTriangle className="h-8 w-8 text-amber-500 mx-auto mb-3" />
      <h3 className="text-sm font-medium text-gray-900 dark:text-white">{title}</h3>
      <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{message}</p>
    </div>
  );
}

function CapabilityLoadingState({
  title,
  message,
}: {
  title: string;
  message: string;
}) {
  return (
    <div className="bg-white dark:bg-gray-800 border border-dashed border-gray-300 dark:border-gray-700 rounded-xl p-8 text-center">
      <Loader2 className="h-8 w-8 text-gray-400 mx-auto mb-3 animate-spin" />
      <h3 className="text-sm font-medium text-gray-900 dark:text-white">{title}</h3>
      <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{message}</p>
    </div>
  );
}

// =============================================================================
// COST TAB
// =============================================================================

function CostTab({
  costBreakdown,
  aiSuggestions,
}: {
  costBreakdown?: Partial<CostBreakdown>;
  aiSuggestions: AnalyticsAiSuggestion[];
}) {
  const datasets = getDatasetBreakdown(costBreakdown);
  const totalCost = costBreakdown?.total ?? 0;
  const currency = costBreakdown?.currency ?? 'DCC';
  const topDataset = datasets[0];
  const optimizationSuggestions = aiSuggestions.filter((suggestion) => suggestion.type === 'optimization');

  return (
    <div className="space-y-6">
      {/* Cost Overview */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Current Month"
          value={formatCurrencyValue(totalCost, currency)}
          icon={<DollarSign className="h-5 w-5" />}
          color="default"
        />
        <StatCard
          label="Highest Dataset"
          value={topDataset ? topDataset.datasetName : '–'}
          icon={<TrendingUp className="h-5 w-5" />}
          color="yellow"
        />
        <StatCard
          label="Datasets Tracked"
          value={datasets.length}
          icon={<Sparkles className="h-5 w-5" />}
          color="green"
        />
      </div>

      {/* Optimization Opportunities */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-4 flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-purple-500" />
          Detected Optimization Opportunities
        </h3>
        <div className="space-y-3">
          {optimizationSuggestions.length > 0 ? (
            optimizationSuggestions.map((suggestion) => (
              <OptimizationItem
                key={suggestion.key}
                title={suggestion.title}
                description={suggestion.description}
                supportingText={`${Math.round(suggestion.confidence * 100)}% confidence`}
              />
            ))
          ) : (
            <OptimizationItem
              title="No optimization hints available"
              description="The assistance service has no current cost-saving recommendations for this tenant."
              supportingText="waiting for signals"
            />
          )}
        </div>
      </div>

      {/* Cost by Resource */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-4">
          Cost by Dataset
        </h3>
        <div className="space-y-3">
          {datasets.length > 0 ? (
            datasets.slice(0, 4).map((dataset, index) => (
              <CostBar
                key={dataset.datasetId}
                label={dataset.datasetName}
                value={dataset.cost}
                total={Math.max(totalCost, dataset.cost)}
                color={index === 0 ? 'blue' : index === 1 ? 'green' : index === 2 ? 'purple' : 'orange'}
                currency={currency}
              />
            ))
          ) : (
            <p className="text-sm text-gray-500 dark:text-gray-400">
              No collection cost reports are available yet.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// HELPER COMPONENTS
// =============================================================================

function SpotlightItem({
  title,
  description,
  type,
}: {
  title: string;
  description: string;
  type: 'optimization' | 'warning' | 'insight';
}) {
  const icons = {
    optimization: <TrendingUp className="h-4 w-4 text-green-500" />,
    warning: <AlertTriangle className="h-4 w-4 text-amber-500" />,
    insight: <Sparkles className="h-4 w-4 text-purple-500" />,
  };
  const colors = {
    optimization: 'bg-green-50 dark:bg-green-900/20',
    warning: 'bg-amber-50 dark:bg-amber-900/20',
    insight: 'bg-purple-50 dark:bg-purple-900/20',
  };

  return (
    <div className={cn('p-3 rounded-lg', colors[type])}>
      <div className="flex items-start gap-3">
        <div className="mt-0.5">{icons[type]}</div>
        <div>
          <p className="text-sm font-medium text-gray-900 dark:text-white">
            {title}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {description}
          </p>
        </div>
      </div>
    </div>
  );
}

function ActivityItem({
  action,
  target,
  time,
  status,
}: {
  action: string;
  target: string;
  time: string;
  status: 'success' | 'warning' | 'error';
}) {
  const icons = {
    success: <CheckCircle className="h-4 w-4 text-green-500" />,
    warning: <AlertTriangle className="h-4 w-4 text-amber-500" />,
    error: <AlertTriangle className="h-4 w-4 text-red-500" />,
  };

  return (
    <div className="flex items-center gap-3 py-2">
      {icons[status]}
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-900 dark:text-white truncate">
          {action} <span className="font-medium">{target}</span>
        </p>
      </div>
      <span className="text-xs text-gray-400">{time}</span>
    </div>
  );
}

function InsightSummaryCard({
  title,
  description,
  status,
}: {
  title: string;
  description: string;
  status: 'healthy' | 'warning' | 'error';
}) {
  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:shadow-md transition-shadow cursor-pointer">
      <div className="flex items-start gap-4">
        <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
          <BarChart3 className="h-6 w-6 text-blue-600 dark:text-blue-400" />
        </div>
        <div className="flex-1">
          <h3 className="font-medium text-gray-900 dark:text-white">{title}</h3>
          <p className="text-sm text-gray-500 mt-1">{description}</p>
          <div className="flex items-center gap-2 mt-3">
            <span
              className={cn(
                'inline-flex items-center gap-1 text-xs font-medium',
                status === 'healthy' && 'text-green-600',
                status === 'warning' && 'text-amber-600',
                status === 'error' && 'text-red-600'
              )}
            >
              {status === 'healthy' && <CheckCircle className="h-3 w-3" />}
              {status === 'warning' && <AlertTriangle className="h-3 w-3" />}
              {status === 'healthy' ? 'All systems normal' : 'Needs attention'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

function OptimizationItem({
  title,
  description,
  supportingText,
}: {
  title: string;
  description: string;
  supportingText: string;
}) {
  return (
    <div className="flex items-center gap-4 p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
      <div className="flex-1">
        <p className="text-sm font-medium text-gray-900 dark:text-white">
          {title}
        </p>
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{description}</p>
      </div>
      <div className="text-right">
        <p className="text-xs font-semibold text-green-600 dark:text-green-400">
          {supportingText}
        </p>
      </div>
    </div>
  );
}

function CostBar({
  label,
  value,
  total,
  color,
  currency,
}: {
  label: string;
  value: number;
  total: number;
  color: 'blue' | 'green' | 'purple' | 'orange';
  currency: string;
}) {
  const percentage = (value / total) * 100;
  const colors = {
    blue: 'bg-blue-500',
    green: 'bg-green-500',
    purple: 'bg-purple-500',
    orange: 'bg-orange-500',
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-sm text-gray-600 dark:text-gray-400">{label}</span>
        <span className="text-sm font-medium text-gray-900 dark:text-white">
          {formatCurrencyValue(value, currency)}
        </span>
      </div>
      <div className="quality-bar">
        <div
          className={cn('quality-bar-fill', colors[color])}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

/** Maps analytics suggestion type to icon + colour. */
function SuggestionIcon({ type }: { type: AnalyticsAiSuggestion['type'] }) {
  switch (type) {
    case 'optimization': return <TrendingUp className="h-4 w-4 text-green-600" />;
    case 'anomaly': return <AlertTriangle className="h-4 w-4 text-amber-600" />;
    case 'warning': return <AlertTriangle className="h-4 w-4 text-red-500" />;
    default: return <Sparkles className="h-4 w-4 text-purple-600" />;
  }
}

export function InsightsPage() {
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const navigate = useNavigate();
  const sessionSnapshot = SessionBootstrap.bootstrap();

  /**
   * Handle analytics suggestion action with deep-link routing
   */
  const handleSuggestionAction = useCallback((suggestion: AnalyticsAiSuggestion) => {
    if (suggestion.type === 'optimization' && suggestion.reasons?.includes('query')) {
      navigate('/query', { state: { query: suggestion.description } });
    } else if (suggestion.type === 'anomaly') {
      navigate('/data', { state: { view: 'quality', filter: suggestion.reasons?.[0] } });
    } else if (suggestion.type === 'warning') {
      navigate('/data', { state: { view: 'quality' } });
    } else {
      // Default: navigate to data page with search
      navigate('/data', { state: { search: suggestion.description } });
    }
  }, [navigate]);

  // Fetch brain stats
  const { data: brainStats } = useQuery({
    queryKey: ['brain-stats'],
    queryFn: () => brainService.getBrainStats(),
    staleTime: 60_000,
  });

  // Fetch active pipeline count
  const { data: workflowsPage } = useQuery({
    queryKey: ['active-workflows-count'],
    queryFn: () => workflowsApi.list({ status: 'active', pageSize: 1 }),
    staleTime: 120_000,
  });

  // Fetch cost analysis
  const { data: costData } = useQuery({
    queryKey: ['cost-analysis'],
    queryFn: () => costService.getCostAnalysis('30d'),
    staleTime: 300_000,
  });

  // Fetch collections for analytics tab
  const { data: collectionsData } = useQuery({
    queryKey: ['collections-for-analytics'],
    queryFn: () => collectionsApi.list(),
    staleTime: 300_000,
  });
  const collectionNames = normalizeCollectionsResponse(collectionsData)
    .map((collection) => collection.name ?? collection.id)
    .filter((name) => name.length > 0);
  const { data: capabilityRegistry, isLoading: capabilitiesLoading } = useCapabilityRegistry();
  const { data: aiQualitySummary } = useAiQualitySummary();
  const analyticsCapability = getCapabilitySignal(
    capabilityRegistry?.capabilities,
    ['analytics', 'trino', 'federated_query', 'federatedQuery'],
  );
  const aiAssistCapability = getCapabilitySignal(
    capabilityRegistry?.capabilities,
    ['ai_assist', 'aiAssist', 'assist', 'brain'],
  );
  const analyticsUnavailable = analyticsCapability?.status === 'unavailable';
  const aiUnavailable = aiAssistCapability?.status === 'unavailable';
  const insightTimestamp = formatInsightTimestamp(brainStats?.timestamp ?? capabilityRegistry?.generatedAt);

  // AI sidebar: fetch real suggestions from POST /api/v1/analytics/suggest
  const { data: aiSuggestions, isLoading: aiLoading } = useAnalyticsAiSuggestions();

  const tabs: { id: TabType; label: string; icon: React.ReactNode }[] = [
    { id: 'overview', label: 'Overview', icon: <Activity className="h-4 w-4" /> },
    { id: 'diagnostics', label: 'Diagnostics', icon: <Brain className="h-4 w-4" /> },
    { id: 'analytics', label: 'Analytics', icon: <BarChart3 className="h-4 w-4" /> },
    { id: 'cost', label: 'Cost', icon: <DollarSign className="h-4 w-4" /> },
  ];

  // Sidebar content — wired to real POST /api/v1/analytics/suggest
  const sidebarContent = (
    <ContextPanel title="Insights">
      {aiUnavailable ? (
        <CapabilityUnavailableState
          title="Insights unavailable"
          message={aiAssistCapability?.detail ?? 'This deployment does not have the insights capability enabled.'}
        />
      ) : capabilitiesLoading && !capabilityRegistry ? (
        <CapabilityLoadingState
          title="Loading runtime capabilities"
          message="Checking which optional insights dependencies are active before rendering insights."
        />
      ) : aiLoading ? (
        <div className="flex items-center gap-2 text-sm text-gray-400 py-4 justify-center">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading suggestions…
        </div>
      ) : (
        <div className="space-y-3">
          {insightTimestamp && (
            <p className="text-[11px] text-gray-400 text-center">
              Generated {insightTimestamp}
            </p>
          )}
          {(aiSuggestions ?? []).map((s) => (
            <SuggestionCard
              key={s.key}
              icon={<SuggestionIcon type={s.type} />}
              title={s.title}
              description={s.description}
              confidence={s.confidence > 0 ? s.confidence : undefined}
              actionLabel={s.fallback ? undefined : 'View'}
              onAction={s.fallback ? undefined : () => handleSuggestionAction(s)}
            />
          ))}
          {(aiSuggestions ?? []).length === 0 && (
            <p className="text-xs text-gray-400 text-center py-4">No suggestions right now</p>
          )}
          {aiSuggestions?.some((s) => s.fallback) && (
            <p className="text-xs text-gray-400 mt-2 italic">
              Insights service offline — showing heuristic suggestions.
            </p>
          )}
        </div>
      )}
    </ContextPanel>
  );

  return (
    <section className="flex flex-col h-full" aria-label="Insights">
      <PageHeader
        title="Insights"
        subtitle="Operational analytics, system intelligence, and cost optimization"
        icon={<BarChart3 className="h-6 w-6 text-primary-600" />}
        actions={
          <button className="flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors">
            <RefreshCw className="h-4 w-4" />
            Refresh
          </button>
        }
      />

      {/* Tabs — migrated to @ghatana/design-system Tabs (DS-009) */}
      <div className="px-6 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <Tabs
          tabs={tabs.map((tab) => ({
            key: tab.id,
            label: tab.label,
            icon: tab.icon,
          }))}
          activeTab={activeTab}
          onChange={(key: string) => setActiveTab(key as TabType)}
          variant="pills"
          size="sm"
        />
      </div>

      <PageContent contextSidebar={sidebarContent}>
        <div role="tabpanel" id={`insights-panel-${activeTab}`} aria-label={tabs.find((t) => t.id === activeTab)?.label ?? activeTab}>
          {activeTab === 'overview' && (
            <OverviewTab
              brainStats={brainStats}
              activePipelines={workflowsPage?.total}
              monthlyCost={costData?.total}
              costBreakdown={costData}
              aiSuggestions={aiSuggestions ?? []}
              capabilities={capabilityRegistry?.capabilities ?? []}
              insightTimestamp={insightTimestamp}
              sessionSnapshot={sessionSnapshot}
              aiQualitySummary={aiQualitySummary}
              capabilityRegistry={capabilityRegistry ? {
                requestId: capabilityRegistry.requestId,
                generatedAt: capabilityRegistry.generatedAt,
                capabilities: capabilityRegistry.capabilities,
              } : undefined}
            />
          )}
          {activeTab === 'diagnostics' && <DiagnosticsTab />}
          {activeTab === 'analytics' && (
            capabilitiesLoading && !capabilityRegistry ? (
              <CapabilityLoadingState
                title="Loading runtime capabilities"
                message="Confirming analytics and federated query dependencies before enabling live analytics views."
              />
            ) : analyticsUnavailable ? (
              <CapabilityUnavailableState
                title="Analytics unavailable"
                message={analyticsCapability?.detail ?? 'Configure analytics connectors such as Trino or ClickHouse to enable live analytics queries.'}
              />
            ) : (
              <AnalyticsTab collections={collectionNames} />
            )
          )}
          {activeTab === 'cost' && <CostTab costBreakdown={costData} aiSuggestions={aiSuggestions ?? []} />}
        </div>
      </PageContent>
    </section>
  );
}

export default InsightsPage;
