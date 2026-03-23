import React, { type ReactNode } from 'react';
import { DateRangePicker, type DateRange } from '../molecules/DateRangePicker';

/**
 * Configuration for a statistics card in StatsDashboard.
 *
 * @template T - Type of data items being analyzed
 */
export interface DashboardStatCardConfig<T> {
  /** Card title */
  title: string;
  /** Function to calculate the stat value from items */
  calculate: (items: T[]) => string | number;
  /** Visual variant for the card */
  variant?: 'red' | 'orange' | 'yellow' | 'green' | 'blue' | 'purple' | 'gray';
  /** Optional subtitle text */
  subtitle?: string;
  /** Optional icon element */
  icon?: ReactNode;
}

/**
 * Configuration for a bar chart item.
 */
export interface BarChartItem {
  /** Item label */
  label: string;
  /** Item value (used for bar width calculation) */
  value: number;
  /** Display text for the value */
  displayValue?: string;
  /** Optional custom color for this specific bar */
  color?: string;
}

/**
 * Configuration for a bar chart section.
 */
export interface BarChartConfig {
  /** Section title */
  title: string;
  /** Items to display as bars */
  items: BarChartItem[];
  /** Empty state message */
  emptyMessage?: string;
  /** Whether to show item numbering (1., 2., etc.) */
  showNumbering?: boolean;
  /** Maximum number of items to display */
  maxItems?: number;
  /** Default color for bars */
  defaultColor?: string;
}

/**
 * Configuration for time range filter.
 */
export interface TimeRangeConfig {
  /** Currently selected time range */
  value: string;
  /** Callback when time range changes */
  onChange: (value: string) => void;
  /** Available time range options */
  options: Array<{ label: string; value: string }>;
  /** Whether to show custom date range picker */
  showCustomPicker?: boolean;
  /** Callback when custom date range is selected */
  onCustomRangeChange?: (range: DateRange) => void;
}

/**
 * Configuration for export functionality.
 */
export interface ExportConfig {
  /** Export button label */
  buttonLabel?: string;
  /** Export options */
  options: Array<{
    /** Option label */
    label: string;
    /** Callback when option is clicked */
    onClick: () => void;
    /** Optional icon */
    icon?: ReactNode;
  }>;
}

/**
 * Configuration for insight items.
 */
export interface InsightItem {
  /** Insight text */
  text: string;
  /** Optional icon */
  icon?: string;
}

/**
 * Props for StatsDashboard component.
 *
 * @template T - Type of data items being analyzed
 */
export interface StatsDashboardProps<T> {
  /** Data items to analyze */
  items: T[];
  /** Dashboard title */
  title?: string;
  /** Statistics cards configuration */
  statsCards?: DashboardStatCardConfig<T>[];
  /** Bar chart sections configuration */
  barCharts?: BarChartConfig[];
  /** Time range filter configuration */
  timeRangeConfig?: TimeRangeConfig;
  /** Export functionality configuration */
  exportConfig?: ExportConfig;
  /** Summary insights */
  insights?: InsightItem[];
  /** Insights section title */
  insightsTitle?: string;
  /** Insights section variant */
  insightsVariant?: 'blue' | 'green' | 'purple' | 'yellow';
  /** Loading state */
  loading?: boolean;
  /** Loading message */
  loadingMessage?: string;
  /** Empty state message */
  emptyMessage?: string;
  /** Additional CSS classes */
  className?: string;
}

