/**
 * Tests for RetentionManager
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import {
  createRetentionManager,
  type RetentionPolicy,
  type RetentionSnapshot,

  RetentionManager} from '../retentionManager';

describe('RetentionManager', () => {
  let manager: RetentionManager;

  beforeEach(() => {
    vi.useFakeTimers();
    manager = createRetentionManager();
  });

  afterEach(() => {
    manager.stopAutoTransitions();
    vi.restoreAllMocks();
  });

  describe('Policy Management', () => {
    it('should have default policy', () => {
      const policies = manager.listPolicies();
      expect(policies.length).toBe(1);
      expect(policies[0].id).toBe('default');
    });

    it('should add policy', () => {
      const policy: RetentionPolicy = {
        id: 'custom-1',
        name: 'Custom Policy',
        hotRetentionDays: 14,
        warmRetentionDays: 60,
        coldRetentionDays: 180,
        totalRetentionDays: 730,
        softDeleteRecoveryDays: 14,
        enableAutoTransition: true,
      };

      manager.addPolicy(policy);

      const retrieved = manager.getPolicy('custom-1');
      expect(retrieved).toBeDefined();
      expect(retrieved!.name).toBe('Custom Policy');
    });

    it('should get policy by ID', () => {
      const policy = manager.getPolicy('default');
      expect(policy).toBeDefined();
      expect(policy!.id).toBe('default');
    });

    it('should return undefined for non-existent policy', () => {
      const policy = manager.getPolicy('non-existent');
      expect(policy).toBeUndefined();
    });

    it('should update policy', () => {
      const updated = manager.updatePolicy('default', {
        hotRetentionDays: 14,
      });

      expect(updated).toBe(true);

      const policy = manager.getPolicy('default');
      expect(policy!.hotRetentionDays).toBe(14);
    });

    it('should return false when updating non-existent policy', () => {
      const updated = manager.updatePolicy('non-existent', {
        hotRetentionDays: 14,
      });

      expect(updated).toBe(false);
    });

    it('should delete custom policy', () => {
      manager.addPolicy({
        id: 'custom-1',
        name: 'Test',
        hotRetentionDays: 7,
        warmRetentionDays: 30,
        coldRetentionDays: 90,
        totalRetentionDays: 365,
        softDeleteRecoveryDays: 7,
        enableAutoTransition: true,
      });

      const deleted = manager.deletePolicy('custom-1');
      expect(deleted).toBe(true);

      const policy = manager.getPolicy('custom-1');
      expect(policy).toBeUndefined();
    });

    it('should not delete default policy', () => {
      const deleted = manager.deletePolicy('default');
      expect(deleted).toBe(false);

      const policy = manager.getPolicy('default');
      expect(policy).toBeDefined();
    });
  });

  describe('Snapshot Registration', () => {
    it('should register snapshot', () => {
      const snapshot = manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      expect(snapshot.tier).toBe('hot');
      expect(snapshot.state).toBe('active');
      expect(snapshot.lastAccessedAt).toBeDefined();
    });

    it('should get registered snapshot', () => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot).toBeDefined();
      expect(snapshot!.id).toBe('snap-1');
    });

    it('should update last accessed time on get', () => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      const firstAccess = manager.getSnapshot('snap-1')!.lastAccessedAt!;

      vi.advanceTimersByTime(1000);

      const secondAccess = manager.getSnapshot('snap-1')!.lastAccessedAt!;
      expect(secondAccess).toBeGreaterThan(firstAccess);
    });

    it('should return undefined for non-existent snapshot', () => {
      const snapshot = manager.getSnapshot('non-existent');
      expect(snapshot).toBeUndefined();
    });
  });

  describe('Snapshot Listing and Filtering', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
        tags: ['daily'],
      });

      vi.advanceTimersByTime(1000);

      manager.registerSnapshot({
        id: 'snap-2',
        createdAt: Date.now(),
        size: 2048,
        type: 'diff',
        tags: ['hourly'],
      });

      manager.transitionTier('snap-1', 'warm', 'Test');
    });

    it('should list all snapshots', () => {
      const snapshots = manager.listSnapshots();
      expect(snapshots.length).toBe(2);
    });

    it('should list snapshots sorted by creation date descending', () => {
      const snapshots = manager.listSnapshots();
      expect(snapshots[0].id).toBe('snap-2');
      expect(snapshots[1].id).toBe('snap-1');
    });

    it('should filter by tier', () => {
      const warmSnapshots = manager.listSnapshots({ tier: 'warm' });
      expect(warmSnapshots.length).toBe(1);
      expect(warmSnapshots[0].id).toBe('snap-1');
    });

    it('should filter by state', () => {
      manager.softDelete('snap-1');

      const activeSnapshots = manager.listSnapshots({ state: 'active' });
      expect(activeSnapshots.length).toBe(1);
      expect(activeSnapshots[0].id).toBe('snap-2');
    });

    it('should filter by tags', () => {
      const dailySnapshots = manager.listSnapshots({ tags: ['daily'] });
      expect(dailySnapshots.length).toBe(1);
      expect(dailySnapshots[0].id).toBe('snap-1');
    });

    it('should filter by age (olderThan)', () => {
      const now = Date.now();
      const snapshots = manager.listSnapshots();
      
      const cutoff = snapshots[0].createdAt - 100;
      const oldSnapshots = manager.listSnapshots({ olderThan: cutoff });
      expect(oldSnapshots.length).toBe(1);
      expect(oldSnapshots[0].id).toBe('snap-1');
    });

    it('should filter by age (newerThan)', () => {
      const now = Date.now();
      const snapshots = manager.listSnapshots();
      
      const cutoff = snapshots[1].createdAt + 100;
      const newSnapshots = manager.listSnapshots({ newerThan: cutoff });
      expect(newSnapshots.length).toBe(1);
      expect(newSnapshots[0].id).toBe('snap-2');
    });
  });

  describe('Soft Delete and Restore', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });
    });

    it('should soft delete snapshot', () => {
      const deleted = manager.softDelete('snap-1');
      expect(deleted).toBe(true);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.state).toBe('soft_deleted');
      expect(snapshot!.softDeletedAt).toBeDefined();
    });

    it('should not soft delete non-existent snapshot', () => {
      const deleted = manager.softDelete('non-existent');
      expect(deleted).toBe(false);
    });

    it('should not soft delete already deleted snapshot', () => {
      manager.softDelete('snap-1');

      const deleted = manager.softDelete('snap-1');
      expect(deleted).toBe(false);
    });

    it('should not soft delete snapshot with dependents', () => {
      manager.registerSnapshot({
        id: 'snap-2',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
        hasDependents: true,
      });

      const deleted = manager.softDelete('snap-2');
      expect(deleted).toBe(false);
    });

    it('should restore soft deleted snapshot within recovery window', () => {
      manager.softDelete('snap-1');

      const restored = manager.restore('snap-1');
      expect(restored).toBe(true);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.state).toBe('active');
      expect(snapshot!.softDeletedAt).toBeUndefined();
    });

    it('should not restore snapshot outside recovery window', () => {
      manager.softDelete('snap-1');

      // Advance past recovery window (7 days default)
      vi.advanceTimersByTime(8 * 24 * 60 * 60 * 1000);

      const restored = manager.restore('snap-1');
      expect(restored).toBe(false);
    });

    it('should not restore non-soft-deleted snapshot', () => {
      const restored = manager.restore('snap-1');
      expect(restored).toBe(false);
    });
  });

  describe('Permanent Deletion', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });
    });

    it('should permanently delete soft deleted snapshot', () => {
      manager.softDelete('snap-1');

      const deleted = manager.permanentDelete('snap-1');
      expect(deleted).toBe(true);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot).toBeUndefined();
    });

    it('should permanently delete archived snapshot', () => {
      manager.archive('snap-1');

      const deleted = manager.permanentDelete('snap-1');
      expect(deleted).toBe(true);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot).toBeUndefined();
    });

    it('should not permanently delete active snapshot', () => {
      const deleted = manager.permanentDelete('snap-1');
      expect(deleted).toBe(false);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot).toBeDefined();
    });

    it('should not permanently delete snapshot with dependents', () => {
      manager.registerSnapshot({
        id: 'snap-2',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
        hasDependents: true,
      });

      manager.softDelete('snap-2');

      const deleted = manager.permanentDelete('snap-2');
      expect(deleted).toBe(false);
    });

    it('should return false for non-existent snapshot', () => {
      const deleted = manager.permanentDelete('non-existent');
      expect(deleted).toBe(false);
    });
  });

  describe('Tier Transitions', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });
    });

    it('should transition to different tier', () => {
      const transitioned = manager.transitionTier('snap-1', 'warm', 'Test');
      expect(transitioned).toBe(true);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.tier).toBe('warm');
    });

    it('should not transition to same tier', () => {
      const transitioned = manager.transitionTier('snap-1', 'hot', 'Test');
      expect(transitioned).toBe(false);
    });

    it('should not transition non-active snapshot', () => {
      manager.softDelete('snap-1');

      const transitioned = manager.transitionTier('snap-1', 'warm', 'Test');
      expect(transitioned).toBe(false);
    });

    it('should not transition non-existent snapshot', () => {
      const transitioned = manager.transitionTier('non-existent', 'warm', 'Test');
      expect(transitioned).toBe(false);
    });

    it('should record transition history', () => {
      manager.transitionTier('snap-1', 'warm', 'Test transition');

      const history = manager.getTransitionHistory();
      expect(history.length).toBe(1);
      expect(history[0].snapshotId).toBe('snap-1');
      expect(history[0].fromTier).toBe('hot');
      expect(history[0].toTier).toBe('warm');
      expect(history[0].reason).toBe('Test transition');
    });
  });

  describe('Archival', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });
    });

    it('should archive snapshot', () => {
      const archived = manager.archive('snap-1');
      expect(archived).toBe(true);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.state).toBe('archived');
      expect(snapshot!.tier).toBe('archived');
      expect(snapshot!.archivedAt).toBeDefined();
    });

    it('should not archive non-active snapshot', () => {
      manager.softDelete('snap-1');

      const archived = manager.archive('snap-1');
      expect(archived).toBe(false);
    });

    it('should not archive non-existent snapshot', () => {
      const archived = manager.archive('non-existent');
      expect(archived).toBe(false);
    });
  });

  describe('Automatic Tier Transitions', () => {
    it('should transition hot to warm after retention period', () => {
      const sevenDaysAgo = Date.now() - (8 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: sevenDaysAgo,
        size: 1024,
        type: 'full',
      });

      const result = manager.runAutoTransitions();

      expect(result.transitioned).toContain('snap-1');

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.tier).toBe('warm');
    });

    it('should transition warm to cold after retention period', () => {
      const ninetyOneDaysAgo = Date.now() - (91 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: ninetyOneDaysAgo,
        size: 1024,
        type: 'full',
      });

      // First transition to warm
      manager.transitionTier('snap-1', 'warm', 'Manual');

      const result = manager.runAutoTransitions();

      expect(result.transitioned).toContain('snap-1');

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.tier).toBe('cold');
    });

    it('should archive cold snapshots after retention period', () => {
      const ninetyOneDaysAgo = Date.now() - (91 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: ninetyOneDaysAgo,
        size: 1024,
        type: 'full',
      });

      // Transition to cold
      manager.transitionTier('snap-1', 'cold', 'Manual');

      const result = manager.runAutoTransitions();

      expect(result.archived).toContain('snap-1');

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.state).toBe('archived');
    });

    it('should delete soft deleted snapshots past recovery window', () => {
      const eightDaysAgo = Date.now() - (8 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      manager.softDelete('snap-1');

      // Manually set soft delete time
      const snapshot = manager.getSnapshot('snap-1');
      snapshot!.softDeletedAt = eightDaysAgo;

      const result = manager.runAutoTransitions();

      expect(result.deleted).toContain('snap-1');
      expect(manager.getSnapshot('snap-1')).toBeUndefined();
    });

    it('should not transition when auto-transition disabled', () => {
      manager.updatePolicy('default', { enableAutoTransition: false });

      const sevenDaysAgo = Date.now() - (8 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: sevenDaysAgo,
        size: 1024,
        type: 'full',
      });

      const result = manager.runAutoTransitions();

      expect(result.transitioned.length).toBe(0);
    });

    it('should respect minimum snapshots requirement', () => {
      manager.updatePolicy('default', { minSnapshots: 2 });

      // Register only 1 snapshot
      const oldDate = Date.now() - (400 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: oldDate,
        size: 1024,
        type: 'full',
      });

      manager.archive('snap-1');

      const result = manager.runAutoTransitions();

      // Should not delete because it would go below minSnapshots
      expect(result.deleted.length).toBe(0);
    });
  });

  describe('Automatic Transition Monitoring', () => {
    it('should start auto transitions', () => {
      manager.startAutoTransitions(1000);

      expect(() => manager.stopAutoTransitions()).not.toThrow();
    });

    it('should not start if already running', () => {
      manager.startAutoTransitions(1000);
      manager.startAutoTransitions(1000); // Second call should be no-op

      manager.stopAutoTransitions();
    });

    it('should stop auto transitions', () => {
      manager.startAutoTransitions(1000);
      manager.stopAutoTransitions();

      expect(() => manager.stopAutoTransitions()).not.toThrow();
    });
  });

  describe('Transition History', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      manager.registerSnapshot({
        id: 'snap-2',
        createdAt: Date.now(),
        size: 2048,
        type: 'full',
      });
    });

    it('should get all transitions', () => {
      manager.transitionTier('snap-1', 'warm', 'Test 1');
      manager.transitionTier('snap-2', 'cold', 'Test 2');

      const history = manager.getTransitionHistory();
      expect(history.length).toBe(2);
    });

    it('should filter by snapshot ID', () => {
      manager.transitionTier('snap-1', 'warm', 'Test 1');
      manager.transitionTier('snap-2', 'cold', 'Test 2');

      const history = manager.getTransitionHistory({ snapshotId: 'snap-1' });
      expect(history.length).toBe(1);
      expect(history[0].snapshotId).toBe('snap-1');
    });

    it('should filter by date range', () => {
      const now = Date.now();

      manager.transitionTier('snap-1', 'warm', 'Test 1');

      vi.advanceTimersByTime(2000);

      manager.transitionTier('snap-2', 'cold', 'Test 2');

      const history = manager.getTransitionHistory({
        startDate: now + 1000,
      });

      expect(history.length).toBe(1);
      expect(history[0].snapshotId).toBe('snap-2');
    });

    it('should return transitions sorted by timestamp descending', () => {
      manager.transitionTier('snap-1', 'warm', 'First');

      vi.advanceTimersByTime(1000);

      manager.transitionTier('snap-2', 'cold', 'Second');

      const history = manager.getTransitionHistory();
      expect(history[0].reason).toBe('Second');
      expect(history[1].reason).toBe('First');
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      manager.registerSnapshot({
        id: 'snap-2',
        createdAt: Date.now(),
        size: 2048,
        type: 'full',
      });

      manager.registerSnapshot({
        id: 'snap-3',
        createdAt: Date.now(),
        size: 4096,
        type: 'full',
      });
    });

    it('should count total snapshots', () => {
      const stats = manager.getStatistics();
      expect(stats.totalSnapshots).toBe(3);
    });

    it('should count by state', () => {
      manager.softDelete('snap-1');
      manager.archive('snap-2');

      const stats = manager.getStatistics();
      expect(stats.activeSnapshots).toBe(1);
      expect(stats.softDeletedSnapshots).toBe(1);
      expect(stats.archivedSnapshots).toBe(1);
    });

    it('should calculate tier statistics', () => {
      manager.transitionTier('snap-1', 'warm', 'Test');
      manager.transitionTier('snap-2', 'cold', 'Test');

      const stats = manager.getStatistics();

      const hotStats = stats.byTier.find(t => t.tier === 'hot');
      const warmStats = stats.byTier.find(t => t.tier === 'warm');
      const coldStats = stats.byTier.find(t => t.tier === 'cold');

      expect(hotStats!.count).toBe(1);
      expect(warmStats!.count).toBe(1);
      expect(coldStats!.count).toBe(1);
    });

    it('should calculate total storage', () => {
      const stats = manager.getStatistics();
      expect(stats.totalStorage).toBe(1024 + 2048 + 4096);
    });

    it('should track recent transitions', () => {
      manager.transitionTier('snap-1', 'warm', 'Test');
      manager.transitionTier('snap-2', 'cold', 'Test');

      const stats = manager.getStatistics();
      expect(stats.recentTransitions).toBe(2);
    });

    it('should count pending deletions', () => {
      const eightDaysAgo = Date.now() - (8 * 24 * 60 * 60 * 1000);

      manager.softDelete('snap-1');

      // Manually set soft delete time
      const snapshot = manager.getSnapshot('snap-1');
      snapshot!.softDeletedAt = eightDaysAgo;

      const stats = manager.getStatistics();
      expect(stats.pendingDeletions).toBe(1);
    });
  });

  describe('Policy Matching', () => {
    it('should use default policy for untagged snapshots', () => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now() - (8 * 24 * 60 * 60 * 1000),
        size: 1024,
        type: 'full',
      });

      const result = manager.runAutoTransitions();

      // Should use default 7-day hot retention
      expect(result.transitioned).toContain('snap-1');
    });

    it('should use matching policy for tagged snapshots', () => {
      manager.addPolicy({
        id: 'custom',
        name: 'Custom',
        hotRetentionDays: 30,
        warmRetentionDays: 60,
        coldRetentionDays: 90,
        totalRetentionDays: 365,
        softDeleteRecoveryDays: 7,
        enableAutoTransition: true,
        tags: ['important'],
      });

      const eightDaysAgo = Date.now() - (8 * 24 * 60 * 60 * 1000);

      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: eightDaysAgo,
        size: 1024,
        type: 'full',
        tags: ['important'],
      });

      const result = manager.runAutoTransitions();

      // Should use custom 30-day hot retention, so no transition yet
      expect(result.transitioned.length).toBe(0);

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.tier).toBe('hot');
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty manager', () => {
      const stats = manager.getStatistics();
      expect(stats.totalSnapshots).toBe(0);
      expect(stats.totalStorage).toBe(0);
    });

    it('should handle snapshot without tags', () => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      const snapshot = manager.getSnapshot('snap-1');
      expect(snapshot!.tags).toBeUndefined();
    });

    it('should handle transition history limit', () => {
      manager.registerSnapshot({
        id: 'snap-1',
        createdAt: Date.now(),
        size: 1024,
        type: 'full',
      });

      // Create more than 1000 transitions
      for (let i = 0; i < 1100; i++) {
        manager.transitionTier('snap-1', i % 2 === 0 ? 'warm' : 'hot', 'Test');
      }

      const history = manager.getTransitionHistory();
      expect(history.length).toBeLessThanOrEqual(1000);
    });

    it('should handle auto transitions with no snapshots', () => {
      const result = manager.runAutoTransitions();

      expect(result.transitioned).toEqual([]);
      expect(result.archived).toEqual([]);
      expect(result.deleted).toEqual([]);
    });
  });
});
