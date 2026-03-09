/**
 * SearchFilters Component for Flashit Web
 * Provides multi-faceted filtering UI for search refinement
 *
 * @doc.type component
 * @doc.purpose Advanced search filters with faceted navigation
 * @doc.layer product
 * @doc.pattern FilterComponent
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Filter,
  X,
  Calendar,
  Tag,
  Smile,
  Circle,
  Image,
  FileText,
  ChevronDown,
  ChevronUp,
  Check,
  Save,
  Clock,
  Search,
  Sparkles,
} from 'lucide-react';
import { api } from '../lib/api';

// ============================================================================
// Types
// ============================================================================

interface FacetValue {
  value: string;
  count: number;
  selected: boolean;
}

interface DateRangeFacet {
  min: Date;
  max: Date;
  buckets: Array<{
    start: Date;
    end: Date;
    count: number;
  }>;
}

interface NumericFacet {
  min: number;
  max: number;
  average: number;
  distribution: Array<{
    range: string;
    count: number;
  }>;
}

interface Facets {
  emotions: FacetValue[];
  tags: FacetValue[];
  spheres: FacetValue[];
  contentTypes: FacetValue[];
  dateRange: DateRangeFacet;
  importance: NumericFacet;
  hasMedia: FacetValue[];
  hasTranscript: FacetValue[];
}

interface SearchFilters {
  query?: string;
  sphereIds?: string[];
  emotions?: string[];
  tags?: string[];
  contentTypes?: string[];
  startDate?: Date;
  endDate?: Date;
  importanceMin?: number;
  importanceMax?: number;
  hasMedia?: boolean;
  hasTranscript?: boolean;
}

interface SearchFiltersProps {
  filters: SearchFilters;
  facets?: Facets;
  onFiltersChange: (filters: SearchFilters) => void;
  onSaveSearch?: (name: string) => void;
  isLoading?: boolean;
  className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

const FacetSection: React.FC<{
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  defaultExpanded?: boolean;
}> = ({ title, icon, children, defaultExpanded = true }) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <div className="border-b border-gray-200 dark:border-gray-700 pb-4">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center justify-between w-full py-2 text-left"
      >
        <span className="flex items-center gap-2 font-medium text-gray-900 dark:text-white">
          {icon}
          {title}
        </span>
        {expanded ? (
          <ChevronUp className="w-4 h-4 text-gray-500" />
        ) : (
          <ChevronDown className="w-4 h-4 text-gray-500" />
        )}
      </button>
      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="pt-2">{children}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

const FacetCheckbox: React.FC<{
  value: string;
  count: number;
  selected: boolean;
  onChange: (selected: boolean) => void;
}> = ({ value, count, selected, onChange }) => (
  <label className="flex items-center gap-2 py-1 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800 px-2 rounded transition-colors">
    <input
      type="checkbox"
      checked={selected}
      onChange={(e) => onChange(e.target.checked)}
      className="w-4 h-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
    />
    <span className="flex-1 text-sm text-gray-700 dark:text-gray-300 truncate">
      {value}
    </span>
    <span className="text-xs text-gray-500 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">
      {count}
    </span>
  </label>
);

const DateRangeSelector: React.FC<{
  startDate?: Date;
  endDate?: Date;
  dateRange?: DateRangeFacet;
  onChange: (start?: Date, end?: Date) => void;
}> = ({ startDate, endDate, dateRange, onChange }) => {
  const formatDate = (date: Date) => date.toISOString().split('T')[0];

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        <div className="flex-1">
          <label className="text-xs text-gray-500 dark:text-gray-400">From</label>
          <input
            type="date"
            value={startDate ? formatDate(startDate) : ''}
            min={dateRange ? formatDate(dateRange.min) : undefined}
            max={endDate ? formatDate(endDate) : undefined}
            onChange={(e) =>
              onChange(e.target.value ? new Date(e.target.value) : undefined, endDate)
            }
            className="w-full mt-1 px-2 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800"
          />
        </div>
        <div className="flex-1">
          <label className="text-xs text-gray-500 dark:text-gray-400">To</label>
          <input
            type="date"
            value={endDate ? formatDate(endDate) : ''}
            min={startDate ? formatDate(startDate) : undefined}
            max={dateRange ? formatDate(dateRange.max) : undefined}
            onChange={(e) =>
              onChange(startDate, e.target.value ? new Date(e.target.value) : undefined)
            }
            className="w-full mt-1 px-2 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800"
          />
        </div>
      </div>

      {/* Quick date buttons */}
      <div className="flex flex-wrap gap-1">
        {[
          { label: 'Today', days: 0 },
          { label: '7d', days: 7 },
          { label: '30d', days: 30 },
          { label: '90d', days: 90 },
        ].map(({ label, days }) => (
          <button
            key={label}
            onClick={() => {
              const end = new Date();
              const start = new Date();
              start.setDate(start.getDate() - days);
              onChange(start, end);
            }}
            className="px-2 py-1 text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
          >
            {label}
          </button>
        ))}
        <button
          onClick={() => onChange(undefined, undefined)}
          className="px-2 py-1 text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
        >
          Clear
        </button>
      </div>
    </div>
  );
};

