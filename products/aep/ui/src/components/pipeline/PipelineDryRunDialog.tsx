/**
 * F-014: Pipeline dry-run dialog.
 *
 * Presents the pre-flight report from POST /api/v1/pipelines/:id/dry-run
 * to the operator before they are allowed to publish. The operator must
 * PipelineDryRunDialog — dialog for pipeline dry run simulation.
 *
 * @doc.type component
 * @doc.purpose Pipeline dry run simulation dialog
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React, { useState, useCallback } from 'react';
import { useMutation } from '@tanstack/react-query';
import { dryRunPipeline } from '@/api/pipeline.api';
import type { PipelineDryRunReport } from '@/api/pipeline.api';
interface PipelineDryRunDialogProps {
  pipelineId: string;
  pipelineName: string;
  tenantId: string;
  open: boolean;
  onClose: () => void;
  onPublish?: (pipelineId: string) => void;
}
function SectionLabel({ children }: { children: React.ReactNode }): React.ReactElement {
  return (
    <h4 className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-1.5 mt-4">
      {children}
    </h4>
  );
}
function ChipList({ items, variant }: { items: string[]; variant: 'neutral' | 'warning' | 'error' }): React.ReactElement {
  const cls = {
    neutral: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
    warning: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200',
    error: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-200',
  }[variant];
  return (
    <ul className="flex flex-wrap gap-1.5">
      {items.map((item) => (
        <li key={item} className={['rounded-full px-2 py-0.5 text-[11px] font-medium', cls].join(' ')}>
          {item}
        </li>
      ))}
    </ul>
  );
}
export function PipelineDryRunDialog({
  pipelineId,
  pipelineName,
  tenantId,
  open,
  onClose,
  onPublish,
}: PipelineDryRunDialogProps): React.ReactElement | null {
  const [acknowledged, setAcknowledged] = React.useState(false);
  const { mutate, data: report, isPending, error, reset } = useMutation<
    PipelineDryRunReport,
    Error
  >({
    mutationFn: () => dryRunPipeline(pipelineId, tenantId),
  });
  const handleRun = useCallback(() => {
    setAcknowledged(false);
    reset();
    mutate();
  }, [mutate, reset]);
  const handlePublish = useCallback(() => {
    onPublish?.(pipelineId);
    onClose();
  }, [onPublish, pipelineId, onClose]);
  if (!open) return null;
  const hasErrors = (report?.validationErrors.length ?? 0) > 0;
  const hasWarnings = (report?.warnings.length ?? 0) > 0;
  const canPublish = report?.passed && acknowledged;
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dry-run-title"
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/40 dark:bg-black/60" onClick={onClose} aria-hidden="true" />
      <div className="relative z-10 bg-white dark:bg-gray-900 rounded-xl shadow-2xl border border-gray-200 dark:border-gray-700 w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col mx-4">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h3 id="dry-run-title" className="text-base font-semibold text-gray-900 dark:text-white">
              Pre-flight dry-run
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              Pipeline: <span className="font-mono text-xs text-indigo-600 dark:text-indigo-400">{pipelineName}</span>
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
            aria-label="Close"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        {/* Body */}
        <div className="overflow-y-auto flex-1 px-6 py-4">
          {!report && !isPending && !error && (
            <div className="text-center py-10 text-gray-400">
              <p className="text-sm mb-4">Run a dry-run to validate this pipeline before publishing.</p>
              <p className="text-xs text-gray-300 dark:text-gray-600">No state will be changed.</p>
            </div>
          )}
          {isPending && (
            <div className="flex flex-col items-center justify-center py-12 text-gray-400">
              <svg className="animate-spin h-8 w-8 mb-3 text-indigo-500" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              <span className="text-sm">Running pre-flight checks…</span>
            </div>
          )}
          {error && (
            <div className="rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-4 text-sm text-red-700 dark:text-red-300">
              <strong>Dry-run failed:</strong> {error.message}
            </div>
          )}
          {report && (
            <div>
              {/* Result banner */}
              <div className={['rounded-lg px-4 py-3 flex items-center gap-3 text-sm font-medium', report.passed ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800' : 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-300 border border-red-200 dark:border-red-800'].join(' ')}>
                <span className="text-xl">{report.passed ? '✓' : '✗'}</span>
                {report.passed ? 'Pre-flight passed — pipeline is ready to publish' : 'Pre-flight failed — resolve errors before publishing'}
              </div>
              {/* Validation errors */}
              {hasErrors && (
                <>
                  <SectionLabel>Validation errors</SectionLabel>
                  <ul className="space-y-1">
                    {report.validationErrors.map((err, i) => (
                      <li key={i} className="text-xs text-red-700 dark:text-red-300 bg-red-50 dark:bg-red-900/20 rounded px-3 py-1.5 border border-red-100 dark:border-red-800">
                        {err}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              {/* Warnings */}
              {hasWarnings && (
                <>
                  <SectionLabel>Warnings</SectionLabel>
                  <ChipList items={report.warnings} variant="warning" />
                </>
              )}
              {/* Agent set */}
              {report.agentSet.length > 0 && (
                <>
                  <SectionLabel>Agent set</SectionLabel>
                  <ChipList items={report.agentSet} variant="neutral" />
                </>
              )}
              {/* Policy set */}
              <SectionLabel>Policy set</SectionLabel>
              <ChipList items={report.policySet} variant="neutral" />
              {/* Compliance bundle */}
              <SectionLabel>Compliance</SectionLabel>
              <div className="rounded-lg bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 divide-y divide-gray-200 dark:divide-gray-700 text-xs font-mono">
                {Object.entries(report.complianceBundle).map(([k, v]) => (
                  <div key={k} className="flex items-center justify-between px-3 py-1.5">
                    <span className="text-gray-500 dark:text-gray-400">{k}</span>
                    <span className={['font-semibold', v === true ? 'text-emerald-600 dark:text-emerald-400' : v === false ? 'text-red-600 dark:text-red-400' : 'text-gray-700 dark:text-gray-300'].join(' ')}>
                      {String(v)}
                    </span>
                  </div>
                ))}
              </div>
              {/* Acknowledgement */}
              {report.passed && report.acknowledgementRequired && (
                <label className="mt-5 flex items-start gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={acknowledged}
                    onChange={(e) => setAcknowledged(e.target.checked)}
                    className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                  />
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    I have reviewed the pre-flight report and acknowledge all warnings before publishing.
                  </span>
                </label>
              )}
            </div>
          )}
        </div>
        {/* Footer */}
        <div className="flex items-center justify-between gap-3 px-6 py-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          >
            Cancel
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleRun}
              disabled={isPending}
              className="px-4 py-2 text-sm font-medium bg-white dark:bg-gray-900 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg transition-colors disabled:opacity-50"
            >
              {report ? 'Re-run' : 'Run dry-run'}
            </button>
            {onPublish && (
              <button
                type="button"
                onClick={handlePublish}
                disabled={!canPublish}
                className="px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Publish
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
