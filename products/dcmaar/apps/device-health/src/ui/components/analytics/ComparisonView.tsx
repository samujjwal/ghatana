/**
 * @fileoverview Comparison View Component
 *
 * Presents side-by-side comparison of metric performance across two
 * selected time periods with delta analysis and qualitative insights.
 *
 * @module ui/components/analytics
 * @since 2.0.0
 */

import React, { useMemo } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { ProcessedMetrics } from '../../../analytics/AnalyticsPipeline';
import {
  getMetricGlossaryEntry,
  type MetricGlossaryEntry,
} from '../../../analytics/metrics/MetricGlossary';

interface ComparisonPeriod {
  label: string;
  range: { from: number; to: number };
  data: ProcessedMetrics[];
}

interface ComparisonViewProps {
  metrics: string[];
  periodA?: ComparisonPeriod;
  periodB?: ComparisonPeriod;
}

type DeltaDirection = 'improved' | 'degraded' | 'stable' | 'unknown';

const formatValue = (value: number, entry?: MetricGlossaryEntry): string => {
  if (!Number.isFinite(value)) return '—';
  const unit = entry?.unit ?? '';

  if (unit === 'ms') {
    return `${Math.round(value)}ms`;
  }

  if (unit === '' && Math.abs(value) < 1) {
    return value.toFixed(2);
  }

  if (unit === '%' || entry?.fullName.toLowerCase().includes('rate')) {
    return `${value.toFixed(1)}%`;
  }

  if (unit === 'KB') {
    if (value > 1024) {
      return `${(value / 1024).toFixed(1)}MB`;
    }
    return `${value.toFixed(1)}KB`;
  }

  return value.toFixed(1);
};

const computeAggregate = (data: ProcessedMetrics[], metric: string): number => {
  if (!data || data.length === 0) return Number.NaN;
  const values = data
    .map((entry) => entry.summary[metric])
    .filter((value) => typeof value === 'number' && Number.isFinite(value));
  if (!values.length) return Number.NaN;

  // Use average for a quick comparison baseline
  const sum = values.reduce((acc, value) => acc + value, 0);
  return sum / values.length;
};

const determineDirection = (
  current: number,
  baseline: number,
  entry?: MetricGlossaryEntry
): DeltaDirection => {
  if (!Number.isFinite(current) || !Number.isFinite(baseline)) {
    return 'unknown';
  }

  const delta = current - baseline;
  if (Math.abs(delta) < 0.01) {
    return 'stable';
  }

  const direction = entry?.direction ?? 'lower-is-better';
  const improved =
    direction === 'lower-is-better' ? delta < 0 : delta > 0;

  return improved ? 'improved' : 'degraded';
};

const formatDelta = (
  current: number,
  baseline: number,
  entry?: MetricGlossaryEntry
): string => {
  if (!Number.isFinite(current) || !Number.isFinite(baseline)) return '—';
  const diff = current - baseline;

  if (entry?.unit === '%' || entry?.fullName.toLowerCase().includes('rate')) {
    const percent = (diff / (baseline === 0 ? 1 : baseline)) * 100;
    return `${percent >= 0 ? '+' : ''}${percent.toFixed(1)}%`;
  }

  const unitSuffix = entry?.unit ? entry.unit : '';

  if (entry?.unit === 'ms') {
    return `${diff >= 0 ? '+' : ''}${Math.round(diff)}ms`;
  }

  if (entry?.unit === 'KB') {
    return `${diff >= 0 ? '+' : ''}${diff.toFixed(1)}${unitSuffix}`;
  }

  return `${diff >= 0 ? '+' : ''}${diff.toFixed(2)}${unitSuffix}`;
};

const statusLabel = (direction: DeltaDirection): string => {
  switch (direction) {
    case 'improved':
      return 'Improved';
    case 'degraded':
      return 'Degraded';
    case 'stable':
      return 'Stable';
    default:
      return 'Unknown';
  }
};

