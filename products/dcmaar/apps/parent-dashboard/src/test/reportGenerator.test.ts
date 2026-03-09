import { describe, it, expect } from 'vitest';
import {
  getReportDateRange,
  filterEventsByDateRange,
  aggregateUsageStats,
  aggregateBlockStats,
  generateReportData,
  createReportSchedule,
} from '../utils/reportGenerator';
import type { UsageEvent, BlockEvent } from '../services/websocket.service';

describe('Report Generator Utilities', () => {
  describe('getReportDateRange', () => {
    it('should calculate daily range (yesterday)', () => {
      const { start, end } = getReportDateRange('daily');
      
      const expectedStart = new Date();
      expectedStart.setDate(expectedStart.getDate() - 1);
      expectedStart.setHours(0, 0, 0, 0);
      
      const expectedEnd = new Date(expectedStart);
      expectedEnd.setHours(23, 59, 59, 999);
      
      expect(start.toDateString()).toBe(expectedStart.toDateString());
      expect(end.toDateString()).toBe(expectedEnd.toDateString());
    });

    it('should calculate weekly range (last 7 days)', () => {
      const { start, end } = getReportDateRange('weekly');
      
      const now = new Date();
      const expectedStart = new Date(now);
      expectedStart.setDate(expectedStart.getDate() - 7);
      expectedStart.setHours(0, 0, 0, 0);
      
      expect(start.toDateString()).toBe(expectedStart.toDateString());
      expect(end.getDate()).toBe(now.getDate());
    });

    it('should calculate monthly range (last 30 days)', () => {
      const { start, end } = getReportDateRange('monthly');
      
      const now = new Date();
      const expectedStart = new Date(now);
      expectedStart.setDate(expectedStart.getDate() - 30);
      
      expect(start.toDateString()).toBe(expectedStart.toDateString());
      expect(end.getDate()).toBe(now.getDate());
    });

    it('should handle custom range', () => {
      const { start, end } = getReportDateRange('custom', '2024-01-01', '2024-01-15');
      
      expect(start.toISOString()).toContain('2024-01-01');
      expect(end.toISOString()).toContain('2024-01-15');
    });

    it('should throw error for custom without dates', () => {
      expect(() => getReportDateRange('custom')).toThrow('Custom period requires startDate and endDate');
    });
  });

  describe('filterEventsByDateRange', () => {
    it('should filter usage events by date range', () => {
      const events: UsageEvent[] = [
        {
          usageSession: {
            id: '1',
            device_id: 'device-1',
            item_name: 'App 1',
            session_type: 'application',
            duration_seconds: 120,
            timestamp: '2024-01-10T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          usageSession: {
            id: '2',
            device_id: 'device-1',
            item_name: 'App 2',
            session_type: 'application',
            duration_seconds: 180,
            timestamp: '2024-01-20T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
      ];

      const start = new Date('2024-01-01');
      const end = new Date('2024-01-15');
      
      const filtered = filterEventsByDateRange(events, start, end);
      
      expect(filtered).toHaveLength(1);
      expect(filtered[0].usageSession.id).toBe('1');
    });

    it('should filter block events by date range', () => {
      const events: BlockEvent[] = [
        {
          blockEvent: {
            id: '1',
            device_id: 'device-1',
            blocked_item: 'App 1',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-10T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          blockEvent: {
            id: '2',
            device_id: 'device-1',
            blocked_item: 'App 2',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-20T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
      ];

      const start = new Date('2024-01-01');
      const end = new Date('2024-01-15');
      
      const filtered = filterEventsByDateRange(events, start, end);
      
      expect(filtered).toHaveLength(1);
      expect(filtered[0].blockEvent.id).toBe('1');
    });
  });

  describe('aggregateUsageStats', () => {
    it('should calculate total minutes and unique devices', () => {
      const events = [
        {
          usageSession: {
            id: '1',
            device_id: 'device-1',
            item_name: 'App 1',
            session_type: 'application',
            duration_seconds: 120,
            timestamp: '2024-01-10T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          usageSession: {
            id: '2',
            device_id: 'device-2',
            item_name: 'App 1',
            session_type: 'application',
            duration_seconds: 180,
            timestamp: '2024-01-10T11:00:00Z',
          },
          device: { id: 'device-2', name: 'Device 2', type: 'mobile' },
        },
      ];

      const stats = aggregateUsageStats(events);

      expect(stats.totalMinutes).toBe(5); // (120 + 180) / 60
      expect(stats.uniqueDevices).toBe(2);
    });

    it('should aggregate top apps', () => {
      const events = [
        {
          usageSession: {
            id: '1',
            device_id: 'device-1',
            item_name: 'App 1',
            session_type: 'application',
            duration_seconds: 300,
            timestamp: '2024-01-10T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          usageSession: {
            id: '2',
            device_id: 'device-1',
            item_name: 'App 1',
            session_type: 'application',
            duration_seconds: 300,
            timestamp: '2024-01-10T11:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          usageSession: {
            id: '3',
            device_id: 'device-1',
            item_name: 'App 2',
            session_type: 'application',
            duration_seconds: 120,
            timestamp: '2024-01-10T12:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
      ];

      const stats = aggregateUsageStats(events);

      expect(stats.topApps).toHaveLength(2);
      expect(stats.topApps[0].app).toBe('App 1');
      expect(stats.topApps[0].minutes).toBe(10); // (300 + 300) / 60
      expect(stats.topApps[1].app).toBe('App 2');
      expect(stats.topApps[1].minutes).toBe(2); // 120 / 60
    });
  });

  describe('aggregateBlockStats', () => {
    it('should calculate total blocks and unique devices', () => {
      const events = [
        {
          blockEvent: {
            id: '1',
            device_id: 'device-1',
            blocked_item: 'App 1',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-10T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          blockEvent: {
            id: '2',
            device_id: 'device-2',
            blocked_item: 'App 2',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-10T11:00:00Z',
          },
          device: { id: 'device-2', name: 'Device 2', type: 'mobile' },
        },
      ];

      const stats = aggregateBlockStats(events);

      expect(stats.totalBlocks).toBe(2);
      expect(stats.uniqueDevices).toBe(2);
    });

    it('should aggregate top blocked items', () => {
      const events = [
        {
          blockEvent: {
            id: '1',
            device_id: 'device-1',
            blocked_item: 'App 1',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-10T10:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          blockEvent: {
            id: '2',
            device_id: 'device-1',
            blocked_item: 'App 1',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-10T11:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
        {
          blockEvent: {
            id: '3',
            device_id: 'device-1',
            blocked_item: 'App 2',
            event_type: 'application',
            reason: 'Policy',
            timestamp: '2024-01-10T12:00:00Z',
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
      ];

      const stats = aggregateBlockStats(events);

      expect(stats.topBlockedItems).toHaveLength(2);
      expect(stats.topBlockedItems[0].item).toBe('App 1');
      expect(stats.topBlockedItems[0].count).toBe(2);
      expect(stats.topBlockedItems[1].item).toBe('App 2');
      expect(stats.topBlockedItems[1].count).toBe(1);
    });
  });

  describe('generateReportData', () => {
    it('should generate complete report data', () => {
      const usageEvents = [
        {
          usageSession: {
            id: '1',
            device_id: 'device-1',
            item_name: 'App 1',
            session_type: 'application',
            duration_seconds: 300,
            timestamp: new Date().toISOString(),
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
      ];

      const blockEvents = [
        {
          blockEvent: {
            id: '1',
            device_id: 'device-1',
            blocked_item: 'App 2',
            event_type: 'application',
            reason: 'Policy',
            timestamp: new Date().toISOString(),
          },
          device: { id: 'device-1', name: 'Device 1', type: 'desktop' },
        },
      ];

      const reportData = generateReportData(usageEvents, blockEvents, {
        period: 'weekly',
        format: 'pdf',
      });

      expect(reportData.period).toBe('weekly');
      expect(reportData.startDate).toBeDefined();
      expect(reportData.endDate).toBeDefined();
      expect(reportData.usageStats).toBeDefined();
      expect(reportData.blockStats).toBeDefined();
    });
  });

  describe('createReportSchedule', () => {
    it('should create daily schedule with next run time', () => {
      const schedule = createReportSchedule({
        period: 'daily',
        format: 'pdf',
        enabled: true,
      });

      expect(schedule.id).toBeDefined();
      expect(schedule.period).toBe('daily');
      expect(schedule.format).toBe('pdf');
      expect(schedule.enabled).toBe(true);
      expect(schedule.nextRun).toBeDefined();
    });

    it('should create weekly schedule', () => {
      const schedule = createReportSchedule({
        period: 'weekly',
        format: 'csv',
        enabled: false,
      });

      expect(schedule.period).toBe('weekly');
      expect(schedule.format).toBe('csv');
      expect(schedule.enabled).toBe(false);
    });
  });
});
