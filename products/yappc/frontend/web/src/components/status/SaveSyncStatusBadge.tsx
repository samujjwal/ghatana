import type { HTMLAttributes } from 'react';
import type { SaveSyncStatusContract } from '@/contracts/workspace-project';

export type SaveSyncStatusLabels = Record<SaveSyncStatusContract, string>;

const DEFAULT_LABELS: SaveSyncStatusLabels = {
  'local-only': 'Local draft only',
  syncing: 'Syncing remote draft',
  'remote-saved': 'Remote draft saved',
  'remote-failed': 'Remote sync failed',
  stale: 'Remote draft stale',
  conflict: 'Sync conflict detected',
};

function getStatusClassName(status: SaveSyncStatusContract): string {
  switch (status) {
    case 'syncing':
      return 'border-info-border bg-info-bg text-info-color dark:border-info-border/60 dark:bg-info-bg/40 dark:text-info-color';
    case 'remote-saved':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200';
    case 'remote-failed':
      return 'border-destructive-border bg-destructive-bg text-destructive dark:border-destructive-border/60 dark:bg-destructive-bg/40 dark:text-destructive';
    case 'stale':
      return 'border-warning-border bg-warning-bg text-warning-color dark:border-warning-border/60 dark:bg-warning-bg/40 dark:text-warning-color';
    case 'conflict':
      return 'border-info-border bg-info-bg text-info-color dark:border-info-border/60 dark:bg-info-bg/40 dark:text-info-color';
    default:
      return 'border-warning-border bg-warning-bg text-warning-color dark:border-warning-border/60 dark:bg-warning-bg/40 dark:text-warning-color';
  }
}

export interface SaveSyncStatusBadgeProps
  extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  status: SaveSyncStatusContract;
  labels?: Partial<SaveSyncStatusLabels>;
}

export function SaveSyncStatusBadge({
  className,
  labels,
  status,
  ...rest
}: SaveSyncStatusBadgeProps) {
  const resolvedLabels = {
    ...DEFAULT_LABELS,
    ...labels,
  } satisfies SaveSyncStatusLabels;

  return (
    <span
      {...rest}
      className={[
        'inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium',
        getStatusClassName(status),
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {resolvedLabels[status]}
    </span>
  );
}