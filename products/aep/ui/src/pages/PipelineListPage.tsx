/**
 * PipelineListPage — Browse, create, and manage AEP pipelines.
 *
 * Lists all pipelines for the active tenant. Each entry shows its name, status,
 * stage count, and last-updated time. Users can navigate to the builder to edit
 * an existing pipeline, or start a new one.
 *
 * @doc.type page
 * @doc.purpose Pipeline browse and management page
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { listPipelines, deletePipeline } from '@/api/pipeline.api';
import type { PipelineStatus } from '@/types/pipeline.types';

// ─── Status badge ────────────────────────────────────────────────────

const STATUS_STYLES: Record<PipelineStatus, string> = {
  DRAFT:     'bg-gray-100 text-gray-600',
  VALID:     'bg-green-100 text-green-700',
  PUBLISHED: 'bg-blue-100 text-blue-700',
  RUNNING:   'bg-amber-100 text-amber-700',
  FAILED:    'bg-red-100 text-red-700',
  ARCHIVED:  'bg-gray-200 text-gray-500',
};

function StatusBadge({ status }: { status: PipelineStatus }) {
  return (
    <span
      className={[
        'inline-block px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide',
        STATUS_STYLES[status] ?? 'bg-gray-100 text-gray-600',
      ].join(' ')}
    >
      {status}
    </span>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

/**
 * Browse and manage pipelines for the active tenant.
 */
export function PipelineListPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const { data: pipelines = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'pipelines', tenantId],
    queryFn: () => listPipelines(tenantId),
    staleTime: 30_000,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePipeline(id, tenantId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['aep', 'pipelines', tenantId] });
      setConfirmDelete(null);
    },
  });

  const filtered = pipelines.filter((p) =>
    !search || p.name.toLowerCase().includes(search.toLowerCase()),
  );

  function handleEdit(id: string | undefined) {
    if (id) {
      void navigate(`/pipelines?id=${encodeURIComponent(id)}`);
    } else {
      void navigate('/pipelines');
    }
  }

  function handleNew() {
    void navigate('/pipelines');
  }

  return (
    <div className="p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Pipelines</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Manage event-processing pipelines for tenant{' '}
            <span className="font-mono text-indigo-600">{tenantId}</span>
          </p>
        </div>
        <button
          onClick={handleNew}
          className="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-md
                     hover:bg-indigo-700 transition-colors"
        >
          + New Pipeline
        </button>
      </div>

      {/* Search */}
      <div className="mb-4">
        <input
          type="search"
          placeholder="Search pipelines…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:w-80 px-3 py-1.5 text-sm border border-gray-200 rounded-md
                     bg-white dark:bg-gray-900 dark:border-gray-700 dark:text-white
                     focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      {/* Loading / Error */}
      {isLoading && (
        <div className="text-center py-12 text-gray-400">Loading pipelines…</div>
      )}
      {isError && (
        <div className="text-center py-12">
          <p className="text-red-500 text-sm mb-2">Failed to load pipelines</p>
          <button
            onClick={() => void refetch()}
            className="text-xs text-indigo-600 underline"
          >
            Retry
          </button>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !isError && filtered.length === 0 && (
        <div className="text-center py-16 text-gray-400">
          <p className="text-lg mb-1">No pipelines found</p>
          <p className="text-sm mb-4">
            {search ? 'Try clearing the search filter.' : 'Create your first pipeline to get started.'}
          </p>
          {!search && (
            <button
              onClick={handleNew}
              className="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-md
                         hover:bg-indigo-700 transition-colors"
            >
              + New Pipeline
            </button>
          )}
        </div>
      )}

      {/* Pipeline list */}
      {!isLoading && !isError && filtered.length > 0 && (
        <div className="space-y-3">
          {filtered.map((pipeline) => {
            const stageCount = pipeline.stages?.length ?? 0;
            const agentCount = pipeline.stages?.reduce(
              (n, s) => n + (s.workflow?.length ?? 0),
              0,
            ) ?? 0;

            return (
              <div
                key={pipeline.id ?? pipeline.name}
                className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800
                           rounded-lg p-4 flex items-center gap-4"
              >
                {/* Icon */}
                <div className="h-10 w-10 rounded-md bg-indigo-50 dark:bg-indigo-950 flex items-center
                                justify-center flex-shrink-0 text-indigo-500 text-xl">
                  ⚡
                </div>

                {/* Details */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-gray-900 dark:text-white truncate">
                      {pipeline.name}
                    </span>
                    <StatusBadge status={(pipeline.status as PipelineStatus) ?? 'DRAFT'} />
                  </div>
                  {pipeline.description && (
                    <p className="text-xs text-gray-500 mt-0.5 truncate">{pipeline.description}</p>
                  )}
                  <div className="flex gap-3 mt-1 text-xs text-gray-400">
                    <span>{stageCount} stage{stageCount !== 1 ? 's' : ''}</span>
                    <span>{agentCount} agent{agentCount !== 1 ? 's' : ''}</span>
                    {pipeline.id && (
                      <span className="font-mono text-[10px]">{pipeline.id}</span>
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleEdit(pipeline.id)}
                    className="px-3 py-1 text-xs font-medium border border-gray-200 rounded
                               hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    Edit
                  </button>
                  {pipeline.id && (
                    <button
                      onClick={() => setConfirmDelete(pipeline.id!)}
                      disabled={deleteMutation.isPending}
                      className="px-3 py-1 text-xs font-medium border border-red-200 text-red-600
                                 rounded hover:bg-red-50 transition-colors disabled:opacity-40"
                    >
                      Delete
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Delete confirmation modal */}
      {confirmDelete && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-900 rounded-xl p-6 max-w-sm w-full mx-4 shadow-xl">
            <h2 className="font-semibold text-gray-900 dark:text-white mb-2">Delete pipeline?</h2>
            <p className="text-sm text-gray-500 mb-4">
              This will permanently remove the pipeline. This action cannot be undone.
            </p>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setConfirmDelete(null)}
                className="px-3 py-1.5 text-sm border border-gray-200 rounded hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={() => deleteMutation.mutate(confirmDelete)}
                disabled={deleteMutation.isPending}
                className="px-3 py-1.5 text-sm bg-red-600 text-white rounded hover:bg-red-700
                           disabled:opacity-40 transition-colors"
              >
                {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
