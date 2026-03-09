/**
 * @fileoverview Time Range Selector Component
 *
 * Allows users to select predefined or custom time ranges
 * for analytics data filtering.
 *
 * @module ui/components/filters
 * @since 2.0.0
 */

import React, { useState } from 'react';

export interface TimeRange {
  from: number;
  to: number;
  preset?: '1h' | '24h' | '7d' | '30d' | 'custom';
}

interface TimeRangeSelectorProps {
  value: TimeRange;
  onChange: (range: TimeRange) => void;
  disabled?: boolean;
}

const PRESET_RANGES: Record<Exclude<TimeRange['preset'], 'custom'>, { label: string; duration: number }> = {
  '1h': { label: 'Last Hour', duration: 60 * 60 * 1000 },
  '24h': { label: 'Last 24 Hours', duration: 24 * 60 * 60 * 1000 },
  '7d': { label: 'Last 7 Days', duration: 7 * 24 * 60 * 60 * 1000 },
  '30d': { label: 'Last 30 Days', duration: 30 * 24 * 60 * 60 * 1000 },
};

/**
 * Time Range Selector Component
 *
 * Provides preset time range options and custom date selection.
 */
export const TimeRangeSelector: React.FC<TimeRangeSelectorProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  const [showCustom, setShowCustom] = useState(false);
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');

  const handlePresetSelect = (preset: Exclude<TimeRange['preset'], 'custom'>) => {
    const now = Date.now();
    const range = {
      from: now - PRESET_RANGES[preset].duration,
      to: now,
      preset,
    };
    onChange(range);
  };

  const handleCustomApply = () => {
    const from = new Date(customFrom).getTime();
    const to = new Date(customTo).getTime();
    
    if (from && to && from < to) {
      onChange({ from, to, preset: 'custom' });
      setShowCustom(false);
    }
  };

  const formatDateTime = (timestamp: number): string => {
    return new Date(timestamp).toLocaleString();
  };

  const getCurrentLabel = (): string => {
    if (value.preset && value.preset !== 'custom') {
      return PRESET_RANGES[value.preset].label;
    }
    if (value.preset === 'custom') {
      return 'Custom Range';
    }
    return 'Custom Range';
  };

  return (
    <div className="relative">
      {/* Main selector */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => setShowCustom(!showCustom)}
          disabled={disabled}
          className="px-3 py-2 text-sm border border-slate-300 rounded-md hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <span className="font-medium">{getCurrentLabel()}</span>
          <span className="ml-1 text-slate-500">▼</span>
        </button>
        
        {/* Current range display */}
        <span className="text-xs text-slate-500 hidden sm:inline">
          {formatDateTime(value.from)} - {formatDateTime(value.to)}
        </span>
      </div>

      {/* Dropdown */}
      {showCustom && (
        <div className="absolute top-full left-0 mt-1 w-80 bg-white border border-slate-200 rounded-lg shadow-lg z-50">
          <div className="p-4">
            {/* Preset options */}
            <div className="mb-4">
              <h3 className="text-sm font-medium text-slate-700 mb-2">Quick Select</h3>
              <div className="grid grid-cols-2 gap-2">
                {Object.entries(PRESET_RANGES).map(([preset, config]) => (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => handlePresetSelect(preset as Exclude<TimeRange['preset'], 'custom'>)}
                    className={`px-3 py-2 text-sm rounded-md text-left transition-colors ${
                      value.preset === preset
                        ? 'bg-blue-100 text-blue-700 border border-blue-200'
                        : 'hover:bg-slate-50 border border-slate-200'
                    }`}
                  >
                    {config.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Custom range */}
            <div>
              <h3 className="text-sm font-medium text-slate-700 mb-2">Custom Range</h3>
              <div className="space-y-2">
                <div>
                  <label className="block text-xs text-slate-500 mb-1">From</label>
                  <input
                    type="datetime-local"
                    value={customFrom}
                    onChange={(e) => setCustomFrom(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 mb-1">To</label>
                  <input
                    type="datetime-local"
                    value={customTo}
                    onChange={(e) => setCustomTo(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleCustomApply}
                    className="flex-1 px-3 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    Apply
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowCustom(false)}
                    className="flex-1 px-3 py-2 text-sm border border-slate-300 rounded-md hover:bg-slate-50"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TimeRangeSelector;
