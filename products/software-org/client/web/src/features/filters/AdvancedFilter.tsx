import { useState } from 'react';
import { Filter, X, Save, Download } from 'lucide-react';
import { Badge } from '@/components/ui';

/**
 * Advanced Filter Panel
 *
 * <p><b>Purpose</b><br>
 * Reusable advanced filtering component with save/load functionality.
 * Supports multiple filter types, saved filters, and export.
 *
 * <p><b>Features</b><br>
 * - Multi-select filters (checkboxes)
 * - Range filters (dates, numbers)
 * - Search filters
 * - Save filter presets
 * - Export filtered data
 *
 * @doc.type component
 * @doc.purpose Advanced filtering UI
 * @doc.layer product
 * @doc.pattern Filter
 */

export interface FilterOption {
    id: string;
    label: string;
    value: string | number;
}

export interface FilterGroup {
    id: string;
    label: string;
    type: 'select' | 'multiselect' | 'range' | 'search';
    options?: FilterOption[];
    min?: number;
    max?: number;
}

export interface ActiveFilter {
    groupId: string;
    values: (string | number)[];
}

export interface SavedFilter {
    id: string;
    name: string;
    filters: ActiveFilter[];
}

interface AdvancedFilterProps {
    filterGroups: FilterGroup[];
    activeFilters: ActiveFilter[];
    onFiltersChange: (filters: ActiveFilter[]) => void;
    onExport?: () => void;
    savedFilters?: SavedFilter[];
    onSaveFilter?: (name: string, filters: ActiveFilter[]) => void;
    onLoadFilter?: (filter: SavedFilter) => void;
}

