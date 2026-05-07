import { describe, expect, it } from 'vitest';

import {
  CANVAS_SYNC_STATUS_LABELS,
  normalizeCanvasSyncStatus,
  pageArtifactSyncStatusToCanvasSyncStatus,
} from '../canvasSyncStatus';

describe('canvasSyncStatus', () => {
  it('normalizes legacy canvas sync statuses into the shared contract', () => {
    expect(normalizeCanvasSyncStatus('synced')).toBe('remote-saved');
    expect(normalizeCanvasSyncStatus('error')).toBe('remote-failed');
    expect(normalizeCanvasSyncStatus('conflict')).toBe('conflict');
  });

  it('maps page artifact sync statuses into canvas badge states', () => {
    expect(pageArtifactSyncStatusToCanvasSyncStatus('dirty')).toBe('local-only');
    expect(pageArtifactSyncStatusToCanvasSyncStatus('saving')).toBe('syncing');
    expect(pageArtifactSyncStatusToCanvasSyncStatus('synced')).toBe('remote-saved');
    expect(pageArtifactSyncStatusToCanvasSyncStatus('error')).toBe('conflict');
    expect(pageArtifactSyncStatusToCanvasSyncStatus('offline')).toBe('remote-failed');
  });

  it('keeps labels exhaustive for the shared status contract', () => {
    expect(Object.keys(CANVAS_SYNC_STATUS_LABELS).sort()).toEqual([
      'conflict',
      'local-only',
      'remote-failed',
      'remote-saved',
      'stale',
      'syncing',
    ]);
  });
});
