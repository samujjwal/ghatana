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
import React, { useMemo, useState } from 'react';
import { useAgents } from '@/hooks/useAgents';
import {
  useAllEpisodes,
  useAgentEpisodes,
  usePolicies,
  useAgentFacts,
} from '@/hooks/useAgentMemory';
import { EpisodeTimeline } from '@/components/memory/EpisodeTimeline';
import { FactTable } from '@/components/memory/FactTable';
import { PolicyCard } from '@/components/memory/PolicyCard';
import type { AgentFact, AgentRegistration, EpisodeRecord, AgentEpisodeRecord, LearnedPolicy } from '@/api/aep.api';
import { Button } from '@ghatana/design-system';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';

// ─── Types ────────────────────────────────────────────────────────────────────

type MemoryTab = 'episodes' | 'facts' | 'policies';
type EpisodeOutcomeFilter = 'ALL' | 'SUCCESS' | 'FAILURE' | 'TIMEOUT' | 'CANCELLED';
type FactStatusFilter = 'ALL' | 'ACTIVE' | 'STALE' | 'RETRACTED' | 'ARCHIVED';
type PolicyStatusFilter = 'ALL' | 'ACTIVE' | 'PENDING_REVIEW' | 'REJECTED';
type ConfidenceFilter = 'ALL' | 'HIGH' | 'MEDIUM' | 'LOW';

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

// ─── Type helpers ─────────────────────────────────────────────────────────────

function normalizeAgentEpisodes(records: AgentEpisodeRecord[]): EpisodeRecord[] {
  return records.map((r) => ({
    id: r.id,
    tenantId: r.tenantId,
    agentId: r.agentId,
    pipelineId: '-',
    outcome: (r.outcome as EpisodeRecord['outcome']) ?? 'SUCCESS',
    latencyMs: r.latencyMs ?? 0,
    inputSummary: (r.input as string) ?? undefined,
    outputSummary: (r.output as string) ?? undefined,
    timestamp: r.timestamp,
  }));
}

function confidenceMatches(value: number | undefined, filter: ConfidenceFilter): boolean {
  if (filter === 'ALL') return true;
  if (value === undefined) return filter !== 'HIGH';
  if (filter === 'HIGH') return value >= 0.8;
  if (filter === 'MEDIUM') return value >= 0.5 && value < 0.8;
  return value < 0.5;
}

interface MemoryCitation {
  id: string;
  label: string;
}

interface MemorySummary {
  title: string;
  detail: string;
  citations: MemoryCitation[];
}

function buildEpisodeSummary(episodes: EpisodeRecord[]): MemorySummary {
  if (episodes.length === 0) {
    return {
      title: 'No visible episodes',
      detail: 'Change filters or agent scope to inspect recorded episodic memory.',
      citations: [],
    };
  }

  const succeeded = episodes.filter((episode) => episode.outcome === 'SUCCESS').length;
  const failed = episodes.filter((episode) => episode.outcome === 'FAILURE').length;
  const slowest = [...episodes].sort((a, b) => b.latencyMs - a.latencyMs)[0];

  return {
    title: 'Assist summary',
    detail: `${episodes.length} episode records are visible. ${succeeded} succeeded and ${failed} failed. The slowest visible episode is ${slowest.id} at ${slowest.latencyMs} ms.`,
    citations: episodes.slice(0, 3).map((episode) => ({
      id: episode.id,
      label: `${episode.id} · ${episode.outcome}`,
    })),
  };
}

function buildFactSummary(facts: AgentFact[]): MemorySummary {
  if (facts.length === 0) {
    return {
      title: 'No visible facts',
      detail: 'Select an agent or broaden filters once semantic memory has been recorded.',
      citations: [],
    };
  }

  const activeFacts = facts.filter((fact) => (fact.validityStatus ?? 'ACTIVE') === 'ACTIVE').length;
  const staleFacts = facts.filter((fact) => fact.validityStatus === 'STALE').length;
  const strongest = [...facts].sort((a, b) => b.confidence - a.confidence)[0];

  return {
    title: 'Assist summary',
    detail: `${facts.length} semantic facts are visible. ${activeFacts} are active and ${staleFacts} are stale. Highest-confidence fact: ${strongest.subject ?? strongest.id} (${Math.round(strongest.confidence * 100)}%).`,
    citations: facts.slice(0, 3).map((fact) => ({
      id: fact.id,
      label: `${fact.subject ?? fact.id} · ${fact.predicate ?? 'fact'}`,
    })),
  };
}

