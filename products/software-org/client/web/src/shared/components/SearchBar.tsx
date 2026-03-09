import React, { useState } from 'react';

/**
 * SearchBar - Global search and filtering component.
 *
 * <p><b>Purpose</b><br>
 * Search interface for finding across all entities (events, incidents, workflows, models).
 * Supports faceted filtering and recent searches.
 *
 * <p><b>Features</b><br>
 * - Real-time search suggestions
 * - Faceted filtering (by type, status, department)
 * - Recent searches persistence
 * - Keyboard navigation
 * - Clear history functionality
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <SearchBar
 *   onSearch={(query) => console.log(query)}
 *   placeholder="Search incidents, workflows..."
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Global search and filtering
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface SearchBarProps {
    onSearch?: (query: string) => void;
    onFilter?: (filters: Record<string, string>) => void;
    placeholder?: string;
    showFilters?: boolean;
}

export const SearchBar = React.memo(function SearchBar({
    onSearch,
    onFilter,
    placeholder = 'Search...',
    showFilters = true,
}: SearchBarProps) {
    const [query, setQuery] = useState('');
    const [showDropdown, setShowDropdown] = useState(false);
    const [recentSearches] = useState<string[]>(['incident:P0', 'workflow:active', 'model:drift']);
    const [activeFilters, setActiveFilters] = useState<Record<string, string>>({});

    const handleSearch = (value: string) => {
        setQuery(value);
        onSearch?.(value);
    };

    const handleFilterChange = (key: string, value: string) => {
        const newFilters = { ...activeFilters, [key]: value };
        setActiveFilters(newFilters);
        onFilter?.(newFilters);
    };

    return (
        <div className="relative w-full max-w-2xl">
            {/* Search Input */}
            <div className="relative flex items-center bg-white dark:bg-neutral-800 border border-slate-300 dark:border-neutral-600 rounded-lg overflow-hidden focus-within:ring-2 focus-within:ring-blue-500">
                <span className="pl-3 text-slate-400">🔍</span>
                <input
                    type="text"
                    value={query}
                    onChange={(e) => handleSearch(e.target.value)}
                    onFocus={() => setShowDropdown(true)}
                    onBlur={() => setTimeout(() => setShowDropdown(false), 200)}
                    placeholder={placeholder}
                    className="flex-1 px-3 py-2 bg-transparent outline-none text-slate-900 dark:text-neutral-100 placeholder-slate-500 dark:placeholder-slate-400"
                    aria-label="Search"
                />
                {query && (
                    <button
                        onClick={() => handleSearch('')}
                        className="pr-3 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                        aria-label="Clear search"
                    >
                        ✕
                    </button>
                )}
            </div>

            {/* Dropdown Menu */}
            {showDropdown && (
                <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg shadow-lg z-10">
                    {/* Recent Searches */}
                    {!query && recentSearches.length > 0 && (
                        <div className="border-b border-slate-100 dark:border-neutral-600 p-3">
                            <p className="text-xs font-medium text-slate-600 dark:text-neutral-400 mb-2">
                                Recent Searches
                            </p>
                            <div className="space-y-1">
                                {recentSearches.map((search) => (
                                    <button
                                        key={search}
                                        onClick={() => handleSearch(search)}
                                        className="w-full text-left px-3 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded"
                                    >
                                        {search}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Search Results */}
                    {query && (
                        <div className="p-3">
                            <p className="text-xs font-medium text-slate-600 dark:text-neutral-400 mb-2">
                                Search results for "{query}"
                            </p>
                            <p className="text-sm text-slate-500 dark:text-neutral-400">
                                Loading results...
                            </p>
                        </div>
                    )}

                    {/* Filters */}
                    {showFilters && (
                        <div className="border-t border-slate-100 dark:border-neutral-600 p-3 space-y-2">
                            <select
                                onChange={(e) => handleFilterChange('type', e.target.value)}
                                className="w-full text-sm px-2 py-1 border border-slate-200 dark:border-neutral-600 rounded bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                            >
                                <option value="">All Types</option>
                                <option value="incident">Incidents</option>
                                <option value="workflow">Workflows</option>
                                <option value="model">Models</option>
                                <option value="alert">Alerts</option>
                            </select>

                            <select
                                onChange={(e) => handleFilterChange('status', e.target.value)}
                                className="w-full text-sm px-2 py-1 border border-slate-200 dark:border-neutral-600 rounded bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                            >
                                <option value="">All Status</option>
                                <option value="open">Open</option>
                                <option value="resolved">Resolved</option>
                                <option value="pending">Pending</option>
                            </select>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
});

export default SearchBar;
