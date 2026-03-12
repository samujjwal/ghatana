/**
 * LearningPage — view agent learning episodes and promoted policies.
 *
 * Two tabs:
 *   Episodes  — raw learning episode log (outcome, latency, input/output summary)
 *   Policies  — synthesized policies awaiting or completed human review
 *               with approve / reject actions for PENDING_REVIEW policies
 *               and a "Trigger reflection" button
 *
 * @doc.type page
 * @doc.purpose AEP agent learning history and policy management
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listEpisodes,
  listPolicies,
  approvePolicy,
  rejectPolicy,
  triggerReflection,
  type EpisodeRecord,
  type LearnedPolicy,
  type EpisodeOutcome,
  type PolicyStatus,
} from '@/api/aep.api';

// ─── Styling maps ────────────────────────────────────────────────────

const OUTCOME_COLORS: Record<EpisodeOutcome, string> = {
  SUCCESS: 'text-green-600 dark:text-green-400',
  FAILURE: 'text-red-600 dark:text-red-400',
  TIMEOUT: 'text-orange-600 dark:text-orange-400',
  CANCELLED: 'text-gray-500',
};

const POLICY_STATUS_COLORS: Record<PolicyStatus, string> = {
  PENDING_REVIEW: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  APPROVED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  REJECTED: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
  ACTIVE: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200',
  DEPRECATED: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
};

// ─── Episodes tab ────────────────────────────────────────────────────

function EpisodesTab() {
  const tenantId = 'default';
  const { data: episodes = [], isLoading } = useQuery({
    queryKey: ['aep', 'episodes', tenantId],
    queryFn: () => listEpisodes(tenantId, 50),
  });

  return (
    <div>
      {isLoading && <p className="text-center text-gray-400 py-12">Loading episodes…</p>}
      {!isLoading && (
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="text-left text-xs font-medium text-gray-500 uppercase tracking-wider border-b border-gray-200 dark:border-gray-800">
              <th className="pb-2 pr-4">Agent</th>
              <th className="pb-2 pr-4">Pipeline</th>
              <th className="pb-2 pr-4">Outcome</th>
              <th className="pb-2 pr-4">Latency</th>
              <th className="pb-2 pr-4">Input</th>
              <th className="pb-2">Time</th>
            </tr>
          </thead>
          <tbody>
            {episodes.length === 0 && (
              <tr>
                <td colSpan={6} className="py-8 text-center text-gray-400 italic">
                  No episodes recorded yet
                </td>
              </tr>
            )}
            {episodes.map((ep: EpisodeRecord) => (
              <tr
                key={ep.id}
                className="border-b border-gray-100 dark:border-gray-900 hover:bg-gray-50 dark:hover:bg-gray-900 group"
              >
                <td className="py-2 pr-4 font-medium text-gray-900 dark:text-white">{ep.agentId}</td>
                <td className="py-2 pr-4 text-gray-500 font-mono text-xs">{ep.pipelineId}</td>
                <td className={['py-2 pr-4 font-medium', OUTCOME_COLORS[ep.outcome]].join(' ')}>
                  {ep.outcome}
                </td>
                <td className="py-2 pr-4 font-mono text-xs text-gray-500">{ep.latencyMs}ms</td>
                <td
                  className="py-2 pr-4 text-xs text-gray-400 max-w-xs truncate"
                  title={ep.inputSummary}
                >
                  {ep.inputSummary ?? '—'}
                </td>
                <td className="py-2 text-xs text-gray-400">
                  {new Date(ep.timestamp).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ─── Policies tab ────────────────────────────────────────────────────

function PoliciesTab() {
  const tenantId = 'default';
  const queryClient = useQueryClient();
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const { data: policies = [], isLoading } = useQuery({
    queryKey: ['aep', 'policies', tenantId],
    queryFn: () => listPolicies(tenantId),
  });

  const approveMut = useMutation({
    mutationFn: (id: string) => approvePolicy(id, tenantId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['aep', 'policies'] }),
  });

  const rejectMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      rejectPolicy(id, reason, tenantId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'policies'] });
      setRejectTarget(null);
      setRejectReason('');
    },
  });

  const reflectMut = useMutation({
    mutationFn: () => triggerReflection(tenantId),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          onClick={() => reflectMut.mutate()}
          disabled={reflectMut.isPending}
          className="px-4 py-2 text-sm rounded-md bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
        >
          {reflectMut.isPending ? 'Running…' : '▶ Trigger reflection'}
        </button>
      </div>

      {isLoading && <p className="text-center text-gray-400 py-12">Loading policies…</p>}
      {!isLoading && (
        <div className="space-y-2">
          {policies.length === 0 && (
            <p className="text-center text-gray-400 italic py-12">No policies extracted yet</p>
          )}
          {policies.map((policy: LearnedPolicy) => (
            <div
              key={policy.id}
              className="p-4 rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900"
            >
              {/* Top row */}
              <div className="flex items-start gap-3">
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-gray-900 dark:text-white">{policy.name}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{policy.description}</p>
                </div>
                <span
                  className={[
                    'flex-shrink-0 px-2 py-0.5 rounded-full text-xs font-medium',
                    POLICY_STATUS_COLORS[policy.status],
                  ].join(' ')}
                >
                  {policy.status.replace('_', ' ')}
                </span>
              </div>

              {/* Details row */}
              <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
                <span>Confidence {Math.round(policy.confidenceScore * 100)}%</span>
                <span>Episodes: {policy.episodeCount}</span>
                <span>v{policy.version}</span>
                <span className="ml-auto">
                  {new Date(policy.updatedAt).toLocaleDateString()}
                </span>
              </div>

              {/* Actions for PENDING_REVIEW */}
              {policy.status === 'PENDING_REVIEW' && (
                <div className="mt-3 flex gap-2">
                  <button
                    onClick={() => approveMut.mutate(policy.id)}
                    disabled={approveMut.isPending}
                    className="px-3 py-1 text-xs rounded bg-green-600 hover:bg-green-700 text-white disabled:opacity-50"
                  >
                    Approve
                  </button>
                  <button
                    onClick={() => setRejectTarget(policy.id)}
                    className="px-3 py-1 text-xs rounded bg-red-600 hover:bg-red-700 text-white"
                  >
                    Reject
                  </button>
                </div>
              )}

              {/* Reject form */}
              {rejectTarget === policy.id && (
                <div className="mt-3 space-y-2">
                  <textarea
                    className="block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-xs"
                    rows={2}
                    placeholder="Rejection reason (required)…"
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={() => { setRejectTarget(null); setRejectReason(''); }}
                      className="px-3 py-1 text-xs rounded border border-gray-200 dark:border-gray-700"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={() => rejectMut.mutate({ id: policy.id, reason: rejectReason })}
                      disabled={!rejectReason || rejectMut.isPending}
                      className="px-3 py-1 text-xs rounded bg-red-600 hover:bg-red-700 text-white disabled:opacity-50"
                    >
                      {rejectMut.isPending ? 'Rejecting…' : 'Confirm reject'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

export function LearningPage() {
  const [tab, setTab] = useState<'episodes' | 'policies'>('episodes');

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-white mb-3">Learning</h1>
        <div className="flex gap-2 border-b border-gray-200 dark:border-gray-800 -mb-4">
          {(['episodes', 'policies'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={[
                'px-4 py-2 text-sm font-medium -mb-px border-b-2 capitalize transition-colors',
                tab === t
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700',
              ].join(' ')}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {tab === 'episodes' ? <EpisodesTab /> : <PoliciesTab />}
      </div>
    </div>
  );
}
