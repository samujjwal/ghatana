/**
 * Command Bar Component
 *
 * Natural language command input for quick actions across Data Cloud.
 * Extends the base CommandPalette from libs/typescript/ui with AI intent parsing.
 *
 * Features:
 * - Natural language input with typeahead
 * - AI intent classification via Brain API
 * - Context-aware suggestions
 * - Command history and favorites
 * - Keyboard shortcuts (Cmd+K / Ctrl+K)
 *
 * @doc.type component
 * @doc.purpose AI-powered command bar for natural language navigation and actions
 * @doc.layer frontend
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Command,
  Search,
  ArrowRight,
  Star,
  History,
  Sparkles,
  Database,
  Workflow,
  Shield,
  Settings,
  X,
  Loader2,
  Lightbulb,
} from 'lucide-react';
import { cn } from '../../lib/theme';
import { useCommandBar } from '../../hooks/useCommandBar';
import type { CommandIntent, CommandSuggestion } from '../../stores/commandBar.store';

interface CommandBarProps {
  className?: string;
}

/**
 * Icon mapping for suggestion categories
 */
const CATEGORY_ICONS: Record<string, React.ReactNode> = {
  Navigation: <ArrowRight className="h-4 w-4" />,
  Actions: <Sparkles className="h-4 w-4" />,
  Data: <Database className="h-4 w-4" />,
  Workflows: <Workflow className="h-4 w-4" />,
  Trust: <Shield className="h-4 w-4" />,
  Settings: <Settings className="h-4 w-4" />,
};

/**
 * Intent type badge colors
 */
const INTENT_COLORS: Record<string, string> = {
  NAVIGATE: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300',
  CREATE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  QUERY: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300',
  ANALYZE: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-300',
  CONFIGURE: 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300',
  UNKNOWN: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
};

/**
 * Command Bar Modal Component
 */
