/**
 * KeyboardShortcutRegistry implementation
 * @module KeyboardShortcutHelp/registry
 */

import { DEFAULT_SHORTCUTS, ShortcutUtils } from './utils';

import type {
  KeybindingAction,
  KeyboardShortcutRegistry,
  ShortcutCategory,
} from './types';

/**
 * Keyboard shortcut registry with full management capabilities
 *
 * The registry handles:
 * - Registration and unregistration of shortcuts
 * - Event listener management
 * - Keyboard event parsing and matching
 * - Formatting key combinations for display
 * - Grouping shortcuts by category
 * - Searching shortcuts
 *
 * @example
 * ```typescript
 * const registry = new KeyboardShortcutRegistryImpl();
 *
 * // Register a custom shortcut
 * registry.register({
 *   id: 'custom.action',
 *   name: 'My Action',
 *   description: 'Do something custom',
 *   category: 'editing',
 *   keys: ['Mod', 'Alt', 'x'],
 *   action: () => myCustomAction(),
 * });
 *
 * // Get all shortcuts organized by category
 * const categories = registry.getShortcutsByCategory();
 *
 * // Search for shortcuts
 * const results = registry.searchShortcuts('zoom');
 * ```
 */
export class KeyboardShortcutRegistryImpl implements KeyboardShortcutRegistry {
  private shortcuts: Map<string, KeybindingAction> = new Map();
  private isEnabled: boolean = true;
  private handleKeyDown?: (event: KeyboardEvent) => void;

  /**
   * Initialize registry with default shortcuts and event listeners
   */
  constructor() {
    this.registerDefaultShortcuts();
    this.initializeEventListeners();
  }

  /**
   * Register a keyboard shortcut in the registry
   *
   * @param shortcut - The shortcut to register
   */
  register(shortcut: KeybindingAction): void {
    this.shortcuts.set(shortcut.id, shortcut);
  }

  /**
   * Unregister a keyboard shortcut from the registry
   *
   * @param id - The unique identifier of the shortcut to remove
   */
  unregister(id: string): void {
    this.shortcuts.delete(id);
  }

  /**
   * Get all shortcuts grouped by category, sorted alphabetically
   *
   * @returns Array of categories with their shortcuts
   */
  getShortcutsByCategory(): ShortcutCategory[] {
    const categories = new Map<string, ShortcutCategory>();

    for (const shortcut of this.shortcuts.values()) {
      if (!categories.has(shortcut.category)) {
        categories.set(shortcut.category, {
          id: shortcut.category,
          name: ShortcutUtils.getCategoryName(shortcut.category),
          description: ShortcutUtils.getCategoryDescription(shortcut.category),
          icon: ShortcutUtils.getCategoryIcon(shortcut.category),
          shortcuts: [],
        });
      }
      categories.get(shortcut.category)?.shortcuts.push(shortcut);
    }

    // Sort shortcuts within each category
    for (const category of categories.values()) {
      category.shortcuts.sort((a, b) => a.name.localeCompare(b.name));
    }

    return Array.from(categories.values()).sort((a, b) =>
      a.name.localeCompare(b.name)
    );
  }

  /**
   * Search shortcuts by name, description, or key combination
   *
   * @param query - Search term (case-insensitive)
   * @returns Array of matching shortcuts
   */
  searchShortcuts(query: string): KeybindingAction[] {
    const queryLower = query.toLowerCase();
    return Array.from(this.shortcuts.values()).filter(
      (shortcut) =>
        shortcut.name.toLowerCase().includes(queryLower) ||
        shortcut.description.toLowerCase().includes(queryLower) ||
        shortcut.keys.some((key) => key.toLowerCase().includes(queryLower))
    );
  }

  /**
   * Enable or disable all keyboard shortcuts
   *
   * @param enabled - Whether shortcuts should be active
   */
  setEnabled(enabled: boolean): void {
    this.isEnabled = enabled;
  }

  /**
   * Format key combination for human-readable display
   *
   * @param keys - Array of key names
   * @returns Formatted key combination string
   */
  formatKeys(keys: string[]): string {
    return ShortcutUtils.formatKeys(keys);
  }

  /**
   * Register all default shortcuts from DEFAULT_SHORTCUTS
   *
   * @private
   */
  private registerDefaultShortcuts(): void {
    DEFAULT_SHORTCUTS.forEach((shortcut) => this.register(shortcut));
  }

  /**
   * Initialize keyboard event listeners
   *
   * @private
   */
  private initializeEventListeners(): void {
    this.handleKeyDown = this.onKeyDown.bind(this);
    document.addEventListener('keydown', this.handleKeyDown);
  }

  /**
   * Handle keyboard events and trigger matching shortcuts
   *
   * @private
   */
  private onKeyDown(event: KeyboardEvent): void {
    if (!this.isEnabled) return;

    // Skip if user is typing in an input field
    if (ShortcutUtils.isEditableElement(event.target)) return;

    const keyCombo = ShortcutUtils.parseKeyEvent(event);

    for (const shortcut of this.shortcuts.values()) {
      if (ShortcutUtils.matchesShortcut(keyCombo, shortcut)) {
        // Check condition if provided
        if (shortcut.condition && !shortcut.condition()) continue;

        if (shortcut.preventDefault !== false) {
          event.preventDefault();
        }

        try {
          shortcut.action();
        } catch (error) {
          console.error('Shortcut execution error:', error);
        }
        break;
      }
    }
  }
}

/**
 * Singleton instance of the keyboard shortcut registry
 *
 * @internal
 */
export const keyboardShortcutRegistry: KeyboardShortcutRegistry =
  new KeyboardShortcutRegistryImpl();
