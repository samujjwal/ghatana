import type { KeyboardShortcut } from './types';

/**
 *
 */
export class ShortcutRegistry {
  private shortcuts = new Map<string, KeyboardShortcut>();
  private activeContexts: string[] = ['global'];

  /**
   *
   */
  private static isMac(): boolean {
    return (
      typeof navigator !== 'undefined' && navigator.platform.includes('Mac')
    );
  }

  /**
   *
   */
  private static normalizeKeyName(key: string): string {
    return key.toLowerCase();
  }

  /**
   *
   */
  registerShortcut(shortcut: KeyboardShortcut): void {
    this.shortcuts.set(shortcut.id, shortcut);
  }

  /**
   *
   */
  unregisterShortcut(id: string): void {
    this.shortcuts.delete(id);
  }

  /**
   *
   */
  setActiveContexts(contexts: string[]): void {
    this.activeContexts = ['global', ...contexts].filter(
      (c, i, a) => a.indexOf(c) === i
    );
  }

  /**
   *
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
   *
   */
  execute(event: KeyboardEvent): boolean {
    const key = ShortcutRegistry.normalizeKeyName(event.key);

    for (const shortcut of this.getShortcuts()) {
      if (shortcut.disabled) continue;

      const shortcutKey = ShortcutRegistry.normalizeKeyName(shortcut.key);
      if (shortcutKey !== key) continue;

      const modifiers = shortcut.modifiers || [];
      const needCtrl = modifiers.includes('ctrl');
      const needCmd = modifiers.includes('cmd');
      const needAlt = modifiers.includes('alt');
      const needShift = modifiers.includes('shift');

      const isMac = ShortcutRegistry.isMac();
      const ctrlPressed = isMac ? event.metaKey : event.ctrlKey;
      const cmdPressed = event.metaKey;

      if (needCtrl && !ctrlPressed) continue;
      if (needCmd && !cmdPressed) continue;
      if (needAlt && !event.altKey) continue;
      if (needShift && !event.shiftKey) continue;

      try {
        const result = shortcut.handler(event);
        if (shortcut.preventDefault !== false) event.preventDefault();
        return !!result;
      } catch (err) {
        console.error('Shortcut handler error', err);
      }
    }

    return false;
  }
}
