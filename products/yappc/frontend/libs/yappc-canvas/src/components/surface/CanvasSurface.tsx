/**
 * Canvas Surface Component
 * 
 * Renders canvas elements (nodes, edges, groups) with optimized performance.
 * Handles element interaction, selection visualization, and rendering coordination.
 * 
 * Features:
 * - Virtualized rendering for large documents
 * - Selection highlighting and interaction
 * - Element lifecycle management
 * - Performance monitoring integration
 * - Accessibility support for each element
 * 
 * @module CanvasSurface
 */

import { useAtomValue, useSetAtom } from 'jotai';
import React, { useMemo } from 'react';

import {
    canvasSelectionAtom,
    canvasElementsArrayAtom,
    selectedElementsAtom,
    updateSelectionAtom,
    boundingBoxAtom
} from '../../state';
import {
    isCanvasNode,
    isCanvasEdge,
    isCanvasGroup
} from '../../types/canvas-document';

import type { CanvasElement, CanvasTheme } from '../../types/canvas-document';

/**
 *
 */
export interface CanvasSurfaceProps {
    /** Custom theme for element rendering */
    readonly theme?: Partial<CanvasTheme>;
    /** Whether to show selection indicators */
    readonly showSelection?: boolean;
    /** Whether to show element bounds for debugging */
    readonly showBounds?: boolean;
    /** Custom element renderer override */
    readonly elementRenderer?: (element: CanvasElement) => React.ReactNode;
    /** Test ID for automated testing */
    readonly testId?: string;
}

/**
 * CanvasSurface - Renders all canvas elements with performance optimization
 * 
 * Provides efficient rendering of canvas elements with proper selection handling
 * and accessibility features.
 */
