/**
 * Reports Routes Tests
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';

vi.mock('../../middleware/auth.middleware', () => ({
  authenticate: (req: any, _res: any, next: () => void) => {
    req.userId = 'parent-123';
    next();
  },
  optionalAuthenticate: (req: any, _res: any, next: () => void) => {
    req.userId = 'parent-123';
    next();
  },
}));

vi.mock('../../services/reports.service', () => ({
  getDailySummary: vi.fn().mockResolvedValue([
    {
      date: '2024-01-01',
      total_screen_time: 3600,
      total_blocks: 2,
      children: [],
    },
  ]),
  getUsageReport: vi.fn().mockResolvedValue([
    {
      child_id: 'child-1',
      child_name: 'Alice',
      total_screen_time: 7200,
      session_count: 3,
      top_apps: [],
      by_category: {},
    },
  ]),
  getBlockReport: vi.fn().mockResolvedValue([
    {
      child_id: 'child-1',
      child_name: 'Alice',
      total_blocks: 4,
      top_blocked: [],
      by_category: {},
    },
  ]),
  exportToCSV: vi.fn().mockReturnValue('csv-content'),
  exportToPDF: vi.fn(),
}));

vi.mock('../../services/audit.service', () => ({
  logAuditEvent: vi.fn(),
  AuditEvents: {
    DATA_EXPORTED: 'DATA_EXPORTED',
  },
}));

vi.mock('../../utils/logger', () => ({
  logger: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  },
  logAudit: vi.fn(),
  logDatabase: vi.fn(),
  logError: vi.fn(),
}));

let app: FastifyInstance;

describe('Reports Routes', () => {
  const startDate = '2024-01-01';
  const endDate = '2024-01-07';

  const mockedSummary = [
    {
      date: '2024-01-01',
      total_screen_time: 3600,
      total_blocks: 2,
      children: [],
    },
  ];

  const mockedUsageReport = [
    {
      child_id: 'child-1',
      child_name: 'Alice',
      total_screen_time: 7200,
      session_count: 3,
      top_apps: [],
      by_category: {},
    },
  ];

  const mockedBlockReport = [
    {
      child_id: 'child-1',
      child_name: 'Alice',
      total_blocks: 4,
      top_blocked: [],
      by_category: {},
    },
  ];

  beforeAll(async () => {
    app = await createTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('GET /api/reports/summary', () => {
    it('returns summary data', async () => {
      const response = await request(app)
        .get('/api/reports/summary')
        .query({ days: 7 })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toEqual(mockedSummary);
      expect(response.body.days).toBe(7);
    });
  });

  describe('GET /api/reports/usage', () => {
    it('returns usage report for date range', async () => {
      const response = await request(app)
        .get('/api/reports/usage')
        .query({ start_date: startDate, end_date: endDate })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toEqual(mockedUsageReport);
      expect(response.body.date_range.start).toContain(startDate);
    });
  });

  describe('GET /api/reports/blocks', () => {
    it('returns block report for date range', async () => {
      const response = await request(app)
        .get('/api/reports/blocks')
        .query({ start_date: startDate, end_date: endDate })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toEqual(mockedBlockReport);
    });
  });

  describe('GET /api/reports/export', () => {
    it('returns csv attachment for usage report', async () => {
      const response = await request(app)
        .get('/api/reports/export')
        .query({
          type: 'usage',
          start_date: startDate,
          end_date: endDate,
        })
        .expect(200);

      expect(response.headers['content-type']).toContain('text/csv');
      expect(response.headers['content-disposition']).toContain('guardian-usage-report');
      expect(response.payload).toBe('csv-content');
    });
  });

  // Edge case tests
  describe('Edge cases', () => {
    describe('GET /api/reports/summary', () => {
      it('rejects days less than 1', async () => {
        // Note: With mocked service, validation happens before service call
        // The route defaults to 7 when days is 0 or invalid, so this test
        // verifies the behavior with negative values
        const response = await request(app)
          .get('/api/reports/summary')
          .query({ days: -5 })
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toContain('between 1 and 90');
      });

      it('rejects days greater than 90', async () => {
        const response = await request(app)
          .get('/api/reports/summary')
          .query({ days: 100 })
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toContain('between 1 and 90');
      });

      it('defaults to 7 days when not specified', async () => {
        const response = await request(app)
          .get('/api/reports/summary')
          .expect(200);

        expect(response.body.days).toBe(7);
      });
    });

    describe('GET /api/reports/usage', () => {
      it('rejects invalid date format', async () => {
        const response = await request(app)
          .get('/api/reports/usage')
          .query({ start_date: 'invalid', end_date: endDate })
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toContain('Invalid');
      });

      it('rejects missing start_date', async () => {
        const response = await request(app)
          .get('/api/reports/usage')
          .query({ end_date: endDate })
          .expect(400);

        expect(response.body.success).toBe(false);
      });

      it('rejects missing end_date', async () => {
        const response = await request(app)
          .get('/api/reports/usage')
          .query({ start_date: startDate })
          .expect(400);

        expect(response.body.success).toBe(false);
      });

      it('accepts optional child_id filter', async () => {
        const response = await request(app)
          .get('/api/reports/usage')
          .query({
            start_date: startDate,
            end_date: endDate,
            child_id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
          })
          .expect(200);

        expect(response.body.success).toBe(true);
      });

      it('rejects invalid child_id format', async () => {
        const response = await request(app)
          .get('/api/reports/usage')
          .query({
            start_date: startDate,
            end_date: endDate,
            child_id: 'not-a-uuid',
          })
          .expect(400);

        expect(response.body.success).toBe(false);
      });
    });

    describe('GET /api/reports/blocks', () => {
      it('rejects invalid date format', async () => {
        const response = await request(app)
          .get('/api/reports/blocks')
          .query({ start_date: '01-01-2024', end_date: endDate })
          .expect(400);

        expect(response.body.success).toBe(false);
      });
    });

    describe('GET /api/reports/export', () => {
      it('rejects invalid report type', async () => {
        const response = await request(app)
          .get('/api/reports/export')
          .query({
            type: 'invalid',
            start_date: startDate,
            end_date: endDate,
          })
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toContain('Invalid report type');
      });

      it('rejects missing type', async () => {
        const response = await request(app)
          .get('/api/reports/export')
          .query({
            start_date: startDate,
            end_date: endDate,
          })
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toContain('Invalid report type');
      });

      it('rejects missing dates', async () => {
        const response = await request(app)
          .get('/api/reports/export')
          .query({ type: 'usage' })
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toContain('required');
      });

      it('exports blocks report type', async () => {
        const response = await request(app)
          .get('/api/reports/export')
          .query({
            type: 'blocks',
            start_date: startDate,
            end_date: endDate,
          })
          .expect(200);

        expect(response.headers['content-type']).toContain('text/csv');
        expect(response.headers['content-disposition']).toContain('guardian-blocks-report');
      });
    });
  });
});