const statusClass = (direction: DeltaDirection): string => {
  switch (direction) {
    case 'improved':
      return 'text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/30';
    case 'degraded':
      return 'text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-900/30';
    case 'stable':
      return 'text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/30';
    default:
      return 'text-slate-600 dark:text-slate-400 bg-slate-100 dark:bg-slate-800';
  }
};

const arrowForDirection = (direction: DeltaDirection): string => {
  switch (direction) {
    case 'improved':
      return '↘';
    case 'degraded':
      return '↗';
    case 'stable':
      return '→';
    default:
      return '•';
  }
};

export const ComparisonView: React.FC<ComparisonViewProps> = ({
  metrics,
  periodA,
  periodB,
}) => {
  const comparisons = useMemo(() => {
    if (!periodA?.data?.length || !periodB?.data?.length) {
      return [];
    }

    return metrics.map((metric) => {
      const entry = getMetricGlossaryEntry(metric);
      const valueA = computeAggregate(periodA.data, metric);
      const valueB = computeAggregate(periodB.data, metric);
      const direction = determineDirection(valueA, valueB, entry);
      return {
        metric,
        entry,
        periodAValue: valueA,
        periodBValue: valueB,
        deltaLabel: formatDelta(valueA, valueB, entry),
        direction,
      };
    });
  }, [metrics, periodA, periodB]);

  const summary = useMemo(() => {
    if (!comparisons.length) return null;
    const improved = comparisons.filter((item) => item.direction === 'improved').length;
    const degraded = comparisons.filter((item) => item.direction === 'degraded').length;
    const stable = comparisons.filter((item) => item.direction === 'stable').length;

    return {
      improved,
      degraded,
      stable,
      total: comparisons.length,
    };
  }, [comparisons]);

  if (!periodA || !periodB) {
    return (
      <Card title="Comparison View">
        <div className="flex h-48 flex-col items-center justify-center text-sm text-slate-500">
          Enable comparison mode and select two periods to view side-by-side insights.
        </div>
      </Card>
    );
  }

  if (!comparisons.length) {
    return (
      <Card title="Comparison View">
        <div className="flex h-48 flex-col items-center justify-center text-sm text-slate-500">
          Not enough data within the selected periods to generate a comparison.
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card
        title="Comparison Summary"
        description={`Period A: ${periodA.label} • Period B: ${periodB.label}`}
      >
        {summary && (
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-center">
              <div className="text-2xl font-bold text-emerald-600">{summary.improved}</div>
              <div className="text-xs uppercase tracking-wide text-emerald-700">Improved</div>
            </div>
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-center">
              <div className="text-2xl font-bold text-rose-600">{summary.degraded}</div>
              <div className="text-xs uppercase tracking-wide text-rose-700">Degraded</div>
            </div>
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-center">
              <div className="text-2xl font-bold text-amber-600">{summary.stable}</div>
              <div className="text-xs uppercase tracking-wide text-amber-700">Stable</div>
            </div>
          </div>
        )}
      </Card>

      <Card title="Metric Comparison">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Metric
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-500">
                  {periodA.label}
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-500">
                  {periodB.label}
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-500">
                  Delta
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Trend
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white">
              {comparisons.map((item) => (
                <tr key={item.metric} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-sm font-medium text-slate-900">
                    <div>{item.entry?.fullName ?? item.metric.toUpperCase()}</div>
                    <div className="text-xs text-slate-500">
                      {item.entry?.description ?? 'Metric comparison'}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right text-sm text-slate-700">
                    {formatValue(item.periodAValue, item.entry)}
                  </td>
                  <td className="px-4 py-3 text-right text-sm text-slate-700">
                    {formatValue(item.periodBValue, item.entry)}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-semibold text-slate-700">
                    {item.deltaLabel}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium ${statusClass(item.direction)}`}
                    >
                      {arrowForDirection(item.direction)} {statusLabel(item.direction)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

export default ComparisonView;
