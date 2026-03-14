/**
 * InheritanceLink Component
 * 
 * Custom edge component showing inheritance relationships between roles
 */

import { memo } from 'react';
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@xyflow/react';
import type { EdgeProps } from '@xyflow/react';

/**
 * InheritanceLink - Custom edge for role inheritance connections
 * 
 * Features:
 * - Smooth bezier curve connection
 * - "inherits" label on hover
 * - Consistent blue styling
 * - Animated option for dynamic visualization
 */
export const InheritanceLink = memo<EdgeProps>((props) => {
    const {
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        style = {},
        markerEnd,
    } = props;

    const [edgePath, labelX, labelY] = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });

    return (
        <>
            <BaseEdge
                path={edgePath}
                markerEnd={markerEnd}
                style={{
                    ...style,
                    stroke: '#3b82f6',
                    strokeWidth: 2,
                }}
            />
            <EdgeLabelRenderer>
                <div
                    style={{
                        position: 'absolute',
                        transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                        fontSize: 10,
                        pointerEvents: 'all',
                    }}
                    className="nodrag nopan bg-white dark:bg-neutral-800 px-2 py-1 rounded border border-blue-300 dark:border-blue-500 text-blue-600 dark:text-indigo-400 text-xs opacity-0 hover:opacity-100 transition-opacity"
                >
                    inherits
                </div>
            </EdgeLabelRenderer>
        </>
    );
});

InheritanceLink.displayName = 'InheritanceLink';
