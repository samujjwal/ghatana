/**
 * Generate Package Panel
 *
 * Phase-native panel for the Generate phase. Shows generated package contents,
 * output files, tests, residual islands, diff view, and merge gate.
 *
 * @doc.type component
 * @doc.purpose Codegen package surface for the Generate phase
 * @doc.layer product
 * @doc.pattern Phase Panel
 */

import React, { useState, useCallback } from 'react';
import { Button, Card, CardContent } from '@ghatana/design-system';

export type GenerationStatus =
  | 'idle'
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'merge-pending';

export interface OutputFile {
  readonly path: string;
  readonly kind: 'component' | 'test' | 'style' | 'type' | 'config' | 'other';
  readonly linesAdded: number;
  readonly linesRemoved: number;
  readonly isNew: boolean;
  readonly hasConflict: boolean;
  readonly content?: string;
}

export interface ResidualIsland {
  readonly id: string;
  readonly nodeId: string;
  readonly reason: string;
  readonly reviewState: 'pending' | 'accepted' | 'rejected';
}

export interface GeneratePackagePanelProps {
  /** Status of the code generation run */
  readonly status: GenerationStatus;
  /** ISO timestamp of the last generation run */
  readonly generatedAt?: string;
  /** Files produced or affected by generation */
  readonly outputFiles: readonly OutputFile[];
  /** Residual islands that could not be fully mapped */
  readonly residuals: readonly ResidualIsland[];
  /** Whether all residuals are reviewed (required before merge) */
  readonly residualsReviewed: boolean;
  /** Called to trigger a new generation run */
  readonly onGenerate: () => void;
  /** Called to merge the generated package */
  readonly onMerge: () => void;
  /** Called when a residual is accepted */
  readonly onAcceptResidual: (residualId: string) => void;
  /** Called when a residual is rejected */
  readonly onRejectResidual: (residualId: string) => void;
  /** Whether generation can be triggered */
  readonly canGenerate: boolean;
  /** Reason generation cannot be triggered */
  readonly cannotGenerateReason?: string;
  /** Custom className */
  readonly className?: string;
}

const FILE_KIND_ICON: Record<OutputFile['kind'], string> = {
  component: '⚛',
  test: '🧪',
  style: '🎨',
  type: '📐',
  config: '⚙',
  other: '📄',
};

const STATUS_BADGE: Record<GenerationStatus, { label: string; className: string }> = {
  idle: { label: 'Not generated', className: 'bg-surface-muted border-border text-fg-muted' },
  running: { label: 'Generating…', className: 'bg-info-bg border-info-border text-info-color' },
  succeeded: { label: 'Generated', className: 'bg-success-bg border-success-border text-success-color' },
  failed: { label: 'Failed', className: 'bg-destructive-bg border-destructive-border text-destructive' },
  'merge-pending': { label: 'Ready to merge', className: 'bg-warning-bg border-warning-border text-warning-color' },
};

/**
 * Generate Package Panel
 *
 * Provides visibility into generated output with:
 * - Status of the current generation run
 * - Output file list with diff stats and conflict indicators
 * - Residual island review (required before merge)
 * - Merge gate (disabled until residuals are cleared)
 */
