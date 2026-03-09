/**
 * Keyboard shortcut registry implementation and utilities
 * @module KeyboardShortcutHelp/utils
 */

import type { KeybindingAction, ShortcutCategoryType } from './types';

/**
 * Utility helper class for keyboard shortcut registry operations
 *
 * Provides static methods for common shortcut management tasks:
 * - Parsing keyboard events into key combinations
 * - Matching key combinations against registered shortcuts
 * - Detecting if an element is editable
 * - Mapping shortcuts to categories and display names
 */
export class ShortcutUtils {
  /**
   * Parse a keyboard event into a standardized key combination array
   *
   * Converts modifier keys to normalized names and extracts the main key.
   * Ignores pure modifier keys (Ctrl, Meta, Alt, Shift) as the main key.
   *
   * @param event - The keyboard event to parse
   * @returns Array of key names (e.g., ['Mod', 'c'] for Ctrl+C)
   *
   * @example
   * ```typescript
   * document.addEventListener('keydown', (e) => {
   *   const keys = ShortcutUtils.parseKeyEvent(e);
   *   console.log(keys); // ['Mod', 'c'] for Ctrl+C
   * });
   * ```
   */
  static parseKeyEvent(event: KeyboardEvent): string[] {
    const keys: string[] = [];

    if (event.ctrlKey || event.metaKey) keys.push('Mod');
    if (event.altKey) keys.push('Alt');
    if (event.shiftKey) keys.push('Shift');

    // Add the main key
    const key = event.key;
    if (
      key !== 'Control' &&
      key !== 'Meta' &&
      key !== 'Alt' &&
      key !== 'Shift'
    ) {
      keys.push(key);
    }

    return keys;
  }

  /**
   * Check if a key combination matches a shortcut's key pattern
   *
   * Both the key combination and shortcut keys must match exactly
   * (length and content).
   *
   * @param keyCombo - Parsed key combination from event
   * @param shortcut - Shortcut to match against
   * @returns true if keys match exactly
   *
   * @example
   * ```typescript
   * const matches = ShortcutUtils.matchesShortcut(
   *   ['Mod', 'c'],
   *   copyShortcut
   * );
   * ```
   */
  static matchesShortcut(
    keyCombo: string[],
    shortcut: KeybindingAction
  ): boolean {
    if (keyCombo.length !== shortcut.keys.length) return false;

    return shortcut.keys.every((key) => keyCombo.includes(key));
  }

  /**
   * Check if an element is editable (input, textarea, contenteditable)
   *
   * Used to skip keyboard shortcuts when user is typing.
   *
   * @param target - The DOM element to check
   * @returns true if element is editable
   *
   * @example
   * ```typescript
   * if (ShortcutUtils.isEditableElement(e.target)) {
   *   return; // Skip shortcut, allow normal typing
   * }
   * ```
   */
  static isEditableElement(target: unknown): boolean {
    if (!target || typeof target !== 'object') return false;

    const element = target as Element;
    const tagName = element.tagName?.toLowerCase();
    const editableTags = ['input', 'textarea', 'select'];

    return (
      editableTags.includes(tagName) ||
      element.getAttribute?.('contenteditable') === 'true' ||
      element.closest?.('[contenteditable="true"]') !== null
    );
  }

  /**
   * Get display name for a shortcut category
   *
   * @param category - The category type
   * @returns Human-readable category name
   *
   * @example
   * ```typescript
   * ShortcutUtils.getCategoryName('editing') // 'Editing'
   * ```
   */
  static getCategoryName(category: ShortcutCategoryType): string {
    const names: Record<ShortcutCategoryType, string> = {
      navigation: 'Navigation',
      editing: 'Editing',
      selection: 'Selection',
      view: 'View',
      file: 'File',
      help: 'Help',
      canvas: 'Canvas',
      collaboration: 'Collaboration',
    };
    // eslint-disable-next-line security/detect-object-injection
    return names[category] ?? category;
  }

