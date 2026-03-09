/**
 * KeyboardShortcutsPanel Component
 * 
 * Displays all available keyboard shortcuts in a modal/panel.
 * Grouped by category with search functionality.
 * 
 * @doc.type component
 * @doc.purpose Keyboard shortcuts reference
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useMemo } from 'react';

interface Shortcut {
  keys: string[];
  description: string;
  category: string;
}

const SHORTCUTS: Shortcut[] = [
  // General
  { keys: ['⌘', 'K'], description: 'Open command palette', category: 'General' },
  { keys: ['⌘', '/'], description: 'Toggle keyboard shortcuts', category: 'General' },
  { keys: ['Esc'], description: 'Close dialog / Deselect all', category: 'General' },
  { keys: ['⌘', 'S'], description: 'Save', category: 'General' },
  
  // Navigation
  { keys: ['⌘', '1'], description: 'Go to Dashboard', category: 'Navigation' },
  { keys: ['⌘', '2'], description: 'Go to Projects', category: 'Navigation' },
  { keys: ['⌘', 'P'], description: 'Quick project switcher', category: 'Navigation' },
  
  // Canvas
  { keys: ['⌘', 'A'], description: 'Select all elements', category: 'Canvas' },
  { keys: ['Delete'], description: 'Delete selected elements', category: 'Canvas' },
  { keys: ['⌘', 'C'], description: 'Copy selected elements', category: 'Canvas' },
  { keys: ['⌘', 'V'], description: 'Paste elements', category: 'Canvas' },
  { keys: ['⌘', 'D'], description: 'Duplicate selected elements', category: 'Canvas' },
  { keys: ['⌘', 'Z'], description: 'Undo', category: 'Canvas' },
  { keys: ['⌘', 'Shift', 'Z'], description: 'Redo', category: 'Canvas' },
  { keys: ['⌘', '0'], description: 'Fit view', category: 'Canvas' },
  { keys: ['⌘', '+'], description: 'Zoom in', category: 'Canvas' },
  { keys: ['⌘', '-'], description: 'Zoom out', category: 'Canvas' },
  
  // Canvas Modes
  { keys: ['1'], description: 'Select mode', category: 'Canvas Modes' },
  { keys: ['2'], description: 'Sketch mode', category: 'Canvas Modes' },
  { keys: ['3'], description: 'Component mode', category: 'Canvas Modes' },
  { keys: ['4'], description: 'Flow mode', category: 'Canvas Modes' },
  { keys: ['5'], description: 'Data mode', category: 'Canvas Modes' },
  { keys: ['6'], description: 'API mode', category: 'Canvas Modes' },
  { keys: ['7'], description: 'Page mode', category: 'Canvas Modes' },
  
  // Sketch Tools
  { keys: ['V'], description: 'Select tool', category: 'Sketch' },
  { keys: ['P'], description: 'Pen tool', category: 'Sketch' },
  { keys: ['R'], description: 'Rectangle tool', category: 'Sketch' },
  { keys: ['E'], description: 'Eraser tool', category: 'Sketch' },
  { keys: ['N'], description: 'Sticky note', category: 'Sketch' },
  
  // Panels
  { keys: ['⌘', 'B'], description: 'Toggle sidebar', category: 'Panels' },
  { keys: ['⌘', 'J'], description: 'Toggle AI panel', category: 'Panels' },
  { keys: ['⌘', 'E'], description: 'Toggle validation panel', category: 'Panels' },
  { keys: ['⌘', 'G'], description: 'Toggle guidance panel', category: 'Panels' },
];

interface KeyboardShortcutsPanelProps {
  open: boolean;
  onClose: () => void;
}

export function KeyboardShortcutsPanel({ open, onClose }: KeyboardShortcutsPanelProps) {
  const [searchQuery, setSearchQuery] = useState('');

  // Filter shortcuts by search query
  const filteredShortcuts = useMemo(() => {
    if (!searchQuery.trim()) return SHORTCUTS;
    
    const query = searchQuery.toLowerCase();
    return SHORTCUTS.filter(
      (shortcut) =>
        shortcut.description.toLowerCase().includes(query) ||
        shortcut.category.toLowerCase().includes(query) ||
        shortcut.keys.some((key) => key.toLowerCase().includes(query))
    );
  }, [searchQuery]);

  // Group shortcuts by category
  const groupedShortcuts = useMemo(() => {
    const groups: Record<string, Shortcut[]> = {};
    filteredShortcuts.forEach((shortcut) => {
      if (!groups[shortcut.category]) {
        groups[shortcut.category] = [];
      }
      groups[shortcut.category].push(shortcut);
    });
    return groups;
  }, [filteredShortcuts]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
      />

      {/* Panel */}
      <div className="relative bg-bg-paper rounded-xl shadow-2xl w-full max-w-2xl max-h-[80vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-divider">
          <h2 className="text-lg font-semibold text-text-primary">
            Keyboard Shortcuts
          </h2>
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
            aria-label="Close"
          >
            <svg className="w-5 h-5 text-text-secondary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Search */}
        <div className="px-6 py-3 border-b border-divider">
          <div className="relative">
            <svg
              className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search shortcuts..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-grey-50 dark:bg-grey-900 border border-divider rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>

        {/* Shortcuts List */}
        <div className="overflow-y-auto max-h-[calc(80vh-140px)] p-6">
          {Object.keys(groupedShortcuts).length === 0 ? (
            <div className="text-center py-8 text-text-secondary">
              No shortcuts found for "{searchQuery}"
            </div>
          ) : (
            <div className="space-y-6">
              {Object.entries(groupedShortcuts).map(([category, shortcuts]) => (
                <div key={category}>
                  <h3 className="text-xs font-semibold text-text-secondary uppercase tracking-wider mb-3">
                    {category}
                  </h3>
                  <div className="space-y-2">
                    {shortcuts.map((shortcut, index) => (
                      <div
                        key={index}
                        className="flex items-center justify-between py-2 px-3 rounded-lg hover:bg-grey-50 dark:hover:bg-grey-800/50"
                      >
                        <span className="text-sm text-text-primary">
                          {shortcut.description}
                        </span>
                        <div className="flex items-center gap-1">
                          {shortcut.keys.map((key, keyIndex) => (
                            <React.Fragment key={keyIndex}>
                              <kbd className="px-2 py-1 text-xs font-medium bg-grey-100 dark:bg-grey-800 border border-grey-200 dark:border-grey-700 rounded shadow-sm">
                                {key}
                              </kbd>
                              {keyIndex < shortcut.keys.length - 1 && (
                                <span className="text-text-tertiary text-xs">+</span>
                              )}
                            </React.Fragment>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-divider bg-grey-50 dark:bg-grey-900/50">
          <p className="text-xs text-text-tertiary text-center">
            Press <kbd className="px-1.5 py-0.5 text-xs bg-grey-200 dark:bg-grey-700 rounded">⌘</kbd> + <kbd className="px-1.5 py-0.5 text-xs bg-grey-200 dark:bg-grey-700 rounded">/</kbd> to toggle this panel
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * useKeyboardShortcuts hook
 * 
 * Registers global keyboard shortcut listener for the shortcuts panel.
 */
export function useKeyboardShortcutsPanel() {
  const [isOpen, setIsOpen] = useState(false);

  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // ⌘+/ or Ctrl+/ to toggle shortcuts panel
      if ((event.metaKey || event.ctrlKey) && event.key === '/') {
        event.preventDefault();
        setIsOpen((prev) => !prev);
      }
      
      // Escape to close
      if (event.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  return {
    isOpen,
    open: () => setIsOpen(true),
    close: () => setIsOpen(false),
    toggle: () => setIsOpen((prev) => !prev),
  };
}

export default KeyboardShortcutsPanel;
