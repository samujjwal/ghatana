/**
 * Migration Runner Tests
 */

import { jest } from 'vitest';

vi.mock('../../utils/logger', () => ({
  logger: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  },
}));

import { applyBaseSchema, applyMigrations } from '../../db/migrate';
import { pool, stopConnectionMetricsInterval } from '../../db';

describe('Database migration utilities', () => {
  afterAll(() => {
    stopConnectionMetricsInterval();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads base schema from file', async () => {
    const querySpy = vi.spyOn(pool, 'query').mockResolvedValueOnce({ rows: [] } as any);

    await applyBaseSchema();

    expect(querySpy).toHaveBeenCalledTimes(1);
    const sql = querySpy.mock.calls[0][0];
    expect(sql).toContain('CREATE TABLE users');
  });

  it('executes sql migrations in order', async () => {
    const querySpy = vi.spyOn(pool, 'query').mockResolvedValue({ rows: [] } as any);

    await applyMigrations();

    const executedSql = querySpy.mock.calls.map(call => call[0]);
    expect(querySpy).toHaveBeenCalled();
    expect(executedSql[0]).toContain('audit_logs');
  });
});
