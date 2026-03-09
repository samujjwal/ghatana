/**
 * @ghatana/yappc-ide - Advanced Search Panel Component
 * 
 * Comprehensive search interface with filters, patterns, and saved queries.
 * 
 * @doc.type component
 * @doc.purpose Advanced search UI for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useAdvancedFileOperations } from '../hooks/useAdvancedFileOperations';
import { InteractiveButton } from './MicroInteractions';
import type { FileSearchQuery, SearchResult } from '../hooks/useAdvancedFileOperations';

/**
 * Advanced Search Panel Props
 */
export interface AdvancedSearchPanelProps {
  className?: string;
  onResultSelect?: (result: SearchResult) => void;
  onClose?: () => void;
  isVisible: boolean;
}

/**
 * Saved search query
 */
interface SavedQuery {
  id: string;
  name: string;
  query: FileSearchQuery;
  createdAt: number;
  lastUsed: number;
}

/**
 * Advanced Search Panel Component
 */
export const AdvancedSearchPanel: React.FC<AdvancedSearchPanelProps> = ({
  className = '',
  onResultSelect,
  onClose,
  isVisible,
}) => {
  const {
    searchFiles,
    clearSearch,
    searchResults,
    isSearching,
  } = useAdvancedFileOperations();

  const [query, setQuery] = useState<FileSearchQuery>({
    pattern: '',
    caseSensitive: false,
    regex: false,
  });

  const [savedQueries, setSavedQueries] = useState<SavedQuery[]>([]);
  const [showSavedQueries, setShowSavedQueries] = useState(false);
  const [searchHistory, setSearchHistory] = useState<FileSearchQuery[]>([]);

  // Load saved queries from localStorage
  useEffect(() => {
    try {
      const saved = localStorage.getItem('ide-saved-queries');
      if (saved) {
        setSavedQueries(JSON.parse(saved));
      }
      
      const history = localStorage.getItem('ide-search-history');
      if (history) {
        setSearchHistory(JSON.parse(history));
      }
    } catch (error) {
      console.warn('Failed to load search data:', error);
    }
  }, []);

  const handleSearch = useCallback(async () => {
    if (!query.pattern.trim()) return;

    try {
      await searchFiles(query);
      
      // Update search history
      const newHistory = [query, ...searchHistory.filter(h => h.pattern !== query.pattern)].slice(0, 20);
      setSearchHistory(newHistory);
      localStorage.setItem('ide-search-history', JSON.stringify(newHistory));
    } catch (error) {
      console.error('Search failed:', error);
    }
  }, [query, searchFiles, searchHistory]);

  const handleSaveQuery = useCallback(() => {
    const name = prompt('Enter a name for this search query:');
    if (!name?.trim()) return;

    const newQuery: SavedQuery = {
      id: Math.random().toString(36).substr(2, 9),
      name: name.trim(),
      query: { ...query },
      createdAt: Date.now(),
      lastUsed: Date.now(),
    };

    const updated = [...savedQueries, newQuery];
    setSavedQueries(updated);
    localStorage.setItem('ide-saved-queries', JSON.stringify(updated));
  }, [query, savedQueries]);

  const handleLoadQuery = useCallback((savedQuery: SavedQuery) => {
    setQuery(savedQuery.query);
    setShowSavedQueries(false);
  }, []);

  const handleDeleteQuery = useCallback((id: string) => {
    const updated = savedQueries.filter(q => q.id !== id);
    setSavedQueries(updated);
    localStorage.setItem('ide-saved-queries', JSON.stringify(updated));
  }, [savedQueries]);

  const handleKeyPress = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleSearch();
    }
  }, [handleSearch]);

  if (!isVisible) return null;

  return (
    <div className={`fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 ${className}`}>
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-2xl w-full max-w-4xl max-h-[80vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Advanced Search
          </h2>
          <div className="flex items-center gap-2">
            <InteractiveButton
              variant="ghost"
              size="sm"
              onClick={() => setShowSavedQueries(!showSavedQueries)}
            >
              Saved Queries
            </InteractiveButton>
            <InteractiveButton
              variant="ghost"
              size="sm"
              onClick={onClose}
            >
              ✕
            </InteractiveButton>
          </div>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {/* Search Form */}
          <div className="w-1/3 p-4 border-r border-gray-200 dark:border-gray-700 overflow-y-auto">
            <div className="space-y-4">
              {/* Pattern Input */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Search Pattern
                </label>
                <input
                  type="text"
                  value={query.pattern}
                  onChange={(e) => setQuery({ ...query, pattern: e.target.value })}
                  onKeyPress={handleKeyPress}
                  placeholder="Enter search pattern..."
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                  autoFocus
                />
              </div>

              {/* Content Search */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Content Search
                </label>
                <textarea
                  value={query.content || ''}
                  onChange={(e) => setQuery({ ...query, content: e.target.value })}
                  placeholder="Search within file contents..."
                  rows={3}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>

              {/* File Types */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  File Types
                </label>
                <input
                  type="text"
                  value={query.fileTypes?.join(', ') || ''}
                  onChange={(e) => setQuery({ 
                    ...query, 
                    fileTypes: e.target.value.split(',').map(t => t.trim()).filter(Boolean) 
                  })}
                  placeholder="js, ts, jsx, tsx"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>

              {/* Size Range */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Size Range (bytes)
                </label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    value={query.minSize || ''}
                    onChange={(e) => setQuery({ ...query, minSize: e.target.value ? Number(e.target.value) : undefined })}
                    placeholder="Min"
                    className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                  />
                  <input
                    type="number"
                    value={query.maxSize || ''}
                    onChange={(e) => setQuery({ ...query, maxSize: e.target.value ? Number(e.target.value) : undefined })}
                    placeholder="Max"
                    className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                  />
                </div>
              </div>

              {/* Options */}
              <div className="space-y-2">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={query.caseSensitive}
                    onChange={(e) => setQuery({ ...query, caseSensitive: e.target.checked })}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    Case Sensitive
                  </span>
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={query.regex}
                    onChange={(e) => setQuery({ ...query, regex: e.target.checked })}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    Regular Expression
                  </span>
                </label>
              </div>

              {/* Actions */}
              <div className="flex gap-2">
                <InteractiveButton
                  variant="primary"
                  onClick={handleSearch}
                  disabled={isSearching || !query.pattern.trim()}
                  className="flex-1"
                >
                  {isSearching ? 'Searching...' : 'Search'}
                </InteractiveButton>
                <InteractiveButton
                  variant="secondary"
                  onClick={handleSaveQuery}
                  disabled={!query.pattern.trim()}
                >
                  Save
                </InteractiveButton>
                <InteractiveButton
                  variant="ghost"
                  onClick={() => {
                    clearSearch();
                    setQuery({ pattern: '', caseSensitive: false, regex: false });
                  }}
                >
                  Clear
                </InteractiveButton>
              </div>

              {/* Search History */}
              {searchHistory.length > 0 && (
                <div>
                  <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    Recent Searches
                  </h3>
                  <div className="space-y-1">
                    {searchHistory.slice(0, 5).map((history, index) => (
                      <button
                        key={index}
                        onClick={() => setQuery(history)}
                        className="w-full text-left px-2 py-1 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 rounded"
                      >
                        {history.pattern}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Saved Queries Panel */}
          {showSavedQueries && (
            <div className="w-1/4 p-4 border-r border-gray-200 dark:border-gray-700 overflow-y-auto">
              <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                Saved Queries
              </h3>
              {savedQueries.length === 0 ? (
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  No saved queries yet
                </p>
              ) : (
                <div className="space-y-2">
                  {savedQueries.map((saved) => (
                    <div
                      key={saved.id}
                      className="p-2 border border-gray-200 dark:border-gray-700 rounded"
                    >
                      <div className="flex items-center justify-between">
                        <button
                          onClick={() => handleLoadQuery(saved)}
                          className="flex-1 text-left text-sm font-medium text-gray-900 dark:text-gray-100"
                        >
                          {saved.name}
                        </button>
                        <InteractiveButton
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDeleteQuery(saved.id)}
                        >
                          🗑️
                        </InteractiveButton>
                      </div>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                        {saved.query.pattern}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Results */}
          <div className="flex-1 p-4 overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300">
                Results ({searchResults.length})
              </h3>
              {searchResults.length > 0 && (
                <span className="text-xs text-gray-500 dark:text-gray-400">
                  Press Ctrl+Enter to search
                </span>
              )}
            </div>

            {isSearching ? (
              <div className="flex items-center justify-center py-8">
                <div className="text-sm text-gray-500 dark:text-gray-400">
                  Searching...
                </div>
              </div>
            ) : searchResults.length === 0 ? (
              <div className="text-center py-8 text-gray-500 dark:text-gray-400">
                No results found
              </div>
            ) : (
              <div className="space-y-2">
                {searchResults.map((result, index) => (
                  <div
                    key={index}
                    onClick={() => onResultSelect?.(result)}
                    className="p-3 border border-gray-200 dark:border-gray-700 rounded hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer"
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="font-medium text-gray-900 dark:text-gray-100">
                          {result.file.name}
                        </div>
                        <div className="text-sm text-gray-500 dark:text-gray-400">
                          {result.file.path}
                        </div>
                      </div>
                      <div className="text-xs text-gray-400">
                        Score: {result.score.toFixed(2)}
                      </div>
                    </div>
                    {result.matches.length > 0 && (
                      <div className="mt-2 space-y-1">
                        {result.matches.slice(0, 3).map((match, matchIndex) => (
                          <div key={matchIndex} className="text-xs text-gray-600 dark:text-gray-400 font-mono">
                            Line {match.line}: {match.context}
                          </div>
                        ))}
                        {result.matches.length > 3 && (
                          <div className="text-xs text-gray-500 dark:text-gray-400">
                            ... and {result.matches.length - 3} more matches
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdvancedSearchPanel;