const ImportanceSlider: React.FC<{
  min?: number;
  max?: number;
  distribution?: NumericFacet['distribution'];
  onChange: (min?: number, max?: number) => void;
}> = ({ min, max, distribution, onChange }) => {
  const [localMin, setLocalMin] = useState(min ?? 1);
  const [localMax, setLocalMax] = useState(max ?? 10);

  const handleMinChange = useCallback(
    (value: number) => {
      setLocalMin(value);
      onChange(value, localMax);
    },
    [localMax, onChange]
  );

  const handleMaxChange = useCallback(
    (value: number) => {
      setLocalMax(value);
      onChange(localMin, value);
    },
    [localMin, onChange]
  );

  return (
    <div className="space-y-3">
      <div className="flex gap-4">
        <div className="flex-1">
          <label className="text-xs text-gray-500">Min: {localMin}</label>
          <input
            type="range"
            min={1}
            max={10}
            value={localMin}
            onChange={(e) => handleMinChange(Number(e.target.value))}
            className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer dark:bg-gray-700"
          />
        </div>
        <div className="flex-1">
          <label className="text-xs text-gray-500">Max: {localMax}</label>
          <input
            type="range"
            min={1}
            max={10}
            value={localMax}
            onChange={(e) => handleMaxChange(Number(e.target.value))}
            className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer dark:bg-gray-700"
          />
        </div>
      </div>

      {/* Distribution chart */}
      {distribution && distribution.length > 0 && (
        <div className="flex items-end gap-1 h-12">
          {distribution.map((bucket, i) => {
            const maxCount = Math.max(...distribution.map((d) => d.count));
            const height = maxCount > 0 ? (bucket.count / maxCount) * 100 : 0;
            const isInRange =
              !min || !max || (i + 1 >= min && i + 1 <= max);

            return (
              <div
                key={bucket.range}
                className="flex-1 flex flex-col items-center"
              >
                <div
                  className={`w-full rounded-t transition-colors ${
                    isInRange
                      ? 'bg-indigo-500'
                      : 'bg-gray-300 dark:bg-gray-600'
                  }`}
                  style={{ height: `${height}%`, minHeight: bucket.count > 0 ? 4 : 0 }}
                />
                <span className="text-[10px] text-gray-400">{bucket.range}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const SearchFiltersComponent: React.FC<SearchFiltersProps> = ({
  filters,
  facets,
  onFiltersChange,
  onSaveSearch,
  isLoading = false,
  className = '',
}) => {
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [searchName, setSearchName] = useState('');

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filters.emotions?.length) count += filters.emotions.length;
    if (filters.tags?.length) count += filters.tags.length;
    if (filters.sphereIds?.length) count += filters.sphereIds.length;
    if (filters.contentTypes?.length) count += filters.contentTypes.length;
    if (filters.startDate || filters.endDate) count++;
    if (filters.importanceMin || filters.importanceMax) count++;
    if (filters.hasMedia !== undefined) count++;
    if (filters.hasTranscript !== undefined) count++;
    return count;
  }, [filters]);

  const handleFacetToggle = useCallback(
    (field: keyof SearchFilters, value: string, selected: boolean) => {
      const currentValues = (filters[field] as string[]) || [];
      const newValues = selected
        ? [...currentValues, value]
        : currentValues.filter((v) => v !== value);

      onFiltersChange({
        ...filters,
        [field]: newValues.length > 0 ? newValues : undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const handleClearAll = useCallback(() => {
    onFiltersChange({ query: filters.query });
  }, [filters.query, onFiltersChange]);

  const handleSaveSearch = useCallback(() => {
    if (searchName.trim() && onSaveSearch) {
      onSaveSearch(searchName.trim());
      setSearchName('');
      setSaveDialogOpen(false);
    }
  }, [searchName, onSaveSearch]);

  return (
    <div
      className={`bg-white dark:bg-gray-900 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 ${className}`}
    >
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <Filter className="w-5 h-5 text-gray-500" />
          <h3 className="font-medium text-gray-900 dark:text-white">Filters</h3>
          {activeFilterCount > 0 && (
            <span className="bg-indigo-100 dark:bg-indigo-900 text-indigo-600 dark:text-indigo-300 text-xs font-medium px-2 py-0.5 rounded-full">
              {activeFilterCount}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {activeFilterCount > 0 && (
            <>
              {onSaveSearch && (
                <button
                  onClick={() => setSaveDialogOpen(true)}
                  className="text-sm text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1"
                >
                  <Save className="w-4 h-4" />
                  Save
                </button>
              )}
              <button
                onClick={handleClearAll}
                className="text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 flex items-center gap-1"
              >
                <X className="w-4 h-4" />
                Clear all
              </button>
            </>
          )}
        </div>
      </div>

      {/* Filter sections */}
      <div className="p-4 space-y-4 max-h-[600px] overflow-y-auto">
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
          </div>
        ) : (
          <>
            {/* Spheres */}
            {facets?.spheres && facets.spheres.length > 0 && (
              <FacetSection
                title="Spheres"
                icon={<Circle className="w-4 h-4" />}
              >
                <div className="max-h-40 overflow-y-auto">
                  {facets.spheres.map((facet) => (
                    <FacetCheckbox
                      key={facet.value}
                      value={facet.value}
                      count={facet.count}
                      selected={facet.selected}
                      onChange={(selected) =>
                        handleFacetToggle('sphereIds', facet.value, selected)
                      }
                    />
                  ))}
                </div>
              </FacetSection>
            )}

            {/* Emotions */}
            {facets?.emotions && facets.emotions.length > 0 && (
              <FacetSection
                title="Emotions"
                icon={<Smile className="w-4 h-4" />}
              >
                <div className="max-h-40 overflow-y-auto">
                  {facets.emotions.map((facet) => (
                    <FacetCheckbox
                      key={facet.value}
                      value={facet.value}
                      count={facet.count}
                      selected={facet.selected}
                      onChange={(selected) =>
                        handleFacetToggle('emotions', facet.value, selected)
                      }
                    />
                  ))}
                </div>
              </FacetSection>
            )}

            {/* Tags */}
            {facets?.tags && facets.tags.length > 0 && (
              <FacetSection
                title="Tags"
                icon={<Tag className="w-4 h-4" />}
              >
                <div className="max-h-40 overflow-y-auto">
                  {facets.tags.map((facet) => (
                    <FacetCheckbox
                      key={facet.value}
                      value={facet.value}
                      count={facet.count}
                      selected={facet.selected}
                      onChange={(selected) =>
                        handleFacetToggle('tags', facet.value, selected)
                      }
                    />
                  ))}
                </div>
              </FacetSection>
            )}

            {/* Content Types */}
            {facets?.contentTypes && facets.contentTypes.length > 0 && (
              <FacetSection
                title="Content Type"
                icon={<FileText className="w-4 h-4" />}
              >
                {facets.contentTypes.map((facet) => (
                  <FacetCheckbox
                    key={facet.value}
                    value={facet.value}
                    count={facet.count}
                    selected={facet.selected}
                    onChange={(selected) =>
                      handleFacetToggle('contentTypes', facet.value, selected)
                    }
                  />
                ))}
              </FacetSection>
            )}

            {/* Date Range */}
            <FacetSection
              title="Date Range"
              icon={<Calendar className="w-4 h-4" />}
            >
              <DateRangeSelector
                startDate={filters.startDate}
                endDate={filters.endDate}
                dateRange={facets?.dateRange}
                onChange={(start, end) =>
                  onFiltersChange({
                    ...filters,
                    startDate: start,
                    endDate: end,
                  })
                }
              />
            </FacetSection>

            {/* Importance */}
            <FacetSection
              title="Importance"
              icon={<Sparkles className="w-4 h-4" />}
              defaultExpanded={false}
            >
              <ImportanceSlider
                min={filters.importanceMin}
                max={filters.importanceMax}
                distribution={facets?.importance?.distribution}
                onChange={(min, max) =>
                  onFiltersChange({
                    ...filters,
                    importanceMin: min,
                    importanceMax: max,
                  })
                }
              />
            </FacetSection>

            {/* Media & Transcript */}
            <FacetSection
              title="Content"
              icon={<Image className="w-4 h-4" />}
              defaultExpanded={false}
            >
              <div className="space-y-2">
                {facets?.hasMedia?.map((facet) => (
                  <FacetCheckbox
                    key={facet.value}
                    value={facet.value}
                    count={facet.count}
                    selected={facet.selected}
                    onChange={(selected) =>
                      onFiltersChange({
                        ...filters,
                        hasMedia: selected ? facet.value === 'With Media' : undefined,
                      })
                    }
                  />
                ))}
                {facets?.hasTranscript?.map((facet) => (
                  <FacetCheckbox
                    key={facet.value}
                    value={facet.value}
                    count={facet.count}
                    selected={facet.selected}
                    onChange={(selected) =>
                      onFiltersChange({
                        ...filters,
                        hasTranscript: selected ? facet.value === 'With Transcript' : undefined,
                      })
                    }
                  />
                ))}
              </div>
            </FacetSection>
          </>
        )}
      </div>

      {/* Save Search Dialog */}
      <AnimatePresence>
        {saveDialogOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
            onClick={() => setSaveDialogOpen(false)}
          >
            <motion.div
              initial={{ scale: 0.95 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white dark:bg-gray-800 rounded-lg shadow-xl p-6 w-full max-w-md mx-4"
            >
              <h4 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                Save Search
              </h4>
              <input
                type="text"
                value={searchName}
                onChange={(e) => setSearchName(e.target.value)}
                placeholder="Enter a name for this search..."
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                autoFocus
              />
              <div className="flex justify-end gap-3 mt-4">
                <button
                  onClick={() => setSaveDialogOpen(false)}
                  className="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSaveSearch}
                  disabled={!searchName.trim()}
                  className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Save
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default SearchFiltersComponent;
