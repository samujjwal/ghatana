/**
 * useNodeDrag - Enhanced Node Dragging Hook
 * 
 * Provides advanced drag interactions with smart guides, multi-select,
 * snap-to-grid, and axis constraints
 * 
 * @doc.type hook
 * @doc.purpose Advanced node dragging with smart guides
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback, useState, useRef } from 'react';
import { useSetAtom } from 'jotai';
import { canvasAtom } from '../state/atoms/unifiedCanvasAtom';
import type { HierarchicalNode } from '../lib/canvas/HierarchyManager';
import type { Guide } from '../lib/canvas/AlignmentEngine';

export interface DragOptions {
    snapToGrid?: boolean;
    gridSize?: number;
    snapToGuides?: boolean;
    guideThreshold?: number;
    constrainToAxis?: 'horizontal' | 'vertical' | null;
    showGuides?: boolean;
}

export interface UsNodeDragReturn {
    isDragging: boolean;
    draggedNodes: string[];
    currentGuides: Guide[];
    startDrag: (nodeIds: string[], event: MouseEvent | TouchEvent) => void;
    updateDrag: (event: MouseEvent | TouchEvent) => void;
    endDrag: () => void;
    cancelDrag: () => void;
}

export function useNodeDrag(
    nodes: HierarchicalNode[],
    selectedNodeIds: string[],
    onUpdate: (nodeId: string, position: { x: number; y: number }) => void,
    options: DragOptions = {}
): UseNodeDragReturn {
    const {
        snapToGrid = false,
        gridSize = 20,
        snapToGuides = true,
        guideThreshold = 5,
        constrainToAxis = null,
        showGuides = true
    } = options;

    const [isDragging, setIsDragging] = useState(false);
    const [draggedNodes, setDraggedNodes] = useState<string[]>([]);
    const [currentGuides, setCurrentGuides] = useState<Guide[]>([]);

    const dragStartPos = useRef<{ x: number; y: number } | null>(null);
    const initialNodePositions = useRef<Map<string, { x: number; y: number }>>(new Map());

    /**
     * Start dragging nodes
     */
    const startDrag = useCallback((nodeIds: string[], event: MouseEvent | TouchEvent) => {
        const clientX = 'touches' in event ? event.touches[0].clientX : event.clientX;
        const clientY = 'touches' in event ? event.touches[0].clientY : event.clientY;

        // Store initial positions
        const positions = new Map<string, { x: number; y: number }>();
        nodeIds.forEach(nodeId => {
            const node = nodes.find(n => n.id === nodeId);
            if (node) {
                positions.set(nodeId, { x: node.x, y: node.y });
            }
        });

        dragStartPos.current = { x: clientX, y: clientY };
        initialNodePositions.current = positions;
        setIsDragging(true);
        setDraggedNodes(nodeIds);

        // Generate guides from other nodes
        if (showGuides && snapToGuides) {
            const otherNodes = nodes.filter(n => !nodeIds.includes(n.id));
            const guides = generateGuidesFromNodes(otherNodes);
            setCurrentGuides(guides);
        }
    }, [nodes, snapToGuides, showGuides]);

    /**
     * Update drag position
     */
    const updateDrag = useCallback((event: MouseEvent | TouchEvent) => {
        if (!isDragging || !dragStartPos.current) return;

        const clientX = 'touches' in event ? event.touches[0].clientX : event.clientX;
        const clientY = 'touches' in event ? event.touches[0].clientY : event.clientY;

        let deltaX = clientX - dragStartPos.current.x;
        let deltaY = clientY - dragStartPos.current.y;

        // Apply axis constraint
        if (constrainToAxis === 'horizontal') {
            deltaY = 0;
        } else if (constrainToAxis === 'vertical') {
            deltaX = 0;
        }

        // Update each dragged node
        draggedNodes.forEach(nodeId => {
            const initialPos = initialNodePositions.current.get(nodeId);
            if (!initialPos) return;

            let newX = initialPos.x + deltaX;
            let newY = initialPos.y + deltaY;

            // Apply snap to grid
            if (snapToGrid) {
                newX = Math.round(newX / gridSize) * gridSize;
                newY = Math.round(newY / gridSize) * gridSize;
            }

            // Apply snap to guides
            if (snapToGuides && currentGuides.length > 0) {
                const snapped = snapPositionToGuides({ x: newX, y: newY }, currentGuides, guideThreshold);
                newX = snapped.x;
                newY = snapped.y;
            }

            onUpdate(nodeId, { x: newX, y: newY });
        });
    }, [isDragging, draggedNodes, snapToGrid, gridSize, snapToGuides, currentGuides, guideThreshold, constrainToAxis, onUpdate]);

    /**
     * End dragging
     */
    const endDrag = useCallback(() => {
        setIsDragging(false);
        setDraggedNodes([]);
        setCurrentGuides([]);
        dragStartPos.current = null;
        initialNodePositions.current.clear();
    }, []);

    /**
     * Cancel dragging (restore original positions)
     */
    const cancelDrag = useCallback(() => {
        // Restore original positions
        draggedNodes.forEach(nodeId => {
            const initialPos = initialNodePositions.current.get(nodeId);
            if (initialPos) {
                onUpdate(nodeId, initialPos);
            }
        });

        endDrag();
    }, [draggedNodes, onUpdate, endDrag]);

    return {
        isDragging,
        draggedNodes,
        currentGuides,
        startDrag,
        updateDrag,
        endDrag,
        cancelDrag
    };
}

/**
 * Generate alignment guides from nodes
 */
function generateGuidesFromNodes(nodes: HierarchicalNode[]): Guide[] {
    const guides: Guide[] = [];
    let guideId = 0;

    nodes.forEach(node => {
        // Vertical guides (left, center, right edges)
        guides.push({
            id: `guide-${guideId++}`,
            type: 'vertical',
            position: node.x,
            start: node.y,
            end: node.y + node.size.height
        });
        guides.push({
            id: `guide-${guideId++}`,
            type: 'vertical',
            position: node.x + node.size.width / 2,
            start: node.y,
            end: node.y + node.size.height
        });
        guides.push({
            id: `guide-${guideId++}`,
            type: 'vertical',
            position: node.x + node.size.width,
            start: node.y,
            end: node.y + node.size.height
        });

        // Horizontal guides (top, center, bottom edges)
        guides.push({
            id: `guide-${guideId++}`,
            type: 'horizontal',
            position: node.y,
            start: node.x,
            end: node.x + node.size.width
        });
        guides.push({
            id: `guide-${guideId++}`,
            type: 'horizontal',
            position: node.y + node.size.height / 2,
            start: node.x,
            end: node.x + node.size.width
        });
        guides.push({
            id: `guide-${guideId++}`,
            type: 'horizontal',
            position: node.y + node.size.height,
            start: node.x,
            end: node.x + node.size.width
        });
    });

    return guides;
}

/**
 * Snap position to nearest guides
 */
function snapPositionToGuides(
    position: { x: number; y: number },
    guides: Guide[],
    threshold: number
): { x: number; y: number } {
    let snappedX = position.x;
    let snappedY = position.y;
    let minXDistance = threshold;
    let minYDistance = threshold;

    guides.forEach(guide => {
        if (guide.type === 'vertical') {
            const distance = Math.abs(position.x - guide.position);
            if (distance < minXDistance) {
                minXDistance = distance;
                snappedX = guide.position;
            }
        } else {
            const distance = Math.abs(position.y - guide.position);
            if (distance < minYDistance) {
                minYDistance = distance;
                snappedY = guide.position;
            }
        }
    });

    return { x: snappedX, y: snappedY };
}
