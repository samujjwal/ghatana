import { useState, useEffect, useMemo, memo } from 'react';
import { useAtomValue } from 'jotai';
import { usageEventsAtom, blockEventsAtom } from '../stores/eventsStore';
import { 
  StatsDashboard,
  type DashboardStatCardConfig,
  type BarChartConfig,
  type TimeRangeConfig,
  type ExportConfig,
  type InsightItem,
  type DateRange,
} from '@ghatana/design-system';
import { exportUsageEventsToCSV, exportBlockEventsToCSV } from '../utils/csvExport';
import { exportUsageEventsToPDF, exportBlockEventsToPDF, exportAnalyticsSummaryToPDF } from '../utils/pdfExport';

interface AnalyticsNewProps {
  onInsightGenerated?: (insight: string) => void;
}

/**
 * AnalyticsNew - Analytics dashboard using StatsDashboard
 * 
 * Displays usage and block statistics with time range filtering and export functionality.
 * 
 * Features:
 * - Usage statistics (total minutes, devices, avg per device)
 * - Block statistics (total blocks, unique items)
 * - Top apps by usage (bar chart)
 * - Most blocked items (bar chart)
 * - Block reasons breakdown
 * - Time range filtering (24h, 7d, 30d, custom)
 * - Export to CSV and PDF
 * 
 * @example
 * ```tsx
 * <AnalyticsNew onInsightGenerated={(insight) => console.log(insight)} />
 * ```
 */
