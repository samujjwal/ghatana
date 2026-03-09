import React, { useMemo, useState } from 'react';
import { Card, Skeleton } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { TimeRange } from '@ghatana/dcmaar-shared-ui-core';

import { useMetricsData } from '../hooks/useMetricsData';
import { Sparkline } from '../components/data/Sparkline';

const RANGE_LABELS: Record<TimeRange, string> = {
  last1h: 'Last Hour',
  last24h: 'Last 24 Hours',
  last7d: 'Last 7 Days',
  last30d: 'Last 30 Days',
};

const formatNumber = (value?: number, decimals = 1, unit = ''): string => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '—';
  }
  return `${value.toFixed(decimals)}${unit}`;
};

export const MetricsPage: React.FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('last24h');
  const { data: metrics, isLoading } = useMetricsData(timeRange);

  const averageCards = useMemo(
    () => [
      {
        label: 'Avg LCP',
        value: formatNumber(metrics?.averages.lcp, 0, ' ms'),
        tone: 'bg-blue-50 text-blue-900',
      },
      {
        label: 'Avg INP',
        value: formatNumber(metrics?.averages.inp, 0, ' ms'),
        tone: 'bg-purple-50 text-purple-900',
      },
      {
        label: 'Avg CLS',
        value: formatNumber(metrics?.averages.cls, 3),
        tone: 'bg-amber-50 text-amber-900',
      },
      {
        label: 'Avg TBT',
        value: formatNumber(metrics?.averages.tbt, 0, ' ms'),
        tone: 'bg-rose-50 text-rose-900',
      },
      {
        label: 'Avg Transfer',
        value: formatNumber(metrics?.network.averageTransfer, 0, ' KB'),
        tone: 'bg-emerald-50 text-emerald-900',
      },
      {
        label: 'Avg Requests',
        value: formatNumber(metrics?.network.averageRequests, 0),
        tone: 'bg-slate-50 text-slate-900',
      },
      {
        label: 'Samples',
        value: metrics ? `${metrics.sampleCount}` : '0',
        tone: 'bg-slate-100 text-slate-900',
      },
    ],
    [metrics]
  );

  const percentileRows = useMemo(
    () => [
      {
        label: 'LCP',
        p95: formatNumber(metrics?.p95.lcp, 0, ' ms'),
        p99: formatNumber(metrics?.p99.lcp, 0, ' ms'),
      },
      {
        label: 'INP',
        p95: formatNumber(metrics?.p95.inp, 0, ' ms'),
        p99: formatNumber(metrics?.p99.inp, 0, ' ms'),
      },
      {
        label: 'CLS',
        p95: formatNumber(metrics?.p95.cls, 3),
        p99: formatNumber(metrics?.p99.cls, 3),
      },
      {
        label: 'TBT',
        p95: formatNumber(metrics?.p95.tbt, 0, ' ms'),
        p99: formatNumber(metrics?.p99.tbt, 0, ' ms'),
      },
    ],
    [metrics]
  );

  const lcpTimeline = metrics?.timeline.map((entry) => entry.lcp) ?? [];
  const inpTimeline = metrics?.timeline.map((entry) => entry.inp) ?? [];
  const transferTimeline =
    metrics?.timeline.map((entry) => entry.resourceTransfer ?? 0) ?? [];

  return (
    <div className="p-6 space-y-6 max-w-7xl">
      <div className="flex flex-wrap gap-2">
        {(Object.keys(RANGE_LABELS) as TimeRange[]).map((range) => (
          <button
            key={range}
            onClick={() => setTimeRange(range)}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition ${timeRange === range
                ? 'bg-blue-600 text-white shadow'
                : 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50'
              }`}
          >
            {RANGE_LABELS[range]}
          </button>
        ))}
      </div>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
        {isLoading
          ? Array.from({ length: 7 }).map((_, idx) => (
            <Card key={idx} className="p-4">
              <Skeleton count={2} height="h-6" />
            </Card>
          ))
          : averageCards.map((card) => (
            <Card key={card.label} className={`p-4 ${card.tone}`}>
              <p className="text-xs font-semibold uppercase text-slate-500">{card.label}</p>
              <p className="mt-2 text-2xl font-semibold">{card.value}</p>
            </Card>
          ))}
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <Card title="LCP Trend" description="Largest Contentful Paint trend line">
          {isLoading ? (
            <Skeleton height="h-24" />
          ) : (
            <Sparkline values={lcpTimeline} ariaLabel="LCP trend" />
          )}
        </Card>
        <Card title="INP Trend" description="Interaction to Next Paint trend line">
          {isLoading ? (
            <Skeleton height="h-24" />
          ) : (
            <Sparkline values={inpTimeline} color="#9333ea" ariaLabel="INP trend" />
          )}
        </Card>
      </section>

      <Card title="Network Throughput" description="Transfer volume per sample (KB)">
        {isLoading ? (
          <Skeleton height="h-24" />
        ) : (
          <div className="space-y-4 md:flex md:items-center md:justify-between md:space-y-0">
            <div>
              <p className="text-sm text-slate-600 dark:text-slate-400">Total transfer</p>
              <p className="text-2xl font-semibold text-slate-900 dark:text-white">
                {formatNumber(metrics?.network.totalTransfer, 0, ' KB')}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Average {formatNumber(metrics?.network.averageTransfer, 1, ' KB')} across all samples
              </p>
            </div>
            <div className="min-w-[160px] flex-1">
              <Sparkline
                values={transferTimeline}
                color="#059669"
                ariaLabel="Network transfer trend"
              />
            </div>
          </div>
        )}
      </Card>

      <Card title="Percentiles" description="P95 / P99 bands for collected samples">
        {isLoading ? (
          <Skeleton count={4} height="h-6" />
        ) : (
          <table className="mt-3 w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="py-1">Metric</th>
                <th className="py-1">P95</th>
                <th className="py-1">P99</th>
              </tr>
            </thead>
            <tbody>
              {percentileRows.map((row) => (
                <tr key={row.label} className="border-t border-slate-200 text-slate-700">
                  <td className="py-2 font-medium">{row.label}</td>
                  <td className="py-2">{row.p95}</td>
                  <td className="py-2">{row.p99}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card title="Budget Status" description="Counts of warning and critical breaches">
        {isLoading ? (
          <Skeleton count={4} height="h-6" />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {['lcp', 'inp', 'cls', 'tbt'].map((metric) => {
              const labelMap: Record<string, string> = {
                lcp: 'Largest Contentful Paint',
                inp: 'Interaction to Next Paint',
                cls: 'Cumulative Layout Shift',
                tbt: 'Total Blocking Time',
              };
              const status = metrics?.budgetStatus[metric as 'lcp' | 'inp' | 'cls' | 'tbt'] ?? {
                ok: 0,
                warning: 0,
                critical: 0,
              };

              return (
                <div key={metric} className="rounded border border-slate-200 p-4">
                  <p className="text-sm font-semibold text-slate-800">{labelMap[metric]}</p>
                  <div className="mt-2 flex items-center gap-3 text-xs">
                    <span className="rounded-full bg-emerald-100 px-2 py-1 font-medium text-emerald-700">
                      OK {status.ok}
                    </span>
                    <span className="rounded-full bg-amber-100 px-2 py-1 font-medium text-amber-700">
                      Warning {status.warning}
                    </span>
                    <span className="rounded-full bg-rose-100 px-2 py-1 font-medium text-rose-700">
                      Critical {status.critical}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      <Card title="Recent Samples" description="Latest metric snapshots" className="overflow-hidden">
        {isLoading ? (
          <Skeleton count={5} height="h-7" />
        ) : metrics && metrics.timeline.length ? (
          <div className="max-h-96 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-white text-xs uppercase tracking-wide text-slate-500">
                <tr className="text-left">
                  <th className="py-2">Timestamp</th>
                  <th className="py-2">LCP</th>
                  <th className="py-2">INP</th>
                  <th className="py-2">CLS</th>
                  <th className="py-2">TBT</th>
                  <th className="py-2">Violations</th>
                  <th className="py-2">Transfer (KB)</th>
                  <th className="py-2">Requests</th>
                </tr>
              </thead>
              <tbody>
                {metrics.timeline
                  .slice(-50)
                  .reverse()
                  .map((entry) => (
                    <tr key={entry.timestamp} className="border-t border-slate-200 dark:border-slate-700">
                      <td className="py-2 text-slate-600 dark:text-slate-400">
                        {new Date(entry.timestamp).toLocaleString()}
                      </td>
                      <td className="py-2">{formatNumber(entry.lcp, 0, ' ms')}</td>
                      <td className="py-2">{formatNumber(entry.inp, 0, ' ms')}</td>
                      <td className="py-2">{formatNumber(entry.cls, 3)}</td>
                      <td className="py-2">{formatNumber(entry.tbt, 0, ' ms')}</td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        {entry.budgetViolations ?? 0}
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        {formatNumber(entry.resourceTransfer, 0, ' KB')}
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        {entry.resourceCount ?? 0}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="py-6 text-center text-sm text-slate-500">No samples recorded yet.</p>
        )}
      </Card>
    </div>
  );
};