  /**
   * Get description for a shortcut category
   *
   * @param category - The category type
   * @returns Category description
   */
  static getCategoryDescription(category: ShortcutCategoryType): string {
    const descriptions: Record<ShortcutCategoryType, string> = {
      navigation: 'Navigate between canvases and UI elements',
      editing: 'Edit and manipulate canvas elements',
      selection: 'Select and manage element selection',
      view: 'Control canvas view and zoom',
      file: 'File operations and data management',
      help: 'Help and assistance shortcuts',
      canvas: 'Canvas-specific operations',
      collaboration: 'Collaboration and sharing features',
    };
    // eslint-disable-next-line security/detect-object-injection
    return descriptions[category] ?? '';
  }

  /**
   * Get emoji icon for a shortcut category
   *
   * @param category - The category type
   * @returns Category emoji icon
   */
  static getCategoryIcon(category: ShortcutCategoryType): string {
    const icons: Record<ShortcutCategoryType, string> = {
      navigation: '🧭',
      editing: '✏️',
      selection: '🎯',
      view: '👁️',
      file: '📁',
      help: '❓',
      canvas: '🎨',
      collaboration: '👥',
    };
    // eslint-disable-next-line security/detect-object-injection
    return icons[category] ?? '⚙️';
  }

  /**
   * Format key combination for human-readable display
   *
   * Converts platform-specific notation and special keys:
   * - 'Mod' → '⌘' (Mac) or 'Ctrl' (Windows/Linux)
   * - 'Alt' → '⌥' (Mac) or 'Alt' (Windows/Linux)
   * - 'Shift' → '⇧'
   * - 'Enter' → '⏎'
   * - 'Backspace' → '⌫'
   * - 'Delete' → '⌦'
   * - 'Tab' → '⇥'
   * - 'Space' → '␣'
   *
   * @param keys - Array of key names
   * @returns Formatted string like '⌘ + C' or 'Ctrl + C'
   *
   * @example
   * ```typescript
   * ShortcutUtils.formatKeys(['Mod', 'c']);     // '⌘ + C' or 'Ctrl + C'
   * ShortcutUtils.formatKeys(['Mod', 'Shift', 's']); // '⌘ + ⇧ + S'
   * ```
   */
  static formatKeys(keys: string[]): string {
    return keys
      .map((key) => {
        let formatted = key;

        // Platform-specific modifiers
        if (key === 'Mod') {
          formatted = navigator.platform.includes('Mac') ? '⌘' : 'Ctrl';
        } else if (key === 'Alt') {
          formatted = navigator.platform.includes('Mac') ? '⌥' : 'Alt';
        } else {
          // Special keys
          const specialKeys: Record<string, string> = {
            Shift: '⇧',
            Enter: '⏎',
            Escape: 'Esc',
            Backspace: '⌫',
            Delete: '⌦',
            Tab: '⇥',
            Space: '␣',
          };

          // eslint-disable-next-line security/detect-object-injection
          formatted = specialKeys[key] ?? key;
        }

        return formatted.charAt(0).toUpperCase() + formatted.slice(1);
      })
      .join(' + ');
  }
}

/**
 * Default keyboard shortcuts for common canvas operations
 *
 * Organized by category:
 * - Canvas operations (copy, paste, delete, undo, redo)
 * - View operations (zoom, fit, actual size)
 * - Navigation (command palette, search, back, forward)
 * - File operations (save, export, new)
 * - Canvas-specific (add node, connection, toggle grid)
 * - Help (shortcuts, escape)
 *
 * @internal
 */
const noop = () => {};