function AnalyticsNewComponent({ onInsightGenerated }: AnalyticsNewProps) {
  const usageEvents = useAtomValue(usageEventsAtom);
  const blockEvents = useAtomValue(blockEventsAtom);
  const [timeRange, setTimeRange] = useState<'24h' | '7d' | '30d' | 'custom'>('24h');
  const [customDateRange, setCustomDateRange] = useState<DateRange | null>(null);

  // Helper to get time range in milliseconds
  const getTimeRangeMs = (range: '24h' | '7d' | '30d'): number => {
    switch (range) {
      case '24h': return 24 * 60 * 60 * 1000;
      case '7d': return 7 * 24 * 60 * 60 * 1000;
      case '30d': return 30 * 24 * 60 * 60 * 1000;
      default: return 24 * 60 * 60 * 1000;
    }
  };

  // Filter events by time range
  const filteredUsageEvents = useMemo(() => {
    const now = new Date();
    let cutoffTime: Date;
    
    if (timeRange === 'custom' && customDateRange) {
      cutoffTime = new Date(customDateRange.startDate);
    } else if (timeRange !== 'custom') {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs(timeRange));
    } else {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs('7d'));
    }
    
    return usageEvents.filter(event => {
      const eventDate = new Date(event.usageSession.timestamp);
      if (timeRange === 'custom' && customDateRange) {
        const endDate = new Date(customDateRange.endDate);
        endDate.setHours(23, 59, 59, 999);
        return eventDate >= cutoffTime && eventDate <= endDate;
      }
      return eventDate >= cutoffTime;
    });
  }, [usageEvents, timeRange, customDateRange]);

  const filteredBlockEvents = useMemo(() => {
    const now = new Date();
    let cutoffTime: Date;
    
    if (timeRange === 'custom' && customDateRange) {
      cutoffTime = new Date(customDateRange.startDate);
    } else if (timeRange !== 'custom') {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs(timeRange));
    } else {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs('7d'));
    }
    
    return blockEvents.filter(event => {
      const eventDate = new Date(event.blockEvent.timestamp);
      if (timeRange === 'custom' && customDateRange) {
        const endDate = new Date(customDateRange.endDate);
        endDate.setHours(23, 59, 59, 999);
        return eventDate >= cutoffTime && eventDate <= endDate;
      }
      return eventDate >= cutoffTime;
    });
  }, [blockEvents, timeRange, customDateRange]);

  // Calculate usage statistics
  const usageStats = useMemo(() => {
    const totalMinutes = filteredUsageEvents.reduce(
      (sum, event) => sum + (event.usageSession.duration_seconds / 60),
      0
    );

    const deviceIds = new Set(filteredUsageEvents.map(event => event.device.id));
    const uniqueDevices = deviceIds.size;

    // Top apps by usage
    const appUsage = new Map<string, number>();
    filteredUsageEvents.forEach(event => {
      const app = event.usageSession.item_name;
      appUsage.set(app, (appUsage.get(app) || 0) + (event.usageSession.duration_seconds / 60));
    });
    const topApps = Array.from(appUsage.entries())
      .map(([app, minutes]) => ({ app, minutes }))
      .sort((a, b) => b.minutes - a.minutes)
      .slice(0, 5);

    return { totalMinutes, uniqueDevices, topApps };
  }, [filteredUsageEvents]);

  // Calculate block statistics
  const blockStats = useMemo(() => {
    const totalBlocks = filteredBlockEvents.length;

    // Top blocked items
    const itemCounts = new Map<string, number>();
    filteredBlockEvents.forEach(event => {
      const item = event.blockEvent.blocked_item;
      itemCounts.set(item, (itemCounts.get(item) || 0) + 1);
    });
    const topBlockedItems = Array.from(itemCounts.entries())
      .map(([item, count]) => ({ item, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);

    // Blocks by reason
    const reasonCounts = new Map<string, number>();
    filteredBlockEvents.forEach(event => {
      const reason = event.blockEvent.reason;
      reasonCounts.set(reason, (reasonCounts.get(reason) || 0) + 1);
    });
    const blocksByReason = Array.from(reasonCounts.entries())
      .map(([reason, count]) => ({ reason, count }))
      .sort((a, b) => b.count - a.count);

    return { totalBlocks, topBlockedItems, blocksByReason, uniqueDevices: new Set(filteredBlockEvents.map(e => e.device.id)).size };
  }, [filteredBlockEvents]);

  // Generate insights
  useEffect(() => {
    if (usageStats.topApps.length > 0) {
      const topApp = usageStats.topApps[0];
      const insight = `${topApp.app} is the most used app with ${Math.round(topApp.minutes)} minutes of usage.`;
      onInsightGenerated?.(insight);
    }
  }, [usageStats, onInsightGenerated]);

  // Handle date range change
  const handleDateRangeChange = (range: DateRange) => {
    setCustomDateRange(range);
    setTimeRange('custom');
  };

  // Statistics cards configuration
  const statsCards: DashboardStatCardConfig<typeof filteredUsageEvents[0]>[] = [
    {
      title: 'Total Usage',
      calculate: () => `${Math.round(usageStats.totalMinutes)} min`,
      variant: 'blue',
      icon: '⏱️',
    },
    {
      title: 'Active Devices',
      calculate: () => usageStats.uniqueDevices,
      variant: 'green',
      icon: '📱',
    },
    {
      title: 'Avg. Per Device',
      calculate: () => {
        const avg = usageStats.uniqueDevices > 0
          ? Math.round(usageStats.totalMinutes / usageStats.uniqueDevices)
          : 0;
        return `${avg} min`;
      },
      variant: 'purple',
      icon: '📊',
    },
    {
      title: 'Total Blocks',
      calculate: () => blockStats.totalBlocks,
      variant: 'red',
      icon: '🚫',
    },
    {
      title: 'Unique Items Blocked',
      calculate: () => blockStats.topBlockedItems.length,
      variant: 'orange',
      icon: '🔒',
    },
  ];

  // Bar charts configuration
  const barCharts: BarChartConfig[] = [
    {
      title: 'Top Apps by Usage',
      items: usageStats.topApps.map(app => ({
        label: app.app,
        value: app.minutes,
        displayValue: `${Math.round(app.minutes)} min`,
      })),
      emptyMessage: 'No usage data available',
      showNumbering: true,
      defaultColor: '#2563eb',
    },
    {
      title: 'Most Blocked Items',
      items: blockStats.topBlockedItems.map(item => ({
        label: item.item,
        value: item.count,
        displayValue: `${item.count} blocks`,
      })),
      emptyMessage: 'No blocks recorded',
      showNumbering: true,
      defaultColor: '#dc2626',
    },
    {
      title: 'Block Reasons',
      items: blockStats.blocksByReason.map(reason => ({
        label: reason.reason,
        value: reason.count,
        displayValue: `${reason.count}`,
      })),
      emptyMessage: 'No block reasons available',
      showNumbering: false,
      defaultColor: '#ea580c',
    },
  ];

  // Time range configuration
  const timeRangeConfig: TimeRangeConfig = {
    value: timeRange,
    onChange: (value) => {
      const newRange = value as typeof timeRange;
      setTimeRange(newRange);
      if (newRange !== 'custom') {
        setCustomDateRange(null);
      }
    },
    options: [
      { label: 'Last 24 Hours', value: '24h' },
      { label: 'Last 7 Days', value: '7d' },
      { label: 'Last 30 Days', value: '30d' },
      { label: 'Custom Range', value: 'custom' },
    ],
    showCustomPicker: timeRange === 'custom',
    onCustomRangeChange: handleDateRangeChange,
  };

  // Export configuration
  const exportConfig: ExportConfig = {
    buttonLabel: 'Export Data',
    options: [
      {
        label: 'Usage Events (CSV)',
        onClick: () => exportUsageEventsToCSV(filteredUsageEvents),
      },
      {
        label: 'Usage Events (PDF)',
        onClick: () => exportUsageEventsToPDF(filteredUsageEvents),
      },
      {
        label: 'Block Events (CSV)',
        onClick: () => exportBlockEventsToCSV(filteredBlockEvents),
      },
      {
        label: 'Block Events (PDF)',
        onClick: () => exportBlockEventsToPDF(filteredBlockEvents),
      },
      {
        label: 'Summary Report (PDF)',
        onClick: () => exportAnalyticsSummaryToPDF(usageStats, blockStats, timeRange),
      },
    ],
  };

  // Insights
  const insights: InsightItem[] = [];
  if (usageStats.totalMinutes > 0) {
    insights.push({
      icon: '⏱️',
      text: `Total screen time: ${Math.floor(usageStats.totalMinutes / 60)} hours ${Math.round(usageStats.totalMinutes % 60)} minutes`,
    });
  }
  if (blockStats.totalBlocks > 0) {
    insights.push({
      icon: '🚫',
      text: `${blockStats.totalBlocks} items were blocked to maintain healthy usage`,
    });
  }
  if (usageStats.topApps.length > 0) {
    insights.push({
      icon: '📱',
      text: `Most used app: ${usageStats.topApps[0].app} (${Math.round(usageStats.topApps[0].minutes)} minutes)`,
    });
  }
  if (blockStats.topBlockedItems.length > 0) {
    insights.push({
      icon: '🔒',
      text: `Most blocked item: ${blockStats.topBlockedItems[0].item} (${blockStats.topBlockedItems[0].count} times)`,
    });
  }

  return (
    <StatsDashboard
      items={filteredUsageEvents}
      title="Analytics & Insights"
      statsCards={statsCards}
      barCharts={barCharts}
      timeRangeConfig={timeRangeConfig}
      exportConfig={exportConfig}
      insights={insights}
      insightsTitle="📊 Summary Insights"
      insightsVariant="blue"
    />
  );
}

// Export memoized component to prevent unnecessary re-renders
export const AnalyticsNew = memo(AnalyticsNewComponent);
