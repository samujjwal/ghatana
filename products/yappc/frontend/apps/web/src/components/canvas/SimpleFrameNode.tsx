/**
 * Simple Frame Node
 * Basic frame for grouping canvas elements
 */

import React from 'react';
import { Box, Typography } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface SimpleFrameData {
    title: string;
    color?: string;
}

export const SimpleFrameNode = React.memo(({ data, selected }: NodeProps<SimpleFrameData>) => {
    const { title = 'Frame', color = '#e3f2fd' } = data as SimpleFrameData;

    return (
        <Box
            className="rounded-2xl p-6 min-w-[300px] min-h-[200px] cursor-move"
            style={{
                backgroundColor: color,
                border: selected ? '2px solid #2196f3' : '1px dashed #90caf9',
                boxShadow: selected ? '0 4px 8px rgba(0,0,0,0.2)' : '0 2px 4px rgba(0,0,0,0.1)',
            }}
        >
            <Handle type="target" position={Position.Top} />
            <Typography
                variant="h6"
                className="text-base font-bold text-[#1976d2] mb-4 border-b border-solid border-b-[#90caf9] pb-2"
            >
                {title}
            </Typography>
            <Box className="rounded-lg min-h-[120px]" >
                {/* Frame content area */}
            </Box>
            <Handle type="source" position={Position.Bottom} />
        </Box>
    );
});

SimpleFrameNode.displayName = 'SimpleFrameNode';
