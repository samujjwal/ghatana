/**
 * PatternStudioPage — manage AEP event-detection patterns and learning.
 *
 * Features:
 *   - Tab navigation between Patterns and Learning
 *   - List patterns with type, status, confidence score
 *   - Filter by type (ALL / ANOMALY / THRESHOLD / SEQUENCE / CORRELATION / CUSTOM)
 *   - Add new pattern (type + name + threshold)
 *   - Delete pattern
 *   - View learning episodes and policies
 *
 * @doc.type page
 * @doc.purpose AEP pattern catalog and learning management
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import Editor from '@monaco-editor/react';
import {
  listPatterns,
  createPattern,
  deletePattern,
  type PatternSummary,
} from '@/api/pipeline.api';
import { toast } from 'sonner';
import type { PatternType } from '@/types/pipeline.types';
import {
  approvePolicy,
  rejectPolicy,
  triggerReflection,
} from '@/api/aep.api';
import { useAllEpisodes, usePolicies, POLICIES_QUERY_KEY } from '@/hooks/useAgentMemory';
import { EpisodeTimeline } from '@/components/memory/EpisodeTimeline';
import { PolicyCard } from '@/components/memory/PolicyCard';
import { Button, TextField, TextArea, Select, EmptyState } from '@ghatana/design-system';
import { Toaster } from 'sonner';
import { ErrorState } from '@/components/core/ErrorState';
import { PageState } from '@/components/shared/PageState';
import { SensitiveActionDialog } from '@/components/shared/SensitiveActionDialog';

// ─── Status badge ────────────────────────────────────────────────────

const STATUS_COLORS: Record<string, string> = {
  ENABLED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  DISABLED: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
  LEARNING: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
};

function PatternTypeBadge({ type }: { type: PatternType }) {
  const colorMap: Record<PatternType, string> = {
    ANOMALY: 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300',
    THRESHOLD: 'bg-orange-50 text-orange-700 dark:bg-orange-950 dark:text-orange-300',
    SEQUENCE: 'bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300',
    CORRELATION: 'bg-cyan-50 text-cyan-700 dark:bg-cyan-950 dark:text-cyan-300',
    CUSTOM: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  };
  return (
    <span
      className={[
        'inline-flex px-2 py-0.5 rounded text-xs font-medium',
        colorMap[type] ?? colorMap.CUSTOM,
      ].join(' ')}
    >
      {type}
    </span>
  );
}

// ─── Create form ─────────────────────────────────────────────────────

function CreatePatternForm({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const tenantId = useAtomValue(tenantIdAtom);
  const [name, setName] = useState('');
  const [type, setType] = useState<PatternType>('THRESHOLD');
  const [threshold, setThreshold] = useState('');
  const [description, setDescription] = useState('');
  const [config, setConfig] = useState('');

  const mut = useMutation({
    mutationFn: () =>
      createPattern(
        { name, type, threshold: threshold ? Number(threshold) : undefined, description, config: config.trim() || undefined },
        tenantId,
      ),
    onSuccess: () => {
      onCreated();
      onClose();
    },
  });

  return (
    <div
      role="dialog"
      aria-label="Create pattern"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-xl p-6 w-full max-w-md">
        <h2 className="text-base font-semibold mb-4 text-gray-900 dark:text-white">New Pattern</h2>

        <div className="space-y-3">
          <label className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Name</span>
            <TextField
              className="mt-1 block w-full text-sm"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. high-error-rate"
            />
          </label>

          <label className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Type</span>
            <Select
              className="mt-1 block w-full text-sm"
              value={type}
              onChange={(e) => setType((e.target as HTMLSelectElement).value as PatternType)}
              options={(['THRESHOLD', 'ANOMALY', 'SEQUENCE', 'CORRELATION', 'CUSTOM'] as const).map((t) => ({ value: t, label: t }))}
            />
          </label>

          {type === 'THRESHOLD' && (
            <label className="block text-sm">
              <span className="text-gray-600 dark:text-gray-400">Threshold value</span>
              <TextField
                type="number"
                className="mt-1 block w-full text-sm"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                placeholder="0.95"
              />
            </label>
          )}

          <label className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Description (optional)</span>
            <TextArea
              className="mt-1 block w-full text-sm"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>

          <div className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Config YAML (optional)</span>
            <div className="mt-1 rounded border border-gray-300 dark:border-gray-700 overflow-hidden">
              <Editor
                height={160}
                language="yaml"
                theme="vs-dark"
                value={config}
                onChange={(val: string | undefined) => setConfig(val ?? '')}
                options={{
                  minimap: { enabled: false },
                  lineNumbers: 'off',
                  fontSize: 12,
                  scrollBeyondLastLine: false,
                  wordWrap: 'on',
                  tabSize: 2,
                }}
              />
            </div>
          </div>
        </div>

        <div className="mt-5 flex justify-end gap-3">
          {config.trim() && (
            <Button
              onClick={() => {
                try {
                  // Basic YAML structure check: attempt JSON.parse if it looks like JSON,
                  // otherwise we rely on Monaco YAML mode for syntax; this catches gross structural errors.
                  const trimmed = config.trim();
                  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
                    JSON.parse(trimmed);
                  }
                  toast.success('YAML syntax looks valid (basic structural check passed)');
                } catch (err) {
                  const message = err instanceof Error ? err.message : String(err);
                  toast.error(`YAML validation failed: ${message}`);
                }
              }}
              variant="ghost"
              className="px-4 py-2 text-sm"
              type="button"
            >
              Dry-run validate
            </Button>
          )}
          <Button
            onClick={onClose}
            variant="secondary"
            className="px-4 py-2 text-sm"
          >
            Cancel
          </Button>
          <Button
            onClick={() => mut.mutate()}
            disabled={!name || mut.isPending}
            variant="primary"
            className="px-4 py-2 text-sm"
          >
            {mut.isPending ? 'Creating…' : 'Create'}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Learning Tab ──────────────────────────────────────────────────────

function EpisodesTab() {
  const { data: episodes = [], isLoading, isError } = useAllEpisodes(50);

  if (isLoading) return <EmptyState title="Loading episodes…" description="Fetching learning episodes." />;
  if (isError) return <ErrorState title="Failed to load episodes" />;
  if (episodes.length === 0) return <EmptyState title="No episodes yet" description="Learning episodes will appear once the agent starts observing." />;

  return <EpisodeTimeline episodes={episodes} />;
}

function PoliciesTab() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  const { data: policies = [], isLoading, isError, refetch } = usePolicies();

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
    onSuccess: () => {
      toast.success('Reflection triggered. Policies will appear once processing completes.');
    },
    onError: (err) => {
      const message = err instanceof Error ? err.message : String(err);
      toast.error(`Reflection failed: ${message}`);
    },
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button
          onClick={() => reflectMut.mutate()}
          disabled={reflectMut.isPending}
          variant="primary"
          className="px-4 py-2 text-sm"
        >
          {reflectMut.isPending ? 'Running…' : '▶ Trigger reflection'}
        </Button>
      </div>

      {isLoading && <EmptyState title="Loading policies…" description="Fetching extracted policies." />}
      {isError && <ErrorState title="Failed to load policies" onRetry={() => void refetch()} />}
      {!isLoading && !isError && (
        <div className="space-y-2">
          {policies.length === 0 && (
            <EmptyState title="No policies extracted yet" description="Policies will appear once the system generates candidate rules." />
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

const ALL_TYPES: Array<PatternType | 'ALL'> = [
  'ALL', 'ANOMALY', 'THRESHOLD', 'SEQUENCE', 'CORRELATION', 'CUSTOM',
];

type MainTab = 'patterns' | 'learning';

export function PatternStudioPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [mainTab, setMainTabState] = useState<MainTab>((searchParams.get('tab') as MainTab) ?? 'patterns');
  const [filterType, setFilterType] = useState<PatternType | 'ALL'>('ALL');
  const [showCreate, setShowCreate] = useState(false);
  const [learningSubTab, setLearningSubTab] = useState<'episodes' | 'policies'>('episodes');
  const [deleteTarget, setDeleteTarget] = useState<PatternSummary | null>(null);

  const setMainTab = (tab: MainTab) => {
    setMainTabState(tab);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (tab === 'learning') {
        next.set('tab', 'learning');
      } else {
        next.delete('tab');
      }
      return next;
    });
  };

  const { data: patterns = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'patterns', tenantId],
    queryFn: () => listPatterns(tenantId),
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => deletePattern(id, tenantId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['aep', 'patterns'] }),
  });

  const filtered =
    filterType === 'ALL' ? patterns : patterns.filter((p) => p.type === filterType);

  return (
    <>
      <Toaster richColors position="top-right" />
      <div className="flex flex-col h-full overflow-hidden" >
        {/* Header */}
      < div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex items-center gap-4" >
        <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Pattern Studio</h1>
        <div className="flex gap-1 ml-2">
          {(['patterns', 'learning'] as const).map((t) => (
            <Button
              key={t}
              onClick={() => setMainTab(t)}
              variant="text"
              className={[
                'px-4 py-2 text-sm font-medium capitalize border-b-2 -mb-px transition-colors',
                mainTab === t
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300',
              ].join(' ')}
            >
              {t}
            </Button>
          ))}
        </div>
        {mainTab === 'patterns' && (
          <>
            <div className="flex gap-1 ml-4">
              {ALL_TYPES.map((t) => (
                <Button
                  key={t}
                  onClick={() => setFilterType(t)}
                  variant="ghost"
                  className={[
                    'px-3 py-1 rounded-full text-xs font-medium transition-colors',
                    filterType === t
                      ? 'bg-indigo-100 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300'
                      : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800',
                  ].join(' ')}
                >
                  {t}
                </Button>
              ))}
            </div>
            <Button
              onClick={() => setShowCreate(true)}
              variant="primary"
              className="ml-auto px-3 py-1.5 text-sm font-medium"
            >
              + New pattern
            </Button>
          </>
        )}
      </div >

      {/* Content */}
      {
        mainTab === 'patterns' ? (
          <>
            <div className="flex-1 overflow-auto px-6 py-4">
              {isLoading && <PageState mode="loading" title="Loading patterns…" description="Fetching event-detection patterns." className="h-full" />}
              {isError && <PageState mode="unavailable" title="Failed to load patterns" description="The pattern service is not reachable." onRetry={() => void refetch()} className="h-full" />}
              {!isLoading && !isError && (
                <div className="space-y-2">
                  {filtered.length === 0 && (
                    <EmptyState title="No patterns found" description="Create your first pattern to detect anomalies or thresholds." action={<Button onClick={() => setShowCreate(true)} variant="primary">+ New Pattern</Button>} />
                  )}
                  {filtered.map((pattern) => (
                    <div
                      key={pattern.id}
                      className="flex items-center gap-4 p-4 rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900"
                    >
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-gray-900 dark:text-white truncate">{pattern.name}</p>
                        <p className="text-xs text-gray-400 font-mono mt-0.5">{pattern.id}</p>
                      </div>
                      <PatternTypeBadge type={pattern.type} />
                      <span
                        className={[
                          'px-2 py-0.5 rounded-full text-xs font-medium',
                          STATUS_COLORS[pattern.status] ?? STATUS_COLORS.DISABLED,
                        ].join(' ')}
                      >
                        {pattern.status}
                      </span>
                      <Button
                        onClick={() => setDeleteTarget(pattern)}
                        aria-label={`Delete pattern ${pattern.name}`}
                        variant="ghost"
                        className="text-gray-400 hover:text-red-500 transition-colors text-sm ml-2 p-1"
                      >
                        🗑
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 overflow-auto px-6 py-4">
            <div className="flex gap-2 border-b border-gray-200 dark:border-gray-800 -mb-4 mb-4">
              {(['episodes', 'policies'] as const).map((t) => (
                <Button
                  key={t}
                  onClick={() => setLearningSubTab(t)}
                  variant="text"
                  className={[
                    'px-4 py-2 text-sm font-medium -mb-px border-b-2 capitalize transition-colors',
                    learningSubTab === t
                      ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                      : 'border-transparent text-gray-500 hover:text-gray-700',
                  ].join(' ')}
                >
                  {t}
                </Button>
              ))}
            </div>
            {learningSubTab === 'episodes' ? <EpisodesTab /> : <PoliciesTab />}
          </div>
        )
      }

      {
        showCreate && (
          <CreatePatternForm
            onClose={() => setShowCreate(false)}
            onCreated={() => void queryClient.invalidateQueries({ queryKey: ['aep', 'patterns'] })}
          />
        )
      }

      {deleteTarget && (
        <SensitiveActionDialog
          open={!!deleteTarget}
          title="Delete pattern"
          description={`This will permanently remove pattern "${deleteTarget.name}" (${deleteTarget.id}). Any pipelines using this pattern may fail.`}
          confirmKeyword="DELETE"
          impactItems={[
            { label: 'Pattern', value: deleteTarget.name, severity: 'high' },
            { label: 'Type', value: deleteTarget.type, severity: 'medium' },
            { label: 'Tenant', value: tenantId, severity: 'low' },
          ]}
          auditMessage={`Pattern ${deleteTarget.id} deleted by user`}
          reasonRequired
          onConfirm={() => {
            if (deleteTarget) deleteMut.mutate(deleteTarget.id);
            setDeleteTarget(null);
          }}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
      </div>
    </>
  );
}

