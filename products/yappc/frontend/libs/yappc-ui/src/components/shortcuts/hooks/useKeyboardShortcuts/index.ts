import { useEffect, useRef, useCallback } from 'react';

import type {
  KeyboardShortcut,
  UseKeyboardShortcutsOptions,
  UseKeyboardShortcutsReturn,
} from './types';
import { ShortcutRegistry } from './utils';

const registry = new ShortcutRegistry();

export function useKeyboardShortcuts(
  opts?: string | UseKeyboardShortcutsOptions
): UseKeyboardShortcutsReturn {
  const ids = useRef<string[]>([]);
  const context = typeof opts === 'string' ? opts : opts?.context;

  useEffect(() => {
    if (context) {
      registry.setActiveContexts([context]);
    }
  }, [context]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;

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

  useEffect(() => {
    return () => {
      ids.current.forEach((id) => {
        registry.unregisterShortcut(id);
      });
      ids.current = [];
    };
  }, []);

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

  const unregister = useCallback((id: string) => {
    registry.unregisterShortcut(id);
    ids.current = ids.current.filter((x) => x !== id);
  }, []);

  const list = useCallback(() => registry.getShortcuts(context), [context]);

  const getShortcuts = useCallback(
    (ctx?: string) => registry.getShortcuts(ctx ?? context),
    [context]
  );

  const formatShortcut = useCallback((key: string, modifiers?: string[]) => {
    if (!modifiers || modifiers.length === 0) return key;
    return `${modifiers.join('+')}+${key}`;
  }, []);

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

export { registry as shortcutRegistry };
