/**
 * LanguageEvolutionFilters Component
 * Advanced filters for language evolution insights
 * Week 13 Day 63 - Add filters/search facets for language patterns
 */

import React, { useState } from 'react';
import { format } from 'date-fns';
import {
  Filter,
  Calendar,
  Heart,
  Hash,
  TrendingUp,
  X,
  Search,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

interface FilterState {
  startDate?: string;
  endDate?: string;
  emotions?: string[];
  minFrequency?: number;
  trendType?: 'increasing' | 'decreasing' | 'stable' | 'all';
  searchTerm?: string;
}

interface LanguageEvolutionFiltersProps {
  onFiltersChange: (filters: FilterState) => void;
  availableEmotions?: string[];
}

export const LanguageEvolutionFilters: React.FC<LanguageEvolutionFiltersProps> = ({
  onFiltersChange,
  availableEmotions = ['joy', 'sadness', 'anger', 'fear', 'surprise', 'love', 'gratitude', 'anxiety'],
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [filters, setFilters] = useState<FilterState>({
    trendType: 'all',
    minFrequency: 1,
  });

  const updateFilters = (updates: Partial<FilterState>) => {
    const newFilters = { ...filters, ...updates };
    setFilters(newFilters);
    onFiltersChange(newFilters);
  };

  const clearFilters = () => {
    const defaultFilters: FilterState = {
      trendType: 'all',
      minFrequency: 1,
    };
    setFilters(defaultFilters);
    onFiltersChange(defaultFilters);
  };

  const toggleEmotion = (emotion: string) => {
    const currentEmotions = filters.emotions || [];
    const newEmotions = currentEmotions.includes(emotion)
      ? currentEmotions.filter((e) => e !== emotion)
      : [...currentEmotions, emotion];

    updateFilters({ emotions: newEmotions.length > 0 ? newEmotions : undefined });
  };

  const activeFilterCount =
    (filters.startDate ? 1 : 0) +
    (filters.endDate ? 1 : 0) +
    (filters.emotions?.length || 0) +
    (filters.minFrequency && filters.minFrequency > 1 ? 1 : 0) +
    (filters.trendType && filters.trendType !== 'all' ? 1 : 0) +
    (filters.searchTerm ? 1 : 0);

  return (
    <div className="rounded-lg border border-gray-300 bg-white shadow-sm">
      {/* Filter Header */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="flex w-full items-center justify-between p-4 hover:bg-gray-50 transition-colors"
      >
        <div className="flex items-center gap-3">
          <Filter className="h-5 w-5 text-gray-600" />
          <span className="font-semibold text-gray-900">Filters</span>
          {activeFilterCount > 0 && (
            <span className="rounded-full bg-purple-100 px-2.5 py-0.5 text-xs font-medium text-purple-700">
              {activeFilterCount}
            </span>
          )}
        </div>
        {isExpanded ? (
          <ChevronUp className="h-5 w-5 text-gray-600" />
        ) : (
          <ChevronDown className="h-5 w-5 text-gray-600" />
        )}
      </button>

      {/* Filter Content */}
      {isExpanded && (
        <div className="border-t border-gray-200 p-4 space-y-4">
          {/* Search Term */}
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
              <Search className="h-4 w-4" />
              Search Word or Pattern
            </label>
            <input
              type="text"
              value={filters.searchTerm || ''}
              onChange={(e) => updateFilters({ searchTerm: e.target.value || undefined })}
              placeholder="e.g., 'grateful', 'learning', 'challenge'"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-200"
            />
            <p className="mt-1 text-xs text-gray-500">
              Filter patterns containing this word
            </p>
          </div>

          {/* Date Range */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
                <Calendar className="h-4 w-4" />
                Start Date
              </label>
              <input
                type="date"
                value={filters.startDate || ''}
                onChange={(e) => updateFilters({ startDate: e.target.value || undefined })}
                max={filters.endDate || format(new Date(), 'yyyy-MM-dd')}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-200"
              />
            </div>
            <div>
              <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
                <Calendar className="h-4 w-4" />
                End Date
              </label>
              <input
                type="date"
                value={filters.endDate || ''}
                onChange={(e) => updateFilters({ endDate: e.target.value || undefined })}
                min={filters.startDate}
                max={format(new Date(), 'yyyy-MM-dd')}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-200"
              />
            </div>
          </div>

          {/* Emotions Filter */}
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
              <Heart className="h-4 w-4" />
              Emotions
            </label>
            <div className="flex flex-wrap gap-2">
              {availableEmotions.map((emotion) => (
                <button
                  key={emotion}
                  onClick={() => toggleEmotion(emotion)}
                  className={`rounded-full px-3 py-1.5 text-sm font-medium transition-all ${
                    filters.emotions?.includes(emotion)
                      ? 'bg-pink-600 text-white shadow-sm'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {emotion}
                </button>
              ))}
            </div>
            <p className="mt-2 text-xs text-gray-500">
              {filters.emotions && filters.emotions.length > 0
                ? `Filtering by ${filters.emotions.length} emotion${filters.emotions.length > 1 ? 's' : ''}`
                : 'Select emotions to filter patterns'}
            </p>
          </div>

          {/* Frequency Threshold */}
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
              <Hash className="h-4 w-4" />
              Minimum Frequency: {filters.minFrequency}
            </label>
            <input
              type="range"
              min="1"
              max="20"
              step="1"
              value={filters.minFrequency || 1}
              onChange={(e) => updateFilters({ minFrequency: parseInt(e.target.value) })}
              className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-purple-600"
            />
            <div className="flex justify-between text-xs text-gray-500 mt-1">
              <span>1</span>
              <span>20</span>
            </div>
            <p className="mt-1 text-xs text-gray-500">
              Show only patterns occurring at least {filters.minFrequency} time{filters.minFrequency !== 1 ? 's' : ''}
            </p>
          </div>

          {/* Trend Type */}
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
              <TrendingUp className="h-4 w-4" />
              Trend Type
            </label>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {(['all', 'increasing', 'decreasing', 'stable'] as const).map((trend) => (
                <button
                  key={trend}
                  onClick={() => updateFilters({ trendType: trend })}
                  className={`rounded-lg border px-3 py-2 text-sm font-medium transition-all ${
                    filters.trendType === trend
                      ? 'border-purple-600 bg-purple-50 text-purple-700'
                      : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  {trend === 'all' ? 'All Trends' : trend.charAt(0).toUpperCase() + trend.slice(1)}
                </button>
              ))}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-between pt-2 border-t border-gray-200">
            <button
              onClick={clearFilters}
              disabled={activeFilterCount === 0}
              className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                activeFilterCount > 0
                  ? 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  : 'bg-gray-50 text-gray-400 cursor-not-allowed'
              }`}
            >
              <X className="h-4 w-4" />
              Clear All
            </button>

            <span className="text-sm text-gray-600">
              {activeFilterCount} {activeFilterCount === 1 ? 'filter' : 'filters'} active
            </span>
          </div>
        </div>
      )}
    </div>
  );
};
