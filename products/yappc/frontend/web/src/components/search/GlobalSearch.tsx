// @ts-nocheck
/**
 * Global Search Component
 *
 * @description Command palette style global search with fuzzy matching,
 * keyboard shortcuts (Cmd+K), and search across all project content.
 *
 * @doc.type component
 * @doc.purpose Global search interface
 * @doc.layer component
 */

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search,
  X,
  Clock,
  TrendingUp,
  FileText,
  Code,
  Users,
  Settings,
  ArrowRight,
  Command,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Input } from '@ghatana/design-system';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';
import {
  globalSearchOpenAtom,
  globalSearchQueryAtom,
  globalSearchResultsAtom,
  globalSearchLoadingAtom,
} from '../../state/atoms';

// =============================================================================
// Types
// =============================================================================

export interface SearchResult {
  id: string;
  title: string;
  description?: string;
  category: 'page' | 'task' | 'file' | 'user' | 'setting';
  path: string;
  icon?: React.ComponentType<{ className?: string }>;
  metadata?: Record<string, unknown>;
  score?: number;
}

export interface GlobalSearchProps {
  placeholder?: string;
  maxResults?: number;
  recentSearchesLimit?: number;
}

// =============================================================================
// Fuzzy Search Algorithm
// =============================================================================

/**
 * Simple fuzzy matching algorithm
 * Returns a score between 0 and 1 (higher is better match)
 */
function fuzzyMatch(query: string, text: string): number {
  const queryLower = query.toLowerCase();
  const textLower = text.toLowerCase();

  // Exact match
  if (textLower === queryLower) return 1.0;

  // Contains match
  if (textLower.includes(queryLower)) return 0.8;

  // Fuzzy character matching
  let queryIndex = 0;
  let textIndex = 0;
  let matches = 0;
  let consecutiveMatches = 0;
  let maxConsecutive = 0;

  while (queryIndex < queryLower.length && textIndex < textLower.length) {
    if (queryLower[queryIndex] === textLower[textIndex]) {
      matches++;
      consecutiveMatches++;
      maxConsecutive = Math.max(maxConsecutive, consecutiveMatches);
      queryIndex++;
    } else {
      consecutiveMatches = 0;
    }
    textIndex++;
  }

  if (queryIndex !== queryLower.length) return 0;

  const matchRatio = matches / queryLower.length;
  const consecutiveBonus = maxConsecutive / queryLower.length;
  
  return matchRatio * 0.7 + consecutiveBonus * 0.3;
}

// =============================================================================
// Component
// =============================================================================

