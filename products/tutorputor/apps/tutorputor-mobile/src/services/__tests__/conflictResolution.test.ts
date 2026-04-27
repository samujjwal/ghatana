/**
 * @doc.type test
 * @doc.purpose Unit tests for mobile offline conflict resolution policy
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import {
  decideConflictResolution,
  getStrategyForMutation,
} from '../conflictResolution';

describe('conflictResolution policy', () => {
  it('uses SERVER_WINS as default for unknown mutation types', () => {
    expect(getStrategyForMutation('UNKNOWN_TYPE')).toBe('SERVER_WINS');
  });

  it('retries client payload for CLIENT_WINS mutations', () => {
    const decision = decideConflictResolution(
      {
        type: 'ADD_NOTE',
        payload: { updatedAt: '2026-04-27T10:00:00.000Z' },
      },
      { serverUpdatedAt: '2026-04-27T10:10:00.000Z' },
    );

    expect(decision).toBe('retry_client');
  });

  it('accepts server state for SERVER_WINS mutations', () => {
    const decision = decideConflictResolution(
      {
        type: 'SUBMIT_QUIZ',
        payload: { updatedAt: '2026-04-27T10:20:00.000Z' },
      },
      { serverUpdatedAt: '2026-04-27T10:10:00.000Z' },
    );

    expect(decision).toBe('accept_server');
  });

  it('uses LAST_WRITE_WINS based on timestamps', () => {
    const newerClient = decideConflictResolution(
      {
        type: 'UPDATE_PROGRESS',
        payload: { updatedAt: '2026-04-27T10:20:00.000Z' },
      },
      { serverUpdatedAt: '2026-04-27T10:10:00.000Z' },
    );

    const newerServer = decideConflictResolution(
      {
        type: 'UPDATE_PROGRESS',
        payload: { updatedAt: '2026-04-27T10:05:00.000Z' },
      },
      { serverUpdatedAt: '2026-04-27T10:10:00.000Z' },
    );

    expect(newerClient).toBe('retry_client');
    expect(newerServer).toBe('accept_server');
  });
});
