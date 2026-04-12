/**
 * @fileoverview ConfidenceBadge - Displays numeric confidence with semantic color band.
 *
 * @doc.type component
 * @doc.purpose Visual indicator of AI confidence level with semantic coloring.
 * @doc.category atom
 * @doc.tags ai, visibility, confidence
 */

import * as React from 'react';

export type ConfidenceBand = 'low' | 'medium' | 'high';

export interface ConfidenceBadgeProps {
  /** Confidence value between 0 and 1 */
  readonly confidence: number;
  /** Optional label to display alongside confidence */
  readonly label?: string;
  /** Size variant */
  readonly size?: 'sm' | 'md' | 'lg';
  /** Whether to show the numeric percentage */
  readonly showPercentage?: boolean;
  /** Additional CSS classes */
  readonly className?: string;
}

/**
 * Determines the confidence band from a numeric value.
 */
export function getConfidenceBand(confidence: number): ConfidenceBand {
  if (confidence >= 0.8) return 'high';
  if (confidence >= 0.5) return 'medium';
  return 'low';
}

const bandConfig: Record<
  ConfidenceBand,
  {
    readonly color: string;
    readonly bgColor: string;
    readonly label: string;
  }
> = {
  high: {
    color: 'text-green-700',
    bgColor: 'bg-green-100',
    label: 'High Confidence',
  },
  medium: {
    color: 'text-yellow-700',
    bgColor: 'bg-yellow-100',
    label: 'Medium Confidence',
  },
  low: {
    color: 'text-red-700',
    bgColor: 'bg-red-100',
    label: 'Low Confidence',
  },
};

const sizeConfig = {
  sm: 'text-xs px-2 py-0.5 gap-1',
  md: 'text-sm px-2.5 py-1 gap-1.5',
  lg: 'text-base px-3 py-1.5 gap-2',
};

/**
 * ConfidenceBadge component - displays AI confidence with visual indicator.
 */
export const ConfidenceBadge: React.FC<ConfidenceBadgeProps> = React.memo(({
  confidence,
  label,
  size = 'md',
  showPercentage = true,
  className = '',
}) => {
  // Clamp confidence to 0-1 range
  const clampedConfidence = Math.max(0, Math.min(1, confidence));
  const band = getConfidenceBand(clampedConfidence);
  const config = bandConfig[band];
  const sizeClasses = sizeConfig[size];
  const percentage = Math.round(clampedConfidence * 100);

  return (
    <span
      className={`inline-flex items-center rounded-full font-medium ${config.bgColor} ${config.color} ${sizeClasses} ${className}`}
      role="meter"
      aria-valuenow={percentage}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={`${config.label}: ${percentage}%`}
      title={`${config.label}: ${percentage}%`}
    >
      {/* Confidence bar visual */}
      <span className="relative flex h-2 w-2">
        <span
          className={`absolute inline-flex h-full w-full rounded-full ${config.bgColor.replace('100', '400')} opacity-75`}
        />
        <span
          className={`relative inline-flex h-2 w-2 rounded-full ${config.bgColor.replace('100', '500')}`}
        />
      </span>

      {/* Label or percentage */}
      {label ? (
        <span>{label}</span>
      ) : showPercentage ? (
        <span>{percentage}%</span>
      ) : (
        <span>{config.label}</span>
      )}
    </span>
  );
});

ConfidenceBadge.displayName = 'ConfidenceBadge';

/**
 * ConfidenceRange displays a confidence band (low-high) as used in AIVisibilityContract.
 */
export interface ConfidenceRangeProps {
  readonly low: number;
  readonly high: number;
  readonly size?: 'sm' | 'md' | 'lg';
  readonly className?: string;
}

export const ConfidenceRange: React.FC<ConfidenceRangeProps> = React.memo(({
  low,
  high,
  size = 'md',
  className = '',
}) => {
  const lowBand = getConfidenceBand(low);
  const highBand = getConfidenceBand(high);
  const lowConfig = bandConfig[lowBand];
  const highConfig = bandConfig[highBand];
  const sizeClasses = sizeConfig[size];

  const lowPct = Math.round(low * 100);
  const highPct = Math.round(high * 100);

  return (
    <span
      className={`inline-flex items-center rounded-md bg-gray-100 ${sizeClasses} ${className}`}
      role="meter"
      aria-label={`Confidence range: ${lowPct}% to ${highPct}%`}
    >
      <span className={lowConfig.color}>{lowPct}%</span>
      <span className="text-gray-400 mx-1">-</span>
      <span className={highConfig.color}>{highPct}%</span>
    </span>
  );
});

ConfidenceRange.displayName = 'ConfidenceRange';
