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
import { Button } from '@ghatana/design-system';
import { TextField } from '@ghatana/design-system';
import {
  getNewPipelineUrl,
  getEditPipelineUrl,
} from '@/lib/routes';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';
import { SensitiveActionDialog } from '@/components/shared/SensitiveActionDialog';
import { PipelineDryRunDialog } from '@/components/pipeline/PipelineDryRunDialog';
import { useAuth } from '@/context/AuthContext';

// ─── Status badge ────────────────────────────────────────────────────

const STATUS_STYLES: Record<PipelineStatus, string> = {
  DRAFT: 'bg-gray-100 text-gray-600',
  VALID: 'bg-green-100 text-green-700',
  PUBLISHED: 'bg-blue-100 text-blue-700',
  RUNNING: 'bg-amber-100 text-amber-700',
  FAILED: 'bg-red-100 text-red-700',
  ARCHIVED: 'bg-gray-200 text-gray-500',
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
  const { hasAnyRole, hasRole, isVerifyingAuth } = useAuth();
  const tenantId = useAtomValue(tenantIdAtom);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [dryRunTarget, setDryRunTarget] = useState<{ id: string; name: string } | null>(null);
  const canManagePipelines = hasAnyRole(['admin', 'operator']);
  const canDeletePipelines = hasRole('admin');

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
      void navigate(getEditPipelineUrl(id));
    } else {
      void navigate(getNewPipelineUrl());
    }
  }

  function handleNew() {
    void navigate(getNewPipelineUrl());
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
        {canManagePipelines ? (
          <Button
            onClick={handleNew}
            variant="primary"
          >
            + New Pipeline
          </Button>
        ) : null}
      </div>

      {!isVerifyingAuth && !canManagePipelines && (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          Read-only access: pipeline creation, edits, dry runs, and deletes are limited to operator or admin roles.
        </div>
      )}

      {/* Search */}
      <div className="mb-4">
        <TextField
          type="search"
          placeholder="Search pipelines…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:w-80"
        />
      </div>

      {/* Loading / Error / Empty */}
      {isLoading && (
        <div className="text-center py-12 text-gray-400">Loading pipelines…</div>
      )}
      {isError && (
        <ErrorState
          title="Failed to load pipelines"
          onRetry={() => void refetch()}
        />
      )}
      {!isLoading && !isError && filtered.length === 0 && (
        <EmptyState
          title={search ? 'No pipelines match your search' : 'No pipelines found'}
          description={search ? 'Try clearing the search filter.' : 'Create your first pipeline to get started.'}
          action={!search && canManagePipelines ? { label: '+ New Pipeline', onClick: handleNew } : undefined}
        />
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
                  {canManagePipelines && (
                    <Button
                      variant="secondary"
                      className="text-xs font-medium"
                      onClick={() => handleEdit(pipeline.id)}
                    >
                      Edit
                    </Button>
                  )}
                  {canManagePipelines && pipeline.id && (
                    <Button
                      onClick={() => setDryRunTarget({ id: pipeline.id!, name: pipeline.name })}
                      variant="secondary"
                      className="text-xs font-medium"
                    >
                      Dry Run
                    </Button>
                  )}
                  {canDeletePipelines && pipeline.id && (
                    <Button
                      onClick={() => setConfirmDelete(pipeline.id!)}
                      disabled={deleteMutation.isPending}
                      variant="secondary"
                      className="text-xs font-medium text-red-600 border-red-200 hover:bg-red-50"
                    >
                      Delete
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Delete confirmation modal */}
      {confirmDelete && (
        <SensitiveActionDialog
          open={!!confirmDelete}
          title="Delete pipeline"
          description="This will permanently remove the pipeline and all associated runs. This action cannot be undone."
          confirmKeyword="DELETE"
          impactItems={[
            { label: 'Pipeline ID', value: confirmDelete, severity: 'high' },
            { label: 'Tenant', value: tenantId, severity: 'medium' },
          ]}
          auditMessage={`Pipeline ${confirmDelete} deleted by user`}
          reasonRequired
          onConfirm={() => {
            if (confirmDelete) deleteMutation.mutate(confirmDelete);
          }}
          onCancel={() => setConfirmDelete(null)}
        />
      )}

      {/* F-014: Dry-run pre-flight dialog */}
      {dryRunTarget && (
        <PipelineDryRunDialog
          open={!!dryRunTarget}
          pipelineId={dryRunTarget.id}
          pipelineName={dryRunTarget.name}
          tenantId={tenantId}
          onClose={() => setDryRunTarget(null)}
        />
      )}
    </div>
  );
}
