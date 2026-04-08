/**
 * Semantic Search Component
 *
 * AI-powered search component with semantic matching across all content types.
 * Provides intelligent search results with highlights and match types.
 *
 * @doc.type component
 * @doc.purpose Semantic search interface
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import { Search as SearchIcon, X as ClearIcon, Zap as LightningIcon, Brain as BrainIcon, Target as TargetIcon } from 'lucide-react';
import { Typography, Input, Box, Card, CardContent, Chip, Button } from '@ghatana/design-system';
import { useSemanticSearch } from '../../hooks/useSemanticSearch';
import type { SearchDocument, SearchResult } from '../../services/search/SearchService';

// ============================================================================
// Types
// ============================================================================

export interface SemanticSearchProps {
  documents: SearchDocument[];
  onResultClick?: (result: SearchResult) => void;
  placeholder?: string;
  maxResults?: number;
  showMatchType?: boolean;
  showHighlights?: boolean;
  className?: string;
}

// ============================================================================
// Match Type Icon Mapping
// ============================================================================

const getMatchTypeIcon = (matchType: SearchResult['matchType']) => {
  switch (matchType) {
    case 'exact':
      return <TargetIcon className="w-4 h-4" />;
    case 'semantic':
      return <BrainIcon className="w-4 h-4" />;
    case 'fuzzy':
      return <LightningIcon className="w-4 h-4" />;
    default:
      return <SearchIcon className="w-4 h-4" />;
  }
};

const getMatchTypeColor = (matchType: SearchResult['matchType']) => {
  switch (matchType) {
    case 'exact':
      return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20';
    case 'semantic':
      return 'text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-900/20';
    case 'fuzzy':
      return 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20';
    default:
      return 'text-gray-600 dark:text-gray-400 bg-gray-50 dark:bg-gray-900/20';
  }
};

const getMatchTypeLabel = (matchType: SearchResult['matchType']) => {
  switch (matchType) {
    case 'exact':
      return 'Exact';
    case 'semantic':
      return 'AI Match';
    case 'fuzzy':
      return 'Fuzzy';
    default:
      return 'Match';
  }
};

// ============================================================================
// Semantic Search Component
// ============================================================================

/**
 * Semantic Search Component
 */
export function SemanticSearch({
  documents,
  onResultClick,
  placeholder = 'Search everything...',
  maxResults = 10,
  showMatchType = true,
  showHighlights = true,
  className = '',
}: SemanticSearchProps): ReactNode {
  const {
    query,
    setQuery,
    results,
    isLoading,
    error,
    searchTime,
    totalResults,
    highlightQuery,
    clearQuery,
  } = useSemanticSearch({
    documents,
    enabled: true,
    limit: maxResults,
  });

  const handleResultClick = useCallback(
    (result: SearchResult) => {
      onResultClick?.(result);
    },
    [onResultClick]
  );

  return (
    <div className={`w-full ${className}`}>
      {/* Search Input */}
      <Box className="relative mb-4">
        <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={placeholder}
          className="pl-10 pr-10"
        />
        {query && (
          <Button
            size="sm"
            variant="text"
            onClick={clearQuery}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
          >
            <ClearIcon className="w-4 h-4" />
          </Button>
        )}
      </Box>

      {/* Search Status */}
      {query && (
        <Box className="flex items-center justify-between mb-2 text-xs text-gray-500">
          <Typography>
            {isLoading ? 'Searching...' : `${totalResults} results found`}
          </Typography>
          {!isLoading && searchTime > 0 && (
            <Typography>
              {searchTime.toFixed(2)}ms
            </Typography>
          )}
        </Box>
      )}

      {/* Error State */}
      {error && (
        <Card variant="outlined" className="mb-4 border-red-200 dark:border-red-800">
          <CardContent className="p-3">
            <Typography className="text-sm text-red-600 dark:text-red-400">
              Search error: {error.message}
            </Typography>
          </CardContent>
        </Card>
      )}

      {/* Results */}
      <div className="space-y-2">
        {results.map((result) => (
          <Card
            key={result.document.id}
            variant="outlined"
            className="cursor-pointer hover:shadow-md transition-shadow"
            onClick={() => handleResultClick(result)}
          >
            <CardContent className="p-3">
              <div className="flex items-start gap-3">
                {/* Match Type Badge */}
                {showMatchType && (
                  <div className={`flex-shrink-0 p-1.5 rounded-md ${getMatchTypeColor(result.matchType)}`}>
                    {getMatchTypeIcon(result.matchType)}
                  </div>
                )}

                {/* Content */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Typography className="font-medium text-sm truncate">
                      {result.document.title}
                    </Typography>
                    <Chip
                      size="sm"
                      label={result.document.type}
                      className="text-xs"
                    />
                    {showMatchType && (
                      <Chip
                        size="sm"
                        label={getMatchTypeLabel(result.matchType)}
                        className={`text-xs ${getMatchTypeColor(result.matchType)}`}
                      />
                    )}
                  </div>

                  {/* Score */}
                  <div className="flex items-center gap-2 mb-1">
                    <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-blue-500"
                        style={{ width: `${result.score * 100}%` }}
                      />
                    </div>
                    <Typography className="text-xs text-gray-500">
                      {Math.round(result.score * 100)}%
                    </Typography>
                  </div>

                  {/* Description */}
                  {result.document.content && (
                    <Typography className="text-xs text-gray-600 dark:text-gray-400 line-clamp-2">
                      {result.document.content}
                    </Typography>
                  )}

                  {/* Highlights */}
                  {showHighlights && query && result.highlights && result.highlights.length > 0 && (
                    <div className="mt-2">
                      {result.highlights.map((highlight, idx) => (
                        <Typography
                          key={idx}
                          className="text-xs text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 px-1 py-0.5 rounded inline-block mr-1"
                        >
                          {highlight}
                        </Typography>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        ))}

        {/* No Results */}
        {query && !isLoading && results.length === 0 && !error && (
          <Card variant="outlined">
            <CardContent className="p-6 text-center">
              <SearchIcon className="w-12 h-12 mx-auto mb-2 text-gray-400" />
              <Typography className="text-gray-600 dark:text-gray-400">
                No results found for "{query}"
              </Typography>
              <Typography className="text-sm text-gray-500 dark:text-gray-500 mt-1">
                Try different keywords or check your spelling
              </Typography>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
