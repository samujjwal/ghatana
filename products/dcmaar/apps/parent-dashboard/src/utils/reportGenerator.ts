/**
 * Report Generation Utility
 * Provides functions to generate analytics reports in various formats
 */

import { exportAnalyticsSummaryToPDF } from './pdfExport';
import type { UsageEvent, BlockEvent } from '../services/websocket.service';

export type ReportPeriod = 'daily' | 'weekly' | 'monthly' | 'custom';
export type ReportFormat = 'pdf' | 'csv' | 'html';

export interface ReportOptions {
  period: ReportPeriod;
  format: ReportFormat;
  startDate?: string;
  endDate?: string;
  includeCharts?: boolean;
  includeUsageStats?: boolean;
  includeBlockStats?: boolean;
  includePolicySummary?: boolean;
}

export interface ReportData {
  period: ReportPeriod;
  startDate: string;
  endDate: string;
  usageStats: {
    totalMinutes: number;
    uniqueDevices: number;
    topApps: Array<{ app: string; minutes: number }>;
  };
  blockStats: {
    totalBlocks: number;
    uniqueDevices: number;
    topBlockedItems: Array<{ item: string; count: number }>;
  };
  policySummary?: {
    totalPolicies: number;
    activePolicies: number;
    affectedDevices: number;
  };
}

/**
 * Calculate date range for a report period
 */
export function getReportDateRange(period: ReportPeriod, customStart?: string, customEnd?: string): { start: Date; end: Date } {
  const now = new Date();
  let start: Date;
  let end: Date = now;

  switch (period) {
    case 'daily':
      // Yesterday
      start = new Date(now);
      start.setDate(start.getDate() - 1);
      start.setHours(0, 0, 0, 0);
      end = new Date(start);
      end.setHours(23, 59, 59, 999);
      break;

    case 'weekly':
      // Last 7 days
      start = new Date(now);
      start.setDate(start.getDate() - 7);
      start.setHours(0, 0, 0, 0);
      end = new Date(now);
      end.setHours(23, 59, 59, 999);
      break;

    case 'monthly':
      // Last 30 days
      start = new Date(now);
      start.setDate(start.getDate() - 30);
      start.setHours(0, 0, 0, 0);
      end = new Date(now);
      end.setHours(23, 59, 59, 999);
      break;

    case 'custom':
      if (!customStart || !customEnd) {
        throw new Error('Custom period requires startDate and endDate');
      }
      start = new Date(customStart);
      end = new Date(customEnd);
      end.setHours(23, 59, 59, 999);
      break;

    default:
      throw new Error(`Unknown period: ${period}`);
  }

  return { start, end };
}

/**
 * Filter events by date range
 */
export function filterEventsByDateRange<T extends { usageSession?: { timestamp: string }; blockEvent?: { timestamp: string } }>(
  events: T[],
  startDate: Date,
  endDate: Date
): T[] {
  return events.filter(event => {
    const timestamp = event.usageSession?.timestamp || event.blockEvent?.timestamp;
    if (!timestamp) return false;
    
    const eventDate = new Date(timestamp);
    return eventDate >= startDate && eventDate <= endDate;
  });
}

/**
 * Aggregate usage statistics for report
 */
export function aggregateUsageStats(events: UsageEvent[]): ReportData['usageStats'] {
  const totalMinutes = events.reduce((sum, event) => {
    return sum + (event.usageSession.duration_seconds / 60);
  }, 0);

  const deviceSet = new Set(events.map(e => e.device.id));
  const uniqueDevices = deviceSet.size;

  // Group by app and sum minutes
  const appMinutes = new Map<string, number>();
  events.forEach(event => {
    const app = event.usageSession.item_name;
    const minutes = event.usageSession.duration_seconds / 60;
    appMinutes.set(app, (appMinutes.get(app) || 0) + minutes);
  });

  // Sort by minutes descending
  const topApps = Array.from(appMinutes.entries())
    .map(([app, minutes]) => ({ app, minutes }))
    .sort((a, b) => b.minutes - a.minutes)
    .slice(0, 10);

  return {
    totalMinutes,
    uniqueDevices,
    topApps,
  };
}

