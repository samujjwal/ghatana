/**
 * MonitoringDashboardPage — real-time pipeline execution metrics.
 *
 * Displays:
 *   - Summary KPI cards (total runs, active, success rate, avg latency)
 *   - Per-pipeline metrics table
 *   - Recent run log with status, duration, error count
 *   - Auto-refresh every 15 s  (manually cancelable)
 *
 * @doc.type page
 * @doc.purpose AEP real-time monitoring dashboard
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listPipelineRuns,
  getPipelineMetrics,
  cancelRun,
  type PipelineRun,
  type PipelineMetrics,
} from '@/api/aep.api';

// ─── Helpers ─────────────────────────────────────────────────────────

function formatDuration(start: string, end?: string): string {
  const ms = (end ? new Date(end) : new Date()).getTime() - new Date(start).getTime();
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60_000)}m ${Math.floor((ms % 60_000) / 1000)}s`;
}

const RUN_STATUS_COLORS: Record<PipelineRun['status'], string> = {
  RUNNING: 'text-blue-600 dark:text-blue-400',
  SUCCEEDED: 'text-green-600 dark:text-green-400',
  FAILED: 'text-red-600 dark:text-red-400',
  CANCELLED: 'text-gray-500',
};

// ─── KPI Card ────────────────────────────────────────────────────────

function KpiCard({
  label,
  value,
  sub,
  accent,
}: {
  label: string;
  value: string | number;
  sub?: string;
  accent?: string;
}) {
  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-5 py-4">
      <p className="text-xs font-medium uppercase tracking-wider text-gray-500">{label}</p>
      <p className={['text-3xl font-bold mt-1', accent ?? 'text-gray-900 dark:text-white'].join(' ')}>
        {value}
      </p>
      {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
    </div>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

export function MonitoringDashboardPage() {
  const tenantId = 'default';
  const queryClient = useQueryClient();

  const { data: runs = [], isLoading: runsLoading } = useQuery({
    queryKey: ['aep', 'runs', tenantId],
    queryFn: () => listPipelineRuns(tenantId, 30),
    refetchInterval: 15_000,
  });

  const { data: metrics = [], isLoading: metricsLoading } = useQuery({
    queryKey: ['aep', 'metrics', tenantId],
    queryFn: () => getPipelineMetrics(tenantId),
    refetchInterval: 15_000,
  });

  const cancelMut = useMutation({
    mutationFn: (runId: string) => cancelRun(runId, tenantId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'runs'] });
    },
  });

  // KPIs derived from runs
  const activeRuns = runs.filter((r) => r.status === 'RUNNING').length;
  const succeededRuns = runs.filter((r) => r.status === 'SUCCEEDED').length;
  const failedRuns = runs.filter((r) => r.status === 'FAILED').length;
  const successRate =
    runs.length > 0 ? Math.round((succeededRuns / runs.length) * 100) : 0;

  const [tab, setTab] = useState<'runs' | 'metrics'>('runs');

  return (
    <div className="flex flex-col h-full overflow-hidden bg-gray-50 dark:bg-gray-950">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex items-center gap-3">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Monitoring</h1>
        <span className="text-xs text-gray-400">Auto-refreshes every 15 s</span>
      </div>

      <div className="flex-1 overflow-auto px-6 py-4 space-y-6">
        {/* KPI row */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <KpiCard label="Total runs" value={runs.length} />
          <KpiCard
            label="Active"
            value={activeRuns}
            accent={activeRuns > 0 ? 'text-blue-600 dark:text-blue-400' : undefined}
          />
          <KpiCard
            label="Success rate"
            value={`${successRate}%`}
            sub={`${succeededRuns} succeeded`}
            accent={successRate >= 90 ? 'text-green-600' : successRate >= 70 ? 'text-yellow-600' : 'text-red-600'}
          />
          <KpiCard
            label="Failed"
            value={failedRuns}
            accent={failedRuns > 0 ? 'text-red-600 dark:text-red-400' : undefined}
          />
        </div>

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

        {/* Runs table */}
        {tab === 'runs' && (
          <div>
            {runsLoading ? (
              <p className="text-gray-400 text-center py-8">Loading runs…</p>
            ) : (
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="text-left text-xs font-medium text-gray-500 uppercase tracking-wider border-b border-gray-200 dark:border-gray-800">
                    <th className="pb-2 pr-4">Pipeline</th>
                    <th className="pb-2 pr-4">Status</th>
                    <th className="pb-2 pr-4">Events</th>
                    <th className="pb-2 pr-4">Errors</th>
                    <th className="pb-2 pr-4">Duration</th>
                    <th className="pb-2">Started</th>
                    <th className="pb-2" />
                  </tr>
                </thead>
                <tbody>
                  {runs.length === 0 && (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-gray-400 italic">
                        No runs yet
                      </td>
                    </tr>
                  )}
                  {runs.map((run) => (
                    <tr
                      key={run.id}
                      className="border-b border-gray-100 dark:border-gray-900 hover:bg-white dark:hover:bg-gray-900"
                    >
                      <td className="py-2 pr-4 font-medium text-gray-900 dark:text-white">
                        {run.pipelineName}
                      </td>
                      <td className={['py-2 pr-4 font-medium', RUN_STATUS_COLORS[run.status]].join(' ')}>
                        {run.status}
                      </td>
                      <td className="py-2 pr-4 text-gray-600 dark:text-gray-400">
                        {run.eventsProcessed.toLocaleString()}
                      </td>
                      <td className={['py-2 pr-4', run.errorsCount > 0 ? 'text-red-600' : 'text-gray-400'].join(' ')}>
                        {run.errorsCount}
                      </td>
                      <td className="py-2 pr-4 text-gray-500 font-mono text-xs">
                        {formatDuration(run.startedAt, run.finishedAt)}
                      </td>
                      <td className="py-2 text-gray-400 text-xs">
                        {new Date(run.startedAt).toLocaleTimeString()}
                      </td>
                      <td className="py-2 text-right">
                        {run.status === 'RUNNING' && (
                          <button
                            onClick={() => cancelMut.mutate(run.id)}
                            className="text-xs text-red-600 hover:underline"
                          >
                            Cancel
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {/* Metrics table */}
        {tab === 'metrics' && (
          <div>
            {metricsLoading ? (
              <p className="text-gray-400 text-center py-8">Loading metrics…</p>
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
                      <td className="py-2 pr-4 font-medium text-gray-900 dark:text-white">
                        {m.pipelineName}
                      </td>
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
            )}
          </div>
        )}
      </div>
    </div>
  );
}
