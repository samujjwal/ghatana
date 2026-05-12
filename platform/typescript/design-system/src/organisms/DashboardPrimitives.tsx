import * as React from 'react';
import { cn } from '@ghatana/platform-utils';

type DashboardGridDensity = 'compact' | 'comfortable';
type DashboardPanelTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

export interface DashboardGridProps extends React.HTMLAttributes<HTMLDivElement> {
  readonly density?: DashboardGridDensity;
}

export interface KpiStripProps extends React.HTMLAttributes<HTMLDivElement> {
  readonly children: React.ReactNode;
  readonly columns?: 2 | 3 | 4;
}

export interface DataCardProps extends Omit<React.HTMLAttributes<HTMLElement>, 'title'> {
  readonly title: React.ReactNode;
  readonly description?: React.ReactNode;
  readonly value?: React.ReactNode;
  readonly trend?: React.ReactNode;
  readonly actions?: React.ReactNode;
  readonly tone?: DashboardPanelTone;
}

export interface FilterBarProps extends React.HTMLAttributes<HTMLDivElement> {
  readonly children: React.ReactNode;
  readonly summary?: React.ReactNode;
  readonly actions?: React.ReactNode;
}

export interface ChartPanelProps extends Omit<React.HTMLAttributes<HTMLElement>, 'title'> {
  readonly title: React.ReactNode;
  readonly description?: React.ReactNode;
  readonly controls?: React.ReactNode;
  readonly children: React.ReactNode;
}

export interface ActivityLogProps extends Omit<React.HTMLAttributes<HTMLElement>, 'title'> {
  readonly title: React.ReactNode;
  readonly children: React.ReactNode;
  readonly actions?: React.ReactNode;
}

const densityClasses: Record<DashboardGridDensity, string> = {
  compact: 'gap-3',
  comfortable: 'gap-5',
};

const panelToneClasses: Record<DashboardPanelTone, string> = {
  neutral: 'border-gray-200 bg-white text-gray-950',
  info: 'border-blue-200 bg-blue-50 text-blue-950',
  success: 'border-green-200 bg-green-50 text-green-950',
  warning: 'border-yellow-200 bg-yellow-50 text-yellow-950',
  danger: 'border-red-200 bg-red-50 text-red-950',
};

export function DashboardGrid({
  density = 'comfortable',
  className,
  children,
  ...rest
}: DashboardGridProps): React.ReactElement {
  return (
    <div
      className={cn('grid grid-cols-1 lg:grid-cols-12', densityClasses[density], className)}
      {...rest}
    >
      {children}
    </div>
  );
}

export function KpiStrip({
  columns = 4,
  className,
  children,
  ...rest
}: KpiStripProps): React.ReactElement {
  const columnClasses: Record<NonNullable<KpiStripProps['columns']>, string> = {
    2: 'sm:grid-cols-2',
    3: 'sm:grid-cols-2 xl:grid-cols-3',
    4: 'sm:grid-cols-2 xl:grid-cols-4',
  };

  return (
    <div
      className={cn('grid grid-cols-1 gap-3', columnClasses[columns], className)}
      {...rest}
    >
      {children}
    </div>
  );
}

export function DataCard({
  title,
  description,
  value,
  trend,
  actions,
  tone = 'neutral',
  className,
  children,
  ...rest
}: DataCardProps): React.ReactElement {
  return (
    <article
      className={cn('rounded border p-4 shadow-sm', panelToneClasses[tone], className)}
      {...rest}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold">{title}</h3>
          {description ? <p className="mt-1 text-sm text-gray-600">{description}</p> : null}
        </div>
        {actions ? <div className="shrink-0">{actions}</div> : null}
      </div>
      {value ? <div className="mt-4 text-2xl font-semibold tracking-normal">{value}</div> : null}
      {trend ? <div className="mt-2 text-sm text-gray-600">{trend}</div> : null}
      {children ? <div className="mt-4">{children}</div> : null}
    </article>
  );
}

export function FilterBar({
  summary,
  actions,
  className,
  children,
  ...rest
}: FilterBarProps): React.ReactElement {
  return (
    <div
      className={cn('flex flex-col gap-3 border-y border-gray-200 bg-white py-3 sm:flex-row sm:items-center sm:justify-between', className)}
      {...rest}
    >
      <div className="flex flex-1 flex-wrap items-center gap-2">{children}</div>
      {(summary || actions) ? (
        <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600">
          {summary ? <span>{summary}</span> : null}
          {actions}
        </div>
      ) : null}
    </div>
  );
}

export function ChartPanel({
  title,
  description,
  controls,
  className,
  children,
  ...rest
}: ChartPanelProps): React.ReactElement {
  return (
    <section className={cn('rounded border border-gray-200 bg-white p-4 shadow-sm', className)} {...rest}>
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-semibold text-gray-950">{title}</h2>
          {description ? <p className="mt-1 text-sm text-gray-600">{description}</p> : null}
        </div>
        {controls ? <div className="shrink-0">{controls}</div> : null}
      </div>
      <div className="min-h-48">{children}</div>
    </section>
  );
}

export function ActivityLog({
  title,
  actions,
  className,
  children,
  ...rest
}: ActivityLogProps): React.ReactElement {
  return (
    <section className={cn('rounded border border-gray-200 bg-white p-4 shadow-sm', className)} {...rest}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-gray-950">{title}</h2>
        {actions ? <div className="shrink-0">{actions}</div> : null}
      </div>
      <div className="space-y-3">{children}</div>
    </section>
  );
}
