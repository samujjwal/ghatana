/**
 * SearchFilterBar Component
 *
 * Standardized search and filter bar with accessible labeled inputs,
 * filter chips, and clear-all action. Replaces ad-hoc search/filter
 * implementations across pages.
 *
 * @doc.type component
 * @doc.purpose Accessible search and filter controls for list views
 * @doc.layer shared
 * @doc.pattern Form Component
 * @example
 * ```tsx
 * <SearchFilterBar
 *   searchQuery={query}
 *   onSearchChange={setQuery}
 *   filters={[
 *     { id: 'status', label: 'Status', value: status, options: [...] }
 *   ]}
 *   onClear={() => { setQuery(''); setStatus('all'); }}
 * />
 * ```
 */

import { Filter, X } from "lucide-react";
import React from "react";
import { cn } from "../../lib/theme";
import { LabeledInput } from "./LabeledInput";

interface FilterOption {
  value: string;
  label: string;
}

interface FilterChip {
  id: string;
  label: string;
  value: string;
  options: FilterOption[];
  onChange: (value: string) => void;
}

interface SearchFilterBarProps {
  /** Current search query */
  searchQuery: string;
  /** Callback when search changes */
  onSearchChange: (query: string) => void;
  /** Search input placeholder */
  searchPlaceholder?: string;
  /** Filter chips */
  filters?: FilterChip[];
  /** Whether any filter is active */
  hasActiveFilters?: boolean;
  /** Clear all filters callback */
  onClear?: () => void;
  /** Optional className */
  className?: string;
  /** Test id */
  "data-testid"?: string;
}

export const SearchFilterBar = React.memo(function SearchFilterBar({
  searchQuery,
  onSearchChange,
  searchPlaceholder = "Search...",
  filters,
  hasActiveFilters,
  onClear,
  className,
  "data-testid": testId,
}: SearchFilterBarProps) {
  const showClear =
    hasActiveFilters ??
    (searchQuery.length > 0 ||
      (filters?.some((f) => f.value !== "all") ?? false));

  return (
    <div
      className={cn("flex flex-wrap items-center gap-4", className)}
      data-testid={testId}
    >
      {/* Search */}
      <div className="flex-1 min-w-[240px]">
        <LabeledInput
          id={`${testId ?? "search"}-input`}
          label="Search"
          labelSrOnly
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder={searchPlaceholder}
          wrapperClassName="flex-1"
        />
      </div>

      {/* Filter selects */}
      {filters && filters.length > 0 && (
        <div className="flex items-center gap-2">
          <Filter
            className="h-4 w-4 text-gray-400 shrink-0"
            aria-hidden="true"
          />
          <div className="flex flex-wrap items-center gap-2">
            {filters.map((filter) => (
              <div key={filter.id} className="flex items-center gap-1.5">
                <label
                  htmlFor={filter.id}
                  className="text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap"
                >
                  {filter.label}:
                </label>
                <select
                  id={filter.id}
                  value={filter.value}
                  onChange={(e) => filter.onChange(e.target.value)}
                  className={cn(
                    "h-9 rounded-md border border-gray-300 dark:border-gray-600",
                    "bg-white dark:bg-gray-800 px-2.5 py-1.5 text-sm",
                    "text-gray-900 dark:text-white",
                    "focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500",
                  )}
                >
                  {filter.options.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Clear all */}
      {showClear && onClear && (
        <button
          type="button"
          onClick={onClear}
          className={cn(
            "inline-flex items-center gap-1.5 px-3 py-1.5",
            "text-sm font-medium text-gray-600 dark:text-gray-400",
            "hover:text-gray-900 dark:hover:text-gray-200",
            "hover:bg-gray-100 dark:hover:bg-gray-700",
            "rounded-md transition-colors",
          )}
          aria-label="Clear all filters"
        >
          <X className="h-4 w-4" aria-hidden="true" />
          Clear
        </button>
      )}
    </div>
  );
});

SearchFilterBar.displayName = "SearchFilterBar";

export default SearchFilterBar;
