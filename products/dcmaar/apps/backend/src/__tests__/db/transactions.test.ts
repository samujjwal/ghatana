/**
 * Database Transaction Tests
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { transaction, pool, stopConnectionMetricsInterval } from '../../db';

describe('DB Transactions', () => {
  afterAll(() => {
    stopConnectionMetricsInterval();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('commits successful transaction', async () => {
    const mockClient = {
      query: vi.fn().mockResolvedValue({ rows: [] }),
      release: vi.fn(),
    };

    const connectSpy = vi
      .spyOn(pool, 'connect')
      .mockResolvedValue(mockClient as unknown as any);

    const result = await transaction(async client => {
      await client.query('SELECT 1');
      return 'success';
    });

    expect(result).toBe('success');
    expect(connectSpy).toHaveBeenCalled();
    expect(mockClient.query).toHaveBeenNthCalledWith(1, 'BEGIN');
    expect(mockClient.query).toHaveBeenNthCalledWith(2, 'SELECT 1');
    expect(mockClient.query).toHaveBeenNthCalledWith(3, 'COMMIT');
    expect(mockClient.release).toHaveBeenCalled();
  });

  it('rolls back on error and rethrows', async () => {
    const mockClient = {
      query: vi.fn().mockResolvedValue({ rows: [] }),
      release: vi.fn(),
    };

    vi.spyOn(pool, 'connect').mockResolvedValue(mockClient as unknown as any);

    await expect(
      transaction(async () => {
        throw new Error('Something failed');
      })
    ).rejects.toThrow('Something failed');

    expect(mockClient.query).toHaveBeenNthCalledWith(1, 'BEGIN');
    expect(mockClient.query).toHaveBeenNthCalledWith(2, 'ROLLBACK');
    expect(mockClient.query).not.toHaveBeenCalledWith('COMMIT');
  });

  it('supports concurrent transactions using separate clients', async () => {
    const firstClient = {
      query: vi.fn().mockResolvedValue({ rows: [] }),
      release: vi.fn(),
    };
    const secondClient = {
      query: vi.fn().mockResolvedValue({ rows: [] }),
      release: vi.fn(),
    };

    const connectSpy = vi
      .spyOn(pool, 'connect')
      .mockResolvedValueOnce(firstClient as unknown as any)
      .mockResolvedValueOnce(secondClient as unknown as any);

    await Promise.all([
      transaction(async client => {
        await client.query('SELECT 1');
      }),
      transaction(async client => {
        await client.query('SELECT 2');
      }),
    ]);

    expect(connectSpy).toHaveBeenCalledTimes(2);
    expect(firstClient.query).toHaveBeenCalledWith('SELECT 1');
    expect(secondClient.query).toHaveBeenCalledWith('SELECT 2');
  });
});

