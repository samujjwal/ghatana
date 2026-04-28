/**
 * @doc.type test
 * @doc.purpose Unit tests for mobile offline conflict resolution policy (F-020)
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import {
  decideConflictResolution,
  getStrategyForMutation,
  type ConflictEvent,
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

  // F-020: warn-on-conflict observability
  describe('onConflict callback (F-020)', () => {
    it('invokes onConflict with a structured ConflictEvent on every conflict', () => {
      const events: ConflictEvent[] = [];
      const onConflict = (e: ConflictEvent) => events.push(e);

      decideConflictResolution(
        {
          type: 'SUBMIT_QUIZ',
          payload: { updatedAt: '2026-04-27T10:20:00.000Z' },
        },
        { serverUpdatedAt: '2026-04-27T10:10:00.000Z', reason: 'concurrent edit' },
        onConflict,
      );

      expect(events).toHaveLength(1);
      expect(events[0]).toMatchObject<Partial<ConflictEvent>>({
        mutationType: 'SUBMIT_QUIZ',
        strategy: 'SERVER_WINS',
        decision: 'accept_server',
        serverUpdatedAt: '2026-04-27T10:10:00.000Z',
        reason: 'concurrent edit',
      });
    });

    it('emits retry_client decision in ConflictEvent for CLIENT_WINS strategy', () => {
      const events: ConflictEvent[] = [];

      decideConflictResolution(
        { type: 'ADD_BOOKMARK', payload: {} },
        {},
        (e) => events.push(e),
      );

      expect(events[0]).toMatchObject<Partial<ConflictEvent>>({
        mutationType: 'ADD_BOOKMARK',
        strategy: 'CLIENT_WINS',
        decision: 'retry_client',
      });
    });

    it('does not throw when onConflict is omitted', () => {
      expect(() =>
        decideConflictResolution(
          { type: 'COMPLETE_LESSON', payload: { updatedAt: '2026-04-27T12:00:00.000Z' } },
          { serverUpdatedAt: '2026-04-27T11:00:00.000Z' },
        ),
      ).not.toThrow();
    });
  });

  // F-020: extended per-entity table coverage
  describe('per-entity strategy table (F-020)', () => {
    it.each([
      ['SUBMIT_ASSESSMENT', 'SERVER_WINS'],
      ['ABANDON_ASSESSMENT', 'SERVER_WINS'],
      ['REMOVE_BOOKMARK', 'SERVER_WINS'],
      ['EDIT_NOTE', 'LAST_WRITE_WINS'],
      ['DELETE_NOTE', 'SERVER_WINS'],
      ['UPDATE_NOTIFICATION_PREF', 'LAST_WRITE_WINS'],
      ['RATE_MODULE', 'SERVER_WINS'],
    ] as const)(
      'strategy for %s is %s',
      (mutationType, expected) => {
        expect(getStrategyForMutation(mutationType)).toBe(expected);
      },
    );
  });
});

