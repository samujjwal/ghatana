export interface KeyboardShortcut {
  id: string;
  key: string;
  modifiers?: ('ctrl' | 'cmd' | 'alt' | 'shift')[];
  category?: string;
  description?: string;
  context?: string;
  handler: (event?: KeyboardEvent) => void | boolean;
  disabled?: boolean;
  preventDefault?: boolean;
}

export interface ShortcutContext {
  id: string;
  name: string;
  priority?: number;
}

export interface UseKeyboardShortcutsOptions {
  context?: string;
}

export interface UseKeyboardShortcutsReturn {
  registerShortcut: (
    shortcut: Omit<KeyboardShortcut, 'id'> & { id?: string }
  ) => string;
  register: (
    shortcut: Omit<KeyboardShortcut, 'id'> & { id?: string }
  ) => string;
  unregisterShortcut: (id: string) => void;
  unregister: (id: string) => void;
  getShortcuts: (context?: string) => KeyboardShortcut[];
  list: () => KeyboardShortcut[];
  formatShortcut: (key: string, modifiers?: string[]) => string;
}