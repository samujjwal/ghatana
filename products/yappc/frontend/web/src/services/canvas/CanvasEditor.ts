/**
 * Canvas Editor Module
 * 
 * Standalone module for canvas editing operations: selection, manipulation,
 * drag-drop, resize, keyboard shortcuts. Separated from rendering concerns
 * for better testability and reusability.
 * 
 * @doc.type module
 * @doc.purpose Canvas editing logic extraction
 * @doc.layer product  
 * @doc.pattern Service Module
 */

import React from 'react';
import type { Node, Edge, ReactFlowInstance, XYPosition } from '@xyflow/react';

/**
 * Selection state
 */
export interface SelectionState {
    selectedNodes: string[];
    selectedEdges: string[];
    lastSelected: string | null;
    selectionMode: 'single' | 'multiple' | 'marquee';
}

/**
 * Editor configuration
 */
export interface EditorConfig {
    snapToGrid?: boolean;
    snapGrid?: [number, number];
    multiSelect?: boolean;
    keyboardShortcuts?: boolean;
    dragThreshold?: number;
}

/**
 * Editor operations
 */
export interface EditorOperations {
    // Selection
    selectNode: (nodeId: string, multi?: boolean) => void;
    selectEdge: (edgeId: string, multi?: boolean) => void;
    selectMultiple: (nodeIds: string[], edgeIds: string[]) => void;
    clearSelection: () => void;

    // Manipulation
    moveNodes: (nodeIds: string[], delta: XYPosition) => void;
    resizeNode: (nodeId: string, width: number, height: number) => void;
    rotateNode: (nodeId: string, angle: number) => void;

    // Clipboard
    copySelection: () => void;
    cutSelection: () => void;
    paste: (position?: XYPosition) => void;
    duplicate: () => void;

    // Deletion
    deleteSelection: () => void;
    deleteNodes: (nodeIds: string[]) => void;
    deleteEdges: (edgeIds: string[]) => void;

    // Alignment
    alignNodes: (alignment: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom') => void;
    distributeNodes: (distribution: 'horizontal' | 'vertical') => void;

    // Grouping
    groupNodes: (nodeIds: string[]) => string; // Returns group ID
    ungroupNodes: (groupId: string) => void;
}

/**
 * Keyboard shortcut handlers
 */
export interface KeyboardShortcuts {
    'Cmd+A': () => void; // Select all
    'Cmd+C': () => void; // Copy
    'Cmd+X': () => void; // Cut
    'Cmd+V': () => void; // Paste
    'Cmd+D': () => void; // Duplicate
    'Delete': () => void; // Delete
    'Backspace': () => void; // Delete
    'Escape': () => void; // Clear selection
    'Cmd+Z': () => void; // Undo
    'Cmd+Shift+Z': () => void; // Redo
}

/**
 * Canvas Editor Service
 * 
 * Provides editing capabilities for canvas elements including selection,
 * manipulation, clipboard operations, and keyboard shortcuts.
 */
export class CanvasEditor {
    private rfInstance: ReactFlowInstance | null = null;
    private selection: SelectionState;
    private config: EditorConfig;
    private clipboard: { nodes: Node[]; edges: Edge[] } | null = null;
    private eventListeners: Map<string, Set<Function>> = new Map();

    constructor(config: EditorConfig = {}) {
        this.config = {
            snapToGrid: true,
            snapGrid: [15, 15],
            multiSelect: true,
            keyboardShortcuts: true,
            dragThreshold: 5,
            ...config,
        };

        this.selection = {
            selectedNodes: [],
            selectedEdges: [],
            lastSelected: null,
            selectionMode: 'single',
        };
    }

    /**
     * Initialize with ReactFlow instance
     */
    public setReactFlowInstance(instance: ReactFlowInstance): void {
        this.rfInstance = instance;
    }

    /**
     * Get current selection state
     */
    public getSelection(): SelectionState {
        return { ...this.selection };
    }

    /**
     * Select a node
     */
    public selectNode(nodeId: string, multi: boolean = false): void {
        if (!multi) {
            this.selection.selectedNodes = [nodeId];
            this.selection.selectedEdges = [];
        } else {
            if (!this.selection.selectedNodes.includes(nodeId)) {
                this.selection.selectedNodes.push(nodeId);
            }
        }

        this.selection.lastSelected = nodeId;
        this.emit('selectionChange', this.selection);
    }