export function AdvancedFilter({
    filterGroups,
    activeFilters,
    onFiltersChange,
    onExport,
    savedFilters = [],
    onSaveFilter,
    onLoadFilter,
}: AdvancedFilterProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [showSaveModal, setShowSaveModal] = useState(false);
    const [filterName, setFilterName] = useState('');

    const getFilterValues = (groupId: string): (string | number)[] => {
        const filter = activeFilters.find((f) => f.groupId === groupId);
        return filter?.values || [];
    };

    const updateFilter = (groupId: string, values: (string | number)[]) => {
        const newFilters = activeFilters.filter((f) => f.groupId !== groupId);
        if (values.length > 0) {
            newFilters.push({ groupId, values });
        }
        onFiltersChange(newFilters);
    };

    const toggleValue = (groupId: string, value: string | number) => {
        const currentValues = getFilterValues(groupId);
        const newValues = currentValues.includes(value)
            ? currentValues.filter((v) => v !== value)
            : [...currentValues, value];
        updateFilter(groupId, newValues);
    };

    const clearAllFilters = () => {
        onFiltersChange([]);
    };

    const handleSaveFilter = () => {
        if (filterName.trim() && onSaveFilter) {
            onSaveFilter(filterName, activeFilters);
            setFilterName('');
            setShowSaveModal(false);
        }
    };

    const activeFilterCount = activeFilters.reduce((sum, f) => sum + f.values.length, 0);

    return (
        <div className="relative">
            {/* Filter Toggle Button */}
            <div className="flex items-center gap-2">
                <button
                    onClick={() => setIsOpen(!isOpen)}
                    className="flex items-center gap-2 px-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                >
                    <Filter className="h-4 w-4 text-slate-600 dark:text-neutral-400" />
                    <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                        Filters
                    </span>
                    {activeFilterCount > 0 && (
                        <Badge variant="primary">{activeFilterCount}</Badge>
                    )}
                </button>

                {activeFilterCount > 0 && (
                    <button
                        onClick={clearAllFilters}
                        className="px-3 py-2 text-sm text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100 transition-colors"
                    >
                        Clear all
                    </button>
                )}

                {onExport && (
                    <button
                        onClick={onExport}
                        className="flex items-center gap-2 px-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                    >
                        <Download className="h-4 w-4 text-slate-600 dark:text-neutral-400" />
                        <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                            Export
                        </span>
                    </button>
                )}
            </div>

            {/* Filter Panel */}
            {isOpen && (
                <div className="absolute top-full left-0 mt-2 w-96 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg shadow-xl z-10">
                    <div className="p-4 border-b border-slate-200 dark:border-slate-800">
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                Advanced Filters
                            </h3>
                            <button
                                onClick={() => setIsOpen(false)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-200"
                            >
                                <X className="h-4 w-4" />
                            </button>
                        </div>

                        {/* Saved Filters */}
                        {savedFilters.length > 0 && (
                            <div className="mb-3">
                                <label className="text-xs font-medium text-slate-600 dark:text-neutral-400 mb-2 block">
                                    Saved Filters
                                </label>
                                <div className="flex flex-wrap gap-2">
                                    {savedFilters.map((saved) => (
                                        <button
                                            key={saved.id}
                                            onClick={() => onLoadFilter?.(saved)}
                                            className="px-2 py-1 text-xs bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300 rounded hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors"
                                        >
                                            {saved.name}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="max-h-96 overflow-y-auto p-4 space-y-4">
                        {filterGroups.map((group) => (
                            <div key={group.id}>
                                <label className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2 block">
                                    {group.label}
                                </label>

                                {group.type === 'multiselect' && group.options && (
                                    <div className="space-y-2">
                                        {group.options.map((option) => {
                                            const isSelected = getFilterValues(group.id).includes(
                                                option.value
                                            );
                                            return (
                                                <label
                                                    key={option.id}
                                                    className="flex items-center gap-2 cursor-pointer"
                                                >
                                                    <input
                                                        type="checkbox"
                                                        checked={isSelected}
                                                        onChange={() =>
                                                            toggleValue(group.id, option.value)
                                                        }
                                                        className="rounded border-slate-300 dark:border-slate-600 text-blue-600 focus:ring-blue-500"
                                                    />
                                                    <span className="text-sm text-slate-700 dark:text-neutral-300">
                                                        {option.label}
                                                    </span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                )}

                                {group.type === 'select' && group.options && (
                                    <select
                                        value={getFilterValues(group.id)[0] || ''}
                                        onChange={(e) =>
                                            updateFilter(
                                                group.id,
                                                e.target.value ? [e.target.value] : []
                                            )
                                        }
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                    >
                                        <option value="">All</option>
                                        {group.options.map((option) => (
                                            <option key={option.id} value={option.value}>
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                )}

                                {group.type === 'search' && (
                                    <input
                                        type="text"
                                        value={(getFilterValues(group.id)[0] as string) || ''}
                                        onChange={(e) =>
                                            updateFilter(
                                                group.id,
                                                e.target.value ? [e.target.value] : []
                                            )
                                        }
                                        placeholder={`Search ${group.label.toLowerCase()}...`}
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                    />
                                )}
                            </div>
                        ))}
                    </div>

                    {onSaveFilter && (
                        <div className="p-4 border-t border-slate-200 dark:border-slate-800">
                            <button
                                onClick={() => setShowSaveModal(true)}
                                className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                            >
                                <Save className="h-4 w-4" />
                                Save Current Filters
                            </button>
                        </div>
                    )}
                </div>
            )}

            {/* Save Filter Modal */}
            {showSaveModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-slate-900 rounded-lg p-6 max-w-md w-full mx-4">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Save Filter Preset
                        </h3>
                        <input
                            type="text"
                            value={filterName}
                            onChange={(e) => setFilterName(e.target.value)}
                            placeholder="Filter name..."
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 mb-4"
                            autoFocus
                        />
                        <div className="flex gap-3">
                            <button
                                onClick={() => {
                                    setShowSaveModal(false);
                                    setFilterName('');
                                }}
                                className="flex-1 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleSaveFilter}
                                disabled={!filterName.trim()}
                                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Save
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
