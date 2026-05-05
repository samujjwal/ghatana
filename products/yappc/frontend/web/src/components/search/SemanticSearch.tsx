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
      return 'text-success-color dark:text-success-color bg-success-bg dark:bg-success-bg/20';
    case 'semantic':
      return 'text-info-color dark:text-info-color bg-info-bg dark:bg-info-bg/20';
    case 'fuzzy':
      return 'text-info-color dark:text-info-color bg-info-bg dark:bg-info-bg/20';
    default:
      return 'text-fg-muted dark:text-fg-muted bg-surface-muted dark:bg-surface/20';
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
        <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-fg-muted" />
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
            className="absolute right-2 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg-muted"
          >
            <ClearIcon className="w-4 h-4" />
          </Button>
        )}
      </Box>

      {/* Search Status */}
      {query && (
        <Box className="flex items-center justify-between mb-2 text-xs text-fg-muted">
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
        <Card variant="outlined" className="mb-4 border-destructive-border dark:border-destructive-border">
          <CardContent className="p-3">
            <Typography className="text-sm text-destructive dark:text-destructive">
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
                    <div className="flex-1 h-1.5 bg-surface-muted dark:bg-surface-muted rounded-full overflow-hidden">
                      <div
                        className="h-full bg-info-bg"
                        style={{ width: `${result.score * 100}%` }}
                      />
                    </div>
                    <Typography className="text-xs text-fg-muted">
                      {Math.round(result.score * 100)}%
                    </Typography>
                  </div>

                  {/* Description */}
                  {result.document.content && (
                    <Typography className="text-xs text-fg-muted dark:text-fg-muted line-clamp-2">
                      {result.document.content}
                    </Typography>
                  )}

                  {/* Highlights */}
                  {showHighlights && query && result.highlights && result.highlights.length > 0 && (
                    <div className="mt-2">
                      {result.highlights.map((highlight, idx) => (
                        <Typography
                          key={idx}
                          className="text-xs text-info-color dark:text-info-color bg-info-bg dark:bg-info-bg/20 px-1 py-0.5 rounded inline-block mr-1"
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
              <SearchIcon className="w-12 h-12 mx-auto mb-2 text-fg-muted" />
              <Typography className="text-fg-muted dark:text-fg-muted">
                No results found for "{query}"
              </Typography>
              <Typography className="text-sm text-fg-muted dark:text-fg-muted mt-1">
                Try different keywords or check your spelling
              </Typography>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
