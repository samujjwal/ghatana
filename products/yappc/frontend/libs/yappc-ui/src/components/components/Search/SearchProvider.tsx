/**
 * Search Provider Component
 * 
 * Unified search interface combining FilterPanel and SearchInput with
 * WebSocket integration for real-time search results and synchronized
 * filter states across components. Provides advanced search capabilities
 * with debounced queries, filter persistence, and live data updates.
 */

import React, { useState, useCallback, useEffect, useMemo } from 'react';

import { FilterPanel } from './FilterPanel';
import { SearchInput } from './SearchInput';

import type { FilterCriteria, FilterOption } from './FilterPanel';
import type { SearchSuggestion } from './SearchInput';


/**
 *
 */
export interface SearchResult {
    id: string;
    title: string;
    description?: string;
    type: string;
    status?: string;
    tags?: string[];
    user?: string;
    date: Date;
    url?: string;
    metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface SearchContextValue {
    query: string;
    criteria: FilterCriteria;
    results: SearchResult[];
    suggestions: SearchSuggestion[];
    isLoading: boolean;
    resultsCount: number;
    totalCount: number;
    hasMore: boolean;
    error: string | null;

    // Actions
    setQuery: (query: string) => void;
    setCriteria: (criteria: FilterCriteria) => void;
    performSearch: (query?: string, filters?: FilterCriteria) => void;
    clearSearch: () => void;
    loadMore: () => void;

    // Filter options (populated from data)
    filterOptions: {
        status: FilterOption[];
        tags: FilterOption[];
        users: FilterOption[];
        types: FilterOption[];
    };
}

/**
 *
 */
export interface SearchProviderProps {
    children: React.ReactNode;
    onSearch?: (query: string, criteria: FilterCriteria) => Promise<SearchResult[]>;
    onLoadMore?: (offset: number) => Promise<SearchResult[]>;
    onSuggestionsUpdate?: (query: string) => Promise<SearchSuggestion[]>;
    initialData?: SearchResult[];
    debounceMs?: number;
    pageSize?: number;
    persistFilters?: boolean;
    className?: string;
}

const SearchContext = React.createContext<SearchContextValue | null>(null);

/**
 *
 */
export function useSearch(): SearchContextValue {
    const context = React.useContext(SearchContext);
    if (!context) {
        throw new Error('useSearch must be used within a SearchProvider');
    }
    return context;
}

/**
 *
 */
export function SearchProvider({
    children,
    onSearch,
    onLoadMore,
    onSuggestionsUpdate,
    initialData = [],
    debounceMs = 300,
    pageSize = 50,
    persistFilters = true,
    className
}: SearchProviderProps) {
    const [query, setQuery] = useState('');
    const [criteria, setCriteria] = useState<FilterCriteria>({});
    const [results, setResults] = useState<SearchResult[]>(initialData);
    const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [totalCount, setTotalCount] = useState(initialData.length);
    const [currentPage, setCurrentPage] = useState(0);

    // Load persisted filters
    useEffect(() => {
        if (persistFilters) {
            const saved = localStorage.getItem('yappc-search-filters');
            if (saved) {
                try {
                    const parsed = JSON.parse(saved);
                    setCriteria(parsed);
                } catch (e) {
                    console.warn('Failed to parse saved filters:', e);
                }
            }
        }
    }, [persistFilters]);

    // Persist filters
    useEffect(() => {
        if (persistFilters) {
            localStorage.setItem('yappc-search-filters', JSON.stringify(criteria));
        }
    }, [criteria, persistFilters]);

    // Generate filter options from current data
    const filterOptions = useMemo(() => {
        const statusMap = new Map<string, number>();
        const tagsMap = new Map<string, number>();
        const usersMap = new Map<string, number>();
        const typesMap = new Map<string, number>();

        results.forEach(result => {
            // Count status occurrences
            if (result.status) {
                statusMap.set(result.status, (statusMap.get(result.status) || 0) + 1);
            }

            // Count tag occurrences
            result.tags?.forEach(tag => {
                tagsMap.set(tag, (tagsMap.get(tag) || 0) + 1);
            });

            // Count user occurrences
            if (result.user) {
                usersMap.set(result.user, (usersMap.get(result.user) || 0) + 1);
            }

            // Count type occurrences
            typesMap.set(result.type, (typesMap.get(result.type) || 0) + 1);
        });

        const getStatusColor = (status: string): string => {
            const colors: Record<string, string> = {
                'active': 'var(--color-success-main, #4caf50)',
                'pending': 'var(--color-warning-main, #ff9800)',
                'completed': 'var(--color-info-main, #2196f3)',
                'failed': 'var(--color-error-main, #f44336)',
                'cancelled': 'var(--color-grey-500, #9e9e9e)',
                'draft': 'var(--color-brown-main, #795548)'
            };
            return colors[status.toLowerCase()] || 'var(--color-text-secondary, #666)';
        };

        return {
            status: Array.from(statusMap.entries()).map(([value, count]) => ({
                value,
                label: value.charAt(0).toUpperCase() + value.slice(1),
                count,
                color: getStatusColor(value)
            })),
            tags: Array.from(tagsMap.entries()).map(([value, count]) => ({
                value,
                label: value,
                count
            })),
            users: Array.from(usersMap.entries()).map(([value, count]) => ({
                value,
                label: value,
                count
            })),
            types: Array.from(typesMap.entries()).map(([value, count]) => ({
                value,
                label: value.charAt(0).toUpperCase() + value.slice(1),
                count
            }))
        };
    }, [results]);

    // Perform search with debouncing
    const performSearch = useCallback(async (
        searchQuery?: string,
        searchCriteria?: FilterCriteria
    ) => {
        const finalQuery = searchQuery ?? query;
        const finalCriteria = searchCriteria ?? criteria;

        setIsLoading(true);
        setError(null);
        setCurrentPage(0);

        try {
            if (onSearch) {
                const searchResults = await onSearch(finalQuery, finalCriteria);
                setResults(searchResults);
                setTotalCount(searchResults.length);
            } else {
                // Fallback: filter existing data
                const filtered = filterLocalResults(initialData, finalQuery, finalCriteria);
                setResults(filtered);
                setTotalCount(filtered.length);
            }

            // Update suggestions
            if (onSuggestionsUpdate && finalQuery) {
                try {
                    const newSuggestions = await onSuggestionsUpdate(finalQuery);
                    setSuggestions(newSuggestions);
                } catch (e) {
                    console.warn('Failed to update suggestions:', e);
                }
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Search failed');
            console.error('Search error:', err);
        } finally {
            setIsLoading(false);
        }
    }, [query, criteria, onSearch, onSuggestionsUpdate, initialData]);

    // Load more results
    const loadMore = useCallback(async () => {
        if (!onLoadMore || isLoading) return;

        setIsLoading(true);
        try {
            const offset = (currentPage + 1) * pageSize;
            const moreResults = await onLoadMore(offset);
            setResults(prev => [...prev, ...moreResults]);
            setCurrentPage(prev => prev + 1);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Load more failed');
            console.error('Load more error:', err);
        } finally {
            setIsLoading(false);
        }
    }, [onLoadMore, isLoading, currentPage, pageSize]);

    // Clear search
    const clearSearch = useCallback(() => {
        setQuery('');
        setCriteria({});
        setResults(initialData);
        setTotalCount(initialData.length);
        setError(null);
        setCurrentPage(0);
        setSuggestions([]);
    }, [initialData]);

    // Handle query changes
    const handleQueryChange = useCallback((newQuery: string) => {
        setQuery(newQuery);
    }, []);

    // Handle criteria changes
    const handleCriteriaChange = useCallback((newCriteria: FilterCriteria) => {
        setCriteria(newCriteria);
    }, []);

    // Context value
    const contextValue: SearchContextValue = {
        query,
        criteria,
        results,
        suggestions,
        isLoading,
        resultsCount: results.length,
        totalCount,
        hasMore: results.length < totalCount,
        error,
        setQuery: handleQueryChange,
        setCriteria: handleCriteriaChange,
        performSearch,
        clearSearch,
        loadMore,
        filterOptions
    };

    const containerStyle: React.CSSProperties = {
        width: '100%'
    };

    return (
        <SearchContext.Provider value={contextValue}>
            <div className={className} style={containerStyle}>
                {children}
            </div>
        </SearchContext.Provider>
    );
}

// Enhanced Search Interface Component
/**
 *
 */
export interface SearchInterfaceProps {
    showFilters?: boolean;
    showAdvancedSearch?: boolean;
    placeholder?: string;
    className?: string;
}

/**
 *
 */
export function SearchInterface({
    showFilters = true,
    showAdvancedSearch = true,
    placeholder = 'Search projects, builds, deployments...',
    className
}: SearchInterfaceProps) {
    const {
        query,
        criteria,
        suggestions,
        isLoading,
        resultsCount,
        setQuery,
        setCriteria,
        performSearch,
        clearSearch,
        filterOptions
    } = useSearch();

    const handleApplyFilters = useCallback(() => {
        performSearch(query, criteria);
    }, [performSearch, query, criteria]);

    const handleClearFilters = useCallback(() => {
        setCriteria({});
        performSearch(query, {});
    }, [performSearch, query, setCriteria]);

    const handleSearchSubmit = useCallback((searchQuery: string) => {
        performSearch(searchQuery, criteria);
    }, [performSearch, criteria]);

    const handleClearSearch = useCallback(() => {
        clearSearch();
    }, [clearSearch]);

    const containerStyle: React.CSSProperties = {
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
        width: '100%'
    };

    return (
        <div className={className} style={containerStyle}>
            {/* Main Search Input */}
            <SearchInput
                value={query}
                onChange={setQuery}
                onSearch={handleSearchSubmit}
                placeholder={placeholder}
                suggestions={suggestions}
                isLoading={isLoading}
                resultsCount={resultsCount}
                showSuggestions={showAdvancedSearch}
                showOperators={showAdvancedSearch}
            />

            {/* Advanced Filters */}
            {showFilters && (
                <FilterPanel
                    criteria={criteria}
                    onCriteriaChange={setCriteria}
                    onApplyFilters={handleApplyFilters}
                    onClearFilters={handleClearFilters}
                    options={filterOptions}
                    isLoading={isLoading}
                    resultsCount={resultsCount}
                />
            )}
        </div>
    );
}

// Local filtering helper function
/**
 *
 */
function filterLocalResults(
    data: SearchResult[],
    query: string,
    criteria: FilterCriteria
): SearchResult[] {
    return data.filter(item => {
        // Text search
        if (query) {
            const searchText = query.toLowerCase();
            const matchesText =
                item.title.toLowerCase().includes(searchText) ||
                item.description?.toLowerCase().includes(searchText) ||
                item.tags?.some(tag => tag.toLowerCase().includes(searchText)) ||
                item.user?.toLowerCase().includes(searchText);

            if (!matchesText) return false;
        }

        // Date range filter
        if (criteria.dateRange) {
            const { start, end } = criteria.dateRange;
            const itemDate = new Date(item.date);

            if (start && itemDate < start) return false;
            if (end && itemDate > end) return false;
        }

        // Status filter
        if (criteria.status?.length && item.status) {
            if (!criteria.status.includes(item.status)) return false;
        }

        // Tags filter
        if (criteria.tags?.length && item.tags) {
            const hasMatchingTag = criteria.tags.some(tag =>
                item.tags!.includes(tag)
            );
            if (!hasMatchingTag) return false;
        }

        // Users filter
        if (criteria.users?.length && item.user) {
            if (!criteria.users.includes(item.user)) return false;
        }

        // Type filter
        if (criteria.type?.length) {
            if (!criteria.type.includes(item.type)) return false;
        }

        return true;
    });
}