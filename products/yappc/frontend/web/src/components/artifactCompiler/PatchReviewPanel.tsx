/**
 * @fileoverview Patch Review Panel for Artifact Compiler
 *
 * P2-4: Patch review panel with:
 * - Show unified diff
 * - Validation results
 * - Residual overlaps
 * - Approve/reject functionality
 *
 * @doc.type component
 * @doc.purpose UI component for reviewing and approving/rejecting patches
 * @doc.layer product
 * @doc.pattern Panel
 */

import { useState } from 'react';
import { X, Check, X as XIcon, AlertTriangle, Layers, FileDiff, ChevronDown, ChevronUp } from 'lucide-react';

import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';

// ============================================================================
// Types
// ============================================================================

export interface PatchDiff {
  filePath: string;
  oldContent: string;
  newContent: string;
  diff: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: Array<{
    code: string;
    message: string;
    severity: 'error' | 'warning';
    filePath?: string;
  }>;
}

export interface ResidualOverlap {
  residualId: string;
  changeId: string;
  filePath: string;
  reason: string;
}

export interface PatchReviewPanelProps {
  open: boolean;
  patches: PatchDiff[];
  validation: ValidationResult;
  residualOverlaps: ResidualOverlap[];
  onClose: () => void;
  onApprove?: () => void;
  onReject?: () => void;
}

// ============================================================================
// Component
// ============================================================================

export function PatchReviewPanel({
  open,
  patches,
  validation,
  residualOverlaps,
  onClose,
  onApprove,
  onReject,
}: PatchReviewPanelProps) {
  const [expandedPatchId, setExpandedPatchId] = useState<string | null>(null);

  if (!open) {
    return null;
  }

  const hasErrors = validation.errors.some(e => e.severity === 'error');
  const hasWarnings = validation.errors.some(e => e.severity === 'warning');
  const hasResidualOverlaps = residualOverlaps.length > 0;

  return (
    <aside
      className="fixed right-0 top-14 z-40 flex h-[calc(100vh-3.5rem)] w-full max-w-3xl flex-col border-l border-divider bg-bg-paper shadow-2xl"
      role="complementary"
      aria-label="Patch Review Panel"
      data-testid="patch-review-panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-divider px-5 py-4">
        <div className="flex items-center gap-3">
          <div className="rounded-full bg-primary-100 p-2 text-primary-700">
            <FileDiff className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-text-primary">Patch Review</h2>
            <p className="text-xs text-text-secondary">{patches.length} patches to review</p>
          </div>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onClose}
          className="rounded-md p-1 text-text-secondary hover:bg-grey-100"
          aria-label="Close panel"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-5 py-4 space-y-4">
        {/* Validation Results */}
        <div className={cn(
          'rounded-lg border p-4 space-y-3',
          hasErrors ? 'border-destructive-border bg-destructive-bg/10' :
          hasWarnings ? 'border-warning-border bg-warning-bg/10' :
          'border-success-border bg-success-bg/10'
        )}>
          <h3 className="text-sm font-semibold text-text-primary">Validation Results</h3>
          {validation.errors.length === 0 ? (
            <div className="flex items-center gap-2 text-sm text-success-color">
              <Check className="h-4 w-4" />
              <span>All patches validated successfully</span>
            </div>
          ) : (
            <div className="space-y-2">
              {validation.errors.map((error, index) => (
                <div
                  key={index}
                  className={cn(
                    'flex items-start gap-2 rounded p-2',
                    error.severity === 'error' ? 'bg-destructive-bg/20 text-destructive' :
                    'bg-warning-bg/20 text-warning-color'
                  )}
                >
                  <AlertTriangle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                  <div className="flex-1">
                    <p className="text-sm font-medium">{error.message}</p>
                    {error.code && (
                      <p className="text-xs opacity-80">Code: {error.code}</p>
                    )}
                    {error.filePath && (
                      <p className="text-xs opacity-80">File: {error.filePath}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Residual Overlaps */}
        {hasResidualOverlaps && (
          <div className="rounded-lg border border-warning-border bg-warning-bg/10 p-4 space-y-3">
            <div className="flex items-center gap-2">
              <Layers className="h-5 w-5 text-warning-color" />
              <h3 className="text-sm font-semibold text-text-primary">Residual Overlaps</h3>
              <span className="rounded-full bg-warning-color px-2 py-0.5 text-xs font-medium text-white">
                {residualOverlaps.length}
              </span>
            </div>
            <div className="space-y-2">
              {residualOverlaps.map((overlap) => (
                <div key={overlap.residualId} className="rounded bg-warning-bg/20 p-3 space-y-1">
                  <p className="text-sm font-medium text-text-primary">{overlap.filePath}</p>
                  <p className="text-xs text-text-secondary">{overlap.reason}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Patch List */}
        <div className="space-y-3">
          <h3 className="text-sm font-semibold text-text-primary">Patches</h3>
          {patches.map((patch) => (
            <PatchCard
              key={patch.filePath}
              patch={patch}
              isExpanded={expandedPatchId === patch.filePath}
              onToggle={() => setExpandedPatchId(
                expandedPatchId === patch.filePath ? null : patch.filePath
              )}
            />
          ))}
        </div>
      </div>

      {/* Footer */}
      <div className="border-t border-divider px-5 py-4">
        <div className="flex gap-3">
          <Button
            type="button"
            variant="outline"
            onClick={onReject}
            className="flex-1"
          >
            <XIcon className="mr-2 h-4 w-4" />
            Reject
          </Button>
          <Button
            type="button"
            onClick={onApprove}
            disabled={hasErrors}
            className="flex-1"
          >
            <Check className="mr-2 h-4 w-4" />
            Approve
          </Button>
        </div>
      </div>
    </aside>
  );
}

// ============================================================================
// Sub-components
// ============================================================================

interface PatchCardProps {
  patch: PatchDiff;
  isExpanded: boolean;
  onToggle: () => void;
}

function PatchCard({ patch, isExpanded, onToggle }: PatchCardProps) {
  return (
    <div className="rounded-lg border border-divider bg-bg-default overflow-hidden">
      <button
        type="button"
        onClick={onToggle}
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-bg-paper transition-colors"
      >
        <div className="flex items-center gap-3">
          <FileDiff className="h-4 w-4 text-text-secondary" />
          <span className="text-sm font-medium text-text-primary">{patch.filePath}</span>
        </div>
        {isExpanded ? (
          <ChevronUp className="h-4 w-4 text-text-secondary" />
        ) : (
          <ChevronDown className="h-4 w-4 text-text-secondary" />
        )}
      </button>
      {isExpanded && (
        <div className="border-t border-divider bg-bg-paper p-4">
          <div className="rounded-lg bg-divider p-4 font-mono text-xs">
            <pre className="whitespace-pre-wrap overflow-x-auto text-text-primary">
              {patch.diff}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}