export function CommandBar({ className }: CommandBarProps) {
  const {
    isOpen,
    query,
    isProcessing,
    currentIntent,
    suggestions,
    history,
    favorites,
    toggle,
    close,
    setQuery,
    execute,
    toggleFavorite,
    isFavorite,
  } = useCommandBar();

  const inputRef = useRef<HTMLInputElement>(null);
  const [selectedIndex, setSelectedIndex] = useState(0);

  // Focus input when opened
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
      setSelectedIndex(0);
    }
  }, [isOpen]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Global shortcut to toggle
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        toggle();
        return;
      }

      if (!isOpen) return;

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex((prev) => (prev + 1) % suggestions.length);
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
          break;
        case 'Enter':
          e.preventDefault();
          if (query.trim()) {
            execute();
          } else if (suggestions[selectedIndex]) {
            suggestions[selectedIndex].action();
            close();
          }
          break;
        case 'Escape':
          e.preventDefault();
          close();
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, query, suggestions, selectedIndex, toggle, close, execute]);

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      execute();
    },
    [execute]
  );

  const handleSuggestionClick = useCallback(
    (suggestion: CommandSuggestion) => {
      suggestion.action();
      close();
    },
    [close]
  );

  if (!isOpen) return null;

  return (
    <div className={cn('fixed inset-0 z-50', className)}>
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        onClick={close}
      />

      {/* Command Bar Modal */}
      <div className="relative flex items-start justify-center pt-[20vh]">
        <div
          className={cn(
            'w-full max-w-2xl mx-4',
            'bg-white dark:bg-gray-900',
            'rounded-xl shadow-2xl',
            'border border-gray-200 dark:border-gray-700',
            'overflow-hidden'
          )}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Search Input */}
          <form onSubmit={handleSubmit}>
            <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
              {isProcessing ? (
                <Loader2 className="h-5 w-5 text-primary-500 animate-spin" />
              ) : (
                <Command className="h-5 w-5 text-gray-400" />
              )}
              <input
                ref={inputRef}
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Type a command or ask a question..."
                className={cn(
                  'flex-1 bg-transparent',
                  'text-gray-900 dark:text-gray-100',
                  'placeholder-gray-400 dark:placeholder-gray-500',
                  'outline-none text-base'
                )}
                autoComplete="off"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => setQuery('')}
                  className="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded"
                >
                  <X className="h-4 w-4 text-gray-400" />
                </button>
              )}
              <kbd className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 bg-gray-100 dark:bg-gray-800 rounded text-xs text-gray-500">
                ⌘K
              </kbd>
            </div>
          </form>

          {/* Intent Badge */}
          {currentIntent && currentIntent.type !== 'UNKNOWN' && (
            <div className="px-4 py-2 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
              <div className="flex items-center gap-2">
                <Lightbulb className="h-4 w-4 text-amber-500" />
                <span className="text-sm text-gray-600 dark:text-gray-400">
                  I understand you want to:
                </span>
                <span
                  className={cn(
                    'px-2 py-0.5 rounded-full text-xs font-medium',
                    INTENT_COLORS[currentIntent.type]
                  )}
                >
                  {currentIntent.type}
                </span>
                {currentIntent.action && (
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    → {currentIntent.action}
                  </span>
                )}
                <span className="ml-auto text-xs text-gray-400">
                  {Math.round(currentIntent.confidence * 100)}% confident
                </span>
              </div>
            </div>
          )}

          {/* Suggestions / Results */}
          <div className="max-h-[50vh] overflow-y-auto">
            {/* Recent History */}
            {!query && history.length > 0 && (
              <div className="p-2">
                <div className="px-2 py-1 text-xs font-medium text-gray-500 uppercase tracking-wide">
                  Recent
                </div>
                {history.slice(0, 5).map((entry, index) => (
                  <button
                    key={entry.id}
                    onClick={() => execute(entry.query)}
                    className={cn(
                      'w-full flex items-center gap-3 px-3 py-2 rounded-lg',
                      'text-left text-sm',
                      'hover:bg-gray-100 dark:hover:bg-gray-800',
                      'transition-colors'
                    )}
                  >
                    <History className="h-4 w-4 text-gray-400" />
                    <span className="flex-1 truncate text-gray-700 dark:text-gray-300">
                      {entry.query}
                    </span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleFavorite(entry.query);
                      }}
                      className="p-1 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    >
                      <Star
                        className={cn(
                          'h-3 w-3',
                          isFavorite(entry.query)
                            ? 'fill-amber-400 text-amber-400'
                            : 'text-gray-300'
                        )}
                      />
                    </button>
                  </button>
                ))}
              </div>
            )}

            {/* Suggestions */}
            {suggestions.length > 0 && (
              <div className="p-2">
                {!query && (
                  <div className="px-2 py-1 text-xs font-medium text-gray-500 uppercase tracking-wide">
                    Suggestions
                  </div>
                )}
                {suggestions.map((suggestion, index) => (
                  <button
                    key={suggestion.id}
                    onClick={() => handleSuggestionClick(suggestion)}
                    className={cn(
                      'w-full flex items-center gap-3 px-3 py-2 rounded-lg',
                      'text-left',
                      'transition-colors',
                      selectedIndex === index
                        ? 'bg-primary-50 dark:bg-primary-900/30 border-l-2 border-primary-500'
                        : 'hover:bg-gray-100 dark:hover:bg-gray-800'
                    )}
                  >
                    <span className="flex-shrink-0 text-gray-400">
                      {CATEGORY_ICONS[suggestion.category] || (
                        <Search className="h-4 w-4" />
                      )}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
                        {suggestion.text}
                      </div>
                      {suggestion.description && (
                        <div className="text-xs text-gray-500 truncate">
                          {suggestion.description}
                        </div>
                      )}
                    </div>
                    <span className="text-xs text-gray-400 bg-gray-100 dark:bg-gray-800 px-2 py-0.5 rounded">
                      {suggestion.category}
                    </span>
                  </button>
                ))}
              </div>
            )}

            {/* Empty state */}
            {query && suggestions.length === 0 && !isProcessing && (
              <div className="p-8 text-center text-gray-500">
                <Sparkles className="h-8 w-8 mx-auto mb-2 text-gray-300" />
                <p className="text-sm">No matching commands found.</p>
                <p className="text-xs mt-1">
                  Press Enter to ask the AI assistant
                </p>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center gap-4 px-4 py-2 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 text-xs text-gray-500">
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 rounded">↑↓</kbd>
              Navigate
            </span>
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 rounded">↵</kbd>
              Execute
            </span>
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 rounded">Esc</kbd>
              Close
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Command Bar Trigger Button
 */
export function CommandBarTrigger({ className }: { className?: string }) {
  const { toggle } = useCommandBar();

  return (
    <button
      onClick={toggle}
      className={cn(
        'flex items-center gap-2 px-3 py-1.5',
        'bg-gray-100 dark:bg-gray-800',
        'hover:bg-gray-200 dark:hover:bg-gray-700',
        'border border-gray-200 dark:border-gray-700',
        'rounded-lg text-sm text-gray-500',
        'transition-colors',
        className
      )}
    >
      <Search className="h-4 w-4" />
      <span className="hidden sm:inline">Search or ask...</span>
      <kbd className="hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 rounded text-xs">
        <Command className="h-3 w-3" />K
      </kbd>
    </button>
  );
}

export default CommandBar;

