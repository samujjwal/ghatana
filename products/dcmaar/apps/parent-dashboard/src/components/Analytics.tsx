import { useState, useEffect, useMemo, memo } from 'react';
import { useAtomValue } from 'jotai';
import { usageEventsAtom, blockEventsAtom } from '../stores/eventsStore';
import { DateRangePicker, type DateRange } from '@ghatana/ui';
import { exportUsageEventsToCSV, exportBlockEventsToCSV } from '../utils/csvExport';
import { exportUsageEventsToPDF, exportBlockEventsToPDF, exportAnalyticsSummaryToPDF } from '../utils/pdfExport';

interface AnalyticsProps {
  onInsightGenerated?: (insight: string) => void;
}

function normalizeDomain(itemName: string): string {
  try {
    const trimmed = itemName.trim();
    if (!trimmed) return 'Unknown';

    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      const url = new URL(trimmed);
      return url.hostname.replace(/^www\./, '');
    }

    // If it already looks like a bare domain (e.g., youtube.com), keep as-is
    if (/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(trimmed)) {
      return trimmed.replace(/^www\./, '');
    }

    // Fallback: treat app/website name as-is
    return trimmed;
  } catch {
    return itemName;
  }
}

function AnalyticsComponent({ onInsightGenerated }: AnalyticsProps) {
  const usageEvents = useAtomValue(usageEventsAtom);
  const blockEvents = useAtomValue(blockEventsAtom);
  const [timeRange, setTimeRange] = useState<'24h' | '7d' | '30d' | 'custom'>('24h');
  const [customDateRange, setCustomDateRange] = useState<DateRange | null>(null);
  const [showDatePicker, setShowDatePicker] = useState(false);

  // Calculate usage statistics
  const usageStats = useMemo(() => {
    const now = new Date();
    let cutoffTime: Date;
    
    if (timeRange === 'custom' && customDateRange) {
      cutoffTime = new Date(customDateRange.startDate);
    } else if (timeRange !== 'custom') {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs(timeRange));
    } else {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs('7d'));
    }
    
    const filteredEvents = usageEvents.filter(
      event => {
        const eventDate = new Date(event.usageSession.timestamp);
        if (timeRange === 'custom' && customDateRange) {
          const endDate = new Date(customDateRange.endDate);
          endDate.setHours(23, 59, 59, 999);
          return eventDate >= cutoffTime && eventDate <= endDate;
        }
        return eventDate >= cutoffTime;
      }
    );

    // Total usage in minutes
    const totalMinutes = filteredEvents.reduce(
      (sum, event) => sum + (event.usageSession.duration_seconds / 60),
      0
    );

    // Unique devices
    const deviceIds = new Set(filteredEvents.map(event => event.device.id));
    const uniqueDevices = deviceIds.size;

    // Top apps by usage
    const appUsage = new Map<string, number>();
    filteredEvents.forEach(event => {
      const app = event.usageSession.item_name;
      appUsage.set(app, (appUsage.get(app) || 0) + (event.usageSession.duration_seconds / 60));
    });
    const topApps = Array.from(appUsage.entries())
      .map(([app, minutes]) => ({ app, minutes }))
      .sort((a, b) => b.minutes - a.minutes)
      .slice(0, 5);

    // Daily usage aggregation
    const dailyUsageMap = new Map<string, number>();
    filteredEvents.forEach(event => {
      const date = new Date(event.usageSession.timestamp).toLocaleDateString();
      dailyUsageMap.set(date, (dailyUsageMap.get(date) || 0) + (event.usageSession.duration_seconds / 60));
    });
    const dailyUsage = Array.from(dailyUsageMap.entries())
      .map(([date, minutes]) => ({ date, minutes }))
      .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

    return { totalMinutes, uniqueDevices, topApps, dailyUsage };
  }, [usageEvents, timeRange, customDateRange]);

  // Calculate block statistics
  const blockStats = useMemo(() => {
    const now = new Date();
    let cutoffTime: Date;
    
    if (timeRange === 'custom' && customDateRange) {
      cutoffTime = new Date(customDateRange.startDate);
    } else if (timeRange !== 'custom') {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs(timeRange));
    } else {
      cutoffTime = new Date(now.getTime() - getTimeRangeMs('7d'));
    }
    
    const filteredBlocks = blockEvents.filter(
      event => {
        const eventDate = new Date(event.blockEvent.timestamp);
        if (timeRange === 'custom' && customDateRange) {
          const endDate = new Date(customDateRange.endDate);
          endDate.setHours(23, 59, 59, 999);
          return eventDate >= cutoffTime && eventDate <= endDate;
        }
        return eventDate >= cutoffTime;
      }
    );

    const totalBlocks = filteredBlocks.length;

    // Top blocked items
    const itemCounts = new Map<string, number>();
    filteredBlocks.forEach(event => {
      const item = event.blockEvent.blocked_item;
      itemCounts.set(item, (itemCounts.get(item) || 0) + 1);
    });
    const topBlockedItems = Array.from(itemCounts.entries())
      .map(([item, count]) => ({ item, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);

    // Blocks by reason
    const reasonCounts = new Map<string, number>();
    filteredBlocks.forEach(event => {
      const reason = event.blockEvent.reason;
      reasonCounts.set(reason, (reasonCounts.get(reason) || 0) + 1);
    });
    const blocksByReason = Array.from(reasonCounts.entries())
      .map(([reason, count]) => ({ reason, count }))
      .sort((a, b) => b.count - a.count);

    // Blocks by device
    const deviceCounts = new Map<string, number>();
    filteredBlocks.forEach(event => {
      const device = event.device.name;
      deviceCounts.set(device, (deviceCounts.get(device) || 0) + 1);
    });
    const blocksByDevice = Array.from(deviceCounts.entries())
      .map(([device, count]) => ({ device, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);

    return { totalBlocks, topBlockedItems, blocksByReason, blocksByDevice, uniqueDevices: deviceCounts.size };
  }, [blockEvents, timeRange, customDateRange]);

  // Filtered events for export
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

  const domainUsage = useMemo(() => {
    const domainMap = new Map<string, number>();
    filteredUsageEvents.forEach((event) => {
      const domain = normalizeDomain(event.usageSession.item_name);
      domainMap.set(
        domain,
        (domainMap.get(domain) || 0) + event.usageSession.duration_seconds / 60
      );
    });
    return Array.from(domainMap.entries())
      .map(([domain, minutes]) => ({ domain, minutes }))
      .sort((a, b) => b.minutes - a.minutes)
      .slice(0, 10);
  }, [filteredUsageEvents]);

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

  const lastBlockedByItem = useMemo(() => {
    const map = new Map<string, Date>();
    filteredBlockEvents.forEach((event) => {
      const item = event.blockEvent.blocked_item;
      const ts = new Date(event.blockEvent.timestamp);
      const prev = map.get(item);
      if (!prev || ts > prev) {
        map.set(item, ts);
      }
    });
    return map;
  }, [filteredBlockEvents]);

  const lastBlockedByReason = useMemo(() => {
    const map = new Map<string, Date>();
    filteredBlockEvents.forEach((event) => {
      const reason = event.blockEvent.reason;
      const ts = new Date(event.blockEvent.timestamp);
      const prev = map.get(reason);
      if (!prev || ts > prev) {
        map.set(reason, ts);
      }
    });
    return map;
  }, [filteredBlockEvents]);

  // Handle date range change
  const handleDateRangeChange = (range: DateRange) => {
    setCustomDateRange(range);
    setTimeRange('custom');
  };

  // Generate insights
  useEffect(() => {
    if (usageStats.topApps.length > 0) {
      const topApp = usageStats.topApps[0];
      const insight = `${topApp.app} is the most used app with ${topApp.minutes} minutes of usage.`;
      onInsightGenerated?.(insight);
    }
  }, [usageStats, onInsightGenerated]);

  const maxDomainMinutes = domainUsage.length > 0 ? domainUsage[0].minutes : 1;

  return (
    <div className="space-y-6">
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">Analytics & Insights</h2>
          <div className="flex gap-3">
            <select
              value={timeRange}
              onChange={(e) => {
                const value = e.target.value as typeof timeRange;
                setTimeRange(value);
                if (value === 'custom') {
                  setShowDatePicker(true);
                } else {
                  setShowDatePicker(false);
                  setCustomDateRange(null);
                }
              }}
              className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="24h">Last 24 Hours</option>
              <option value="7d">Last 7 Days</option>
              <option value="30d">Last 30 Days</option>
              <option value="custom">Custom Range</option>
            </select>
            
            {/* Export Dropdown */}
            <div className="relative group">
              <button className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500">
                Export Data ▾
              </button>
              <div className="absolute right-0 mt-2 w-48 bg-white rounded-md shadow-lg hidden group-hover:block z-10 border border-gray-200">
                <button
                  onClick={() => exportUsageEventsToCSV(filteredUsageEvents)}
                  className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                >
                  Usage Events (CSV)
                </button>
                <button
                  onClick={() => exportUsageEventsToPDF(filteredUsageEvents)}
                  className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                >
                  Usage Events (PDF)
                </button>
                <button
                  onClick={() => exportBlockEventsToCSV(filteredBlockEvents)}
                  className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                >
                  Block Events (CSV)
                </button>
                <button
                  onClick={() => exportBlockEventsToPDF(filteredBlockEvents)}
                  className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                >
                  Block Events (PDF)
                </button>
                <button
                  onClick={() => exportAnalyticsSummaryToPDF(usageStats, blockStats, timeRange)}
                  className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 border-t border-gray-200"
                >
                  Summary Report (PDF)
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Date Range Picker */}
        {showDatePicker && (
          <div className="mb-6">
            <DateRangePicker onDateRangeChange={handleDateRangeChange} />
          </div>
        )}

        {/* Usage Statistics */}
        <div className="mb-8">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Usage Overview</h3>
          <div className="mb-6" data-testid="chart-usage-overview">
            {domainUsage.length === 0 ? (
              <p className="text-gray-500">No usage data available</p>
            ) : (
              <div className="space-y-2">
                {domainUsage.map((entry, index) => (
                  <div
                    key={entry.domain}
                    className="flex items-center"
                    data-testid={`chart-bar-usage-overview-${index}`}
                  >
                    <span className="text-sm font-medium text-gray-700 w-8">{index + 1}.</span>
                    <div className="flex-1">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm font-medium text-gray-900">{entry.domain}</span>
                        <span className="text-sm text-gray-600">
                          {Math.round(entry.minutes)} min
                        </span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className="bg-blue-600 h-2 rounded-full"
                          style={{
                            width: `${(entry.minutes / maxDomainMinutes) * 100}%`,
                          }}
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Top Apps */}
          <div className="mb-6">
            <h4 className="text-md font-semibold text-gray-900 mb-3">Top Apps by Usage</h4>
            {usageStats.topApps.length === 0 ? (
              <p className="text-gray-500">No usage data available</p>
            ) : (
              <div className="space-y-2">
                {usageStats.topApps.map((app, index) => (
                  <div key={app.app} className="flex items-center">
                    <span className="text-sm font-medium text-gray-700 w-8">{index + 1}.</span>
                    <div className="flex-1">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm font-medium text-gray-900">{app.app}</span>
                        <span className="text-sm text-gray-600">{app.minutes} min</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className="bg-blue-600 h-2 rounded-full"
                          style={{
                            width: `${(app.minutes / usageStats.topApps[0].minutes) * 100}%`,
                          }}
                        ></div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Block Statistics */}
        <div className="mb-8">
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Block Activity</h3>
          <p className="text-sm text-gray-500 mb-4">
            Total: <span className="font-semibold text-red-600">{blockStats.totalBlocks}</span> blocks ·{' '}
            <span className="font-semibold text-orange-600">{blockStats.topBlockedItems.length}</span> unique items ·{' '}
            <span className="font-semibold text-indigo-600">{blockStats.uniqueDevices}</span> devices
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
            <div className="bg-red-50 rounded-lg p-4">
              <h4 className="text-sm font-medium text-red-600">Total Blocks</h4>
              <p className="text-3xl font-bold text-red-900">{blockStats.totalBlocks}</p>
            </div>
            <div className="bg-orange-50 rounded-lg p-4">
              <h4 className="text-sm font-medium text-orange-600">Unique Items Blocked</h4>
              <p className="text-3xl font-bold text-orange-900">
                {blockStats.topBlockedItems.length}
              </p>
            </div>
          </div>

          {/* Top Blocked Items */}
          <div className="mb-6">
            <h4 className="text-md font-semibold text-gray-900 mb-3">Most Blocked Items</h4>
            {blockStats.topBlockedItems.length === 0 ? (
              <p className="text-gray-500">No blocks recorded</p>
            ) : (
              <div className="space-y-2">
                {blockStats.topBlockedItems.map((item, index) => (
                  <div
                    key={item.item}
                    className="flex items-center justify-between p-2 bg-gray-50 rounded border border-gray-100"
                  >
                    <div className="flex flex-col">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-700">{index + 1}.</span>
                        <span className="text-sm font-medium text-gray-900">{item.item}</span>
                      </div>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {item.count} blocks
                        {lastBlockedByItem.get(item.item) && (
                          <>
                            {' '}
                            · last {formatRelativeTime(lastBlockedByItem.get(item.item)!)}
                          </>
                        )}
                      </p>
                    </div>
                    <span
                      className="text-xs font-semibold text-red-700 bg-red-50 px-2 py-1 rounded-full"
                      title={`${item.count} blocks in selected period`}
                    >
                      {item.count}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Blocks by Reason */}
          <div>
            <h4 className="text-md font-semibold text-gray-900 mb-3">Block Reasons</h4>
            {blockStats.blocksByReason.length === 0 ? (
              <p className="text-gray-500">No block reasons available</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {blockStats.blocksByReason.map((reason) => (
                  <div
                    key={reason.reason}
                    className="flex items-center justify-between p-3 border border-gray-200 rounded bg-gray-50"
                  >
                    <div className="flex flex-col">
                      <span className="text-sm text-gray-900">{reason.reason}</span>
                      <span className="text-xs text-gray-500">
                        {reason.count} blocks
                        {lastBlockedByReason.get(reason.reason) && (
                          <>
                            {' '}
                            · last {formatRelativeTime(lastBlockedByReason.get(reason.reason)!)}
                          </>
                        )}
                      </span>
                    </div>
                    <span className="text-xs font-semibold text-gray-800 bg-white px-2 py-1 rounded-full">
                      {reason.count}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Summary Insights */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <h3 className="text-md font-semibold text-blue-900 mb-2">📊 Summary Insights</h3>
          <ul className="space-y-1 text-sm text-blue-800">
            {usageStats.totalMinutes > 0 && (
              <li>• Total screen time: {Math.round(usageStats.totalMinutes / 60)} hours {usageStats.totalMinutes % 60} minutes</li>
            )}
            {blockStats.totalBlocks > 0 && (
              <li>• {blockStats.totalBlocks} items were blocked to maintain healthy usage</li>
            )}
            {usageStats.topApps.length > 0 && (
              <li>• Most used app: {usageStats.topApps[0].app} ({usageStats.topApps[0].minutes} minutes)</li>
            )}
            {blockStats.topBlockedItems.length > 0 && (
              <li>• Most blocked item: {blockStats.topBlockedItems[0].item} ({blockStats.topBlockedItems[0].count} times)</li>
            )}
          </ul>
        </div>
      </div>
    </div>
  );
}

function formatRelativeTime(date: Date): string {
  const now = Date.now();
  const diff = now - date.getTime();

  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function getTimeRangeMs(range: '24h' | '7d' | '30d'): number {
  switch (range) {
    case '24h':
      return 24 * 60 * 60 * 1000;
    case '7d':
      return 7 * 24 * 60 * 60 * 1000;
    case '30d':
      return 30 * 24 * 60 * 60 * 1000;
    default:
      return 24 * 60 * 60 * 1000;
  }
}

// Export memoized component to prevent unnecessary re-renders
export const Analytics = memo(AnalyticsComponent);
