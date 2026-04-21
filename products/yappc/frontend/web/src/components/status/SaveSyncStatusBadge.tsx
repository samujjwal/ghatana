import type { HTMLAttributes } from 'react';
import type { SaveSyncStatusContract } from '@/contracts/workspace-project';

export type SaveSyncStatusLabels = Record<SaveSyncStatusContract, string>;

const DEFAULT_LABELS: SaveSyncStatusLabels = {
  'local-only': 'Local draft only',
  syncing: 'Syncing remote draft',
  'remote-saved': 'Remote draft saved',
  'remote-failed': 'Remote sync failed',
};

function getStatusClassName(status: SaveSyncStatusContract): string {
  switch (status) {
    case 'syncing':
      return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/60 dark:bg-blue-950/40 dark:text-blue-200';
    case 'remote-saved':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200';
    case 'remote-failed':
      return 'border-red-200 bg-red-50 text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200';
    default:
      return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200';
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