/**
 * @ghatana/yappc-ide - Command Palette Component
 * 
 * Command palette for quick command execution with search.
 * Integrates with keyboard shortcuts hook.
 * 
 * @doc.type component
 * @doc.purpose Command palette for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useEffect, useRef } from 'react';
import type { CommandPaletteProps } from '../hooks/useKeyboardShortcuts';

/**
 * Command Palette Component
 */
export const CommandPalette: React.FC<CommandPaletteProps> = ({
  isOpen,
  onClose,
  commands,
  query,
  onQueryChange,
  selectedIndex,
  onKeyDown,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);

  // Focus input when opened
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-start justify-center pt-20 z-50">
      <div className="w-full max-w-2xl bg-white dark:bg-gray-800 rounded-lg shadow-2xl overflow-hidden">
        {/* Search input */}
        <div className="p-4 border-b border-gray-200 dark:border-gray-700">
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Type a command or search..."
            className="w-full px-4 py-2 text-lg bg-transparent outline-none"
          />
        </div>

        {/* Commands list */}
        <div className="max-h-96 overflow-y-auto">
          {commands.length === 0 ? (
            <div className="p-8 text-center text-gray-500 dark:text-gray-400">
              No commands found
            </div>
          ) : (
            commands.map((command, index) => (
              <div
                key={command.id}
                className={`
                  flex items-center gap-3 px-4 py-3 cursor-pointer
                  ${index === selectedIndex
                    ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                    : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'
                  }
                `}
                onClick={() => {
                  command.action();
                  onClose();
                }}
              >
                <div className="flex-1">
                  <div className="font-medium">{command.description}</div>
                  <div className="text-sm text-gray-500 dark:text-gray-400">
                    {command.category}
                  </div>
                </div>
                <div className="text-sm text-gray-500 dark:text-gray-400 font-mono">
                  {[...command.modifiers, command.key.toUpperCase()].join(' + ')}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 bg-gray-50 dark:bg-gray-900 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-400">
          Navigate with ↑↓, Enter to execute, Escape to close
        </div>
      </div>
    </div>
  );
};
