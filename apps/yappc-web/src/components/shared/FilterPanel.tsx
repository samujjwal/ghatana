/**
 * Generic Filter Panel
 * 
 * Reusable filtering component that works with any data type.
 * Supports multiple filter types: select, multi-select, search, date range.
 * 
 * @doc.type component
 * @doc.purpose Generic filtering UI
 * @doc.layer product
 * @doc.pattern Generic Component
 */

import React, { useState } from 'react';
import { Filter as FilterList, XCircle as Clear, Search } from 'lucide-react';

export interface FilterOption {
    label: string;
    value: string | number;
}

export interface FilterConfig {
    id: string;
    label: string;
    type: 'select' | 'multi-select' | 'search' | 'toggle';
    options?: FilterOption[];
    placeholder?: string;
}

export interface FilterPanelProps {
    filters: FilterConfig[];
    values: Record<string, unknown>;
    onChange: (id: string, value: unknown) => void;
    onClear: () => void;
    onApply?: () => void;
}

export const FilterPanel: React.FC<FilterPanelProps> = ({
    filters,
    values,
    onChange,
    onClear,
    onApply,
}) => {
    const [isExpanded, setIsExpanded] = useState(false);

    const hasActiveFilters = Object.values(values).some(v => {
        if (Array.isArray(v)) return v.length > 0;
        if (typeof v === 'string') return v.length > 0;
        return v !== null && v !== undefined;
    });

    const activeFilterCount = Object.values(values).filter(v => {
        if (Array.isArray(v)) return v.length > 0;
        if (typeof v === 'string') return v.length > 0;
        return v !== null && v !== undefined;
    }).length;

    const renderFilter = (filter: FilterConfig) => {
        const value = values[filter.id];

        switch (filter.type) {
            case 'search':
                return (
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-secondary" />
                        <input
                            type="text"
                            value={value || ''}
                            onChange={(e) => onChange(filter.id, e.target.value)}
                            placeholder={filter.placeholder || 'Search...'}
                            className="w-full pl-10 pr-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
                        />
                    </div>
                );

            case 'select':
                return (
                    <select
                        value={value || ''}
                        onChange={(e) => onChange(filter.id, e.target.value)}
                        className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
                    >
                        <option value="">All</option>
                        {filter.options?.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                );

            case 'multi-select':
                const selectedValues = Array.isArray(value) ? value : [];
                return (
                    <div className="space-y-2">
                        {filter.options?.map((opt) => (
                            <label key={opt.value} className="flex items-center gap-2 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={selectedValues.includes(opt.value)}
                                    onChange={(e) => {
                                        const newValues = e.target.checked
                                            ? [...selectedValues, opt.value]
                                            : selectedValues.filter(v => v !== opt.value);
                                        onChange(filter.id, newValues);
                                    }}
                                    className="w-4 h-4 text-primary-600 rounded"
                                />
                                <span className="text-sm text-text-primary">{opt.label}</span>
                            </label>
                        ))}
                    </div>
                );

            case 'toggle':
                return (
                    <label className="flex items-center gap-2 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={value || false}
                            onChange={(e) => onChange(filter.id, e.target.checked)}
                            className="w-4 h-4 text-primary-600 rounded"
                        />
                        <span className="text-sm text-text-primary">{filter.placeholder || 'Enabled'}</span>
                    </label>
                );

            default:
                return null;
        }
    };

    return (
        <div className="border border-divider rounded-lg bg-bg-paper">
            {/* Header */}
            <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-grey-50 transition-colors"
            >
                <div className="flex items-center gap-2">
                    <FilterList className="w-5 h-5 text-text-secondary" />
                    <span className="text-sm font-medium text-text-primary">Filters</span>
                    {activeFilterCount > 0 && (
                        <span className="px-2 py-0.5 text-xs font-medium bg-primary-600 text-white rounded-full">
                            {activeFilterCount}
                        </span>
                    )}
                </div>
                <div className="flex items-center gap-2">
                    {hasActiveFilters && (
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onClear();
                            }}
                            className="flex items-center gap-1 px-2 py-1 text-xs text-error-color hover:bg-red-50 rounded transition-colors"
                        >
                            <Clear className="w-3 h-3" />
                            Clear
                        </button>
                    )}
                    <span className="text-xs text-text-secondary">
                        {isExpanded ? '▲' : '▼'}
                    </span>
                </div>
            </button>

            {/* Filter Controls */}
            {isExpanded && (
                <div className="px-4 pb-4 space-y-4 border-t border-divider">
                    {filters.map((filter) => (
                        <div key={filter.id}>
                            <label className="block text-xs font-medium text-text-secondary mb-2">
                                {filter.label}
                            </label>
                            {renderFilter(filter)}
                        </div>
                    ))}

                    {onApply && (
                        <button
                            onClick={onApply}
                            className="w-full px-4 py-2 text-sm font-medium bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors"
                        >
                            Apply Filters
                        </button>
                    )}
                </div>
            )}
        </div>
    );
};
