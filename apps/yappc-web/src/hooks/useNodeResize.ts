/**
 * useNodeResize - Enhanced Node Resizing Hook
 * 
 * Provides advanced resize interactions with 8 handles, aspect ratio lock,
 * center resize, and minimum size constraints
 * 
 * @doc.type hook
 * @doc.purpose Advanced node resizing with handles
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback, useState, useRef } from 'react';
import type { HierarchicalNode } from '../lib/canvas/HierarchyManager';

export type ResizeHandle = 'tl' | 'tr' | 'bl' | 'br' | 't' | 'r' | 'b' | 'l';

export interface ResizeOptions {
    minWidth?: number;
    minHeight?: number;
    maxWidth?: number;
    maxHeight?: number;
    maintainAspectRatio?: boolean;
    resizeFromCenter?: boolean;
    snapToGrid?: boolean;
    gridSize?: number;
}

export interface UseNodeResizeReturn {
    isResizing: boolean;
    resizingNodeId: string | null;
    activeHandle: ResizeHandle | null;
    startResize: (nodeId: string, handle: ResizeHandle, event: MouseEvent) => void;
    updateResize: (event: MouseEvent) => void;
    endResize: () => void;
    cancelResize: () => void;
}

export function useNodeResize(
    nodes: HierarchicalNode[],
    onUpdate: (nodeId: string, updates: Partial<HierarchicalNode>) => void,
    options: ResizeOptions = {}
): UseNodeResizeReturn {
    const {
        minWidth = 50,
        minHeight = 50,
        maxWidth = 2000,
        maxHeight = 2000,
        maintainAspectRatio = false,
        resizeFromCenter = false,
        snapToGrid = false,
        gridSize = 20
    } = options;

    const [isResizing, setIsResizing] = useState(false);
    const [resizingNodeId, setResizingNodeId] = useState<string | null>(null);
    const [activeHandle, setActiveHandle] = useState<ResizeHandle | null>(null);

    const resizeStartPos = useRef<{ x: number; y: number } | null>(null);
    const initialNodeState = useRef<{
        x: number;
        y: number;
        width: number;
        height: number;
        aspectRatio: number;
    } | null>(null);

    /**
     * Start resizing a node
     */
    const startResize = useCallback((nodeId: string, handle: ResizeHandle, event: MouseEvent) => {
        const node = nodes.find(n => n.id === nodeId);
        if (!node) return;

        resizeStartPos.current = { x: event.clientX, y: event.clientY };
        initialNodeState.current = {
            x: node.x,
            y: node.y,
            width: node.size.width,
            height: node.size.height,
            aspectRatio: node.size.width / node.size.height
        };

        setIsResizing(true);
        setResizingNodeId(nodeId);
        setActiveHandle(handle);

        // Prevent text selection during resize
        event.preventDefault();
    }, [nodes]);

    /**
     * Update resize based on mouse movement
     */
    const updateResize = useCallback((event: MouseEvent) => {
        if (!isResizing || !resizingNodeId || !activeHandle || !resizeStartPos.current || !initialNodeState.current) {
            return;
        }

        const deltaX = event.clientX - resizeStartPos.current.x;
        const deltaY = event.clientY - resizeStartPos.current.y;

        let newX = initialNodeState.current.x;
        let newY = initialNodeState.current.y;
        let newWidth = initialNodeState.current.width;
        let newHeight = initialNodeState.current.height;

        // Check for modifier keys
        const shouldMaintainAspectRatio = maintainAspectRatio || event.shiftKey;
        const shouldResizeFromCenter = resizeFromCenter || event.altKey;

        // Calculate new dimensions based on handle
        switch (activeHandle) {
            case 'tl': // Top-left
                newX = initialNodeState.current.x + deltaX;
                newY = initialNodeState.current.y + deltaY;
                newWidth = initialNodeState.current.width - deltaX;
                newHeight = initialNodeState.current.height - deltaY;
                break;
            case 'tr': // Top-right
                newY = initialNodeState.current.y + deltaY;
                newWidth = initialNodeState.current.width + deltaX;
                newHeight = initialNodeState.current.height - deltaY;
                break;
            case 'bl': // Bottom-left
                newX = initialNodeState.current.x + deltaX;
                newWidth = initialNodeState.current.width - deltaX;
                newHeight = initialNodeState.current.height + deltaY;
                break;
            case 'br': // Bottom-right
                newWidth = initialNodeState.current.width + deltaX;
                newHeight = initialNodeState.current.height + deltaY;
                break;
            case 't': // Top edge
                newY = initialNodeState.current.y + deltaY;
                newHeight = initialNodeState.current.height - deltaY;
                if (shouldMaintainAspectRatio) {
                    newWidth = newHeight * initialNodeState.current.aspectRatio;
                    newX = initialNodeState.current.x - (newWidth - initialNodeState.current.width) / 2;
                }
                break;
            case 'r': // Right edge
                newWidth = initialNodeState.current.width + deltaX;
                if (shouldMaintainAspectRatio) {
                    newHeight = newWidth / initialNodeState.current.aspectRatio;
                    newY = initialNodeState.current.y - (newHeight - initialNodeState.current.height) / 2;
                }
                break;
            case 'b': // Bottom edge
                newHeight = initialNodeState.current.height + deltaY;
                if (shouldMaintainAspectRatio) {
                    newWidth = newHeight * initialNodeState.current.aspectRatio;
                    newX = initialNodeState.current.x - (newWidth - initialNodeState.current.width) / 2;
                }
                break;
            case 'l': // Left edge
                newX = initialNodeState.current.x + deltaX;
                newWidth = initialNodeState.current.width - deltaX;
                if (shouldMaintainAspectRatio) {
                    newHeight = newWidth / initialNodeState.current.aspectRatio;
                    newY = initialNodeState.current.y - (newHeight - initialNodeState.current.height) / 2;
                }
                break;
        }

        // Apply minimum and maximum constraints
        newWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));
        newHeight = Math.max(minHeight, Math.min(maxHeight, newHeight));

        // Maintain aspect ratio if needed
        if (shouldMaintainAspectRatio) {
            const aspectRatio = initialNodeState.current.aspectRatio;
            if (newWidth / aspectRatio > maxHeight) {
                newWidth = maxHeight * aspectRatio;
            }
            if (newHeight * aspectRatio > maxWidth) {
                newHeight = maxWidth / aspectRatio;
            }

            // Recalculate based on which dimension hit the limit
            if (['tl', 'tr', 'bl', 'br'].includes(activeHandle)) {
                newHeight = newWidth / aspectRatio;
            }
        }

        // Resize from center
        if (shouldResizeFromCenter) {
            const widthDiff = newWidth - initialNodeState.current.width;
            const heightDiff = newHeight - initialNodeState.current.height;
            newX = initialNodeState.current.x - widthDiff / 2;
            newY = initialNodeState.current.y - heightDiff / 2;
        }

        // Snap to grid
        if (snapToGrid) {
            newWidth = Math.round(newWidth / gridSize) * gridSize;
            newHeight = Math.round(newHeight / gridSize) * gridSize;
            newX = Math.round(newX / gridSize) * gridSize;
            newY = Math.round(newY / gridSize) * gridSize;
        }

        // Update node
        onUpdate(resizingNodeId, {
            x: newX,
            y: newY,
            size: { width: newWidth, height: newHeight }
        });
    }, [isResizing, resizingNodeId, activeHandle, onUpdate, minWidth, minHeight, maxWidth, maxHeight, maintainAspectRatio, resizeFromCenter, snapToGrid, gridSize]);

    /**
     * End resizing
     */
    const endResize = useCallback(() => {
        setIsResizing(false);
        setResizingNodeId(null);
        setActiveHandle(null);
        resizeStartPos.current = null;
        initialNodeState.current = null;
    }, []);

    /**
     * Cancel resizing (restore original state)
     */
    const cancelResize = useCallback(() => {
        if (resizingNodeId && initialNodeState.current) {
            onUpdate(resizingNodeId, {
                x: initialNodeState.current.x,
                y: initialNodeState.current.y,
                size: {
                    width: initialNodeState.current.width,
                    height: initialNodeState.current.height
                }
            });
        }
        endResize();
    }, [resizingNodeId, onUpdate, endResize]);

    return {
        isResizing,
        resizingNodeId,
        activeHandle,
        startResize,
        updateResize,
        endResize,
        cancelResize
    };
}
