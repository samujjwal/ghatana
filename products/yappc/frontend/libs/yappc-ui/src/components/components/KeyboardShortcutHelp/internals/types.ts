/**
 * Type definitions for KeyboardShortcutHelp component
 * @module KeyboardShortcutHelp/types
 */

/**
 * Keyboard action categories for organizing shortcuts
 */
export type ShortcutCategoryType =
  | 'navigation'
  | 'editing'
  | 'selection'
  | 'view'
  | 'file'
  | 'help'
  | 'canvas'
  | 'collaboration';

/**
 * Represents a single keyboard binding action with execution logic
 *
 * @example
 * ```typescript
 * const copyAction: KeybindingAction = {
 *   id: 'canvas.copy',
 *   name: 'Copy',
 *   description: 'Copy selected elements',
 *   category: 'editing',
 *   keys: ['Mod', 'c'],
 *   action: () => copyElements(),
 *   preventDefault: true,
 * };
 * ```
 */
export interface KeybindingAction {
  /** Unique identifier for the shortcut */
  id: string;

  /** Human-readable name for the shortcut */
  name: string;

  /** Detailed description of what the shortcut does */
  description: string;

  /** Category for organizing shortcuts in UI */
  category: ShortcutCategoryType;

  /** Array of key names (e.g., ['Mod', 'c']) */
  keys: string[];

  /** Optional context where shortcut is available (e.g., 'canvas') */
  context?: string;

  /** Callback function executed when shortcut is triggered */
  action: () => void;

  /** Optional condition that must be true to execute the action */
  condition?: () => boolean;

  /** Whether to prevent default browser behavior (default: true) */
  preventDefault?: boolean;
}

/**
 * Represents a category of related keyboard shortcuts
 *
 * @example
 * ```typescript
 * const editingCategory: ShortcutCategory = {
 *   id: 'editing',
 *   name: 'Editing',
 *   description: 'Edit and manipulate canvas elements',
 *   icon: '✏️',
 *   shortcuts: [copyAction, pasteAction, cutAction],
 * };
 * ```
 */
export interface ShortcutCategory {
  /** Unique identifier for category */
  id: string;

  /** Display name for category */
  name: string;

  /** Description of category */
  description: string;

  /** Emoji or icon for category */
  icon: string;

  /** Shortcuts in this category */
  shortcuts: KeybindingAction[];
}

/**
 * Props for KeyboardShortcutHelp component
 *
 * @example
 * ```typescript
 * <KeyboardShortcutHelp
 *   isOpen={showShortcuts}
 *   onClose={() => setShowShortcuts(false)}
 * />
 * ```
 */
export interface KeyboardShortcutProps {
  /** Controls visibility of the help dialog */
  isOpen: boolean;

  /** Callback when dialog should close */
  onClose: () => void;

  /** Optional CSS class for styling */
  className?: string;
}

/**
 * Represents the keyboard shortcut registry interface
 * See utils.ts for the full implementation and examples
 */
export interface KeyboardShortcutRegistry {
  /** Register a keyboard shortcut */
  register(shortcut: KeybindingAction): void;

  /** Unregister a keyboard shortcut */
  unregister(id: string): void;

  /** Get all shortcuts grouped by category, sorted alphabetically */
  getShortcutsByCategory(): ShortcutCategory[];

  /** Search shortcuts by name, description, or key combination */
  searchShortcuts(query: string): KeybindingAction[];

  /** Enable or disable all keyboard shortcuts */
  setEnabled(enabled: boolean): void;

  /** Format key combination for human-readable display */
  formatKeys(keys: string[]): string;
}

/**
 * Props for the ShortcutsList internal component
 *
 * @internal
 */
export interface ShortcutsListProps {
  /** Shortcuts to display */
  shortcuts: KeybindingAction[];

  /** Registry instance for formatting keys */
  registry: KeyboardShortcutRegistry;
}

// Note: Import keyboardShortcutRegistry directly from './registry' to avoid circular dependency
