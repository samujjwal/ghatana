/**
 * PageFilters Component
 *
 * Consistent filter bar with search, category pills, and actions.
 *
 * @doc.type component
 * @doc.purpose Page filter controls
 * @doc.layer product
 */

import React from 'react';
import { Search, RefreshCw } from 'lucide-react';
import clsx from 'clsx';
import { Button } from '@/components/ui';
import { FILTER_PILL_STYLES, INPUT_STYLES, COLORS } from './theme';

export interface FilterCategory {
    /** Unique identifier */
    id: string;
    /** Display label */
    label: string;
    /** Optional icon */
    icon?: React.ReactNode;
}

export interface PageFiltersProps {
    /** Current search query */
    searchQuery?: string;
    /** Search input change handler */
    onSearchChange?: (query: string) => void;
    /** Search placeholder text */
    searchPlaceholder?: string;
    /** Category filter options */
    categories?: FilterCategory[];
    /** Currently selected category */
    selectedCategory?: string;
    /** Category selection handler */
    onCategoryChange?: (category: string) => void;
    /** Show refresh button */
    showRefresh?: boolean;
    /** Refresh handler */
    onRefresh?: () => void;
    /** Is currently refreshing */
    isRefreshing?: boolean;
    /** Additional action buttons (rendered on the right) */
    actions?: React.ReactNode;
    /** Additional filter controls (rendered between category and actions) */
    additionalFilters?: React.ReactNode;
    /** Additional class names */
    className?: string;
}

/**
 * PageFilters - Consistent filter bar component
 *
 * @example
 * ```tsx
 * <PageFilters
 *   searchQuery={searchQuery}
 *   onSearchChange={setSearchQuery}
 *   searchPlaceholder="Search agents..."
 *   categories={[
 *     { id: 'all', label: 'All' },
 *     { id: 'engineering', label: 'Engineering' },
 *   ]}
 *   selectedCategory={category}
 *   onCategoryChange={setCategory}
 *   showRefresh
 *   onRefresh={refetch}
 *   actions={<Button>Add New</Button>}
 * />
 * ```
 */
export function PageFilters({
    searchQuery = '',
    onSearchChange,
    searchPlaceholder = 'Search...',
    categories,
    selectedCategory = 'all',
    onCategoryChange,
    showRefresh = false,
    onRefresh,
    isRefreshing = false,
    actions,
    additionalFilters,
    className,
}: PageFiltersProps) {
    return (
        <div className={clsx(
            'flex items-center justify-between gap-4 mb-6',
            className
        )}>
            {/* Left section: filters */}
            <div className="flex items-center gap-4 flex-1">
                {/* Category pills */}
                {categories && categories.length > 0 && onCategoryChange && (
                    <div className={FILTER_PILL_STYLES.container}>
                        {categories.map((cat) => (
                            <button
                                key={cat.id}
                                onClick={() => onCategoryChange(cat.id)}
                                className={clsx(
                                    FILTER_PILL_STYLES.base,
                                    selectedCategory === cat.id
                                        ? FILTER_PILL_STYLES.active
                                        : FILTER_PILL_STYLES.inactive
                                )}
                            >
                                {cat.icon && <span className="mr-1.5">{cat.icon}</span>}
                                {cat.label}
                            </button>
                        ))}
                    </div>
                )}

                {/* Search input */}
                {onSearchChange && (
                    <div className="relative">
                        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={(e) => onSearchChange(e.target.value)}
                            placeholder={searchPlaceholder}
                            className={clsx(
                                'pl-9 pr-4 py-2 w-64',
                                INPUT_STYLES.base,
                                INPUT_STYLES.focus,
                                INPUT_STYLES.placeholder
                            )}
                        />
                    </div>
                )}

                {/* Additional filters */}
                {additionalFilters}
            </div>

            {/* Right section: actions */}
            <div className="flex items-center gap-2">
                {showRefresh && onRefresh && (
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onRefresh}
                        disabled={isRefreshing}
                        className="flex items-center gap-1"
                    >
                        <RefreshCw className={clsx(
                            'w-4 h-4',
                            isRefreshing && 'animate-spin'
                        )} />
                    </Button>
                )}
                {actions}
            </div>
        </div>
    );
}

// =============================================================================
// Helper: Category Pill (for standalone use)
// =============================================================================

export interface CategoryPillProps {
    label: string;
    isActive?: boolean;
    onClick?: () => void;
    icon?: React.ReactNode;
}

export function CategoryPill({ label, isActive, onClick, icon }: CategoryPillProps) {
    return (
        <button
            onClick={onClick}
            className={clsx(
                FILTER_PILL_STYLES.base,
                isActive ? FILTER_PILL_STYLES.active : FILTER_PILL_STYLES.inactive
            )}
        >
            {icon && <span className="mr-1.5">{icon}</span>}
            {label}
        </button>
    );
}
