/**
 * Keyboard Shortcut Registry for Canvas
 *
 * Provides comprehensive keyboard navigation and shortcut management
 * for canvas operations with conflict detection and customization support.
 *
 * @module canvas/accessibility/shortcutRegistry
 */

import { useEffect, useRef, useState } from 'react';

/**
 * Keyboard modifier keys
 */
export type ModifierKey = 'ctrl' | 'alt' | 'shift' | 'meta' | 'cmd';

/**
 * Keyboard shortcut definition
 */
export interface KeyboardShortcut {
  /** Unique identifier for the shortcut */
  id: string;
  /** Human-readable description */
  description: string;
  /** Key combination (e.g., 'ctrl+z', 'cmd+shift+z') */
  keys: string;
  /** Handler function */
  handler: (event: KeyboardEvent) => void | Promise<void>;
  /** Category for organization */
  category?: string;
  /** Whether the shortcut is enabled */
  enabled?: boolean;
  /** Prevent default browser behavior */
  preventDefault?: boolean;
  /** Priority for conflict resolution (higher wins) */
  priority?: number;
}

/**
 * Shortcut conflict information
 */
export interface ShortcutConflict {
  keys: string;
  shortcuts: KeyboardShortcut[];
}

/**
 * Normalize key combination string for consistent matching
 */
function normalizeKeys(keys: string): string {
  const parts = keys.toLowerCase().split('+').map(k => k.trim());
  const modifiers: Set<string> = new Set();
  let mainKey = '';

  parts.forEach(part => {
    // Normalize cmd/meta to unified 'meta'
    if (part === 'cmd' || part === 'command' || part === 'meta') {
      modifiers.add('meta');
    } else if (part === 'ctrl' || part === 'control') {
      modifiers.add('ctrl');
    } else if (part === 'alt' || part === 'option') {
      modifiers.add('alt');
    } else if (part === 'shift') {
      modifiers.add('shift');
    } else {
      mainKey = part;
    }
  });

  // Sort modifiers for consistency
  const sortedMods = Array.from(modifiers).sort();
  return [...sortedMods, mainKey].filter(Boolean).join('+');
}

/**
 * Extract modifier state from keyboard event
 */
function getEventModifiers(event: KeyboardEvent): Set<string> {
  const mods = new Set<string>();
  if (event.ctrlKey) mods.add('ctrl');
  if (event.altKey) mods.add('alt');
  if (event.shiftKey) mods.add('shift');
  if (event.metaKey) mods.add('meta');
  return mods;
}

/**
 * Check if event matches shortcut keys
 */
function matchesShortcut(event: KeyboardEvent, keys: string): boolean {
  const normalized = normalizeKeys(keys);
  const mods = getEventModifiers(event);
  
  const keyParts = normalized.split('+');
  const expectedMods = new Set<string>();
  let expectedKey = '';

  keyParts.forEach(part => {
    if (['ctrl', 'alt', 'shift', 'meta'].includes(part)) {
      expectedMods.add(part);
    } else {
      expectedKey = part;
    }
  });

  // Check modifiers match exactly
  if (expectedMods.size !== mods.size) return false;
  for (const mod of expectedMods) {
    if (!mods.has(mod)) return false;
  }

  // Check main key matches
  const eventKey = event.key.toLowerCase();
  return eventKey === expectedKey;
}

/**
 * Keyboard shortcut registry manager
 */
export class ShortcutRegistry {
  private shortcuts: Map<string, KeyboardShortcut> = new Map();
  private keyMap: Map<string, Set<string>> = new Map();

  /**
   * Register a keyboard shortcut
   *
   * @param shortcut - Shortcut definition
   * @throws Error if shortcut ID already exists
   */
  register(shortcut: KeyboardShortcut): void {
    if (this.shortcuts.has(shortcut.id)) {
      throw new Error(`Shortcut with ID "${shortcut.id}" already exists`);
    }

    const normalized = normalizeKeys(shortcut.keys);
    
    this.shortcuts.set(shortcut.id, {
      ...shortcut,
      enabled: shortcut.enabled ?? true,
      preventDefault: shortcut.preventDefault ?? true,
      priority: shortcut.priority ?? 0,
    });

    if (!this.keyMap.has(normalized)) {
      this.keyMap.set(normalized, new Set());
    }
    this.keyMap.get(normalized)!.add(shortcut.id);
  }

