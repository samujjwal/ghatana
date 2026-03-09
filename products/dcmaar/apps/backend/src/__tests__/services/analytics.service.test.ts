/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * Analytics Service Tests
 */

import {
  calculateTopApps,
  calculateTopCategories,
  identifyUsagePatterns,
  detectUsageTrends,
  detectAnomalies,
  generateRecommendations,
  analyzeTimeSeries,
  analyzeCorrelation,
  type UsageRecord,
  type DailyUsagePoint,
  type DailyBlockPoint,
} from '../../services/analytics.service';

describe('AnalyticsService', () => {
  const buildUsageRecord = (overrides: Partial<UsageRecord> = {}): UsageRecord => ({
    childId: 'child-1',
    deviceId: 'device-1',
    sessionType: 'app',
    itemName: overrides.itemName ?? 'YouTube',
    category: overrides.category ?? 'entertainment',
    durationSeconds: overrides.durationSeconds ?? 1800,
    startedAt: overrides.startedAt ?? new Date('2024-01-01T18:00:00Z'),
    ...overrides,
  });

  describe('calculateTopApps', () => {
    it('aggregates duration per app and sorts descending', () => {
      const records = [
        buildUsageRecord({ itemName: 'YouTube', durationSeconds: 1800 }),
        buildUsageRecord({ itemName: 'Minecraft', durationSeconds: 1200 }),
        buildUsageRecord({ itemName: 'YouTube', durationSeconds: 600 }),
      ];

      const result = calculateTopApps(records, 2);

      expect(result).toEqual([
        { name: 'YouTube', duration: 2400 },
        { name: 'Minecraft', duration: 1200 },
      ]);
    });
  });

  describe('calculateTopCategories', () => {
    it('groups records by category including uncategorized', () => {
      const records = [
        buildUsageRecord({ category: 'social', durationSeconds: 900 }),
        buildUsageRecord({ category: 'social', durationSeconds: 600 }),
        buildUsageRecord({ category: null, durationSeconds: 300 }),
      ];

      const result = calculateTopCategories(records);
      expect(result[0]).toMatchObject({ category: 'social', duration: 1500 });
      expect(result[1]).toMatchObject({ category: 'uncategorized', duration: 300 });
    });
  });

  describe('identifyUsagePatterns', () => {
    it('identifies peak hours and weekend share', () => {
      const records = [
        buildUsageRecord({ startedAt: new Date('2024-01-01T22:00:00Z'), durationSeconds: 1200 }),
        buildUsageRecord({ startedAt: new Date('2024-01-02T22:30:00Z'), durationSeconds: 1800 }),
        buildUsageRecord({ startedAt: new Date('2024-01-06T10:00:00Z'), durationSeconds: 900 }), // Saturday
      ];

      const result = identifyUsagePatterns(records);

      expect(result.peakHours[0].hour).toBe(22);
      expect(result.weekendShare).toBeGreaterThan(0);
      expect(result.lateNightUsage).toBeGreaterThan(0);
    });
  });

  describe('detectUsageTrends', () => {
    it('detects upward trend with percentage change', () => {
      const points: DailyUsagePoint[] = [
        { date: '2024-01-01', totalDuration: 1200 },
        { date: '2024-01-02', totalDuration: 1500 },
        { date: '2024-01-03', totalDuration: 2400 },
        { date: '2024-01-04', totalDuration: 3000 },
      ];

      const result = detectUsageTrends(points);

      expect(result.direction).toBe('upward');
      expect(result.percentageChange).toBeGreaterThan(0);
    });
  });

  describe('detectAnomalies', () => {
    it('flags points whose z-score exceeds threshold', () => {
      const points: DailyUsagePoint[] = [
        { date: '2024-01-01', totalDuration: 100 },
        { date: '2024-01-02', totalDuration: 110 },
        { date: '2024-01-03', totalDuration: 105 },
        { date: '2024-01-04', totalDuration: 95 },
        { date: '2024-01-05', totalDuration: 102 },
        { date: '2024-01-06', totalDuration: 108 },
        { date: '2024-01-07', totalDuration: 2000 }, // clear anomaly with more baseline data
      ];

      const anomalies = detectAnomalies(points, 2);

      expect(anomalies.length).toBeGreaterThanOrEqual(1);
      expect(anomalies.find(a => a.date === '2024-01-07')).toBeDefined();
    });
  });

  describe('generateRecommendations', () => {
    it('generates screen time recommendation for long sessions', () => {
      const recommendations = generateRecommendations([
        {
          child_id: 'child-1',
          child_name: 'Alice',
          total_screen_time: 18000,
          session_count: 2,
          top_apps: [{ app_name: 'TikTok', duration: 4000 }],
          by_category: {},
          alerts: 5,
        },
      ]);

      expect(recommendations).toHaveLength(2);
      expect(recommendations[0].childId).toBe('child-1');
      expect(recommendations[0].headline).toContain('Long session');
      expect(recommendations[1].category).toBe('engagement');
    });

    it('recommends checking connectivity when no activity recorded', () => {
      const recommendations = generateRecommendations([
        {
          child_id: 'child-2',
          child_name: 'Bob',
          total_screen_time: 0,
          session_count: 0,
          top_apps: [],
          by_category: {},
          alerts: 0,
        },
      ]);

      expect(recommendations[0].headline).toContain('No activity');
    });
  });

  describe('analyzeTimeSeries', () => {
    it('returns moving average and volatility', () => {
      const points: DailyUsagePoint[] = [
        { date: '2024-01-01', totalDuration: 1200 },
        { date: '2024-01-02', totalDuration: 1800 },
        { date: '2024-01-03', totalDuration: 2400 },
        { date: '2024-01-04', totalDuration: 3600 },
      ];

      const analysis = analyzeTimeSeries(points, 2);

      expect(analysis.movingAverage).toHaveLength(4);
      expect(analysis.movingAverage[3]).toBeGreaterThan(analysis.movingAverage[0]);
      expect(analysis.volatility).toBeGreaterThan(0);
    });

    it('throws when window size invalid', () => {
      expect(() => analyzeTimeSeries([], 0)).toThrow('windowSize must be greater than zero');
    });
  });

  describe('analyzeCorrelation', () => {
    it('computes correlation between usage and blocks', () => {
      const usage: DailyUsagePoint[] = [
        { date: '2024-01-01', totalDuration: 1200 },
        { date: '2024-01-02', totalDuration: 2400 },
        { date: '2024-01-03', totalDuration: 3600 },
      ];
      const blocks: DailyBlockPoint[] = [
        { date: '2024-01-01', totalBlocks: 1 },
        { date: '2024-01-02', totalBlocks: 2 },
        { date: '2024-01-03', totalBlocks: 3 },
      ];

      const result = analyzeCorrelation(usage, blocks);

      expect(result.coefficient).toBeCloseTo(1);
      expect(result.strength).toBe('strong');
    });

    it('returns neutral correlation when insufficient data', () => {
      const result = analyzeCorrelation([], []);
      expect(result.coefficient).toBe(0);
      expect(result.strength).toBe('none');
    });
  });
});