    /**
     * Select an edge
     */
    public selectEdge(edgeId: string, multi: boolean = false): void {
        if (!multi) {
            this.selection.selectedNodes = [];
            this.selection.selectedEdges = [edgeId];
        } else {
            if (!this.selection.selectedEdges.includes(edgeId)) {
                this.selection.selectedEdges.push(edgeId);
            }
        }

        this.selection.lastSelected = edgeId;
        this.emit('selectionChange', this.selection);
    }

    /**
     * Select multiple elements
     */
    public selectMultiple(nodeIds: string[], edgeIds: string[]): void {
        this.selection.selectedNodes = [...nodeIds];
        this.selection.selectedEdges = [...edgeIds];
        this.selection.lastSelected = nodeIds[nodeIds.length - 1] || edgeIds[edgeIds.length - 1] || null;
        this.selection.selectionMode = 'multiple';
        this.emit('selectionChange', this.selection);
    }

    /**
     * Clear selection
     */
    public clearSelection(): void {
        this.selection.selectedNodes = [];
        this.selection.selectedEdges = [];
        this.selection.lastSelected = null;
        this.selection.selectionMode = 'single';
        this.emit('selectionChange', this.selection);
    }

    /**
     * Move selected nodes
     */
    public moveNodes(nodeIds: string[], delta: XYPosition): void {
        if (!this.rfInstance) return;

        const nodes = this.rfInstance.getNodes();
        const updatedNodes = nodes.map(node => {
            if (nodeIds.includes(node.id)) {
                const newPosition = {
                    x: node.position.x + delta.x,
                    y: node.position.y + delta.y,
                };

                // Snap to grid if enabled
                if (this.config.snapToGrid && this.config.snapGrid) {
                    newPosition.x = Math.round(newPosition.x / this.config.snapGrid[0]) * this.config.snapGrid[0];
                    newPosition.y = Math.round(newPosition.y / this.config.snapGrid[1]) * this.config.snapGrid[1];
                }

                return { ...node, position: newPosition };
            }
            return node;
        });

        this.rfInstance.setNodes(updatedNodes);
        this.emit('nodesMove', { nodeIds, delta });
    }

    /**
     * Copy selection to clipboard
     */
    public copySelection(): void {
        if (!this.rfInstance) return;

        const nodes = this.rfInstance.getNodes();
        const edges = this.rfInstance.getEdges();

        this.clipboard = {
            nodes: nodes.filter(n => this.selection.selectedNodes.includes(n.id)),
            edges: edges.filter(e => this.selection.selectedEdges.includes(e.id)),
        };

        this.emit('copy', { count: this.clipboard.nodes.length + this.clipboard.edges.length });
    }

    /**
     * Cut selection to clipboard
     */
    public cutSelection(): void {
        this.copySelection();
        this.deleteSelection();
    }

    /**
     * Paste from clipboard
     */
    public paste(position?: XYPosition): void {
        if (!this.rfInstance || !this.clipboard) return;

        const viewport = this.rfInstance.getViewport();
        const pastePosition = position || {
            x: viewport.x + 100,
            y: viewport.y + 100,
        };

        // Calculate offset from original positions
        const firstNode = this.clipboard.nodes[0];
        const offset = firstNode ? {
            x: pastePosition.x - firstNode.position.x,
            y: pastePosition.y - firstNode.position.y,
        } : { x: 0, y: 0 };

        // Create new nodes with offset positions and new IDs
        const newNodes = this.clipboard.nodes.map(node => ({
            ...node,
            id: `${node.id}-copy-${Date.now()}`,
            position: {
                x: node.position.x + offset.x,
                y: node.position.y + offset.y,
            },
            selected: true,
        }));

        this.rfInstance.setNodes([...this.rfInstance.getNodes(), ...newNodes]);
        this.emit('paste', { count: newNodes.length });
    }

    /**
     * Duplicate selection
     */
    public duplicate(): void {
        this.copySelection();
        this.paste({ x: 50, y: 50 }); // Offset by 50px
    }

