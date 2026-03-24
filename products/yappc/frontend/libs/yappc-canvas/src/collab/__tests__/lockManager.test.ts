/**
 * Tests for Advisory Lock Manager
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  createLockManager,
  validateLockRequest,
  type LockRequest,
  type LockEvent,

  LockManager} from '../lockManager';

describe('LockManager', () => {
  describe('Lock Acquisition', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should acquire lock on available resource', () => {
      const request: LockRequest = {
        userId: 'user1',
        username: 'Alice',
        reason: 'Editing node',
      };

      const result = manager.acquire('node1', request);

      expect(result.success).toBe(true);
      expect(result.lock?.status).toBe('locked');
      expect(result.lock?.holder?.userId).toBe('user1');
      expect(result.event?.type).toBe('acquired');
    });

    it('should fail to acquire already locked resource', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.acquire('node1', { userId: 'user2', username: 'Bob' });

      expect(result.success).toBe(false);
      expect(result.error).toContain('locked by Alice');
    });

    it('should respect custom timeout', () => {
      const request: LockRequest = {
        userId: 'user1',
        username: 'Alice',
        timeout: 60000, // 1 minute
      };

      const result = manager.acquire('node1', request);

      expect(result.success).toBe(true);
      expect(result.lock?.holder?.expiresAt).toBeGreaterThan(Date.now() + 50000);
    });

    it('should reject timeout exceeding maximum', () => {
      const request: LockRequest = {
        userId: 'user1',
        username: 'Alice',
        timeout: 60 * 60 * 1000, // 1 hour (exceeds 30 min max)
      };

      const result = manager.acquire('node1', request);

      expect(result.success).toBe(false);
      expect(result.error).toContain('exceeds maximum');
    });

    it('should include reason in lock holder', () => {
      const request: LockRequest = {
        userId: 'user1',
        username: 'Alice',
        reason: 'Critical update',
      };

      const result = manager.acquire('node1', request);

      expect(result.lock?.holder?.reason).toBe('Critical update');
    });

    it('should set lock expiration', () => {
      const result = manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      expect(result.lock?.holder?.acquiredAt).toBeDefined();
      expect(result.lock?.holder?.expiresAt).toBeGreaterThan(Date.now());
    });
  });

  describe('Lock Release', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should release held lock', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.release('node1', 'user1');

      expect(result.success).toBe(true);
      expect(result.event?.type).toBe('released');
      expect(manager.isLocked('node1')).toBe(false);
    });

    it('should fail to release unlocked resource', () => {
      const result = manager.release('node1', 'user1');

      expect(result.success).toBe(false);
      expect(result.error).toContain('not locked');
    });

    it('should fail to release lock held by another user', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.release('node1', 'user2');

      expect(result.success).toBe(false);
      expect(result.error).toContain('lock holder or admin');
    });

    it('should allow admin to release any lock', () => {
      manager.addAdmin('admin1');
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.release('node1', 'admin1');

      expect(result.success).toBe(true);
      expect(manager.isLocked('node1')).toBe(false);
    });
  });

  describe('Lock Timeout', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager({ defaultTimeout: 100 }); // 100ms for testing
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should auto-release lock after timeout', async () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      expect(manager.isLocked('node1')).toBe(true);

      // Wait for timeout
      await new Promise((resolve) => setTimeout(resolve, 150));

      expect(manager.isLocked('node1')).toBe(false);
    });

    it('should emit timeout event', async () => {
      const events: LockEvent[] = [];
      manager.subscribe((event) => events.push(event));

      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      // Wait for timeout
      await new Promise((resolve) => setTimeout(resolve, 150));

      const timeoutEvent = events.find((e) => e.type === 'timeout');
      expect(timeoutEvent).toBeDefined();
      expect(timeoutEvent?.resourceId).toBe('node1');
    });

    it('should allow re-acquisition after timeout', async () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      // Wait for timeout
      await new Promise((resolve) => setTimeout(resolve, 150));

      const result = manager.acquire('node1', { userId: 'user2', username: 'Bob' });
      expect(result.success).toBe(true);
    });
  });

  describe('Admin Override', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
      manager.addAdmin('admin1');
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should request override as admin', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.requestOverride('node1', {
        userId: 'admin1',
        username: 'Admin',
        reason: 'Emergency fix',
      });

      expect(result.success).toBe(true);
      expect(result.lock?.status).toBe('pending_override');
      expect(result.lock?.overrideRequest?.userId).toBe('admin1');
      expect(result.event?.type).toBe('override_requested');
    });

    it('should fail to request override as non-admin', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.requestOverride('node1', {
        userId: 'user2',
        username: 'Bob',
      });

      expect(result.success).toBe(false);
      expect(result.error).toContain('Only admins');
    });

    it('should grant override and transfer lock', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.requestOverride('node1', { userId: 'admin1', username: 'Admin' });

      const result = manager.grantOverride('node1', 'admin1');

      expect(result.success).toBe(true);
      expect(result.lock?.status).toBe('locked');
      expect(result.lock?.holder?.userId).toBe('admin1');
      expect(result.event?.type).toBe('override_granted');
      expect(result.event?.previousHolder?.userId).toBe('user1');
    });

    it('should deny override and restore lock', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.requestOverride('node1', { userId: 'admin1', username: 'Admin' });

      const result = manager.denyOverride('node1', 'admin1');

      expect(result.success).toBe(true);
      expect(result.lock?.status).toBe('locked');
      expect(result.lock?.holder?.userId).toBe('user1');
      expect(result.lock?.overrideRequest).toBeUndefined();
      expect(result.event?.type).toBe('override_denied');
    });

    it('should fail to grant override without pending request', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.grantOverride('node1', 'admin1');

      expect(result.success).toBe(false);
      expect(result.error).toContain('No pending override');
    });

    it('should notify previous holder on override request', () => {
      const events: LockEvent[] = [];
      manager.subscribe((event) => events.push(event));

      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.requestOverride('node1', { userId: 'admin1', username: 'Admin' });

      const overrideEvent = events.find((e) => e.type === 'override_requested');
      expect(overrideEvent?.previousHolder?.userId).toBe('user1');
    });

    it('should be disabled if config disables it', () => {
      const mgr = createLockManager({ enableAdminOverride: false });
      mgr.addAdmin('admin1');

      mgr.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = mgr.requestOverride('node1', { userId: 'admin1', username: 'Admin' });

      expect(result.success).toBe(false);
      expect(result.error).toContain('not enabled');

      mgr.destroy();
    });
  });

  describe('Lock Queries', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should get lock status', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const lock = manager.getLock('node1');

      expect(lock).toBeDefined();
      expect(lock?.status).toBe('locked');
      expect(lock?.holder?.userId).toBe('user1');
    });

    it('should return undefined for non-existent lock', () => {
      const lock = manager.getLock('node999');
      expect(lock).toBeUndefined();
    });

    it('should check if resource is locked', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      expect(manager.isLocked('node1')).toBe(true);
      expect(manager.isLocked('node2')).toBe(false);
    });

    it('should get all locks', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.acquire('node2', { userId: 'user2', username: 'Bob' });

      const locks = manager.getAllLocks();

      expect(locks).toHaveLength(2);
      expect(locks.map((l) => l.resourceId)).toContain('node1');
      expect(locks.map((l) => l.resourceId)).toContain('node2');
    });

    it('should get locks by user', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.acquire('node2', { userId: 'user1', username: 'Alice' });
      manager.acquire('node3', { userId: 'user2', username: 'Bob' });

      const user1Locks = manager.getLocksByUser('user1');

      expect(user1Locks).toHaveLength(2);
      expect(user1Locks.every((l) => l.holder?.userId === 'user1')).toBe(true);
    });
  });

  describe('Lock Extension', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should extend lock timeout', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const lock = manager.getLock('node1');
      const originalExpiry = lock?.holder?.expiresAt;

      const result = manager.extend('node1', 'user1', 60000);

      expect(result.success).toBe(true);
      expect(result.lock?.holder?.expiresAt).toBeGreaterThan(originalExpiry!);
    });

    it('should fail to extend lock held by another user', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.extend('node1', 'user2', 60000);

      expect(result.success).toBe(false);
      expect(result.error).toContain('lock holder or admin');
    });

    it('should allow admin to extend any lock', () => {
      manager.addAdmin('admin1');
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = manager.extend('node1', 'admin1', 60000);

      expect(result.success).toBe(true);
    });

    it('should respect maximum timeout on extension', () => {
      const mgr = createLockManager({ defaultTimeout: 1000, maxTimeout: 5000 });

      mgr.acquire('node1', { userId: 'user1', username: 'Alice' });

      const result = mgr.extend('node1', 'user1', 10000); // Try to extend beyond max

      expect(result.success).toBe(true);
      const lock = mgr.getLock('node1');
      expect(lock?.holder?.expiresAt).toBeLessThanOrEqual(Date.now() + 5000);

      mgr.destroy();
    });
  });

  describe('Event Subscriptions', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should notify on lock acquisition', () => {
      const events: LockEvent[] = [];
      manager.subscribe((event) => events.push(event));

      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      expect(events).toHaveLength(1);
      expect(events[0].type).toBe('acquired');
      expect(events[0].userId).toBe('user1');
    });

    it('should notify on lock release', () => {
      const events: LockEvent[] = [];
      manager.subscribe((event) => events.push(event));

      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.release('node1', 'user1');

      expect(events).toHaveLength(2);
      expect(events[1].type).toBe('released');
    });

    it('should unsubscribe', () => {
      const events: LockEvent[] = [];
      const unsubscribe = manager.subscribe((event) => events.push(event));

      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      expect(events).toHaveLength(1);

      unsubscribe();

      manager.acquire('node2', { userId: 'user2', username: 'Bob' });
      expect(events).toHaveLength(1); // No new event
    });

    it('should handle multiple subscribers', () => {
      const events1: LockEvent[] = [];
      const events2: LockEvent[] = [];

      manager.subscribe((event) => events1.push(event));
      manager.subscribe((event) => events2.push(event));

      manager.acquire('node1', { userId: 'user1', username: 'Alice' });

      expect(events1).toHaveLength(1);
      expect(events2).toHaveLength(1);
    });
  });

  describe('Admin Management', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should add admin', () => {
      manager.addAdmin('admin1');

      expect(manager.isAdmin('admin1')).toBe(true);
    });

    it('should remove admin', () => {
      manager.addAdmin('admin1');
      manager.removeAdmin('admin1');

      expect(manager.isAdmin('admin1')).toBe(false);
    });

    it('should check admin status', () => {
      manager.addAdmin('admin1');

      expect(manager.isAdmin('admin1')).toBe(true);
      expect(manager.isAdmin('user1')).toBe(false);
    });

    it('should initialize with admin list', () => {
      const mgr = createLockManager({
        adminUserIds: new Set(['admin1', 'admin2']),
      });

      expect(mgr.isAdmin('admin1')).toBe(true);
      expect(mgr.isAdmin('admin2')).toBe(true);

      mgr.destroy();
    });
  });

  describe('Configuration', () => {
    it('should use custom config', () => {
      const manager = createLockManager({
        defaultTimeout: 120000,
        maxTimeout: 600000,
        idleTimeout: 300000,
      });

      const config = manager.getConfig();

      expect(config.defaultTimeout).toBe(120000);
      expect(config.maxTimeout).toBe(600000);
      expect(config.idleTimeout).toBe(300000);

      manager.destroy();
    });

    it('should merge with default config', () => {
      const manager = createLockManager({
        defaultTimeout: 120000,
      });

      const config = manager.getConfig();

      expect(config.defaultTimeout).toBe(120000);
      expect(config.maxTimeout).toBe(30 * 60 * 1000); // Default
      expect(config.enableAdminOverride).toBe(true); // Default

      manager.destroy();
    });
  });

  describe('Validation', () => {
    it('should validate valid lock request', () => {
      const request: LockRequest = {
        userId: 'user1',
        username: 'Alice',
        timeout: 60000,
        reason: 'Editing',
      };

      expect(validateLockRequest(request)).toBe(true);
    });

    it('should validate minimal lock request', () => {
      const request: LockRequest = {
        userId: 'user1',
        username: 'Alice',
      };

      expect(validateLockRequest(request)).toBe(true);
    });

    it('should reject invalid lock request', () => {
      expect(validateLockRequest(null)).toBe(false);
      expect(validateLockRequest({})).toBe(false);
      expect(validateLockRequest({ userId: 'user1' })).toBe(false);
      expect(validateLockRequest({ userId: 'user1', username: 'Alice', timeout: -1 })).toBe(false);
    });
  });

  describe('Edge Cases', () => {
    let manager: LockManager;

    beforeEach(() => {
      manager = createLockManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should handle expired lock on isLocked check', async () => {
      const mgr = createLockManager({ defaultTimeout: 50 });

      mgr.acquire('node1', { userId: 'user1', username: 'Alice' });

      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(mgr.isLocked('node1')).toBe(false);

      mgr.destroy();
    });

    it('should handle multiple locks by same user', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.acquire('node2', { userId: 'user1', username: 'Alice' });
      manager.acquire('node3', { userId: 'user1', username: 'Alice' });

      const locks = manager.getLocksByUser('user1');
      expect(locks).toHaveLength(3);
    });

    it('should handle concurrent lock attempts', () => {
      const result1 = manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      const result2 = manager.acquire('node1', { userId: 'user2', username: 'Bob' });

      expect(result1.success).toBe(true);
      expect(result2.success).toBe(false);
    });

    it('should cleanup on destroy', () => {
      manager.acquire('node1', { userId: 'user1', username: 'Alice' });
      manager.acquire('node2', { userId: 'user2', username: 'Bob' });

      manager.destroy();

      expect(manager.getAllLocks()).toHaveLength(0);
    });
  });
});
