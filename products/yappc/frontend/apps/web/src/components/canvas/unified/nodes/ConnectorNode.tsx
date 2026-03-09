/**
 * ConnectorNode - AFFiNE-style smart connector
 * 
 * Supports three connector modes:
 * - Straight: Direct line connection
 * - Elbow: Right-angle (orthogonal) connection
 * - Curve: Bezier curve connection
 * 
 * @doc.type component
 * @doc.purpose Connector element for linking canvas nodes
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useCallback, useMemo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Box } from '@ghatana/ui';
import type { ConnectorMode } from '@/state/atoms/unifiedCanvasAtom';

export interface ConnectorNodeData {
    label?: string;
    mode: ConnectorMode;
    sourceNodeId?: string;
    targetNodeId?: string;
    sourceHandle?: string;
    targetHandle?: string;
    color?: string;
    strokeWidth?: number;
    strokeStyle?: 'solid' | 'dashed' | 'dotted';
    arrowStart?: boolean;
    arrowEnd?: boolean;
    // Control points for curve mode
    controlPoints?: Array<{ x: number; y: number }>;
}

interface ConnectorNodeProps extends NodeProps {
    data: ConnectorNodeData;
}

/**
 * Calculate path based on connector mode
 */
function calculatePath(
    mode: ConnectorMode,
    start: { x: number; y: number },
    end: { x: number; y: number },
    controlPoints?: Array<{ x: number; y: number }>
): string {
    switch (mode) {
        case 'straight':
            return `M ${start.x} ${start.y} L ${end.x} ${end.y}`;

        case 'elbow': {
            // Create orthogonal (right-angle) path
            const midX = (start.x + end.x) / 2;
            return `M ${start.x} ${start.y} L ${midX} ${start.y} L ${midX} ${end.y} L ${end.x} ${end.y}`;
        }

        case 'curve': {
            // Create bezier curve
            if (controlPoints && controlPoints.length >= 2) {
                const [cp1, cp2] = controlPoints;
                return `M ${start.x} ${start.y} C ${cp1.x} ${cp1.y}, ${cp2.x} ${cp2.y}, ${end.x} ${end.y}`;
            }
            // Default curve with auto control points
            const dx = end.x - start.x;
            const cp1x = start.x + dx * 0.4;
            const cp2x = start.x + dx * 0.6;
            return `M ${start.x} ${start.y} C ${cp1x} ${start.y}, ${cp2x} ${end.y}, ${end.x} ${end.y}`;
        }

        default:
            return `M ${start.x} ${start.y} L ${end.x} ${end.y}`;
    }
}

/**
 * Get stroke dash array based on style
 */
function getStrokeDashArray(style?: 'solid' | 'dashed' | 'dotted'): string {
    switch (style) {
        case 'dashed': return '8,4';
        case 'dotted': return '2,2';
        default: return 'none';
    }
}

function ConnectorNodeComponent({ data, selected }: ConnectorNodeProps) {
    const {
        mode = 'straight',
        color = '#64748b',
        strokeWidth = 2,
        strokeStyle = 'solid',
        arrowStart = false,
        arrowEnd = true,
        label
    } = data;

    // For standalone rendering (when not connected yet)
    const width = 150;
    const height = 50;
    const start = { x: 10, y: height / 2 };
    const end = { x: width - 10, y: height / 2 };

    const path = useMemo(() => calculatePath(mode, start, end), [mode]);
    const dashArray = useMemo(() => getStrokeDashArray(strokeStyle), [strokeStyle]);

    // Arrow marker ID (unique per connector)
    const markerId = `arrow-${color.replace('#', '')}`;

    return (
        <Box
            style={{
                width,
                height,
                position: 'relative',
                cursor: selected ? 'move' : 'pointer',
            }}
        >
            {/* SVG Connector Preview */}
            <svg
                width={width}
                height={height}
                style={{ position: 'absolute',
                    top: 0,
                    left: 0,
                    overflow: 'visible', transform: 'translate(-50%' }}
            >
                <defs>
                    {/* Arrow marker */}
                    <marker
                        id={markerId}
                        markerWidth="10"
                        markerHeight="10"
                        refX="9"
                        refY="3"
                        orient="auto"
                        markerUnits="strokeWidth"
                    >
                        <path d="M0,0 L0,6 L9,3 z" fill={color} />
                    </marker>
                    {/* Start arrow marker (reversed) */}
                    <marker
                        id={`${markerId}-start`}
                        markerWidth="10"
                        markerHeight="10"
                        refX="0"
                        refY="3"
                        orient="auto"
                        markerUnits="strokeWidth"
                    >
                        <path d="M9,0 L9,6 L0,3 z" fill={color} />
                    </marker>
                </defs>

                <path
                    d={path}
                    stroke={color}
                    strokeWidth={strokeWidth}
                    strokeDasharray={dashArray}
                    fill="none"
                    markerEnd={arrowEnd ? `url(#${markerId})` : undefined}
                    markerStart={arrowStart ? `url(#${markerId}-start)` : undefined}
                    style={{
                        filter: selected ? 'drop-shadow(0 0 3px rgba(25, 118, 210, 0.5))' : 'none'
                    }}
                />
            </svg>

            {/* Label */}
            {label && (
                <Box
                    className="absolute px-2 py-0.5 text-xs whitespace-nowrap top-[50%] left-[50%] bg-white dark:bg-gray-900 rounded-sm text-gray-500 dark:text-gray-400" >
                    {label}
                </Box>
            )}

            {/* Connection handles */}
            <Handle
                type="source"
                position={Position.Right}
                style={{
                    background: color,
                    width: 8,
                    height: 8,
                    border: '2px solid white'
                }}
            />
            <Handle
                type="target"
                position={Position.Left}
                style={{
                    background: color,
                    width: 8,
                    height: 8,
                    border: '2px solid white'
                }}
            />
        </Box>
    );
}

export const ConnectorNode = memo(ConnectorNodeComponent);
export default ConnectorNode;