/**
 * StatsDashboard - Statistics dashboard with cards, bar charts, and time filtering
 *
 * A comprehensive dashboard component for displaying statistics, bar charts, and insights
 * with time range filtering and export functionality.
 *
 * ## Features
 * - **Statistics Cards**: Configurable stat cards with variant colors and icons
 * - **Bar Charts**: Multiple bar chart sections with customizable items
 * - **Time Range Filtering**: Quick time ranges and custom date range picker
 * - **Export**: Dropdown menu with multiple export options
 * - **Insights**: Summary insights section with customizable styling
 * - **Loading States**: Customizable loading message
 * - **Empty States**: Customizable empty message
 * - **Responsive**: Mobile-friendly layout
 *
 * ## Use Cases
 * - Analytics dashboards
 * - Usage monitoring
 * - Activity tracking
 * - Performance metrics
 * - Reporting interfaces
 *
 * @example
 * ```tsx
 * <StatsDashboard<UsageEvent>
 *   items={events}
 *   title="Analytics Dashboard"
 *   statsCards={[
 *     { title: 'Total Usage', calculate: (items) => items.length, variant: 'blue' },
 *     { title: 'Active Users', calculate: (items) => uniqueUsers(items), variant: 'green' },
 *   ]}
 *   barCharts={[
 *     {
 *       title: 'Top Apps',
 *       items: topApps.map(app => ({ label: app.name, value: app.usage })),
 *     },
 *   ]}
 *   timeRangeConfig={{
 *     value: timeRange,
 *     onChange: setTimeRange,
 *     options: [
 *       { label: 'Last 24 Hours', value: '24h' },
 *       { label: 'Last 7 Days', value: '7d' },
 *     ],
 *   }}
 *   insights={[
 *     { text: 'Usage increased by 20% this week', icon: '📈' },
 *   ]}
 * />
 * ```
 *
 * @template T - Type of data items being analyzed
 *
 * @doc.type component
 * @doc.purpose Statistics dashboard with cards, charts, and insights
 * @doc.layer ui
 * @doc.pattern Organism
 */
