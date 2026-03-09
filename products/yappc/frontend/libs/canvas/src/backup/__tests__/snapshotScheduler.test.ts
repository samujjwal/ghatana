/**
 * Tests for SnapshotScheduler
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import {
  createSnapshotScheduler,
  type Snapshot,

  SnapshotScheduler} from '../snapshotScheduler';

describe('SnapshotScheduler', () => {
  let scheduler: SnapshotScheduler;

  beforeEach(() => {
    vi.useFakeTimers();
    scheduler = createSnapshotScheduler({
      enabled: false, // Start disabled for most tests
      enableChecksums: true,
    });
  });

  afterEach(() => {
    scheduler.stop();
    vi.restoreAllMocks();
  });

  describe('Snapshot Creation', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should create full snapshot', () => {
      const snapshot = scheduler.createSnapshot('full');

      expect(snapshot.metadata.type).toBe('full');
      expect(snapshot.metadata.id).toBeDefined();
      expect(snapshot.metadata.timestamp).toBeGreaterThan(0);
      expect(snapshot.data).toBeDefined();
      expect(snapshot.metadata.checksum).toBeDefined();
    });

    it('should create diff snapshot with parent', () => {
      // Create full backup first
      const full = scheduler.createSnapshot('full');

      // Update state
      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1', label: 'Test' }],
        edges: [],
      }));

      // Create diff
      const diff = scheduler.createSnapshot('diff');

      expect(diff.metadata.type).toBe('diff');
      expect(diff.metadata.parentId).toBe(full.metadata.id);
      expect(diff.diff).toBeDefined();
    });

    it('should create full snapshot if no parent for diff', () => {
      // Try to create diff without full backup
      const snapshot = scheduler.createSnapshot('diff');

      expect(snapshot.metadata.type).toBe('full');
      expect(snapshot.metadata.parentId).toBeUndefined();
    });

    it('should include optional metadata', () => {
      const snapshot = scheduler.createSnapshot('full', {
        description: 'Manual backup',
        createdBy: 'user-123',
        tags: ['manual', 'before-deploy'],
      });

      expect(snapshot.metadata.description).toBe('Manual backup');
      expect(snapshot.metadata.createdBy).toBe('user-123');
      expect(snapshot.metadata.tags).toEqual(['manual', 'before-deploy']);
    });

    it('should calculate snapshot size', () => {
      const data = JSON.stringify({ nodes: [], edges: [] });
      scheduler.updateState(data);

      const snapshot = scheduler.createSnapshot('full');
      expect(snapshot.metadata.size).toBeGreaterThan(0);
    });
  });

  describe('Snapshot Retrieval', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should get snapshot by ID', () => {
      const created = scheduler.createSnapshot('full');
      const retrieved = scheduler.getSnapshot(created.metadata.id);

      expect(retrieved).toBeDefined();
      expect(retrieved!.metadata.id).toBe(created.metadata.id);
    });

    it('should return undefined for non-existent snapshot', () => {
      const snapshot = scheduler.getSnapshot('non-existent');
      expect(snapshot).toBeUndefined();
    });

    it('should list all snapshots', () => {
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');

      const snapshots = scheduler.listSnapshots();
      expect(snapshots.length).toBe(2);
    });

    it('should list snapshots sorted by timestamp descending', () => {
      const first = scheduler.createSnapshot('full');
      vi.advanceTimersByTime(1000);
      const second = scheduler.createSnapshot('full');

      const snapshots = scheduler.listSnapshots();
      expect(snapshots[0].metadata.id).toBe(second.metadata.id);
      expect(snapshots[1].metadata.id).toBe(first.metadata.id);
    });
  });

  describe('Snapshot Filtering', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));

      scheduler.createSnapshot('full', { tags: ['daily'], createdBy: 'user-1' });
      vi.advanceTimersByTime(1000);
      scheduler.createSnapshot('full');
      vi.advanceTimersByTime(1000);
      scheduler.createSnapshot('diff', { tags: ['hourly'], createdBy: 'user-2' });
    });

    it('should filter by type', () => {
      const fullSnapshots = scheduler.listSnapshots({ type: 'full' });
      expect(fullSnapshots.length).toBe(2);
      expect(fullSnapshots.every(s => s.metadata.type === 'full')).toBe(true);
    });

    it('should filter by date range', () => {
      const now = Date.now();
      const snapshots = scheduler.listSnapshots();
      
      const startDate = snapshots[1].metadata.timestamp;
      const endDate = snapshots[0].metadata.timestamp;

      const filtered = scheduler.listSnapshots({ startDate, endDate });
      expect(filtered.length).toBeGreaterThanOrEqual(1);
    });

    it('should filter by tags', () => {
      const filtered = scheduler.listSnapshots({ tags: ['daily'] });
      expect(filtered.length).toBe(1);
      expect(filtered[0].metadata.tags).toContain('daily');
    });

    it('should filter by creator', () => {
      const filtered = scheduler.listSnapshots({ createdBy: 'user-1' });
      expect(filtered.length).toBe(1);
      expect(filtered[0].metadata.createdBy).toBe('user-1');
    });

    it('should filter by verification status', () => {
      const snapshots = scheduler.listSnapshots();
      scheduler.verifySnapshot(snapshots[0].metadata.id);

      const verified = scheduler.listSnapshots({ verified: true });
      const unverified = scheduler.listSnapshots({ verified: false });

      expect(verified.length).toBe(1);
      expect(unverified.length).toBe(2);
    });
  });

  describe('Snapshot Deletion', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should delete snapshot', () => {
      const snapshot = scheduler.createSnapshot('full');

      const deleted = scheduler.deleteSnapshot(snapshot.metadata.id);
      expect(deleted).toBe(true);

      const retrieved = scheduler.getSnapshot(snapshot.metadata.id);
      expect(retrieved).toBeUndefined();
    });

    it('should return false when deleting non-existent snapshot', () => {
      const deleted = scheduler.deleteSnapshot('non-existent');
      expect(deleted).toBe(false);
    });

    it('should not delete snapshot with dependents', () => {
      const full = scheduler.createSnapshot('full');
      scheduler.updateState(JSON.stringify({ nodes: [{ id: '1' }], edges: [] }));
      scheduler.createSnapshot('diff'); // Depends on full

      const deleted = scheduler.deleteSnapshot(full.metadata.id);
      expect(deleted).toBe(false);

      const retrieved = scheduler.getSnapshot(full.metadata.id);
      expect(retrieved).toBeDefined();
    });
  });

  describe('Snapshot Verification', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should verify snapshot with correct checksum', () => {
      const snapshot = scheduler.createSnapshot('full');
      const verified = scheduler.verifySnapshot(snapshot.metadata.id);

      expect(verified).toBe(true);
      expect(snapshot.metadata.verified).toBe(true);
      expect(snapshot.metadata.verifiedAt).toBeDefined();
    });

    it('should return false for non-existent snapshot', () => {
      const verified = scheduler.verifySnapshot('non-existent');
      expect(verified).toBe(false);
    });

    it('should skip verification when checksums disabled', () => {
      scheduler = createSnapshotScheduler({ enabled: false, enableChecksums: false });
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));

      const snapshot = scheduler.createSnapshot('full');
      const verified = scheduler.verifySnapshot(snapshot.metadata.id);

      expect(verified).toBe(true);
      expect(snapshot.metadata.verified).toBe(true);
    });
  });

  describe('Snapshot Restore', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should restore from full snapshot', () => {
      const data = JSON.stringify({ nodes: [{ id: '1' }], edges: [] });
      scheduler.updateState(data);

      const snapshot = scheduler.createSnapshot('full');
      const restored = scheduler.restore(snapshot.metadata.id);

      expect(restored).toBe(data);
    });

    it('should return null for non-existent snapshot', () => {
      const restored = scheduler.restore('non-existent');
      expect(restored).toBeNull();
    });

    it('should restore from diff snapshot', () => {
      const baseData = JSON.stringify({ nodes: [], edges: [] });
      scheduler.updateState(baseData);
      const full = scheduler.createSnapshot('full');

      scheduler.updateState(JSON.stringify({ nodes: [{ id: '1' }], edges: [] }));
      const diff = scheduler.createSnapshot('diff');

      const restored = scheduler.restore(diff.metadata.id);
      expect(restored).toBeDefined();
    });

    it('should verify before restore', () => {
      const snapshot = scheduler.createSnapshot('full');

      const restored = scheduler.restore(snapshot.metadata.id);
      expect(restored).toBeDefined();
      expect(snapshot.metadata.verified).toBe(true);
    });

    it('should return null if verification fails', () => {
      const snapshot = scheduler.createSnapshot('full');

      // Corrupt checksum
      snapshot.metadata.checksum = 'invalid';

      const restored = scheduler.restore(snapshot.metadata.id);
      expect(restored).toBeNull();
    });
  });

  describe('Schedule Management', () => {
    it('should initialize schedules', () => {
      const schedules = scheduler.listSchedules();
      expect(schedules.length).toBe(2);

      const fullSchedule = schedules.find(s => s.type === 'full');
      const diffSchedule = schedules.find(s => s.type === 'diff');

      expect(fullSchedule).toBeDefined();
      expect(diffSchedule).toBeDefined();
    });

    it('should get schedule by ID', () => {
      const schedule = scheduler.getSchedule('full-backup');
      expect(schedule).toBeDefined();
      expect(schedule!.type).toBe('full');
    });

    it('should update schedule', () => {
      const updated = scheduler.updateSchedule('full-backup', {
        interval: 12 * 60 * 60 * 1000,
      });

      expect(updated).toBe(true);

      const schedule = scheduler.getSchedule('full-backup');
      expect(schedule!.interval).toBe(12 * 60 * 60 * 1000);
    });

    it('should return false when updating non-existent schedule', () => {
      const updated = scheduler.updateSchedule('non-existent', { interval: 1000 });
      expect(updated).toBe(false);
    });

    it('should pause schedule', () => {
      const paused = scheduler.pauseSchedule('full-backup');
      expect(paused).toBe(true);

      const schedule = scheduler.getSchedule('full-backup');
      expect(schedule!.active).toBe(false);
    });

    it('should resume schedule', () => {
      scheduler.pauseSchedule('full-backup');

      const resumed = scheduler.resumeSchedule('full-backup');
      expect(resumed).toBe(true);

      const schedule = scheduler.getSchedule('full-backup');
      expect(schedule!.active).toBe(true);
    });
  });

  describe('Automatic Scheduling', () => {
    it('should execute scheduled backups', () => {
      scheduler = createSnapshotScheduler({
        enabled: true,
        fullBackupInterval: 1000,
        diffBackupInterval: 500,
      });

      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
      scheduler.start();

      // Advance time to trigger diff backup
      vi.advanceTimersByTime(500);

      const snapshots = scheduler.listSnapshots();
      expect(snapshots.length).toBeGreaterThan(0);

      scheduler.stop();
    });

    it('should not execute when no state', () => {
      scheduler = createSnapshotScheduler({
        enabled: true,
        fullBackupInterval: 1000,
      });

      scheduler.start();
      vi.advanceTimersByTime(1000);

      const snapshots = scheduler.listSnapshots();
      expect(snapshots.length).toBe(0);

      scheduler.stop();
    });

    it('should notify listeners on backup', () => {
      const listener = vi.fn();
      scheduler.onSnapshot(listener);

      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
      scheduler.createSnapshot('full');

      expect(listener).toHaveBeenCalledTimes(1);
    });

    it('should unsubscribe listener', () => {
      const listener = vi.fn();
      const unsubscribe = scheduler.onSnapshot(listener);

      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
      scheduler.createSnapshot('full');

      unsubscribe();

      scheduler.createSnapshot('full');

      expect(listener).toHaveBeenCalledTimes(1);
    });
  });

  describe('Retention Policy', () => {
    beforeEach(() => {
      scheduler = createSnapshotScheduler({
        enabled: false,
        maxBackups: 3,
      });
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should enforce max backups limit', () => {
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full'); // 4th should trigger cleanup

      const snapshots = scheduler.listSnapshots();
      expect(snapshots.length).toBe(3);
    });

    it('should not delete snapshots with dependents during cleanup', () => {
      const full = scheduler.createSnapshot('full');
      scheduler.updateState(JSON.stringify({ nodes: [{ id: '1' }], edges: [] }));
      scheduler.createSnapshot('diff'); // Depends on full
      
      // Create more to trigger cleanup
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');

      const retrieved = scheduler.getSnapshot(full.metadata.id);
      expect(retrieved).toBeDefined(); // Should still exist
    });

    it('should enforce retention period', () => {
      scheduler = createSnapshotScheduler({
        enabled: false,
        retentionPeriod: 1000,
      });

      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));

      const old = scheduler.createSnapshot('full');
      
      vi.advanceTimersByTime(2000);
      
      scheduler.createSnapshot('full'); // Trigger cleanup

      const retrieved = scheduler.getSnapshot(old.metadata.id);
      expect(retrieved).toBeUndefined();
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
    });

    it('should calculate total snapshots', () => {
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');

      const stats = scheduler.getStatistics();
      expect(stats.totalSnapshots).toBe(2);
    });

    it('should count by type', () => {
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');
      scheduler.updateState(JSON.stringify({ nodes: [{ id: '1' }], edges: [] }));
      scheduler.createSnapshot('diff');

      const stats = scheduler.getStatistics();
      expect(stats.fullBackups).toBe(2);
      expect(stats.diffBackups).toBe(1);
    });

    it('should calculate total size', () => {
      scheduler.createSnapshot('full');
      scheduler.createSnapshot('full');

      const stats = scheduler.getStatistics();
      expect(stats.totalSize).toBeGreaterThan(0);
      expect(stats.averageSize).toBeGreaterThan(0);
    });

    it('should track last backup time', () => {
      const snapshot = scheduler.createSnapshot('full');

      const stats = scheduler.getStatistics();
      expect(stats.lastBackupTime).toBe(snapshot.metadata.timestamp);
    });

    it('should track verification stats', () => {
      const s1 = scheduler.createSnapshot('full');
      const s2 = scheduler.createSnapshot('full');

      scheduler.verifySnapshot(s1.metadata.id);

      const stats = scheduler.getStatistics();
      expect(stats.verifiedSnapshots).toBe(1);
    });
  });

  describe('Configuration', () => {
    it('should get configuration', () => {
      const config = scheduler.getConfig();
      expect(config.enabled).toBe(false);
      expect(config.enableChecksums).toBe(true);
    });

    it('should update configuration', () => {
      scheduler.updateConfig({
        fullBackupInterval: 12 * 60 * 60 * 1000,
        enableCompression: true,
      });

      const config = scheduler.getConfig();
      expect(config.fullBackupInterval).toBe(12 * 60 * 60 * 1000);
      expect(config.enableCompression).toBe(true);
    });

    it('should start scheduling when enabled', () => {
      const startSpy = vi.spyOn(scheduler, 'start');

      scheduler.updateConfig({ enabled: true });

      expect(startSpy).toHaveBeenCalled();
    });

    it('should stop scheduling when disabled', () => {
      scheduler = createSnapshotScheduler({ enabled: true });
      const stopSpy = vi.spyOn(scheduler, 'stop');

      scheduler.updateConfig({ enabled: false });

      expect(stopSpy).toHaveBeenCalled();
    });
  });

  describe('Diff Calculation', () => {
    it('should detect added nodes', () => {
      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));
      const full = scheduler.createSnapshot('full');

      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1', label: 'New' }],
        edges: [],
      }));

      const diff = scheduler.createSnapshot('diff');
      expect(diff.diff!.addedNodes).toContain('node-1');
    });

    it('should detect removed nodes', () => {
      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1', label: 'Test' }],
        edges: [],
      }));
      const full = scheduler.createSnapshot('full');

      scheduler.updateState(JSON.stringify({ nodes: [], edges: [] }));

      const diff = scheduler.createSnapshot('diff');
      expect(diff.diff!.removedNodes).toContain('node-1');
    });

    it('should detect modified nodes', () => {
      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1', label: 'Original' }],
        edges: [],
      }));
      const full = scheduler.createSnapshot('full');

      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1', label: 'Modified' }],
        edges: [],
      }));

      const diff = scheduler.createSnapshot('diff');
      expect(diff.diff!.modifiedNodes).toContain('node-1');
    });

    it('should detect edge changes', () => {
      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1' }, { id: 'node-2' }],
        edges: [],
      }));
      const full = scheduler.createSnapshot('full');

      scheduler.updateState(JSON.stringify({
        nodes: [{ id: 'node-1' }, { id: 'node-2' }],
        edges: [{ source: 'node-1', target: 'node-2' }],
      }));

      const diff = scheduler.createSnapshot('diff');
      expect(diff.diff!.addedEdges).toContain('node-1-node-2');
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty state', () => {
      scheduler.updateState('');
      const snapshot = scheduler.createSnapshot('full');

      expect(snapshot.data).toBe('');
    });

    it('should handle invalid JSON for diff calculation', () => {
      scheduler.updateState('invalid json');
      const full = scheduler.createSnapshot('full');

      scheduler.updateState('other invalid json');
      const diff = scheduler.createSnapshot('diff');

      expect(diff.diff).toBeDefined();
      expect(diff.diff!.addedNodes).toEqual([]);
    });

    it('should handle zero snapshots', () => {
      const stats = scheduler.getStatistics();
      expect(stats.totalSnapshots).toBe(0);
      expect(stats.averageSize).toBe(0);
    });

    it('should handle stop without start', () => {
      expect(() => scheduler.stop()).not.toThrow();
    });
  });
});
