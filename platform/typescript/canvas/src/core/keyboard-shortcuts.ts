/**
 * Keyboard Shortcut Handler System
 *
 * Generic keyboard shortcut management for canvas actions.
 * Supports platform-specific modifiers and conflict detection.
 *
 * @doc.type core
 * @doc.purpose Keyboard shortcut management
 * @doc.layer core
 * @doc.pattern Command + Observer
 */

export interface ShortcutConfig {
  key: string;
  ctrl?: boolean;
  alt?: boolean;
  shift?: boolean;
  meta?: boolean; // Command on Mac, Windows key on Windows
  description?: string;
  handler: (event: KeyboardEvent) => void | Promise<void>;
  preventDefault?: boolean;
  stopPropagation?: boolean;
  enabled?: () => boolean;
}

export interface ShortcutGroup {
  id: string;
  label: string;
  shortcuts: ShortcutConfig[];
}

/**
 * Normalize shortcut key for consistent comparison
 */
function normalizeKey(key: string): string {
  return key.toLowerCase().trim();
}

/**
 * Generate shortcut ID from config
 */
function getShortcutId(config: ShortcutConfig): string {
  const parts: string[] = [];
  if (config.ctrl) parts.push("ctrl");
  if (config.alt) parts.push("alt");
  if (config.shift) parts.push("shift");
  if (config.meta) parts.push("meta");
  parts.push(normalizeKey(config.key));
  return parts.join("+");
}

/**
 * Check if event matches shortcut config
 */
function matchesShortcut(
  event: KeyboardEvent,
  config: ShortcutConfig,
): boolean {
  const keyMatches = normalizeKey(event.key) === normalizeKey(config.key);
  const ctrlMatches = !!config.ctrl === (event.ctrlKey || event.metaKey);
  const altMatches = !!config.alt === event.altKey;
  const shiftMatches = !!config.shift === event.shiftKey;
  const metaMatches = !!config.meta === event.metaKey;

  return keyMatches && ctrlMatches && altMatches && shiftMatches && metaMatches;
}

/**
 * Format shortcut for display
 */
export function formatShortcut(config: ShortcutConfig): string {
  const parts: string[] = [];
  const isMac =
    typeof navigator !== "undefined" && /Mac/.test(navigator.platform);

  if (config.ctrl) parts.push(isMac ? "⌘" : "Ctrl");
  if (config.alt) parts.push(isMac ? "⌥" : "Alt");
  if (config.shift) parts.push(isMac ? "⇧" : "Shift");
  if (config.meta) parts.push(isMac ? "⌘" : "Win");

  // Capitalize single letters, keep special keys as-is
  const key = config.key.length === 1 ? config.key.toUpperCase() : config.key;
  parts.push(key);

  return parts.join(isMac ? "" : "+");
}

/**
 * Keyboard Shortcut Manager
 */
export class KeyboardShortcutManager {
  private shortcuts: Map<string, ShortcutConfig> = new Map();
  private groups: Map<string, ShortcutGroup> = new Map();
  private isListening = false;
  private boundHandler: ((event: KeyboardEvent) => void) | null = null;

  /**
   * Register a keyboard shortcut
   */
  register(config: ShortcutConfig): void {
    const id = getShortcutId(config);

    if (this.shortcuts.has(id)) {
      console.warn(`Shortcut already registered: ${id}`);
    }

    this.shortcuts.set(id, config);
  }

  /**
   * Register multiple shortcuts at once
   */
  registerMany(configs: ShortcutConfig[]): void {
    configs.forEach((config) => this.register(config));
  }

  /**
   * Register a group of related shortcuts
   */
  registerGroup(group: ShortcutGroup): void {
    this.groups.set(group.id, group);
    this.registerMany(group.shortcuts);
  }

  /**
   * Unregister a shortcut
   */
  unregister(config: ShortcutConfig): void {
    const id = getShortcutId(config);
    this.shortcuts.delete(id);
  }

  /**
   * Unregister all shortcuts in a group
   */
  unregisterGroup(groupId: string): void {
    const group = this.groups.get(groupId);
    if (group) {
      group.shortcuts.forEach((config) => this.unregister(config));
      this.groups.delete(groupId);
    }
  }

  /**
   * Start listening for keyboard events
   */
  startListening(target: HTMLElement | Window = window): void {
    if (this.isListening) {
      console.warn("Already listening for keyboard shortcuts");
      return;
    }

    this.boundHandler = this.handleKeyDown.bind(this);
    target.addEventListener("keydown", this.boundHandler as EventListener);
    this.isListening = true;
  }

  /**
   * Stop listening for keyboard events
   */
  stopListening(target: HTMLElement | Window = window): void {
    if (!this.isListening || !this.boundHandler) {
      return;
    }

    target.removeEventListener("keydown", this.boundHandler as EventListener);
    this.boundHandler = null;
    this.isListening = false;
  }

  /**
   * Handle keyboard event
   */
  private async handleKeyDown(event: KeyboardEvent): Promise<void> {
    // Find matching shortcut
    for (const [id, config] of this.shortcuts.entries()) {
      if (matchesShortcut(event, config)) {
        // Check if shortcut is enabled
        if (config.enabled && !config.enabled()) {
          continue;
        }

        // Prevent default and stop propagation if configured
        if (config.preventDefault !== false) {
          event.preventDefault();
        }
        if (config.stopPropagation) {
          event.stopPropagation();
        }

        // Execute handler
        try {
          await config.handler(event);
        } catch (error) {
          console.error(`Error executing shortcut ${id}:`, error);
        }

        // Only execute first matching shortcut
        break;
      }
    }
  }

