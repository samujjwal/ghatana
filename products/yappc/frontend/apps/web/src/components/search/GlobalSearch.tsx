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
import { Input } from '@ghatana/ui';
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
  const navigate = useNavigate();
  
  const isOpen = useAtomValue(globalSearchOpenAtom);
  const setIsOpen = useSetAtom(globalSearchOpenAtom);
  const query = useAtomValue(globalSearchQueryAtom);
  const setQuery = useSetAtom(globalSearchQueryAtom);
  const searchResults = useAtomValue(globalSearchResultsAtom);
  const isLoading = useAtomValue(globalSearchLoadingAtom);

  const [selectedIndex, setSelectedIndex] = useState(0);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);

  // Mock search data - will be replaced with real search implementation
  const mockResults: SearchResult[] = useMemo(() => [
    {
      id: '1',
      title: 'Project Dashboard',
      description: 'View project overview and metrics',
      category: 'page',
      path: '/project/123/dashboard',
      icon: TrendingUp,
    },
    {
      id: '2',
      title: 'Development Canvas',
      description: 'Visual project planning canvas',
      category: 'page',
      path: '/project/123/dev/canvas',
      icon: Code,
    },
    {
      id: '3',
      title: 'Team Settings',
      description: 'Manage team members and permissions',
      category: 'setting',
      path: '/project/123/settings/team',
      icon: Users,
    },
    {
      id: '4',
      title: 'Sprint Board',
      description: 'Manage sprint tasks and progress',
      category: 'page',
      path: '/project/123/dev/sprint',
      icon: FileText,
    },
  ], []);

  // Fuzzy search filtering
  const filteredResults = useMemo(() => {
    if (!query.trim()) return [];

    const results = mockResults
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
  }, [query, mockResults, maxResults]);

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
      case 'page': return 'text-blue-600 dark:text-blue-400';
      case 'task': return 'text-green-600 dark:text-green-400';
      case 'file': return 'text-purple-600 dark:text-purple-400';
      case 'user': return 'text-orange-600 dark:text-orange-400';
      case 'setting': return 'text-gray-600 dark:text-gray-400';
      default: return 'text-gray-600 dark:text-gray-400';
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
          className="relative w-full max-w-2xl overflow-hidden rounded-lg border border-gray-200 bg-white shadow-2xl dark:border-gray-800 dark:bg-gray-950"
        >
          {/* Search Input */}
          <div className="flex items-center gap-3 border-b border-gray-200 p-4 dark:border-gray-800">
            <Search className="h-5 w-5 text-gray-400" />
            <Input
              type="text"
              placeholder={placeholder}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              autoFocus
              className="flex-1 border-0 bg-transparent p-0 text-lg focus:ring-0"
            />
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <kbd className="rounded bg-gray-100 px-2 py-1 font-mono dark:bg-gray-800">
                <Command className="inline h-3 w-3" />K
              </kbd>
              <button
                onClick={handleClose}
                className="rounded p-1 hover:bg-gray-100 dark:hover:bg-gray-800"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          {/* Results */}
          <div className="max-h-96 overflow-y-auto">
            {isLoading ? (
              <div className="flex items-center justify-center p-8">
                <div className="h-6 w-6 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600" />
              </div>
            ) : filteredResults.length > 0 ? (
              <div className="p-2">
                {filteredResults.map((result, index) => {
                  const Icon = result.icon || getCategoryIcon(result.category);
                  const isSelected = index === selectedIndex;

                  return (
                    <button
                      key={result.id}
                      onClick={() => handleSelectResult(result)}
                      onMouseEnter={() => setSelectedIndex(index)}
                      className={cn(
                        'flex w-full items-center gap-3 rounded-lg p-3 text-left transition-colors',
                        isSelected
                          ? 'bg-blue-50 dark:bg-blue-950'
                          : 'hover:bg-gray-50 dark:hover:bg-gray-900'
                      )}
                    >
                      <div className={cn('rounded-lg bg-gray-100 p-2 dark:bg-gray-800', getCategoryColor(result.category))}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-gray-900 dark:text-gray-100">
                          {result.title}
                        </div>
                        {result.description && (
                          <div className="truncate text-sm text-gray-500 dark:text-gray-400">
                            {result.description}
                          </div>
                        )}
                      </div>
                      <ArrowRight className="h-4 w-4 text-gray-400" />
                    </button>
                  );
                })}
              </div>
            ) : query.trim() ? (
              <div className="p-8 text-center text-gray-500 dark:text-gray-400">
                No results found for "{query}"
              </div>
            ) : (
              <div className="p-4">
                {recentSearches.length > 0 && (
                  <>
                    <div className="mb-2 flex items-center gap-2 px-3 text-xs font-semibold text-gray-500 dark:text-gray-400">
                      <Clock className="h-3 w-3" />
                      Recent Searches
                    </div>
                    <div className="space-y-1">
                      {recentSearches.map((search, index) => (
                        <button
                          key={index}
                          onClick={() => setQuery(search)}
                          className="flex w-full items-center gap-3 rounded-lg p-3 text-left text-sm text-gray-700 transition-colors hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-900"
                        >
                          <Clock className="h-4 w-4 text-gray-400" />
                          {search}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="border-t border-gray-200 bg-gray-50 p-3 dark:border-gray-800 dark:bg-gray-900">
            <div className="flex items-center justify-between text-xs text-gray-500">
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-1">
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-gray-800">↑</kbd>
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-gray-800">↓</kbd>
                  <span>Navigate</span>
                </div>
                <div className="flex items-center gap-1">
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-gray-800">↵</kbd>
                  <span>Select</span>
                </div>
                <div className="flex items-center gap-1">
                  <kbd className="rounded bg-white px-1.5 py-0.5 font-mono dark:bg-gray-800">esc</kbd>
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