    /**
     * Delete selection
     */
    public deleteSelection(): void {
        if (!this.rfInstance) return;

        this.deleteNodes(this.selection.selectedNodes);
        this.deleteEdges(this.selection.selectedEdges);
        this.clearSelection();
    }

    /**
     * Delete specific nodes
     */
    public deleteNodes(nodeIds: string[]): void {
        if (!this.rfInstance) return;

        const nodes = this.rfInstance.getNodes();
        const edges = this.rfInstance.getEdges();

        // Remove nodes
        const remainingNodes = nodes.filter(n => !nodeIds.includes(n.id));

        // Remove edges connected to deleted nodes
        const remainingEdges = edges.filter(e =>
            !nodeIds.includes(e.source) && !nodeIds.includes(e.target)
        );

        this.rfInstance.setNodes(remainingNodes);
        this.rfInstance.setEdges(remainingEdges);

        this.emit('delete', { nodes: nodeIds });
    }

    /**
     * Delete specific edges
     */
    public deleteEdges(edgeIds: string[]): void {
        if (!this.rfInstance) return;

        const edges = this.rfInstance.getEdges();
        const remainingEdges = edges.filter(e => !edgeIds.includes(e.id));

        this.rfInstance.setEdges(remainingEdges);
        this.emit('delete', { edges: edgeIds });
    }

    /**
     * Align selected nodes
     */
    public alignNodes(alignment: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom'): void {
        if (!this.rfInstance || this.selection.selectedNodes.length < 2) return;

        const nodes = this.rfInstance.getNodes();
        const selectedNodes = nodes.filter(n => this.selection.selectedNodes.includes(n.id));

        // Calculate alignment reference
        const positions = selectedNodes.map(n => n.position);
        let referenceValue: number;

        switch (alignment) {
            case 'left':
                referenceValue = Math.min(...positions.map(p => p.x));
                selectedNodes.forEach(n => n.position.x = referenceValue);
                break;
            case 'right':
                referenceValue = Math.max(...positions.map(p => p.x));
                selectedNodes.forEach(n => n.position.x = referenceValue);
                break;
            case 'center':
                referenceValue = (Math.min(...positions.map(p => p.x)) + Math.max(...positions.map(p => p.x))) / 2;
                selectedNodes.forEach(n => n.position.x = referenceValue);
                break;
            case 'top':
                referenceValue = Math.min(...positions.map(p => p.y));
                selectedNodes.forEach(n => n.position.y = referenceValue);
                break;
            case 'bottom':
                referenceValue = Math.max(...positions.map(p => p.y));
                selectedNodes.forEach(n => n.position.y = referenceValue);
                break;
            case 'middle':
                referenceValue = (Math.min(...positions.map(p => p.y)) + Math.max(...positions.map(p => p.y))) / 2;
                selectedNodes.forEach(n => n.position.y = referenceValue);
                break;
        }

        this.rfInstance.setNodes(nodes);
        this.emit('align', { alignment, nodeIds: this.selection.selectedNodes });
    }

    /**
     * Event system
     */
    public on(event: string, handler: Function): () => void {
        if (!this.eventListeners.has(event)) {
            this.eventListeners.set(event, new Set());
        }
        this.eventListeners.get(event)!.add(handler);

        // Return unsubscribe function
        return () => this.off(event, handler);
    }

    public off(event: string, handler: Function): void {
        this.eventListeners.get(event)?.delete(handler);
    }

    private emit(event: string, data: unknown): void {
        this.eventListeners.get(event)?.forEach(handler => handler(data));
    }

    /**
     * Cleanup
     */
    public destroy(): void {
        this.rfInstance = null;
        this.eventListeners.clear();
        this.clipboard = null;
    }
}

/**
 * React hook for using Canvas Editor
 */
export function useCanvasEditor(config?: EditorConfig) {
    const editorRef = React.useRef<CanvasEditor | null>(null);

    if (!editorRef.current) {
        editorRef.current = new CanvasEditor(config);
    }

    React.useEffect(() => {
        return () => {
            editorRef.current?.destroy();
        };
    }, []);

    return editorRef.current;
}
