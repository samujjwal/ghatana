import React, { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';

/**
 * Keyboard Shortcuts Manager component.
 *
 * Centralized keyboard shortcut management and help overlay.
 * Shows all available shortcuts organized by category.
 *
 * @doc.type component
 * @doc.purpose Keyboard shortcuts management and help
 * @doc.layer ui
 */

export interface KeyboardShortcut {
  id: string;
  keys: string[];
  description: string;
  category: ShortcutCategory;
  action: () => void;
  enabled?: boolean;
}

export type ShortcutCategory =
  | 'global'
  | 'canvas'
  | 'code-editor'
  | 'studio'
  | 'navigation';

export interface KeyboardShortcutsManagerProps {
  shortcuts?: KeyboardShortcut[];
  onShortcutExecute?: (shortcut: KeyboardShortcut) => void;
}

const DEFAULT_SHORTCUTS: KeyboardShortcut[] = [
  // Global
  {
    id: 'cmd-k',
    keys: ['⌘', 'K'],
    description: 'Command Palette (search everything)',
    category: 'global',
    action: () => {},
  },
  {
    id: 'cmd-slash',
    keys: ['⌘', '/'],
    description: 'Show all keyboard shortcuts',
    category: 'global',
    action: () => {},
  },
  {
    id: 'cmd-p',
    keys: ['⌘', 'P'],
    description: 'Quick project switcher',
    category: 'global',
    action: () => {},
  },
  {
    id: 'cmd-shift-s',
    keys: ['⌘', '⇧', 'S'],
    description: 'Toggle Studio Mode',
    category: 'global',
    action: () => {},
  },
  {
    id: 'esc',
    keys: ['Esc'],
    description: 'Close any panel/modal, deselect',
    category: 'global',
    action: () => {},
  },

  // Canvas
  {
    id: 'space-drag',
    keys: ['Space', '+', 'Drag'],
    description: 'Pan canvas',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-plus',
    keys: ['⌘', '+'],
    description: 'Zoom in',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-minus',
    keys: ['⌘', '-'],
    description: 'Zoom out',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-0',
    keys: ['⌘', '0'],
    description: 'Fit to screen',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-1',
    keys: ['⌘', '1'],
    description: 'Zoom to 100%',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'a',
    keys: ['A'],
    description: 'Add artifact',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'c',
    keys: ['C'],
    description: 'Add connection between selected',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'delete',
    keys: ['Delete'],
    description: 'Delete selected',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-z',
    keys: ['⌘', 'Z'],
    description: 'Undo',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-shift-z',
    keys: ['⌘', '⇧', 'Z'],
    description: 'Redo',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-c',
    keys: ['⌘', 'C'],
    description: 'Copy',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'cmd-v',
    keys: ['⌘', 'V'],
    description: 'Paste',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'arrows',
    keys: ['Arrow', 'keys'],
    description: 'Nudge selected element',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'tab',
    keys: ['Tab'],
    description: 'Cycle selection forward',
    category: 'canvas',
    action: () => {},
  },
  {
    id: 'shift-tab',
    keys: ['⇧', 'Tab'],
    description: 'Cycle selection backward',
    category: 'canvas',
    action: () => {},
  },

  // Code Editor
  {
    id: 'tab-accept',
    keys: ['Tab'],
    description: 'Accept AI suggestion',
    category: 'code-editor',
    action: () => {},
  },
  {
    id: 'cmd-space',
    keys: ['⌘', 'Space'],
    description: 'Trigger autocomplete',
    category: 'code-editor',
    action: () => {},
  },
  {
    id: 'cmd-shift-f',
    keys: ['⌘', '⇧', 'F'],
    description: 'Format document',
    category: 'code-editor',
    action: () => {},
  },
  {
    id: 'f2',
    keys: ['F2'],
    description: 'Rename symbol',
    category: 'code-editor',
    action: () => {},
  },
  {
    id: 'cmd-dot',
    keys: ['⌘', '.'],
    description: 'Quick fix (AI-powered)',
    category: 'code-editor',
    action: () => {},
  },
  {
    id: 'cmd-slash-comment',
    keys: ['⌘', '/'],
    description: 'Toggle comment',
    category: 'code-editor',
    action: () => {},
  },
  {
    id: 'cmd-d',
    keys: ['⌘', 'D'],
    description: 'Select next occurrence',
    category: 'code-editor',
    action: () => {},
  },

  // Studio
  {
    id: 'cmd-b',
    keys: ['⌘', 'B'],
    description: 'Toggle sidebar/file tree',
    category: 'studio',
    action: () => {},
  },
  {
    id: 'cmd-j',
    keys: ['⌘', 'J'],
    description: 'Toggle bottom panel (code editor)',
    category: 'studio',
    action: () => {},
  },
  {
    id: 'cmd-backslash',
    keys: ['⌘', '\\'],
    description: 'Toggle right panel (inspector)',
    category: 'studio',
    action: () => {},
  },

  // Navigation
  {
    id: 'cmd-1-7',
    keys: ['⌘', '1-7'],
    description: 'Jump to lifecycle phase',
    category: 'navigation',
    action: () => {},
  },
];

function ShortcutKey({ keyLabel }: { keyLabel: string }) {
  return (
    <kbd className="px-2 py-1 text-xs font-semibold bg-gray-100 dark:bg-gray-800 border border-gray-300 dark:border-gray-700 rounded">
      {keyLabel}
    </kbd>
  );
}

function ShortcutRow({ shortcut }: { shortcut: KeyboardShortcut }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-gray-200 dark:border-gray-800 last:border-b-0">
      <span className="text-sm text-gray-900 dark:text-gray-100">
        {shortcut.description}
      </span>
      <div className="flex items-center gap-1">
        {shortcut.keys.map((key, index) => (
          <React.Fragment key={index}>
            <ShortcutKey keyLabel={key} />
            {index < shortcut.keys.length - 1 && key !== '+' && (
              <span className="text-gray-400 text-xs">+</span>
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}

function ShortcutCategory({
  title,
  shortcuts,
}: {
  title: string;
  shortcuts: KeyboardShortcut[];
}) {
  return (
    <div className="mb-6">
      <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3 uppercase tracking-wide">
        {title}
      </h3>
      <div className="space-y-0">
        {shortcuts.map((shortcut) => (
          <ShortcutRow key={shortcut.id} shortcut={shortcut} />
        ))}
      </div>
    </div>
  );
}

export function KeyboardShortcutsHelp({
  isOpen,
  onClose,
}: {
  isOpen: boolean;
  onClose: () => void;
}) {
  const shortcuts = DEFAULT_SHORTCUTS;

  const categorizedShortcuts = {
    global: shortcuts.filter((s) => s.category === 'global'),
    canvas: shortcuts.filter((s) => s.category === 'canvas'),
    'code-editor': shortcuts.filter((s) => s.category === 'code-editor'),
    studio: shortcuts.filter((s) => s.category === 'studio'),
    navigation: shortcuts.filter((s) => s.category === 'navigation'),
  };

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };

    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/50 z-50" onClick={onClose} />

      {/* Modal */}
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="bg-white dark:bg-gray-900 rounded-lg shadow-2xl max-w-4xl w-full max-h-[80vh] overflow-hidden">
          {/* Header */}
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between">
            <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">
              Keyboard Shortcuts
            </h2>
            <button
              onClick={onClose}
              className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
            >
              ✕
            </button>
          </div>

          {/* Content */}
          <div className="px-6 py-4 overflow-y-auto max-h-[calc(80vh-120px)]">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <ShortcutCategory
                  title="Global"
                  shortcuts={categorizedShortcuts.global}
                />
                <ShortcutCategory
                  title="Canvas"
                  shortcuts={categorizedShortcuts.canvas}
                />
              </div>
              <div>
                <ShortcutCategory
                  title="Code Editor"
                  shortcuts={categorizedShortcuts['code-editor']}
                />
                <ShortcutCategory
                  title="Studio Mode"
                  shortcuts={categorizedShortcuts.studio}
                />
                <ShortcutCategory
                  title="Navigation"
                  shortcuts={categorizedShortcuts.navigation}
                />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-3 border-t border-gray-200 dark:border-gray-800 text-sm text-gray-500 dark:text-gray-400">
            Press <ShortcutKey keyLabel="Esc" /> to close
          </div>
        </div>
      </div>
    </>
  );
}

/**
 * Hook for managing keyboard shortcuts.
 *
 * @doc.type hook
 * @doc.purpose Keyboard shortcuts state management
 */
export function useKeyboardShortcuts(
  shortcuts: KeyboardShortcut[] = DEFAULT_SHORTCUTS
) {
  const [isHelpOpen, setIsHelpOpen] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // ⌘/ to show help
      if ((e.metaKey || e.ctrlKey) && e.key === '/') {
        e.preventDefault();
        setIsHelpOpen(true);
        return;
      }

      // Check for matching shortcuts
      for (const shortcut of shortcuts) {
        if (!shortcut.enabled && shortcut.enabled !== undefined) continue;

        const matches = matchesShortcut(e, shortcut);
        if (matches) {
          e.preventDefault();
          shortcut.action();
          break;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [shortcuts]);

  return {
    isHelpOpen,
    setIsHelpOpen,
    openHelp: () => setIsHelpOpen(true),
    closeHelp: () => setIsHelpOpen(false),
  };
}

function matchesShortcut(
  e: KeyboardEvent,
  shortcut: KeyboardShortcut
): boolean {
  // Simple matching logic - can be enhanced
  const key = e.key.toLowerCase();
  const hasCmd = e.metaKey || e.ctrlKey;
  const hasShift = e.shiftKey;
  const hasAlt = e.altKey;

  // This is a simplified version - real implementation would be more sophisticated
  return false;
}
