/**
 * Confidence Badge
 *
 * Unified confidence indicator for AI-generated content.
 * Displays a color-coded badge with confidence level and optional evidence link.
 *
 * @doc.type component
 * @doc.purpose Display AI confidence level with consistent styling
 * @doc.layer product
 * @doc.pattern Generic Component
 */

import React from 'react';

function clsx(...classes: (string | false | undefined | null)[]): string {
  return classes.filter(Boolean).join(' ');
}

export type ConfidenceLevel = 'high' | 'medium' | 'low' | 'uncertain';

export interface ConfidenceBadgeProps {
  /** Confidence score 0-100 */
  score: number;
  /** Optional label override (e.g., "87% confidence") */
  label?: string;
  /** Whether to show the confidence type */
  showType?: boolean;
  /** Confidence type description (e.g., "rule-based heuristic") */
  type?: string;
  /** Optional evidence URL or reference */
  evidenceRef?: string;
  /** Size variant */
  size?: 'sm' | 'md';
  /** Additional CSS classes */
  className?: string;
}

function getConfidenceLevel(score: number): ConfidenceLevel {
  if (score >= 80) return 'high';
  if (score >= 60) return 'medium';
  if (score >= 40) return 'low';
  return 'uncertain';
}

const LEVEL_STYLES: Record<ConfidenceLevel, { badge: string; dot: string }> = {
  high: {
    badge: 'bg-success-bg text-success-color border-success-border',
    dot: 'bg-success-bg',
  },
  medium: {
    badge: 'bg-warning-bg text-warning-color border-warning-border',
    dot: 'bg-warning-bg',
  },
  low: {
    badge: 'bg-warning-bg text-warning-color border-warning-border',
    dot: 'bg-warning-bg',
  },
  uncertain: {
    badge: 'bg-grey-100 text-grey-700 border-grey-200',
    dot: 'bg-grey-400',
  },
};

export const ConfidenceBadge: React.FC<ConfidenceBadgeProps> = ({
  score,
  label,
  showType = false,
  type,
  evidenceRef,
  size = 'sm',
  className,
}) => {
  const level = getConfidenceLevel(score);
  const styles = LEVEL_STYLES[level];
  const displayLabel = label ?? `${Math.round(score)}% confidence`;

  const sizeClasses = size === 'sm'
    ? 'px-2 py-0.5 text-[11px] gap-1'
    : 'px-3 py-1 text-xs gap-1.5';

  const dotSize = size === 'sm' ? 'w-1.5 h-1.5' : 'w-2 h-2';

  return (
    <div className={clsx('inline-flex items-center', className)}>
      <span
        className={clsx(
          'inline-flex items-center rounded-full border font-medium uppercase tracking-wide',
          sizeClasses,
          styles.badge
        )}
        title={type ? `Confidence type: ${type}` : undefined}
        data-testid="confidence-badge"
      >
        <span className={clsx('rounded-full', dotSize, styles.dot)} aria-hidden="true" />
        {displayLabel}
      </span>
      {showType && type && (
        <span className="ml-1.5 text-[11px] text-text-secondary">{type}</span>
      )}
      {evidenceRef && (
        <a
          href={evidenceRef}
          target="_blank"
          rel="noopener noreferrer"
          className="ml-1.5 text-[11px] text-primary-600 hover:underline"
        >
          Evidence
        </a>
      )}
    </div>
  );
};

/**
 * Compact confidence indicator for inline use.
 */
export const ConfidenceDot: React.FC<{ score: number }> = ({ score }) => {
  const level = getConfidenceLevel(score);
  const styles = LEVEL_STYLES[level];
  return (
    <span
      className={clsx('inline-block h-2 w-2 rounded-full', styles.dot)}
      title={`${Math.round(score)}% confidence`}
      data-testid="confidence-dot"
    />
  );
};
