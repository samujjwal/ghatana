import type { Meta, StoryObj } from '@storybook/react';
import { StatsDashboard, type DashboardStatCardConfig, type BarChartConfig, type TimeRangeConfig, type ExportConfig, type InsightItem } from '../StatsDashboard';

/**
 * StatsDashboard - Statistics dashboard with cards, bar charts, and insights
 *
 * A comprehensive dashboard component for displaying statistics, bar charts, and insights
 * with time range filtering and export functionality.
 *
 * ## Features
 * - **Statistics Cards**: Configurable cards with variant colors and icons
 * - **Bar Charts**: Multiple sections with customizable bars
 * - **Time Range Filtering**: Quick ranges and custom date picker
 * - **Export**: Dropdown with multiple export options
 * - **Insights**: Summary insights section
 * - **Loading/Empty States**: Customizable messages
 *
 * ## Use Cases
 * - Analytics dashboards
 * - Usage monitoring
 * - Activity tracking
 * - Performance metrics
 */
const meta = {
  title: 'Organisms/StatsDashboard',
  component: StatsDashboard,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'A comprehensive dashboard for statistics, bar charts, and insights with filtering and export.',
      },
    },
  },
  tags: ['autodocs'],
} satisfies Meta<typeof StatsDashboard>;

export default meta;
type Story = StoryObj<typeof meta>;

// Sample data types
interface UsageData {
  id: string;
  app: string;
  duration: number;
  device: string;
  timestamp: string;
}

interface AnalyticsData {
  id: string;
  metric: string;
  value: number;
  category: string;
}

// Sample data
const usageData: UsageData[] = [
  { id: '1', app: 'YouTube', duration: 120, device: 'iPhone', timestamp: '2025-01-01' },
  { id: '2', app: 'Instagram', duration: 90, device: 'iPad', timestamp: '2025-01-01' },
  { id: '3', app: 'Safari', duration: 60, device: 'iPhone', timestamp: '2025-01-02' },
  { id: '4', app: 'TikTok', duration: 150, device: 'Android', timestamp: '2025-01-02' },
  { id: '5', app: 'YouTube', duration: 80, device: 'iPad', timestamp: '2025-01-03' },
];

const analyticsData: AnalyticsData[] = [
  { id: '1', metric: 'Page Views', value: 1250, category: 'Traffic' },
  { id: '2', metric: 'Unique Visitors', value: 450, category: 'Traffic' },
  { id: '3', metric: 'Bounce Rate', value: 35, category: 'Engagement' },
  { id: '4', metric: 'Avg Session', value: 180, category: 'Engagement' },
];

/**
 * Usage Analytics Dashboard
 *
 * Shows app usage statistics with time filtering and export.
 */
export const UsageAnalytics: Story = {
  args: {
    items: usageData,
    title: 'App Usage Analytics',
    statsCards: [
      {
        title: 'Total Usage',
        calculate: (items: UsageData[]) => `${items.reduce((sum, i) => sum + i.duration, 0)} min`,
        variant: 'blue',
        icon: '⏱️',
      },
      {
        title: 'Active Devices',
        calculate: (items: UsageData[]) => new Set(items.map(i => i.device)).size,
        variant: 'green',
        icon: '📱',
      },
      {
        title: 'Avg Per Session',
        calculate: (items: UsageData[]) => `${Math.round(items.reduce((sum, i) => sum + i.duration, 0) / items.length)} min`,
        variant: 'purple',
        icon: '📊',
      },
    ] as DashboardStatCardConfig<UsageData>[],
    barCharts: [
      {
        title: 'Top Apps by Usage',
        items: Object.entries(
          usageData.reduce((acc, item) => {
            acc[item.app] = (acc[item.app] || 0) + item.duration;
            return acc;
          }, {} as Record<string, number>)
        )
          .map(([app, duration]) => ({ label: app, value: duration, displayValue: `${duration} min` }))
          .sort((a, b) => b.value - a.value),
        showNumbering: true,
        defaultColor: '#2563eb',
      },
    ] as BarChartConfig[],
    timeRangeConfig: {
      value: '7d',
      onChange: (value: string) => console.log('Time range changed:', value),
      options: [
        { label: 'Last 24 Hours', value: '24h' },
        { label: 'Last 7 Days', value: '7d' },
        { label: 'Last 30 Days', value: '30d' },
      ],
    } as TimeRangeConfig,
    exportConfig: {
      options: [
        { label: 'Export CSV', onClick: () => alert('Export CSV') },
        { label: 'Export PDF', onClick: () => alert('Export PDF') },
      ],
    } as ExportConfig,
    insights: [
      { text: `Total screen time: ${usageData.reduce((sum, i) => sum + i.duration, 0)} minutes`, icon: '⏱️' },
      { text: `Most used app: YouTube`, icon: '📱' },
    ] as InsightItem[],
  },
};

/**
 * Website Analytics Dashboard
 *
 * Shows website metrics with various statistic types.
 */
export const WebsiteAnalytics: Story = {
  args: {
    items: analyticsData,
    title: 'Website Analytics',
    statsCards: [
      {
        title: 'Page Views',
        calculate: () => '1,250',
        variant: 'blue',
        subtitle: '+12% from last week',
      },
      {
        title: 'Unique Visitors',
        calculate: () => '450',
        variant: 'green',
        subtitle: '+8% from last week',
      },
      {
        title: 'Bounce Rate',
        calculate: () => '35%',
        variant: 'yellow',
        subtitle: '-5% from last week',
      },
      {
        title: 'Avg Session',
        calculate: () => '3m',
        variant: 'purple',
        subtitle: '+15s from last week',
      },
    ] as DashboardStatCardConfig<AnalyticsData>[],
    barCharts: [
      {
        title: 'Traffic by Category',
        items: [
          { label: 'Direct', value: 45, displayValue: '45%', color: '#2563eb' },
          { label: 'Organic Search', value: 35, displayValue: '35%', color: '#10b981' },
          { label: 'Social', value: 15, displayValue: '15%', color: '#8b5cf6' },
          { label: 'Referral', value: 5, displayValue: '5%', color: '#f59e0b' },
        ],
      },
    ] as BarChartConfig[],
    insights: [
      { text: 'Traffic increased by 12% this week', icon: '📈' },
      { text: 'Most popular page: /home', icon: '🏠' },
      { text: 'Peak traffic at 2 PM daily', icon: '⏰' },
    ] as InsightItem[],
  },
};