/**
 * Aggregate block statistics for report
 */
export function aggregateBlockStats(events: BlockEvent[]): ReportData['blockStats'] {
  const totalBlocks = events.length;

  const deviceSet = new Set(events.map(e => e.device.id));
  const uniqueDevices = deviceSet.size;

  // Group by blocked item and count
  const itemCounts = new Map<string, number>();
  events.forEach(event => {
    const item = event.blockEvent.blocked_item;
    itemCounts.set(item, (itemCounts.get(item) || 0) + 1);
  });

  // Sort by count descending
  const topBlockedItems = Array.from(itemCounts.entries())
    .map(([item, count]) => ({ item, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 10);

  return {
    totalBlocks,
    uniqueDevices,
    topBlockedItems,
  };
}

/**
 * Generate report data from events
 */
export function generateReportData(
  usageEvents: UsageEvent[],
  blockEvents: BlockEvent[],
  options: ReportOptions
): ReportData {
  const { start, end } = getReportDateRange(options.period, options.startDate, options.endDate);

  const filteredUsage = filterEventsByDateRange(usageEvents, start, end);
  const filteredBlocks = filterEventsByDateRange(blockEvents, start, end);

  const usageStats = aggregateUsageStats(filteredUsage as UsageEvent[]);
  const blockStats = aggregateBlockStats(filteredBlocks as BlockEvent[]);

  return {
    period: options.period,
    startDate: start.toISOString(),
    endDate: end.toISOString(),
    usageStats,
    blockStats,
  };
}

/**
 * Generate report in specified format
 */
export function generateReport(reportData: ReportData, format: ReportFormat): void {
  switch (format) {
    case 'pdf': {
      const timeRange = reportData.period === 'daily' ? '24h' :
                       reportData.period === 'weekly' ? '7d' :
                       reportData.period === 'monthly' ? '30d' : 'custom';
      
      exportAnalyticsSummaryToPDF(
        reportData.usageStats,
        reportData.blockStats,
        timeRange
      );
      break;
    }

    case 'csv': {
      // CSV export is handled by individual export functions
      throw new Error('CSV format should be handled by specific export functions');
    }

    case 'html': {
      // HTML report generation (future enhancement)
      throw new Error('HTML format not yet implemented');
    }

    default: {
      throw new Error(`Unknown format: ${format}`);
    }
  }
}

/**
 * Schedule report generation (placeholder for backend integration)
 */
export interface ReportSchedule {
  id: string;
  period: ReportPeriod;
  format: ReportFormat;
  enabled: boolean;
  recipients?: string[]; // Email addresses (future)
  nextRun?: Date;
}

export function createReportSchedule(options: Omit<ReportSchedule, 'id'>): ReportSchedule {
  const schedule: ReportSchedule = {
    id: `schedule-${Date.now()}`,
    ...options,
  };

  // Calculate next run time based on period
  const now = new Date();
  switch (schedule.period) {
    case 'daily':
      schedule.nextRun = new Date(now.getTime() + 24 * 60 * 60 * 1000);
      break;
    case 'weekly':
      schedule.nextRun = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
      break;
    case 'monthly':
      schedule.nextRun = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
      break;
    default:
      break;
  }

  return schedule;
}

/**
 * Get all report schedules (mock - backend integration needed)
 */
export function getReportSchedules(): ReportSchedule[] {
  // This would connect to backend in production
  const stored = localStorage.getItem('reportSchedules');
  return stored ? JSON.parse(stored) : [];
}

/**
 * Save report schedule (mock - backend integration needed)
 */
export function saveReportSchedule(schedule: ReportSchedule): void {
  const schedules = getReportSchedules();
  const index = schedules.findIndex(s => s.id === schedule.id);
  
  if (index >= 0) {
    schedules[index] = schedule;
  } else {
    schedules.push(schedule);
  }
  
  localStorage.setItem('reportSchedules', JSON.stringify(schedules));
}

/**
 * Delete report schedule
 */
export function deleteReportSchedule(scheduleId: string): void {
  const schedules = getReportSchedules();
  const filtered = schedules.filter(s => s.id !== scheduleId);
  localStorage.setItem('reportSchedules', JSON.stringify(filtered));
}
