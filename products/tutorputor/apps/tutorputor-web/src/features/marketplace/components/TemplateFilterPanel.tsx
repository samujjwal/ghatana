/**
 * TemplateFilterPanel Component
 *
 * Sidebar panel for filtering simulation templates in the marketplace.
 *
 * @doc.type component
 * @doc.purpose Filter templates by domain, difficulty, tags, etc.
 * @doc.layer product
 * @doc.pattern Filter
 */

import { useState, useCallback } from "react";
import { Button, Badge, Checkbox } from "@ghatana/design-system";
import type { TemplateFilters, TemplateDifficulty } from "../types";

// Local SimulationDomain type (mirroring contracts)
type SimulationDomain =
  | "PHYSICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ECONOMICS"
  | "CS_DISCRETE"
  | "MATH"
  | "ENGINEERING";

// =============================================================================
// Props
// =============================================================================

export interface TemplateFilterPanelProps {
  filters: TemplateFilters;
  onFiltersChange: (filters: TemplateFilters) => void;
  onClearFilters: () => void;
  availableTags?: string[];
  isCollapsed?: boolean;
  onToggleCollapse?: () => void;
}

// =============================================================================
// Constants
// =============================================================================

const DOMAINS: { value: SimulationDomain; label: string; icon: string }[] = [
  { value: "PHYSICS", label: "Physics", icon: "⚛️" },
  { value: "CHEMISTRY", label: "Chemistry", icon: "🧪" },
  { value: "BIOLOGY", label: "Biology", icon: "🧬" },
  { value: "MEDICINE", label: "Medicine", icon: "💊" },
  { value: "ECONOMICS", label: "Economics", icon: "📊" },
  { value: "CS_DISCRETE", label: "Computer Science", icon: "🔢" },
];

const DIFFICULTIES: { value: TemplateDifficulty; label: string; color: string }[] = [
  { value: "beginner", label: "Beginner", color: "bg-green-500" },
  { value: "intermediate", label: "Intermediate", color: "bg-blue-500" },
  { value: "advanced", label: "Advanced", color: "bg-orange-500" },
  { value: "expert", label: "Expert", color: "bg-red-500" },
];

const RATING_OPTIONS = [
  { value: 4.5, label: "4.5+ ⭐" },
  { value: 4.0, label: "4.0+ ⭐" },
  { value: 3.5, label: "3.5+ ⭐" },
  { value: 3.0, label: "3.0+ ⭐" },
];

// =============================================================================
// Component
// =============================================================================

