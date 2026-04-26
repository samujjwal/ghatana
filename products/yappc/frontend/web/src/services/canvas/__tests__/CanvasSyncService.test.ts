import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CanvasSyncService, getCanvasSyncService, clearCanvasSyncService } from '../CanvasSyncService';

describe('CanvasSyncService', () => {
  beforeEach(() => {
    clearCanvasSyncService('test-project');
  });

  it('starts in local-only state', () => {
    const service = getCanvasSyncService('test-project');
    expect(service.getState()).toBe('local-only');
  });

  it('transitions to syncing on startSync', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    expect(service.getState()).toBe('syncing');
  });

  it('transitions to remote-saved on syncSucceeded', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.syncSucceeded(42);
    expect(service.getState()).toBe('remote-saved');
    expect(service.getSnapshot().remoteVersion).toBe(42);
    expect(service.getSnapshot().lastSyncedAt).not.toBeNull();
  });

  it('transitions to remote-failed on syncFailed', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.syncFailed('Network timeout');
    expect(service.getState()).toBe('remote-failed');
    expect(service.getSnapshot().history.at(-1)?.message).toContain('Network timeout');
  });

  it('transitions from saved to stale on markLocalChange', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.syncSucceeded();
    service.markLocalChange();
    expect(service.getState()).toBe('stale');
  });

  it('transitions to conflict on reportConflict', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.reportConflict('Remote updated by another user');
    expect(service.getState()).toBe('conflict');
  });

  it('resolves conflict with local winning', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.reportConflict();
    service.resolveConflict('local');
    expect(service.getState()).toBe('local-only');
  });

  it('resolves conflict with remote winning', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.reportConflict();
    service.resolveConflict('remote');
    expect(service.getState()).toBe('syncing');
  });

  it('emits snapshot to subscribers', () => {
    const service = getCanvasSyncService('test-project');
    const listener = vi.fn();
    const unsubscribe = service.subscribe(listener);

    service.startSync();
    expect(listener).toHaveBeenCalledTimes(1);

    unsubscribe();
    service.syncSucceeded();
    expect(listener).toHaveBeenCalledTimes(1); // no new call after unsubscribe
  });

  it('caps history at 50 entries', () => {
    const service = getCanvasSyncService('test-project');
    for (let i = 0; i < 60; i++) {
      service.markStale(`reason-${i}`);
    }
    expect(service.getSnapshot().history.length).toBeLessThanOrEqual(50);
  });

  it('does not duplicate identical states', () => {
    const service = getCanvasSyncService('test-project');
    service.startSync();
    service.startSync();
    expect(service.getSnapshot().history.length).toBe(1);
  });
});
