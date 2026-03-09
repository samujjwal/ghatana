/**
 * @vitest-environment jsdom
 */

import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import {
  ShortcutRegistry,
  useKeyboardShortcuts,
  globalShortcutRegistry,
  CANVAS_SHORTCUTS,
  type KeyboardShortcut,
} from '../shortcutRegistry';

describe('ShortcutRegistry', () => {
  let registry: ShortcutRegistry;

  beforeEach(() => {
    registry = new ShortcutRegistry();
  });

  describe('register', () => {
    it('should register a shortcut', () => {
      const handler = vi.fn();
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test shortcut',
        keys: 'ctrl+t',
        handler,
      };

      registry.register(shortcut);
      const registered = registry.get('test');

      expect(registered).toBeDefined();
      expect(registered?.id).toBe('test');
      expect(registered?.keys).toBe('ctrl+t');
    });

    it('should throw error for duplicate IDs', () => {
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      };

      registry.register(shortcut);
      expect(() => registry.register(shortcut)).toThrow();
    });

    it('should normalize key combinations', () => {
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test',
        keys: 'CTRL+SHIFT+T',
        handler: vi.fn(),
      };

      registry.register(shortcut);
      const registered = registry.get('test');
      expect(registered).toBeDefined();
    });

    it('should set default values', () => {
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      };

      registry.register(shortcut);
      const registered = registry.get('test')!;

      expect(registered.enabled).toBe(true);
      expect(registered.preventDefault).toBe(true);
      expect(registered.priority).toBe(0);
    });
  });

  describe('unregister', () => {
    it('should unregister a shortcut', () => {
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      };

      registry.register(shortcut);
      expect(registry.get('test')).toBeDefined();

      const result = registry.unregister('test');
      expect(result).toBe(true);
      expect(registry.get('test')).toBeUndefined();
    });

    it('should return false for non-existent shortcut', () => {
      const result = registry.unregister('nonexistent');
      expect(result).toBe(false);
    });
  });

  describe('update', () => {
    it('should update shortcut properties', () => {
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      };

      registry.register(shortcut);
      registry.update('test', { description: 'Updated' });

      const updated = registry.get('test');
      expect(updated?.description).toBe('Updated');
    });

    it('should update shortcut keys', () => {
      const shortcut: KeyboardShortcut = {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      };

      registry.register(shortcut);
      registry.update('test', { keys: 'ctrl+shift+t' });

      const updated = registry.get('test');
      expect(updated?.keys).toBe('ctrl+shift+t');
    });

    it('should return false for non-existent shortcut', () => {
      const result = registry.update('nonexistent', { description: 'Test' });
      expect(result).toBe(false);
    });
  });

  describe('getByCategory', () => {
    it('should return shortcuts by category', () => {
      registry.register({
        id: 'test1',
        description: 'Test 1',
        keys: 'ctrl+t',
        handler: vi.fn(),
        category: 'test',
      });

      registry.register({
        id: 'test2',
        description: 'Test 2',
        keys: 'ctrl+y',
        handler: vi.fn(),
        category: 'test',
      });

      registry.register({
        id: 'other',
        description: 'Other',
        keys: 'ctrl+o',
        handler: vi.fn(),
        category: 'other',
      });

      const testCategory = registry.getByCategory('test');
      expect(testCategory).toHaveLength(2);
      expect(testCategory.every(s => s.category === 'test')).toBe(true);
    });
  });

  describe('detectConflicts', () => {
    it('should detect shortcut conflicts', () => {
      registry.register({
        id: 'shortcut1',
        description: 'Shortcut 1',
        keys: 'ctrl+t',
        handler: vi.fn(),
      });

      registry.register({
        id: 'shortcut2',
        description: 'Shortcut 2',
        keys: 'ctrl+t',
        handler: vi.fn(),
      });

      const conflicts = registry.detectConflicts();
      expect(conflicts).toHaveLength(1);
      expect(conflicts[0].shortcuts).toHaveLength(2);
    });

    it('should not report disabled shortcuts as conflicts', () => {
      registry.register({
        id: 'shortcut1',
        description: 'Shortcut 1',
        keys: 'ctrl+t',
        handler: vi.fn(),
      });

      registry.register({
        id: 'shortcut2',
        description: 'Shortcut 2',
        keys: 'ctrl+t',
        handler: vi.fn(),
        enabled: false,
      });

      const conflicts = registry.detectConflicts();
      expect(conflicts).toHaveLength(0);
    });
  });

  describe('handle', () => {
    it('should execute matching shortcut handler', async () => {
      const handler = vi.fn();
      registry.register({
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler,
      });

      const event = new KeyboardEvent('keydown', {
        key: 't',
        ctrlKey: true,
      });

      const handled = await registry.handle(event);
      expect(handled).toBe(true);
      expect(handler).toHaveBeenCalledWith(event);
    });

    it('should not execute disabled shortcut', async () => {
      const handler = vi.fn();
      registry.register({
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler,
        enabled: false,
      });

      const event = new KeyboardEvent('keydown', {
        key: 't',
        ctrlKey: true,
      });

      const handled = await registry.handle(event);
      expect(handled).toBe(false);
      expect(handler).not.toHaveBeenCalled();
    });

    it('should respect shortcut priority', async () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      registry.register({
        id: 'low',
        description: 'Low priority',
        keys: 'ctrl+t',
        handler: handler1,
        priority: 0,
      });

      registry.register({
        id: 'high',
        description: 'High priority',
        keys: 'ctrl+t',
        handler: handler2,
        priority: 10,
      });

      const event = new KeyboardEvent('keydown', {
        key: 't',
        ctrlKey: true,
      });

      await registry.handle(event);
      expect(handler2).toHaveBeenCalled();
      expect(handler1).not.toHaveBeenCalled();
    });

    it('should handle modifier key combinations', async () => {
      const handler = vi.fn();
      registry.register({
        id: 'test',
        description: 'Test',
        keys: 'ctrl+shift+t',
        handler,
      });

      const event = new KeyboardEvent('keydown', {
        key: 't',
        ctrlKey: true,
        shiftKey: true,
      });

      const handled = await registry.handle(event);
      expect(handled).toBe(true);
      expect(handler).toHaveBeenCalled();
    });

    it('should not match partial modifier combinations', async () => {
      const handler = vi.fn();
      registry.register({
        id: 'test',
        description: 'Test',
        keys: 'ctrl+shift+t',
        handler,
      });

      // Only ctrl, missing shift
      const event = new KeyboardEvent('keydown', {
        key: 't',
        ctrlKey: true,
      });

      const handled = await registry.handle(event);
      expect(handled).toBe(false);
      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('setEnabled', () => {
    it('should enable/disable shortcuts', () => {
      registry.register({
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      });

      registry.setEnabled('test', false);
      expect(registry.get('test')?.enabled).toBe(false);

      registry.setEnabled('test', true);
      expect(registry.get('test')?.enabled).toBe(true);
    });

    it('should return false for non-existent shortcut', () => {
      const result = registry.setEnabled('nonexistent', true);
      expect(result).toBe(false);
    });
  });

  describe('clear', () => {
    it('should clear all shortcuts', () => {
      registry.register({
        id: 'test1',
        description: 'Test 1',
        keys: 'ctrl+t',
        handler: vi.fn(),
      });

      registry.register({
        id: 'test2',
        description: 'Test 2',
        keys: 'ctrl+y',
        handler: vi.fn(),
      });

      expect(registry.getAll()).toHaveLength(2);

      registry.clear();
      expect(registry.getAll()).toHaveLength(0);
    });
  });
});

describe('useKeyboardShortcuts', () => {
  beforeEach(() => {
    globalShortcutRegistry.clear();
  });

  it('should register shortcuts on mount', () => {
    const handler = vi.fn();
    const shortcuts: KeyboardShortcut[] = [
      {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler,
      },
    ];

    renderHook(() => useKeyboardShortcuts(shortcuts));

    expect(globalShortcutRegistry.get('test')).toBeDefined();
  });

  it('should unregister shortcuts on unmount', () => {
    const handler = vi.fn();
    const shortcuts: KeyboardShortcut[] = [
      {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler,
      },
    ];

    const { unmount } = renderHook(() => useKeyboardShortcuts(shortcuts));

    expect(globalShortcutRegistry.get('test')).toBeDefined();

    unmount();

    expect(globalShortcutRegistry.get('test')).toBeUndefined();
  });

  it('should detect conflicts', () => {
    const shortcuts: KeyboardShortcut[] = [
      {
        id: 'test1',
        description: 'Test 1',
        keys: 'ctrl+t',
        handler: vi.fn(),
      },
      {
        id: 'test2',
        description: 'Test 2',
        keys: 'ctrl+t',
        handler: vi.fn(),
      },
    ];

    const { result } = renderHook(() => useKeyboardShortcuts(shortcuts));

    expect(result.current.conflicts).toHaveLength(1);
  });

  it('should not register when disabled', () => {
    const shortcuts: KeyboardShortcut[] = [
      {
        id: 'test',
        description: 'Test',
        keys: 'ctrl+t',
        handler: vi.fn(),
      },
    ];

    renderHook(() => useKeyboardShortcuts(shortcuts, false));

    // Shortcuts should not be in registry when disabled
    expect(globalShortcutRegistry.getAll()).toHaveLength(0);
  });
});

describe('CANVAS_SHORTCUTS', () => {
  it('should provide standard canvas shortcuts', () => {
    expect(CANVAS_SHORTCUTS.UNDO.keys).toBe('ctrl+z');
    expect(CANVAS_SHORTCUTS.REDO.keys).toBe('ctrl+shift+z');
    expect(CANVAS_SHORTCUTS.SAVE.keys).toBe('ctrl+s');
    expect(CANVAS_SHORTCUTS.SELECT_ALL.keys).toBe('ctrl+a');
  });

  it('should have descriptions for all shortcuts', () => {
    Object.values(CANVAS_SHORTCUTS).forEach(shortcut => {
      expect(shortcut.description).toBeTruthy();
      expect(shortcut.keys).toBeTruthy();
    });
  });
});
