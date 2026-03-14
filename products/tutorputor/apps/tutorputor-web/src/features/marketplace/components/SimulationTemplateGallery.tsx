/**
 * SimulationTemplateGallery Component
 *
 * Main gallery view for browsing simulation templates in the marketplace.
 * Includes grid display, filtering, sorting, search, and pagination.
 *
 * @doc.type component
 * @doc.purpose Browse and filter simulation templates
 * @doc.layer product
 * @doc.pattern Gallery
 */

import { useState, useCallback, useMemo, useRef, useEffect } from "react";
import { Button, TextField, Select } from "@ghatana/design-system";
import { SimulationTemplateCard } from "./SimulationTemplateCard";
import { TemplateFilterPanel } from "./TemplateFilterPanel";
import { useSimulationTemplates, useFeaturedTemplates, useToggleFavorite } from "../hooks";
import type { SimulationTemplate, TemplateSortField } from "../types";

// =============================================================================
// Props
// =============================================================================

export interface SimulationTemplateGalleryProps {
  onTemplateSelect?: (template: SimulationTemplate) => void;
  onTemplateUse?: (templateId: string) => void;
  showFeatured?: boolean;
  showFilters?: boolean;
  compactCards?: boolean;
  gridColumns?: 2 | 3 | 4;
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const SORT_OPTIONS: { value: TemplateSortField; label: string }[] = [
  { value: "popularity", label: "Most Popular" },
  { value: "rating", label: "Highest Rated" },
  { value: "newest", label: "Newest" },
  { value: "mostUsed", label: "Most Used" },
  { value: "alphabetical", label: "Alphabetical" },
];

const AVAILABLE_TAGS = [
  "projectile",
  "kinematics",
  "thermodynamics",
  "organic",
  "inorganic",
  "genetics",
  "ecology",
  "pharmacology",
  "microeconomics",
  "algorithms",
  "data-structures",
  "sorting",
  "calculus",
  "statistics",
  "circuits",
  "mechanics",
];

// =============================================================================
// Component
// =============================================================================

export const SimulationTemplateGallery = ({
  onTemplateSelect,
  onTemplateUse,
  showFeatured = true,
  showFilters = true,
  compactCards = false,
  gridColumns = 3,
  className = "",
}: SimulationTemplateGalleryProps) => {
  const [filtersCollapsed, setFiltersCollapsed] = useState(false);
  const [favoritedIds, setFavoritedIds] = useState<Set<string>>(new Set());
  const loadMoreRef = useRef<HTMLDivElement>(null);

  // Hooks
  const {
    templates,
    total,
    hasMore,
    isLoading,
    isLoadingMore,
    error,
    loadMore,
    filters,
    setFilters,
    clearFilters,
    sort,
    setSortField,
    search,
    setSearch,
  } = useSimulationTemplates({
    pageSize: 12,
  });

  const { templates: featuredTemplates, isLoading: isFeaturedLoading } =
    useFeaturedTemplates();

  // Infinite scroll
  useEffect(() => {
    if (!loadMoreRef.current) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !isLoadingMore) {
          loadMore();
        }
      },
      { threshold: 0.1 }
    );

    observer.observe(loadMoreRef.current);

