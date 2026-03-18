import { useState, memo } from 'react';

/**
 * Date range value object.
 * 
 * @doc.type interface
 * @doc.purpose Date range data structure for start and end dates
 * @doc.layer ui
 * @doc.pattern Value Object
 */
export interface DateRange {
  startDate: string;
  endDate: string;
}

/**
 * Predefined date range preset configuration.
 * 
 * @doc.type interface
 * @doc.purpose Configuration for quick-select date range presets
 * @doc.layer ui
 * @doc.pattern Configuration
 */
export interface DateRangePreset {
  /** Unique identifier for the preset */
  id: string;
  /** Display label for the preset button */
  label: string;
  /** Handler to calculate the date range */
  getValue: () => DateRange;
}

/**
 * Props for DateRangePicker component.
 * 
 * @doc.type interface
 * @doc.purpose Component props for date range selection
 * @doc.layer ui
 * @doc.pattern Component Props
 */
export interface DateRangePickerProps {
  /** Callback fired when date range changes */
  onDateRangeChange: (range: DateRange) => void;
  /** Initial date range value */
  initialRange?: DateRange;
  /** Minimum selectable date (ISO format: YYYY-MM-DD) */
  minDate?: string;
  /** Maximum selectable date (ISO format: YYYY-MM-DD) */
  maxDate?: string;
  /** Custom preset configurations. If not provided, default presets are used */
  presets?: DateRangePreset[];
  /** Whether the component is disabled */
  disabled?: boolean;
  /** Additional CSS classes */
  className?: string;
  /** Whether to show the apply button for custom ranges */
  showApplyButton?: boolean;
  /** Whether to show the selected range display */
  showSelectedRange?: boolean;
}

/**
 * Default date range presets.
 */
const DEFAULT_PRESETS: DateRangePreset[] = [
  {
    id: 'today',
    label: 'Today',
    getValue: () => {
      const now = new Date();
      const today = new Date(now.setHours(0, 0, 0, 0)).toISOString().split('T')[0];
      return { startDate: today, endDate: today };
    },
  },
  {
    id: 'yesterday',
    label: 'Yesterday',
    getValue: () => {
      const now = new Date();
      const yesterday = new Date(now.setDate(now.getDate() - 1));
      const date = yesterday.toISOString().split('T')[0];
      return { startDate: date, endDate: date };
    },
  },
  {
    id: 'last7days',
    label: 'Last 7 Days',
    getValue: () => {
      const now = new Date();
      const end = now.toISOString().split('T')[0];
      const start = new Date(now.setDate(now.getDate() - 7)).toISOString().split('T')[0];
      return { startDate: start, endDate: end };
    },
  },
  {
    id: 'last30days',
    label: 'Last 30 Days',
    getValue: () => {
      const now = new Date();
      const end = now.toISOString().split('T')[0];
      const start = new Date(now.setDate(now.getDate() - 30)).toISOString().split('T')[0];
      return { startDate: start, endDate: end };
    },
  },
  {
    id: 'thisMonth',
    label: 'This Month',
    getValue: () => {
      const now = new Date();
      const start = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
      const end = now.toISOString().split('T')[0];
      return { startDate: start, endDate: end };
    },
  },
  {
    id: 'lastMonth',
    label: 'Last Month',
    getValue: () => {
      const now = new Date();
      const start = new Date(now.getFullYear(), now.getMonth() - 1, 1).toISOString().split('T')[0];
      const end = new Date(now.getFullYear(), now.getMonth(), 0).toISOString().split('T')[0];
      return { startDate: start, endDate: end };
    },
  },
];

/**
 * DateRangePicker component for selecting date ranges.
 * 
 * <p><b>Purpose</b><br>
 * Provides an intuitive interface for selecting date ranges with quick presets
 * and custom date selection. Supports both predefined ranges (Today, Yesterday,
 * Last 7 Days, etc.) and custom start/end date selection.
 * 
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <DateRangePicker
 *   onDateRangeChange={(range) => console.log(range)}
 *   initialRange={{ startDate: '2025-01-01', endDate: '2025-01-31' }}
 *   minDate="2024-01-01"
 *   maxDate="2025-12-31"
 * />
 * }</pre>
 * 
 * <p><b>Custom Presets</b><br>
 * <pre>{@code
 * const customPresets: DateRangePreset[] = [
 *   {
 *     id: 'custom-quarter',
 *     label: 'This Quarter',
 *     getValue: () => ({ startDate: '2025-01-01', endDate: '2025-03-31' })
 *   }
 * ];
 * 
 * <DateRangePicker
 *   onDateRangeChange={handleChange}
 *   presets={customPresets}
 * />
 * }</pre>
 * 
 * <p><b>Features</b><br>
 * - Quick select presets (Today, Yesterday, Last 7 Days, Last 30 Days, This Month, Last Month)
 * - Custom date range selection with validation
 * - Min/max date constraints
 * - Configurable presets
 * - Disabled state support
 * - Selected range display
 * - Responsive design
 * 
 * <p><b>Accessibility</b><br>
 * - Keyboard navigation support
 * - Proper labeling for screen readers
 * - Focus management
 * - ARIA attributes
 * 
 * @doc.type component
 * @doc.purpose Date range selection with presets and custom range
 * @doc.layer ui
 * @doc.pattern Molecule
 */
