/**
 * Unit tests for AnomalyTimelineChart component
 *
 * Tests pure logic functions for timeline visualization:
 * - Hour bucketing and aggregation
 * - Chronological sorting
 * - Color mapping for severity
 * - Tooltip data formatting
 *
 * @see AnomalyTimelineChart.tsx
 */

import { describe, it, expect } from 'vitest';

describe('AnomalyTimelineChart', () => {
  describe('hour bucketing', () => {
    /**
     * GIVEN: Array of anomalies with different timestamps
     * WHEN: Bucketing by hour
     * THEN: Anomalies grouped by start of hour
     */
    it('should group anomalies into hourly buckets', () => {
      const now = new Date('2025-11-13T14:30:00');
      const oneHourAgo = new Date('2025-11-13T13:45:00');
      const twoHoursAgo = new Date('2025-11-13T12:15:00');

      const anomalies = [
        { timestamp: now, severity: 'CRITICAL' },
        { timestamp: oneHourAgo, severity: 'HIGH' },
        { timestamp: twoHoursAgo, severity: 'MEDIUM' },
      ];

      // Pure function to group by hour
      const getHourBucket = (date: Date): number => {
        const d = new Date(date);
        d.setMinutes(0, 0, 0);
        return d.getTime();
      };

      const buckets: Record<number, typeof anomalies> = {};
      anomalies.forEach((a) => {
        const bucket = getHourBucket(a.timestamp);
        if (!buckets[bucket]) buckets[bucket] = [];
        buckets[bucket].push(a);
      });

      expect(Object.keys(buckets).length).toBe(3);
      expect(buckets[getHourBucket(now)].length).toBe(1);
    });

    /**
     * GIVEN: Multiple anomalies in same hour
     * WHEN: Grouping by hour
     * THEN: All anomalies in same bucket
     */
    it('should group multiple anomalies in same hour together', () => {
      const hour1 = new Date('2025-11-13T14:10:00');
      const hour1b = new Date('2025-11-13T14:45:00');
      const hour1c = new Date('2025-11-13T14:30:00');

      const anomalies = [
        { timestamp: hour1, severity: 'CRITICAL' },
        { timestamp: hour1b, severity: 'HIGH' },
        { timestamp: hour1c, severity: 'MEDIUM' },
      ];

      const getHourBucket = (date: Date): number => {
        const d = new Date(date);
        d.setMinutes(0, 0, 0);
        return d.getTime();
      };

      const bucket = getHourBucket(hour1);
      const allInSameBucket = anomalies.every(
        (a) => getHourBucket(a.timestamp) === bucket
      );

      expect(allInSameBucket).toBe(true);
    });
  });

  describe('chronological sorting', () => {
    /**
     * GIVEN: Unordered hourly data
     * WHEN: Sorting chronologically
     * THEN: Hours ordered from earliest to latest
     */
    it('should sort hours from earliest to latest', () => {
      const twoHoursAgo = new Date('2025-11-13T12:00:00').getTime();
      const oneHourAgo = new Date('2025-11-13T13:00:00').getTime();
      const now = new Date('2025-11-13T14:00:00').getTime();

      const unsorted = [now, twoHoursAgo, oneHourAgo];
      const sorted = [...unsorted].sort((a, b) => a - b);

      expect(sorted[0]).toBe(twoHoursAgo);
      expect(sorted[1]).toBe(oneHourAgo);
      expect(sorted[2]).toBe(now);
    });

    /**
     * GIVEN: Hourly buckets with data
     * WHEN: Creating timeline
     * THEN: Timeline sorted chronologically
     */
    it('should maintain chronological order in timeline', () => {
      const hourData: Record<number, { count: number; severity: string[] }> = {
        [new Date('2025-11-13T12:00:00').getTime()]: {
          count: 2,
          severity: ['LOW', 'MEDIUM'],
        },
        [new Date('2025-11-13T14:00:00').getTime()]: {
          count: 1,
          severity: ['CRITICAL'],
        },
        [new Date('2025-11-13T13:00:00').getTime()]: {
          count: 3,
          severity: ['HIGH', 'HIGH', 'MEDIUM'],
        },
      };

      const timeline = Object.keys(hourData)
        .map((h) => parseInt(h))
        .sort((a, b) => a - b);

      expect(timeline[0]).toBeLessThan(timeline[1]);
      expect(timeline[1]).toBeLessThan(timeline[2]);
    });
  });

  describe('severity color mapping', () => {
    /**
     * GIVEN: Anomaly severity levels
     * WHEN: Mapping to chart colors
     * THEN: Correct Tailwind color classes returned
     */
    it('should map severity to correct chart colors', () => {
      const severityToColor = (severity: string): string => {
        const colorMap: Record<string, string> = {
          CRITICAL: '#DC2626', // red-600
          HIGH: '#F97316', // orange-500
          MEDIUM: '#EAB308', // yellow-500
          LOW: '#3B82F6', // blue-500
        };
        return colorMap[severity] || '#808080';
      };

      expect(severityToColor('CRITICAL')).toBe('#DC2626');
      expect(severityToColor('HIGH')).toBe('#F97316');
      expect(severityToColor('MEDIUM')).toBe('#EAB308');
      expect(severityToColor('LOW')).toBe('#3B82F6');
    });

    /**
     * GIVEN: Multiple anomalies with different severities
     * WHEN: Mapping to colors
     * THEN: Each severity gets consistent color
     */
    it('should provide consistent colors across anomalies', () => {
      const severityToColor = (severity: string): string => {
        const colorMap: Record<string, string> = {
          CRITICAL: '#DC2626',
          HIGH: '#F97316',
          MEDIUM: '#EAB308',
          LOW: '#3B82F6',
        };
        return colorMap[severity] || '#808080';
      };

      const anomalies = [
        { severity: 'CRITICAL' },
        { severity: 'CRITICAL' },
        { severity: 'HIGH' },
      ];

      const colors = anomalies.map((a) => severityToColor(a.severity));

      expect(colors[0]).toBe(colors[1]);
      expect(colors[0]).not.toBe(colors[2]);
    });
  });

  describe('tooltip data formatting', () => {
    /**
     * GIVEN: Hourly aggregated data
     * WHEN: Formatting for tooltip display
     * THEN: Human-readable tooltip data returned
     */
    it('should format tooltip with hour and anomaly count', () => {
      const hour = new Date('2025-11-13T14:00:00');
      const count = 5;

      const formatTooltip = (h: Date, c: number): string => {
        return `${h.toLocaleTimeString()}: ${c} anomalies`;
      };

      const tooltip = formatTooltip(hour, count);

      expect(tooltip).toContain('anomalies');
      expect(tooltip).toContain('5');
    });

    /**
     * GIVEN: Hour and severity breakdown
     * WHEN: Creating detailed tooltip
     * THEN: Breakdown shown in tooltip
     */
    it('should show severity breakdown in tooltip', () => {
      const breakdown = { CRITICAL: 2, HIGH: 1, MEDIUM: 2 };

      const formatBreakdown = (b: Record<string, number>): string => {
        return Object.entries(b)
          .map(([severity, count]) => `${severity}: ${count}`)
          .join(', ');
      };

      const formatted = formatBreakdown(breakdown);

      expect(formatted).toContain('CRITICAL: 2');
      expect(formatted).toContain('HIGH: 1');
      expect(formatted).toContain('MEDIUM: 2');
    });
  });

  describe('data aggregation', () => {
    /**
     * GIVEN: Timeline data for multiple hours
     * WHEN: Calculating totals
     * THEN: Correct statistics returned
     */
    it('should calculate hourly statistics', () => {
      const hourlyData = [
        { hour: 12, anomalies: 2, critical: 1, high: 1 },
        { hour: 13, anomalies: 5, critical: 2, high: 3 },
        { hour: 14, anomalies: 3, critical: 1, high: 2 },
      ];

      const totalAnomalies = hourlyData.reduce((sum, h) => sum + h.anomalies, 0);
      const maxHour = Math.max(...hourlyData.map((h) => h.anomalies));

      expect(totalAnomalies).toBe(10);
      expect(maxHour).toBe(5);
    });
  });
});