function buildPolicySummary(policies: LearnedPolicy[]): MemorySummary {
  if (policies.length === 0) {
    return {
      title: 'No visible policies',
      detail: 'Visible policy memory will be summarized here once learned policies exist for this tenant.',
      citations: [],
    };
  }

  const pending = policies.filter((policy) => policy.status === 'PENDING_REVIEW').length;
  const lowConfidence = policies.filter((policy) => policy.confidenceScore < 0.8).length;

  return {
    title: 'Assist summary',
    detail: `${policies.length} learned policies are visible. ${pending} still require review and ${lowConfidence} are below the auto-apply confidence tier.`,
    citations: policies.slice(0, 3).map((policy) => ({
      id: policy.id,
      label: `${policy.name} · v${policy.version}`,
    })),
  };
}

function SummaryPanel({ summary }: { summary: MemorySummary }) {
  return (
    <div className="mb-4 rounded-xl border border-indigo-200 bg-indigo-50 p-4 dark:border-indigo-900 dark:bg-indigo-950/40">
      <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
        {summary.title}
      </p>
      <p className="mt-1 text-sm text-indigo-950 dark:text-indigo-100">{summary.detail}</p>
      {summary.citations.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2 text-[11px]">
          {summary.citations.map((citation) => (
            <span
              key={citation.id}
              className="rounded-full border border-indigo-200 bg-white px-2 py-1 text-indigo-700 dark:border-indigo-800 dark:bg-indigo-950 dark:text-indigo-300"
            >
              {citation.label}
            </span>
          ))}
        </div>
      )}
      <p className="mt-2 text-[11px] text-indigo-700/80 dark:text-indigo-300/80">
        Derived from the records currently visible in this tab.
      </p>
    </div>
  );
}

// ─── Episodes tab ─────────────────────────────────────────────────────────────

function EpisodesTab({
  agentId,
  outcomeFilter,
}: {
  agentId: string | undefined;
  outcomeFilter: EpisodeOutcomeFilter;
}) {
  const scopedEpisodesQuery = useAgentEpisodes(agentId, 50);
  const tenantEpisodesQuery = useAllEpisodes(50);
  const rawEpisodes = agentId ? normalizeAgentEpisodes(scopedEpisodesQuery.data ?? []) : tenantEpisodesQuery.data ?? [];
  const isLoading = agentId ? scopedEpisodesQuery.isLoading : tenantEpisodesQuery.isLoading;
  const isError = agentId ? scopedEpisodesQuery.isError : tenantEpisodesQuery.isError;
  const refetch = agentId ? scopedEpisodesQuery.refetch : tenantEpisodesQuery.refetch;
  const episodes = React.useMemo(() => {
    if (outcomeFilter === 'ALL') {
      return rawEpisodes;
    }
    return rawEpisodes.filter((episode) => episode.outcome === outcomeFilter);
  }, [outcomeFilter, rawEpisodes]);

  if (isLoading) {
    return <EmptyState title="Loading episodes…" description="Fetching agent episode history." />;
  }
  if (isError) {
    return <ErrorState title="Failed to load episodes" onRetry={() => void refetch()} />;
  }
  if (episodes.length === 0) {
    return (
      <EmptyState
        title="No episodes found"
        description={
          agentId
            ? 'This agent has not recorded any episodes yet.'
            : 'Episodes will appear once agents record memory events.'
        }
      />
    );
  }

  return (
    <>
      <SummaryPanel summary={buildEpisodeSummary(episodes)} />
      <EpisodeTimeline episodes={episodes} />
    </>
  );
}

// ─── Facts tab ────────────────────────────────────────────────────────────────

function FactsTab({
  agentId,
  statusFilter,
  confidenceFilter,
}: {
  agentId: string | undefined;
  statusFilter: FactStatusFilter;
  confidenceFilter: ConfidenceFilter;
}) {
  if (!agentId) {
    return (
      <EmptyState
        title="Select an agent to inspect facts"
        description="Semantic facts are stored per agent, so choose one agent before reviewing fact memory."
      />
    );
  }

  const { data: facts = [], isLoading, isError, refetch } = useAgentFacts(agentId);
  const visibleFacts = useMemo(() => {
    return facts.filter((fact) => {
      const statusMatches = statusFilter === 'ALL' || (fact.validityStatus ?? 'ACTIVE') === statusFilter;
      return statusMatches && confidenceMatches(fact.confidence, confidenceFilter);
    });
  }, [confidenceFilter, facts, statusFilter]);

  if (isLoading) return <EmptyState title="Loading facts…" description="Fetching semantic memory." />;
  if (isError) return <ErrorState title="Failed to load facts" onRetry={() => void refetch()} />;
  if (facts.length === 0) return <EmptyState title="No facts found" description="Semantic facts will be extracted once the agent accumulates observations." />;
  return (
    <>
      <SummaryPanel summary={buildFactSummary(visibleFacts)} />
      <FactTable
        facts={visibleFacts}
        isLoading={isLoading}
        isError={isError}
      />
    </>
  );
}

