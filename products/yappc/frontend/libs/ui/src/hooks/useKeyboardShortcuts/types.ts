/**
 * Keyboard shortcut configuration with modifiers, handlers, and context support.
 *
 * @example
 * const shortcut: KeyboardShortcut = {
 *   id: 'cmd-save',
 *   key: 's',
 *   modifiers: ['cmd'],
 *   handler: () => saveFile(),
 *   preventDefault: true
 * };
 */
export interface KeyboardShortcut {
  /** Unique identifier for the shortcut */
  id: string;
  /** Single key name, e.g., 'k', 'Escape', 's' */
  key: string;
  /** Optional modifier keys: 'ctrl', 'cmd', 'alt', 'shift' */
  modifiers?: ('ctrl' | 'cmd' | 'alt' | 'shift')[];
  /** Optional category for grouping related shortcuts */
  category?: string;
  /** Optional human-readable description of the shortcut action */
  description?: string;
  /** Optional context ID to associate shortcut with specific context */
  context?: string;
  /** Handler function called when shortcut is triggered. Returns true to signal execution. */
  handler: (event?: KeyboardEvent) => void | boolean;
  /** Whether the shortcut is currently disabled */
  disabled?: boolean;
  /** Whether to prevent default event behavior. Defaults to true. */
  preventDefault?: boolean;
}

/**
 * Named context for grouping and prioritizing shortcuts.
 *
 * @example
 * const modalContext: ShortcutContext = {
 *   id: 'modal-dialog',
 *   name: 'Modal Dialog',
 *   priority: 10
 * };
 */
export interface ShortcutContext {
  /** Unique context identifier */
  id: string;
  /** Human-readable context name */
  name: string;
  /** Priority for context evaluation (higher = evaluated first) */
  priority?: number;
}

/**
 * Hook options for useKeyboardShortcuts.
 */
export interface UseKeyboardShortcutsOptions {
  /** Context ID or full options object for shortcut context */
  context?: string;
}

/**
 * Return type for useKeyboardShortcuts hook.
 *
 * Provides methods for registering, unregistering, and querying keyboard shortcuts.
 * Supports both old (unregisterShortcut, registerShortcut) and new (unregister, register) API names
 * for backward compatibility.
 */
export interface UseKeyboardShortcutsReturn {
  /** Register a new keyboard shortcut. Overwrites previous shortcut with same ID if exists. */
  registerShortcut: (
    shortcut: Omit<KeyboardShortcut, 'id'> & { id?: string }
  ) => string;
  /** Alias for registerShortcut for modern API */
  register: (
    shortcut: Omit<KeyboardShortcut, 'id'> & { id?: string }
  ) => string;
  /** Unregister a keyboard shortcut by ID */
  unregisterShortcut: (id: string) => void;
  /** Alias for unregisterShortcut for modern API */
  unregister: (id: string) => void;
  /** Get all shortcuts for a specific context, or all active shortcuts if no context provided */
  getShortcuts: (context?: string) => KeyboardShortcut[];
  /** Alias for getShortcuts - get all active shortcuts for current context */
  list: () => KeyboardShortcut[];
  /** Format keyboard shortcut modifiers and key into human-readable string (e.g., "ctrl+s") */
  formatShortcut: (key: string, modifiers?: string[]) => string;
}
