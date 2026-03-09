/**
 * Search Page
 * Full-featured search with filters and AI-powered hybrid search
 */

import React, { useState, useCallback } from 'react';
import { useSearchMoments } from '../hooks/use-api';
import Layout from '../components/Layout';
import { Search, Filter, X, Clock, FileText, Tag, Layers } from 'lucide-react';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [sphereFilter, setSphereFilter] = useState<string | undefined>();
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [showFilters, setShowFilters] = useState(false);
  const [debouncedQuery, setDebouncedQuery] = useState('');

  const { data: results, isLoading } = useSearchMoments(
    debouncedQuery.length >= 2
      ? { query: debouncedQuery, sphereIds: sphereFilter ? [sphereFilter] : undefined }
      : undefined
  );

  const handleSearch = useCallback(() => {
    setDebouncedQuery(query);
  }, [query]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch();
  };

  const momentTypes = ['TEXT', 'VOICE', 'IMAGE', 'VIDEO', 'DOCUMENT'];

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Search Moments</h1>
          <p className="mt-1 text-sm text-gray-500">
            Find moments using text, tags, or AI-powered semantic search
          </p>
        </div>

        {/* Search Bar */}
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Search by text, tags, or describe what you're looking for..."
              className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent text-sm"
              aria-label="Search moments"
            />
            {query && (
              <button
                onClick={() => { setQuery(''); setDebouncedQuery(''); }}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                aria-label="Clear search"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>
          <button
            onClick={handleSearch}
            className="px-6 py-3 bg-primary-600 text-white rounded-lg hover:bg-primary-700 font-medium text-sm"
          >
            Search
          </button>
          <button
            onClick={() => setShowFilters(!showFilters)}
            className={`px-4 py-3 border rounded-lg text-sm font-medium ${
              showFilters ? 'bg-primary-50 border-primary-300 text-primary-700' : 'border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}
            aria-label="Toggle filters"
            aria-expanded={showFilters}
          >
            <Filter className="h-4 w-4" />
          </button>
        </div>

        {/* Filters */}
        {showFilters && (
          <div className="bg-white p-4 rounded-lg border border-gray-200 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Moment Type</label>
              <select
                value={typeFilter || ''}
                onChange={(e) => setTypeFilter(e.target.value || undefined)}
                className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
              >
                <option value="">All Types</option>
                {momentTypes.map((type) => (
                  <option key={type} value={type}>{type.charAt(0) + type.slice(1).toLowerCase()}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Sphere</label>
              <input
                type="text"
                value={sphereFilter || ''}
                onChange={(e) => setSphereFilter(e.target.value || undefined)}
                placeholder="Sphere ID (optional)"
                className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
              />
            </div>
          </div>
        )}

        {/* Results */}
        {isLoading && (
          <div className="text-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600 mx-auto"></div>
            <p className="mt-2 text-sm text-gray-500">Searching...</p>
          </div>
        )}

        {results && Array.isArray(results) && results.length > 0 && (
          <div className="space-y-3">
            <p className="text-sm text-gray-500">{results.length} result{results.length !== 1 ? 's' : ''} found</p>
            {results.map((moment: { id: string; content?: string; type?: string; tags?: string[]; sphereId?: string; createdAt?: string }) => (
              <div
                key={moment.id}
                className="bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-700">
                        <FileText className="h-3 w-3 mr-1" />
                        {moment.type || 'TEXT'}
                      </span>
                      {moment.sphereId && (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700">
                          <Layers className="h-3 w-3 mr-1" />
                          {moment.sphereId}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-gray-900 line-clamp-3">
                      {moment.content || 'No content preview available'}
                    </p>
                    {moment.tags && moment.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1 mt-2">
                        {moment.tags.map((tag: string) => (
                          <span
                            key={tag}
                            className="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-primary-50 text-primary-700"
                          >
                            <Tag className="h-3 w-3 mr-1" />
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  {moment.createdAt && (
                    <span className="flex items-center text-xs text-gray-400 ml-4 whitespace-nowrap">
                      <Clock className="h-3 w-3 mr-1" />
                      {new Date(moment.createdAt).toLocaleDateString()}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {debouncedQuery && !isLoading && (!results || (Array.isArray(results) && results.length === 0)) && (
          <div className="text-center py-16">
            <Search className="h-12 w-12 text-gray-300 mx-auto" />
            <h3 className="mt-4 text-lg font-medium text-gray-900">No results found</h3>
            <p className="mt-1 text-sm text-gray-500">
              Try different keywords or adjust your filters
            </p>
          </div>
        )}

        {!debouncedQuery && (
          <div className="text-center py-16">
            <Search className="h-12 w-12 text-gray-300 mx-auto" />
            <h3 className="mt-4 text-lg font-medium text-gray-900">Search your moments</h3>
            <p className="mt-1 text-sm text-gray-500">
              Type a query and press Enter or click Search to find moments
            </p>
          </div>
        )}
      </div>
    </Layout>
  );
}
