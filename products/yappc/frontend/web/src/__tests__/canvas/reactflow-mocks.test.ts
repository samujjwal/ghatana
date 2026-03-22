import { describe, it, expect } from 'vitest';

import { createMockReactFlowInstance } from '../../test-utils';

describe('createMockReactFlowInstance', () => {
  it('projects coordinates using viewport and zoom, and exposes viewport', () => {
    const inst = createMockReactFlowInstance([], []) as unknown;

    // default viewport
    expect(inst.__viewport()).toEqual({ x: 0, y: 0, zoom: 1 });

    inst.setViewport({ x: 100, y: 50, zoom: 2 });
    expect(inst.__viewport()).toEqual({ x: 100, y: 50, zoom: 2 });

    const projected = inst.project({ x: 150, y: 70 });
    // (150 - 100) * 2 = 100 ; (70 - 50) * 2 = 40
    expect(projected).toEqual({ x: 100, y: 40 });
  });

  it('applies node changes: add, update, remove', () => {
    const initial = [{ id: 'n1', position: { x: 0, y: 0 }, data: {} }];
    const inst = createMockReactFlowInstance(initial as unknown, [] as unknown) as unknown;

    // add
    inst.__applyNodeChanges([
      { type: 'add', item: { id: 'n2', position: { x: 10, y: 20 }, data: {} } },
    ]);
    expect(inst.getNodes().map((n: unknown) => n.id)).toContain('n2');

    // update
    inst.__applyNodeChanges([
      { type: 'update', item: { id: 'n2', position: { x: 30, y: 40 } } },
    ]);
    const n2 = inst.getNodes().find((n: unknown) => n.id === 'n2');
    expect(n2.position).toEqual({ x: 30, y: 40 });

    // remove
    inst.__applyNodeChanges([{ type: 'remove', id: 'n1' }]);
    expect(inst.getNodes().map((n: unknown) => n.id)).not.toContain('n1');
  });

  it('applies edge changes: add, update, remove', () => {
    const initialEdges = [{ id: 'e1', source: 'n1', target: 'n2' }];
    const inst = createMockReactFlowInstance([], initialEdges as unknown) as unknown;

    // add
    inst.__applyEdgeChanges([
      { type: 'add', item: { id: 'e2', source: 'n2', target: 'n3' } },
    ]);
    expect(inst.getEdges().map((e: unknown) => e.id)).toContain('e2');

    // update
    inst.__applyEdgeChanges([
      { type: 'update', item: { id: 'e2', animated: true } },
    ]);
    const e2 = inst.getEdges().find((e: unknown) => e.id === 'e2');
    expect(e2.animated).toBe(true);

    // remove
    inst.__applyEdgeChanges([{ type: 'remove', id: 'e1' }]);
    expect(inst.getEdges().map((e: unknown) => e.id)).not.toContain('e1');
  });
});
