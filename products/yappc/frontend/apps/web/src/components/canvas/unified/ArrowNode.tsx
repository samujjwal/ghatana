/**
 * Arrow Node - Directional arrow connector
 * 
 * Renders an arrow with arrowhead
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface ArrowNodeData {
    startPoint?: { x: number; y: number };
    endPoint?: { x: number; y: number };
    color?: string;
    strokeWidth?: number;
    arrowSize?: number;
    strokeStyle?: 'solid' | 'dashed' | 'dotted';
}

/**
 * Arrow Node Component
 */
export const ArrowNode = React.memo(({ data, selected }: NodeProps<ArrowNodeData>) => {
    const {
        startPoint = { x: 0, y: 50 },
        endPoint = { x: 100, y: 50 },
        color = '#000000',
        strokeWidth = 2,
        arrowSize = 10,
        strokeStyle = 'solid'
    } = data;

    // Calculate arrow dimensions
    const width = Math.abs(endPoint.x - startPoint.x);
    const height = Math.abs(endPoint.y - startPoint.y);
    const minX = Math.min(startPoint.x, endPoint.x);
    const minY = Math.min(startPoint.y, endPoint.y);

    // Convert to local coordinates
    const localStart = { x: startPoint.x - minX, y: startPoint.y - minY };
    const localEnd = { x: endPoint.x - minX, y: endPoint.y - minY };

    // Calculate arrowhead angle
    const angle = Math.atan2(localEnd.y - localStart.y, localEnd.x - localStart.x);
    const arrowAngle = Math.PI / 6; // 30 degrees

    // Arrowhead points
    const arrowPoint1 = {
        x: localEnd.x - arrowSize * Math.cos(angle - arrowAngle),
        y: localEnd.y - arrowSize * Math.sin(angle - arrowAngle)
    };
    const arrowPoint2 = {
        x: localEnd.x - arrowSize * Math.cos(angle + arrowAngle),
        y: localEnd.y - arrowSize * Math.sin(angle + arrowAngle)
    };

    const dashArray = strokeStyle === 'dashed' ? '8,4' : strokeStyle === 'dotted' ? '2,2' : '';
    const displayColor = selected ? '#1976d2' : color;
    const displayStrokeWidth = selected ? strokeWidth + 1 : strokeWidth;

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
                {/* Main line */}
                <line
                    x1={localStart.x}
                    y1={localStart.y}
                    x2={localEnd.x}
                    y2={localEnd.y}
                    stroke={displayColor}
                    strokeWidth={displayStrokeWidth}
                    strokeDasharray={dashArray}
                    strokeLinecap="round"
                />

                {/* Arrowhead */}
                <polygon
                    points={`${localEnd.x},${localEnd.y} ${arrowPoint1.x},${arrowPoint1.y} ${arrowPoint2.x},${arrowPoint2.y}`}
                    fill={displayColor}
                    stroke={displayColor}
                    strokeWidth={1}
                    strokeLinejoin="miter"
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

ArrowNode.displayName = 'ArrowNode';
