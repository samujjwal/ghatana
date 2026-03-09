/**
 * Drawing Node Renderer - Freehand Drawings
 * 
 * Renders freehand drawing strokes on canvas
 */

import React, { useCallback, useRef } from 'react';
import { Box } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { DrawingStroke } from '../../../lib/canvas/DrawingManager';

export interface DrawingNodeData {
    strokes: DrawingStroke[];
    editable: boolean;
}

/**
 * Drawing Node Component
 */
export const DrawingNode = React.memo(({ data, selected }: NodeProps<DrawingNodeData>) => {
    const { strokes = [], editable = false } = data;
    const svgRef = useRef<SVGSVGElement>(null);

    // Calculate bounds to offset stroke rendering
    const bounds = strokes.length > 0 ? (() => {
        let minX = Infinity, minY = Infinity;
        let maxX = -Infinity, maxY = -Infinity;
        for (const stroke of strokes) {
            for (const point of stroke.points) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
        }
        return { minX, minY };
    })() : { minX: 0, minY: 0 };

    // Render strokes as SVG paths
    const renderStrokes = useCallback(() => {
        return strokes.map(stroke => {
            if (stroke.points.length === 0) return null;

            // Offset points relative to node bounds
            const offsetPoints = stroke.points.map(p => ({
                x: p.x - bounds.minX,
                y: p.y - bounds.minY
            }));

            let path = `M ${offsetPoints[0].x} ${offsetPoints[0].y}`;
            for (let i = 1; i < offsetPoints.length; i++) {
                path += ` L ${offsetPoints[i].x} ${offsetPoints[i].y}`;
            }

            return (
                <path
                    key={stroke.id}
                    d={path}
                    stroke={stroke.color}
                    strokeWidth={stroke.width}
                    fill="none"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    opacity={stroke.tool === 'highlighter' ? 0.5 : 1}
                />
            );
        });
    }, [strokes, bounds]);

    return (
        <Box
            className="w-full h-full bg-transparent border-0 rounded-none overflow-visible relative" style={{ pointerEvents: editable ? 'auto' : 'none' }}
        >
            <svg
                ref={svgRef}
                width="100%"
                height="100%"
                style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    overflow: 'visible',
                    pointerEvents: editable ? 'auto' : 'none'
                }}
            >
                {renderStrokes()}
            </svg>

            {/* Handles for connections */}
            {selected && (
                <>
                    <Handle
                        type="source"
                        position={Position.Top}
                        style={{ opacity: 0 }}
                    />
                    <Handle
                        type="target"
                        position={Position.Bottom}
                        style={{ opacity: 0 }}
                    />
                </>
            )}
        </Box>
    );
});

DrawingNode.displayName = 'DrawingNode';