// ─── Policies tab ─────────────────────────────────────────────────────────────
//
// LearnedPolicy records (synthesized by the reflection system) are tenant-level
// and do not carry an agentId. The agent selector therefore scopes episodes and
// facts but not policies — all tenant policies are always shown.

function PoliciesTab({
  statusFilter,
  confidenceFilter,
}: {
  statusFilter: PolicyStatusFilter;
  confidenceFilter: ConfidenceFilter;
}) {
  const { data: policies = [], isLoading, isError, refetch } = usePolicies();
  const visiblePolicies = useMemo(() => {
    return policies.filter((policy) => {
      const statusMatches = statusFilter === 'ALL' || policy.status === statusFilter;
      return statusMatches && confidenceMatches(policy.confidenceScore, confidenceFilter);
    });
  }, [confidenceFilter, policies, statusFilter]);

  if (isLoading) return <EmptyState title="Loading policies…" description="Fetching procedural policies." />;
  if (isError) return <ErrorState title="Failed to load policies" onRetry={() => void refetch()} />;
  if (policies.length === 0) return (
    <EmptyState
      title="No policies found for this tenant"
      description="Policies will appear once the system generates candidate rules."
    />
  );
  return (
    <>
      <SummaryPanel summary={buildPolicySummary(visiblePolicies)} />
      <div className="grid gap-4 mt-4 sm:grid-cols-2 xl:grid-cols-3">
        {visiblePolicies.map((policy) => (
          <PolicyCard key={policy.id} policy={policy} />
        ))}
      </div>
    </>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function MemoryExplorerPage() {
  const [activeTab, setActiveTab] = useState<MemoryTab>('episodes');
  const [selectedAgentId, setSelectedAgentId] = useState<string | undefined>(undefined);
  const [episodeOutcomeFilter, setEpisodeOutcomeFilter] = useState<EpisodeOutcomeFilter>('ALL');
  const [factStatusFilter, setFactStatusFilter] = useState<FactStatusFilter>('ALL');
  const [policyStatusFilter, setPolicyStatusFilter] = useState<PolicyStatusFilter>('ALL');
  const [confidenceFilter, setConfidenceFilter] = useState<ConfidenceFilter>('ALL');

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

      <div className="px-6 py-3 border-b border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="flex flex-wrap gap-3 text-sm">
          {activeTab === 'episodes' && (
            <label className="flex items-center gap-2 text-gray-600 dark:text-gray-400">
              Outcome
              <select
                value={episodeOutcomeFilter}
                onChange={(event) => setEpisodeOutcomeFilter(event.target.value as EpisodeOutcomeFilter)}
                className="rounded-md border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
              >
                <option value="ALL">All outcomes</option>
                <option value="SUCCESS">Success</option>
                <option value="FAILURE">Failure</option>
                <option value="TIMEOUT">Timeout</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </label>
          )}

          {activeTab === 'facts' && (
            <>
              <label className="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                Status
                <select
                  value={factStatusFilter}
                  onChange={(event) => setFactStatusFilter(event.target.value as FactStatusFilter)}
                  className="rounded-md border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="ALL">All statuses</option>
                  <option value="ACTIVE">Active</option>
                  <option value="STALE">Stale</option>
                  <option value="RETRACTED">Retracted</option>
                  <option value="ARCHIVED">Archived</option>
                </select>
              </label>
              <label className="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                Confidence
                <select
                  value={confidenceFilter}
                  onChange={(event) => setConfidenceFilter(event.target.value as ConfidenceFilter)}
                  className="rounded-md border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="ALL">All tiers</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </label>
            </>
          )}

          {activeTab === 'policies' && (
            <>
              <label className="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                Status
                <select
                  value={policyStatusFilter}
                  onChange={(event) => setPolicyStatusFilter(event.target.value as PolicyStatusFilter)}
                  className="rounded-md border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="ALL">All statuses</option>
                  <option value="ACTIVE">Active</option>
                  <option value="PENDING_REVIEW">Pending review</option>
                  <option value="REJECTED">Rejected</option>
                </select>
              </label>
              <label className="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                Confidence
                <select
                  value={confidenceFilter}
                  onChange={(event) => setConfidenceFilter(event.target.value as ConfidenceFilter)}
                  className="rounded-md border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="ALL">All tiers</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </label>
            </>
          )}
        </div>
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {activeTab === 'episodes' && <EpisodesTab agentId={selectedAgentId} outcomeFilter={episodeOutcomeFilter} />}
        {activeTab === 'facts' && (
          <FactsTab
            agentId={selectedAgentId}
            statusFilter={factStatusFilter}
            confidenceFilter={confidenceFilter}
          />
        )}
        {activeTab === 'policies' && (
          <PoliciesTab
            statusFilter={policyStatusFilter}
            confidenceFilter={confidenceFilter}
          />
        )}
      </div>
    </div>
  );
}
