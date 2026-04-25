/**
 * MonitoringDashboardPage — real-time pipeline execution metrics.
 *
 * Displays:
 *   - Summary KPI cards (total runs, active, success rate, avg latency)
 *   - Per-pipeline metrics table
 *   - Recent run log with status, duration, error count
 *   - Live run updates via SSE through useLivePipelineRuns
 *
 * @doc.type page
 * @doc.purpose AEP real-time monitoring dashboard
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import {
  getPipelineMetrics,
  getRuntimeDurabilityStatus,
  type PipelineMetrics,
  type RuntimeDurabilityStatus,
} from '@/api/aep.api';
import { tenantIdAtom } from '@/stores/tenant.store';
import { useLivePipelineRuns, useCancelRun } from '@/hooks/usePipelineRuns';
import { StatCard } from '@/components/monitoring/StatCard';
import { RunTable } from '@/components/monitoring/RunTable';
import { useAiSuggestions } from '@/components/monitoring/AiSuggestionsPanel';
import { useSelection } from '@/hooks/useSelection';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { useConsent } from '@/components/privacy/ConsentManager';
import { useSpeechSynthesis } from '@audio-video/ui';
import { Button } from '@ghatana/design-system';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';

// ─── Chart ───────────────────────────────────────────────────────────

function SuccessRateChart({ metrics }: { metrics: PipelineMetrics[] }) {
  if (metrics.length === 0) return null;
  const data = metrics.map((m) => ({
    name: m.pipelineName.length > 16 ? m.pipelineName.slice(0, 14) + '…' : m.pipelineName,
    Succeeded: Math.round(m.totalRuns * (1 - m.errorRate)),
    Failed: Math.round(m.totalRuns * m.errorRate),
  }));
  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-4 py-4">
      <p className="text-xs font-medium uppercase tracking-wider text-gray-500 mb-3">Pipeline success vs failure</p>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={data} margin={{ top: 0, right: 8, left: -16, bottom: 0 }}>
          <XAxis dataKey="name" tick={{ fontSize: 11 }} />
          <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
          <Tooltip />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <Bar dataKey="Succeeded" stackId="a" fill="#22c55e" radius={[0, 0, 0, 0]} />
          <Bar dataKey="Failed" stackId="a" fill="#ef4444" radius={[3, 3, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function durabilityTone(mode: RuntimeDurabilityStatus['mode']): string {
  switch (mode) {
    case 'durable':
      return 'border-emerald-200 bg-emerald-50 text-emerald-950 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-100';
    case 'degraded':
      return 'border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-100';
    case 'ephemeral':
      return 'border-red-200 bg-red-50 text-red-950 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-100';
  }
}

function formatComponentName(name: string): string {
  return name.replaceAll('.', ' ');
}

function formatRuntimeContext(durability: RuntimeDurabilityStatus): string[] {
  const context: string[] = [];
  if (durability.profile) {
    context.push(`profile: ${durability.profile}`);
  }
  if (durability.dataCloudStorage) {
    context.push(`storage: ${durability.dataCloudStorage}`);
  }
  return context;
}

// ─── Page ────────────────────────────────────────────────────────────

export function MonitoringDashboardPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const qc = useQueryClient();

  const { data: runs = [], isLoading: runsLoading } = useLivePipelineRuns(30);
  const cancelRun = useCancelRun();
  const { speak } = useSpeechSynthesis();
  const { consentGranted: voiceConsent } = useConsent('voice_processing');

  // AI suggestions surfaced per run row instead of a separate panel (TASK-M6)
  const { suggestions: aiSuggestions = [] } = useAiSuggestions(tenantId);

  const { data: metrics = [], isLoading: metricsLoading } = useQuery({
    queryKey: ['aep', 'metrics', tenantId],
    queryFn: () => getPipelineMetrics(tenantId),
    refetchInterval: 15_000,
  });

  const { data: durability } = useQuery({
    queryKey: ['aep', 'runtime-durability'],
    queryFn: () => getRuntimeDurabilityStatus(),
    staleTime: 15_000,
    refetchInterval: 15_000,
  });

  // Bulk selection
  const {
    selectedIds,
    selectedItems,
    toggle,
    toggleAll,
    isAllSelected,
    isIndeterminate,
    deselectAll,
  } = useSelection({
    items: runs,
    keyFn: (run) => run.id,
  });

  // Bulk cancel mutation
  const bulkCancelMut = useMutation({
    mutationFn: async (ids: string[]) => {
      return Promise.all(ids.map((id) => cancelRun.mutateAsync(id)));
    },
    onSuccess: () => {
      deselectAll();
      qc.invalidateQueries({ queryKey: ['aep', 'runs', tenantId] });
      if (voiceConsent) {
        speak(`Cancelled ${selectedIds.size} pipeline runs`);
      }
    },
  });

  // KPIs derived from runs
  const activeRuns = runs.filter((r) => r.status === 'RUNNING').length;
  const succeededRuns = runs.filter((r) => r.status === 'SUCCEEDED').length;
  const failedRuns = runs.filter((r) => r.status === 'FAILED').length;
  const successRate = runs.length > 0 ? Math.round((succeededRuns / runs.length) * 100) : 0;

  const [tab, setTab] = useState<'runs' | 'metrics'>('runs');

  return (
    <div className="flex flex-col h-full overflow-hidden bg-gray-50 dark:bg-gray-950">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex items-center gap-3">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Monitoring</h1>
        <span className="text-xs text-gray-400">Live via SSE · 15 s fallback</span>
      </div>

      <div className="flex-1 overflow-auto px-6 py-4 space-y-6">
        {durability ? (
          <div className={['rounded-xl border px-4 py-3', durabilityTone(durability.mode)].join(' ')}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold">{durability.title}</p>
                <p className="mt-1 text-xs opacity-90">{durability.description}</p>
              </div>
              <span className="text-[11px] uppercase tracking-wide opacity-75">
                Checked{' '}
                {durability.checkedAt && !Number.isNaN(new Date(durability.checkedAt).getTime())
                  ? new Date(durability.checkedAt).toLocaleTimeString()
                  : 'not available'}
              </span>
            </div>
            {formatRuntimeContext(durability).length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2 text-[11px] uppercase tracking-wide opacity-80">
                {formatRuntimeContext(durability).map((entry) => (
                  <span key={entry} className="rounded-full border border-current/15 px-2 py-1 font-mono">
                    {entry}
                  </span>
                ))}
              </div>
            ) : null}
            {durability.reasons && durability.reasons.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2 text-[11px] opacity-85">
                {durability.reasons.map((reason) => (
                  <span key={reason} className="rounded-full border border-current/15 px-2 py-1">
                    {reason}
                  </span>
                ))}
              </div>
            ) : null}
            <div className="mt-3 flex flex-wrap gap-2 text-[11px]">
              {Object.entries(durability.components).map(([name, value]) => (
                <span
                  key={name}
                  className="rounded-full border border-current/15 px-2 py-1 font-mono"
                >
                  {formatComponentName(name)}: {value}
                </span>
              ))}
            </div>
          </div>
        ) : null}

        {/* KPI row */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <StatCard label="Total runs" value={runs.length} loading={runsLoading} />
          <StatCard
            label="Active"
            value={activeRuns}
            trend={activeRuns > 0 ? 'up' : 'neutral'}
            loading={runsLoading}
          />
          <StatCard
            label="Success rate"
            value={`${successRate}%`}
            sub={`${succeededRuns} succeeded`}
            trend={successRate >= 90 ? 'up' : successRate >= 70 ? 'neutral' : 'down'}
            loading={runsLoading}
          />
          <StatCard
            label="Failed"
            value={failedRuns}
            trend={failedRuns > 0 ? 'down' : 'neutral'}
            loading={runsLoading}
          />
        </div>

        {/* Success rate chart */}
        {!metricsLoading && metrics.length > 0 && (
          <SuccessRateChart metrics={metrics} />
        )}

        {/* Tab selector */}
        <div className="flex gap-2 border-b border-gray-200 dark:border-gray-800">
          {(['runs', 'metrics'] as const).map((t) => (
            <Button
              key={t}
              onClick={() => setTab(t)}
              variant="text"
              className={[
                'px-4 py-2 text-sm font-medium -mb-px border-b-2 transition-colors capitalize',
                tab === t
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700',
              ].join(' ')}
            >
              {t === 'runs' ? 'Recent Runs' : 'Pipeline Metrics'}
            </Button>
          ))}
        </div>

        {/* Runs tab */}
        {tab === 'runs' && (
          <>
            {/* Bulk action toolbar */}
            {selectedIds.size > 0 && isFeatureEnabled('BULK_OPERATIONS') && (
              <div className="flex items-center justify-between bg-indigo-50 dark:bg-indigo-950 border border-indigo-200 dark:border-indigo-800 rounded-lg px-4 py-2 mb-4">
                <span className="text-sm text-indigo-900 dark:text-indigo-100">
                  {selectedIds.size} run{selectedIds.size !== 1 ? 's' : ''} selected
                </span>
                <div className="flex gap-2">
                  <Button
                    onClick={() => {
                      const runningIds = selectedItems.filter(r => r.status === 'RUNNING').map(r => r.id);
                      if (runningIds.length > 0) {
                        bulkCancelMut.mutate(runningIds);
                      }
                    }}
                    disabled={bulkCancelMut.isPending}
                    variant="primary"
                    className="px-3 py-1.5 text-xs font-medium"
                    style={{ backgroundColor: '#dc2626' }}
                  >
                    Cancel Selected
                  </Button>
                  <Button
                    onClick={deselectAll}
                    variant="secondary"
                    className="px-3 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-800"
                  >
                    Clear Selection
                  </Button>
                </div>
              </div>
            )}

            {runsLoading ? (
              <EmptyState title="Loading runs…" description="Fetching recent pipeline execution data." />
            ) : runs.length === 0 ? (
              <EmptyState
                title="No pipeline runs yet"
                description="Runs will appear here once pipelines are executed."
              />
            ) : (
              <RunTable
                runs={runs}
                onCancel={(id) => cancelRun.mutate(id)}
                selectedIds={selectedIds}
                onSelectToggle={toggle}
                onSelectAll={toggleAll}
                isAllSelected={isAllSelected}
                isIndeterminate={isIndeterminate}
                aiSuggestions={aiSuggestions}
              />
            )}
          </>
        )}

        {/* Metrics tab */}
        {tab === 'metrics' && (
          metricsLoading ? (
            <EmptyState title="Loading metrics…" description="Fetching pipeline performance metrics." />
          ) : metrics.length === 0 ? (
            <EmptyState
              title="No metrics available"
              description="Metrics will populate once pipelines have run."
            />
          ) : (
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="text-left text-xs font-medium text-gray-500 uppercase tracking-wider border-b border-gray-200 dark:border-gray-800">
                  <th className="pb-2 pr-4">Pipeline</th>
                  <th className="pb-2 pr-4">Throughput/s</th>
                  <th className="pb-2 pr-4">Error rate</th>
                  <th className="pb-2 pr-4">Avg latency</th>
                  <th className="pb-2 pr-4">Active</th>
                  <th className="pb-2">Total runs</th>
                </tr>
              </thead>
              <tbody>
                {metrics.map((m: PipelineMetrics) => (
                  <tr
                    key={m.pipelineId}
                    className="border-b border-gray-100 dark:border-gray-900 hover:bg-white dark:hover:bg-gray-900"
                  >
                    <td className="py-2 pr-4 font-medium text-gray-900 dark:text-white">{m.pipelineName}</td>
                    <td className="py-2 pr-4 font-mono text-xs">{m.throughputPerSec.toFixed(1)}</td>
                    <td className={['py-2 pr-4 font-mono text-xs', m.errorRate > 0.05 ? 'text-red-600' : 'text-gray-500'].join(' ')}>
                      {(m.errorRate * 100).toFixed(2)}%
                    </td>
                    <td className="py-2 pr-4 font-mono text-xs">{m.avgLatencyMs.toFixed(0)}ms</td>
                    <td className="py-2 pr-4 text-gray-500">{m.activeRuns}</td>
                    <td className="py-2 text-gray-500">{m.totalRuns}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        )}
      </div>
    </div>
  );
}
