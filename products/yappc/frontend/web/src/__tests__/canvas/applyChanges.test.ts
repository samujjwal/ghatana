import { describe, it, expect } from 'vitest';

import { createMockReactFlowInstance } from '../../test-utils/reactflow-mocks';

interface MockRfInstance {
  __applyNodeChanges: (items: Array<{ type: string; item?: unknown; id?: string }>) => void;
  __applyEdgeChanges: (items: Array<{ type: string; item?: unknown; id?: string }>) => void;
  getNodes: () => Array<{ id: string; position: { x: number; y: number } }>;
  getEdges: () => Array<{ id: string; source: string; target: string }>;
}

describe('reactflow-mocks apply changes', () => {
  it('adds, updates and removes nodes via __applyNodeChanges', () => {
    const mock = createMockReactFlowInstance([], [] as unknown[]) as MockRfInstance;
    const addNode = {
      id: 'n1',
      position: { x: 1, y: 2 },
      data: { label: 'one' },
    };
    mock.__applyNodeChanges([{ type: 'add', item: addNode }]);
    expect(mock.getNodes()).toHaveLength(1);

    // update
    mock.__applyNodeChanges([
      { type: 'update', item: { id: 'n1', position: { x: 5, y: 6 } } },
    ]);
    expect(mock.getNodes()[0].position).toEqual({ x: 5, y: 6 });

    // remove
    mock.__applyNodeChanges([{ type: 'remove', id: 'n1' }]);
    expect(mock.getNodes()).toHaveLength(0);
  });

  it('adds and removes edges via __applyEdgeChanges', () => {
    const mock = createMockReactFlowInstance([], [] as unknown[]) as MockRfInstance;
    const edge = { id: 'e1', source: 'n1', target: 'n2' };
    mock.__applyEdgeChanges([{ type: 'add', item: edge }]);
    expect(mock.getEdges()).toHaveLength(1);

    mock.__applyEdgeChanges([{ type: 'remove', id: 'e1' }]);
    expect(mock.getEdges()).toHaveLength(0);
  });
});
