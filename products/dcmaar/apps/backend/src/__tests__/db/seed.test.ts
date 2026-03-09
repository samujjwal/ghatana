/**
 * Seed Script Tests
 */

import { describe, it, expect, afterEach, vi } from 'vitest';

vi.mock('../../utils/logger', () => ({
  logger: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  },
}));

import { seedDatabase } from '../../db/seed';
import * as db from '../../db';

describe('Database seeding', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('inserts baseline parent, child, device, and policy', async () => {
    const queryMock = vi
      .fn()
      .mockResolvedValueOnce({ rows: [{ id: 'parent-1' }] }) // user
      .mockResolvedValueOnce({ rows: [{ id: 'child-1' }] }) // child
      .mockResolvedValueOnce({ rows: [{ id: 'device-1' }] }) // device
      .mockResolvedValue({ rows: [] }); // remaining inserts

    const transactionSpy = vi
      .spyOn(db, 'transaction')
      .mockImplementation(async callback => {
        await callback({ query: queryMock } as any);
      });

    await seedDatabase({ parentEmail: 'seed@example.com', childName: 'Seed Child' });

    expect(transactionSpy).toHaveBeenCalled();
    expect(queryMock).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO users'),
      expect.arrayContaining(['seed@example.com'])
    );
    expect(queryMock).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO children'),
      expect.arrayContaining(['Seed Child'])
    );
    expect(queryMock).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO devices'),
      expect.arrayContaining(['parent-1', 'child-1'])
    );
    expect(queryMock).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO policies'),
      expect.arrayContaining(['parent-1', 'child-1', 'device-1'])
    );
  });
});

