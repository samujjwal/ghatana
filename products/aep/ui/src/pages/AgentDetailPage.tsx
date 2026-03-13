/**
 * AgentDetailPage — detailed view of a single registered agent.
 *
 * Tabs:
 *   Overview   — agent metadata and status
 *   Memory     — memory item counts by type + episodic list
 *   Executions — recent executions via agent.invocation events
 *
 * @doc.type page
 * @doc.purpose Detailed agent view with memory and execution history
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import {
  getAgent,
  getAgentMemory,
  deregisterAgent,
  getAgentEpisodes,
  getAgentFacts,
  getAgentPolicies,
  type AgentRegistration,
  type AgentEpisodeRecord,
  type AgentFact,
  type AgentPolicyRecord,
} from '@/api/aep.api';
import { tenantIdAtom } from '@/stores/tenant.store';
import { FactTable } from '@/components/memory/FactTable';
import { ConfidenceBadge } from '@/components/shared/ConfidenceBadge';
import type { AgentMemorySummary } from '@/types/memory.types';

// ─── Helpers ─────────────────────────────────────────────────────────

function MemoryBar({
  label,
  value,
  total,
  color,
}: {
  label: string;
  value: number;
  total: number;
  color: string;
}) {
  const pct = total > 0 ? Math.round((value / total) * 100) : 0;
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs">
        <span className="text-gray-600 dark:text-gray-400">{label}</span>
        <span className="font-medium text-gray-900 dark:text-white">
          {value.toLocaleString()} ({pct}%)
        </span>
      </div>
      <div className="h-2 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
        <div
          className={`h-full rounded-full ${color}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  IDLE: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  ERROR: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
  UNKNOWN: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
};

// ─── Overview Tab ────────────────────────────────────────────────────

function OverviewTab({ agent }: { agent: AgentRegistration }) {
  const rows: [string, React.ReactNode][] = [
    ['ID', <code className="text-xs font-mono">{agent.id}</code>],
    ['Name', agent.name],
    ['Version', agent.version],
    ['Tenant', agent.tenantId],
    ['Capabilities', agent.capabilities.join(', ') || '—'],
    ['Registered', new Date(agent.registeredAt).toLocaleString()],
    ...(agent.lastSeen
      ? [['Last seen', new Date(agent.lastSeen).toLocaleString()] as [string, React.ReactNode]]
      : []),
  ];
  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 overflow-hidden">
      <table className="w-full text-sm">
        <tbody>
          {rows.map(([label, value]) => (
            <tr
              key={label}
              className="border-b border-gray-100 dark:border-gray-800 last:border-0"
            >
              <td className="px-4 py-3 font-medium text-gray-500 w-40">{label}</td>
              <td className="px-4 py-3 text-gray-900 dark:text-white">{value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── Memory Tab ──────────────────────────────────────────────────────

type MemorySubTab = 'episodes' | 'facts' | 'procedures';

function MemoryTab({
  agentId,
  tenantId,
}: {
  agentId: string;
  tenantId: string;
}) {
  const [subTab, setSubTab] = useState<MemorySubTab>('episodes');

  const { data: summary, isLoading: summaryLoading } = useQuery<AgentMemorySummary>({
    queryKey: ['aep', 'agent-memory', agentId, tenantId],
    queryFn: () => getAgentMemory(agentId, tenantId) as Promise<AgentMemorySummary>,
    staleTime: 60_000,
  });

  const bars: Array<{ label: string; key: keyof AgentMemorySummary['byType']; color: string }> = [
    { label: 'Episodic', key: 'EPISODIC', color: 'bg-indigo-500' },
    { label: 'Semantic', key: 'SEMANTIC', color: 'bg-violet-500' },
    { label: 'Procedural', key: 'PROCEDURAL', color: 'bg-emerald-500' },
    { label: 'Preference', key: 'PREFERENCE', color: 'bg-amber-500' },
    { label: 'Working', key: 'WORKING', color: 'bg-sky-400' },
    { label: 'Artifact', key: 'ARTIFACT', color: 'bg-gray-400' },
  ];

  const SUB_TABS: Array<{ id: MemorySubTab; label: string }> = [
    { id: 'episodes', label: 'Episodes' },
    { id: 'facts', label: 'Facts' },
    { id: 'procedures', label: 'Procedures' },
  ];

  return (
    <div className="space-y-6">
      {/* Summary distribution */}
      <div className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-5 py-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
            Memory Distribution
          </h3>
          {summaryLoading ? (
            <span className="text-xs text-gray-400">Loading…</span>
          ) : summary ? (
            <span className="text-xs text-gray-400">{summary.total.toLocaleString()} total items</span>
          ) : (
            <span className="text-xs text-gray-400">DataCloud not configured</span>
          )}
        </div>
        {summary && (
          <div className="space-y-3">
            {bars.map(({ label, key, color }) => (
              <MemoryBar
                key={key}
                label={label}
                value={summary.byType[key] ?? 0}
                total={summary.total}
                color={color}
              />
            ))}
          </div>
        )}
      </div>

      {/* Sub-tab selector */}
      <div className="flex gap-1 border-b border-gray-200 dark:border-gray-800">
        {SUB_TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setSubTab(t.id)}
            className={[
              'px-4 py-2 text-sm font-medium -mb-px border-b-2 transition-colors',
              subTab === t.id
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300',
            ].join(' ')}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Sub-tab content */}
      {subTab === 'episodes' && <EpisodesSection agentId={agentId} tenantId={tenantId} />}
      {subTab === 'facts' && <FactsSection agentId={agentId} tenantId={tenantId} />}
      {subTab === 'procedures' && <ProceduresSection agentId={agentId} tenantId={tenantId} />}
    </div>
  );
}