    return () => observer.disconnect();
  }, [hasMore, isLoadingMore, loadMore]);

  // Handlers
  const { mutate: toggleFavorite } = useToggleFavorite();

  const handleFavorite = useCallback((templateId: string) => {
    // Optimistic local update
    setFavoritedIds((prev) => {
      const next = new Set(prev);
      if (next.has(templateId)) {
        next.delete(templateId);
      } else {
        next.add(templateId);
      }
      return next;
    });
    // Sync with server
    toggleFavorite(templateId);
  }, [toggleFavorite]);

  const handleSearchChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setSearch(e.target.value);
    },
    [setSearch]
  );

  const handleSortChange = useCallback(
    (value: string) => {
      setSortField(value as TemplateSortField);
    },
    [setSortField]
  );

  // Grid columns class
  const gridClass = useMemo(() => {
    switch (gridColumns) {
      case 2:
        return "grid-cols-1 sm:grid-cols-2";
      case 4:
        return "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4";
      case 3:
      default:
        return "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3";
    }
  }, [gridColumns]);

  // Render loading skeleton
  const renderSkeleton = (count: number) => (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className={`
            rounded-xl border border-gray-200 dark:border-gray-700
            bg-gray-100 dark:bg-gray-800 animate-pulse
            ${compactCards ? "h-48" : "h-72"}
          `}
        >
          <div className={`${compactCards ? "h-24" : "h-36"} bg-gray-200 dark:bg-gray-700`} />
          <div className="p-3 space-y-2">
            <div className="h-4 w-20 bg-gray-200 dark:bg-gray-700 rounded" />
            <div className="h-5 w-full bg-gray-200 dark:bg-gray-700 rounded" />
            <div className="h-4 w-2/3 bg-gray-200 dark:bg-gray-700 rounded" />
          </div>
        </div>
      ))}
    </>
  );

  return (
    <div className={`flex h-full bg-gray-50 dark:bg-gray-900 ${className}`}>
      {/* Filter Sidebar */}
      {showFilters && (
        <TemplateFilterPanel
          filters={filters}
          onFiltersChange={setFilters}
          onClearFilters={clearFilters}
          availableTags={AVAILABLE_TAGS}
          isCollapsed={filtersCollapsed}
          onToggleCollapse={() => setFiltersCollapsed((prev) => !prev)}
        />
      )}

      {/* Main Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="p-6 space-y-6">
          {/* Header */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                Simulation Templates
              </h1>
              <p className="text-gray-600 dark:text-gray-400">
                {total > 0
                  ? `${total} templates available`
                  : "Explore simulation templates"}
              </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-3">
              {/* Search */}
              <TextField
                type="text"
                placeholder="Search templates..."
                value={search}
                onChange={handleSearchChange}
                className="w-full sm:w-64"
              />

              {/* Sort */}
              <Select
                value={sort.field}
                onChange={(e) => handleSortChange(e.target.value)}
                className="w-full sm:w-48"
                options={SORT_OPTIONS}
              />
            </div>
          </div>

          {/* Featured Section */}
          {showFeatured && featuredTemplates.length > 0 && !search && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                ⭐ Featured Templates
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {isFeaturedLoading
                  ? renderSkeleton(4)
                  : featuredTemplates.slice(0, 4).map((template) => (
                      <SimulationTemplateCard
                        key={template.id}
                        template={template}
                        onSelect={onTemplateSelect}
                        onFavorite={handleFavorite}
                        onUse={onTemplateUse}
                        isFavorited={favoritedIds.has(template.id)}
                        compact
                      />
                    ))}
              </div>
            </div>
          )}

          {/* Error State */}
          {error && (
            <div className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
              <p className="text-red-600 dark:text-red-400">
                Failed to load templates: {error.message}
              </p>
              <Button
                variant="outline"
                tone="danger"
                size="sm"
                className="mt-2"
                onClick={() => window.location.reload()}
              >
                Retry
              </Button>
            </div>
          )}

          {/* Templates Grid */}
          <div className="space-y-4">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
              {search ? `Search Results for "${search}"` : "All Templates"}
            </h2>

            {isLoading ? (
              <div className={`grid ${gridClass} gap-4`}>
                {renderSkeleton(12)}
              </div>
            ) : templates.length === 0 ? (
              <div className="text-center py-12">
                <div className="text-4xl mb-4">🔍</div>
                <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                  No templates found
                </h3>
                <p className="text-gray-600 dark:text-gray-400 mb-4">
                  Try adjusting your filters or search query
                </p>
                <Button variant="outline" onClick={clearFilters}>
                  Clear Filters
                </Button>
              </div>
            ) : (
              <>
                <div className={`grid ${gridClass} gap-4`}>
                  {templates.map((template) => (
                    <SimulationTemplateCard
                      key={template.id}
                      template={template}
                      onSelect={onTemplateSelect}
                      onFavorite={handleFavorite}
                      onUse={onTemplateUse}
                      isFavorited={favoritedIds.has(template.id)}
                      compact={compactCards}
                    />
                  ))}
                </div>

                {/* Load More / Infinite Scroll Trigger */}
                {hasMore && (
                  <div
                    ref={loadMoreRef}
                    className="flex justify-center py-8"
                  >
                    {isLoadingMore ? (
                      <div className="flex items-center gap-2 text-gray-500">
                        <div className="w-5 h-5 border-2 border-gray-300 border-t-blue-500 rounded-full animate-spin" />
                        Loading more...
                      </div>
                    ) : (
                      <Button variant="outline" onClick={loadMore}>
                        Load More
                      </Button>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SimulationTemplateGallery;