  /**
   * Unregister a keyboard shortcut
   *
   * @param id - Shortcut ID to remove
   * @returns true if shortcut was removed
   */
  unregister(id: string): boolean {
    const shortcut = this.shortcuts.get(id);
    if (!shortcut) return false;

    const normalized = normalizeKeys(shortcut.keys);
    const ids = this.keyMap.get(normalized);
    if (ids) {
      ids.delete(id);
      if (ids.size === 0) {
        this.keyMap.delete(normalized);
      }
    }

    return this.shortcuts.delete(id);
  }

  /**
   * Update an existing shortcut
   */
  update(id: string, updates: Partial<KeyboardShortcut>): boolean {
    const shortcut = this.shortcuts.get(id);
    if (!shortcut) return false;

    // If keys changed, update keyMap
    if (updates.keys && updates.keys !== shortcut.keys) {
      const oldNormalized = normalizeKeys(shortcut.keys);
      const newNormalized = normalizeKeys(updates.keys);

      const oldIds = this.keyMap.get(oldNormalized);
      if (oldIds) {
        oldIds.delete(id);
        if (oldIds.size === 0) {
          this.keyMap.delete(oldNormalized);
        }
      }

      if (!this.keyMap.has(newNormalized)) {
        this.keyMap.set(newNormalized, new Set());
      }
      this.keyMap.get(newNormalized)!.add(id);
    }

    this.shortcuts.set(id, { ...shortcut, ...updates });
    return true;
  }

  /**
   * Get a shortcut by ID
   */
  get(id: string): KeyboardShortcut | undefined {
    return this.shortcuts.get(id);
  }

  /**
   * Get all registered shortcuts
   */
  getAll(): KeyboardShortcut[] {
    return Array.from(this.shortcuts.values());
  }

  /**
   * Get shortcuts by category
   */
  getByCategory(category: string): KeyboardShortcut[] {
    return this.getAll().filter(s => s.category === category);
  }

  /**
   * Detect shortcut conflicts
   *
   * @returns Array of conflicts (shortcuts sharing the same keys)
   */
  detectConflicts(): ShortcutConflict[] {
    const conflicts: ShortcutConflict[] = [];

    for (const [keys, ids] of this.keyMap.entries()) {
      if (ids.size > 1) {
        const shortcuts = Array.from(ids)
          .map(id => this.shortcuts.get(id))
          .filter((s): s is KeyboardShortcut => s !== undefined && s.enabled !== false);

        if (shortcuts.length > 1) {
          conflicts.push({ keys, shortcuts });
        }
      }
    }

    return conflicts;
  }

  /**
   * Handle keyboard event
   *
   * @param event - Keyboard event
   * @returns true if shortcut was handled
   */
  async handle(event: KeyboardEvent): Promise<boolean> {
    // Build key string from event
    const mods = getEventModifiers(event);
    const key = event.key.toLowerCase();
    const keyString = [...Array.from(mods).sort(), key].join('+');

    const ids = this.keyMap.get(keyString);
    if (!ids || ids.size === 0) return false;

    // Get enabled shortcuts matching this key combination
    const matchingShortcuts = Array.from(ids)
      .map(id => this.shortcuts.get(id))
      .filter((s): s is KeyboardShortcut => 
        s !== undefined && 
        s.enabled !== false &&
        matchesShortcut(event, s.keys)
      )
      .sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));

    if (matchingShortcuts.length === 0) return false;

    // Execute highest priority shortcut
    const shortcut = matchingShortcuts[0];
    
    if (shortcut.preventDefault) {
      event.preventDefault();
      event.stopPropagation();
    }

    await shortcut.handler(event);
    return true;
  }

  /**
   * Clear all shortcuts
   */
  clear(): void {
    this.shortcuts.clear();
    this.keyMap.clear();
  }

  /**
   * Enable/disable a shortcut
   */
  setEnabled(id: string, enabled: boolean): boolean {
    const shortcut = this.shortcuts.get(id);
    if (!shortcut) return false;
    shortcut.enabled = enabled;
    return true;
  }
}

