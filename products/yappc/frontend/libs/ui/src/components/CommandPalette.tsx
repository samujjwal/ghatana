/**
 * Command Palette - Unified command interface for canvas operations
 * Provides keyboard-driven access to all canvas features
 */

import clsx from 'clsx';
import React, { useState, useEffect, useMemo } from 'react';

/**
 *
 */
export interface Command {
  id: string;
  title: string;
  description?: string;
  category: 'navigation' | 'editing' | 'export' | 'view' | 'collaboration' | 'system';
  keywords: string[];
  icon?: string;
  shortcut?: string;
  action: () => void | Promise<void>;
  disabled?: boolean;
  dangerous?: boolean;
}

/**
 *
 */
export interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  commands: Command[];
  placeholder?: string;
  maxResults?: number;
  className?: string;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({
  isOpen,
  onClose,
  commands,
  placeholder = 'Type a command...',
  maxResults = 10,
  className,
}) => {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);

  // Filter and rank commands based on query
  const filteredCommands = useMemo(() => {
    if (!query.trim()) {
      return commands.slice(0, maxResults);
    }

    const queryLower = query.toLowerCase();
    const scored = commands
      .map(command => {
        let score = 0;
        const titleLower = command.title.toLowerCase();
        const descLower = command.description?.toLowerCase() || '';
        const keywordsLower = command.keywords.join(' ').toLowerCase();

        // Exact title match gets highest score
        if (titleLower === queryLower) score += 100;
        // Title starts with query gets high score
        else if (titleLower.startsWith(queryLower)) score += 50;
        // Title contains query gets medium score
        else if (titleLower.includes(queryLower)) score += 25;

        // Description matches get bonus points
        if (descLower.includes(queryLower)) score += 10;

        // Keywords matches get bonus points
        if (keywordsLower.includes(queryLower)) score += 15;

        // Category match gets small bonus
        if (command.category.toLowerCase().includes(queryLower)) score += 5;

        return { command, score };
      })
      .filter(({ score }) => score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, maxResults)
      .map(({ command }) => command);

    return scored;
  }, [query, commands, maxResults]);

  // Reset selection when results change
  useEffect(() => {
    setSelectedIndex(0);
  }, [filteredCommands]);

  // Handle keyboard navigation
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex(prev => 
            prev < filteredCommands.length - 1 ? prev + 1 : 0
          );
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex(prev => 
            prev > 0 ? prev - 1 : filteredCommands.length - 1
          );
          break;
        case 'Enter':
          e.preventDefault();
          if (filteredCommands[selectedIndex]) {
            executeCommand(filteredCommands[selectedIndex]);
          }
          break;
        case 'Escape':
          e.preventDefault();
          onClose();
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, filteredCommands, selectedIndex, onClose]);

  // Execute command
  const executeCommand = async (command: Command) => {
    if (command.disabled) return;
    
    try {
      await command.action();
      onClose();
    } catch (error) {
      console.error('Command execution failed:', error);
    }
  };

  // Close on backdrop click
  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // Reset state when closing
  useEffect(() => {
    if (!isOpen) {
      setQuery('');
      setSelectedIndex(0);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div
      className={clsx(
        'fixed inset-0 z-50 flex items-start justify-center bg-black bg-opacity-50',
        'pt-20 px-4',
        className
      )}
      onClick={handleBackdropClick}
      data-testid="command-palette-backdrop"
    >
      <div
        className={clsx(
          'w-full max-w-2xl bg-white rounded-lg shadow-2xl',
          'border border-gray-200 overflow-hidden'
        )}
        data-testid="command-palette"
      >
        {/* Search Input */}
        <div className="p-4 border-b border-gray-200">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={placeholder}
            className={clsx(
              'w-full px-0 py-2 text-lg border-none outline-none',
              'placeholder-gray-400 bg-transparent'
            )}
            autoFocus
            data-testid="command-palette-input"
          />
        </div>

        {/* Results */}
        <div className="max-h-96 overflow-y-auto">
          {filteredCommands.length === 0 ? (
            <div className="p-4 text-center text-gray-500">
              {query ? 'No commands found' : 'Start typing to search commands...'}
            </div>
          ) : (
            <div className="p-2">
              {filteredCommands.map((command, index) => (
                <CommandItem
                  key={command.id}
                  command={command}
                  isSelected={index === selectedIndex}
                  onSelect={() => executeCommand(command)}
                  onHover={() => setSelectedIndex(index)}
                />
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 bg-gray-50 text-xs text-gray-500 border-t border-gray-200">
          <div className="flex justify-between items-center">
            <span>↑↓ to navigate, ⏎ to select, esc to close</span>
            <span>{filteredCommands.length} commands</span>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 *
 */
interface CommandItemProps {
  command: Command;
  isSelected: boolean;
  onSelect: () => void;
  onHover: () => void;
}

const CommandItem: React.FC<CommandItemProps> = ({
  command,
  isSelected,
  onSelect,
  onHover,
}) => {
  const getCategoryIcon = (category: Command['category']) => {
    switch (category) {
      case 'navigation': return '🧭';
      case 'editing': return '✏️';
      case 'export': return '📤';
      case 'view': return '👁️';
      case 'collaboration': return '👥';
      case 'system': return '⚙️';
      default: return '📋';
    }
  };

  return (
    <div
      className={clsx(
        'flex items-center p-3 rounded-md cursor-pointer transition-colors',
        isSelected 
          ? 'bg-blue-50 text-blue-900' 
          : 'hover:bg-gray-50',
        command.disabled && 'opacity-50 cursor-not-allowed',
        command.dangerous && 'text-red-600'
      )}
      onClick={command.disabled ? undefined : onSelect}
      onMouseEnter={onHover}
      data-testid={`command-item-${command.id}`}
    >
      {/* Icon */}
      <div className="flex-shrink-0 mr-3">
        <span className="text-lg">
          {command.icon || getCategoryIcon(command.category)}
        </span>
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex justify-between items-center">
          <h3 className="text-sm font-medium truncate">
            {command.title}
          </h3>
          {command.shortcut && (
            <span className="ml-2 px-2 py-1 text-xs bg-gray-200 text-gray-600 rounded">
              {command.shortcut}
            </span>
          )}
        </div>
        {command.description && (
          <p className="text-xs text-gray-500 mt-1 truncate">
            {command.description}
          </p>
        )}
      </div>

      {/* Category Badge */}
      <div className="flex-shrink-0 ml-3">
        <span className={clsx(
          'px-2 py-1 text-xs rounded-full',
          isSelected 
            ? 'bg-blue-100 text-blue-700' 
            : 'bg-gray-100 text-gray-600'
        )}>
          {command.category}
        </span>
      </div>
    </div>
  );
};

/**
 * Hook for command palette functionality
 */
export function useCommandPalette() {
  const [isOpen, setIsOpen] = useState(false);

  // Handle global keyboard shortcut
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd+K or Ctrl+K to open
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen(true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return {
    isOpen,
    open: () => setIsOpen(true),
    close: () => setIsOpen(false),
    toggle: () => setIsOpen(prev => !prev),
  };
}

export default CommandPalette;