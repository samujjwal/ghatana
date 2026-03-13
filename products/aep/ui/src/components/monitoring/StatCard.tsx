/**
 * StatCard — KPI metric display card for the monitoring dashboard.
 *
 * @doc.type component
 * @doc.purpose Display a single KPI metric with optional trend indicator
 * @doc.layer frontend
 */
import React from 'react';

interface StatCardProps {
  label: string;
  value: string | number;
  /** Optional sub-label displayed below the value. */
  sub?: string;
  /** Positive/negative trend indicator for colouring. */
  trend?: 'up' | 'down' | 'neutral';
  className?: string;
}

export function StatCard({ label, value, sub, trend, className = '' }: StatCardProps) {
  const trendColor =
    trend === 'up' ? 'text-green-600' : trend === 'down' ? 'text-red-500' : 'text-gray-500';

  return (
    <div
      className={[
        'rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-5 py-4 flex flex-col gap-1',
        className,
      ].join(' ')}
    >
      <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
        {label}
      </span>
      <span className={['text-2xl font-bold tabular-nums', trendColor].join(' ')}>
        {value}
      </span>
      {sub && (
        <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
      )}
    </div>
  );
}
