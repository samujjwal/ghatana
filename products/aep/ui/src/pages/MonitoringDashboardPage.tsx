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
import { useQuery } from '@tanstack/react-query';
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
  type PipelineMetrics,
} from '@/api/aep.api';
import { tenantIdAtom } from '@/stores/tenant.store';
import { useLivePipelineRuns, useCancelRun } from '@/hooks/usePipelineRuns';
import { StatCard } from '@/components/monitoring/StatCard';
import { RunTable } from '@/components/monitoring/RunTable';

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

// ─── Page ────────────────────────────────────────────────────────────

export function MonitoringDashboardPage() {
  const tenantId = useAtomValue(tenantIdAtom);

  const { data: runs = [], isLoading: runsLoading } = useLivePipelineRuns(30);
  const cancelRun = useCancelRun();

  const { data: metrics = [], isLoading: metricsLoading } = useQuery({
    queryKey: ['aep', 'metrics', tenantId],
    queryFn: () => getPipelineMetrics(tenantId),
    refetchInterval: 15_000,
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
        {/* KPI row */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <StatCard label="Total runs" value={runs.length} />
          <StatCard
            label="Active"
            value={activeRuns}
            trend={activeRuns > 0 ? 'up' : 'neutral'}
          />
          <StatCard
            label="Success rate"
            value={`${successRate}%`}
            sub={`${succeededRuns} succeeded`}
            trend={successRate >= 90 ? 'up' : successRate >= 70 ? 'neutral' : 'down'}
          />
          <StatCard
            label="Failed"
            value={failedRuns}
            trend={failedRuns > 0 ? 'down' : 'neutral'}
          />
        </div>

        {/* Success rate chart */}
        {!metricsLoading && metrics.length > 0 && (
          <SuccessRateChart metrics={metrics} />
        )}

        {/* Tab selector */}
        <div className="flex gap-2 border-b border-gray-200 dark:border-gray-800">
          {(['runs', 'metrics'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={[
                'px-4 py-2 text-sm font-medium -mb-px border-b-2 transition-colors capitalize',
                tab === t
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700',
              ].join(' ')}
            >
              {t === 'runs' ? 'Recent Runs' : 'Pipeline Metrics'}
            </button>
          ))}
        </div>

        {/* Runs tab */}
        {tab === 'runs' && (
          runsLoading
            ? <p className="text-gray-400 text-center py-8">Loading runs…</p>
            : <RunTable runs={runs} onCancel={(id) => cancelRun.mutate(id)} />
        )}

        {/* Metrics tab */}
        {tab === 'metrics' && (
          metricsLoading
            ? <p className="text-gray-400 text-center py-8">Loading metrics…</p>
            : (
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
                  {metrics.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-8 text-center text-gray-400 italic">
                        No metrics available
                      </td>
                    </tr>
                  )}
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
