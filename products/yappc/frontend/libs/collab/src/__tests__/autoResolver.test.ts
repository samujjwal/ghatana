import { describe, expect, it } from 'vitest';

import { autoResolveOperations } from '../crdt/conflict-resolution/auto-resolver.js';
import type { CRDTOperation, VectorClock } from '../crdt/core/types.js';

function makeVectorClock(values: Array<[string, number]>): VectorClock {
  return {
    id: `vc-${values.map(([replicaId]) => replicaId).join('-')}`,
    values: new Map(values),
    timestamp: Date.now(),
  };
}

function makeOperation(
  params: Partial<CRDTOperation> & { targetId: string; type: CRDTOperation['type'] }
): CRDTOperation {
  return {
    id: `op-${Math.random()}`,
    replicaId: 'replica-a',
    type: 'update',
    targetId: 'node-1',
    vectorClock: makeVectorClock([['replica-a', 1]]),
    data: {},
    timestamp: Date.now(),
    parents: [],
    ...params,
  };
}

describe('autoResolveOperations()', () => {
  it('keeps concurrent inserts at different positions in positional order', () => {
    const left = makeOperation({
      id: 'insert-b',
      type: 'insert',
      targetId: 'doc-1',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-b', 2],
      ]),
      data: { position: 9, value: 'B' },
    });
    const right = makeOperation({
      id: 'insert-a',
      type: 'insert',
      targetId: 'doc-1',
      replicaId: 'replica-a',
      vectorClock: makeVectorClock([
        ['replica-a', 2],
        ['replica-b', 1],
      ]),
      data: { position: 3, value: 'A' },
    });

    const result = autoResolveOperations(left, right);

    expect(result.resolved).toBe(true);
    expect(result.rule).toBe('distinct-position-insert');
    expect(result.operations.map((operation) => operation.id)).toEqual(['insert-a', 'insert-b']);
  });

  it('orders same-position inserts lexicographically by replica ID', () => {
    const left = makeOperation({
      id: 'insert-z',
      type: 'insert',
      targetId: 'doc-1',
      replicaId: 'replica-z',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-z', 2],
      ]),
      data: { position: 4, value: 'Z' },
    });
    const right = makeOperation({
      id: 'insert-a',
      type: 'insert',
      targetId: 'doc-1',
      replicaId: 'replica-a',
      vectorClock: makeVectorClock([
        ['replica-a', 2],
        ['replica-z', 1],
      ]),
      data: { position: 4, value: 'A' },
    });

    const result = autoResolveOperations(left, right);

    expect(result.resolved).toBe(true);
    expect(result.rule).toBe('same-position-insert');
    expect(result.operations.map((operation) => operation.replicaId)).toEqual(['replica-a', 'replica-z']);
  });

  it('lets delete win over concurrent update on the same target', () => {
    const update = makeOperation({
      id: 'update-node',
      type: 'update',
      targetId: 'node-1',
      data: { field: 'title', value: 'Updated title' },
    });
    const deletion = makeOperation({
      id: 'delete-node',
      type: 'delete',
      targetId: 'node-1',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-b', 1],
      ]),
    });

    const result = autoResolveOperations(update, deletion);

    expect(result.resolved).toBe(true);
    expect(result.rule).toBe('delete-wins');
    expect(result.operations).toEqual([deletion]);
  });

  it('keeps both concurrent updates when they touch different fields', () => {
    const titleUpdate = makeOperation({
      id: 'update-title',
      type: 'update',
      targetId: 'node-1',
      data: { field: 'title', value: 'New title' },
    });
    const descriptionUpdate = makeOperation({
      id: 'update-description',
      type: 'update',
      targetId: 'node-1',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-b', 1],
      ]),
      data: { field: 'description', value: 'New description' },
    });

    const result = autoResolveOperations(titleUpdate, descriptionUpdate);

    expect(result.resolved).toBe(true);
    expect(result.rule).toBe('field-merge');
    expect(result.operations).toEqual([titleUpdate, descriptionUpdate]);
  });

  it('uses vector clocks and stable tie-breakers for same-field updates', () => {
    const earlier = makeOperation({
      id: 'update-a',
      type: 'update',
      targetId: 'node-1',
      replicaId: 'replica-a',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-b', 1],
      ]),
      timestamp: 100,
      data: { field: 'title', value: 'A' },
    });
    const later = makeOperation({
      id: 'update-b',
      type: 'update',
      targetId: 'node-1',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-b', 1],
      ]),
      timestamp: 200,
      data: { field: 'title', value: 'B' },
    });

    const result = autoResolveOperations(earlier, later);

    expect(result.resolved).toBe(true);
    expect(result.rule).toBe('vector-clock-wins');
    expect(result.operations).toEqual([later]);
  });

  it('averages concurrent move operations on the same target', () => {
    const left = makeOperation({
      id: 'move-left',
      type: 'move',
      targetId: 'node-1',
      data: { x: 10, y: 20 },
    });
    const right = makeOperation({
      id: 'move-right',
      type: 'move',
      targetId: 'node-1',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock([
        ['replica-a', 1],
        ['replica-b', 1],
      ]),
      data: { x: 30, y: 40 },
    });

    const result = autoResolveOperations(left, right);

    expect(result.resolved).toBe(true);
    expect(result.rule).toBe('move-average');
    expect(result.operations).toHaveLength(1);
    expect(result.operations[0]?.data).toEqual({ x: 20, y: 30 });
  });
});