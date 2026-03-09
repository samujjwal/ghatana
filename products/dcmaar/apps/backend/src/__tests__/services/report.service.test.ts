/**
 * Reports Service Tests
 */

import { jest } from 'vitest';

vi.mock('../../db', () => ({
  query: vi.fn(),
}));

import {
  getUsageReport,
  getBlockReport,
  getDailySummary,
  exportToCSV,
  exportToPDF,
} from '../../services/reports.service';
import { query } from '../../db';

const mockedQuery = vi.mocked(query);

function createDate(date: string) {
  return new Date(date);
}

describe('ReportsService', () => {
  beforeEach(() => {
    mockedQuery.mockReset();
  });

  describe('getUsageReport', () => {
    it('returns aggregated usage report with top apps and categories', async () => {
      mockedQuery
        .mockResolvedValueOnce([
          {
            child_id: 'child-1',
            child_name: 'Alice',
            total_screen_time: '7200',
            session_count: '3',
          },
          {
            child_id: 'child-2',
            child_name: 'Bob',
            total_screen_time: '3600',
            session_count: '2',
          },
        ])
        .mockResolvedValueOnce([
          { item_name: 'YouTube', duration: '3600' },
          { item_name: 'Minecraft', duration: '1800' },
        ])
        .mockResolvedValueOnce([
          { category: 'entertainment', duration: '3600' },
          { category: 'games', duration: '1800' },
        ])
        .mockResolvedValueOnce([{ item_name: 'Chrome', duration: '2400' }])
        .mockResolvedValueOnce([{ category: 'education', duration: '2400' }]);

      const result = await getUsageReport(
        'parent-123',
        createDate('2024-01-01'),
        createDate('2024-01-07')
      );

      expect(result).toHaveLength(2);
      expect(result[0]).toMatchObject({
        child_id: 'child-1',
        child_name: 'Alice',
        total_screen_time: 7200,
        session_count: 3,
      });
      expect(result[0].top_apps[0]).toEqual({ app_name: 'YouTube', duration: 3600 });
      expect(result[0].by_category.entertainment).toBe(3600);
      expect(result[1].top_apps[0].app_name).toBe('Chrome');

      // Ensure query called with expected params
      const firstCallParams = mockedQuery.mock.calls[0][1];
      expect(firstCallParams?.[0]).toBe('parent-123');
      expect(firstCallParams?.[1]).toBeInstanceOf(Date);
      expect(firstCallParams?.[2]).toBeInstanceOf(Date);
    });

    it('applies child filter when provided', async () => {
      mockedQuery
        .mockResolvedValueOnce([
          {
            child_id: 'child-1',
            child_name: 'Alice',
            total_screen_time: '1800',
            session_count: '1',
          },
        ])
        .mockResolvedValueOnce([{ item_name: 'YouTube', duration: '1800' }])
        .mockResolvedValueOnce([{ category: 'entertainment', duration: '1800' }]);

      await getUsageReport(
        'parent-123',
        createDate('2024-01-01'),
        createDate('2024-01-02'),
        'child-1'
      );

      const params = mockedQuery.mock.calls[0][1];
      expect(params).toHaveLength(4);
      expect(params?.[3]).toBe('child-1');
    });
  });

  describe('getBlockReport', () => {
    it('returns aggregated block report with top blocked items', async () => {
      mockedQuery
        .mockResolvedValueOnce([
          {
            child_id: 'child-1',
            child_name: 'Alice',
            total_blocks: '4',
          },
        ])
        .mockResolvedValueOnce([
          { blocked_item: 'facebook.com', category: 'social', count: '3' },
          { blocked_item: 'games.com', category: 'games', count: '1' },
        ])
        .mockResolvedValueOnce([
          { category: 'social', count: '3' },
          { category: 'games', count: '1' },
        ]);

      const result = await getBlockReport(
        'parent-123',
        createDate('2024-01-01'),
        createDate('2024-01-07')
      );

      expect(result).toHaveLength(1);
      expect(result[0].total_blocks).toBe(4);
      expect(result[0].top_blocked[0]).toEqual({
        target: 'facebook.com',
        count: 3,
        category: 'social',
      });
      expect(result[0].by_category.social).toBe(3);
    });
  });

  describe('getDailySummary', () => {
    it('builds chronological summaries per day', async () => {
      mockedQuery
        .mockResolvedValueOnce([
          { child_id: 'child-1', child_name: 'Alice', screen_time: '1200', blocks: '2' },
        ])
        .mockResolvedValueOnce([
          { child_id: 'child-1', child_name: 'Alice', screen_time: '600', blocks: '0' },
        ]);

      const result = await getDailySummary('parent-123', 2);

      expect(result).toHaveLength(2);
      // Should be chronological (oldest first)
      expect(result[0].date <= result[1].date).toBe(true);
      expect(result[1].total_screen_time).toBe(1200);
      expect(result[0].children[0]).toMatchObject({
        child_id: 'child-1',
        screen_time: 600,
        blocks: 0,
      });
    });
  });

  describe('export helpers', () => {
    it('exports usage data to CSV', () => {
      const csv = exportToCSV(
        [
          {
            child_id: 'child-1',
            child_name: 'Alice',
            total_screen_time: 7200,
            session_count: 3,
            top_apps: [
              { app_name: 'YouTube', duration: 3600 },
              { app_name: 'Minecraft', duration: 1800 },
            ],
            by_category: { entertainment: 3600, games: 1800 },
          },
        ],
        'usage'
      );

      expect(csv.split('\n')[0]).toContain('Child Name');
      expect(csv).toContain('Alice');
      expect(csv).toContain('YouTube; Minecraft');
    });

    it('exports block data to CSV', () => {
      const csv = exportToCSV(
        [
          {
            child_id: 'child-1',
            child_name: 'Alice',
            total_blocks: 4,
            top_blocked: [
              { target: 'facebook.com', count: 3, category: 'social' },
              { target: 'games.com', count: 1, category: 'games' },
            ],
            by_category: { social: 3, games: 1 },
          },
        ],
        'blocks'
      );

      expect(csv.split('\n')[0]).toContain('Total Blocks');
      expect(csv).toContain('facebook.com; games.com');
    });

    it('exports data to PDF buffer', () => {
      const buffer = exportToPDF(
        [
          {
            child_id: 'child-1',
            child_name: 'Alice',
            total_screen_time: 7200,
            session_count: 3,
            top_apps: [],
            by_category: {},
          },
        ],
        'usage'
      );

      const payload = JSON.parse(buffer.toString('utf-8'));
      expect(payload.type).toBe('usage');
      expect(payload.recordCount).toBe(1);
      expect(payload.generatedAt).toBeDefined();
    });
  });
});

