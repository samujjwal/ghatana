/**
 * Advanced Filter Panel Component
 * 
 * Comprehensive filtering interface with multi-criteria support, date ranges,
 * status filters, and real-time updates. Integrates with WebSocket data for 
 * live filtering and synchronized filter states across components.
 */

import React, { useState, useCallback, useEffect } from 'react';

/**
 *
 */
export interface FilterCriteria {
    text?: string;
    dateRange?: {
        start: Date | null;
        end: Date | null;
    };
    status?: string[];
    tags?: string[];
    users?: string[];
    severity?: string[];
    type?: string[];
}

/**
 *
 */
export interface FilterOption {
    value: string;
    label: string;
    count?: number;
    color?: string;
}

/**
 *
 */
export interface FilterPanelProps {
    criteria: FilterCriteria;
    onCriteriaChange: (criteria: FilterCriteria) => void;
    onApplyFilters: () => void;
    onClearFilters: () => void;
    options?: {
        status?: FilterOption[];
        tags?: FilterOption[];
        users?: FilterOption[];
        severity?: FilterOption[];
        type?: FilterOption[];
    };
    isLoading?: boolean;
    resultsCount?: number;
    className?: string;
}

/**
 *
 */
export function FilterPanel({
    criteria,
    onCriteriaChange,
    onApplyFilters,
    onClearFilters,
    options = {},
    isLoading = false,
    resultsCount,
    className
}: FilterPanelProps) {
    const [isExpanded, setIsExpanded] = useState(false);
    const [localCriteria, setLocalCriteria] = useState<FilterCriteria>(criteria);

    // Sync local criteria with props
    useEffect(() => {
        setLocalCriteria(criteria);
    }, [criteria]);

    const handleCriteriaUpdate = useCallback((updates: Partial<FilterCriteria>) => {
        const newCriteria = { ...localCriteria, ...updates };
        setLocalCriteria(newCriteria);
        onCriteriaChange(newCriteria);
    }, [localCriteria, onCriteriaChange]);

    const handleMultiSelectChange = useCallback((
        field: keyof FilterCriteria,
        value: string,
        checked: boolean
    ) => {
        const currentValues = (localCriteria[field] as string[]) || [];
        const newValues = checked
            ? [...currentValues, value]
            : currentValues.filter(v => v !== value);

        handleCriteriaUpdate({ [field]: newValues });
    }, [localCriteria, handleCriteriaUpdate]);

    const hasActiveFilters = Object.keys(localCriteria).some(key => {
        const value = localCriteria[key as keyof FilterCriteria];
        if (Array.isArray(value)) return value.length > 0;
        if (typeof value === 'object' && value !== null) {
            return Object.values(value).some(v => v !== null);
        }
        return value !== undefined && value !== '';
    });

    const activeFilterCount = Object.keys(localCriteria).reduce((count, key) => {
        const value = localCriteria[key as keyof FilterCriteria];
        if (Array.isArray(value)) return count + value.length;
        if (typeof value === 'object' && value !== null) {
            return count + Object.values(value).filter(v => v !== null).length;
        }
        return value !== undefined && value !== '' ? count + 1 : count;
    }, 0);

    const containerStyle: React.CSSProperties = {
        backgroundColor: 'white',
        border: '1px solid var(--color-border, #e0e0e0)',
        borderRadius: '8px',
        padding: '1rem',
        marginBottom: '1rem'
    };

    const headerStyle: React.CSSProperties = {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: isExpanded ? '1rem' : '0',
        cursor: 'pointer'
    };

    const badgeStyle: React.CSSProperties = {
        backgroundColor: 'var(--color-primary, #1976d2)',
        color: 'white',
        borderRadius: '12px',
        padding: '0.25rem 0.5rem',
        fontSize: '0.75rem',
        fontWeight: '500',
        minWidth: '20px',
        textAlign: 'center'
    };

    const buttonStyle: React.CSSProperties = {
        padding: '0.5rem 1rem',
        borderRadius: '4px',
        border: 'none',
        cursor: 'pointer',
        fontSize: '0.875rem',
        fontWeight: '500',
        transition: 'all 0.2s ease'
    };

    const primaryButtonStyle: React.CSSProperties = {
        ...buttonStyle,
        backgroundColor: 'var(--color-primary, #1976d2)',
        color: 'white'
    };

    const secondaryButtonStyle: React.CSSProperties = {
        ...buttonStyle,
        backgroundColor: 'transparent',
        color: 'var(--color-text-secondary, #666)',
        border: '1px solid var(--color-border, #e0e0e0)'
    };

    return (
        <div className={className} style={containerStyle}>
            {/* Header */}
            <div style={headerStyle} onClick={() => setIsExpanded(!isExpanded)}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: '600' }}>
                        🔍 Advanced Filters
                    </h3>
                    {activeFilterCount > 0 && (
                        <span style={badgeStyle}>{activeFilterCount}</span>
                    )}
                    {resultsCount !== undefined && (
                        <span style={{ color: 'var(--color-text-secondary, #666)', fontSize: '0.875rem' }}>
                            {resultsCount} results
                        </span>
                    )}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    {hasActiveFilters && (
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onClearFilters();
                            }}
                            style={{
                                ...secondaryButtonStyle,
                                padding: '0.25rem 0.5rem',
                                fontSize: '0.75rem'
                            }}
                        >
                            Clear All
                        </button>
                    )}
                    <span style={{ color: 'var(--color-text-secondary, #666)' }}>
                        {isExpanded ? '▲' : '▼'}
                    </span>
                </div>
            </div>

            {/* Expanded Filter Content */}
            {isExpanded && (
                <div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1rem' }}>

                        {/* Date Range Filter */}
                        <div>
                            <label style={{ display: 'block', fontWeight: '500', marginBottom: '0.5rem', fontSize: '0.875rem' }}>
                                Date Range
                            </label>
                            <div style={{ display: 'flex', gap: '0.5rem' }}>
                                <input
                                    type="date"
                                    value={localCriteria.dateRange?.start?.toISOString().split('T')[0] || ''}
                                    onChange={(e) => handleCriteriaUpdate({
                                        dateRange: {
                                            start: e.target.value ? new Date(e.target.value) : null,
                                            end: localCriteria.dateRange?.end || null
                                        }
                                    })}
                                    style={{
                                        flex: 1,
                                        padding: '0.5rem',
                                        border: '1px solid var(--color-border, #e0e0e0)',
                                        borderRadius: '4px',
                                        fontSize: '0.875rem'
                                    }}
                                />
                                <input
                                    type="date"
                                    value={localCriteria.dateRange?.end?.toISOString().split('T')[0] || ''}
                                    onChange={(e) => handleCriteriaUpdate({
                                        dateRange: {
                                            start: localCriteria.dateRange?.start || null,
                                            end: e.target.value ? new Date(e.target.value) : null
                                        }
                                    })}
                                    style={{
                                        flex: 1,
                                        padding: '0.5rem',
                                        border: '1px solid var(--color-border, #e0e0e0)',
                                        borderRadius: '4px',
                                        fontSize: '0.875rem'
                                    }}
                                />
                            </div>
                        </div>

                        {/* Status Filter */}
                        {options.status && (
                            <div>
                                <label style={{ display: 'block', fontWeight: '500', marginBottom: '0.5rem', fontSize: '0.875rem' }}>
                                    Status
                                </label>
                                <div style={{ maxHeight: '120px', overflowY: 'auto' }}>
                                    {options.status.map((option) => (
                                        <label key={option.value} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem', cursor: 'pointer' }}>
                                            <input
                                                type="checkbox"
                                                checked={(localCriteria.status || []).includes(option.value)}
                                                onChange={(e) => handleMultiSelectChange('status', option.value, e.target.checked)}
                                                style={{ cursor: 'pointer' }}
                                            />
                                            <span style={{ fontSize: '0.875rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                                                {option.color && (
                                                    <span
                                                        style={{
                                                            width: '8px',
                                                            height: '8px',
                                                            borderRadius: '50%',
                                                            backgroundColor: option.color
                                                        }}
                                                    />
                                                )}
                                                {option.label}
                                                {option.count !== undefined && (
                                                    <span style={{ color: 'var(--color-text-secondary, #666)' }}>
                                                        ({option.count})
                                                    </span>
                                                )}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Tags Filter */}
                        {options.tags && (
                            <div>
                                <label style={{ display: 'block', fontWeight: '500', marginBottom: '0.5rem', fontSize: '0.875rem' }}>
                                    Tags
                                </label>
                                <div style={{ maxHeight: '120px', overflowY: 'auto' }}>
                                    {options.tags.map((option) => (
                                        <label key={option.value} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem', cursor: 'pointer' }}>
                                            <input
                                                type="checkbox"
                                                checked={(localCriteria.tags || []).includes(option.value)}
                                                onChange={(e) => handleMultiSelectChange('tags', option.value, e.target.checked)}
                                                style={{ cursor: 'pointer' }}
                                            />
                                            <span style={{ fontSize: '0.875rem' }}>
                                                {option.label}
                                                {option.count !== undefined && (
                                                    <span style={{ color: 'var(--color-text-secondary, #666)', marginLeft: '0.25rem' }}>
                                                        ({option.count})
                                                    </span>
                                                )}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Users Filter */}
                        {options.users && (
                            <div>
                                <label style={{ display: 'block', fontWeight: '500', marginBottom: '0.5rem', fontSize: '0.875rem' }}>
                                    Users
                                </label>
                                <div style={{ maxHeight: '120px', overflowY: 'auto' }}>
                                    {options.users.map((option) => (
                                        <label key={option.value} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem', cursor: 'pointer' }}>
                                            <input
                                                type="checkbox"
                                                checked={(localCriteria.users || []).includes(option.value)}
                                                onChange={(e) => handleMultiSelectChange('users', option.value, e.target.checked)}
                                                style={{ cursor: 'pointer' }}
                                            />
                                            <span style={{ fontSize: '0.875rem' }}>
                                                {option.label}
                                                {option.count !== undefined && (
                                                    <span style={{ color: 'var(--color-text-secondary, #666)', marginLeft: '0.25rem' }}>
                                                        ({option.count})
                                                    </span>
                                                )}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Action Buttons */}
                    <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                        <button
                            onClick={onClearFilters}
                            disabled={!hasActiveFilters || isLoading}
                            style={{
                                ...secondaryButtonStyle,
                                opacity: (!hasActiveFilters || isLoading) ? 0.5 : 1,
                                cursor: (!hasActiveFilters || isLoading) ? 'not-allowed' : 'pointer'
                            }}
                        >
                            Clear All
                        </button>
                        <button
                            onClick={onApplyFilters}
                            disabled={isLoading}
                            style={{
                                ...primaryButtonStyle,
                                opacity: isLoading ? 0.7 : 1,
                                cursor: isLoading ? 'wait' : 'pointer'
                            }}
                        >
                            {isLoading ? 'Applying...' : 'Apply Filters'}
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}