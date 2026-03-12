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
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listAgents,
  deregisterAgent,
  type AgentRegistration,
  type AgentStatus,
} from '@/api/aep.api';

// ─── Status styling ──────────────────────────────────────────────────

const STATUS_COLORS: Record<AgentStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  IDLE: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
  ERROR: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
  UNKNOWN: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
};

function StatusBadge({ status }: { status: AgentStatus }) {
  return (
    <span
      className={[
        'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
        STATUS_COLORS[status] ?? STATUS_COLORS.UNKNOWN,
      ].join(' ')}
    >
      {status}
    </span>
  );
}

// ─── Agent Detail Panel ──────────────────────────────────────────────

function AgentDetailPanel({
  agent,
  onClose,
  onDeregister,
}: {
  agent: AgentRegistration;
  onClose: () => void;
  onDeregister: (id: string) => void;
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
        <Row label="Status" value={<StatusBadge status={agent.status} />} />
        <Row label="Version" value={agent.version} />
        <Row label="Tenant" value={agent.tenantId} />
        <Row label="Memory items" value={String(agent.memoryCount)} />
        <Row
          label="Registered"
          value={new Date(agent.registeredAt).toLocaleString()}
        />
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
          className="w-full px-3 py-2 text-sm font-medium rounded-md bg-red-50 dark:bg-red-950 text-red-700 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900 transition-colors"
        >
          Deregister agent
        </button>
      </div>
    </aside>
  );
}

function Row({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div>
      <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">{label}</span>
      <div className={['mt-0.5', mono ? 'font-mono text-xs' : ''].join(' ')}>{value}</div>
    </div>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

export function AgentRegistryPage() {
  const tenantId = 'default';
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<AgentRegistration | null>(null);

  const { data: agents = [], isLoading, isError } = useQuery({
    queryKey: ['aep', 'agents', tenantId],
    queryFn: () => listAgents(tenantId),
    refetchInterval: 10_000,
  });

  const deregisterMut = useMutation({
    mutationFn: (id: string) => deregisterAgent(id, tenantId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'agents'] });
      setSelected(null);
    },
  });

  const filtered = useMemo(() => {
    if (!search) return agents;
    const q = search.toLowerCase();
    return agents.filter(
      (a) =>
        a.name.toLowerCase().includes(q) ||
        a.capabilities.some((c) => c.toLowerCase().includes(q)),
    );
  }, [agents, search]);

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
          {isLoading && (
            <p className="text-center text-gray-400 py-12">Loading agents…</p>
          )}
          {isError && (
            <p className="text-center text-red-500 py-12">Failed to load agents. Is the AEP backend running?</p>
          )}
          {!isLoading && !isError && (
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-800 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  <th className="pb-2 pr-4">Name</th>
                  <th className="pb-2 pr-4">Status</th>
                  <th className="pb-2 pr-4">Version</th>
                  <th className="pb-2 pr-4">Memory</th>
                  <th className="pb-2">Last seen</th>
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-gray-400 italic">
                      No agents found
                    </td>
                  </tr>
                )}
                {filtered.map((agent) => (
                  <tr
                    key={agent.id}
                    onClick={() => setSelected(agent)}
                    className={[
                      'border-b border-gray-100 dark:border-gray-900 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-900 transition-colors',
                      selected?.id === agent.id ? 'bg-indigo-50 dark:bg-indigo-950' : '',
                    ].join(' ')}
                  >
                    <td className="py-2 pr-4 font-medium text-gray-900 dark:text-white">{agent.name}</td>
                    <td className="py-2 pr-4">
                      <StatusBadge status={agent.status} />
                    </td>
                    <td className="py-2 pr-4 text-gray-500 font-mono">{agent.version}</td>
                    <td className="py-2 pr-4 text-gray-500">{agent.memoryCount}</td>
                    <td className="py-2 text-gray-400">
                      {agent.lastSeen
                        ? new Date(agent.lastSeen).toLocaleTimeString()
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Detail panel */}
      {selected && (
        <AgentDetailPanel
          agent={selected}
          onClose={() => setSelected(null)}
          onDeregister={(id) => deregisterMut.mutate(id)}
        />
      )}
    </div>
  );
}