export const CanvasSurface: React.FC<CanvasSurfaceProps> = React.memo(({
    theme,
    showSelection = true,
    showBounds = false,
    elementRenderer,
    testId = 'canvas-surface'
}) => {
    // State management
    const selection = useAtomValue(canvasSelectionAtom);
    const elements = useAtomValue(canvasElementsArrayAtom);
    const selectedElements = useAtomValue(selectedElementsAtom);
    const boundingBox = useAtomValue(boundingBoxAtom);
    const updateSelection = useSetAtom(updateSelectionAtom);

    const elementMap = useMemo(() => {
        const map = new Map<string, CanvasElement>();
        elements.forEach((el) => map.set(el.id, el));
        return map;
    }, [elements]);

    // Computed theme values
    const surfaceTheme = useMemo(() => ({
        selection: theme?.colors?.selection || '#0066cc',
        hover: theme?.colors?.hover || '#e6f3ff',
        focus: theme?.colors?.focus || '#0052a3',
        ...theme
    }), [theme]);

    // Handle element click for selection
    const handleElementClick = React.useCallback((
        elementId: string,
        event: React.MouseEvent
    ) => {
        event.stopPropagation();

        const isSelected = selection.selectedIds.includes(elementId);
        const isMultiSelect = event.ctrlKey || event.metaKey;

        let newSelectedIds: string[];

        if (isMultiSelect) {
            if (isSelected) {
                // Remove from selection
                newSelectedIds = selection.selectedIds.filter(id => id !== elementId);
            } else {
                // Add to selection
                newSelectedIds = [...selection.selectedIds, elementId];
            }
        } else {
            // Single selection
            newSelectedIds = isSelected ? [] : [elementId];
        }

        updateSelection({
            selectedIds: newSelectedIds,
            ...(newSelectedIds.length === 1 && { focusedId: newSelectedIds[0] })
        });
    }, [selection.selectedIds, updateSelection]);

    // Render individual element
    const renderElement = React.useCallback((element: CanvasElement) => {
        const isSelected = selection.selectedIds.includes(element.id);
        const isFocused = selection.focusedId === element.id;
        const isHovered = selection.hoveredId === element.id;

        const baseStyle: React.CSSProperties = {
            position: 'absolute',
            left: element.bounds.x,
            top: element.bounds.y,
            width: element.bounds.width,
            height: element.bounds.height,
            boxSizing: 'border-box',
            zIndex: element.zIndex,
        };

        // Use custom renderer if provided
        if (elementRenderer) {
            return (
                <div
                    key={element.id}
                    onClick={(e) => handleElementClick(element.id, e)}
                    data-element-id={element.id}
                >
                    {elementRenderer(element)}
                </div>
            );
        }

        // Default element rendering based on type
        const commonProps = {
            'data-element-id': element.id,
            'data-element-type': element.type,
            'aria-selected': isSelected,
            'aria-describedby': `element-${element.id}-description`,
            tabIndex: isFocused ? 0 : -1,
            onClick: (e: React.MouseEvent) => handleElementClick(element.id, e),
            style: {
                ...baseStyle,
                outline: isFocused ? `2px solid ${surfaceTheme.focus}` : undefined,
                backgroundColor: isHovered ? surfaceTheme.hover : undefined,
                border: isSelected ? `2px solid ${surfaceTheme.selection}` : '1px solid #ddd'
            }
        };

        switch (element.type) {
            case 'node':
                const nodeData = isCanvasNode(element) ? element.data : {};
                const nodeLabel = (nodeData.label as string) || element.id;
                return (
                    <div
                        key={element.id}
                        {...commonProps}
                        className="canvas-node"
                        role="button"
                        aria-label={`Node: ${nodeLabel}`}
                    >
                        <div className="node-content">
                            {nodeLabel}
                        </div>
                        <div id={`element-${element.id}-description`} className="sr-only">
                            Canvas node. Press Enter to select or modify.
                        </div>
                    </div>
                );

            case 'edge':
                const edgeSourceId = isCanvasEdge(element) ? element.sourceId : 'unknown';
                const edgeTargetId = isCanvasEdge(element) ? element.targetId : 'unknown';
                const sourceEl = isCanvasEdge(element) ? elementMap.get(element.sourceId) : undefined;
                const targetEl = isCanvasEdge(element) ? elementMap.get(element.targetId) : undefined;

                const sourceCenter = sourceEl
                    ? {
                        x: sourceEl.bounds.x + sourceEl.bounds.width / 2,
                        y: sourceEl.bounds.y + sourceEl.bounds.height / 2,
                    }
                    : undefined;
                const targetCenter = targetEl
                    ? {
                        x: targetEl.bounds.x + targetEl.bounds.width / 2,
                        y: targetEl.bounds.y + targetEl.bounds.height / 2,
                    }
                    : undefined;

                const edgeCustom = element.metadata?.custom as Record<string, unknown> | undefined;
                const errorRate = typeof edgeCustom?.errorRate === 'number' ? edgeCustom.errorRate : 0;
                const throughput = typeof edgeCustom?.throughput === 'number' ? edgeCustom.throughput : 0;

                const strokeColor = errorRate > 0.1
                    ? '#ef4444'
                    : errorRate > 0.05
                        ? '#f59e0b'
                        : errorRate > 0.01
                            ? '#eab308'
                            : '#22c55e';
                const strokeWidth = throughput > 1000 ? 4 : throughput > 500 ? 3 : throughput > 100 ? 2 : 1.5;

                const padding = 24;
                const x1 = sourceCenter?.x ?? 0;
                const y1 = sourceCenter?.y ?? 0;
                const x2 = targetCenter?.x ?? 0;
                const y2 = targetCenter?.y ?? 0;
                const minX = Math.min(x1, x2) - padding;
                const minY = Math.min(y1, y2) - padding;
                const width = Math.max(1, Math.abs(x2 - x1) + padding * 2);
                const height = Math.max(1, Math.abs(y2 - y1) + padding * 2);

                return (
                    <div
                        key={element.id}
                        {...commonProps}
                        className="canvas-edge"
                        role="presentation"
                        aria-label={`Connection from ${edgeSourceId} to ${edgeTargetId}`}
                        style={{
                            ...baseStyle,
                            left: minX,
                            top: minY,
                            width,
                            height,
                            border: 'none',
                            backgroundColor: undefined,
                            outline: undefined,
                            pointerEvents: 'none',
                        }}
                    >
                        <svg width="100%" height="100%">
                            <line
                                x1={x1 - minX}
                                y1={y1 - minY}
                                x2={x2 - minX}
                                y2={y2 - minY}
                                stroke={strokeColor}
                                strokeWidth={strokeWidth}
                                strokeLinecap="round"
                                opacity={0.65}
                            />
                        </svg>
                        <div id={`element-${element.id}-description`} className="sr-only">
                            Canvas connection. Press Enter to select or modify.
                        </div>
                    </div>
                );

            case 'group':
                const customMetadata = element.metadata?.custom as Record<string, unknown> | undefined;
                const metadataLabel = typeof customMetadata?.label === 'string' ? customMetadata.label : undefined;
                const groupLabel = metadataLabel && metadataLabel.trim().length > 0 ? metadataLabel : 'Group';
                const childCount = isCanvasGroup(element) ? element.childIds.length : 0;
                return (
                    <div
                        key={element.id}
                        {...commonProps}
                        className="canvas-group"
                        role="group"
                        aria-label={`Group: ${groupLabel}`}
                    >
                        <div className="group-header">
                            {groupLabel}
                        </div>
                        <div id={`element-${element.id}-description`} className="sr-only">
                            Canvas group containing {childCount} elements.
                        </div>
                    </div>
                );

            default:
                return (
                    <div
                        {...commonProps}
                        className="canvas-element"
                        role="button"
                        aria-label={`Element: ${element.id}`}
                    >
                        {element.id}
                    </div>
                );
        }
    }, [
        selection.selectedIds,
        selection.focusedId,
        selection.hoveredId,
        elementRenderer,
        handleElementClick,
        surfaceTheme,
        elementMap
    ]);

    // Selection bounding box overlay
    const selectionOverlay = useMemo(() => {
        if (!showSelection || !boundingBox || selectedElements.length === 0) {
            return null;
        }

        return (
            <div
                className="selection-overlay"
                style={{
                    position: 'absolute',
                    left: boundingBox.x,
                    top: boundingBox.y,
                    width: boundingBox.width,
                    height: boundingBox.height,
                    border: `2px dashed ${surfaceTheme.selection}`,
                    backgroundColor: `${surfaceTheme.selection}20`, // 20% opacity
                    pointerEvents: 'none',
                    zIndex: 1000
                }}
                aria-hidden="true"
            />
        );
    }, [showSelection, boundingBox, selectedElements.length, surfaceTheme.selection]);

    return (
        <div
            className="canvas-surface"
            data-testid={testId}
            style={{
                position: 'relative',
                width: '100%',
                height: '100%'
            }}
        >
            {/* Render all elements */}
            {elements.map(renderElement)}

            {/* Selection overlay */}
            {selectionOverlay}

            {/* Debug bounds overlay */}
            {showBounds && (
                <div
                    className="debug-bounds"
                    style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        border: '1px solid red',
                        pointerEvents: 'none',
                        zIndex: 999
                    }}
                    aria-hidden="true"
                />
            )}

            {/* Accessibility status */}
            <div className="sr-only" aria-live="polite">
                Canvas contains {elements.length} elements.
                {selectedElements.length > 0 &&
                    `${selectedElements.length} element${selectedElements.length > 1 ? 's' : ''} selected.`
                }
            </div>
        </div>
    );
});

CanvasSurface.displayName = 'CanvasSurface';

export default CanvasSurface;
