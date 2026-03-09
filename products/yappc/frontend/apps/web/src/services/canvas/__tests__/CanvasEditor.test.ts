/**
 * CanvasEditor Tests
 * 
 * Unit tests for CanvasEditor service module.
 * Tests selection, manipulation, clipboard, and alignment operations.
 * 
 * @doc.type test
 * @doc.purpose Unit tests for CanvasEditor
 * @doc.layer product
 * @doc.pattern Unit Testing
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { CanvasEditor } from '../CanvasEditor';
import type { ReactFlowInstance, Node, Edge } from '@xyflow/react';

describe('CanvasEditor', () => {
    let editor: CanvasEditor;
    let mockRfInstance: Partial<ReactFlowInstance>;
    let mockNodes: Node[];
    let mockEdges: Edge[];

    beforeEach(() => {
        // Reset editor before each test
        editor = new CanvasEditor({
            snapToGrid: true,
            snapGrid: [15, 15],
            multiSelect: true,
        });

        // Mock nodes and edges
        mockNodes = [
            { id: 'node-1', position: { x: 0, y: 0 }, data: {} },
            { id: 'node-2', position: { x: 100, y: 100 }, data: {} },
            { id: 'node-3', position: { x: 200, y: 200 }, data: {} },
        ];

        mockEdges = [
            { id: 'edge-1', source: 'node-1', target: 'node-2' },
            { id: 'edge-2', source: 'node-2', target: 'node-3' },
        ];

        // Mock ReactFlow instance
        mockRfInstance = {
            getNodes: vi.fn(() => [...mockNodes]),
            getEdges: vi.fn(() => [...mockEdges]),
            setNodes: vi.fn((nodes) => { mockNodes = nodes; }),
            setEdges: vi.fn((edges) => { mockEdges = edges; }),
            getViewport: vi.fn(() => ({ x: 0, y: 0, zoom: 1 })),
        };

        editor.setReactFlowInstance(mockRfInstance as ReactFlowInstance);
    });

    describe('Selection Management', () => {
        it('should select a single node', () => {
            editor.selectNode('node-1');

            const selection = editor.getSelection();
            expect(selection.selectedNodes).toEqual(['node-1']);
            expect(selection.selectedEdges).toEqual([]);
            expect(selection.lastSelected).toBe('node-1');
        });

        it('should select multiple nodes when multi is true', () => {
            editor.selectNode('node-1', false);
            editor.selectNode('node-2', true);
            editor.selectNode('node-3', true);

            const selection = editor.getSelection();
            expect(selection.selectedNodes).toEqual(['node-1', 'node-2', 'node-3']);
            expect(selection.lastSelected).toBe('node-3');
        });

        it('should replace selection when multi is false', () => {
            editor.selectNode('node-1', false);
            editor.selectNode('node-2', false);

            const selection = editor.getSelection();
            expect(selection.selectedNodes).toEqual(['node-2']);
        });

        it('should select an edge', () => {
            editor.selectEdge('edge-1');

            const selection = editor.getSelection();
            expect(selection.selectedEdges).toEqual(['edge-1']);
            expect(selection.selectedNodes).toEqual([]);
        });

        it('should select multiple elements at once', () => {
            editor.selectMultiple(['node-1', 'node-2'], ['edge-1']);

            const selection = editor.getSelection();
            expect(selection.selectedNodes).toEqual(['node-1', 'node-2']);
            expect(selection.selectedEdges).toEqual(['edge-1']);
            expect(selection.selectionMode).toBe('multiple');
        });

        it('should clear selection', () => {
            editor.selectNode('node-1');
            editor.selectEdge('edge-1', true);
            editor.clearSelection();

            const selection = editor.getSelection();
            expect(selection.selectedNodes).toEqual([]);
            expect(selection.selectedEdges).toEqual([]);
            expect(selection.lastSelected).toBeNull();
        });
    });

    describe('Element Manipulation', () => {
        it('should move nodes with delta', () => {
            editor.moveNodes(['node-1', 'node-2'], { x: 50, y: 50 });

            expect(mockRfInstance.setNodes).toHaveBeenCalled();
            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];

            expect(updatedNodes[0].position).toEqual({ x: 45, y: 45 }); // Snapped to grid
            expect(updatedNodes[1].position).toEqual({ x: 150, y: 150 });
        });

        it('should snap to grid when enabled', () => {
            editor.moveNodes(['node-1'], { x: 7, y: 8 });

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            expect(updatedNodes[0].position).toEqual({ x: 15, y: 15 }); // Snapped to nearest 15
        });

        it('should not move unselected nodes', () => {
            editor.moveNodes(['node-1'], { x: 50, y: 50 });

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            expect(updatedNodes[1].position).toEqual({ x: 100, y: 100 }); // Unchanged
            expect(updatedNodes[2].position).toEqual({ x: 200, y: 200 }); // Unchanged
        });
    });

    describe('Clipboard Operations', () => {
        it('should copy selection to clipboard', () => {
            editor.selectNode('node-1');
            editor.copySelection();

            // Verify copy event was emitted
            const copyHandler = vi.fn();
            editor.on('copy', copyHandler);

            editor.copySelection();
            expect(copyHandler).toHaveBeenCalledWith({ count: 1 });
        });

        it('should paste from clipboard with offset', () => {
            editor.selectNode('node-1');
            editor.copySelection();
            editor.paste({ x: 100, y: 100 });

            expect(mockRfInstance.setNodes).toHaveBeenCalled();
            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];

            // Should have 4 nodes (3 original + 1 pasted)
            expect(updatedNodes.length).toBe(4);

            // New node should be at offset position
            const newNode = updatedNodes[3];
            expect(newNode.position.x).toBe(100);
            expect(newNode.position.y).toBe(100);
            expect(newNode.selected).toBe(true);
        });

        it('should cut selection (copy + delete)', () => {
            editor.selectNode('node-1');
            editor.cutSelection();

            // Should delete the node
            expect(mockRfInstance.setNodes).toHaveBeenCalled();
            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            expect(updatedNodes.length).toBe(2); // One node removed
        });

        it('should duplicate selection', () => {
            editor.selectNode('node-1');
            editor.duplicate();

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            expect(updatedNodes.length).toBe(4); // 3 original + 1 duplicate
        });
    });

    describe('Deletion Operations', () => {
        it('should delete selected nodes', () => {
            editor.selectNode('node-1');
            editor.deleteSelection();

            expect(mockRfInstance.setNodes).toHaveBeenCalled();
            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            expect(updatedNodes.length).toBe(2);
            expect(updatedNodes.find((n: Node) => n.id === 'node-1')).toBeUndefined();
        });

        it('should delete specific nodes', () => {
            editor.deleteNodes(['node-1', 'node-2']);

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            expect(updatedNodes.length).toBe(1);
            expect(updatedNodes[0].id).toBe('node-3');
        });

        it('should delete edges connected to deleted nodes', () => {
            editor.deleteNodes(['node-2']);

            // Both edges connected to node-2 should be deleted
            expect(mockRfInstance.setEdges).toHaveBeenCalled();
            const updatedEdges = (mockRfInstance.setEdges as unknown).mock.calls[0][0];
            expect(updatedEdges.length).toBe(0);
        });

        it('should delete specific edges', () => {
            editor.deleteEdges(['edge-1']);

            const updatedEdges = (mockRfInstance.setEdges as unknown).mock.calls[0][0];
            expect(updatedEdges.length).toBe(1);
            expect(updatedEdges[0].id).toBe('edge-2');
        });
    });

    describe('Alignment Operations', () => {
        it('should align nodes to the left', () => {
            editor.alignNodes('left');

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            const leftMost = Math.min(...mockNodes.map(n => n.position.x));

            updatedNodes.forEach((node: Node) => {
                expect(node.position.x).toBe(leftMost);
            });
        });

        it('should align nodes to the right', () => {
            editor.alignNodes('right');

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            const rightMost = Math.max(...mockNodes.map(n => n.position.x));

            updatedNodes.forEach((node: Node) => {
                expect(node.position.x).toBe(rightMost);
            });
        });

        it('should align nodes to center', () => {
            editor.alignNodes('center');

            const updatedNodes = (mockRfInstance.setNodes as unknown).mock.calls[0][0];
            const positions = mockNodes.map(n => n.position.x);
            const centerX = (Math.min(...positions) + Math.max(...positions)) / 2;

            updatedNodes.forEach((node: Node) => {
                expect(node.position.x).toBe(centerX);
            });
        });

        it('should not align with less than 2 nodes selected', () => {
            editor.selectNode('node-1');
            editor.alignNodes('left');

            // Should not call setNodes if less than 2 nodes
            expect(mockRfInstance.setNodes).not.toHaveBeenCalled();
        });
    });

    describe('Event System', () => {
        it('should emit selection change events', () => {
            const handler = vi.fn();
            editor.on('selectionChange', handler);

            editor.selectNode('node-1');

            expect(handler).toHaveBeenCalledWith(
                expect.objectContaining({
                    selectedNodes: ['node-1'],
                })
            );
        });

        it('should emit move events', () => {
            const handler = vi.fn();
            editor.on('nodesMove', handler);

            editor.moveNodes(['node-1'], { x: 10, y: 10 });

            expect(handler).toHaveBeenCalledWith({
                nodeIds: ['node-1'],
                delta: { x: 10, y: 10 },
            });
        });

        it('should remove event listeners', () => {
            const handler = vi.fn();
            editor.on('selectionChange', handler);
            editor.off('selectionChange', handler);

            editor.selectNode('node-1');

            expect(handler).not.toHaveBeenCalled();
        });
    });

    describe('Cleanup', () => {
        it('should cleanup resources on destroy', () => {
            editor.selectNode('node-1');
            editor.destroy();

            const selection = editor.getSelection();
            expect(selection.selectedNodes).toEqual([]);
        });
    });
});
