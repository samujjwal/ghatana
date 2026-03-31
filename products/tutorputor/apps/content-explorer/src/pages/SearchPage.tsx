import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from 'react-query';
import { Search, SearchResults } from '../components/search';
import { RecommendationPanel } from '../components/recommendations';
import { searchContent, getRecommendations } from '../services/contentApi';

/**
 * Search Page
 * 
 * Main search interface with semantic search, filters, and recommendations.
 * Integrates with the real semantic search service.
 * 
 * @doc.type component
 * @doc.purpose Main search interface for content discovery
 * @doc.layer product
 * @doc.pattern Page
 */
export function SearchPage(): React.ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState(searchParams.get('q') || '');
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);

  // Search query
  const {
    data: searchResults,
    isLoading: isSearchLoading,
    error: searchError,
    refetch: refetchSearch
  } = useQuery(
    ['search', query],
    () => searchContent(query),
    {
      enabled: query.length > 0,
      keepPreviousData: true,
    }
  );

  // Recommendations for selected asset
  const {
    data: recommendations,
    isLoading: isRecommendationsLoading
  } = useQuery(
    ['recommendations', selectedAssetId],
    () => getRecommendations(selectedAssetId!),
    {
      enabled: !!selectedAssetId,
    }
  );

  const handleSearch = (newQuery: string) => {
    setQuery(newQuery);
    if (newQuery) {
      setSearchParams({ q: newQuery });
    } else {
      setSearchParams({});
    }
  };

  const handleAssetSelect = (assetId: string) => {
    setSelectedAssetId(assetId);
  };

  return (
    <div className="search-page">
      <div className="search-container">
        <div className="search-header">
          <h1>Search Content</h1>
          <p>Discover learning content using semantic search and intelligent recommendations</p>
        </div>

        <Search
          value={query}
          onChange={handleSearch}
          placeholder="Search for concepts, topics, or skills..."
          isLoading={isSearchLoading}
        />

        {searchError && (
          <div className="search-error">
            <p>Search failed: {searchError.message}</p>
            <button onClick={() => refetchSearch()}>Retry</button>
          </div>
        )}

        <div className="search-content">
          <div className="search-results">
            <SearchResults
              results={searchResults?.results || []}
              isLoading={isSearchLoading}
              onAssetSelect={handleAssetSelect}
              selectedAssetId={selectedAssetId}
              queryTime={searchResults?.queryTime}
              explanation={searchResults?.explanation}
            />
          </div>

          <div className="recommendations-panel">
            <RecommendationPanel
              recommendations={recommendations}
              isLoading={isRecommendationsLoading}
              selectedAssetId={selectedAssetId}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
