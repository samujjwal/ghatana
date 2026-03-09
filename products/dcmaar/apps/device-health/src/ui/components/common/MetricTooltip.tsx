/**
 * @fileoverview Metric tooltip component for contextual help.
 *
 * @module ui/components/common
 */

import React, { useId, useMemo, useState, type ReactNode } from 'react';
import { getMetricGlossaryEntry } from '../../../analytics/metrics/MetricGlossary';

type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

export interface MetricTooltipProps {
  metricKey: string;
  children: ReactNode;
  position?: TooltipPosition;
  className?: string;
}

const positionMap: Record<TooltipPosition, string> = {
  top: 'bottom-full left-1/2 -translate-x-1/2 -translate-y-2',
  bottom: 'top-full left-1/2 -translate-x-1/2 translate-y-2',
  left: 'right-full top-1/2 -translate-y-1/2 -translate-x-2',
  right: 'left-full top-1/2 -translate-y-1/2 translate-x-2',
};

const formatThreshold = (value: number, unit: string): string => {
  if (!Number.isFinite(value)) {
    return '—';
  }

  const unitsNeedingDecimals = unit === '%' ? 1 : 0;
  const formatted = value % 1 === 0 ? value.toString() : value.toFixed(unitsNeedingDecimals || 2);
  return `${formatted}${unit ? unit : ''}`;
};

export const MetricTooltip: React.FC<MetricTooltipProps> = ({
  metricKey,
  children,
  position = 'top',
  className = '',
}) => {
  const [open, setOpen] = useState(false);
  const tooltipId = useId();
  const entry = useMemo(() => getMetricGlossaryEntry(metricKey), [metricKey]);

  if (!entry) {
    return <>{children}</>;
  }

  const goodPrefix = entry.direction === 'higher-is-better' ? '≥ ' : '≤ ';
  const poorPrefix = entry.direction === 'higher-is-better' ? '≤ ' : '≥ ';

  return (
    <span
      className={`relative inline-flex ${className}`}
      onMouseEnter={() => setOpen(true)}
      onFocus={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onBlur={() => setOpen(false)}
      aria-describedby={open ? tooltipId : undefined}
    >
      <span className="inline-flex items-center gap-1">
        {children}
      </span>

      {open && (
        <div
          id={tooltipId}
          role="tooltip"
          className={`absolute z-50 w-72 max-w-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-4 shadow-lg drop-shadow ${positionMap[position]}`}
        >
          <div className="flex flex-col gap-2">
            <div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-white">{entry.fullName}</h4>
              <p className="text-xs text-slate-600 dark:text-slate-400">{entry.description}</p>
            </div>

            <div className="space-y-1 rounded-md bg-slate-50 dark:bg-slate-700 p-2">
              <p className="text-xs text-slate-500 dark:text-slate-400">
                <span className="font-semibold text-slate-600 dark:text-slate-300">Why it matters:</span> {entry.why}
              </p>
              <div className="flex items-center justify-between text-xs font-medium">
                <span className="text-emerald-600">
                  Good: {goodPrefix}
                  {formatThreshold(entry.goodThreshold, entry.unit)}
                </span>
                <span className="text-rose-600">
                  Poor: {poorPrefix}
                  {formatThreshold(entry.poorThreshold, entry.unit)}
                </span>
              </div>
            </div>

            <div className="space-y-1 text-xs text-slate-500 dark:text-slate-400">
              <p className="font-semibold text-slate-600 dark:text-slate-300">Examples</p>
              <div className="rounded border border-emerald-100 bg-emerald-50 p-2">
                <p className="font-medium text-emerald-600">
                  {formatThreshold(entry.examples.good.value, entry.unit)}
                </p>
                <p>{entry.examples.good.description}</p>
              </div>
              <div className="rounded border border-rose-100 bg-rose-50 p-2">
                <p className="font-medium text-rose-600">
                  {formatThreshold(entry.examples.poor.value, entry.unit)}
                </p>
                <p>{entry.examples.poor.description}</p>
              </div>
            </div>

            <a
              href={entry.learnMoreUrl}
              target="_blank"
              rel="noreferrer"
              className="block text-xs font-medium text-blue-600 hover:text-blue-700 hover:underline"
            >
              Learn more →
            </a>
          </div>
        </div>
      )}
    </span>
  );
};

export default MetricTooltip;
