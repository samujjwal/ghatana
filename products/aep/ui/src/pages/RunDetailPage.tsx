/**
 * RunDetailPage — unified run detail view for AEP operator cockpit.
 *
 * Displays a single pipeline run as a coherent operator story, combining:
 *   - Run header: ID, pipeline, status badge, timing, quick actions
 *   - Overview panel: metadata, events processed, duration
 *   - Event Lineage tab: ordered lineage of events in this run
 *   - Agent Decisions tab: agent invocations and decisions recorded for this run
 *   - Policies tab: policies applied during execution
 *   - Actions panel: contextual actions (cancel, rerun, create review)
 *
 * Accessible from /operate/runs/:runId. Reachable from MonitoringDashboardPage
 * run row clicks and from HITL review items.
 *
 * @doc.type page
 * @doc.purpose Unified run detail — pipeline graph, event lineage, decisions, policies, actions
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { getRunDetail, cancelRun, type PipelineRun } from '@/api/aep.api';

// ─── Types ────────────────────────────────────────────────────────────

type TabId = 'lineage' | 'decisions' | 'policies';

// ─── Status badge ─────────────────────────────────────────────────────

const STATUS_COLORS: Record<PipelineRun['status'], string> = {
  RUNNING:   'bg-blue-100  text-blue-800  dark:bg-blue-900  dark:text-blue-300',
  SUCCEEDED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  FAILED:    'bg-red-100   text-red-800   dark:bg-red-900   dark:text-red-300',
  CANCELLED: 'bg-gray-100  text-gray-700  dark:bg-gray-800  dark:text-gray-300',
};

function StatusBadge({ status }: { status: PipelineRun['status'] }) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${STATUS_COLORS[status]}`}>
      {status}
    </span>
  );
}

// ─── Duration helper ──────────────────────────────────────────────────

function formatDuration(startedAt: string, finishedAt?: string): string {
  const start = new Date(startedAt).getTime();
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now();
  const ms = end - start;
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60_000)}m ${Math.floor((ms % 60_000) / 1000)}s`;
}

// ─── Metadata row ─────────────────────────────────────────────────────

function MetaRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2 py-2 border-b border-gray-100 dark:border-gray-800 last:border-0">
      <span className="w-32 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400 font-medium">{label}</span>
      <span className="text-sm text-gray-900 dark:text-gray-100 font-mono break-all">{value}</span>
    </div>
  );
}

// ─── Placeholder panel ────────────────────────────────────────────────

function ComingSoonPanel({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        className="h-10 w-10 text-gray-300 dark:text-gray-700"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.2}
        aria-hidden
      >
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
      </svg>
      <p className="text-sm font-semibold text-gray-600 dark:text-gray-400">{title}</p>
      <p className="text-xs text-gray-400 dark:text-gray-600 max-w-xs">{description}</p>
    </div>
  );
}

// ─── Tabs ─────────────────────────────────────────────────────────────

const TABS: { id: TabId; label: string }[] = [
  { id: 'lineage',   label: 'Event Lineage' },
  { id: 'decisions', label: 'Agent Decisions' },
  { id: 'policies',  label: 'Policies' },
];

// ─── Page ─────────────────────────────────────────────────────────────

/**
 * Unified run detail page — the primary operator view for a single pipeline execution.
 */
