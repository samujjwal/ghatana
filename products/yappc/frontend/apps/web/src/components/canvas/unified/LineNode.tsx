/**
 * Line Node - Straight line connector
 * 
 * Renders a straight line between two points
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface LineNodeData {
    startPoint?: { x: number; y: number };
    endPoint?: { x: number; y: number };
    color?: string;
    strokeWidth?: number;
    strokeStyle?: 'solid' | 'dashed' | 'dotted';
}

/**
 * Line Node Component
 */
export const LineNode = React.memo(({ data, selected }: NodeProps<LineNodeData>) => {
    const {
        startPoint = { x: 0, y: 50 },
        endPoint = { x: 100, y: 50 },
        color = '#000000',
        strokeWidth = 2,
        strokeStyle = 'solid'
    } = data;

    // Calculate line dimensions
    const width = Math.abs(endPoint.x - startPoint.x);
    const height = Math.abs(endPoint.y - startPoint.y);
    const minX = Math.min(startPoint.x, endPoint.x);
    const minY = Math.min(startPoint.y, endPoint.y);

    // Convert to local coordinates
    const localStart = { x: startPoint.x - minX, y: startPoint.y - minY };
    const localEnd = { x: endPoint.x - minX, y: endPoint.y - minY };

    const dashArray = strokeStyle === 'dashed' ? '8,4' : strokeStyle === 'dotted' ? '2,2' : '';

    return (
        <Box
            className="w-full h-full relative pointer-events-auto"
        >
            <svg
                width="100%"
                height="100%"
                style={{
                    overflow: 'visible',
                    pointerEvents: 'auto'
                }}
            >
                <line
                    x1={localStart.x}
                    y1={localStart.y}
                    x2={localEnd.x}
                    y2={localEnd.y}
                    stroke={selected ? '#1976d2' : color}
                    strokeWidth={selected ? strokeWidth + 1 : strokeWidth}
                    strokeDasharray={dashArray}
                    strokeLinecap="round"
                />

                {/* Selection handles at endpoints */}
                {selected && (
                    <>
                        <circle
                            cx={localStart.x}
                            cy={localStart.y}
                            r={4}
                            fill="#1976d2"
                            stroke="white"
                            strokeWidth={2}
                        />
                        <circle
                            cx={localEnd.x}
                            cy={localEnd.y}
                            r={4}
                            fill="#1976d2"
                            stroke="white"
                            strokeWidth={2}
                        />
                    </>
                )}
            </svg>

            {/* Connection Handles */}
            <Handle
                type="target"
                position={Position.Left}
                style={{
                    width: 8,
                    height: 8,
                    background: '#1976d2',
                    border: '2px solid white',
                    opacity: selected ? 1 : 0
                }}
            />
            <Handle
                type="source"
                position={Position.Right}
                style={{
                    width: 8,
                    height: 8,
                    background: '#1976d2',
                    border: '2px solid white',
                    opacity: selected ? 1 : 0
                }}
            />
        </Box>
    );
});

LineNode.displayName = 'LineNode';
