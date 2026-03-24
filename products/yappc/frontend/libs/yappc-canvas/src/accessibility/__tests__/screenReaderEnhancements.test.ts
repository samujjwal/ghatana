/**
 * Feature 2.31: Screen Reader Enhancements - Tests
 *
 * Comprehensive test suite for relationship announcements, collaboration updates,
 * and keyboard shortcut help system.
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createScreenReaderEnhancements,
  announceNodeRelationships,
  announceCollaborativeEdit,
  getKeyboardShortcutHelp,
  announceKeyboardShortcuts,
  announceCustom,
  getNextAnnouncement,
  clearAnnouncementQueue,
  setScreenReaderEnabled,
  updateScreenReaderConfig,
  registerKeyboardShortcut,
  unregisterKeyboardShortcut,
  getAnnouncementStatistics,
  describeNodeRelationships,
  type ScreenReaderEnhancementState,
  type NodeRelationships,
  type CollaborativeEditEvent,
  type KeyboardShortcut,
} from '../screenReaderEnhancements';

describe('Feature 2.31: Screen Reader Enhancements', () => {
  let state: ScreenReaderEnhancementState;

  beforeEach(() => {
    state = createScreenReaderEnhancements({
      enableRelationships: true,
      enableCollaboration: true,
      enableShortcutHelp: true,
      announceDelay: 300,
      maxQueueSize: 10,
      verboseMode: false,
    });
  });

  describe('State Creation', () => {
    it('should create state with default config', () => {
      const defaultState = createScreenReaderEnhancements();

      expect(defaultState.config.enableRelationships).toBe(true);
      expect(defaultState.config.enableCollaboration).toBe(true);
      expect(defaultState.config.enableShortcutHelp).toBe(true);
      expect(defaultState.announcementQueue).toEqual([]);
      expect(defaultState.isEnabled).toBe(true);
    });

    it('should create state with custom config', () => {
      const customState = createScreenReaderEnhancements({
        enableRelationships: false,
        verboseMode: true,
        maxQueueSize: 20,
      });

      expect(customState.config.enableRelationships).toBe(false);
      expect(customState.config.verboseMode).toBe(true);
      expect(customState.config.maxQueueSize).toBe(20);
    });

    it('should include default keyboard shortcuts', () => {
      expect(state.shortcuts.length).toBeGreaterThan(0);
      expect(state.shortcuts.some((s) => s.category === 'navigation')).toBe(
        true
      );
      expect(state.shortcuts.some((s) => s.category === 'edit')).toBe(true);
    });
  });

  describe('Relationship Announcements', () => {
    it('should announce node with incoming connections', () => {
      const relationships: NodeRelationships = {
        incoming: ['node-a', 'node-b'],
        outgoing: [],
        bidirectional: [],
        labels: {
          'node-a': 'Source A',
          'node-b': 'Source B',
        },
      };

      const updated = announceNodeRelationships(
        state,
        'node-1',
        relationships,
        'Target Node'
      );

      expect(updated.announcementQueue).toHaveLength(1);
      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('Target Node');
      expect(announcement.message).toContain('2 incoming connections');
      expect(announcement.message).toContain('Source A');
      expect(announcement.message).toContain('Source B');
      expect(announcement.category).toBe('relationship');
    });

    it('should announce node with outgoing connections', () => {
      const relationships: NodeRelationships = {
        incoming: [],
        outgoing: ['node-x', 'node-y', 'node-z'],
        bidirectional: [],
        labels: {
          'node-x': 'Target X',
          'node-y': 'Target Y',
          'node-z': 'Target Z',
        },
      };

      const updated = announceNodeRelationships(
        state,
        'node-1',
        relationships,
        'Source Node'
      );

      expect(updated.announcementQueue).toHaveLength(1);
      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('3 outgoing connections');
      expect(announcement.message).toContain('Target X');
      expect(announcement.message).toContain('Target Y');
      expect(announcement.message).toContain('Target Z');
    });

    it('should announce bidirectional connections', () => {
      const relationships: NodeRelationships = {
        incoming: [],
        outgoing: [],
        bidirectional: ['node-peer'],
        labels: {
          'node-peer': 'Peer Node',
        },
      };

      const updated = announceNodeRelationships(
        state,
        'node-1',
        relationships,
        'Central Node'
      );

      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('1 bidirectional connection');
      expect(announcement.message).toContain('Peer Node');
    });

    it('should announce node with no connections', () => {
      const relationships: NodeRelationships = {
        incoming: [],
        outgoing: [],
        bidirectional: [],
      };

      const updated = announceNodeRelationships(
        state,
        'node-1',
        relationships,
        'Isolated Node'
      );

      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('no connections');
    });

    it('should not announce if relationships disabled', () => {
      const disabledState = createScreenReaderEnhancements({
        enableRelationships: false,
      });

      const relationships: NodeRelationships = {
        incoming: ['node-a'],
        outgoing: [],
        bidirectional: [],
      };

      const updated = announceNodeRelationships(
        disabledState,
        'node-1',
        relationships
      );

      expect(updated.announcementQueue).toHaveLength(0);
    });

    it('should use node ID if label not provided', () => {
      const relationships: NodeRelationships = {
        incoming: ['node-a'],
        outgoing: [],
        bidirectional: [],
      };

      const updated = announceNodeRelationships(
        state,
        'node-123',
        relationships
      );

      expect(updated.announcementQueue[0].message).toContain('Node node-123');
    });
  });

  describe('Collaborative Edit Announcements', () => {
    it('should announce collaborative creation', () => {
      const event: CollaborativeEditEvent = {
        user: 'Alice',
        action: 'created',
        elementId: 'node-1',
        elementType: 'node',
        elementLabel: 'New Node',
        timestamp: new Date(),
      };

      const updated = announceCollaborativeEdit(state, event);

      expect(updated.announcementQueue).toHaveLength(1);
      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toBe(
        'Alice created a node: New Node'
      );
      expect(announcement.politeness).toBe('polite');
      expect(announcement.category).toBe('collaboration');
    });

    it('should announce collaborative update', () => {
      const event: CollaborativeEditEvent = {
        user: 'Bob',
        action: 'updated',
        elementId: 'edge-1',
        elementType: 'edge',
        elementLabel: 'Connection',
        timestamp: new Date(),
      };

      const updated = announceCollaborativeEdit(state, event);

      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toBe(
        'Bob updated an edge: Connection'
      );
    });

    it('should announce collaborative deletion', () => {
      const event: CollaborativeEditEvent = {
        user: 'Charlie',
        action: 'deleted',
        elementId: 'group-1',
        elementType: 'group',
        timestamp: new Date(),
      };

      const updated = announceCollaborativeEdit(state, event);

      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('Charlie deleted');
      expect(announcement.message).toContain('group-1');
    });

    it('should include details in verbose mode', () => {
      const verboseState = createScreenReaderEnhancements({
        verboseMode: true,
      });

      const event: CollaborativeEditEvent = {
        user: 'Diana',
        action: 'moved',
        elementId: 'node-1',
        elementType: 'node',
        timestamp: new Date(),
        details: {
          from: 'layer-1',
          to: 'layer-2',
        },
      };

      const updated = announceCollaborativeEdit(verboseState, event);

      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('Details:');
      expect(announcement.message).toContain('from:');
      expect(announcement.message).toContain('to:');
    });

    it('should not announce if collaboration disabled', () => {
      const disabledState = createScreenReaderEnhancements({
        enableCollaboration: false,
      });

      const event: CollaborativeEditEvent = {
        user: 'Eve',
        action: 'updated',
        elementId: 'node-1',
        elementType: 'node',
        timestamp: new Date(),
      };

      const updated = announceCollaborativeEdit(disabledState, event);

      expect(updated.announcementQueue).toHaveLength(0);
    });
  });

  describe('Keyboard Shortcut Help', () => {
    it('should get all shortcuts', () => {
      const shortcuts = getKeyboardShortcutHelp(state);

      expect(shortcuts.length).toBeGreaterThan(0);
      expect(shortcuts.every((s) => s.enabled)).toBe(true);
    });

    it('should filter shortcuts by category', () => {
      const navigationShortcuts = getKeyboardShortcutHelp(state, 'navigation');

      expect(navigationShortcuts.every((s) => s.category === 'navigation')).toBe(
        true
      );
      expect(navigationShortcuts.some((s) => s.keys === 'Tab')).toBe(true);
    });

    it('should return empty array if shortcut help disabled', () => {
      const disabledState = createScreenReaderEnhancements({
        enableShortcutHelp: false,
      });

      const shortcuts = getKeyboardShortcutHelp(disabledState);

      expect(shortcuts).toEqual([]);
    });

    it('should announce keyboard shortcuts', () => {
      const updated = announceKeyboardShortcuts(state, 'edit');

      expect(updated.announcementQueue).toHaveLength(1);
      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('Keyboard shortcuts for edit');
      expect(announcement.message).toContain('Delete/Backspace');
      expect(announcement.category).toBe('shortcut');
    });

    it('should announce all shortcuts if no category specified', () => {
      const updated = announceKeyboardShortcuts(state);

      const announcement = updated.announcementQueue[0];
      expect(announcement.message).toContain('all categories');
    });
  });

  describe('Announcement Queue Management', () => {
    it('should enqueue custom announcement', () => {
      const updated = announceCustom(state, 'Custom message', 'assertive');

      expect(updated.announcementQueue).toHaveLength(1);
      expect(updated.announcementQueue[0].message).toBe('Custom message');
      expect(updated.announcementQueue[0].politeness).toBe('assertive');
      expect(updated.announcementQueue[0].category).toBe('general');
    });

    it('should respect max queue size', () => {
      const smallQueueState = createScreenReaderEnhancements({
        maxQueueSize: 3,
      });

      let updated = smallQueueState;
      for (let i = 0; i < 5; i++) {
        updated = announceCustom(updated, `Message ${i}`);
      }

      expect(updated.announcementQueue).toHaveLength(3);
      // Should have messages 2, 3, 4 (oldest removed)
      expect(updated.announcementQueue[0].message).toBe('Message 2');
      expect(updated.announcementQueue[2].message).toBe('Message 4');
    });

    it('should prevent duplicate announcements within delay', () => {
      let updated = announceCustom(state, 'Same message');

      // Try to announce same message immediately
      updated = announceCustom(updated, 'Same message');

      expect(updated.announcementQueue).toHaveLength(1);
    });

    it('should allow duplicate after delay', async () => {
      const shortDelayState = createScreenReaderEnhancements({
        announceDelay: 10,
      });

      let updated = announceCustom(shortDelayState, 'Same message');

      // Wait for delay to pass
      await new Promise((resolve) => setTimeout(resolve, 15));

      updated = announceCustom(updated, 'Same message');

      expect(updated.announcementQueue).toHaveLength(2);
    });

    it('should get next announcement from queue', () => {
      let updated = announceCustom(state, 'First');
      updated = announceCustom(updated, 'Second');

      const { state: newState, announcement } = getNextAnnouncement(updated);

      expect(announcement?.message).toBe('First');
      expect(newState.announcementQueue).toHaveLength(1);
      expect(newState.announcementQueue[0].message).toBe('Second');
    });

    it('should return null if queue empty', () => {
      const { announcement } = getNextAnnouncement(state);

      expect(announcement).toBeNull();
    });

    it('should clear announcement queue', () => {
      let updated = announceCustom(state, 'Message 1');
      updated = announceCustom(updated, 'Message 2');

      const cleared = clearAnnouncementQueue(updated);

      expect(cleared.announcementQueue).toHaveLength(0);
    });
  });

  describe('State Management', () => {
    it('should enable/disable screen reader', () => {
      const disabled = setScreenReaderEnabled(state, false);

      expect(disabled.isEnabled).toBe(false);

      // Announcements should not be added when disabled
      const updated = announceCustom(disabled, 'Test');
      expect(updated.announcementQueue).toHaveLength(0);
    });

    it('should update configuration', () => {
      const updated = updateScreenReaderConfig(state, {
        verboseMode: true,
        maxQueueSize: 20,
      });

      expect(updated.config.verboseMode).toBe(true);
      expect(updated.config.maxQueueSize).toBe(20);
      expect(updated.config.enableRelationships).toBe(true); // Unchanged
    });
  });

  describe('Custom Keyboard Shortcuts', () => {
    it('should register new shortcut', () => {
      const newShortcut: KeyboardShortcut = {
        keys: 'Cmd+K',
        description: 'Custom action',
        category: 'edit',
        enabled: true,
      };

      const updated = registerKeyboardShortcut(state, newShortcut);

      const found = updated.shortcuts.find((s) => s.keys === 'Cmd+K');
      expect(found).toBeDefined();
      expect(found?.description).toBe('Custom action');
    });

    it('should update existing shortcut', () => {
      const existingShortcut = state.shortcuts.find((s) => s.keys === 'Tab');
      expect(existingShortcut).toBeDefined();

      const updatedShortcut: KeyboardShortcut = {
        keys: 'Tab',
        description: 'Updated description',
        category: 'navigation',
        enabled: false,
      };

      const updated = registerKeyboardShortcut(state, updatedShortcut);

      const found = updated.shortcuts.find((s) => s.keys === 'Tab');
      expect(found?.description).toBe('Updated description');
      expect(found?.enabled).toBe(false);
    });

    it('should unregister shortcut', () => {
      const updated = unregisterKeyboardShortcut(state, 'Tab', 'navigation');

      const found = updated.shortcuts.find((s) => s.keys === 'Tab');
      expect(found).toBeUndefined();
    });
  });

  describe('Statistics', () => {
    it('should calculate announcement statistics', () => {
      let updated = announceCustom(state, 'Message 1');
      updated = announceNodeRelationships(updated, 'node-1', {
        incoming: ['node-a'],
        outgoing: [],
        bidirectional: [],
      });
      updated = announceCollaborativeEdit(updated, {
        user: 'Alice',
        action: 'created',
        elementId: 'node-2',
        elementType: 'node',
        timestamp: new Date(),
      });

      const stats = getAnnouncementStatistics(updated);

      expect(stats.queueSize).toBe(3);
      expect(stats.categoryCounts.general).toBe(1);
      expect(stats.categoryCounts.relationship).toBe(1);
      expect(stats.categoryCounts.collaboration).toBe(1);
      expect(stats.oldestAnnouncement).toBeDefined();
      expect(stats.newestAnnouncement).toBeDefined();
    });

    it('should handle empty queue in statistics', () => {
      const stats = getAnnouncementStatistics(state);

      expect(stats.queueSize).toBe(0);
      expect(stats.categoryCounts).toEqual({});
      expect(stats.oldestAnnouncement).toBeNull();
      expect(stats.newestAnnouncement).toBeNull();
    });
  });

  describe('Relationship Description Utilities', () => {
    it('should describe simple relationships', () => {
      const relationships: NodeRelationships = {
        incoming: ['a', 'b'],
        outgoing: ['c'],
        bidirectional: [],
      };

      const description = describeNodeRelationships(relationships);

      expect(description).toContain('2 incoming edges');
      expect(description).toContain('1 outgoing edge');
    });

    it('should describe verbose relationships', () => {
      const relationships: NodeRelationships = {
        incoming: ['a'],
        outgoing: [],
        bidirectional: [],
      };

      const description = describeNodeRelationships(
        relationships,
        'My Node',
        true
      );

      expect(description).toContain('My Node');
      expect(description).toContain('has');
    });

    it('should describe no connections', () => {
      const relationships: NodeRelationships = {
        incoming: [],
        outgoing: [],
        bidirectional: [],
      };

      const description = describeNodeRelationships(relationships);

      expect(description).toBe('No connections');
    });

    it('should describe verbose no connections', () => {
      const relationships: NodeRelationships = {
        incoming: [],
        outgoing: [],
        bidirectional: [],
      };

      const description = describeNodeRelationships(
        relationships,
        'Isolated Node',
        true
      );

      expect(description).toContain('Isolated Node');
      expect(description).toContain('not connected');
    });
  });

  describe('Accessibility Integration', () => {
    it('should maintain politeness levels', () => {
      const updated = announceNodeRelationships(state, 'node-1', {
        incoming: ['a'],
        outgoing: [],
        bidirectional: [],
      });

      expect(updated.announcementQueue[0].politeness).toBe('polite');
    });

    it('should respect category filters', () => {
      const shortcuts = getKeyboardShortcutHelp(state, 'accessibility');

      expect(shortcuts.every((s) => s.category === 'accessibility')).toBe(true);
      expect(shortcuts.some((s) => s.keys === 'Cmd/Ctrl+/')).toBe(true);
    });

    it('should track timestamps correctly', () => {
      const before = Date.now();
      const updated = announceCustom(state, 'Test');
      const after = Date.now();

      const timestamp = updated.announcementQueue[0].timestamp;
      expect(timestamp).toBeGreaterThanOrEqual(before);
      expect(timestamp).toBeLessThanOrEqual(after);
    });
  });
});
