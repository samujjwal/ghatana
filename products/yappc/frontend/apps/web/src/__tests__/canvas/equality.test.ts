import { describe, it, expect } from 'vitest';

import {
  normalizeNodesForCompare,
  normalizeConnectionsForCompare,
} from '../../components/canvas/utils/transform';

describe('normalization equality helpers', () => {
  it('normalizeNodesForCompare produces stable, sorted output', () => {
    const nodes = [
      { id: 'b', type: 'api', position: { x: 0, y: 0 }, data: { label: 'B' } },
      {
        id: 'a',
        type: 'component',
        position: { x: 1, y: 2 },
        data: { label: 'A' },
      },
    ];

    const normalized = normalizeNodesForCompare(nodes as unknown);
    expect(normalized).toHaveLength(2);
    expect(normalized[0].id).toBe('a');
    expect(normalized[1].id).toBe('b');
  });

  it('normalizeConnectionsForCompare ignores irrelevant ordering and handles undefined handles', () => {
    const conns = [
      {
        id: 'c2',
        source: 'n1',
        target: 'n2',
        sourceHandle: undefined,
        targetHandle: 'a',
      },
      {
        id: 'c1',
        source: 'n3',
        target: 'n4',
        sourceHandle: 'x',
        targetHandle: undefined,
      },
    ];

    const normalized = normalizeConnectionsForCompare(conns as unknown);
    expect(normalized).toHaveLength(2);
    expect(normalized[0].id).toBe('c1');
    expect(normalized[1].id).toBe('c2');
  });
});
