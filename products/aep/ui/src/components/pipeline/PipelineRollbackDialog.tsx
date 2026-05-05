import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Input, TextArea, Radio } from '@ghatana/design-system';
import {
  listPipelineVersions,
  rollbackPipeline,
  type PipelineRollbackResult,
  type PipelineVersionSummary,
} from '@/api/pipeline.api';

interface PipelineRollbackDialogProps {
  open: boolean;
  tenantId: string;
  pipelineId: string;
  pipelineName: string;
  currentVersion?: number;
  actor?: string;
  onClose: () => void;
  onRolledBack: (result: PipelineRollbackResult) => void;
}

function formatTimestamp(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? 'Unavailable' : parsed.toLocaleString();
}

export function PipelineRollbackDialog({
  open,
  tenantId,
  pipelineId,
  pipelineName,
  currentVersion,
  actor,
  onClose,
  onRolledBack,
}: PipelineRollbackDialogProps): React.ReactElement | null {
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [confirmText, setConfirmText] = useState('');

  const { data: versions = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'pipeline-versions', tenantId, pipelineId],
    queryFn: () => listPipelineVersions(pipelineId, tenantId),
    enabled: open,
    staleTime: 30_000,
  });

  const eligibleVersions = useMemo(
    () => versions.filter((version) => version.version !== currentVersion),
    [currentVersion, versions],
  );
  const target = eligibleVersions.find((version) => version.version === selectedVersion) ?? eligibleVersions[0];

  const rollbackMutation = useMutation({
    mutationFn: () =>
      rollbackPipeline(
        pipelineId,
        selectedVersion ?? 0,
        {
          reason: reason.trim(),
          actor,
        },
        tenantId,
      ),
    onSuccess: (result) => {
      onRolledBack(result);
    },
  });

  useEffect(() => {
    if (!open) {
      setSelectedVersion(null);
      setReason('');
      setConfirmText('');
      return;
    }
    setSelectedVersion((current) => current ?? (eligibleVersions[0]?.version ?? null));
  }, [eligibleVersions, open]);

  if (!open) {
    return null;
  }

  const canConfirm = selectedVersion !== null && reason.trim().length > 3 && confirmText.trim() === 'ROLLBACK';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div
        className="max-h-[80vh] w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-xl dark:bg-gray-900"
        role="dialog"
        aria-modal="true"
        aria-labelledby="pipeline-rollback-title"
      >
        <div className="border-b border-gray-200 px-6 py-4 dark:border-gray-800">
          <h2 id="pipeline-rollback-title" className="text-base font-semibold text-gray-900 dark:text-white">
            Roll Back Pipeline
          </h2>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Restore <span className="font-medium text-gray-900 dark:text-white">{pipelineName}</span> to a prior saved version and write a linked rollback audit event.
          </p>
        </div>

        <div className="space-y-5 overflow-y-auto px-6 py-5">
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
              <p className="text-xs text-gray-500 dark:text-gray-400">Pipeline</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">{pipelineName}</p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
              <p className="text-xs text-gray-500 dark:text-gray-400">Current version</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">
                {currentVersion ?? 'Unavailable'}
              </p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
              <p className="text-xs text-gray-500 dark:text-gray-400">Tenant</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">{tenantId}</p>
            </div>
          </div>

          {isLoading ? (
            <p className="text-sm text-gray-500 dark:text-gray-400">Loading version history…</p>
          ) : isError ? (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
              Failed to load version history.
              <Button type="button" onClick={() => void refetch()} variant="link" className="ml-2 text-inherit underline p-0 h-auto">
                Retry
              </Button>
            </div>
          ) : eligibleVersions.length === 0 ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
              No prior version snapshots are available for rollback yet.
            </div>
          ) : (
            <div>
              <p className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Select rollback target</p>
              <div className="space-y-3">
                {eligibleVersions
                  .slice()
                  .sort((left, right) => right.version - left.version)
                  .map((version: PipelineVersionSummary) => (
                    <label
                      key={version.version}
                      aria-label={`v${version.version}`}
                      className={[
                        'flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-3',
                        selectedVersion === version.version
                          ? 'border-indigo-300 bg-indigo-50 dark:border-indigo-700 dark:bg-indigo-950/40'
                          : 'border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950',
                      ].join(' ')}
                    >
                      <Radio
                        name="rollback-version"
                        checked={selectedVersion === version.version}
                        onChange={() => setSelectedVersion(version.version)}
                        className="mt-1"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-sm font-semibold text-gray-900 dark:text-white">v{version.version}</span>
                          {version.versionLabel ? (
                            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                              {version.versionLabel}
                            </span>
                          ) : null}
                          <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[11px] font-medium text-blue-700 dark:bg-blue-900 dark:text-blue-300">
                            {version.versionStatus}
                          </span>
                        </div>
                        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                          Updated {formatTimestamp(version.updatedAt)}{version.updatedBy ? ` by ${version.updatedBy}` : ''}
                        </p>
                      </div>
                    </label>
                  ))}
              </div>
            </div>
          )}

          {target ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
              Rolling back will restore version <span className="font-semibold">v{target.version}</span> and move the pipeline back into <span className="font-semibold">DRAFT</span> for review before republish.
            </div>
          ) : null}

          <label className="block">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-200">Reason</span>
            <TextArea
              aria-label="Reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              className="mt-2 w-full"
              placeholder="Describe why this rollback is needed and what changed."
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-200">
              Type <code className="rounded bg-gray-100 px-1 py-0.5 dark:bg-gray-800">ROLLBACK</code> to confirm
            </span>
            <Input
              aria-label="Type ROLLBACK to confirm"
              value={confirmText}
              onChange={(event) => setConfirmText(event.target.value)}
              className="mt-2 w-full"
            />
          </label>

          {rollbackMutation.isError ? (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
              {rollbackMutation.error instanceof Error ? rollbackMutation.error.message : 'Rollback failed.'}
            </div>
          ) : null}
        </div>

        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-4 dark:border-gray-800 dark:bg-gray-950">
          <Button onClick={onClose} variant="secondary" type="button">
            Cancel
          </Button>
          <Button
            onClick={() => rollbackMutation.mutate()}
            variant="primary"
            type="button"
            disabled={!canConfirm || rollbackMutation.isPending}
          >
            {rollbackMutation.isPending ? 'Rolling back…' : 'Confirm rollback'}
          </Button>
        </div>
      </div>
    </div>
  );
}
