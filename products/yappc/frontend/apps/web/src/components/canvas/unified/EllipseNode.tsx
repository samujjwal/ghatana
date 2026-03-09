/**
 * Ellipse/Circle Node Renderer - Simple Circle Shape
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface EllipseNodeData {
    color?: string;
    strokeWidth?: number;
    fill?: string;
    fillOpacity?: number;
}

/**
 * Ellipse Node Component - Renders as a circle
 */
export const EllipseNode = React.memo(({ data, selected }: NodeProps<EllipseNodeData>) => {
    const {
        color = '#1976d2',
        strokeWidth = 2,
        fill = 'transparent',
        fillOpacity = 0
    } = data;

    return (
        <Box
            className="w-full h-full" style={{ border: `${strokeWidth }}
        >
            <Handle type="target" position={Position.Top} />
            <Handle type="source" position={Position.Bottom} />
            <Handle type="target" position={Position.Left} />
            <Handle type="source" position={Position.Right} />
        </Box>
    );
});

EllipseNode.displayName = 'EllipseNode';