export const TemplateFilterPanel = ({
  filters,
  onFiltersChange,
  onClearFilters,
  availableTags = [],
  isCollapsed = false,
  onToggleCollapse,
}: TemplateFilterPanelProps) => {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(["domains", "difficulty"])
  );

  const toggleSection = useCallback((section: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(section)) {
        next.delete(section);
      } else {
        next.add(section);
      }
      return next;
    });
  }, []);

  const toggleDomain = useCallback(
    (domain: SimulationDomain) => {
      const currentDomains = filters.domains ?? [];
      const newDomains = currentDomains.includes(domain)
        ? currentDomains.filter((d) => d !== domain)
        : [...currentDomains, domain];
      onFiltersChange({
        ...filters,
        domains: newDomains.length > 0 ? newDomains : undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const toggleDifficulty = useCallback(
    (difficulty: TemplateDifficulty) => {
      const currentDifficulties = filters.difficulties ?? [];
      const newDifficulties = currentDifficulties.includes(difficulty)
        ? currentDifficulties.filter((d) => d !== difficulty)
        : [...currentDifficulties, difficulty];
      onFiltersChange({
        ...filters,
        difficulties: newDifficulties.length > 0 ? newDifficulties : undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const toggleTag = useCallback(
    (tag: string) => {
      const currentTags = filters.tags ?? [];
      const newTags = currentTags.includes(tag)
        ? currentTags.filter((t) => t !== tag)
        : [...currentTags, tag];
      onFiltersChange({
        ...filters,
        tags: newTags.length > 0 ? newTags : undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const setMinRating = useCallback(
    (rating: number | undefined) => {
      onFiltersChange({
        ...filters,
        minRating: rating,
      });
    },
    [filters, onFiltersChange]
  );

  const setVerifiedOnly = useCallback(
    (isVerified: boolean) => {
      onFiltersChange({
        ...filters,
        isVerified: isVerified || undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const setFreeOnly = useCallback(
    (freeOnly: boolean) => {
      onFiltersChange({
        ...filters,
        isPremium: freeOnly ? false : undefined,
      });
    },
    [filters, onFiltersChange]
  );

  const activeFilterCount =
    (filters.domains?.length ?? 0) +
    (filters.difficulties?.length ?? 0) +
    (filters.tags?.length ?? 0) +
    (filters.minRating ? 1 : 0) +
    (filters.isVerified ? 1 : 0) +
    (filters.isPremium === false ? 1 : 0);

  if (isCollapsed) {
    return (
      <div className="p-2">
        <Button
          variant="ghost"
          size="sm"
          onClick={onToggleCollapse}
          className="w-full"
        >
          <span>☰</span>
          {activeFilterCount > 0 && (
            <Badge variant="solid" tone="primary" className="ml-2">
              {activeFilterCount}
            </Badge>
          )}
        </Button>
      </div>
    );
  }

  return (
    <div className="w-64 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 h-full overflow-y-auto">
      {/* Header */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
        <h3 className="font-semibold text-gray-900 dark:text-white">Filters</h3>
        <div className="flex items-center gap-2">
          {activeFilterCount > 0 && (
            <Button variant="ghost" size="sm" onClick={onClearFilters}>
              Clear all
            </Button>
          )}
          {onToggleCollapse && (
            <Button variant="ghost" size="sm" onClick={onToggleCollapse}>
              ✕
            </Button>
          )}
        </div>
      </div>

      <div className="p-4 space-y-6">
        {/* Domain Filter */}
        <div>
          <button
            onClick={() => toggleSection("domains")}
            className="w-full flex items-center justify-between mb-3"
          >
            <span className="font-medium text-gray-900 dark:text-white">
              Domain
            </span>
            <span className="text-gray-500">
              {expandedSections.has("domains") ? "−" : "+"}
            </span>
          </button>
          {expandedSections.has("domains") && (
            <div className="space-y-2">
              {DOMAINS.map(({ value, label, icon }) => (
                <label
                  key={value}
                  className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 p-2 rounded-lg transition-colors"
                >
                  <Checkbox
                    checked={filters.domains?.includes(value) ?? false}
                    onChange={() => toggleDomain(value)}
                  />
                  <span className="text-sm">
                    {icon} {label}
                  </span>
                </label>
              ))}
            </div>
          )}
        </div>

        {/* Difficulty Filter */}
        <div>
          <button
            onClick={() => toggleSection("difficulty")}
            className="w-full flex items-center justify-between mb-3"
          >
            <span className="font-medium text-gray-900 dark:text-white">
              Difficulty
            </span>
            <span className="text-gray-500">
              {expandedSections.has("difficulty") ? "−" : "+"}
            </span>
          </button>
          {expandedSections.has("difficulty") && (
            <div className="space-y-2">
              {DIFFICULTIES.map(({ value, label, color }) => (
                <label
                  key={value}
                  className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 p-2 rounded-lg transition-colors"
                >
                  <Checkbox
                    checked={filters.difficulties?.includes(value) ?? false}
                    onChange={() => toggleDifficulty(value)}
                  />
                  <span className={`w-2 h-2 rounded-full ${color}`} />
                  <span className="text-sm">{label}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        {/* Rating Filter */}
        <div>
          <button
            onClick={() => toggleSection("rating")}
            className="w-full flex items-center justify-between mb-3"
          >
            <span className="font-medium text-gray-900 dark:text-white">
              Rating
            </span>
            <span className="text-gray-500">
              {expandedSections.has("rating") ? "−" : "+"}
            </span>
          </button>
          {expandedSections.has("rating") && (
            <div className="space-y-2">
              {RATING_OPTIONS.map(({ value, label }) => (
                <label
                  key={value}
                  className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 p-2 rounded-lg transition-colors"
                >
                  <input
                    type="radio"
                    name="minRating"
                    checked={filters.minRating === value}
                    onChange={() => setMinRating(value)}
                    className="w-4 h-4 text-blue-600"
                  />
                  <span className="text-sm">{label}</span>
                </label>
              ))}
              {filters.minRating && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setMinRating(undefined)}
                >
                  Clear
                </Button>
              )}
            </div>
          )}
        </div>

        {/* Tags Filter */}
        {availableTags.length > 0 && (
          <div>
            <button
              onClick={() => toggleSection("tags")}
              className="w-full flex items-center justify-between mb-3"
            >
              <span className="font-medium text-gray-900 dark:text-white">
                Tags
              </span>
              <span className="text-gray-500">
                {expandedSections.has("tags") ? "−" : "+"}
              </span>
            </button>
            {expandedSections.has("tags") && (
              <div className="flex flex-wrap gap-2">
                {availableTags.slice(0, 20).map((tag) => (
                  <button
                    key={tag}
                    onClick={() => toggleTag(tag)}
                    className={`
                      px-3 py-1 rounded-full text-xs font-medium transition-colors
                      ${
                        filters.tags?.includes(tag)
                          ? "bg-blue-500 text-white"
                          : "bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600"
                      }
                    `}
                  >
                    {tag}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Other Filters */}
        <div>
          <span className="font-medium text-gray-900 dark:text-white block mb-3">
            Other
          </span>
          <div className="space-y-2">
            <label className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 p-2 rounded-lg transition-colors">
              <Checkbox
                checked={filters.isVerified ?? false}
                onChange={(e) => setVerifiedOnly(e.target.checked)}
              />
              <span className="text-sm">Verified only</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 p-2 rounded-lg transition-colors">
              <Checkbox
                checked={filters.isPremium === false}
                onChange={(e) => setFreeOnly(e.target.checked)}
              />
              <span className="text-sm">Free only</span>
            </label>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TemplateFilterPanel;
