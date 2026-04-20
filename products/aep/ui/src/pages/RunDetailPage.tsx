/**
 * RunDetailPage — unified run detail view for AEP operator cockpit.
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
import {
  cancelRun,
  getRunDetail,
  type PipelineRunDetail,
  type RunDecisionEntry,
  type RunLineageEntry,
  type RunPolicyEntry,
} from '@/api/aep.api';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { Button } from '@ghatana/design-system';

type TabId = 'lineage' | 'decisions' | 'policies';

const STATUS_COLORS: Record<PipelineRunDetail['status'], string> = {
  RUNNING: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300',
  SUCCEEDED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  FAILED: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300',
  CANCELLED: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
};

const TABS: { id: TabId; label: string }[] = [
  { id: 'lineage', label: 'Event Lineage' },
  { id: 'decisions', label: 'Agent Decisions' },
  { id: 'policies', label: 'Policies' },
];

function StatusBadge({ status }: { status: PipelineRunDetail['status'] }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_COLORS[status]}`}>
      {status}
    </span>
  );
}

function formatDuration(startedAt: string, finishedAt?: string): string {
  const start = new Date(startedAt).getTime();
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now();
  const ms = end - start;
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60_000)}m ${Math.floor((ms % 60_000) / 1000)}s`;
}

function MetaRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2 border-b border-gray-100 py-2 last:border-0 dark:border-gray-800">
      <span className="w-32 flex-shrink-0 text-xs font-medium text-gray-500 dark:text-gray-400">{label}</span>
      <span className="break-all font-mono text-sm text-gray-900 dark:text-gray-100">{value}</span>
    </div>
  );
}

function BoundaryPanel({
  title,
  summary,
  bullets,
  locked = false,
}: {
  title: string;
  summary: string;
  bullets: string[];
  locked?: boolean;
}) {
  return (
    <div className="p-6">
      <div className="max-w-3xl rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-950">
        <div className={`rounded-lg border px-4 py-3 text-sm ${locked
          ? 'border-gray-300 bg-gray-50 text-gray-700 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300'
          : 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200'}`}>
          <p className="font-medium">{title}</p>
          <p className="mt-1">{summary}</p>
        </div>

        <div className="mt-5">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Current run facts</h3>
          <ul className="list-disc space-y-2 pl-5 text-sm text-gray-700 dark:text-gray-300">
            {bullets.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

function EvidenceList<T>({
  rows,
  emptyMessage,
  renderRow,
}: {
  rows: T[];
  emptyMessage: string;
  renderRow: (row: T, index: number) => React.ReactNode;
}) {
  if (rows.length === 0) {
    return <p className="py-10 text-center text-sm text-gray-400">{emptyMessage}</p>;
  }

  return <div className="space-y-3 p-6">{rows.map(renderRow)}</div>;
}

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
    refetchInterval: (query) => query.state.data?.status === 'RUNNING' ? 3_000 : false,
  });

  const cancelMut = useMutation({
    mutationFn: () => cancelRun(runId!, tenantId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['aep', 'run', runId] }),
  });

  if (isLoading) {
    return <div className="flex h-full items-center justify-center text-sm text-gray-400">Loading run…</div>;
  }

  if (isError || !run) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-4">
        <p className="text-sm text-red-500">Run not found.</p>
        <Link to="/operate" className="text-xs text-indigo-600 hover:underline dark:text-indigo-400">
          ← Back to Runs &amp; Alerts
        </Link>
      </div>
    );
  }

  const duration = formatDuration(run.startedAt, run.finishedAt);

  return (
    <div className="flex h-full flex-col">
      <header className="flex-shrink-0 border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-800 dark:bg-gray-950">
        <nav aria-label="breadcrumb" className="mb-2 flex items-center gap-1 text-xs text-gray-400">
          <Link to="/operate" className="hover:text-indigo-600 dark:hover:text-indigo-400">Operate</Link>
          <span>/</span>
          <span className="text-gray-600 dark:text-gray-300">Run detail</span>
        </nav>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <StatusBadge status={run.status} />
            <h1 className="max-w-md truncate text-base font-semibold text-gray-900 dark:text-white">
              {run.pipelineName || run.pipelineId}
            </h1>
            <span className="hidden font-mono text-xs text-gray-400 sm:inline">{run.id}</span>
          </div>

          <div className="flex items-center gap-2">
            {run.status === 'RUNNING' && (
              <Button
                onClick={() => cancelMut.mutate()}
                disabled={cancelMut.isPending}
                variant="secondary"
                className="rounded bg-red-50 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50 dark:bg-red-950 dark:text-red-400 dark:hover:bg-red-900"
              >
                {cancelMut.isPending ? 'Cancelling…' : 'Cancel run'}
              </Button>
            )}
            {run.status === 'FAILED' && (
              <Link
                to="/operate/reviews"
                className="rounded bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700 transition-colors hover:bg-amber-100 dark:bg-amber-950 dark:text-amber-400 dark:hover:bg-amber-900"
              >
                Open review queue
              </Link>
            )}
            <Button
              onClick={() => navigate('/operate')}
              variant="secondary"
              className="rounded px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
            >
              ← All runs
            </Button>
          </div>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <aside className="w-72 flex-shrink-0 overflow-y-auto border-r border-gray-200 bg-white px-4 py-4 dark:border-gray-800 dark:bg-gray-950">
          <p className="mb-3 text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600">Overview</p>

          <MetaRow label="Run ID" value={run.id} />
          <MetaRow label="Pipeline" value={run.pipelineId} />
          <MetaRow label="Status" value={<StatusBadge status={run.status} />} />
          <MetaRow label="Started" value={new Date(run.startedAt).toLocaleString()} />
          {run.finishedAt && <MetaRow label="Finished" value={new Date(run.finishedAt).toLocaleString()} />}
          <MetaRow label="Duration" value={duration} />
          <MetaRow label="Events" value={String(run.eventsProcessed)} />
          <MetaRow label="Lineage rows" value={String(run.lineage.length)} />
          <MetaRow label="Decisions" value={String(run.decisions.length)} />
          <MetaRow label="Policies" value={String(run.policies.length)} />
          {run.errorsCount > 0 && (
            <MetaRow
              label="Errors"
              value={<span className="text-red-600 dark:text-red-400">{String(run.errorsCount)}</span>}
            />
          )}
        </aside>

        <div className="flex flex-1 flex-col overflow-hidden">
          <div className="flex flex-shrink-0 gap-0 border-b border-gray-200 bg-white px-4 dark:border-gray-800 dark:bg-gray-950">
            {TABS.map((tab) => (
              <Button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                variant="text"
                className={[
                  'border-b-2 px-4 py-3 text-sm font-medium transition-colors -mb-px',
                  activeTab === tab.id
                    ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                    : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200',
                ].join(' ')}
              >
                {tab.label}
              </Button>
            ))}
          </div>

          <div className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900">
            {activeTab === 'lineage' && isFeatureEnabled('EVENT_LINEAGE') ? (
              <EvidenceList<RunLineageEntry>
                rows={run.lineage}
                emptyMessage="No lineage events were recorded for this run."
                renderRow={(entry, index) => (
                  <div key={`${entry.eventType}-${entry.timestamp}-${index}`} className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-950">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <p className="text-sm font-semibold text-gray-900 dark:text-white">{entry.stepType}</p>
                        <p className="text-xs font-mono text-gray-500 dark:text-gray-400">{entry.eventType}</p>
                      </div>
                      <span className="text-xs text-gray-500 dark:text-gray-400">{new Date(entry.timestamp).toLocaleString()}</span>
                    </div>
                    <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">Pipeline {entry.pipelineId} · status {entry.status}</p>
                  </div>
                )}
              />
            ) : activeTab === 'lineage' ? (
              <BoundaryPanel
                title="Event lineage not enabled"
                summary="Event-lineage tracing is disabled for this tenant or deployment profile."
                bullets={[
                  'The UI does not render mock lineage nodes when the feature is disabled.',
                  'Enable the backend lineage feed before using this tab as an operator tool.',
                ]}
                locked
              />
            ) : null}

            {activeTab === 'decisions' && isFeatureEnabled('AGENT_DECISIONS') ? (
              <EvidenceList<RunDecisionEntry>
                rows={run.decisions}
                emptyMessage="No run-linked agent decisions were recorded for this execution."
                renderRow={(entry, index) => (
                  <div key={`${entry.reviewItemId}-${entry.decidedAt}-${index}`} className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-950">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <p className="text-sm font-semibold text-gray-900 dark:text-white">{entry.skillId || 'unknown skill'}</p>
                        <p className="text-xs font-mono text-gray-500 dark:text-gray-400">{entry.reviewItemId || 'review item unavailable'}</p>
                      </div>
                      <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">{entry.decision || 'UNKNOWN'}</span>
                    </div>
                    <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">{entry.decidedAt ? new Date(entry.decidedAt).toLocaleString() : 'No decision timestamp'}</p>
                  </div>
                )}
              />
            ) : activeTab === 'decisions' ? (
              <BoundaryPanel
                title="Agent decisions not enabled"
                summary="Agent-decision tracking is disabled for this tenant or deployment profile."
                bullets={[
                  'No synthetic agent decisions are shown while this feature is disabled.',
                  'Enable the decision feed before treating this tab as operational evidence.',
                ]}
                locked
              />
            ) : null}

            {activeTab === 'policies' && isFeatureEnabled('POLICY_REFERENCES') ? (
              <EvidenceList<RunPolicyEntry>
                rows={run.policies}
                emptyMessage="No policy-reference events were recorded for this run."
                renderRow={(entry, index) => (
                  <div key={`${entry.policyId}-${entry.promotedAt}-${index}`} className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-950">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <p className="text-sm font-semibold text-gray-900 dark:text-white">{entry.policyId || 'unknown policy'}</p>
                        <p className="text-xs text-gray-500 dark:text-gray-400">Skill {entry.skillId || 'n/a'} · version {entry.version || 'n/a'}</p>
                      </div>
                      <span className="text-xs text-gray-500 dark:text-gray-400">{entry.promotedAt ? new Date(entry.promotedAt).toLocaleString() : 'No promotion time'}</span>
                    </div>
                  </div>
                )}
              />
            ) : activeTab === 'policies' ? (
              <BoundaryPanel
                title="Policy references not enabled"
                summary="Policy-reference tracking is disabled for this tenant or deployment profile."
                bullets={[
                  'No mock policy data is rendered while the feature is disabled.',
                  'Enable policy-reference tracking before relying on this tab for run-level governance evidence.',
                ]}
                locked
              />
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
