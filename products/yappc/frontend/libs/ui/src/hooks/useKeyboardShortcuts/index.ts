import { useEffect, useRef, useCallback } from 'react';

import { ShortcutRegistry } from './utils';

import type {
  KeyboardShortcut,
  UseKeyboardShortcutsOptions,
  UseKeyboardShortcutsReturn,
} from './types';

const registry = new ShortcutRegistry();

/**
 * React hook for managing keyboard shortcuts with context support.
 *
 * Provides a lightweight, context-aware keyboard shortcut registry. Shortcuts are
 * automatically unregistered on component unmount. Supports cross-platform modifiers
 * (ctrl/cmd detection for Mac), and can filter shortcuts by context.
 *
 * Features:
 * - Automatic ID generation if not provided
 * - Context-based shortcut filtering (e.g., modal vs main)
 * - Platform-aware modifier handling (Mac cmd vs Windows ctrl)
 * - Prevention of shortcuts in input/textarea elements
 * - Backward compatibility with old and new API names
 *
 * @param opts - Optional configuration or context string
 * @returns Object with methods to register, unregister, and list keyboard shortcuts
 *
 * @example
 * ```tsx
 * const { register, unregister, getShortcuts } = useKeyboardShortcuts('modal');
 *
 * useEffect(() => {
 *   const saveId = register({
 *     key: 's',
 *     modifiers: ['cmd'],
 *     handler: () => handleSave(),
 *     description: 'Save document'
 *   });
 *
 *   return () => unregister(saveId);
 * }, []);
 * ```
 */
export function useKeyboardShortcuts(
  opts?: string | UseKeyboardShortcutsOptions
): UseKeyboardShortcutsReturn {
  const ids = useRef<string[]>([]);
  const context = typeof opts === 'string' ? opts : opts?.context;

  // Update active contexts when context prop changes
  useEffect(() => {
    if (context) {
      registry.setActiveContexts([context]);
    }
  }, [context]);

  // Set up global keyboard listener
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;

      // Skip shortcuts if target is an input field
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.isContentEditable)
      ) {
        return;
      }

      registry.execute(e);
    };

    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // Cleanup registered shortcuts on unmount
  useEffect(() => {
    return () => {
      ids.current.forEach((id) => {
        registry.unregisterShortcut(id);
      });
      ids.current = [];
    };
  }, []);

  /**
   * Register a new keyboard shortcut.
   *
   * Generates an ID if not provided. The shortcut is automatically unregistered
   * when the component unmounts.
   *
   * @param shortcut - Shortcut configuration (id is optional)
   * @returns The registered shortcut ID
   */
  const register = useCallback(
    (shortcut: Omit<KeyboardShortcut, 'id'> & { id?: string }) => {
      const id =
        shortcut.id ||
        `sc_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      const full: KeyboardShortcut = { ...shortcut, id } as KeyboardShortcut;
      registry.registerShortcut(full);
      ids.current.push(id);
      return id;
    },
    []
  );

  /**
   * Unregister a keyboard shortcut by ID.
   *
   * @param id - The shortcut ID to unregister
   */
  const unregister = useCallback((id: string) => {
    registry.unregisterShortcut(id);
    ids.current = ids.current.filter((x) => x !== id);
  }, []);

  /**
   * Get all shortcuts for the current context.
   *
   * @returns Array of active shortcuts
   */
  const list = useCallback(() => registry.getShortcuts(context), [context]);

  /**
   * Get shortcuts for a specific context or current active contexts.
   *
   * @param ctx - Optional context ID. If not provided, uses current active context.
   * @returns Array of shortcuts matching the context
   */
  const getShortcuts = useCallback(
    (ctx?: string) => registry.getShortcuts(ctx ?? context),
    [context]
  );

  /**
   * Format keyboard shortcut modifiers and key into human-readable string.
   *
   * @param key - The key name (e.g., 's', 'Enter')
   * @param modifiers - Optional modifier keys (e.g., ['ctrl', 'shift'])
   * @returns Formatted shortcut string (e.g., 'ctrl+shift+s')
   *
   * @example
   * formatShortcut('s', ['cmd']) // 'cmd+s'
   * formatShortcut('Enter') // 'Enter'
   */
  const formatShortcut = useCallback((key: string, modifiers?: string[]) => {
    if (!modifiers || modifiers.length === 0) return key;
    return `${modifiers.join('+')}+${key}`;
  }, []);

  // Return both old (unregisterShortcut, registerShortcut) and new (unregister, register) API names
  // for backward compatibility across the codebase
  return {
    registerShortcut: register,
    register,
    unregisterShortcut: unregister,
    unregister,
    getShortcuts,
    list,
    formatShortcut,
  } as const;
}

/** Export the shared global shortcut registry instance */
export { registry as shortcutRegistry };
