/**
 * MemoryExplorerPage — tenant-wide and agent-scoped memory explorer.
 *
 * Features:
 *   - Optional agent selector — scopes episode/fact/policy queries to one agent,
 *     or shows tenant-level data when no agent is selected
 *   - Three tabs: Episodes (episodic) | Facts (semantic) | Policies (procedural)
 *   - Reuses EpisodeTimeline, FactTable, PolicyCard shared components
 *   - Read-only view; policy approval/rejection is handled on LearningPage
 *
 * @doc.type page
 * @doc.purpose AEP agent memory explorer across all memory types
 * @doc.layer frontend
 */
import React, { useState, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { useAgents } from '@/hooks/useAgents';
import {
  useAllEpisodes,
  usePolicies,
  useAgentFacts,
} from '@/hooks/useAgentMemory';
import { EpisodeTimeline } from '@/components/memory/EpisodeTimeline';
import { FactTable } from '@/components/memory/FactTable';
import { PolicyCard } from '@/components/memory/PolicyCard';
import type { AgentRegistration } from '@/api/aep.api';
import { Button } from '@ghatana/design-system';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';

// ─── Types ────────────────────────────────────────────────────────────────────

type MemoryTab = 'episodes' | 'facts' | 'policies';

// ─── Agent selector ───────────────────────────────────────────────────────────

interface AgentSelectorProps {
  agents: AgentRegistration[];
  selectedId: string | undefined;
  onChange: (id: string | undefined) => void;
}

function AgentSelector({ agents, selectedId, onChange }: AgentSelectorProps) {
  return (
    <div className="flex items-center gap-2">
      <label
        htmlFor="agent-select"
        className="text-xs font-medium text-gray-600 dark:text-gray-400 shrink-0"
      >
        Agent:
      </label>
      <select
        id="agent-select"
        value={selectedId ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
        className="rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      >
        <option value="">All agents (tenant-level)</option>
        {agents.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name} ({a.id})
          </option>
        ))}
      </select>
    </div>
  );
}

// ─── Tab bar ──────────────────────────────────────────────────────────────────

const TABS: { key: MemoryTab; label: string }[] = [
  { key: 'episodes', label: 'Episodes' },
  { key: 'facts', label: 'Facts' },
  { key: 'policies', label: 'Policies' },
];

interface TabBarProps {
  active: MemoryTab;
  onChange: (tab: MemoryTab) => void;
}

function TabBar({ active, onChange }: TabBarProps) {
  return (
    <div className="flex gap-1 border-b border-gray-200 dark:border-gray-700">
      {TABS.map(({ key, label }) => (
        <Button
          key={key}
          type="button"
          onClick={() => onChange(key)}
          variant="text"
          className={[
            'px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
            active === key
              ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200',
          ].join(' ')}
        >
          {label}
        </Button>
      ))}
    </div>
  );
}

// ─── Episodes tab ─────────────────────────────────────────────────────────────

function EpisodesTab({ episodes, isLoading, isError, refetch }: { episodes: Episode[]; isLoading: boolean; isError: boolean; refetch: () => void }) {
  if (isLoading) {
    return <EmptyState title="Loading episodes…" description="Fetching agent episode history." />;
  }
  if (isError) {
    return <ErrorState title="Failed to load episodes" onRetry={() => void refetch()} />;
  }
  if (episodes.length === 0) {
    return <EmptyState title="No episodes found" description="Episodes will appear once the agent records memory events." />;
  }

  return <EpisodeTimeline episodes={episodes} />;
}

// ─── Facts tab ────────────────────────────────────────────────────────────────

function FactsTab({ agentId }: { agentId: string | undefined }) {
  const { data: facts = [], isLoading, isError, refetch } = useAgentFacts(agentId);

  if (isLoading) return <EmptyState title="Loading facts…" description="Fetching semantic memory." />;
  if (isError) return <ErrorState title="Failed to load facts" onRetry={() => void refetch()} />;
  if (facts.length === 0) return <EmptyState title="No facts found" description="Semantic facts will be extracted once the agent accumulates observations." />;
  return (
    <FactTable
      facts={facts}
      isLoading={isLoading}
      isError={isError}
    />
  );
}

// ─── Policies tab ─────────────────────────────────────────────────────────────
//
// LearnedPolicy records (synthesized by the reflection system) are tenant-level
// and do not carry an agentId. The agent selector therefore scopes episodes and
// facts but not policies — all tenant policies are always shown.

function PoliciesTab() {
  const { data: policies = [], isLoading, isError, refetch } = usePolicies();

  if (isLoading) return <EmptyState title="Loading policies…" description="Fetching procedural policies." />;
  if (isError) return <ErrorState title="Failed to load policies" onRetry={() => void refetch()} />;
  if (policies.length === 0) return (
    <EmptyState
      title="No policies found for this tenant"
      description="Policies will appear once the system generates candidate rules."
    />
  );
  return (
    <div className="grid gap-4 mt-4 sm:grid-cols-2 xl:grid-cols-3">
      {policies.map((policy) => (
        <PolicyCard key={policy.id} policy={policy} />
      ))}
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function MemoryExplorerPage() {
  const [activeTab, setActiveTab] = useState<MemoryTab>('episodes');
  const [selectedAgentId, setSelectedAgentId] = useState<string | undefined>(undefined);

  const { data: agents = [] } = useAgents();

  // Reset to episodes when switching agents so stale fact/policy queries don't flash
  function handleAgentChange(id: string | undefined) {
    setSelectedAgentId(id);
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shrink-0">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
              Memory Explorer
            </h1>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
              Browse episodic, semantic and procedural memory across agents
            </p>
          </div>
          <AgentSelector
            agents={agents}
            selectedId={selectedAgentId}
            onChange={handleAgentChange}
          />
        </div>
      </div>

      {/* Tab bar */}
      <div className="px-6 bg-white dark:bg-gray-900 shrink-0">
        <TabBar active={activeTab} onChange={setActiveTab} />
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {activeTab === 'episodes' && <EpisodesTab agentId={selectedAgentId} />}
        {activeTab === 'facts' && <FactsTab agentId={selectedAgentId} />}
        {activeTab === 'policies' && <PoliciesTab />}
      </div>
    </div>
  );
}
