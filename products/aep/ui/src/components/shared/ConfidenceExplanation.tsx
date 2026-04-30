/**
 * ConfidenceExplanation — displays AI decision confidence with explanation.
 *
 * @doc.type component
 * @doc.purpose Show AI confidence scores and explanations
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React from 'react';
import { ChevronDown, ChevronUp, Shield } from 'lucide-react';

export type ConfidenceTier = 'high' | 'medium' | 'low';

interface ConfidenceExplanationProps {
  tier: ConfidenceTier;
  score?: number;
  reasoning?: string;
  evidenceUrl?: string;
  auditEntryId?: string;
  allowOverride?: boolean;
  onOverride?: () => void;
  className?: string;
}

const TIER_CONFIG: Record<ConfidenceTier, { label: string; bg: string; text: string; border: string }> = {
  high: { label: 'High confidence', bg: 'bg-green-50 dark:bg-green-950/30', text: 'text-green-700 dark:text-green-300', border: 'border-green-200 dark:border-green-900' },
  medium: { label: 'Medium confidence', bg: 'bg-amber-50 dark:bg-amber-950/30', text: 'text-amber-700 dark:text-amber-300', border: 'border-amber-200 dark:border-amber-900' },
  low: { label: 'Low confidence — review recommended', bg: 'bg-red-50 dark:bg-red-950/30', text: 'text-red-700 dark:text-red-300', border: 'border-red-200 dark:border-red-900' },
};

export function ConfidenceExplanation({
  tier,
  score,
  reasoning,
  evidenceUrl,
  auditEntryId,
  allowOverride,
  onOverride,
  className = '',
}: ConfidenceExplanationProps): React.ReactElement {
  const [expanded, setExpanded] = useState(false);
  const config = TIER_CONFIG[tier];

  return (
    <div
      className={[
        'rounded-lg border p-3',
        config.bg,
        config.border,
        className,
      ].join(' ')}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Shield className="h-4 w-4 flex-shrink-0" />
          <span className={['text-xs font-semibold', config.text].join(' ')}>
            {config.label}
            {score !== undefined && ` (${Math.round(score * 100)}%)`}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {allowOverride && (
            <button
              type="button"
              onClick={onOverride}
              className="text-[11px] font-medium text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 underline"
            >
              Override
            </button>
          )}
          {reasoning && (
            <button
              type="button"
              onClick={() => setExpanded((e) => !e)}
              className="text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
              aria-expanded={expanded}
              aria-label={expanded ? 'Hide reasoning' : 'Show reasoning'}
            >
              {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            </button>
          )}
        </div>
      </div>

      {expanded && reasoning && (
        <div className="mt-2 space-y-2 text-xs text-gray-600 dark:text-gray-400">
          <p className="leading-relaxed">{reasoning}</p>
          <div className="flex gap-3">
            {evidenceUrl && (
              <a
                href={evidenceUrl}
                target="_blank"
                rel="noreferrer"
                className="text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                View evidence
              </a>
            )}
            {auditEntryId && (
              <a
                href={`/govern?tab=audit&entry=${auditEntryId}`}
                className="text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                View audit
              </a>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
