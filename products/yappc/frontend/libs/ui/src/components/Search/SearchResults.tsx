/**
 * Search Results Component
 * 
 * Advanced search results display with real-time updates, result highlighting,
 * pagination, sorting options, and different view modes. Integrates with
 * WebSocket data for live result updates and synchronized view states.
 */

import React, { useState, useCallback, useMemo } from 'react';

import type { SearchResult } from './SearchProvider';

/**
 *
 */
export interface SearchResultsProps {
    results: SearchResult[];
    query: string;
    isLoading?: boolean;
    error?: string | null;
    resultsCount?: number;
    totalCount?: number;
    hasMore?: boolean;
    onLoadMore?: () => void;
    onResultClick?: (result: SearchResult) => void;
    viewMode?: 'list' | 'grid' | 'compact';
    sortBy?: 'relevance' | 'date' | 'title' | 'type';
    sortOrder?: 'asc' | 'desc';
    onSortChange?: (sortBy: string, sortOrder: 'asc' | 'desc') => void;
    className?: string;
}

/**
 *
 */
export function SearchResults({
    results,
    query,
    isLoading = false,
    error = null,
    resultsCount = 0,
    totalCount = 0,
    hasMore = false,
    onLoadMore,
    onResultClick,
    viewMode = 'list',
    sortBy = 'relevance',
    sortOrder = 'desc',
    onSortChange,
    className
}: SearchResultsProps) {
    const [selectedResults, setSelectedResults] = useState<Set<string>>(new Set());

    // Sort results
    const sortedResults = useMemo(() => {
        const sorted = [...results];

        switch (sortBy) {
            case 'date':
                sorted.sort((a, b) => {
                    const dateA = new Date(a.date).getTime();
                    const dateB = new Date(b.date).getTime();
                    return sortOrder === 'asc' ? dateA - dateB : dateB - dateA;
                });
                break;
            case 'title':
                sorted.sort((a, b) => {
                    return sortOrder === 'asc'
                        ? a.title.localeCompare(b.title)
                        : b.title.localeCompare(a.title);
                });
                break;
            case 'type':
                sorted.sort((a, b) => {
                    return sortOrder === 'asc'
                        ? a.type.localeCompare(b.type)
                        : b.type.localeCompare(a.type);
                });
                break;
            case 'relevance':
            default:
                // Keep original order for relevance
                break;
        }

        return sorted;
    }, [results, sortBy, sortOrder]);

    const handleResultClick = useCallback((result: SearchResult) => {
        if (onResultClick) {
            onResultClick(result);
        } else if (result.url) {
            window.open(result.url, '_blank');
        }
    }, [onResultClick]);

    const handleSelectResult = useCallback((resultId: string, selected: boolean) => {
        setSelectedResults(prev => {
            const newSet = new Set(prev);
            if (selected) {
                newSet.add(resultId);
            } else {
                newSet.delete(resultId);
            }
            return newSet;
        });
    }, []);

    const handleSelectAll = useCallback((selected: boolean) => {
        if (selected) {
            setSelectedResults(new Set(results.map(r => r.id)));
        } else {
            setSelectedResults(new Set());
        }
    }, [results]);

    // Highlight matching text
    const highlightText = useCallback((text: string, searchQuery: string): React.ReactNode => {
        if (!searchQuery.trim()) return text;

        const parts = text.split(new RegExp(`(${searchQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi'));

        return parts.map((part, index) =>
            part.toLowerCase() === searchQuery.toLowerCase() ? (
                <mark key={index} style={{
                    backgroundColor: 'var(--color-warning-light, #fff3cd)',
                    padding: '0 2px',
                    borderRadius: '2px',
                    fontWeight: '600'
                }}>
                    {part}
                </mark>
            ) : (
                part
            )
        );
    }, []);

    const getStatusColor = useCallback((status?: string): string => {
        if (!status) return 'var(--color-text-secondary, #666)';

        const colors: Record<string, string> = {
            'active': 'var(--color-success-main, #4caf50)',
            'pending': 'var(--color-warning-main, #ff9800)',
            'completed': 'var(--color-info-main, #2196f3)',
            'failed': 'var(--color-error-main, #f44336)',
            'cancelled': 'var(--color-grey-500, #9e9e9e)',
            'draft': 'var(--color-brown-main, #795548)'
        };

        return colors[status.toLowerCase()] || 'var(--color-text-secondary, #666)';
    }, []);

    const getTypeIcon = useCallback((type: string): string => {
        const icons: Record<string, string> = {
            'build': '🔨',
            'deployment': '🚀',
            'log': '📄',
            'issue': '🐛',
            'task': '✓',
            'project': '📁',
            'user': '👤'
        };

        return icons[type.toLowerCase()] || '📄';
    }, []);

    // Container styles
    const containerStyle: React.CSSProperties = {
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem'
    };

    const headerStyle: React.CSSProperties = {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '1rem',
        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
        borderRadius: '8px',
        border: '1px solid var(--color-border, #e0e0e0)'
    };

    const toolbarStyle: React.CSSProperties = {
        display: 'flex',
        alignItems: 'center',
        gap: '1rem'
    };

    const resultsContainerStyle: React.CSSProperties = {
        display: 'flex',
        flexDirection: 'column',
        gap: viewMode === 'compact' ? '0.5rem' : '1rem'
    };

    const gridContainerStyle: React.CSSProperties = {
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
        gap: '1rem'
    };

    // Error state
    if (error) {
        return (
            <div className={className} style={containerStyle}>
                <div style={{
                    padding: '2rem',
                    textAlign: 'center',
                    backgroundColor: 'var(--color-error-lighter, #fff5f5)',
                    border: '1px solid var(--color-error-light, #fed7d7)',
                    borderRadius: '8px',
                    color: 'var(--color-error-dark, #c53030)'
                }}>
                    <span style={{ fontSize: '2rem', marginBottom: '1rem', display: 'block' }}>⚠️</span>
                    <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.125rem' }}>Search Error</h3>
                    <p style={{ margin: 0, fontSize: '0.875rem' }}>{error}</p>
                </div>
            </div>
        );
    }

    // Empty state
    if (!isLoading && results.length === 0) {
        return (
            <div className={className} style={containerStyle}>
                <div style={{
                    padding: '3rem',
                    textAlign: 'center',
                    backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
                    borderRadius: '8px',
                    border: '1px solid var(--color-border, #e0e0e0)'
                }}>
                    <span style={{ fontSize: '3rem', marginBottom: '1rem', display: 'block', opacity: 0.5 }}>🔍</span>
                    <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.125rem', color: 'var(--color-text-secondary, #666)' }}>
                        {query ? 'No Results Found' : 'No Search Performed'}
                    </h3>
                    <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-text-secondary, #666)' }}>
                        {query
                            ? `No results found for "${query}". Try different keywords or adjust your filters.`
                            : 'Enter a search query or apply filters to find results.'
                        }
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className={className} style={containerStyle}>
            {/* Results Header & Toolbar */}
            <div style={headerStyle}>
                <div>
                    <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: '600' }}>
                        {isLoading ? 'Searching...' : `${resultsCount} of ${totalCount} results`}
                        {query && (
                            <span style={{ fontWeight: '400', color: 'var(--color-text-secondary, #666)' }}>
                                {' '}for "{query}"
                            </span>
                        )}
                    </h3>
                </div>

                <div style={toolbarStyle}>
                    {/* Sort Controls */}
                    {onSortChange && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <span style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary, #666)' }}>
                                Sort by:
                            </span>
                            <select
                                value={sortBy}
                                onChange={(e) => onSortChange(e.target.value, sortOrder)}
                                style={{
                                    padding: '0.25rem 0.5rem',
                                    borderRadius: '4px',
                                    border: '1px solid var(--color-border, #e0e0e0)',
                                    fontSize: '0.875rem'
                                }}
                            >
                                <option value="relevance">Relevance</option>
                                <option value="date">Date</option>
                                <option value="title">Title</option>
                                <option value="type">Type</option>
                            </select>
                            <button
                                onClick={() => onSortChange(sortBy, sortOrder === 'asc' ? 'desc' : 'asc')}
                                style={{
                                    padding: '0.25rem 0.5rem',
                                    borderRadius: '4px',
                                    border: '1px solid var(--color-border, #e0e0e0)',
                                    backgroundColor: 'white',
                                    cursor: 'pointer',
                                    fontSize: '0.875rem'
                                }}
                                title={`Sort ${sortOrder === 'asc' ? 'Descending' : 'Ascending'}`}
                            >
                                {sortOrder === 'asc' ? '↑' : '↓'}
                            </button>
                        </div>
                    )}

                    {/* Bulk Selection */}
                    {results.length > 0 && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <label style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', cursor: 'pointer' }}>
                                <input
                                    type="checkbox"
                                    checked={selectedResults.size === results.length}
                                    onChange={(e) => handleSelectAll(e.target.checked)}
                                />
                                <span style={{ fontSize: '0.875rem' }}>
                                    Select All ({selectedResults.size})
                                </span>
                            </label>
                        </div>
                    )}
                </div>
            </div>

            {/* Results List */}
            <div style={viewMode === 'grid' ? gridContainerStyle : resultsContainerStyle}>
                {sortedResults.map((result) => (
                    <SearchResultItem
                        key={result.id}
                        result={result}
                        query={query}
                        viewMode={viewMode}
                        isSelected={selectedResults.has(result.id)}
                        onSelect={(selected) => handleSelectResult(result.id, selected)}
                        onClick={() => handleResultClick(result)}
                        highlightText={highlightText}
                        getStatusColor={getStatusColor}
                        getTypeIcon={getTypeIcon}
                    />
                ))}
            </div>

            {/* Load More */}
            {hasMore && onLoadMore && (
                <div style={{ textAlign: 'center', padding: '1rem' }}>
                    <button
                        onClick={onLoadMore}
                        disabled={isLoading}
                        style={{
                            padding: '0.75rem 1.5rem',
                            backgroundColor: 'var(--color-primary, #1976d2)',
                            color: 'white',
                            border: 'none',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontSize: '0.875rem',
                            fontWeight: '500',
                            opacity: isLoading ? 0.7 : 1
                        }}
                    >
                        {isLoading ? 'Loading...' : 'Load More Results'}
                    </button>
                </div>
            )}

            {/* Loading Indicator */}
            {isLoading && (
                <div style={{
                    padding: '2rem',
                    textAlign: 'center',
                    color: 'var(--color-text-secondary, #666)'
                }}>
                    <span style={{ fontSize: '1.5rem', marginBottom: '0.5rem', display: 'block' }}>⏳</span>
                    <span style={{ fontSize: '0.875rem' }}>Searching...</span>
                </div>
            )}
        </div>
    );
}

// Individual Search Result Item Component
/**
 *
 */
interface SearchResultItemProps {
    result: SearchResult;
    query: string;
    viewMode: 'list' | 'grid' | 'compact';
    isSelected: boolean;
    onSelect: (selected: boolean) => void;
    onClick: () => void;
    highlightText: (text: string, query: string) => React.ReactNode;
    getStatusColor: (status?: string) => string;
    getTypeIcon: (type: string) => string;
}

/**
 *
 */
function SearchResultItem({
    result,
    query,
    viewMode,
    isSelected,
    onSelect,
    onClick,
    highlightText,
    getStatusColor,
    getTypeIcon
}: SearchResultItemProps) {
    const itemStyle: React.CSSProperties = {
        padding: viewMode === 'compact' ? '0.75rem' : '1rem',
        backgroundColor: 'white',
        border: `1px solid ${isSelected ? 'var(--color-primary, #1976d2)' : 'var(--color-border, #e0e0e0)'}`,
        borderRadius: '8px',
        cursor: 'pointer',
        transition: 'all 0.2s ease',
        position: 'relative'
    };

    const titleStyle: React.CSSProperties = {
        margin: '0 0 0.5rem 0',
        fontSize: viewMode === 'compact' ? '0.875rem' : '1rem',
        fontWeight: '600',
        color: 'var(--color-text-primary, #333)',
        lineHeight: 1.4
    };

    const descriptionStyle: React.CSSProperties = {
        margin: '0 0 0.75rem 0',
        fontSize: '0.875rem',
        color: 'var(--color-text-secondary, #666)',
        lineHeight: 1.5,
        display: viewMode === 'compact' ? 'none' : 'block'
    };

    const metaStyle: React.CSSProperties = {
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        flexWrap: 'wrap',
        fontSize: '0.75rem',
        color: 'var(--color-text-secondary, #666)'
    };

    const badgeStyle = (color: string): React.CSSProperties => ({
        backgroundColor: color,
        color: 'white',
        padding: '0.125rem 0.375rem',
        borderRadius: '12px',
        fontSize: '0.625rem',
        fontWeight: '500',
        textTransform: 'uppercase'
    });

    return (
        <div
            style={itemStyle}
            onClick={(e) => {
                e.preventDefault();
                onClick();
            }}
            onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = '0 2px 8px rgba(0, 0, 0, 0.1)';
            }}
            onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = 'none';
            }}
        >
            {/* Selection Checkbox */}
            <label
                style={{
                    position: 'absolute',
                    top: '0.75rem',
                    right: '0.75rem',
                    cursor: 'pointer'
                }}
                onClick={(e) => e.stopPropagation()}
            >
                <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={(e) => onSelect(e.target.checked)}
                />
            </label>

            {/* Content */}
            <div style={{ paddingRight: '2rem' }}>
                <h4 style={titleStyle}>
                    <span style={{ marginRight: '0.5rem' }}>{getTypeIcon(result.type)}</span>
                    {highlightText(result.title, query)}
                </h4>

                {result.description && (
                    <p style={descriptionStyle}>
                        {highlightText(result.description, query)}
                    </p>
                )}

                <div style={metaStyle}>
                    <span style={badgeStyle(getStatusColor(result.status))}>
                        {result.status || result.type}
                    </span>

                    {result.user && (
                        <span>👤 {result.user}</span>
                    )}

                    <span>📅 {new Date(result.date).toLocaleDateString()}</span>

                    {result.tags && result.tags.length > 0 && (
                        <div style={{ display: 'flex', gap: '0.25rem' }}>
                            {result.tags.slice(0, 3).map((tag) => (
                                <span
                                    key={tag}
                                    style={{
                                        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
                                        padding: '0.125rem 0.375rem',
                                        borderRadius: '4px',
                                        fontSize: '0.625rem'
                                    }}
                                >
                                    #{tag}
                                </span>
                            ))}
                            {result.tags.length > 3 && (
                                <span style={{ fontSize: '0.625rem' }}>
                                    +{result.tags.length - 3} more
                                </span>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}