export function RunDetailPage() {
  const { runId } = useParams<{ runId: string }>();
  const tenantId = useAtomValue(tenantIdAtom);
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [activeTab, setActiveTab] = useState<TabId>('lineage');

  const { data: run, isLoading, isError } = useQuery({
    queryKey: ['aep', 'run', runId, tenantId],
    queryFn: () => getRunDetail(runId!, tenantId),
    enabled: !!runId,
    refetchInterval: (query) =>
      query.state.data?.status === 'RUNNING' ? 3_000 : false,
  });

  const cancelMut = useMutation({
    mutationFn: () => cancelRun(runId!, tenantId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['aep', 'run', runId] }),
  });

  // ── Loading/error states
  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-gray-400 text-sm">
        Loading run…
      </div>
    );
  }

  if (isError || !run) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-4">
        <p className="text-sm text-red-500">Run not found.</p>
        <Link to="/operate" className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline">
          ← Back to Runs &amp; Alerts
        </Link>
      </div>
    );
  }

  const duration = formatDuration(run.startedAt, run.finishedAt);

  return (
    <div className="flex flex-col h-full">
      {/* ── Header ──────────────────────────────────────────── */}
      <header className="flex-shrink-0 px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950">
        {/* Breadcrumb */}
        <nav aria-label="breadcrumb" className="flex items-center gap-1 text-xs text-gray-400 mb-2">
          <Link to="/operate" className="hover:text-indigo-600 dark:hover:text-indigo-400">Operate</Link>
          <span>/</span>
          <span className="text-gray-600 dark:text-gray-300">Run detail</span>
        </nav>

        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-3">
            <StatusBadge status={run.status} />
            <h1 className="text-base font-semibold text-gray-900 dark:text-white truncate max-w-md">
              {run.pipelineName || run.pipelineId}
            </h1>
            <span className="text-xs text-gray-400 font-mono hidden sm:inline">{run.id}</span>
          </div>

          {/* Quick actions */}
          <div className="flex items-center gap-2">
            {run.status === 'RUNNING' && (
              <button
                onClick={() => cancelMut.mutate()}
                disabled={cancelMut.isPending}
                className="px-3 py-1.5 rounded text-xs font-medium bg-red-50 text-red-700 hover:bg-red-100 dark:bg-red-950 dark:text-red-400 dark:hover:bg-red-900 transition-colors disabled:opacity-50"
              >
                {cancelMut.isPending ? 'Cancelling…' : 'Cancel run'}
              </button>
            )}
            {run.status === 'FAILED' && (
              <Link
                to="/operate/reviews"
                className="px-3 py-1.5 rounded text-xs font-medium bg-amber-50 text-amber-700 hover:bg-amber-100 dark:bg-amber-950 dark:text-amber-400 dark:hover:bg-amber-900 transition-colors"
              >
                Open review queue
              </Link>
            )}
            <button
              onClick={() => navigate('/operate')}
              className="px-3 py-1.5 rounded text-xs font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              ← All runs
            </button>
          </div>
        </div>
      </header>

      {/* ── Body ────────────────────────────────────────────── */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left: overview metadata */}
        <aside className="w-72 flex-shrink-0 border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 overflow-y-auto px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600 mb-3">Overview</p>

          <MetaRow label="Run ID"      value={run.id} />
          <MetaRow label="Pipeline"    value={run.pipelineId} />
          <MetaRow label="Status"      value={<StatusBadge status={run.status} />} />
          <MetaRow label="Started"     value={new Date(run.startedAt).toLocaleString()} />
          {run.finishedAt && (
            <MetaRow label="Finished"  value={new Date(run.finishedAt).toLocaleString()} />
          )}
          <MetaRow label="Duration"    value={duration} />
          {run.eventsProcessed !== undefined && (
            <MetaRow label="Events"    value={String(run.eventsProcessed)} />
          )}
          {run.errorsCount !== undefined && run.errorsCount > 0 && (
            <MetaRow label="Errors"    value={
              <span className="text-red-600 dark:text-red-400">{String(run.errorsCount)}</span>
            } />
          )}
        </aside>

        {/* Right: tabbed detail */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Tab bar */}
          <div className="flex-shrink-0 flex gap-0 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 px-4">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={[
                  'px-4 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
                  activeTab === tab.id
                    ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                    : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200',
                ].join(' ')}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900">
            {activeTab === 'lineage' && (
              <ComingSoonPanel
                title="Event lineage"
                description="Once Event Cloud integration is wired, this panel will show the ordered chain of events processed in this run, including source event, intermediate transformations, and output events."
              />
            )}
            {activeTab === 'decisions' && (
              <ComingSoonPanel
                title="Agent decisions"
                description="Agent decision records will appear here once the run ledger is connected. Each decision includes the agent ID, input context, confidence score, policy applied, and outcome."
              />
            )}
            {activeTab === 'policies' && (
              <ComingSoonPanel
                title="Policy references"
                description="When governance rules are applied during this run, they will be listed here with their version, approval status, and effect on agent behavior."
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
