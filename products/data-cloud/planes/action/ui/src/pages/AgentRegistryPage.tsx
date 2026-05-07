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
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import type { AgentRegistration } from '@/api/aep.api';
import { useAgents, useDeregisterAgent } from '@/hooks/useAgents';
import { PageState } from '@/components/shared/PageState';
import { getMarketplaceUrl, getWorkflowCatalogUrl } from '@/lib/routes';
import { AgentTable } from '@/components/agents/AgentTable';
import { AgentStatusBadge } from '@/components/agents/AgentStatusBadge';
import { Button } from '@ghatana/design-system';
import { TextField } from '@ghatana/design-system';
import { SensitiveActionDialog } from '@/components/shared/SensitiveActionDialog';

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

function registrationModeLabel(agent: AgentRegistration): string {
  return agent.registrationMode === 'manifest-only' ? 'Discovery only' : 'Direct registration';
}

function persistenceLabel(value: string): string {
  return value === 'datacloud' ? 'Data Cloud' : 'Unavailable';
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
        <Button
          onClick={onClose}
          aria-label="Close agent details"
          variant="ghost"
          className="text-gray-400 hover:text-gray-600 focus:outline-none p-1"
        >
          ✕
        </Button>
      </div>

      <div className="px-4 py-3 space-y-3 text-sm">
        <Row label="ID" value={agent.id} mono />
        <Row label="Status" value={<AgentStatusBadge status={agent.status} />} />
        <Row label="Type" value={agent.type} />
        <Row label="Version" value={agent.version} />
        <Row label="Tenant" value={agent.tenantId} />
        <Row label="Registration" value={registrationModeLabel(agent)} />
        <Row label="Execution" value={agent.executable ? 'Executable' : 'Blocked for execution'} />
        <Row label="Registry storage" value={persistenceLabel(agent.registryStorage)} />
        <Row label="Memory persistence" value={persistenceLabel(agent.memoryPersistence)} />
        <Row label="Memory items" value={String(agent.memoryCount)} />
        <Row label="Registered" value={new Date(agent.registeredAt).toLocaleString()} />
        {agent.lastSeen && (
          <Row label="Last seen" value={new Date(agent.lastSeen).toLocaleString()} />
        )}
        {agent.description ? <Row label="Description" value={agent.description} /> : null}

        {!agent.executable ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
            This agent was registered for discovery and catalog visibility only. The runtime will reject direct execution requests until a real executable implementation is attached.
          </div>
        ) : null}

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
        <Button
          onClick={() => onDeregister(agent.id)}
          disabled={isDeregistering}
          variant="secondary"
          className="w-full px-3 py-2 text-sm font-medium bg-red-50 dark:bg-red-950 text-red-700 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900"
        >
          {isDeregistering ? 'Removing…' : 'Deregister agent'}
        </Button>
      </div>
    </aside>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

