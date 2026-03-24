/**
 * Advanced Search Input Component
 * 
 * Intelligent search interface with debounced input, real-time suggestions,
 * result highlighting, and advanced search operators. Integrates with
 * WebSocket data for live search results and suggestion updates.
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';

/**
 *
 */
export interface SearchSuggestion {
    id: string;
    text: string;
    type: 'recent' | 'suggestion' | 'command';
    icon?: string;
    description?: string;
    category?: string;
}

/**
 *
 */
export interface SearchOperator {
    operator: string;
    description: string;
    example: string;
}

/**
 *
 */
export interface SearchInputProps {
    value?: string;
    onChange: (value: string) => void;
    onSearch: (query: string) => void;
    placeholder?: string;
    suggestions?: SearchSuggestion[];
    operators?: SearchOperator[];
    isLoading?: boolean;
    resultsCount?: number;
    debounceMs?: number;
    showSuggestions?: boolean;
    showOperators?: boolean;
    className?: string;
    autoFocus?: boolean;
}

/**
 *
 */
export function SearchInput({
    value = '',
    onChange,
    onSearch,
    placeholder = 'Search...',
    suggestions = [],
    operators = [],
    isLoading = false,
    resultsCount,
    debounceMs = 300,
    showSuggestions = true,
    showOperators = true,
    className,
    autoFocus = false
}: SearchInputProps) {
    const [localValue, setLocalValue] = useState(value);
    const [isFocused, setIsFocused] = useState(false);
    const [showDropdown, setShowDropdown] = useState(false);
    const [selectedSuggestionIndex, setSelectedSuggestionIndex] = useState(-1);
    const [recentSearches, setRecentSearches] = useState<string[]>([]);

    const inputRef = useRef<HTMLInputElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const debounceRef = useRef<NodeJS.Timeout | null>(null);

    // Default search operators
    const defaultOperators: SearchOperator[] = [
        { operator: 'status:', description: 'Filter by status', example: 'status:active' },
        { operator: 'user:', description: 'Filter by user', example: 'user:john' },
        { operator: 'tag:', description: 'Filter by tag', example: 'tag:urgent' },
        { operator: 'type:', description: 'Filter by type', example: 'type:bug' },
        { operator: 'date:', description: 'Filter by date', example: 'date:2024-01-01' },
        { operator: 'is:', description: 'Filter by property', example: 'is:completed' },
        ...operators
    ];

    // Sync with external value changes
    useEffect(() => {
        setLocalValue(value);
    }, [value]);

    // Auto-focus if requested
    useEffect(() => {
        if (autoFocus && inputRef.current) {
            inputRef.current.focus();
        }
    }, [autoFocus]);

    // Load recent searches from localStorage
    useEffect(() => {
        const saved = localStorage.getItem('yappc-recent-searches');
        if (saved) {
            try {
                setRecentSearches(JSON.parse(saved));
            } catch (e) {
                console.warn('Failed to parse recent searches:', e);
            }
        }
    }, []);

    // Debounced search handler
    const debouncedSearch = useCallback((query: string) => {
        if (debounceRef.current) {
            clearTimeout(debounceRef.current);
        }

        debounceRef.current = setTimeout(() => {
            onChange(query);
            if (query.trim()) {
                onSearch(query);
            }
        }, debounceMs);
    }, [onChange, onSearch, debounceMs]);

    const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const newValue = e.target.value;
        setLocalValue(newValue);
        setSelectedSuggestionIndex(-1);
        debouncedSearch(newValue);

        // Show dropdown when typing
        if (newValue.length > 0 && (showSuggestions || showOperators)) {
            setShowDropdown(true);
        }
    }, [debouncedSearch, showSuggestions, showOperators]);

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (!showDropdown) {
            if (e.key === 'Enter') {
                handleSubmit();
            }
            return;
        }

        const totalSuggestions = getFilteredSuggestions().length;

        switch (e.key) {
            case 'ArrowDown':
                e.preventDefault();
                setSelectedSuggestionIndex(prev =>
                    prev < totalSuggestions - 1 ? prev + 1 : prev
                );
                break;
            case 'ArrowUp':
                e.preventDefault();
                setSelectedSuggestionIndex(prev => prev > 0 ? prev - 1 : -1);
                break;
            case 'Enter':
                e.preventDefault();
                if (selectedSuggestionIndex >= 0) {
                    const filtered = getFilteredSuggestions();
                    if (filtered[selectedSuggestionIndex]) {
                        handleSuggestionSelect(filtered[selectedSuggestionIndex].text);
                    }
                } else {
                    handleSubmit();
                }
                break;
            case 'Escape':
                setShowDropdown(false);
                setSelectedSuggestionIndex(-1);
                inputRef.current?.blur();
                break;
        }
    }, [showDropdown, selectedSuggestionIndex]);

    const handleSubmit = useCallback(() => {
        const query = localValue.trim();
        if (query) {
            // Add to recent searches
            const newRecent = [query, ...recentSearches.filter(s => s !== query)].slice(0, 10);
            setRecentSearches(newRecent);
            localStorage.setItem('yappc-recent-searches', JSON.stringify(newRecent));

            onChange(query);
            onSearch(query);
        }
        setShowDropdown(false);
        setSelectedSuggestionIndex(-1);
    }, [localValue, onChange, onSearch, recentSearches]);

    const handleSuggestionSelect = useCallback((suggestionText: string) => {
        setLocalValue(suggestionText);
        setShowDropdown(false);
        setSelectedSuggestionIndex(-1);

        // Trigger search immediately for suggestion selections
        onChange(suggestionText);
        onSearch(suggestionText);
    }, [onChange, onSearch]);

    const getFilteredSuggestions = useCallback(() => {
        const query = localValue.toLowerCase();
        const filtered: SearchSuggestion[] = [];

        // Recent searches
        if (query === '' || showSuggestions) {
            recentSearches
                .filter(search => search.toLowerCase().includes(query))
                .forEach(search => {
                    filtered.push({
                        id: `recent-${search}`,
                        text: search,
                        type: 'recent',
                        icon: '🕒',
                        description: 'Recent search'
                    });
                });
        }

        // Provided suggestions
        if (showSuggestions) {
            suggestions
                .filter(suggestion => suggestion.text.toLowerCase().includes(query))
                .forEach(suggestion => filtered.push(suggestion));
        }

        // Search operators
        if (showOperators && (query === '' || query.includes(':'))) {
            const operatorQuery = query.split(' ').pop() || '';
            defaultOperators
                .filter(op => op.operator.includes(operatorQuery) || op.description.toLowerCase().includes(query))
                .forEach(operator => {
                    filtered.push({
                        id: `operator-${operator.operator}`,
                        text: operator.example,
                        type: 'command',
                        icon: '⚡',
                        description: operator.description,
                        category: 'Search Operators'
                    });
                });
        }

        return filtered;
    }, [localValue, suggestions, recentSearches, showSuggestions, showOperators, defaultOperators]);

    // Click outside handler
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (
                dropdownRef.current &&
                !dropdownRef.current.contains(event.target as Node) &&
                inputRef.current &&
                !inputRef.current.contains(event.target as Node)
            ) {
                setShowDropdown(false);
                setSelectedSuggestionIndex(-1);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const containerStyle: React.CSSProperties = {
        position: 'relative',
        width: '100%'
    };

    const inputContainerStyle: React.CSSProperties = {
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        backgroundColor: 'white',
        border: `2px solid ${isFocused ? 'var(--color-primary, #1976d2)' : 'var(--color-border, #e0e0e0)'}`,
        borderRadius: '8px',
        transition: 'border-color 0.2s ease',
        overflow: 'hidden'
    };

    const inputStyle: React.CSSProperties = {
        flex: 1,
        padding: '0.75rem 1rem',
        border: 'none',
        outline: 'none',
        fontSize: '1rem',
        backgroundColor: 'transparent'
    };

    const iconStyle: React.CSSProperties = {
        padding: '0 0.75rem',
        color: 'var(--color-text-secondary, #666)',
        display: 'flex',
        alignItems: 'center'
    };

    const dropdownStyle: React.CSSProperties = {
        position: 'absolute',
        top: '100%',
        left: 0,
        right: 0,
        backgroundColor: 'white',
        border: '1px solid var(--color-border, #e0e0e0)',
        borderRadius: '8px',
        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
        zIndex: 1000,
        maxHeight: '300px',
        overflowY: 'auto',
        marginTop: '4px'
    };

    const suggestionStyle = (index: number): React.CSSProperties => ({
        padding: '0.75rem 1rem',
        cursor: 'pointer',
        borderBottom: '1px solid var(--color-border-light, #f0f0f0)',
        backgroundColor: index === selectedSuggestionIndex ? 'var(--color-background-hover, #f5f5f5)' : 'transparent',
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        transition: 'background-color 0.2s ease'
    });

    const filteredSuggestions = getFilteredSuggestions();

    return (
        <div className={className} style={containerStyle}>
            <div style={inputContainerStyle}>
                <div style={iconStyle}>
                    {isLoading ? (
                        <span style={{ animation: 'spin 1s linear infinite' }}>⏳</span>
                    ) : (
                        <span>🔍</span>
                    )}
                </div>
                <input
                    ref={inputRef}
                    type="text"
                    value={localValue}
                    onChange={handleInputChange}
                    onKeyDown={handleKeyDown}
                    onFocus={() => {
                        setIsFocused(true);
                        if (showSuggestions || showOperators) {
                            setShowDropdown(true);
                        }
                    }}
                    onBlur={() => setIsFocused(false)}
                    placeholder={placeholder}
                    style={inputStyle}
                />
                {resultsCount !== undefined && (
                    <div style={{
                        ...iconStyle,
                        fontSize: '0.875rem',
                        color: 'var(--color-text-secondary, #666)'
                    }}>
                        {resultsCount} results
                    </div>
                )}
                {localValue && (
                    <button
                        onClick={() => {
                            setLocalValue('');
                            onChange('');
                            inputRef.current?.focus();
                        }}
                        style={{
                            ...iconStyle,
                            cursor: 'pointer',
                            border: 'none',
                            background: 'none',
                            padding: '0 0.75rem'
                        }}
                        title="Clear search"
                    >
                        ✕
                    </button>
                )}
            </div>

            {/* Suggestions Dropdown */}
            {showDropdown && filteredSuggestions.length > 0 && (
                <div ref={dropdownRef} style={dropdownStyle}>
                    {filteredSuggestions.map((suggestion, index) => (
                        <div
                            key={suggestion.id}
                            style={suggestionStyle(index)}
                            onClick={() => handleSuggestionSelect(suggestion.text)}
                        >
                            <span style={{ fontSize: '1.2em' }}>{suggestion.icon || '📄'}</span>
                            <div style={{ flex: 1 }}>
                                <div style={{ fontWeight: '500', fontSize: '0.9rem' }}>
                                    {highlightMatch(suggestion.text, localValue)}
                                </div>
                                {suggestion.description && (
                                    <div style={{
                                        fontSize: '0.75rem',
                                        color: 'var(--color-text-secondary, #666)',
                                        marginTop: '0.25rem'
                                    }}>
                                        {suggestion.description}
                                    </div>
                                )}
                            </div>
                            <div style={{
                                fontSize: '0.75rem',
                                color: 'var(--color-text-secondary, #666)',
                                textTransform: 'uppercase',
                                fontWeight: '500'
                            }}>
                                {suggestion.type}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

// Helper function to highlight matching text
/**
 *
 */
function highlightMatch(text: string, query: string): React.ReactNode {
    if (!query.trim()) return text;

    const parts = text.split(new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi'));

    return parts.map((part, index) =>
        part.toLowerCase() === query.toLowerCase() ? (
            <mark key={index} style={{ backgroundColor: 'var(--color-warning-light, #fff3cd)', padding: '0 2px', borderRadius: '2px' }}>
                {part}
            </mark>
        ) : (
            part
        )
    );
}
