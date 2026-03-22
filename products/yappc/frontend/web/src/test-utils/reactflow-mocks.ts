// Minimal React Flow instance mock for tests
import type { ReactFlowInstance, Node, Edge } from '@xyflow/react';

/**
 * Improved mock of a React Flow instance with simple viewport/project behavior
 * and helpers to simulate applyNodeChanges/applyEdgeChanges semantics for tests.
 */
export function createMockReactFlowInstance(
  initialNodes: Node[] = [],
  initialEdges: Edge[] = []
): Partial<ReactFlowInstance> {
  let nodes = [...initialNodes];
  let edges = [...initialEdges];

  // simple viewport state used by project/unproject
  const viewport = { x: 0, y: 0, zoom: 1 } as {
    x: number;
    y: number;
    zoom: number;
  };

  const getNodes = () => nodes;
  const getEdges = () => edges;

  const project = (pos: { x: number; y: number }) => {
    // apply simple viewport translation and zoom
    return {
      x: (pos.x - viewport.x) * viewport.zoom,
      y: (pos.y - viewport.y) * viewport.zoom,
    };
  };

  const setViewport = (vp: { x?: number; y?: number; zoom?: number }) => {
    if (typeof vp.x === 'number') viewport.x = vp.x;
    if (typeof vp.y === 'number') viewport.y = vp.y;
    if (typeof vp.zoom === 'number') viewport.zoom = vp.zoom;
  };

  const toObject = () => ({ nodes, edges });

  // Minimal implementation of applyNodeChanges semantics used in tests
  const __applyNodeChanges = (changes: Array<unknown>) => {
    changes.forEach((ch) => {
      if (ch.type === 'remove') {
        nodes = nodes.filter((n) => n.id !== ch.id);
      } else if (ch.type === 'add' && ch.item) {
        nodes.push(ch.item);
      } else if (ch.type === 'update' && ch.item) {
        // `ch.item` can be a partial node update
        nodes = nodes.map((n) =>
          n.id === ch.item.id ? { ...n, ...ch.item } : n
        );
      }
    });
    return nodes;
  };

  const __applyEdgeChanges = (changes: Array<unknown>) => {
    changes.forEach((ch) => {
      if (ch.type === 'remove') {
        edges = edges.filter((e) => e.id !== ch.id);
      } else if (ch.type === 'add' && ch.item) {
        edges.push(ch.item);
      } else if (ch.type === 'update' && ch.item) {
        edges = edges.map((e) =>
          e.id === ch.item.id ? { ...e, ...ch.item } : e
        );
      }
    });
    return edges;
  };

  return {
    getNodes,
    getEdges,
    project,
    setViewport,
    toObject,
    // helpers exposed for tests
    __applyNodeChanges,
    __applyEdgeChanges,
    // viewport exposed for test assertions
    __viewport: () => ({ ...viewport }),
  } as unknown as Partial<ReactFlowInstance>;
}
