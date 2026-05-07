import type { SaveSyncStatusContract } from '@/contracts/workspace-project';
import type { PageArtifactSyncStatus } from '@/components/canvas/page/pageArtifactDocument';

export type CanvasSyncStatus = SaveSyncStatusContract;
export type LegacyCanvasSyncStatus = 'synced' | 'error';

export const CANVAS_SYNC_STATUS_LABELS: Record<CanvasSyncStatus, string> = {
  'local-only': 'Local draft only',
  syncing: 'Syncing remote draft',
  'remote-saved': 'Remote draft saved',
  'remote-failed': 'Remote sync failed',
  stale: 'Remote draft stale',
  conflict: 'Sync conflict detected',
};

export function normalizeCanvasSyncStatus(
  status: CanvasSyncStatus | LegacyCanvasSyncStatus,
): CanvasSyncStatus {
  if (status === 'synced') {
    return 'remote-saved';
  }
  if (status === 'error') {
    return 'remote-failed';
  }
  return status;
}

export function pageArtifactSyncStatusToCanvasSyncStatus(
  status: PageArtifactSyncStatus,
): CanvasSyncStatus {
  switch (status) {
    case 'dirty':
      return 'local-only';
    case 'saving':
      return 'syncing';
    case 'synced':
      return 'remote-saved';
    case 'error':
      return 'conflict';
    case 'offline':
      return 'remote-failed';
  }
}
