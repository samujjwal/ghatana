/**
 * @ghatana/yappc-ide - Search Bar Component
 * 
 * Advanced file search with filtering, highlighting, and history.
 * Integrates with advanced file operations hook.
 * 
 * @doc.type component
 * @doc.purpose Advanced file search for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useAdvancedFileOperations } from '../hooks/useAdvancedFileOperations';
import type { FileSearchQuery, SearchResult } from '../hooks/useAdvancedFileOperations';

/**
 * Search Bar Props
 */
export interface SearchBarProps {
  className?: string;
  onResultSelect?: (result: SearchResult) => void;
  placeholder?: string;
  showHistory?: boolean;
  autoFocus?: boolean;
}

/**
 * Search history item
 */
interface SearchHistoryItem {
  query: FileSearchQuery;
  timestamp: number;
  resultCount: number;
}

/**
 * Search Bar Component
 */
export const SearchBar: React.FC<SearchBarProps> = ({
  className = '',
  onResultSelect,
  placeholder = 'Search files...',
  showHistory = true,
  autoFocus = false,
}) => {
  const {
    searchFiles,
    clearSearch,
    searchResults,
    isSearching,
  } = useAdvancedFileOperations();

  // Search state
  const [query, setQuery] = useState('');
  const [searchQuery, setSearchQuery] = useState<FileSearchQuery>({
    pattern: '',
    caseSensitive: false,
    regex: false,
  });
  const [isExpanded, setIsExpanded] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [history, setHistory] = useState<SearchHistoryItem[]>([]);

  // Refs
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Load search history from localStorage
  useEffect(() => {
    if (showHistory) {
      try {
        const saved = localStorage.getItem('ide-search-history');
        if (saved) {
          setHistory(JSON.parse(saved));
        }
      } catch (error) {
        console.warn('Failed to load search history:', error);
      }
    }
  }, [showHistory]);

  // Save search history to localStorage
  const saveToHistory = useCallback((query: FileSearchQuery, resultCount: number) => {
    if (!showHistory) return;

    const historyItem: SearchHistoryItem = {
      query,
      timestamp: Date.now(),
      resultCount,
    };

    setHistory(prev => {
      const updated = [historyItem, ...prev.filter(h => 
        JSON.stringify(h.query) !== JSON.stringify(query)
      )].slice(0, 10); // Keep last 10 searches

      try {
        localStorage.setItem('ide-search-history', JSON.stringify(updated));
      } catch (error) {
        console.warn('Failed to save search history:', error);
      }

      return updated;
    });
  }, [showHistory]);

  // Handle search input
  const handleInputChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setQuery(value);
    setSearchQuery(prev => ({ ...prev, pattern: value }));
    setSelectedIndex(-1);
  }, []);

  // Handle search submission
  const handleSearch = useCallback(async () => {
    if (!searchQuery.pattern.trim()) return;

    setIsExpanded(true);
    await searchFiles(searchQuery);
    saveToHistory(searchQuery, searchResults.length);
  }, [searchQuery, searchFiles, searchResults.length, saveToHistory]);

  // Handle keyboard navigation
  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    const results = isExpanded ? searchResults : history;

    switch (event.key) {
      case 'Enter':
        event.preventDefault();
        if (selectedIndex >= 0 && selectedIndex < results.length) {
          if (isExpanded) {
            onResultSelect?.(results[selectedIndex] as SearchResult);
          } else {
            // Load historical search
            const historyItem = results[selectedIndex] as SearchHistoryItem;
            setQuery(historyItem.query.pattern);
            setSearchQuery(historyItem.query);
            handleSearch();
          }
        } else {
          handleSearch();
        }
        break;

      case 'Escape':
        event.preventDefault();
        setIsExpanded(false);
        setSelectedIndex(-1);
        inputRef.current?.blur();
        break;

      case 'ArrowDown':
        event.preventDefault();
        setSelectedIndex(prev => 
          prev < results.length - 1 ? prev + 1 : 0
        );
        break;

      case 'ArrowUp':
        event.preventDefault();
        setSelectedIndex(prev => 
          prev > 0 ? prev - 1 : results.length - 1
        );
        break;

      case 'Tab':
        event.preventDefault();
        // Focus on search options
        break;
    }
  }, [isExpanded, searchResults, history, selectedIndex, onResultSelect, handleSearch]);

  // Handle result click
  const handleResultClick = useCallback((result: SearchResult) => {
    onResultSelect?.(result);
    setIsExpanded(false);
    setSelectedIndex(-1);
  }, [onResultSelect]);

  // Handle history item click
  const handleHistoryClick = useCallback((historyItem: SearchHistoryItem) => {
    setQuery(historyItem.query.pattern);
    setSearchQuery(historyItem.query);
    handleSearch();
  }, [handleSearch]);

  // Clear search
  const handleClear = useCallback(() => {
    setQuery('');
    setSearchQuery({ pattern: '', caseSensitive: false, regex: false });
    clearSearch();
    setIsExpanded(false);
    setSelectedIndex(-1);
  }, [clearSearch]);

  // Toggle search options
  const toggleOptions = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);

  // Update search options
  const updateSearchOption = useCallback(<K extends keyof FileSearchQuery>(
    key: K,
    value: FileSearchQuery[K]
  ) => {
    setSearchQuery(prev => ({ ...prev, [key]: value }));
  }, []);

  // Highlight text in search results
  const highlightText = useCallback((text: string, pattern: string) => {
    if (!pattern) return text;

    const regex = new RegExp(`(${pattern})`, 'gi');
    const parts = text.split(regex);

    return parts.map((part, index) => 
      regex.test(part) ? (
        <mark key={index} className="bg-yellow-200 dark:bg-yellow-800 text-yellow-900 dark:text-yellow-100">
          {part}
        </mark>
      ) : (
        part
      )
    );
  }, []);

  // Format file size
  const formatFileSize = useCallback((bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }, []);

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Search Input */}
      <div className="flex items-center border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800">
        <div className="flex items-center px-3">
          {isSearching ? (
            <div className="animate-spin h-4 w-4 border-2 border-blue-500 border-t-transparent rounded-full" />
          ) : (
            <svg className="h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          )}
        </div>
        
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={() => setIsExpanded(true)}
          placeholder={placeholder}
          autoFocus={autoFocus}
          className="flex-1 px-2 py-2 bg-transparent outline-none text-sm"
        />
        
        {query && (
          <button
            onClick={handleClear}
            className="px-2 py-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
        
        <button
          onClick={toggleOptions}
          className="px-2 py-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 border-l border-gray-300 dark:border-gray-600"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
          </svg>
        </button>
      </div>

      {/* Search Results Dropdown */}
      {isExpanded && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg shadow-lg z-50 max-h-96 overflow-y-auto">
          {/* Search Options */}
          <div className="p-3 border-b border-gray-200 dark:border-gray-700">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={searchQuery.caseSensitive}
                  onChange={(e) => updateSearchOption('caseSensitive', e.target.checked)}
                  className="rounded"
                />
                <span>Case Sensitive</span>
              </label>
              
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={searchQuery.regex}
                  onChange={(e) => updateSearchOption('regex', e.target.checked)}
                  className="rounded"
                />
                <span>Regular Expression</span>
              </label>
            </div>
          </div>

          {/* Results or History */}
          <div>
            {searchResults.length > 0 ? (
              /* Search Results */
              searchResults.map((result, index) => (
                <div
                  key={result.file.id}
                  className={`px-3 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer border-b border-gray-100 dark:border-gray-700 ${
                    index === selectedIndex ? 'bg-blue-50 dark:bg-blue-900/20' : ''
                  }`}
                  onClick={() => handleResultClick(result)}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2 flex-1 min-w-0">
                      <span className="text-blue-600 dark:text-blue-400">
                        {highlightText(result.file.name, searchQuery.pattern)}
                      </span>
                      <span className="text-xs text-gray-500 dark:text-gray-400">
                        {formatFileSize(result.file.size)}
                      </span>
                    </div>
                    <span className="text-xs text-gray-500 dark:text-gray-400">
                      Score: {result.score}
                    </span>
                  </div>
                  
                  <div className="text-xs text-gray-600 dark:text-gray-400 truncate">
                    {result.file.path}
                  </div>
                  
                  {result.matches.length > 0 && (
                    <div className="mt-1 text-xs text-gray-600 dark:text-gray-400">
                      {result.matches.slice(0, 2).map((match, i) => (
                        <div key={i} className="truncate">
                          Line {match.line}: {highlightText(match.text, searchQuery.pattern)}
                        </div>
                      ))}
                      {result.matches.length > 2 && (
                        <div className="text-gray-500">
                          ... and {result.matches.length - 2} more matches
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))
            ) : history.length > 0 && !query ? (
              /* Search History */
              <div className="p-2">
                <div className="text-xs text-gray-500 dark:text-gray-400 px-1 pb-2">
                  Recent Searches
                </div>
                {history.map((item, index) => (
                  <div
                    key={item.timestamp}
                    className={`px-2 py-1 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer rounded text-sm ${
                      index === selectedIndex ? 'bg-blue-50 dark:bg-blue-900/20' : ''
                    }`}
                    onClick={() => handleHistoryClick(item)}
                  >
                    <div className="flex items-center justify-between">
                      <span>{item.query.pattern}</span>
                      <span className="text-xs text-gray-500">
                        {item.resultCount} results
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : query && !isSearching ? (
              /* No Results */
              <div className="p-4 text-center text-gray-500 dark:text-gray-400 text-sm">
                No files found matching "{query}"
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
};
