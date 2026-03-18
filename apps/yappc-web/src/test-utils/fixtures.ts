/**
 * @module fixtures
 * @description Test fixture data for canvas testing.
 */

import type { Node, Edge } from '@xyflow/react';

/**
 * Sample workflow nodes for testing
 */
export const fixtureWorkflowNodes: Node[] = [
  {
    id: 'start-1',
    type: 'input',
    position: { x: 100, y: 100 },
    data: { label: 'Start' },
  },
  {
    id: 'process-1',
    type: 'default',
    position: { x: 300, y: 100 },
    data: { label: 'Process Data' },
  },
  {
    id: 'decision-1',
    type: 'default',
    position: { x: 500, y: 100 },
    data: { label: 'Valid?' },
  },
  {
    id: 'end-success',
    type: 'output',
    position: { x: 700, y: 50 },
    data: { label: 'Success' },
  },
  {
    id: 'end-failure',
    type: 'output',
    position: { x: 700, y: 150 },
    data: { label: 'Failure' },
  },
];

/**
 * Sample workflow edges for testing
 */
export const fixtureWorkflowEdges: Edge[] = [
  {
    id: 'e1',
    source: 'start-1',
    target: 'process-1',
  },
  {
    id: 'e2',
    source: 'process-1',
    target: 'decision-1',
  },
  {
    id: 'e3',
    source: 'decision-1',
    target: 'end-success',
    label: 'Yes',
  },
  {
    id: 'e4',
    source: 'decision-1',
    target: 'end-failure',
    label: 'No',
  },
];

/**
 * Simple linear flow for testing
 */
export const fixtureLinearFlow = {
  nodes: [
    {
      id: 'node-1',
      type: 'input',
      position: { x: 0, y: 0 },
      data: { label: 'Step 1' },
    },
    {
      id: 'node-2',
      type: 'default',
      position: { x: 200, y: 0 },
      data: { label: 'Step 2' },
    },
    {
      id: 'node-3',
      type: 'output',
      position: { x: 400, y: 0 },
      data: { label: 'Step 3' },
    },
  ],
  edges: [
    {
      id: 'edge-1-2',
      source: 'node-1',
      target: 'node-2',
    },
    {
      id: 'edge-2-3',
      source: 'node-2',
      target: 'node-3',
    },
  ],
};

/**
 * Complex branching flow for testing
 */
export const fixtureBranchingFlow = {
  nodes: [
    {
      id: 'root',
      type: 'input',
      position: { x: 250, y: 0 },
      data: { label: 'Root' },
    },
    {
      id: 'branch-a',
      type: 'default',
      position: { x: 100, y: 150 },
      data: { label: 'Branch A' },
    },
    {
      id: 'branch-b',
      type: 'default',
      position: { x: 400, y: 150 },
      data: { label: 'Branch B' },
    },
    {
      id: 'merge',
      type: 'output',
      position: { x: 250, y: 300 },
      data: { label: 'Merge' },
    },
  ],
  edges: [
    {
      id: 'root-a',
      source: 'root',
      target: 'branch-a',
    },
    {
      id: 'root-b',
      source: 'root',
      target: 'branch-b',
    },
    {
      id: 'a-merge',
      source: 'branch-a',
      target: 'merge',
    },
    {
      id: 'b-merge',
      source: 'branch-b',
      target: 'merge',
    },
  ],
};

/**
 * Large flow for performance testing
 */
export function fixtureGenerateLargeFlow(nodeCount: number): {
  nodes: Node[];
  edges: Edge[];
} {
  const nodes: Node[] = [];
  const edges: Edge[] = [];
  const columns = Math.ceil(Math.sqrt(nodeCount));

  for (let i = 0; i < nodeCount; i++) {
    const row = Math.floor(i / columns);
    const col = i % columns;

    nodes.push({
      id: `node-${i}`,
      type: i === 0 ? 'input' : i === nodeCount - 1 ? 'output' : 'default',
      position: { x: col * 200, y: row * 150 },
      data: { label: `Node ${i + 1}` },
    });

    // Connect to next node in same row
    if (col < columns - 1 && i < nodeCount - 1) {
      edges.push({
        id: `edge-${i}-${i + 1}`,
        source: `node-${i}`,
        target: `node-${i + 1}`,
      });
    }

    // Connect to node in next row
    if (i + columns < nodeCount) {
      edges.push({
        id: `edge-${i}-${i + columns}`,
        source: `node-${i}`,
        target: `node-${i + columns}`,
      });
    }
  }

  return { nodes, edges };
}

/**
 * Sample palette items
 */
export const fixturePaletteItems = [
  {
    id: 'shape-rectangle',
    category: 'shapes',
    type: 'rectangle',
    label: 'Rectangle',
    icon: '▭',
  },
  {
    id: 'shape-circle',
    category: 'shapes',
    type: 'circle',
    label: 'Circle',
    icon: '○',
  },
  {
    id: 'shape-triangle',
    category: 'shapes',
    type: 'triangle',
    label: 'Triangle',
    icon: '△',
  },
  {
    id: 'diagram-flowchart',
    category: 'diagrams',
    type: 'flowchart',
    label: 'Flowchart',
    icon: '⧉',
  },
  {
    id: 'diagram-sequence',
    category: 'diagrams',
    type: 'sequence',
    label: 'Sequence',
    icon: '⧉',
  },
];

/**
 * Sample viewport configurations
 */
export const fixtureViewports = {
  default: { x: 0, y: 0, zoom: 1 },
  zoomedIn: { x: 0, y: 0, zoom: 2 },
  zoomedOut: { x: 0, y: 0, zoom: 0.5 },
  centered: { x: 400, y: 300, zoom: 1 },
  topLeft: { x: 0, y: 0, zoom: 1 },
  bottomRight: { x: 800, y: 600, zoom: 1 },
};