export function StatsDashboard<T>({
  items,
  title = 'Analytics Dashboard',
  statsCards = [],
  barCharts = [],
  timeRangeConfig,
  exportConfig,
  insights = [],
  insightsTitle = '📊 Summary Insights',
  insightsVariant = 'blue',
  loading = false,
  loadingMessage = 'Loading analytics...',
  emptyMessage = 'No data available for the selected time range.',
  className = '',
}: StatsDashboardProps<T>): React.JSX.Element {
  // Get variant classes for stat cards
  const getVariantClasses = (variant?: string) => {
    const variants = {
      red: 'bg-red-50 text-red-600',
      orange: 'bg-orange-50 text-orange-600',
      yellow: 'bg-yellow-50 text-yellow-600',
      green: 'bg-green-50 text-green-600',
      blue: 'bg-blue-50 text-blue-600',
      purple: 'bg-purple-50 text-purple-600',
      gray: 'bg-gray-50 text-gray-600',
    };
    return variants[variant as keyof typeof variants] || variants.blue;
  };

  // Get variant classes for insights section
  const getInsightsVariantClasses = (variant: string) => {
    const variants = {
      blue: 'bg-blue-50 border-blue-200 text-blue-800',
      green: 'bg-green-50 border-green-200 text-green-800',
      purple: 'bg-purple-50 border-purple-200 text-purple-800',
      yellow: 'bg-yellow-50 border-yellow-200 text-yellow-800',
    };
    return variants[variant as keyof typeof variants] || variants.blue;
  };

  // Render a bar chart section
  const renderBarChart = (config: BarChartConfig, index: number) => {
    const displayItems = config.maxItems ? config.items.slice(0, config.maxItems) : config.items;
    const maxValue = displayItems.length > 0 ? Math.max(...displayItems.map(item => item.value)) : 0;

    return (
      <div key={index} className="mb-6" data-testid={`chart-${index}`}>
        <h4 className="text-md font-semibold text-gray-900 mb-3">{config.title}</h4>
        {displayItems.length === 0 ? (
          <p className="text-gray-500">{config.emptyMessage || 'No data available'}</p>
        ) : (
          <div className="space-y-2">
            {displayItems.map((item, itemIndex) => (
              <div key={`${item.label}-${itemIndex}`} className="flex items-center" data-testid={`chart-item-${itemIndex}`}>
                {config.showNumbering && (
                  <span className="text-sm font-medium text-gray-700 w-8">{itemIndex + 1}.</span>
                )}
                <div className="flex-1">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-gray-900">{item.label}</span>
                    <span className="text-sm text-gray-600">
                      {item.displayValue || item.value}
                    </span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className="h-2 rounded-full"
                      data-testid={`chart-bar-${itemIndex}`}
                      style={{
                        width: `${maxValue > 0 ? (item.value / maxValue) * 100 : 0}%`,
                        backgroundColor: item.color || config.defaultColor || '#2563eb',
                      }}
                    ></div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className={`space-y-6 ${className}`}>
      <div className="bg-white shadow rounded-lg p-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">{title}</h2>
          <div className="flex gap-3">
            {/* Time Range Filter */}
            {timeRangeConfig && (
              <select
                data-testid="time-range-select"
                value={timeRangeConfig.value}
                onChange={(e) => timeRangeConfig.onChange(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {timeRangeConfig.options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            )}

            {/* Export Dropdown */}
            {exportConfig && (
              <div className="relative group">
                <button 
                  data-testid="export-button"
                  className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500"
                >
                  {exportConfig.buttonLabel || 'Export Data'} ▾
                </button>
                <div className="absolute right-0 mt-2 w-48 bg-white rounded-md shadow-lg hidden group-hover:block z-10 border border-gray-200">
                  {exportConfig.options.map((option, index) => (
                    <button
                      key={index}
                      data-testid={`export-option-${index}`}
                      onClick={option.onClick}
                      className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                    >
                      {option.icon && <span className="mr-2">{option.icon}</span>}
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Custom Date Range Picker */}
        {timeRangeConfig?.showCustomPicker && timeRangeConfig.onCustomRangeChange && (
          <div className="mb-6" data-testid="custom-date-picker">
            <DateRangePicker onDateRangeChange={timeRangeConfig.onCustomRangeChange} />
          </div>
        )}

        {/* Loading State */}
        {loading && (
          <div className="flex items-center justify-center py-12">
            <div className="text-center">
              <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mb-3"></div>
              <p className="text-gray-600">{loadingMessage}</p>
            </div>
          </div>
        )}

        {/* Empty State */}
        {!loading && items.length === 0 && (
          <div className="text-center py-12">
            <p className="text-gray-500">{emptyMessage}</p>
          </div>
        )}

        {/* Statistics Cards */}
        {!loading && items.length > 0 && statsCards.length > 0 && (
          <div className={`grid grid-cols-1 md:grid-cols-${Math.min(statsCards.length, 4)} gap-4 mb-8`}>
            {statsCards.map((card, index) => {
              const value = card.calculate(items);
              const variantClasses = getVariantClasses(card.variant);
              return (
                <div key={index} className={`rounded-lg p-4 ${variantClasses}`}>
                  <div className="flex items-center justify-between mb-1">
                    <h4 className="text-sm font-medium">{card.title}</h4>
                    {card.icon && <span className="text-2xl">{card.icon}</span>}
                  </div>
                  <p className="text-3xl font-bold">{value}</p>
                  {card.subtitle && <p className="text-xs mt-1 opacity-80">{card.subtitle}</p>}
                </div>
              );
            })}
          </div>
        )}

        {/* Bar Charts */}
        {!loading && items.length > 0 && barCharts.length > 0 && (
          <div className="space-y-8">
            {barCharts.map((chart, index) => renderBarChart(chart, index))}
          </div>
        )}

        {/* Insights Section */}
        {!loading && items.length > 0 && insights.length > 0 && (
          <div className={`border rounded-lg p-4 mt-8 ${getInsightsVariantClasses(insightsVariant)}`}>
            <h3 className="text-md font-semibold mb-2">{insightsTitle}</h3>
            <ul className="space-y-1 text-sm">
              {insights.map((insight, index) => (
                <li key={index}>
                  {insight.icon && <span className="mr-1">{insight.icon}</span>}
                  • {insight.text}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