/**
 * Global shortcut registry instance
 */
export const globalShortcutRegistry = new ShortcutRegistry();

/**
 * React hook for using keyboard shortcuts
 *
 * @param shortcuts - Array of shortcuts to register
 * @param enabled - Whether shortcuts are enabled
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   useKeyboardShortcuts([
 *     {
 *       id: 'save',
 *       description: 'Save canvas',
 *       keys: 'ctrl+s',
 *       handler: () => saveCanvas(),
 *       category: 'document'
 *     },
 *     {
 *       id: 'undo',
 *       description: 'Undo last action',
 *       keys: 'ctrl+z',
 *       handler: () => undo(),
 *       category: 'edit'
 *     }
 *   ]);
 * }
 * ```
 */
export function useKeyboardShortcuts(
  shortcuts: KeyboardShortcut[],
  enabled: boolean = true
): {
  conflicts: ShortcutConflict[];
  registry: ShortcutRegistry;
} {
  const [conflicts, setConflicts] = useState<ShortcutConflict[]>([]);
  const registryRef = useRef<ShortcutRegistry>(globalShortcutRegistry);

  // Register shortcuts
  useEffect(() => {
    if (!enabled) return;

    const ids = shortcuts.map(s => s.id);

    shortcuts.forEach(shortcut => {
      try {
        registryRef.current.register(shortcut);
      } catch (error) {
        // Shortcut already exists, update instead
        registryRef.current.update(shortcut.id, shortcut);
      }
    });

    // Check for conflicts
    setConflicts(registryRef.current.detectConflicts());

    return () => {
      ids.forEach(id => registryRef.current.unregister(id));
    };
  }, [shortcuts, enabled]);

  // Set up keyboard event listener
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      registryRef.current.handle(event);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled]);

  return {
    conflicts,
    registry: registryRef.current,
  };
}

/**
 * Common canvas keyboard shortcuts
 */
export const CANVAS_SHORTCUTS = {
  // Navigation
  PAN_UP: { keys: 'shift+up', description: 'Pan canvas up' },
  PAN_DOWN: { keys: 'shift+down', description: 'Pan canvas down' },
  PAN_LEFT: { keys: 'shift+left', description: 'Pan canvas left' },
  PAN_RIGHT: { keys: 'shift+right', description: 'Pan canvas right' },
  
  // Zoom
  ZOOM_IN: { keys: 'ctrl+=', description: 'Zoom in' },
  ZOOM_OUT: { keys: 'ctrl+-', description: 'Zoom out' },
  ZOOM_FIT: { keys: 'ctrl+0', description: 'Fit to screen' },
  
  // Selection
  SELECT_ALL: { keys: 'ctrl+a', description: 'Select all' },
  DESELECT: { keys: 'escape', description: 'Clear selection' },
  
  // Editing
  UNDO: { keys: 'ctrl+z', description: 'Undo' },
  REDO: { keys: 'ctrl+shift+z', description: 'Redo' },
  CUT: { keys: 'ctrl+x', description: 'Cut' },
  COPY: { keys: 'ctrl+c', description: 'Copy' },
  PASTE: { keys: 'ctrl+v', description: 'Paste' },
  DELETE: { keys: 'delete', description: 'Delete selection' },
  
  // Document
  SAVE: { keys: 'ctrl+s', description: 'Save' },
  EXPORT: { keys: 'ctrl+e', description: 'Export' },
  
  // Layout
  ALIGN_LEFT: { keys: 'ctrl+shift+l', description: 'Align left' },
  ALIGN_RIGHT: { keys: 'ctrl+shift+r', description: 'Align right' },
  ALIGN_TOP: { keys: 'ctrl+shift+t', description: 'Align top' },
  ALIGN_BOTTOM: { keys: 'ctrl+shift+b', description: 'Align bottom' },
  
  // Layers
  BRING_FORWARD: { keys: 'ctrl+]', description: 'Bring forward' },
  SEND_BACKWARD: { keys: 'ctrl+[', description: 'Send backward' },
  BRING_TO_FRONT: { keys: 'ctrl+shift+]', description: 'Bring to front' },
  SEND_TO_BACK: { keys: 'ctrl+shift+[', description: 'Send to back' },
} as const;