function DateRangePickerComponent({
  onDateRangeChange,
  initialRange,
  minDate,
  maxDate,
  presets = DEFAULT_PRESETS,
  disabled = false,
  className = '',
  showApplyButton = true,
  showSelectedRange = true,
}: DateRangePickerProps) {
  const today = maxDate || new Date().toISOString().split('T')[0];
  const defaultStartDate = minDate || new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  
  const [startDate, setStartDate] = useState(initialRange?.startDate || defaultStartDate);
  const [endDate, setEndDate] = useState(initialRange?.endDate || today);
  const [selectedPreset, setSelectedPreset] = useState<string>('custom');

  const handlePresetSelect = (preset: DateRangePreset) => {
    setSelectedPreset(preset.id);
    const range = preset.getValue();
    setStartDate(range.startDate);
    setEndDate(range.endDate);
    onDateRangeChange(range);
  };

  const handleStartDateChange = (value: string) => {
    setStartDate(value);
    setSelectedPreset('custom');
    if (value && endDate) {
      onDateRangeChange({ startDate: value, endDate });
    }
  };

  const handleEndDateChange = (value: string) => {
    setEndDate(value);
    setSelectedPreset('custom');
    if (startDate && value) {
      onDateRangeChange({ startDate, endDate: value });
    }
  };

  const handleApplyCustomRange = () => {
    if (startDate && endDate) {
      onDateRangeChange({ startDate, endDate });
    }
  };

  return (
    <div className={`bg-white rounded-lg shadow p-4 space-y-4 ${className}`}>
      <div>
        <h3 className="text-sm font-medium text-gray-900 mb-2">Date Range</h3>
        
        {/* Preset Buttons */}
        <div className="grid grid-cols-3 gap-2 mb-4">
          {presets.map((preset) => (
            <button
              key={preset.id}
              onClick={() => handlePresetSelect(preset)}
              disabled={disabled}
              className={`px-3 py-2 text-xs rounded-md border transition-colors ${
                selectedPreset === preset.id
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed'
              }`}
              aria-pressed={selectedPreset === preset.id}
            >
              {preset.label}
            </button>
          ))}
        </div>

        {/* Custom Date Inputs */}
        <div className="space-y-3">
          <div>
            <label htmlFor="start-date" className="block text-xs font-medium text-gray-700 mb-1">
              Start Date
            </label>
            <input
              id="start-date"
              type="date"
              value={startDate}
              onChange={(e) => handleStartDateChange(e.target.value)}
              min={minDate}
              max={endDate}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="Start date"
            />
          </div>
          <div>
            <label htmlFor="end-date" className="block text-xs font-medium text-gray-700 mb-1">
              End Date
            </label>
            <input
              id="end-date"
              type="date"
              value={endDate}
              onChange={(e) => handleEndDateChange(e.target.value)}
              min={startDate}
              max={maxDate || today}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="End date"
            />
          </div>
        </div>

        {/* Apply Button for Custom Range */}
        {showApplyButton && selectedPreset === 'custom' && (
          <button
            onClick={handleApplyCustomRange}
            disabled={disabled || !startDate || !endDate}
            className="mt-3 w-full px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            aria-label="Apply custom date range"
          >
            Apply Custom Range
          </button>
        )}
      </div>

      {/* Selected Range Display */}
      {showSelectedRange && (
        <div className="pt-3 border-t border-gray-200">
          <p className="text-xs text-gray-600">
            Selected: <span className="font-medium text-gray-900">{startDate}</span> to{' '}
            <span className="font-medium text-gray-900">{endDate}</span>
          </p>
        </div>
      )}
    </div>
  );
}

/**
 * Memoized DateRangePicker component.
 * Optimized to prevent unnecessary re-renders when props haven't changed.
 */
export const DateRangePicker = memo(DateRangePickerComponent);
DateRangePicker.displayName = 'DateRangePicker';

export default DateRangePicker;
