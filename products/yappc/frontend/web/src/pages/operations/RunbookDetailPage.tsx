import React, { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

type StepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
type RunStatus = 'success' | 'failure' | 'running' | 'cancelled';

interface RunbookStep {
  id: string;
  order: number;
  name: string;
  description: string;
  command: string;
  status: StepStatus;
  durationMs: number | null;
}

interface ExecutionRecord {
  id: string;
  triggeredBy: string;
  startedAt: string;
  finishedAt: string | null;
  status: RunStatus;
  stepsCompleted: number;
  stepsTotal: number;
}

interface RunbookData {
  id: string;
  name: string;
  description: string;
  tags: string[];
  updatedAt: string;
  steps: RunbookStep[];
  executions: ExecutionRecord[];
}

const authHeaders = (): Record<string, string> => ({
  Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
  'Content-Type': 'application/json',
});

const STEP_STATUS_STYLE: Record<StepStatus, { dot: string; text: string }> = {
  pending: { dot: 'bg-zinc-600', text: 'text-zinc-500' },
  running: { dot: 'bg-blue-500 animate-pulse', text: 'text-blue-400' },
  completed: { dot: 'bg-green-500', text: 'text-green-400' },
  failed: { dot: 'bg-red-500', text: 'text-red-400' },
  skipped: { dot: 'bg-zinc-500', text: 'text-zinc-500' },
};

const RUN_STATUS_BADGE: Record<RunStatus, string> = {
  success: 'bg-green-500/20 text-green-400',
  failure: 'bg-red-500/20 text-red-400',
  running: 'bg-blue-500/20 text-blue-400',
  cancelled: 'bg-zinc-500/20 text-zinc-400',
};

/**
 * RunbookDetailPage — Runbook detail with steps, execution history, and run action.
 *
 * @doc.type component
 * @doc.purpose Runbook detail and execution management view
 * @doc.layer product
 */
const RunbookDetailPage: React.FC = () => {
  const { runbookId } = useParams<{ runbookId: string }>();
  const [activeTab, setActiveTab] = useState<'steps' | 'history'>('steps');
  const queryClient = useQueryClient();

  const { data: runbook, isLoading, error } = useQuery<RunbookData>({
    queryKey: ['runbook', runbookId],
    queryFn: async () => {
      const res = await fetch(`/api/runbooks/${runbookId}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load runbook');
      return res.json() as Promise<RunbookData>;
    },
    enabled: !!runbookId,
  });

  const runMutation = useMutation<void, Error>({
    mutationFn: async () => {
      const res = await fetch(`/api/runbooks/${runbookId}/run`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Failed to start runbook');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['runbook', runbookId] });
    },
  });

  const formatDuration = (ms: number | null) => {
    if (ms === null) return '—';
    if (ms < 1_000) return `${ms}ms`;
    return `${(ms / 1_000).toFixed(1)}s`;
  };

  const formatTimestamp = (ts: string) =>
    new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          {error instanceof Error ? error.message : 'Failed to load runbook'}
        </div>
      </div>
    );
  }

  const tabs: { key: 'steps' | 'history'; label: string }[] = [
    { key: 'steps', label: `Steps (${runbook?.steps.length ?? 0})` },
    { key: 'history', label: `History (${runbook?.executions.length ?? 0})` },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-zinc-500">
        <Link to="/operations/runbooks" className="hover:text-zinc-300 transition-colors">Runbooks</Link>
        <span>/</span>
        <span className="text-zinc-300 truncate max-w-xs">{runbook?.name ?? 'Runbook'}</span>
      </nav>

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-zinc-100">{runbook?.name}</h1>
          <p className="text-sm text-zinc-400 max-w-2xl">{runbook?.description}</p>
          {(runbook?.tags ?? []).length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {runbook?.tags.map((tag) => (
                <span key={tag} className="px-2 py-0.5 text-xs bg-zinc-800 text-zinc-400 rounded-full">{tag}</span>
              ))}
            </div>
          )}
        </div>
        <button
          onClick={() => runMutation.mutate()}
          disabled={runMutation.isPending}
          className="px-5 py-2.5 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {runMutation.isPending ? 'Starting…' : 'Run Runbook'}
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-zinc-800 pb-1">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
              activeTab === tab.key
                ? 'bg-zinc-700 text-zinc-100'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Steps tab */}
      {activeTab === 'steps' && (
        <div className="space-y-2">
          {(runbook?.steps ?? []).length === 0 ? (
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 text-center text-zinc-500 text-sm">
              No steps defined.
            </div>
          ) : (
            runbook?.steps.map((step) => {
              const style = STEP_STATUS_STYLE[step.status];
              return (
                <div key={step.id} className="bg-zinc-900 border border-zinc-800 rounded-lg p-4 flex items-start gap-4">
                  <div className="flex items-center justify-center w-8 h-8 rounded-full bg-zinc-800 text-zinc-400 text-sm font-semibold shrink-0">
                    {step.order}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-semibold text-zinc-200">{step.name}</h3>
                      <span className={`flex items-center gap-1 text-xs ${style.text}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${style.dot}`} />
                        {step.status}
                      </span>
                      {step.durationMs !== null && (
                        <span className="text-xs text-zinc-600">{formatDuration(step.durationMs)}</span>
                      )}
                    </div>
                    <p className="text-xs text-zinc-500 mt-1">{step.description}</p>
                    {step.command && (
                      <code className="block mt-2 text-xs font-mono bg-zinc-950 text-zinc-400 px-3 py-1.5 rounded border border-zinc-800 truncate">
                        {step.command}
                      </code>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* History tab */}
      {activeTab === 'history' && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg overflow-hidden">
          {(runbook?.executions ?? []).length === 0 ? (
            <div className="p-8 text-center text-zinc-500 text-sm">No execution history.</div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-800 text-left">
                  <th className="px-4 py-3 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Status</th>
                  <th className="px-4 py-3 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Triggered By</th>
                  <th className="px-4 py-3 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Started</th>
                  <th className="px-4 py-3 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Finished</th>
                  <th className="px-4 py-3 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Progress</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800">
                {runbook?.executions.map((exec) => (
                  <tr key={exec.id} className="hover:bg-zinc-800/30">
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 text-xs font-medium rounded ${RUN_STATUS_BADGE[exec.status]}`}>
                        {exec.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-zinc-300">{exec.triggeredBy}</td>
                    <td className="px-4 py-3 text-zinc-400">{formatTimestamp(exec.startedAt)}</td>
                    <td className="px-4 py-3 text-zinc-400">{exec.finishedAt ? formatTimestamp(exec.finishedAt) : '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-20 h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-blue-500 rounded-full"
                            style={{ width: `${exec.stepsTotal ? (exec.stepsCompleted / exec.stepsTotal) * 100 : 0}%` }}
                          />
                        </div>
                        <span className="text-xs text-zinc-500">{exec.stepsCompleted}/{exec.stepsTotal}</span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
};

export default RunbookDetailPage;
