/**
 * ConfidenceBadge — displays a numeric confidence score with color coding.
 *
 * Color tiers:
 *  - Green  (≥ 0.9) — high confidence
 *  - Yellow (≥ 0.7) — acceptable confidence
 *  - Red    (< 0.7) — low confidence, may require human review
 *
 * @doc.type component
 * @doc.purpose Visually communicate confidence scores from learned policies
 * @doc.layer frontend
 */
import React from 'react';

interface ConfidenceBadgeProps {
  /** Confidence score in [0, 1]. */
  value: number;
  /** Show the numeric value in the badge (default: true). */
  showValue?: boolean;
  className?: string;
}

function colorClass(value: number): string {
  if (value >= 0.9) return 'text-green-700 bg-green-50 border-green-200 dark:text-green-300 dark:bg-green-950 dark:border-green-800';
  if (value >= 0.7) return 'text-yellow-700 bg-yellow-50 border-yellow-200 dark:text-yellow-300 dark:bg-yellow-950 dark:border-yellow-800';
  return 'text-red-700 bg-red-50 border-red-200 dark:text-red-300 dark:bg-red-950 dark:border-red-800';
}

export function ConfidenceBadge({ value, showValue = true, className = '' }: ConfidenceBadgeProps) {
  const pct = Math.round(value * 100);
  const tier = value >= 0.9 ? 'High' : value >= 0.7 ? 'Medium' : 'Low';

  return (
    <span
      className={[
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border',
        colorClass(value),
        className,
      ].join(' ')}
      title={`Confidence: ${pct}% (${tier})`}
    >
      {showValue && <span>{pct}%</span>}
      <span className="hidden sm:inline">{tier}</span>
    </span>
  );
}
