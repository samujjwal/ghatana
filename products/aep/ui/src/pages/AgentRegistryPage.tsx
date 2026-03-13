/**
 * AgentRegistryPage — browse and manage registered AEP agents.
 *
 * Features:
 *   - Live list of all agents for the current tenant
 *   - Search by name / capability
 *   - Status badge (ACTIVE / IDLE / ERROR / UNKNOWN)
 *   - Agent detail drawer (click row) with capabilities + memory count
 *   - Deregister action
 *
 * @doc.type page
 * @doc.purpose AEP agent catalog browser
 * @doc.layer frontend
 */
import React, { useMemo, useState } from 'react';
import type { AgentRegistration } from '@/api/aep.api';
import { useAgents, useDeregisterAgent } from '@/hooks/useAgents';
import { AgentTable } from '@/components/agents/AgentTable';
import { AgentStatusBadge } from '@/components/agents/AgentStatusBadge';

// ─── Status styling ──────────────────────────────────────────────────

// ─── Agent Detail Panel ──────────────────────────────────────────────

function Row({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div>
      <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">{label}</span>
      <div className={['mt-0.5', mono ? 'font-mono text-xs' : ''].join(' ')}>{value}</div>
    </div>
  );
}

function AgentDetailPanel({
  agent,
  onClose,
  onDeregister,
  isDeregistering,
}: {
  agent: AgentRegistration;
  onClose: () => void;
  onDeregister: (id: string) => void;
  isDeregistering: boolean;
}) {
  return (
    <aside
      role="dialog"
      aria-label={`Agent details: ${agent.name}`}
      className="w-80 border-l border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex flex-col overflow-y-auto"
    >
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-800">
        <h2 className="font-semibold text-sm text-gray-900 dark:text-white truncate">{agent.name}</h2>
        <button
          onClick={onClose}
          aria-label="Close agent details"
          className="text-gray-400 hover:text-gray-600 focus:outline-none"
        >
          ✕
        </button>
      </div>

      <div className="px-4 py-3 space-y-3 text-sm">
        <Row label="ID" value={agent.id} mono />
        <Row label="Status" value={<AgentStatusBadge status={agent.status} />} />
        <Row label="Version" value={agent.version} />
        <Row label="Tenant" value={agent.tenantId} />
        <Row label="Memory items" value={String(agent.memoryCount)} />
        <Row label="Registered" value={new Date(agent.registeredAt).toLocaleString()} />
        {agent.lastSeen && (
          <Row label="Last seen" value={new Date(agent.lastSeen).toLocaleString()} />
        )}

        <div>
          <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-1">
            Capabilities
          </p>
          {agent.capabilities.length === 0 ? (
            <p className="text-gray-400 text-xs italic">None declared</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {agent.capabilities.map((cap) => (
                <span
                  key={cap}
                  className="px-2 py-0.5 rounded bg-indigo-50 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300 text-xs font-mono"
                >
                  {cap}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="mt-auto px-4 py-3 border-t border-gray-200 dark:border-gray-800">
        <button
          onClick={() => onDeregister(agent.id)}
          disabled={isDeregistering}
          className="w-full px-3 py-2 text-sm font-medium rounded-md bg-red-50 dark:bg-red-950 text-red-700 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900 disabled:opacity-50 transition-colors"
        >
          {isDeregistering ? 'Removing…' : 'Deregister agent'}
        </button>
      </div>
    </aside>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

export function AgentRegistryPage() {
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<AgentRegistration | null>(null);

  const { data: agents = [], isLoading, isError } = useAgents();
  const deregisterMut = useDeregisterAgent();

  const filtered = useMemo(() => {
    if (!search) return agents;
    const q = search.toLowerCase();
    return agents.filter(
      (a) =>
        a.name.toLowerCase().includes(q) ||
        a.capabilities.some((c) => c.toLowerCase().includes(q)),
    );
  }, [agents, search]);

  function handleDeregister(id: string) {
    deregisterMut.mutate(id, { onSuccess: () => setSelected(null) });
  }

  return (
    <div className="flex h-full">
      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center gap-4 bg-white dark:bg-gray-950">
          <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Agent Registry</h1>
          <span className="text-xs text-gray-400">{agents.length} registered</span>
          <input
            type="search"
            placeholder="Search by name or capability…"
            aria-label="Search agents"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="ml-auto w-64 text-sm rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        {/* Table */}
        <div className="flex-1 overflow-auto px-6 py-4">
          {isLoading && <p className="text-center text-gray-400 py-12">Loading agents…</p>}
          {isError && (
            <p className="text-center text-red-500 py-12">Failed to load agents. Is the AEP backend running?</p>
          )}
          {!isLoading && !isError && (
            <AgentTable
              agents={filtered}
              selectedId={selected?.id}
              onSelect={setSelected}
            />
          )}
        </div>
      </div>

      {/* Detail panel */}
      {selected && (
        <AgentDetailPanel
          agent={selected}
          onClose={() => setSelected(null)}
          onDeregister={handleDeregister}
          isDeregistering={deregisterMut.isPending}
        />
      )}
    </div>
  );
}
