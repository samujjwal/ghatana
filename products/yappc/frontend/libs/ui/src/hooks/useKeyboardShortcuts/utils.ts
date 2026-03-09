import type { KeyboardShortcut } from './types';

/**
 * Utility class for managing keyboard shortcuts registry and execution.
 *
 * Handles:
 * - Shortcut registration and unregistration
 * - Context-aware shortcut filtering
 * - Cross-platform keyboard event handling (Mac vs Windows/Linux)
 * - Modifier key detection and matching
 */
export class ShortcutRegistry {
  private shortcuts = new Map<string, KeyboardShortcut>();
  private activeContexts: string[] = ['global'];

  /**
   * Determine if running on macOS.
   *
   * Uses navigator.platform to detect Mac environment. Falls back to false if navigator unavailable.
   *
   * @returns true if running on macOS, false otherwise
   */
  private static isMac(): boolean {
    return (
      typeof navigator !== 'undefined' && navigator.platform.includes('Mac')
    );
  }

  /**
   * Normalize key name to lowercase for consistent comparison.
   *
   * @param key - The key name to normalize (e.g., 'Enter', 'ENTER', 'enter')
   * @returns Lowercased key name (e.g., 'enter')
   */
  private static normalizeKeyName(key: string): string {
    return key.toLowerCase();
  }

  /**
   * Register a keyboard shortcut in the registry.
   *
   * If a shortcut with the same ID already exists, it will be overwritten.
   *
   * @param shortcut - The shortcut configuration to register
   *
   * @example
   * registry.registerShortcut({
   *   id: 'cmd-save',
   *   key: 's',
   *   modifiers: ['cmd'],
   *   handler: () => saveFile()
   * });
   */
  registerShortcut(shortcut: KeyboardShortcut): void {
    this.shortcuts.set(shortcut.id, shortcut);
  }

  /**
   * Unregister a keyboard shortcut by ID.
   *
   * Safe operation - does nothing if shortcut ID doesn't exist.
   *
   * @param id - The unique shortcut ID to remove
   */
  unregisterShortcut(id: string): void {
    this.shortcuts.delete(id);
  }

  /**
   * Set the active shortcut contexts.
   *
   * Contexts determine which shortcuts are evaluated. Always includes 'global' context.
   * Automatically deduplicates contexts.
   *
   * @param contexts - Array of context IDs to activate
   *
   * @example
   * registry.setActiveContexts(['global', 'modal-dialog']);
   */
  setActiveContexts(contexts: string[]): void {
    this.activeContexts = ['global', ...contexts].filter(
      (c, i, a) => a.indexOf(c) === i
    );
  }

  /**
   * Get shortcuts for a specific context or all active contexts.
   *
   * Returns shortcuts that:
   * - Have no context (global shortcuts), or
   * - Have a context matching the provided context (or current active contexts if none provided)
   *
   * @param context - Optional specific context to filter by. If provided, returns only shortcuts for that context.
   * @returns Array of shortcuts matching the context criteria
   *
   * @example
   * const globalShortcuts = registry.getShortcuts(); // All active shortcuts
   * const modalShortcuts = registry.getShortcuts('modal-dialog'); // Only modal shortcuts
   */
  getShortcuts(context?: string): KeyboardShortcut[] {
    if (context) {
      return Array.from(this.shortcuts.values()).filter(
        (s) => s.context === context || !s.context
      );
    }
    return Array.from(this.shortcuts.values()).filter(
      (s) => !s.context || this.activeContexts.includes(s.context)
    );
  }

  /**
   * Execute keyboard shortcuts matching the event.
   *
   * Matches shortcut based on key and modifier keys. Respects platform differences
   * (cmd on Mac vs ctrl on Windows/Linux). Calls the first matching shortcut handler.
   *
   * Skips disabled shortcuts and shortcuts not in active contexts.
   *
   * @param event - The keyboard event to match against registered shortcuts
   * @returns true if a shortcut handler was executed, false otherwise
   *
   * @throws Does not throw. Logs errors to console if handler throws.
   */
  // eslint-disable-next-line complexity
  execute(event: KeyboardEvent): boolean {
    const key = ShortcutRegistry.normalizeKeyName(event.key);

    for (const s of this.getShortcuts()) {
      if (s.disabled) continue;

      const sk = ShortcutRegistry.normalizeKeyName(s.key);
      if (sk !== key) continue;

      // Check modifiers match
      const mods = s.modifiers || [];
      const needCtrl = mods.includes('ctrl');
      const needCmd = mods.includes('cmd');
      const needAlt = mods.includes('alt');
      const needShift = mods.includes('shift');

      const isMac = ShortcutRegistry.isMac();
      const ctrlPressed = isMac ? event.metaKey : event.ctrlKey;
      const cmdPressed = event.metaKey;

      if (needCtrl && !ctrlPressed) continue;
      if (needCmd && !cmdPressed) continue;
      if (needAlt && !event.altKey) continue;
      if (needShift && !event.shiftKey) continue;

      try {
        const res = s.handler(event);
        if (s.preventDefault !== false) event.preventDefault();
        return !!res;
      } catch (err) {
        console.error('Shortcut handler error', err);
      }
    }

    return false;
  }
}
