/**
 * @ghatana/yappc-ide - Keyboard Shortcuts Hook
 * 
 * Comprehensive keyboard shortcut management for IDE.
 * Supports customizable key bindings and command palette.
 * 
 * @doc.type module
 * @doc.purpose Keyboard shortcuts management for IDE
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Keyboard shortcut definition
 */
export interface KeyboardShortcut {
  id: string;
  key: string;
  modifiers: ('ctrl' | 'alt' | 'shift' | 'meta')[];
  action: () => void;
  description: string;
  category: 'file' | 'edit' | 'view' | 'navigation' | 'tools';
  when?: string; // Condition when shortcut is active
  isCustom?: boolean;
}

/**
 * Shortcut help item
 */
export interface ShortcutHelpItem {
  id: string;
  key: string;
  description: string;
  category: string;
}

/**
 * Hook for keyboard shortcuts
 */
export function useKeyboardShortcuts(shortcuts: KeyboardShortcut[] = []) {
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);
  const [commandPaletteQuery, setCommandPaletteQuery] = useState('');
  const [activeCommandIndex, setActiveCommandIndex] = useState(0);
  const shortcutsRef = useRef<KeyboardShortcut[]>(shortcuts);

  // Update ref when shortcuts change
  useEffect(() => {
    shortcutsRef.current = shortcuts;
  }, [shortcuts]);

  // Normalize key event
  const normalizeKeyEvent = useCallback((event: KeyboardEvent): string => {
    const parts: string[] = [];

    if (event.ctrlKey) parts.push('ctrl');
    if (event.altKey) parts.push('alt');
    if (event.shiftKey) parts.push('shift');
    if (event.metaKey) parts.push('meta');

    parts.push(event.key.toLowerCase());

    return parts.join('+');
  }, []);

  // Check if shortcut matches event
  const matchesShortcut = useCallback((
    event: KeyboardEvent,
    shortcut: KeyboardShortcut
  ): boolean => {
    const eventKey = normalizeKeyEvent(event);
    const shortcutKey = [...shortcut.modifiers, shortcut.key.toLowerCase()].join('+');

    return eventKey === shortcutKey;
  }, [normalizeKeyEvent]);

  // Handle keyboard events
  const handleKeyDown = useCallback((event: KeyboardEvent) => {
    // Don't handle shortcuts when typing in input fields
    const target = event.target as HTMLElement;
    if (
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.contentEditable === 'true'
    ) {
      // Allow certain shortcuts even in inputs
      const allowedInInput = ['escape', 'tab', 'enter'];
      if (!allowedInInput.includes(event.key.toLowerCase())) {
        return;
      }
    }

    // Check for command palette shortcut (Ctrl/Cmd + P)
    if (matchesShortcut(event, {
      id: 'command-palette',
      key: 'p',
      modifiers: [event.ctrlKey ? 'ctrl' : 'meta'],
      action: () => { },
      description: '',
      category: 'tools'
    })) {
      event.preventDefault();
      setIsCommandPaletteOpen(true);
      return;
    }

    // Check other shortcuts
    for (const shortcut of shortcutsRef.current) {
      if (matchesShortcut(event, shortcut)) {
        event.preventDefault();
        event.stopPropagation();

        // Check if shortcut should be active in current context
        if (shortcut.when && !eval(shortcut.when)) {
          continue;
        }

        shortcut.action();
        break;
      }
    }
  }, [matchesShortcut]);

  // Setup global keyboard listener
  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  // Filter commands for command palette
  const filteredCommands = shortcuts.filter(shortcut =>
    shortcut.description.toLowerCase().includes(commandPaletteQuery.toLowerCase()) ||
    shortcut.id.toLowerCase().includes(commandPaletteQuery.toLowerCase())
  );

  // Handle command palette navigation
  const handleCommandPaletteKeyDown = useCallback((event: React.KeyboardEvent) => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setActiveCommandIndex(prev =>
          prev < filteredCommands.length - 1 ? prev + 1 : 0
        );
        break;
      case 'ArrowUp':
        event.preventDefault();
        setActiveCommandIndex(prev =>
          prev > 0 ? prev - 1 : filteredCommands.length - 1
        );
        break;
      case 'Enter':
        event.preventDefault();
        if (filteredCommands[activeCommandIndex]) {
          filteredCommands[activeCommandIndex].action();
          setIsCommandPaletteOpen(false);
          setCommandPaletteQuery('');
          setActiveCommandIndex(0);
        }
        break;
      case 'Escape':
        event.preventDefault();
        setIsCommandPaletteOpen(false);
        setCommandPaletteQuery('');
        setActiveCommandIndex(0);
        break;
    }
  }, [filteredCommands, activeCommandIndex]);

  // Execute command
  const executeCommand = useCallback((commandId: string) => {
    const shortcut = shortcuts.find(s => s.id === commandId);
    if (shortcut) {
      shortcut.action();
    }
  }, [shortcuts]);

  // Get help text
  const getHelpText = useCallback((): ShortcutHelpItem[] => {
    return shortcuts.map(shortcut => ({
      id: shortcut.id,
      key: [...shortcut.modifiers, shortcut.key].join(' + ').toUpperCase(),
      description: shortcut.description,
      category: shortcut.category,
    }));
  }, [shortcuts]);

  // Register new shortcut
  const registerShortcut = useCallback((shortcut: KeyboardShortcut) => {
    shortcutsRef.current = [...shortcutsRef.current, shortcut];
  }, []);

  // Unregister shortcut
  const unregisterShortcut = useCallback((id: string) => {
    shortcutsRef.current = shortcutsRef.current.filter(s => s.id !== id);
  }, []);

  return {
    // Command palette
    isCommandPaletteOpen,
    openCommandPalette: () => setIsCommandPaletteOpen(true),
    closeCommandPalette: () => {
      setIsCommandPaletteOpen(false);
      setCommandPaletteQuery('');
      setActiveCommandIndex(0);
    },
    commandPaletteQuery,
    setCommandPaletteQuery,
    filteredCommands,
    activeCommandIndex,
    setActiveCommandIndex,
    handleCommandPaletteKeyDown,

    // Shortcut management
    executeCommand,
    registerShortcut,
    unregisterShortcut,
    getHelpText,

    // Utilities
    normalizeKeyEvent,
    matchesShortcut,
  };
}