/**
 * Minimal Configuration
 *
 * Shows dashboard with minimal props.
 */
export const MinimalConfiguration: Story = {
  args: {
    items: analyticsData,
  },
};

/**
 * With Statistics Cards Only
 *
 * Shows just statistics cards without charts.
 */
export const WithStatsCardsOnly: Story = {
  args: {
    items: analyticsData,
    title: 'Quick Stats',
    statsCards: [
      { title: 'Total', calculate: (items: AnalyticsData[]) => items.length, variant: 'blue' },
      { title: 'Sum', calculate: (items: AnalyticsData[]) => items.reduce((sum, i) => sum + i.value, 0), variant: 'green' },
      { title: 'Average', calculate: (items: AnalyticsData[]) => Math.round(items.reduce((sum, i) => sum + i.value, 0) / items.length), variant: 'purple' },
    ] as DashboardStatCardConfig<AnalyticsData>[],
  },
};

/**
 * With Bar Charts Only
 *
 * Shows just bar charts without statistics.
 */
export const WithBarChartsOnly: Story = {
  args: {
    items: usageData,
    title: 'App Usage',
    barCharts: [
      {
        title: 'Usage by App',
        items: [
          { label: 'YouTube', value: 200, displayValue: '200 min' },
          { label: 'TikTok', value: 150, displayValue: '150 min' },
          { label: 'Instagram', value: 90, displayValue: '90 min' },
          { label: 'Safari', value: 60, displayValue: '60 min' },
        ],
        showNumbering: true,
      },
    ] as BarChartConfig[],
  },
};

/**
 * Multiple Bar Charts
 *
 * Shows multiple bar chart sections.
 */
export const MultipleBarCharts: Story = {
  args: {
    items: usageData,
    title: 'Comprehensive Analytics',
    barCharts: [
      {
        title: 'Top Apps',
        items: [
          { label: 'YouTube', value: 200 },
          { label: 'TikTok', value: 150 },
          { label: 'Instagram', value: 90 },
        ],
        showNumbering: true,
        maxItems: 3,
      },
      {
        title: 'Top Devices',
        items: [
          { label: 'iPhone', value: 180 },
          { label: 'iPad', value: 170 },
          { label: 'Android', value: 150 },
        ],
        showNumbering: false,
        defaultColor: '#10b981',
      },
    ] as BarChartConfig[],
  },
};

/**
 * With Time Range Filter
 *
 * Shows time range selector functionality.
 */
export const WithTimeRangeFilter: Story = {
  args: {
    items: usageData,
    title: 'Filtered Analytics',
    statsCards: [
      { title: 'Total', calculate: (items: UsageData[]) => items.length, variant: 'blue' },
    ] as DashboardStatCardConfig<UsageData>[],
    timeRangeConfig: {
      value: '7d',
      onChange: (value: string) => console.log('Selected:', value),
      options: [
        { label: 'Today', value: 'today' },
        { label: 'Yesterday', value: 'yesterday' },
        { label: 'Last 7 Days', value: '7d' },
        { label: 'Last 30 Days', value: '30d' },
        { label: 'Custom', value: 'custom' },
      ],
    } as TimeRangeConfig,
  },
};

/**
 * With Export Options
 *
 * Shows export dropdown functionality.
 */
export const WithExportOptions: Story = {
  args: {
    items: usageData,
    title: 'Exportable Data',
    statsCards: [
      { title: 'Records', calculate: (items: UsageData[]) => items.length, variant: 'blue' },
    ] as DashboardStatCardConfig<UsageData>[],
    exportConfig: {
      buttonLabel: 'Download',
      options: [
        { label: 'CSV File', onClick: () => alert('Export CSV') },
        { label: 'Excel File', onClick: () => alert('Export Excel') },
        { label: 'PDF Report', onClick: () => alert('Export PDF') },
        { label: 'JSON Data', onClick: () => alert('Export JSON') },
      ],
    } as ExportConfig,
  },
};

/**
 * With Insights
 *
 * Shows insights section.
 */
export const WithInsights: Story = {
  args: {
    items: usageData,
    title: 'Analytics with Insights',
    statsCards: [
      { title: 'Total', calculate: (items: UsageData[]) => items.length, variant: 'blue' },
    ] as DashboardStatCardConfig<UsageData>[],
    insights: [
      { text: 'Usage increased by 25% this week', icon: '📈' },
      { text: 'New high score for daily usage', icon: '🏆' },
      { text: 'Most active day was Monday', icon: '📅' },
    ] as InsightItem[],
    insightsTitle: '💡 Key Insights',
    insightsVariant: 'green',
  },
};

/**
 * Loading State
 *
 * Shows loading indicator.
 */
export const LoadingState: Story = {
  args: {
    items: [],
    loading: true,
    loadingMessage: 'Loading analytics data...',
  },
};

/**
 * Empty State
 *
 * Shows empty message when no data.
 */
export const EmptyState: Story = {
  args: {
    items: [],
    title: 'No Data Available',
    emptyMessage: 'No analytics data available for the selected time range. Try selecting a different period.',
  },
};
