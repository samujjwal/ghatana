/**
 * Fuzzy Finder component for keyboard navigation
 *
 * Provides Cmd+K fuzzy search for quick navigation to pages, actions, and resources.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/navigation-ui/**
 * FuzzyFinder — searchable dropdown with fuzzy matching.
 *
 * @doc.type component
 * @doc.purpose Fuzzy search dropdown component
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
/* eslint-disable ghatana/no-duplicate-utilities */
import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Search, Command, FileText, BarChart3, Settings, Shield, Database } from 'lucide-react';

/**
 * Finder item
 */
export interface FinderItem {
  id: string;
  label: string;
  icon?: React.ReactNode;
  action: () => void;
  category?: string;
  shortcut?: string;
}

/**
 * FuzzyFinder component props
 */
interface FuzzyFinderProps {
  /**
   * Items to search
   */
  items: FinderItem[];
  /**
   * Optional: Enable keyboard shortcut (Cmd+K)
   */
  enableShortcut?: boolean;
  /**
   * Optional: Placeholder text
   */
  placeholder?: string;
  className?: string;
}

/**
 * Default finder items — using canonical AEP routes only.
 * Non-existent routes (settings, old hash paths) have been removed.
 */
export const DEFAULT_FINDER_ITEMS: FinderItem[] = [
  {
    id: 'monitoring',
    label: 'Monitoring Dashboard',
    icon: <BarChart3 className="h-4 w-4" />,
    action: () => window.location.assign('/operate'),
    category: 'Pages',
  },
  {
    id: 'governance',
    label: 'Governance',
    icon: <Shield className="h-4 w-4" />,
    action: () => window.location.assign('/govern'),
    category: 'Pages',
  },
  {
    id: 'pipelines',
    label: 'Pipelines',
    icon: <FileText className="h-4 w-4" />,
    action: () => window.location.assign('/build/pipelines'),
    category: 'Pages',
  },
];

/**
 * FuzzyFinder component
 *
 * Provides a keyboard-driven fuzzy search interface for quick navigation.
 * Supports Cmd+K shortcut, arrow key navigation, and category grouping.
 */
export const FuzzyFinder: React.FC<FuzzyFinderProps> = ({
  items,
  enableShortcut = true,
  placeholder = 'Search pages, actions, and resources...',
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  /**
   * Filter items by query
   */
  const filteredItems = React.useMemo(() => {
    if (!query) return items;

    const lowerQuery = query.toLowerCase();
    return items.filter(item =>
      item.label.toLowerCase().includes(lowerQuery) ||
      item.category?.toLowerCase().includes(lowerQuery)
    );
  }, [items, query]);

  /**
   * Group items by category
   */
  const groupedItems = React.useMemo(() => {
    const groups: Record<string, FinderItem[]> = {};

    filteredItems.forEach(item => {
      const category = item.category || 'Other';
      if (!groups[category]) {
        groups[category] = [];
      }
      groups[category].push(item);
    });

    return groups;
  }, [filteredItems]);

  /**
   * Flatten grouped items for keyboard navigation
   */
  const flatItems = React.useMemo(() => {
    return Object.values(groupedItems).flat();
  }, [groupedItems]);

  /**
   * Handle keyboard navigation
   */
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % flatItems.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + flatItems.length) % flatItems.length);
    } else if (e.key === 'Enter' && flatItems[selectedIndex]) {
      e.preventDefault();
      flatItems[selectedIndex].action();
      setIsOpen(false);
      setQuery('');
    } else if (e.key === 'Escape') {
      setIsOpen(false);
      setQuery('');
    }
  }, [flatItems, selectedIndex]);

  /**
   * Handle global keyboard shortcut
   */
  useEffect(() => {
    if (!enableShortcut) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen((prev) => !prev);
        if (!isOpen) {
          setTimeout(() => inputRef.current?.focus(), 0);
        }
      }
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enableShortcut, isOpen]);

  /**
   * Focus input when opened
   */
  useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus();
    }
  }, [isOpen]);

  /**
   * Reset selected index when query changes
   */
  useEffect(() => {
    setSelectedIndex(0);
  }, [query]);

  /**
   * Scroll selected item into view
   */
  useEffect(() => {
    if (listRef.current && selectedIndex >= 0) {
      const item = listRef.current.children[selectedIndex] as HTMLElement;
      item?.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedIndex]);

  if (!isOpen) {
    return (
      <button
        onClick={() => setIsOpen(true)}
        className={cn(
          'flex items-center gap-2 px-3 py-1.5',
          'bg-gray-100 dark:bg-gray-800',
          'text-gray-600 dark:text-gray-400',
          'rounded-lg text-sm',
          'hover:bg-gray-200 dark:hover:bg-gray-700',
          'transition-colors',
          className
        )}
        aria-label="Open fuzzy finder (Cmd+K)"
      >
        <Search className="h-4 w-4" />
        <span>Search...</span>
        <Command className="h-3 w-3 ml-auto" />
      </button>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-start justify-center pt-24 z-50 animate-in fade-in duration-200">
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-xl max-w-2xl w-full mx-4 overflow-hidden">
        <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
          <Search className="h-5 w-5 text-gray-400" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            className="flex-1 bg-transparent border-none outline-none text-gray-900 dark:text-white placeholder:text-gray-500"
          />
          <Command className="h-4 w-4 text-gray-400" />
          <span className="text-xs text-gray-500">ESC</span>
        </div>

        <div ref={listRef} className="max-h-96 overflow-y-auto py-2">
          {flatItems.length === 0 ? (
            <div className="px-4 py-8 text-center text-gray-500">
              No results found
            </div>
          ) : (
            Object.entries(groupedItems).map(([category, categoryItems]) => (
              <div key={category}>
                <div className="px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  {category}
                </div>
                {categoryItems.map((item, index) => {
                  const globalIndex = flatItems.indexOf(item);
                  return (
                    <button
                      key={item.id}
                      onClick={() => {
                        item.action();
                        setIsOpen(false);
                        setQuery('');
                      }}
                      className={cn(
                        'flex items-center gap-3 px-4 py-2 w-full text-left',
                        'hover:bg-gray-100 dark:hover:bg-gray-800',
                        'transition-colors',
                        globalIndex === selectedIndex && 'bg-gray-100 dark:bg-gray-800'
                      )}
                    >
                      {item.icon}
                      <span className="flex-1">{item.label}</span>
                      {item.shortcut && (
                        <span className="text-xs text-gray-500">{item.shortcut}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * Utility function to combine class names
 */
function cn(...classes: (string | undefined | boolean)[]): string | undefined {
  const filtered = classes.filter(Boolean) as string[];
  return filtered.length > 0 ? filtered.join(' ') : undefined;
}
