/**
 * RefactorSuggestionPanel — Simulate-then-Apply lifecycle (F-Y017 / AI-Y4)
 *
 * Renders refactoring suggestions for a design with full lifecycle:
 * 1. List suggestions (confidence, rationale, affected files)
 * 2. Simulate — preview diff before committing
 * 3. Apply — apply the suggestion after reviewing the diff
 * 4. Undo  — revert if needed
 *
 * @doc.type component
 * @doc.purpose Refactor suggestion management with simulate-then-apply lifecycle
 * @doc.layer product
 * @doc.pattern AI Assist Panel
 */

import {
  Check,
  ChevronDown,
  ChevronRight,
  Eye,
  FileDiff,
  Loader2,
  RotateCcw,
  TriangleAlert,
  X,
} from 'lucide-react';
import React, { useCallback, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { cn } from '@/lib/utils';
import { ConfidenceBadge } from './ConfidenceBadge';
import {
  applyRefactorSuggestion,
  listRefactorSuggestions,
  simulateRefactorSuggestion,
  undoRefactorSuggestion,
  type RefactorSuggestion,
  type SimulateResult,
} from '@/services/ai/refactoringSuggestionsApi';

// ── Props ─────────────────────────────────────────────────────────────────────

export interface RefactorSuggestionPanelProps {
  designId: string;
  open: boolean;
  onClose: () => void;
}

// ── Sub-components ────────────────────────────────────────────────────────────

interface DiffViewerProps {
  result: SimulateResult;
}

function DiffViewer({ result }: DiffViewerProps) {
  const riskColor: Record<SimulateResult['estimatedRiskLevel'], string> = {
    LOW: 'text-success-color',
    MEDIUM: 'text-warning-color',
    HIGH: 'text-error-color',
  };

  return (
    <div className="rounded-md border border-divider bg-bg-default" data-testid="diff-viewer">
      {result.warnings.length > 0 && (
        <div className="flex items-start gap-2 border-b border-divider bg-amber-50 px-3 py-2 text-xs text-amber-800">
          <TriangleAlert className="mt-0.5 h-3 w-3 shrink-0" />
          <ul className="space-y-0.5">
            {result.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      )}
      <div className="flex items-center gap-2 border-b border-divider px-3 py-2 text-xs">
        <span className={cn('font-semibold', riskColor[result.estimatedRiskLevel])}>
          {result.estimatedRiskLevel} risk
        </span>
        {!result.canApply && (
          <span className="rounded bg-error-color/10 px-1.5 py-0.5 text-error-color">
            Cannot apply
          </span>
        )}
      </div>
      <div className="max-h-60 overflow-auto p-3">
        {result.diff.map((file) => (
          <div key={file.path} className="mb-3">
            <p className="mb-1 font-mono text-xs font-semibold text-text-primary">
              {file.path}
              <span className="ml-2 text-success-color">+{file.linesAdded}</span>
              <span className="ml-1 text-error-color">−{file.linesRemoved}</span>
            </p>
            <pre className="whitespace-pre-wrap break-all rounded bg-bg-paper p-2 font-mono text-xs text-text-secondary">
              {file.diff}
            </pre>
          </div>
        ))}
      </div>
    </div>
  );
}

interface SuggestionRowProps {
  suggestion: RefactorSuggestion;
  designId: string;
  onSimulateSuccess: (result: SimulateResult) => void;
}

function SuggestionRow({ suggestion, designId, onSimulateSuccess }: SuggestionRowProps) {
  const [expanded, setExpanded] = useState(false);
  const queryClient = useQueryClient();

  const simulate = useMutation({
    mutationFn: () => simulateRefactorSuggestion(designId, suggestion.id),
    onSuccess: (result) => {
      onSimulateSuccess(result);
    },
  });

  const apply = useMutation({
    mutationFn: () => applyRefactorSuggestion(designId, suggestion.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['refactoring-suggestions', designId],
      });
    },
  });

  const undo = useMutation({
    mutationFn: () => undoRefactorSuggestion(designId, suggestion.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['refactoring-suggestions', designId],
      });
    },
  });

  const isApplied = suggestion.status === 'APPLIED';
  const isUndone = suggestion.status === 'UNDONE';
  const isSimulated = suggestion.status === 'SIMULATED';

  return (
    <div
      className="rounded-md border border-divider bg-bg-paper"
      data-testid={`suggestion-row-${suggestion.id}`}
    >
      {/* Header */}
      <button
        type="button"
        className="flex w-full items-start gap-2 px-4 py-3 text-left"
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
        data-testid={`btn-expand-${suggestion.id}`}
      >
        {expanded ? (
          <ChevronDown className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
        ) : (
          <ChevronRight className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-sm font-medium text-text-primary">
              {suggestion.title}
            </span>
            <ConfidenceBadge confidence={suggestion.confidence} />
            {isApplied && (
              <span className="rounded-full bg-success-color/10 px-2 py-0.5 text-xs text-success-color">
                Applied
              </span>
            )}
            {isUndone && (
              <span className="rounded-full bg-grey-100 px-2 py-0.5 text-xs text-text-secondary">
                Undone
              </span>
            )}
          </div>
          <p className="mt-0.5 truncate text-xs text-text-secondary">{suggestion.rationale}</p>
        </div>
      </button>

      {/* Expanded body */}
      {expanded && (
        <div className="border-t border-divider px-4 py-3 space-y-3">
          <div>
            <p className="mb-1 text-xs font-semibold text-text-secondary">Affected files</p>
            <ul className="space-y-0.5">
              {suggestion.affectedFiles.map((f) => (
                <li key={f} className="flex items-center gap-1.5 text-xs text-text-primary">
                  <FileDiff className="h-3 w-3 text-text-secondary" />
                  <span className="font-mono">{f}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2">
            {/* Simulate — always available while not applied */}
            {!isApplied && !isUndone && (
              <button
                type="button"
                className="inline-flex items-center gap-1.5 rounded-md border border-divider bg-bg-default px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-grey-50 disabled:opacity-50"
                onClick={() => simulate.mutate()}
                disabled={simulate.isPending}
                data-testid={`btn-simulate-${suggestion.id}`}
              >
                {simulate.isPending ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Eye className="h-3 w-3" />
                )}
                Simulate
              </button>
            )}

            {/* Apply — only after simulate and when canApply */}
            {(isSimulated || (!isApplied && !isUndone && suggestion.simulatedDiff)) && !isApplied && (
              <button
                type="button"
                className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary/90 disabled:opacity-50"
                onClick={() => apply.mutate()}
                disabled={apply.isPending || simulate.isPending}
                data-testid={`btn-apply-${suggestion.id}`}
              >
                {apply.isPending ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Check className="h-3 w-3" />
                )}
                Apply
              </button>
            )}

            {/* Undo — only when applied */}
            {isApplied && (
              <button
                type="button"
                className="inline-flex items-center gap-1.5 rounded-md border border-divider bg-bg-default px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-grey-50 disabled:opacity-50"
                onClick={() => undo.mutate()}
                disabled={undo.isPending}
                data-testid={`btn-undo-${suggestion.id}`}
              >
                {undo.isPending ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <RotateCcw className="h-3 w-3" />
                )}
                Undo
              </button>
            )}
          </div>

          {simulate.isError && (
            <p className="text-xs text-error-color" role="alert">
              Simulation failed. Please try again.
            </p>
          )}
          {apply.isError && (
            <p className="text-xs text-error-color" role="alert">
              Apply failed. Please try again.
            </p>
          )}
          {undo.isError && (
            <p className="text-xs text-error-color" role="alert">
              Undo failed. Please try again.
            </p>
          )}
        </div>
      )}
    </div>
  );
}

// ── Panel ─────────────────────────────────────────────────────────────────────

export function RefactorSuggestionPanel({
  designId,
  open,
  onClose,
}: RefactorSuggestionPanelProps) {
  const [activeSimResult, setActiveSimResult] = useState<SimulateResult | null>(null);

  const handleSimulateSuccess = useCallback((result: SimulateResult) => {
    setActiveSimResult(result);
  }, []);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['refactoring-suggestions', designId],
    queryFn: () => listRefactorSuggestions(designId),
    enabled: open && Boolean(designId),
    staleTime: 30_000,
  });

  if (!open) return null;

  const suggestions = data?.suggestions ?? [];
  const pendingCount = suggestions.filter(
    (s) => s.status === 'PENDING' || s.status === 'SIMULATED'
  ).length;

  return (
    <aside
      className="fixed right-0 top-14 z-40 flex h-[calc(100vh-3.5rem)] w-full max-w-lg flex-col border-l border-divider bg-bg-paper shadow-2xl"
      role="complementary"
      aria-label="Refactoring suggestions panel"
      data-testid="refactor-suggestion-panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-divider px-5 py-4">
        <div className="flex items-center gap-3">
          <div className="rounded-full bg-primary-100 p-2 text-primary-700">
            <FileDiff className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-text-primary">Refactoring suggestions</h2>
            <p className="text-xs text-text-secondary">
              {pendingCount} pending · simulate before applying
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md p-1 text-text-secondary hover:bg-grey-100"
          aria-label="Close refactoring suggestions panel"
          data-testid="btn-close-refactor-panel"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Simulate diff preview (sticky) */}
      {activeSimResult && (
        <div className="border-b border-divider px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-semibold text-text-primary">Simulated diff preview</p>
            <button
              type="button"
              className="text-xs text-text-secondary hover:text-text-primary"
              onClick={() => setActiveSimResult(null)}
              aria-label="Dismiss diff preview"
            >
              Dismiss
            </button>
          </div>
          <DiffViewer result={activeSimResult} />
        </div>
      )}

      {/* Suggestions list */}
      <div className="flex-1 overflow-y-auto px-4 py-4">
        {isLoading && (
          <div
            className="flex items-center justify-center py-12 text-text-secondary"
            data-testid="loading-spinner"
          >
            <Loader2 className="h-6 w-6 animate-spin" />
          </div>
        )}

        {isError && (
          <p
            className="text-center text-sm text-error-color"
            role="alert"
            data-testid="error-message"
          >
            Failed to load refactoring suggestions.
          </p>
        )}

        {!isLoading && !isError && suggestions.length === 0 && (
          <div
            className="flex flex-col items-center justify-center gap-3 py-16 text-center"
            data-testid="empty-state"
          >
            <Check className="h-10 w-10 text-success-color" />
            <div>
              <p className="text-sm font-semibold text-text-primary">
                No refactoring suggestions
              </p>
              <p className="mt-1 text-xs text-text-secondary">
                The AI refactorer found nothing to improve right now.
              </p>
            </div>
          </div>
        )}

        {!isLoading && !isError && suggestions.length > 0 && (
          <ul className="space-y-3" role="list">
            {suggestions.map((s) => (
              <li key={s.id}>
                <SuggestionRow
                  suggestion={s}
                  designId={designId}
                  onSimulateSuccess={handleSimulateSuccess}
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  );
}
