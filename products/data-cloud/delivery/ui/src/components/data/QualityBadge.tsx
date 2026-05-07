/**
 * Quality Badge Component
 *
 * Inline quality indicator for data fields and columns.
 * Shows quality score with color-coded badge and hover details.
 *
 * Features:
 * - Color-coded quality scores (green/yellow/red)
 * - Hover tooltip with detailed breakdown
 * - Click to see full quality report
 * - Compact and inline display
 *
 * @doc.type component
 * @doc.purpose Inline quality indicator badge
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { AlertTriangle, CheckCircle, XCircle, Info } from 'lucide-react';
import { cn } from '../../lib/theme';

export type QualityLevel = 'excellent' | 'good' | 'fair' | 'poor' | 'unknown';

export interface QualityMetrics {
  completeness: number;
  accuracy: number;
  freshness: number;
  consistency: number;
}

export interface QualityBadgeProps {
  score: number; // 0-100
  metrics?: QualityMetrics;
  issues?: number;
  size?: 'sm' | 'md' | 'lg';
  showLabel?: boolean;
  onClick?: () => void;
  className?: string;
}

/**
 * Get quality level from score
 */
function getQualityLevel(score: number): QualityLevel {
  if (score >= 90) return 'excellent';
  if (score >= 70) return 'good';
  if (score >= 50) return 'fair';
  if (score >= 0) return 'poor';
  return 'unknown';
}

/**
 * Quality level configurations
 */
const QUALITY_CONFIG: Record<
  QualityLevel,
  {
    color: string;
    bgColor: string;
    borderColor: string;
    icon: React.ReactNode;
    label: string;
  }
> = {
  excellent: {
    color: 'text-green-700 dark:text-green-300',
    bgColor: 'bg-green-100 dark:bg-green-900/30',
    borderColor: 'border-green-200 dark:border-green-800',
    icon: <CheckCircle className="h-3 w-3" />,
    label: 'Excellent',
  },
  good: {
    color: 'text-blue-700 dark:text-blue-300',
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    borderColor: 'border-blue-200 dark:border-blue-800',
    icon: <CheckCircle className="h-3 w-3" />,
    label: 'Good',
  },
  fair: {
    color: 'text-amber-700 dark:text-amber-300',
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
    borderColor: 'border-amber-200 dark:border-amber-800',
    icon: <AlertTriangle className="h-3 w-3" />,
    label: 'Fair',
  },
  poor: {
    color: 'text-red-700 dark:text-red-300',
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    borderColor: 'border-red-200 dark:border-red-800',
    icon: <XCircle className="h-3 w-3" />,
    label: 'Poor',
  },
  unknown: {
    color: 'text-gray-500 dark:text-gray-400',
    bgColor: 'bg-gray-100 dark:bg-gray-800',
    borderColor: 'border-gray-200 dark:border-gray-700',
    icon: <Info className="h-3 w-3" />,
    label: 'Unknown',
  },
};

/**
 * Size configurations
 */
const SIZE_CONFIG = {
  sm: {
    badge: 'px-1.5 py-0.5 text-xs',
    icon: 'h-3 w-3',
    tooltip: 'w-48',
  },
  md: {
    badge: 'px-2 py-1 text-xs',
    icon: 'h-3.5 w-3.5',
    tooltip: 'w-56',
  },
  lg: {
    badge: 'px-2.5 py-1.5 text-sm',
    icon: 'h-4 w-4',
    tooltip: 'w-64',
  },
};

/**
 * Metric Bar Component
 */
function MetricBar({ label, value }: { label: string; value: number }) {
  const level = getQualityLevel(value);
  const config = QUALITY_CONFIG[level];

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500 w-20">{label}</span>
      <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className={cn('h-full rounded-full transition-all', config.bgColor)}
          style={{ width: `${value}%` }}
        />
      </div>
      <span className={cn('text-xs font-medium w-8 text-right', config.color)}>
        {value}%
      </span>
    </div>
  );
}

/**
 * Quality Badge Component
 */
export function QualityBadge({
  score,
  metrics,
  issues,
  size = 'md',
  showLabel = false,
  onClick,
  className,
}: QualityBadgeProps) {
  const [showTooltip, setShowTooltip] = useState(false);
  const level = getQualityLevel(score);
  const config = QUALITY_CONFIG[level];
  const sizeConfig = SIZE_CONFIG[size];

  return (
    <div className="relative inline-flex">
      <button
        onClick={onClick}
        onMouseEnter={() => setShowTooltip(true)}
        onMouseLeave={() => setShowTooltip(false)}
        className={cn(
          'inline-flex items-center gap-1 rounded-full border',
          'transition-all hover:shadow-sm',
          config.bgColor,
          config.borderColor,
          config.color,
          sizeConfig.badge,
          onClick && 'cursor-pointer',
          className
        )}
      >
        {config.icon}
        <span className="font-medium">{score}%</span>
        {showLabel && (
          <span className="font-normal opacity-80">{config.label}</span>
        )}
        {issues !== undefined && issues > 0 && (
          <span className="ml-0.5 px-1 py-0.5 bg-white/50 dark:bg-black/20 rounded text-xs">
            {issues}
          </span>
        )}
      </button>

      {/* Tooltip */}
      {showTooltip && metrics && (
        <div
          className={cn(
            'absolute bottom-full left-1/2 -translate-x-1/2 mb-2',
            'bg-white dark:bg-gray-900',
            'border border-gray-200 dark:border-gray-700',
            'rounded-lg shadow-lg p-3 z-50',
            sizeConfig.tooltip
          )}
        >
          <div className="flex items-center justify-between mb-2 pb-2 border-b border-gray-100 dark:border-gray-800">
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              Quality Score
            </span>
            <span className={cn('text-lg font-bold', config.color)}>
              {score}%
            </span>
          </div>
          <div className="space-y-2">
            <MetricBar label="Completeness" value={metrics.completeness} />
            <MetricBar label="Accuracy" value={metrics.accuracy} />
            <MetricBar label="Freshness" value={metrics.freshness} />
            <MetricBar label="Consistency" value={metrics.consistency} />
          </div>
          {issues !== undefined && issues > 0 && (
            <div className="mt-2 pt-2 border-t border-gray-100 dark:border-gray-800">
              <span className="text-xs text-amber-600 dark:text-amber-400">
                ⚠️ {issues} issue{issues > 1 ? 's' : ''} detected
              </span>
            </div>
          )}
          {/* Arrow */}
          <div className="absolute top-full left-1/2 -translate-x-1/2 -mt-px">
            <div className="border-8 border-transparent border-t-white dark:border-t-gray-900" />
          </div>
        </div>
      )}
    </div>
  );
}