export const GlobalSearch: React.FC<GlobalSearchProps> = ({
  placeholder = 'Search project...',
  maxResults = 10,
  recentSearchesLimit = 5,
}) => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  
  const isOpen = useAtomValue(globalSearchOpenAtom);
  const setIsOpen = useSetAtom(globalSearchOpenAtom);
  const query = useAtomValue(globalSearchQueryAtom);
  const setQuery = useSetAtom(globalSearchQueryAtom);
  const searchResults = useAtomValue(globalSearchResultsAtom);
  const isLoading = useAtomValue(globalSearchLoadingAtom);

  const [selectedIndex, setSelectedIndex] = useState(0);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);

  // Mounted navigation routes — the only searchable paths that are actively served
  const mountedResults: SearchResult[] = useMemo(() => [
    {
      id: 'nav-workspaces',
      title: 'Workspaces',
      description: 'Manage your workspaces and contexts',
      category: 'page',
      path: '/workspaces',
      icon: TrendingUp,
    },
    {
      id: 'nav-projects',
      title: 'Projects',
      description: 'Browse and create projects',
      category: 'page',
      path: '/projects',
      icon: FileText,
    },
    {
      id: 'nav-settings',
      title: 'Settings',
      description: 'Product settings and preferences',
      category: 'setting',
      path: '/settings',
      icon: Settings,
    },
    {
      id: 'nav-profile',
      title: 'Profile',
      description: 'Your account summary',
      category: 'page',
      path: '/profile',
      icon: Users,
    },
  ], []);

  // Fuzzy search filtering
  const filteredResults = useMemo(() => {
    if (!query.trim()) return [];

    const results = mountedResults
      .map((result) => ({
        ...result,
        score: Math.max(
          fuzzyMatch(query, result.title),
          fuzzyMatch(query, result.description || '')
        ),
      }))
      .filter((result) => result.score > 0.3)
      .sort((a, b) => (b.score || 0) - (a.score || 0))
      .slice(0, maxResults);

    return results;
  }, [query, mountedResults, maxResults]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd+K or Ctrl+K to open
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen(true);
      }

      // Escape to close
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
        setQuery('');
      }

      // Arrow navigation
      if (isOpen && filteredResults.length > 0) {
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          setSelectedIndex((prev) => 
            prev < filteredResults.length - 1 ? prev + 1 : prev
          );
        }
        if (e.key === 'ArrowUp') {
          e.preventDefault();
          setSelectedIndex((prev) => (prev > 0 ? prev - 1 : 0));
        }
        if (e.key === 'Enter') {
          e.preventDefault();
          const selected = filteredResults[selectedIndex];
          if (selected) {
            handleSelectResult(selected);
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, filteredResults, selectedIndex]);

  // Reset selected index when query changes
  useEffect(() => {
    setSelectedIndex(0);
  }, [query]);

  const handleSelectResult = useCallback((result: SearchResult) => {
    navigate(result.path);
    setIsOpen(false);
    setQuery('');
    
    // Add to recent searches
    setRecentSearches((prev) => {
      const updated = [result.title, ...prev.filter((s) => s !== result.title)];
      return updated.slice(0, recentSearchesLimit);
    });
  }, [navigate, setIsOpen, setQuery, recentSearchesLimit]);

  const handleClose = useCallback(() => {
    setIsOpen(false);
    setQuery('');
  }, [setIsOpen, setQuery]);

  const getCategoryIcon = (category: SearchResult['category']) => {
    switch (category) {
      case 'page': return FileText;
      case 'task': return TrendingUp;
      case 'file': return Code;
      case 'user': return Users;
      case 'setting': return Settings;
      default: return FileText;
    }
  };

  const getCategoryColor = (category: SearchResult['category']) => {
    switch (category) {
      case 'page': return 'text-info-color dark:text-info-color';
      case 'task': return 'text-success-color dark:text-success-color';
      case 'file': return 'text-info-color dark:text-info-color';
      case 'user': return 'text-warning-color dark:text-warning-color';
      case 'setting': return 'text-fg-muted dark:text-fg-muted';
      default: return 'text-fg-muted dark:text-fg-muted';
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-start justify-center pt-20">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={handleClose}
          className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        />

        {/* Search Modal */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: -20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: -20 }}
          className="relative w-full max-w-2xl overflow-hidden rounded-lg border border-border bg-white shadow-2xl dark:border-border dark:bg-surface"
        >
          {/* Search Input */}
          <div className="flex items-center gap-3 border-b border-border p-4 dark:border-border">
            <Search className="h-5 w-5 text-fg-muted" />
            <Input
              type="text"
              placeholder={placeholder}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              autoFocus
              className="flex-1 border-0 bg-transparent p-0 text-lg focus:ring-0"
            />
            <div className="flex items-center gap-2 text-xs text-fg-muted">
              <kbd className="rounded bg-surface-muted px-2 py-1 font-mono dark:bg-surface">
                <Command className="inline h-3 w-3" />K
              </kbd>
              <Button
                onClick={handleClose}
                className="rounded p-1 hover:bg-surface-muted dark:hover:bg-surface"
                aria-label={t('search.close')}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {/* Results */}
          <div className="max-h-96 overflow-y-auto">
            {isLoading ? (
              <div className="flex items-center justify-center p-8">
                <div role="status" className="h-6 w-6 animate-spin rounded-full border-2 border-border border-t-blue-600" />
              </div>
            ) : filteredResults.length > 0 ? (
              <div className="p-2">
                {filteredResults.map((result, index) => {
                  const Icon = result.icon || getCategoryIcon(result.category);
                  const isSelected = index === selectedIndex;

                  return (
                    <Button
                      key={result.id}
                      onClick={() => handleSelectResult(result)}
                      onMouseEnter={() => setSelectedIndex(index)}
                      className={cn(
                        'flex w-full items-center gap-3 rounded-lg p-3 text-left transition-colors',
                        isSelected
                          ? 'bg-info-bg dark:bg-info-bg'
                          : 'hover:bg-surface-muted dark:hover:bg-surface'
                      )}
                    >
                      <div className={cn('rounded-lg bg-surface-muted p-2 dark:bg-surface', getCategoryColor(result.category))}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-fg dark:text-fg-muted">
                          {result.title}
                        </div>
                        {result.description && (
                          <div className="truncate text-sm text-fg-muted dark:text-fg-muted">
                            {result.description}
                          </div>
                        )}
                      </div>
                      <ArrowRight className="h-4 w-4 text-fg-muted" />
                    </Button>
                  );
                })}
              </div>
            ) : query.trim() ? (
              <div className="p-8 text-center text-fg-muted dark:text-fg-muted">
                No results found for "{query}"
              </div>
            ) : (
              <div className="p-4">
                {recentSearches.length > 0 && (
                  <>
                    <div className="mb-2 flex items-center gap-2 px-3 text-xs font-semibold text-fg-muted dark:text-fg-muted">
                      <Clock className="h-3 w-3" />
                      Recent Searches
                    </div>
                    <div className="space-y-1">
                      {recentSearches.map((search, index) => (
                        <Button
                          key={index}
                          onClick={() => setQuery(search)}
                          className="flex w-full items-center gap-3 rounded-lg p-3 text-left text-sm text-fg transition-colors hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface"
                        >
                          <Clock className="h-4 w-4 text-fg-muted" />
                          {search}
                        </Button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="border-t border-border bg-surface-muted p-3 dark:border-border dark:bg-surface">
            <div className="flex items-center justify-between text-xs text-fg-muted">
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-1">
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-surface">↑</kbd>
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-surface">↓</kbd>
                  <span>Navigate</span>
                </div>
                <div className="flex items-center gap-1">
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-surface">↵</kbd>
                  <span>Select</span>
                </div>
                <div className="flex items-center gap-1">
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-surface">esc</kbd>
                  <span>Close</span>
                </div>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};

export default GlobalSearch;
