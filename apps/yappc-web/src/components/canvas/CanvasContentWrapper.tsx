/**
 * CanvasContentWrapper
 *
 * A shared wrapper for all rich-content node interiors (Monaco, Mermaid,
 * MDX blocks, forms, etc.). Applies the three ReactFlow isolation classes
 * consistently across all content nodes, and stops pointer events from
 * bubbling to the canvas pan/drag layer.
 *
 * Without this wrapper, events inside embedded content (e.g. clicks inside
 * Monaco) trigger canvas panning, zooming, or node dragging.
 *
 * Usage:
 *   <CanvasContentWrapper>
 *     <MonacoEditor ... />
 *   </CanvasContentWrapper>
 *
 * Do NOT wrap the outer drag handle of a node — only the interactive content
 * interior. The node's title bar (drag handle) must remain outside.
 *
 * @doc.type component
 * @doc.purpose Isolates rich-content node interiors from canvas pointer events
 * @doc.layer product
 * @doc.pattern Containment
 */

import React from 'react';
import { cn } from '../../utils/cn';

export interface CanvasContentWrapperProps {
    children: React.ReactNode;
    /** Additional CSS classes for sizing/overflow control. */
    className?: string;
    /**
     * Set to true to disable pointer event isolation (e.g., for read-only
     * content nodes where you want canvas panning to work over them).
     * Default: false (isolation always on).
     */
    allowCanvasEvents?: boolean;
}

/**
 * CanvasContentWrapper — universal isolation boundary for node content.
 *
 * ReactFlow class responsibilities:
 *   nodrag   — prevents the canvas drag handler from treating mousedown
 *              inside this element as a node-drag initiation
 *   nopan    — prevents canvas panning when pointer moves over this area
 *   nowheel  — prevents scroll events from zooming the canvas
 *
 * Additionally stops pointer-down propagation so ReactFlow's selection
 * mechanism does not interfere with embedded text editors / form controls.
 */
export const CanvasContentWrapper: React.FC<CanvasContentWrapperProps> = ({
    children,
    className,
    allowCanvasEvents = false,
}) => {
    if (allowCanvasEvents) {
        return (
            <div className={cn('overflow-auto w-full h-full', className)}>
                {children}
            </div>
        );
    }

    return (
        <div
            className={cn('nodrag nopan nowheel overflow-auto w-full h-full', className)}
            onPointerDown={(e) => e.stopPropagation()}
            onWheel={(e) => e.stopPropagation()}
        >
            {children}
        </div>
    );
};

export default CanvasContentWrapper;