function EpisodesSection({ agentId, tenantId }: { agentId: string; tenantId: string }) {
  const { data: episodes = [], isLoading } = useQuery<AgentEpisodeRecord[]>({
    queryKey: ['aep', 'agent-episodes', agentId, tenantId],
    queryFn: () => getAgentEpisodes(agentId, tenantId, 20),
    staleTime: 30_000,
  });

  if (isLoading) {
    return <p className="text-gray-400 text-center py-4">Loading episodes…</p>;
  }

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100 dark:border-gray-800">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Recent Episodes</h3>
      </div>
      {episodes.length === 0 ? (
        <p className="text-gray-400 text-center py-6 text-sm italic">No episodes yet</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-xs text-gray-500 uppercase tracking-wider border-b border-gray-100 dark:border-gray-800">
              <th className="px-4 py-2 text-left">Turn ID</th>
              <th className="px-4 py-2 text-left">Outcome</th>
              <th className="px-4 py-2 text-left">Duration</th>
              <th className="px-4 py-2 text-left">Date</th>
            </tr>
          </thead>
          <tbody>
            {episodes.map((ep) => (
              <tr
                key={ep.id}
                className="border-b border-gray-50 dark:border-gray-900 hover:bg-gray-50 dark:hover:bg-gray-800"
              >
                <td className="px-4 py-2 font-mono text-xs text-gray-600 dark:text-gray-400">
                  {ep.id.slice(0, 8)}…
                </td>
                <td className="px-4 py-2">
                  <span
                    className={[
                      'text-xs font-medium',
                      ep.outcome === 'SUCCESS'
                        ? 'text-green-600 dark:text-green-400'
                        : ep.outcome === 'FAILURE'
                          ? 'text-red-600 dark:text-red-400'
                          : 'text-gray-500',
                    ].join(' ')}
                  >
                    {ep.outcome ?? '—'}
                  </span>
                </td>
                <td className="px-4 py-2 text-gray-500 font-mono text-xs">
                  {ep.latencyMs != null ? `${ep.latencyMs}ms` : '—'}
                </td>
                <td className="px-4 py-2 text-gray-400 text-xs">
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

function FactsSection({ agentId, tenantId }: { agentId: string; tenantId: string }) {
  const { data: facts = [], isLoading, isError } = useQuery<AgentFact[]>({
    queryKey: ['aep', 'agent-facts', agentId, tenantId],
    queryFn: () => getAgentFacts(agentId, tenantId, 100),
    staleTime: 60_000,
  });

  return <FactTable facts={facts} isLoading={isLoading} isError={isError} />;
}

function ProceduresSection({ agentId, tenantId }: { agentId: string; tenantId: string }) {
  const { data: policies = [], isLoading } = useQuery<AgentPolicyRecord[]>({
    queryKey: ['aep', 'agent-policies', agentId, tenantId],
    queryFn: () => getAgentPolicies(agentId, tenantId, 50),
    staleTime: 60_000,
  });

  if (isLoading) {
    return <p className="text-gray-400 text-center py-4">Loading procedures…</p>;
  }

  if (policies.length === 0) {
    return (
      <p className="text-gray-400 text-center py-8 text-sm italic">
        No procedures learned yet. Procedures are synthesized from recurring episode patterns.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {policies.map((policy) => (
        <div
          key={policy.id}
          className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-5 py-4"
        >
          <div className="flex items-start justify-between gap-3">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">
                {policy.name ?? <span className="italic text-gray-400">Unnamed procedure</span>}
              </p>
              {policy.description && (
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 line-clamp-2">
                  {policy.description}
                </p>
              )}
            </div>
            {policy.confidence != null && (
              <ConfidenceBadge value={policy.confidence} className="shrink-0" />
            )}
          </div>
          <div className="mt-3 flex items-center gap-4 text-xs text-gray-400">
            {policy.status && (
              <span
                className={[
                  'font-medium',
                  policy.status === 'APPROVED'
                    ? 'text-green-600 dark:text-green-400'
                    : policy.status === 'PENDING_REVIEW'
                      ? 'text-yellow-600 dark:text-yellow-400'
                      : policy.status === 'REJECTED'
                        ? 'text-red-600 dark:text-red-400'
                        : 'text-gray-500',
                ].join(' ')}
              >
                {policy.status}
              </span>
            )}
            {policy.episodeCount != null && (
              <span>{policy.episodeCount} episodes</span>
            )}
            {policy.createdAt && (
              <span>Created {new Date(policy.createdAt).toLocaleDateString()}</span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

type Tab = 'overview' | 'memory';

/**
 * Detailed view for a single agent, accessed via /agents/:agentId.
 */
export function AgentDetailPage() {
  const { agentId } = useParams<{ agentId: string }>();
  const navigate = useNavigate();
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('overview');
  const [confirmDelete, setConfirmDelete] = useState(false);

  const { data: agent, isLoading, isError } = useQuery<AgentRegistration>({
    queryKey: ['aep', 'agent', agentId, tenantId],
    queryFn: () => getAgent(agentId!, tenantId),
    enabled: !!agentId,
    staleTime: 60_000,
  });

  const deregisterMut = useMutation({
    mutationFn: () => deregisterAgent(agentId!, tenantId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'agents'] });
      void navigate('/agents');
    },
  });

  if (!agentId) {
    return (
      <div className="flex h-full items-center justify-center text-gray-400">
        Invalid agent ID
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-gray-400">
        Loading agent…
      </div>
    );
  }

  if (isError || !agent) {
    return (
      <div className="flex h-full items-center justify-center flex-col gap-3">
        <p className="text-gray-500">Agent not found or DataCloud not configured.</p>
        <button
          onClick={() => void navigate('/agents')}
          className="text-indigo-600 hover:underline text-sm"
        >
          ← Back to agents
        </button>
      </div>
    );
  }

  const TABS: Array<{ id: Tab; label: string }> = [
    { id: 'overview', label: 'Overview' },
    { id: 'memory', label: 'Memory' },
  ];

  return (
    <div className="flex flex-col h-full overflow-hidden bg-gray-50 dark:bg-gray-950">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex items-center gap-4">
        <button
          onClick={() => void navigate('/agents')}
          className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          aria-label="Back to agents"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-lg font-semibold text-gray-900 dark:text-white truncate">
            {agent.name}
          </h1>
          <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{agent.id}</p>
        </div>
        <span
          className={[
            'inline-flex px-2 py-0.5 rounded text-xs font-medium',
            STATUS_COLORS[agent.status] ?? STATUS_COLORS.UNKNOWN,
          ].join(' ')}
        >
          {agent.status}
        </span>
        <button
          onClick={() => setConfirmDelete(true)}
          className="text-xs text-red-600 hover:text-red-700 border border-red-200 rounded px-3 py-1.5 hover:bg-red-50 dark:hover:bg-red-950 transition-colors"
        >
          Deregister
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 px-6 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={[
              'px-4 py-2 text-sm font-medium -mb-px border-b-2 transition-colors',
              tab === t.id
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300',
            ].join(' ')}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {tab === 'overview' && <OverviewTab agent={agent} />}
        {tab === 'memory' && <MemoryTab agentId={agentId} tenantId={tenantId} />}
      </div>

      {/* Confirm deregister dialog */}
      {confirmDelete && (
        <div
          role="alertdialog"
          aria-label="Confirm deregister"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
        >
          <div className="bg-white dark:bg-gray-900 rounded-xl shadow-xl p-6 w-full max-w-sm">
            <h2 className="text-base font-semibold text-gray-900 dark:text-white mb-2">
              Deregister agent?
            </h2>
            <p className="text-sm text-gray-500 mb-5">
              This will remove <strong>{agent.name}</strong> from the registry. Active pipelines
              using this agent will fail.
            </p>
            {deregisterMut.isError && (
              <p className="text-sm text-red-600 mb-3">
                Failed to deregister agent. Please try again.
              </p>
            )}
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setConfirmDelete(false)}
                className="px-4 py-2 text-sm rounded border border-gray-300 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800"
              >
                Cancel
              </button>
              <button
                onClick={() => deregisterMut.mutate()}
                disabled={deregisterMut.isPending}
                className="px-4 py-2 text-sm rounded bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
              >
                {deregisterMut.isPending ? 'Deregistering…' : 'Deregister'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
