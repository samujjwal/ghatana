/**
 * AIAssistSuggestion Component
 *
 * Advisory card displaying a model-generated or rule-generated suggestion with
 * a confidence indicator and optional evidence list. Provides Apply and Dismiss
 * actions so the caller can record an override decision.
 *
 * Intentionally NOT branded as "AI" in the visible label — surfaces as a
 * "Suggested action" to avoid over-reliance signalling (per DC-UX-040). Callers
 * that want a different label can pass `headingLabel`.
 *
 * @doc.type component
 * @doc.purpose Advisory suggestion card with confidence indicator, evidence list, and override action
 * @doc.layer shared
 * @doc.pattern Advisory Presentation
 *
 * @example
 * ```tsx
 * <AIAssistSuggestion
 *   suggestion="Consider partitioning this table by event_date to reduce scan cost."
 *   confidence={0.82}
 *   evidence={['Average query scan: 4.2 GB', 'Partition key selectivity: high']}
 *   onApply={() => applySuggestion(id)}
 *   onDismiss={() => dismissSuggestion(id)}
 *   isApplying={applySuggestionMutation.isPending}
 * />
 * ```
 */

import React, { useCallback, useId } from 'react';
import { Lightbulb, ChevronDown, ChevronUp } from 'lucide-react';
import { Button } from './Button';
import { cn } from '../../lib/theme';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface AIAssistSuggestionProps {
  /** The advisory text — the main suggestion body. */
  suggestion: string;
  /**
   * Confidence score in the range [0, 1]. When provided, rendered as a
   * labelled percentage bar below the suggestion text.
   */
  confidence?: number;
  /**
   * Supporting evidence items. Renders as a collapsible bullet list. Omit
   * when no evidence is available.
   */
  evidence?: string[];
  /**
   * Overrides the heading label. Defaults to "Suggested action" — intentionally
   * avoids the word "AI" in the visible surface per DC-UX-040.
   */
  headingLabel?: string;
  /**
   * When `true`, the Apply action button is rendered.
   * @default true
   */
  canApply?: boolean;
  /** Label for the apply action button. @default "Apply" */
  applyLabel?: string;
  /** Called when the user clicks the apply/accept button. */
  onApply?: () => void;
  /** Called when the user clicks the dismiss button. */
  onDismiss?: () => void;
  /** When `true`, the apply button shows a loading spinner. @default false */
  isApplying?: boolean;
  /** Optional className for the card container. */
  className?: string;
  /** Optional test id applied to the card. */
  'data-testid'?: string;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Maps a confidence value (0–1) to a human-readable level label and a
 * Tailwind colour class for the bar fill.
 */
function confidenceAppearance(confidence: number): {
  label: string;
  barClass: string;
} {
  if (confidence >= 0.8) {
    return { label: 'High', barClass: 'bg-emerald-500' };
  }
  if (confidence >= 0.5) {
    return { label: 'Medium', barClass: 'bg-amber-400' };
  }
  return { label: 'Low', barClass: 'bg-rose-400' };
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * AIAssistSuggestion
 *
 * Advisory card. The caller is responsible for triggering apply/dismiss
 * mutations; this component only surfaces the action buttons.
 */
export const AIAssistSuggestion = React.memo(function AIAssistSuggestion({
  suggestion,
  confidence,
  evidence,
  headingLabel = 'Suggested action',
  canApply = true,
  applyLabel = 'Apply',
  onApply,
  onDismiss,
  isApplying = false,
  className,
  'data-testid': testId,
}: AIAssistSuggestionProps): React.ReactElement {
  const evidenceToggleId = useId();
  const [evidenceOpen, setEvidenceOpen] = React.useState(false);

  const handleApply = useCallback(() => {
    onApply?.();
  }, [onApply]);

  const handleDismiss = useCallback(() => {
    onDismiss?.();
  }, [onDismiss]);

  const hasEvidence = Array.isArray(evidence) && evidence.length > 0;

  return (
    <div
      role="note"
      aria-label={headingLabel}
      data-testid={testId}
      className={cn(
        'rounded-lg border border-blue-200 bg-blue-50 p-4 dark:border-blue-800 dark:bg-blue-950/40',
        className
      )}
    >
      {/* Heading */}
      <div className="mb-2 flex items-center gap-2">
        <Lightbulb
          className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400"
          aria-hidden="true"
        />
        <span className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-400">
          {headingLabel}
        </span>
      </div>

      {/* Suggestion body */}
      <p className="mb-3 text-sm text-neutral-800 dark:text-neutral-200">{suggestion}</p>

      {/* Confidence bar */}
      {confidence !== undefined && (
        <div className="mb-3">
          <div className="mb-1 flex items-center justify-between">
            <span className="text-xs text-neutral-500 dark:text-neutral-400">Confidence</span>
            <span
              className="text-xs font-medium text-neutral-700 dark:text-neutral-300"
              data-testid={testId ? `${testId}-confidence` : undefined}
            >
              {confidenceAppearance(confidence).label} ({Math.round(confidence * 100)}%)
            </span>
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-neutral-200 dark:bg-neutral-700">
            <div
              className={cn('h-full rounded-full', confidenceAppearance(confidence).barClass)}
              style={{ width: `${Math.round(confidence * 100)}%` }}
              aria-hidden="true"
            />
          </div>
        </div>
      )}

      {/* Evidence toggle */}
      {hasEvidence && (
        <div className="mb-3">
          <button
            type="button"
            id={evidenceToggleId}
            onClick={() => { setEvidenceOpen((v) => !v); }}
            aria-expanded={evidenceOpen}
            className="flex items-center gap-1 text-xs text-blue-700 underline-offset-2 hover:underline dark:text-blue-400"
          >
            {evidenceOpen ? (
              <ChevronUp className="h-3 w-3" aria-hidden="true" />
            ) : (
              <ChevronDown className="h-3 w-3" aria-hidden="true" />
            )}
            {evidenceOpen ? 'Hide evidence' : 'Show evidence'}
          </button>
          {evidenceOpen && (
            <ul
              aria-labelledby={evidenceToggleId}
              className="mt-2 space-y-1 pl-4"
            >
              {evidence!.map((item, i) => (
                <li key={i} className="text-xs text-neutral-700 dark:text-neutral-300 list-disc">
                  {item}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* Actions */}
      {(canApply || onDismiss) && (
        <div className="flex items-center gap-2">
          {canApply && onApply && (
            <Button
              variant="primary"
              size="sm"
              onClick={handleApply}
              isLoading={isApplying}
              disabled={isApplying}
              data-testid={testId ? `${testId}-apply` : undefined}
            >
              {applyLabel}
            </Button>
          )}
          {onDismiss && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleDismiss}
              disabled={isApplying}
              data-testid={testId ? `${testId}-dismiss` : undefined}
            >
              Dismiss
            </Button>
          )}
        </div>
      )}
    </div>
  );
});
