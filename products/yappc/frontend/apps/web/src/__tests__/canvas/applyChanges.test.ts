import { describe, it, expect } from 'vitest';

import { createMockReactFlowInstance } from '../../test-utils/reactflow-mocks';

describe('reactflow-mocks apply changes', () => {
  it('adds, updates and removes nodes via __applyNodeChanges', () => {
    const mock = createMockReactFlowInstance([], [] as unknown);
    const addNode = {
      id: 'n1',
      position: { x: 1, y: 2 },
      data: { label: 'one' },
    } as unknown;
    // add
    // @ts-ignore
    mock.__applyNodeChanges([{ type: 'add', item: addNode }]);
    // @ts-ignore
    expect(mock.getNodes()).toHaveLength(1);

    // update
    // @ts-ignore
    mock.__applyNodeChanges([
      { type: 'update', item: { id: 'n1', position: { x: 5, y: 6 } } },
    ]);
    // @ts-ignore
    expect(mock.getNodes()[0].position).toEqual({ x: 5, y: 6 });

    // remove
    // @ts-ignore
    mock.__applyNodeChanges([{ type: 'remove', id: 'n1' }]);
    // @ts-ignore
    expect(mock.getNodes()).toHaveLength(0);
  });

  it('adds and removes edges via __applyEdgeChanges', () => {
    const mock = createMockReactFlowInstance([], [] as unknown);
    const edge = { id: 'e1', source: 'n1', target: 'n2' } as unknown;
    // @ts-ignore
    mock.__applyEdgeChanges([{ type: 'add', item: edge }]);
    // @ts-ignore
    expect(mock.getEdges()).toHaveLength(1);

    // @ts-ignore
    mock.__applyEdgeChanges([{ type: 'remove', id: 'e1' }]);
    // @ts-ignore
    expect(mock.getEdges()).toHaveLength(0);
  });
});