/**
 * Default IDE shortcuts
 */
export const DEFAULT_IDE_SHORTCUTS: KeyboardShortcut[] = [
  // File operations
  {
    id: 'new-file',
    key: 'n',
    modifiers: ['ctrl'],
    action: () => console.log('New file'),
    description: 'New File',
    category: 'file',
  },
  {
    id: 'open-file',
    key: 'o',
    modifiers: ['ctrl'],
    action: () => console.log('Open file'),
    description: 'Open File',
    category: 'file',
  },
  {
    id: 'save-file',
    key: 's',
    modifiers: ['ctrl'],
    action: () => console.log('Save file'),
    description: 'Save File',
    category: 'file',
  },
  {
    id: 'save-all',
    key: 's',
    modifiers: ['ctrl', 'shift'],
    action: () => console.log('Save all'),
    description: 'Save All',
    category: 'file',
  },
  {
    id: 'close-file',
    key: 'w',
    modifiers: ['ctrl'],
    action: () => console.log('Close file'),
    description: 'Close File',
    category: 'file',
  },

  // Edit operations
  {
    id: 'undo',
    key: 'z',
    modifiers: ['ctrl'],
    action: () => console.log('Undo'),
    description: 'Undo',
    category: 'edit',
  },
  {
    id: 'redo',
    key: 'y',
    modifiers: ['ctrl'],
    action: () => console.log('Redo'),
    description: 'Redo',
    category: 'edit',
  },
  {
    id: 'cut',
    key: 'x',
    modifiers: ['ctrl'],
    action: () => console.log('Cut'),
    description: 'Cut',
    category: 'edit',
  },
  {
    id: 'copy',
    key: 'c',
    modifiers: ['ctrl'],
    action: () => console.log('Copy'),
    description: 'Copy',
    category: 'edit',
  },
  {
    id: 'paste',
    key: 'v',
    modifiers: ['ctrl'],
    action: () => console.log('Paste'),
    description: 'Paste',
    category: 'edit',
  },
  {
    id: 'select-all',
    key: 'a',
    modifiers: ['ctrl'],
    action: () => console.log('Select all'),
    description: 'Select All',
    category: 'edit',
  },
  {
    id: 'find',
    key: 'f',
    modifiers: ['ctrl'],
    action: () => console.log('Find'),
    description: 'Find',
    category: 'edit',
  },
  {
    id: 'replace',
    key: 'h',
    modifiers: ['ctrl'],
    action: () => console.log('Replace'),
    description: 'Replace',
    category: 'edit',
  },

  // View operations
  {
    id: 'toggle-sidebar',
    key: 'b',
    modifiers: ['ctrl'],
    action: () => console.log('Toggle sidebar'),
    description: 'Toggle Sidebar',
    category: 'view',
  },
  {
    id: 'toggle-terminal',
    key: '`',
    modifiers: ['ctrl'],
    action: () => console.log('Toggle terminal'),
    description: 'Toggle Terminal',
    category: 'view',
  },
  {
    id: 'toggle-explorer',
    key: 'e',
    modifiers: ['ctrl', 'shift'],
    action: () => console.log('Toggle explorer'),
    description: 'Toggle Explorer',
    category: 'view',
  },

  // Navigation
  {
    id: 'goto-line',
    key: 'g',
    modifiers: ['ctrl'],
    action: () => console.log('Go to line'),
    description: 'Go to Line',
    category: 'navigation',
  },
  {
    id: 'goto-symbol',
    key: 't',
    modifiers: ['ctrl', 'shift'],
    action: () => console.log('Go to symbol'),
    description: 'Go to Symbol',
    category: 'navigation',
  },
  {
    id: 'goto-file',
    key: 'p',
    modifiers: ['ctrl'],
    action: () => console.log('Go to file'),
    description: 'Go to File',
    category: 'navigation',
  },

  // Tools
  {
    id: 'command-palette',
    key: 'p',
    modifiers: ['ctrl'],
    action: () => { }, // Handled specially
    description: 'Command Palette',
    category: 'tools',
  },
  {
    id: 'toggle-command-palette',
    key: 'p',
    modifiers: ['ctrl', 'shift'],
    action: () => console.log('Toggle command palette'),
    description: 'Toggle Command Palette',
    category: 'tools',
  },
];

/**
 * Command Palette Component Props
 */
export interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  commands: KeyboardShortcut[];
  query: string;
  onQueryChange: (query: string) => void;
  selectedIndex: number;
  onSelectedIndexChange: (index: number) => void;
  onKeyDown: (event: React.KeyboardEvent) => void;
}