  /**
   * Get all registered shortcuts
   */
  getAllShortcuts(): ShortcutConfig[] {
    return Array.from(this.shortcuts.values());
  }

  /**
   * Get shortcuts by group
   */
  getShortcutsByGroup(groupId: string): ShortcutConfig[] {
    const group = this.groups.get(groupId);
    return group ? group.shortcuts : [];
  }

  /**
   * Get all groups
   */
  getAllGroups(): ShortcutGroup[] {
    return Array.from(this.groups.values());
  }

  /**
   * Check if a shortcut is registered
   */
  isRegistered(config: ShortcutConfig): boolean {
    const id = getShortcutId(config);
    return this.shortcuts.has(id);
  }

  /**
   * Find conflicts (shortcuts with same key combination)
   */
  findConflicts(): Array<{ id: string; shortcuts: ShortcutConfig[] }> {
    const conflicts: Map<string, ShortcutConfig[]> = new Map();

    this.shortcuts.forEach((config, id) => {
      if (!conflicts.has(id)) {
        conflicts.set(id, []);
      }
      conflicts.get(id)!.push(config);
    });

    return Array.from(conflicts.entries())
      .filter(([, shortcuts]) => shortcuts.length > 1)
      .map(([id, shortcuts]) => ({ id, shortcuts }));
  }

  /**
   * Clear all shortcuts
   */
  clear(): void {
    this.shortcuts.clear();
    this.groups.clear();
  }

  /**
   * Get statistics
   */
  getStats(): {
    total: number;
    byGroup: Record<string, number>;
    conflicts: number;
  } {
    const byGroup: Record<string, number> = {};
    this.groups.forEach((group, id) => {
      byGroup[id] = group.shortcuts.length;
    });

    return {
      total: this.shortcuts.size,
      byGroup,
      conflicts: this.findConflicts().length,
    };
  }
}

/**
 * Global keyboard shortcut manager instance
 */
let globalManager: KeyboardShortcutManager | null = null;

/**
 * Get the global keyboard shortcut manager
 */
export function getKeyboardShortcutManager(): KeyboardShortcutManager {
  if (!globalManager) {
    globalManager = new KeyboardShortcutManager();
  }
  return globalManager;
}

/**
 * Reset the global keyboard shortcut manager (useful for testing)
 */
export function resetKeyboardShortcutManager(): void {
  if (globalManager) {
    globalManager.stopListening();
  }
  globalManager = new KeyboardShortcutManager();
}

/**
 * React hook for keyboard shortcuts
 * Note: This is a placeholder. Actual implementation should use useEffect.
 * Consumers should implement their own React hook using this manager.
 */
export function createKeyboardShortcutHook() {
  return function useKeyboardShortcuts(
    shortcuts: ShortcutConfig[],
    enabled = true,
  ): void {
    if (typeof window === "undefined") return;

    const manager = getKeyboardShortcutManager();

    // Register shortcuts on mount
    if (enabled) {
      shortcuts.forEach((shortcut) => manager.register(shortcut));

      if (!manager["isListening"]) {
        manager.startListening();
      }
    }

    // Note: Cleanup should be handled in useEffect in actual React implementation
  };
}

/**
 * Common shortcut presets
 */
export const COMMON_SHORTCUTS = {
  // Navigation
  ZOOM_IN: { key: "+", ctrl: true, description: "Zoom in" },
  ZOOM_OUT: { key: "-", ctrl: true, description: "Zoom out" },
  ZOOM_FIT: { key: "0", ctrl: true, description: "Zoom to fit" },
  ZOOM_100: { key: "1", ctrl: true, description: "Zoom to 100%" },

  // Editing
  UNDO: { key: "z", ctrl: true, description: "Undo" },
  REDO: { key: "y", ctrl: true, description: "Redo" },
  COPY: { key: "c", ctrl: true, description: "Copy" },
  PASTE: { key: "v", ctrl: true, description: "Paste" },
  CUT: { key: "x", ctrl: true, description: "Cut" },
  DUPLICATE: { key: "d", ctrl: true, description: "Duplicate" },
  DELETE: { key: "Delete", description: "Delete" },

  // Selection
  SELECT_ALL: { key: "a", ctrl: true, description: "Select all" },
  DESELECT: { key: "Escape", description: "Deselect" },

  // Tools
  COMMAND_PALETTE: { key: "k", ctrl: true, description: "Command palette" },
  SEARCH: { key: "f", ctrl: true, description: "Search" },

  // Panels
  TOGGLE_OUTLINE: { key: "1", ctrl: true, description: "Toggle outline" },
  TOGGLE_LAYERS: { key: "2", ctrl: true, description: "Toggle layers" },
  TOGGLE_PALETTE: { key: "3", ctrl: true, description: "Toggle palette" },
  TOGGLE_TASKS: { key: "4", ctrl: true, description: "Toggle tasks" },
  TOGGLE_MINIMAP: { key: "5", ctrl: true, description: "Toggle minimap" },
} as const;
