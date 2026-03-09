/**
 * Rectangle Node Renderer - Simple Rectangle Shape
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface RectangleNodeData {
    color?: string;
    strokeWidth?: number;
    fill?: string;
    fillOpacity?: number;
}

/**
 * Rectangle Node Component
 */
export const RectangleNode = React.memo(({ data, selected }: NodeProps<RectangleNodeData>) => {
    const {
        color = '#000000',
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

RectangleNode.displayName = 'RectangleNode';
