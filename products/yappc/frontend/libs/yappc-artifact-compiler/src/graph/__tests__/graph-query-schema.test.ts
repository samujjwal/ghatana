import { describe, expect, it } from 'vitest';
import { GraphQuerySchema } from '../types';

describe('GraphQuerySchema', () => {
  it('accepts cursor pagination and unresolved-edge filters', () => {
    const parsed = GraphQuerySchema.parse({
      nodeKinds: ['component'],
      edgeKinds: ['imports'],
      cursor: '2026-05-15T10:20:30.000Z',
      limit: 50,
      includeUnresolvedEdges: true,
      unresolvedStatuses: ['ambiguous', 'cross-repo'],
    });

    expect(parsed.cursor).toBe('2026-05-15T10:20:30.000Z');
    expect(parsed.unresolvedStatuses).toEqual(['ambiguous', 'cross-repo']);
  });

  it('rejects empty cursor values', () => {
    expect(() =>
      GraphQuerySchema.parse({
        cursor: '',
      }),
    ).toThrow();
  });
});