export const GeneratePackagePanel: React.FC<GeneratePackagePanelProps> = ({
  status,
  generatedAt,
  outputFiles,
  residuals,
  residualsReviewed,
  onGenerate,
  onMerge,
  onAcceptResidual,
  onRejectResidual,
  canGenerate,
  cannotGenerateReason,
  className = '',
}) => {
  const [expandedFile, setExpandedFile] = useState<string | null>(null);

  const badge = STATUS_BADGE[status];

  const pendingResiduals = residuals.filter((r) => r.reviewState === 'pending');
  const conflictFiles = outputFiles.filter((f) => f.hasConflict);
  const canMerge = status === 'succeeded' || status === 'merge-pending';
  const mergeBlocked = !residualsReviewed || conflictFiles.length > 0;

  const toggleFile = useCallback((path: string) => {
    setExpandedFile((prev) => (prev === path ? null : path));
  }, []);

  return (
    <section
      className={`generate-package-panel space-y-6 ${className}`}
      aria-label="Generation package"
      data-testid="generate-package-panel"
    >
      {/* Status Header */}
      <Card variant="outlined">
        <CardContent className="p-5">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <h3 className="text-base font-semibold text-fg">Code generation</h3>
              {generatedAt && (
                <p className="text-xs text-fg-muted mt-1">
                  Last run:{' '}
                  <time dateTime={generatedAt}>
                    {new Date(generatedAt).toLocaleString()}
                  </time>
                </p>
              )}
            </div>
            <div className="flex items-center gap-3">
              <span
                className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${badge.className}`}
              >
                {badge.label}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={onGenerate}
                disabled={!canGenerate || status === 'running'}
                aria-label="Run code generation"
                title={cannotGenerateReason}
              >
                {status === 'running' ? 'Generating…' : 'Generate'}
              </Button>
            </div>
          </div>
          {!canGenerate && cannotGenerateReason && (
            <p className="mt-3 text-xs text-fg-muted border-t border-border pt-3">
              {cannotGenerateReason}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Output Files */}
      {outputFiles.length > 0 && (
        <section aria-label="Output files">
          <div className="flex items-center justify-between mb-3">
            <h4 className="text-sm font-medium text-fg">
              Output files ({outputFiles.length})
            </h4>
            <span className="text-xs text-fg-muted">
              {outputFiles.filter((f) => f.isNew).length} new ·{' '}
              {conflictFiles.length > 0 && (
                <span className="text-destructive font-medium">
                  {conflictFiles.length} conflict{conflictFiles.length !== 1 ? 's' : ''}
                </span>
              )}
            </span>
          </div>
          <div className="divide-y divide-border rounded-lg border border-border overflow-hidden">
            {outputFiles.map((file) => (
              <div key={file.path}>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className={`w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-surface-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                    file.hasConflict ? 'bg-destructive-bg/30' : 'bg-surface'
                  }`}
                  onClick={() => file.content != null && toggleFile(file.path)}
                  aria-expanded={expandedFile === file.path}
                  aria-label={`${file.path} — ${file.isNew ? 'new file' : 'modified'}`}
                >
                  <span className="text-sm" aria-hidden="true">
                    {FILE_KIND_ICON[file.kind]}
                  </span>
                  <span className="flex-1 font-mono text-xs text-fg truncate">
                    {file.path}
                  </span>
                  <span className="text-xs text-success-color font-mono whitespace-nowrap">
                    +{file.linesAdded}
                  </span>
                  <span className="text-xs text-destructive font-mono whitespace-nowrap">
                    -{file.linesRemoved}
                  </span>
                  {file.isNew && (
                    <span className="text-xs bg-info-bg border border-info-border text-info-color rounded px-1.5 py-0.5">
                      new
                    </span>
                  )}
                  {file.hasConflict && (
                    <span className="text-xs bg-destructive-bg border border-destructive-border text-destructive rounded px-1.5 py-0.5">
                      conflict
                    </span>
                  )}
                </Button>
                {expandedFile === file.path && file.content != null && (
                  <div className="bg-surface-muted border-t border-border px-4 py-3">
                    <pre className="text-xs font-mono text-fg whitespace-pre-wrap max-h-64 overflow-y-auto">
                      {file.content}
                    </pre>
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Residual Islands */}
      {residuals.length > 0 && (
        <section aria-label="Residual islands requiring review">
          <h4 className="text-sm font-medium text-fg mb-3">
            Residual islands ({pendingResiduals.length} pending review)
          </h4>
          <div className="space-y-2">
            {residuals.map((residual) => (
              <Card key={residual.id} variant="outlined">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-mono text-fg truncate">{residual.nodeId}</p>
                      <p className="text-xs text-fg-muted mt-0.5">{residual.reason}</p>
                    </div>
                    {residual.reviewState === 'pending' ? (
                      <div className="flex gap-2 flex-shrink-0">
                        <Button
                          variant="solid"
                          size="sm"
                          onClick={() => onAcceptResidual(residual.id)}
                          aria-label={`Accept residual island ${residual.nodeId}`}
                        >
                          Accept
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onRejectResidual(residual.id)}
                          aria-label={`Reject residual island ${residual.nodeId}`}
                        >
                          Reject
                        </Button>
                      </div>
                    ) : (
                      <span
                        className={`text-xs font-medium ${
                          residual.reviewState === 'accepted'
                            ? 'text-success-color'
                            : 'text-destructive'
                        }`}
                      >
                        {residual.reviewState === 'accepted' ? 'Accepted' : 'Rejected'}
                      </span>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* Merge Gate */}
      {canMerge && (
        <section aria-label="Merge generated code">
          {mergeBlocked && (
            <div className="mb-3 rounded-lg bg-warning-bg border border-warning-border p-3">
              <p className="text-sm text-warning-color">
                {!residualsReviewed
                  ? `Review all ${pendingResiduals.length} residual island${pendingResiduals.length !== 1 ? 's' : ''} before merging.`
                  : `Resolve ${conflictFiles.length} conflict${conflictFiles.length !== 1 ? 's' : ''} before merging.`}
              </p>
            </div>
          )}
          <Button
            variant="solid"
            onClick={onMerge}
            disabled={mergeBlocked}
            aria-label="Merge generated code into project"
            className="w-full sm:w-auto"
          >
            Merge into project
          </Button>
        </section>
      )}
    </section>
  );
};

export default GeneratePackagePanel;