export function AgentRegistryPage() {
  const navigate = useNavigate();
  const { agentId } = useParams<{ agentId?: string }>();
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<AgentRegistration | null>(null);
  const [deregisterTarget, setDeregisterTarget] = useState<AgentRegistration | null>(null);
  const [page, setPage] = useState(1);
  const PAGE_SIZE = 20;

  const { data: agents = [], isLoading, isError } = useAgents();

  // Auto-select agent when deep-linked via /catalog/agents/:agentId
  useEffect(() => {
    if (!agentId || agents.length === 0) return;
    const matched = agents.find((a) => a.id === agentId);
    if (matched) {
      setSelected(matched);
    }
  }, [agentId, agents]);
  const deregisterMut = useDeregisterAgent();

  const filtered = useMemo(() => {
    if (!search) return agents;
    const q = search.toLowerCase();
    return agents.filter(
      (a) =>
        a.name.toLowerCase().includes(q) ||
        a.id.toLowerCase().includes(q) ||
        a.type.toLowerCase().includes(q) ||
        a.description.toLowerCase().includes(q) ||
        a.capabilities.some((c) => c.toLowerCase().includes(q)),
    );
  }, [agents, search]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(Math.max(1, page), totalPages);
  const pagedAgents = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  function handleDeregister(id: string) {
    const target = agents.find((a) => a.id === id) ?? null;
    setDeregisterTarget(target);
  }

  function confirmDeregister(reason: string) {
    if (!deregisterTarget) return;
    deregisterMut.mutate(deregisterTarget.id, {
      onSuccess: () => {
        setSelected(null);
        setDeregisterTarget(null);
      },
    });
  }

  return (
    <div className="flex h-full">
      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center gap-4 bg-white dark:bg-gray-950">
          <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Agent Registry</h1>
          <span className="text-xs text-gray-400">{agents.length} registered</span>
          <TextField
            type="search"
            placeholder="Search by name or capability…"
            aria-label="Search agents"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="ml-auto w-64 text-sm"
          />
        </div>

        {/* Table */}
        <div className="flex-1 overflow-auto px-6 py-4">
          {isLoading && <PageState mode="loading" title="Loading agents…" description="Fetching registered agents for this tenant." className="h-full" />}
          {isError && (
            <PageState mode="unavailable" title="Failed to load agents" description="The agent registry service is not reachable." className="h-full" />
          )}
          {!isLoading && !isError && filtered.length === 0 && (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <div className="w-16 h-16 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center mb-4">
                <svg className="w-8 h-8 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                </svg>
              </div>
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">No agents registered</h3>
              <p className="text-sm text-gray-500 dark:text-gray-400 max-w-md mb-6">
                Get started by registering your first agent. Agents provide pattern detection, enrichment, and other AI capabilities for your event pipelines.
              </p>
              <div className="flex gap-3">
                <Button
                  onClick={() => void navigate(getMarketplaceUrl())}
                  variant="primary"
                  className="px-4 py-2 text-sm font-medium"
                >
                  Register first agent
                </Button>
                <Button
                  onClick={() => void navigate(getWorkflowCatalogUrl())}
                  variant="secondary"
                  className="px-4 py-2 text-sm font-medium"
                >
                  Auto-discover services
                </Button>
              </div>
            </div>
          )}
          {!isLoading && !isError && filtered.length > 0 && (
            <>
              <AgentTable
                agents={pagedAgents}
                selectedId={selected?.id}
                onSelect={setSelected}
              />
              {totalPages > 1 && (
                <div className="mt-4 flex items-center justify-between">
                  <span className="text-xs text-gray-500 dark:text-gray-400">
                    Showing {pagedAgents.length} of {filtered.length} agents
                  </span>
                  <div className="flex items-center gap-1">
                    <Button
                      onClick={() => setPage((p) => Math.max(1, p - 1))}
                      disabled={safePage <= 1}
                      variant="ghost"
                      className="px-2 py-1 text-xs"
                    >
                      Previous
                    </Button>
                    <span className="text-xs font-medium text-gray-700 dark:text-gray-300 px-2">
                      {safePage} / {totalPages}
                    </span>
                    <Button
                      onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                      disabled={safePage >= totalPages}
                      variant="ghost"
                      className="px-2 py-1 text-xs"
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </>
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

      {/* Deregister confirmation */}
      {deregisterTarget && (
        <SensitiveActionDialog
          open={!!deregisterTarget}
          title="Deregister agent"
          description={`This will remove agent "${deregisterTarget.name}" (${deregisterTarget.id}) from the registry. Any pipelines using this agent may fail.`}
          confirmKeyword="DEREGISTER"
          impactItems={[
            { label: 'Agent', value: deregisterTarget.name, severity: 'high' },
            { label: 'ID', value: deregisterTarget.id, severity: 'medium' },
            { label: 'Tenant', value: deregisterTarget.tenantId, severity: 'low' },
            { label: 'Capabilities', value: deregisterTarget.capabilities.join(', ') || 'none', severity: 'medium' },
          ]}
          auditMessage={`Agent ${deregisterTarget.id} deregistered by user`}
          reasonRequired
          onConfirm={confirmDeregister}
          onCancel={() => setDeregisterTarget(null)}
        />
      )}
    </div>
  );
}