export const DEFAULT_SHORTCUTS: KeybindingAction[] = [
  // Canvas Operations
  {
    id: 'canvas.selectAll',
    name: 'Select All',
    description: 'Select all elements on the canvas',
    category: 'selection',
    keys: ['Mod', 'a'],
    action: noop,
  },
  {
    id: 'canvas.copy',
    name: 'Copy',
    description: 'Copy selected elements',
    category: 'editing',
    keys: ['Mod', 'c'],
    action: noop,
  },
  {
    id: 'canvas.paste',
    name: 'Paste',
    description: 'Paste copied elements',
    category: 'editing',
    keys: ['Mod', 'v'],
    action: noop,
  },
  {
    id: 'canvas.cut',
    name: 'Cut',
    description: 'Cut selected elements',
    category: 'editing',
    keys: ['Mod', 'x'],
    action: noop,
  },
  {
    id: 'canvas.delete',
    name: 'Delete',
    description: 'Delete selected elements',
    category: 'editing',
    keys: ['Delete'],
    action: noop,
  },
  {
    id: 'canvas.undo',
    name: 'Undo',
    description: 'Undo last action',
    category: 'editing',
    keys: ['Mod', 'z'],
    action: noop,
  },
  {
    id: 'canvas.redo',
    name: 'Redo',
    description: 'Redo last undone action',
    category: 'editing',
    keys: ['Mod', 'Shift', 'z'],
    action: noop,
  },

  // View Operations
  {
    id: 'view.zoomIn',
    name: 'Zoom In',
    description: 'Zoom into the canvas',
    category: 'view',
    keys: ['Mod', '+'],
    action: noop,
  },
  {
    id: 'view.zoomOut',
    name: 'Zoom Out',
    description: 'Zoom out of the canvas',
    category: 'view',
    keys: ['Mod', '-'],
    action: noop,
  },
  {
    id: 'view.fitToScreen',
    name: 'Fit to Screen',
    description: 'Fit all elements to screen',
    category: 'view',
    keys: ['Mod', '0'],
    action: noop,
  },
  {
    id: 'view.actualSize',
    name: 'Actual Size',
    description: 'Reset zoom to 100%',
    category: 'view',
    keys: ['Mod', '1'],
    action: noop,
  },

  // Navigation
  {
    id: 'nav.commandPalette',
    name: 'Command Palette',
    description: 'Open command palette',
    category: 'navigation',
    keys: ['Mod', 'k'],
    action: noop,
  },
  {
    id: 'nav.search',
    name: 'Search',
    description: 'Search elements and canvases',
    category: 'navigation',
    keys: ['Mod', 'f'],
    action: noop,
  },
  {
    id: 'nav.goBack',
    name: 'Go Back',
    description: 'Navigate to previous canvas',
    category: 'navigation',
    keys: ['Alt', 'ArrowLeft'],
    action: noop,
  },
  {
    id: 'nav.goForward',
    name: 'Go Forward',
    description: 'Navigate to next canvas',
    category: 'navigation',
    keys: ['Alt', 'ArrowRight'],
    action: noop,
  },

  // File Operations
  {
    id: 'file.save',
    name: 'Save',
    description: 'Save current canvas',
    category: 'file',
    keys: ['Mod', 's'],
    action: noop,
  },
  {
    id: 'file.export',
    name: 'Export',
    description: 'Export canvas',
    category: 'file',
    keys: ['Mod', 'e'],
    action: noop,
  },
  {
    id: 'file.new',
    name: 'New Canvas',
    description: 'Create new canvas',
    category: 'file',
    keys: ['Mod', 'n'],
    action: noop,
  },

  // Canvas-specific
  {
    id: 'canvas.addNode',
    name: 'Add Node',
    description: 'Add a new node',
    category: 'canvas',
    keys: ['n'],
    context: 'canvas',
    action: noop,
  },
  {
    id: 'canvas.addConnection',
    name: 'Add Connection',
    description: 'Start connecting nodes',
    category: 'canvas',
    keys: ['c'],
    context: 'canvas',
    action: noop,
  },
  {
    id: 'canvas.toggleGrid',
    name: 'Toggle Grid',
    description: 'Show/hide canvas grid',
    category: 'view',
    keys: ['g'],
    context: 'canvas',
    action: noop,
  },

  // Help
  {
    id: 'help.shortcuts',
    name: 'Keyboard Shortcuts',
    description: 'Show this help dialog',
    category: 'help',
    keys: ['?'],
    action: noop,
  },
  {
    id: 'help.escape',
    name: 'Cancel/Escape',
    description: 'Cancel current action or close dialogs',
    category: 'help',
    keys: ['Escape'],
    action: noop,
  },
];
