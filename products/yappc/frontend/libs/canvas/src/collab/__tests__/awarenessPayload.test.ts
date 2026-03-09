/**
 * Tests for Awareness Payload Management
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  AwarenessManager,
  createAwarenessManager,
  validatePresence,
  validatePayload,
  type PresenceState,
  type AwarenessPayload,
  type AwarenessUpdate,
} from '../awarenessPayload';

describe('AwarenessManager', () => {
  describe('Presence Management', () => {
    let manager: AwarenessManager;

    beforeEach(() => {
      manager = createAwarenessManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should create manager with default config', () => {
      expect(manager).toBeInstanceOf(AwarenessManager);
      expect(manager.getPresenceCount()).toBe(0);
    });

    it('should set local presence', () => {
      const payload = manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
      });

      expect(payload).toBeDefined();
      expect(payload?.presence.userId).toBe('user1');
      expect(payload?.presence.username).toBe('Alice');
      expect(payload?.presence.color).toBe('#ff0000');
      expect(manager.getPresenceCount()).toBe(1);
    });

    it('should update existing presence', () => {
      manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
      });

      // Wait for throttle
      vi.useFakeTimers();
      vi.advanceTimersByTime(600);

      const updated = manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#00ff00',
        cursor: { x: 100, y: 200 },
      });

      expect(updated?.presence.color).toBe('#00ff00');
      expect(updated?.presence.cursor).toEqual({ x: 100, y: 200 });
      expect(manager.getPresenceCount()).toBe(1);

      vi.useRealTimers();
    });

    it('should throttle presence updates', () => {
      manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
      });

      // Immediate update should be throttled
      const throttled = manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#00ff00',
      });

      expect(throttled).toBeNull();
    });

    it('should get all presence states', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(600);

      manager.setLocalPresence({ userId: 'user2', username: 'Bob', color: '#00ff00' });

      const all = manager.getAllPresence();
      expect(all).toHaveLength(2);
      expect(all.map((p) => p.userId)).toEqual(['user1', 'user2']);

      vi.useRealTimers();
    });

    it('should get specific user presence', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      const presence = manager.getPresence('user1');
      expect(presence?.username).toBe('Alice');

      const missing = manager.getPresence('user999');
      expect(missing).toBeUndefined();
    });

    it('should remove presence', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });
      expect(manager.getPresenceCount()).toBe(1);

      manager.removePresence('user1');
      expect(manager.getPresenceCount()).toBe(0);
    });
  });

  describe('Editing Indicators', () => {
    let manager: AwarenessManager;

    beforeEach(() => {
      manager = createAwarenessManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should set editing indicator', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(200);

      const payload = manager.setEditing('user1', 'node123');

      expect(payload?.presence.editing).toBe('node123');
      expect(manager.isBeingEdited('node123')).toBe(true);

      vi.useRealTimers();
    });

    it('should clear editing indicator', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(200);

      manager.setEditing('user1', 'node123');
      expect(manager.isBeingEdited('node123')).toBe(true);

      vi.advanceTimersByTime(200);

      manager.setEditing('user1', null);
      expect(manager.isBeingEdited('node123')).toBe(false);

      vi.useRealTimers();
    });

    it('should throttle editing updates', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(200);

      manager.setEditing('user1', 'node123');

      // Immediate update should be throttled
      const throttled = manager.setEditing('user1', 'node456');
      expect(throttled).toBeNull();

      vi.useRealTimers();
    });

    it('should get all editing users', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(600);

      manager.setLocalPresence({ userId: 'user2', username: 'Bob', color: '#00ff00' });

      vi.advanceTimersByTime(200);
      manager.setEditing('user1', 'node123');

      vi.advanceTimersByTime(200);
      manager.setEditing('user2', 'node456');

      const editing = manager.getEditingUsers();
      expect(editing).toHaveLength(2);
      expect(editing).toEqual([
        { userId: 'user1', targetId: 'node123' },
        { userId: 'user2', targetId: 'node456' },
      ]);

      vi.useRealTimers();
    });

    it('should return null for non-existent user', () => {
      const payload = manager.setEditing('user999', 'node123');
      expect(payload).toBeNull();
    });
  });

  describe('Remote Payloads', () => {
    let manager: AwarenessManager;

    beforeEach(() => {
      manager = createAwarenessManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should apply remote payload', () => {
      const remotePayload: AwarenessPayload = {
        presence: {
          userId: 'user1',
          username: 'Alice',
          color: '#ff0000',
          lastActivity: Date.now(),
        },
        timestamp: Date.now(),
        size: 100,
        truncated: false,
      };

      manager.applyRemotePayload(remotePayload);

      expect(manager.getPresenceCount()).toBe(1);
      expect(manager.getPresence('user1')?.username).toBe('Alice');
    });

    it('should update existing remote presence', () => {
      const payload1: AwarenessPayload = {
        presence: {
          userId: 'user1',
          username: 'Alice',
          color: '#ff0000',
          lastActivity: Date.now(),
        },
        timestamp: Date.now(),
        size: 100,
        truncated: false,
      };

      manager.applyRemotePayload(payload1);

      const payload2: AwarenessPayload = {
        presence: {
          userId: 'user1',
          username: 'Alice',
          color: '#00ff00',
          cursor: { x: 100, y: 200 },
          lastActivity: Date.now(),
        },
        timestamp: Date.now(),
        size: 120,
        truncated: false,
      };

      manager.applyRemotePayload(payload2);

      expect(manager.getPresenceCount()).toBe(1);
      expect(manager.getPresence('user1')?.color).toBe('#00ff00');
      expect(manager.getPresence('user1')?.cursor).toEqual({ x: 100, y: 200 });
    });
  });

  describe('Payload Size Limits', () => {
    let manager: AwarenessManager;

    beforeEach(() => {
      manager = createAwarenessManager({ maxPayloadSize: 500 });
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should truncate oversized selection', () => {
      const largeSelection = Array.from({ length: 100 }, (_, i) => `node${i}`);

      const payload = manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        selection: largeSelection,
      });

      expect(payload?.truncated).toBe(true);
      expect(payload?.presence.selection?.length).toBeLessThanOrEqual(10);

      const warnings = manager.getWarnings();
      expect(warnings).toHaveLength(1);
      expect(warnings[0].field).toBe('selection');
      expect(warnings[0].originalSize).toBe(100);
      expect(warnings[0].truncatedSize).toBe(10);
    });

    it('should remove viewport if still oversized', () => {
      const largeSelection = Array.from({ length: 100 }, (_, i) => `node${i}`);

      const payload = manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        selection: largeSelection,
        viewport: { x: 0, y: 0, zoom: 1 },
      });

      expect(payload?.truncated).toBe(true);

      const warnings = manager.getWarnings();
      // Should have at least selection truncation warning
      expect(warnings.length).toBeGreaterThan(0);
      expect(warnings.some((w) => w.field === 'selection')).toBe(true);
    });

    it('should not truncate small payloads', () => {
      const payload = manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        cursor: { x: 100, y: 200 },
      });

      expect(payload?.truncated).toBe(false);
    });

    it('should clear warnings', () => {
      const largeSelection = Array.from({ length: 100 }, (_, i) => `node${i}`);

      manager.setLocalPresence({
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        selection: largeSelection,
      });

      expect(manager.getWarnings()).toHaveLength(1);

      manager.clearWarnings();
      expect(manager.getWarnings()).toHaveLength(0);
    });
  });

  describe('Update Subscriptions', () => {
    let manager: AwarenessManager;

    beforeEach(() => {
      manager = createAwarenessManager();
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should notify on presence added', () => {
      const updates: AwarenessUpdate[] = [];
      manager.subscribe((update) => updates.push(update));

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      expect(updates).toHaveLength(1);
      expect(updates[0].added).toEqual(['user1']);
      expect(updates[0].count).toBe(1);
    });

    it('should notify on presence updated', () => {
      const updates: AwarenessUpdate[] = [];
      manager.subscribe((update) => updates.push(update));

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(600);

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#00ff00' });

      expect(updates).toHaveLength(2);
      expect(updates[1].updated).toEqual(['user1']);
      expect(updates[1].added).toHaveLength(0);

      vi.useRealTimers();
    });

    it('should notify on presence removed', () => {
      const updates: AwarenessUpdate[] = [];
      manager.subscribe((update) => updates.push(update));

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });
      manager.removePresence('user1');

      expect(updates).toHaveLength(2);
      expect(updates[1].removed).toEqual(['user1']);
      expect(updates[1].count).toBe(0);
    });

    it('should unsubscribe', () => {
      const updates: AwarenessUpdate[] = [];
      const unsubscribe = manager.subscribe((update) => updates.push(update));

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });
      expect(updates).toHaveLength(1);

      unsubscribe();

      vi.useFakeTimers();
      vi.advanceTimersByTime(600);

      manager.setLocalPresence({ userId: 'user2', username: 'Bob', color: '#00ff00' });
      expect(updates).toHaveLength(1); // No new update

      vi.useRealTimers();
    });

    it('should handle multiple subscribers', () => {
      const updates1: AwarenessUpdate[] = [];
      const updates2: AwarenessUpdate[] = [];

      manager.subscribe((update) => updates1.push(update));
      manager.subscribe((update) => updates2.push(update));

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      expect(updates1).toHaveLength(1);
      expect(updates2).toHaveLength(1);
    });
  });

  describe('Inactivity Cleanup', () => {
    let manager: AwarenessManager;

    beforeEach(() => {
      manager = createAwarenessManager({ inactivityTimeout: 1000 });
    });

    afterEach(() => {
      manager.destroy();
    });

    it('should remove inactive users', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });
      expect(manager.getPresenceCount()).toBe(1);

      // Fast-forward past inactivity timeout
      vi.useFakeTimers();
      vi.advanceTimersByTime(15000); // 15s (cleanup runs every 10s)

      expect(manager.getPresenceCount()).toBe(0);

      vi.useRealTimers();
    });

    it('should not remove active users', () => {
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();

      // Update activity before timeout
      vi.advanceTimersByTime(600);
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.advanceTimersByTime(600);
      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.advanceTimersByTime(15000);

      expect(manager.getPresenceCount()).toBe(1);

      vi.useRealTimers();
    });

    it('should notify on cleanup removal', () => {
      const updates: AwarenessUpdate[] = [];
      manager.subscribe((update) => updates.push(update));

      manager.setLocalPresence({ userId: 'user1', username: 'Alice', color: '#ff0000' });

      vi.useFakeTimers();
      vi.advanceTimersByTime(15000);

      const cleanupUpdate = updates.find((u) => u.removed.length > 0);
      expect(cleanupUpdate).toBeDefined();
      expect(cleanupUpdate?.removed).toEqual(['user1']);

      vi.useRealTimers();
    });
  });

  describe('Validation', () => {
    it('should validate valid presence state', () => {
      const presence: PresenceState = {
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        lastActivity: Date.now(),
      };

      expect(validatePresence(presence)).toBe(true);
    });

    it('should validate presence with optional fields', () => {
      const presence: PresenceState = {
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        cursor: { x: 100, y: 200 },
        selection: ['node1', 'node2'],
        editing: 'node3',
        viewport: { x: 0, y: 0, zoom: 1 },
        lastActivity: Date.now(),
      };

      expect(validatePresence(presence)).toBe(true);
    });

    it('should reject presence with missing required fields', () => {
      expect(validatePresence({})).toBe(false);
      expect(validatePresence({ userId: 'user1' })).toBe(false);
      expect(validatePresence({ userId: 'user1', username: 'Alice' })).toBe(false);
    });

    it('should reject presence with invalid color', () => {
      const presence = {
        userId: 'user1',
        username: 'Alice',
        color: 'red', // Invalid format
        lastActivity: Date.now(),
      };

      expect(validatePresence(presence)).toBe(false);
    });

    it('should reject presence with invalid cursor', () => {
      const presence = {
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        cursor: { x: 'invalid' }, // Invalid type
        lastActivity: Date.now(),
      };

      expect(validatePresence(presence)).toBe(false);
    });

    it('should reject presence with invalid selection', () => {
      const presence = {
        userId: 'user1',
        username: 'Alice',
        color: '#ff0000',
        selection: ['node1', 123], // Mixed types
        lastActivity: Date.now(),
      };

      expect(validatePresence(presence)).toBe(false);
    });

    it('should validate valid payload', () => {
      const payload: AwarenessPayload = {
        presence: {
          userId: 'user1',
          username: 'Alice',
          color: '#ff0000',
          lastActivity: Date.now(),
        },
        timestamp: Date.now(),
        size: 100,
        truncated: false,
      };

      expect(validatePayload(payload)).toBe(true);
    });

    it('should reject payload with invalid presence', () => {
      const payload = {
        presence: { invalid: 'data' },
        timestamp: Date.now(),
        size: 100,
        truncated: false,
      };

      expect(validatePayload(payload)).toBe(false);
    });

    it('should reject payload with missing fields', () => {
      expect(validatePayload({})).toBe(false);
      expect(
        validatePayload({
          presence: {
            userId: 'user1',
            username: 'Alice',
            color: '#ff0000',
            lastActivity: Date.now(),
          },
        })
      ).toBe(false);
    });
  });

  describe('Configuration', () => {
    it('should use custom config', () => {
      const manager = createAwarenessManager({
        maxPayloadSize: 1000,
        inactivityTimeout: 5000,
        editingThrottle: 50,
        presenceThrottle: 200,
        warnOnTruncate: false,
      });

      expect(manager).toBeInstanceOf(AwarenessManager);
      manager.destroy();
    });

    it('should merge with default config', () => {
      const manager = createAwarenessManager({
        maxPayloadSize: 8192,
      });

      // Other config values should be defaults
      expect(manager).toBeInstanceOf(AwarenessManager);
      manager.destroy();
    });
  });
});
