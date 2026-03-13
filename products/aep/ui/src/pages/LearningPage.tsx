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
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  approvePolicy,
  rejectPolicy,
  triggerReflection,
} from '@/api/aep.api';
import { useAllEpisodes, usePolicies, POLICIES_QUERY_KEY } from '@/hooks/useAgentMemory';
import { EpisodeTimeline } from '@/components/memory/EpisodeTimeline';
import { PolicyCard } from '@/components/memory/PolicyCard';

// ─── Episodes tab ────────────────────────────────────────────────────

function EpisodesTab() {
  const { data: episodes = [], isLoading } = useAllEpisodes(50);

  return (
    <div>
      {isLoading && <p className="text-center text-gray-400 py-12">Loading episodes…</p>}
      {!isLoading && <EpisodeTimeline episodes={episodes} />}
    </div>
  );
}

// ─── Policies tab ────────────────────────────────────────────────────

function PoliciesTab() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  const { data: policies = [], isLoading } = usePolicies();

  const approveMut = useMutation({
    mutationFn: (id: string) => approvePolicy(id, tenantId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: [POLICIES_QUERY_KEY] }),
  });

  const rejectMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      rejectPolicy(id, reason, tenantId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: [POLICIES_QUERY_KEY] }),
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
          {policies.map((policy) => (
            <PolicyCard
              key={policy.id}
              policy={policy}
              isSubmitting={approveMut.isPending || rejectMut.isPending}
              onApprove={(id) => approveMut.mutate(id)}
              onReject={(id, reason) => rejectMut.mutate({ id, reason })}
            />
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
