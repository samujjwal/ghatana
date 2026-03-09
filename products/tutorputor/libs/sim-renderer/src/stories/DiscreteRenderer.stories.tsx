/**
 * Discrete Renderer Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for algorithm visualization renderers
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import type { DiscreteNodeEntity, DiscreteEdgeEntity, DiscretePointerEntity, SimEntityId } from '@ghatana/tutorputor-contracts/v1/simulation';
import { StoryCanvas } from './StoryCanvas';

const meta: Meta<typeof StoryCanvas> = {
    title: 'Simulation/Discrete',
    component: StoryCanvas,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Renderers for discrete algorithm visualization: nodes, edges, and pointers.',
            },
        },
    },
    argTypes: {
        width: { control: { type: 'range', min: 300, max: 1200, step: 50 } },
        height: { control: { type: 'range', min: 200, max: 800, step: 50 } },
        zoom: { control: { type: 'range', min: 0.5, max: 2, step: 0.1 } },
        showGrid: { control: 'boolean' },
        backgroundColor: { control: 'color' },
    },
};

export default meta;
type Story = StoryObj<typeof StoryCanvas>;

// Helper to create typed IDs
const id = (s: string) => s as SimEntityId;

// =============================================================================
// Array Nodes
// =============================================================================

const arrayNodes: DiscreteNodeEntity[] = [
    { id: id('n1'), type: 'node', x: -150, y: 0, value: 42, label: '[0]', shape: 'rect' },
    { id: id('n2'), type: 'node', x: -50, y: 0, value: 17, label: '[1]', shape: 'rect' },
    { id: id('n3'), type: 'node', x: 50, y: 0, value: 8, label: '[2]', shape: 'rect', highlighted: true },
    { id: id('n4'), type: 'node', x: 150, y: 0, value: 35, label: '[3]', shape: 'rect' },
];

export const ArrayNodes: Story = {
    args: {
        entities: arrayNodes,
        width: 600,
        height: 200,
    },
    parameters: {
        docs: {
            description: {
                story: 'Basic array visualization with rectangular nodes. Node [2] is highlighted.',
            },
        },
    },
};

// =============================================================================
// Node States
// =============================================================================

const nodeStates: DiscreteNodeEntity[] = [
    { id: id('s1'), type: 'node', x: -120, y: 0, value: 'N', label: 'Normal', shape: 'circle' },
    { id: id('s2'), type: 'node', x: 0, y: 0, value: 'H', label: 'Highlighted', shape: 'circle', highlighted: true },
    { id: id('s3'), type: 'node', x: 120, y: 0, value: 'V', label: 'Visited', shape: 'circle', visited: true },
    { id: id('s4'), type: 'node', x: -120, y: 100, value: 'C', label: 'Comparing', shape: 'circle', comparing: true },
    { id: id('s5'), type: 'node', x: 0, y: 100, value: 'S', label: 'Sorted', shape: 'circle', sorted: true },
];

export const NodeStates: Story = {
    args: {
        entities: nodeStates,
        width: 400,
        height: 300,
    },
    parameters: {
        docs: {
            description: {
                story: 'Different node states: normal, highlighted, visited, comparing, and sorted.',
            },
        },
    },
};

// =============================================================================
// Node Shapes
// =============================================================================

const nodeShapes: DiscreteNodeEntity[] = [
    { id: id('sh1'), type: 'node', x: -120, y: 0, value: 'R', label: 'Rectangle', shape: 'rect' },
    { id: id('sh2'), type: 'node', x: 0, y: 0, value: 'C', label: 'Circle', shape: 'circle' },
    { id: id('sh3'), type: 'node', x: 120, y: 0, value: 'D', label: 'Diamond', shape: 'diamond' },
    { id: id('sh4'), type: 'node', x: 0, y: 100, value: 'H', label: 'Hexagon', shape: 'hexagon' },
];

export const NodeShapes: Story = {
    args: {
        entities: nodeShapes,
        width: 400,
        height: 300,
    },
    parameters: {
        docs: {
            description: {
                story: 'Available node shapes: rectangle, circle, diamond, and hexagon.',
            },
        },
    },
};

// =============================================================================
// Graph with Edges
// =============================================================================

const graphNodes: DiscreteNodeEntity[] = [
    { id: id('g1'), type: 'node', x: 0, y: -80, value: 'A', shape: 'circle' },
    { id: id('g2'), type: 'node', x: -100, y: 40, value: 'B', shape: 'circle' },
    { id: id('g3'), type: 'node', x: 100, y: 40, value: 'C', shape: 'circle' },
    { id: id('g4'), type: 'node', x: 0, y: 100, value: 'D', shape: 'circle' },
];

const graphEdges: DiscreteEdgeEntity[] = [
    { id: id('e1'), type: 'edge', x: 0, y: 0, sourceId: id('g1'), targetId: id('g2'), directed: true },
    { id: id('e2'), type: 'edge', x: 0, y: 0, sourceId: id('g1'), targetId: id('g3'), directed: true },
    { id: id('e3'), type: 'edge', x: 0, y: 0, sourceId: id('g2'), targetId: id('g4'), directed: true },
    { id: id('e4'), type: 'edge', x: 0, y: 0, sourceId: id('g3'), targetId: id('g4'), directed: true },
];

export const DirectedGraph: Story = {
    args: {
        entities: [...graphEdges, ...graphNodes],
        width: 400,
        height: 300,
    },
    parameters: {
        docs: {
            description: {
                story: 'A directed graph with arrow-headed edges.',
            },
        },
    },
};

// =============================================================================
// Weighted Graph
// =============================================================================

const weightedEdges: DiscreteEdgeEntity[] = [
    { id: id('we1'), type: 'edge', x: 0, y: 0, sourceId: id('g1'), targetId: id('g2'), weight: 5 },
    { id: id('we2'), type: 'edge', x: 0, y: 0, sourceId: id('g1'), targetId: id('g3'), weight: 3 },
    { id: id('we3'), type: 'edge', x: 0, y: 0, sourceId: id('g2'), targetId: id('g4'), weight: 2 },
    { id: id('we4'), type: 'edge', x: 0, y: 0, sourceId: id('g3'), targetId: id('g4'), weight: 4 },
];

export const WeightedGraph: Story = {
    args: {
        entities: [...weightedEdges, ...graphNodes],
        width: 400,
        height: 300,
    },
    parameters: {
        docs: {
            description: {
                story: 'A graph with weighted edges showing edge costs.',
            },
        },
    },
};

// =============================================================================
// Linked List with Pointers
// =============================================================================

const listNodes: DiscreteNodeEntity[] = [
    { id: id('l1'), type: 'node', x: -150, y: 0, value: 10, shape: 'rect' },
    { id: id('l2'), type: 'node', x: -50, y: 0, value: 20, shape: 'rect' },
    { id: id('l3'), type: 'node', x: 50, y: 0, value: 30, shape: 'rect' },
    { id: id('l4'), type: 'node', x: 150, y: 0, value: 40, shape: 'rect' },
];

const listEdges: DiscreteEdgeEntity[] = [
    { id: id('le1'), type: 'edge', x: 0, y: 0, sourceId: id('l1'), targetId: id('l2'), directed: true },
    { id: id('le2'), type: 'edge', x: 0, y: 0, sourceId: id('l2'), targetId: id('l3'), directed: true },
    { id: id('le3'), type: 'edge', x: 0, y: 0, sourceId: id('l3'), targetId: id('l4'), directed: true },
];

const listPointers: DiscretePointerEntity[] = [
    { id: id('p1'), type: 'pointer', x: 0, y: 0, targetId: id('l1'), pointerLabel: 'head', style: 'arrow' },
    { id: id('p2'), type: 'pointer', x: 0, y: 0, targetId: id('l3'), pointerLabel: 'curr', style: 'arrow' },
];

export const LinkedListWithPointers: Story = {
    args: {
        entities: [...listEdges, ...listNodes, ...listPointers],
        width: 600,
        height: 250,
    },
    parameters: {
        docs: {
            description: {
                story: 'A linked list with head and current pointers.',
            },
        },
    },
};

// =============================================================================
// Bubble Sort Step
// =============================================================================

const bubbleSortNodes: DiscreteNodeEntity[] = [
    { id: id('b1'), type: 'node', x: -150, y: 0, value: 5, shape: 'rect', sorted: true },
    { id: id('b2'), type: 'node', x: -50, y: 0, value: 12, shape: 'rect', comparing: true },
    { id: id('b3'), type: 'node', x: 50, y: 0, value: 8, shape: 'rect', comparing: true },
    { id: id('b4'), type: 'node', x: 150, y: 0, value: 23, shape: 'rect' },
];

const bubbleSortPointers: DiscretePointerEntity[] = [
    { id: id('bp1'), type: 'pointer', x: 0, y: 0, targetId: id('b2'), pointerLabel: 'i', style: 'bracket' },
    { id: id('bp2'), type: 'pointer', x: 0, y: 0, targetId: id('b3'), pointerLabel: 'j', style: 'bracket' },
];

export const BubbleSortStep: Story = {
    args: {
        entities: [...bubbleSortNodes, ...bubbleSortPointers],
        width: 600,
        height: 250,
    },
    parameters: {
        docs: {
            description: {
                story: 'A bubble sort step showing comparison of adjacent elements.',
            },
        },
    },
};

// =============================================================================
// Binary Tree
// =============================================================================

const treeNodes: DiscreteNodeEntity[] = [
    { id: id('t1'), type: 'node', x: 0, y: -80, value: 50, shape: 'circle' },
    { id: id('t2'), type: 'node', x: -80, y: 0, value: 30, shape: 'circle' },
    { id: id('t3'), type: 'node', x: 80, y: 0, value: 70, shape: 'circle' },
    { id: id('t4'), type: 'node', x: -120, y: 80, value: 20, shape: 'circle' },
    { id: id('t5'), type: 'node', x: -40, y: 80, value: 40, shape: 'circle' },
    { id: id('t6'), type: 'node', x: 40, y: 80, value: 60, shape: 'circle' },
    { id: id('t7'), type: 'node', x: 120, y: 80, value: 80, shape: 'circle' },
];

const treeEdges: DiscreteEdgeEntity[] = [
    { id: id('te1'), type: 'edge', x: 0, y: 0, sourceId: id('t1'), targetId: id('t2') },
    { id: id('te2'), type: 'edge', x: 0, y: 0, sourceId: id('t1'), targetId: id('t3') },
    { id: id('te3'), type: 'edge', x: 0, y: 0, sourceId: id('t2'), targetId: id('t4') },
    { id: id('te4'), type: 'edge', x: 0, y: 0, sourceId: id('t2'), targetId: id('t5') },
    { id: id('te5'), type: 'edge', x: 0, y: 0, sourceId: id('t3'), targetId: id('t6') },
    { id: id('te6'), type: 'edge', x: 0, y: 0, sourceId: id('t3'), targetId: id('t7') },
];

export const BinarySearchTree: Story = {
    args: {
        entities: [...treeEdges, ...treeNodes],
        width: 500,
        height: 350,
    },
    parameters: {
        docs: {
            description: {
                story: 'A binary search tree with nodes and edges.',
            },
        },
    },
};
