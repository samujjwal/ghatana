/**
 * Simple Image Node
 * Basic image element for canvas
 */

import React from 'react';
import { Box, Typography } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface SimpleImageData {
    url?: string;
    alt?: string;
    width?: number;
    height?: number;
}

export const SimpleImageNode = React.memo(({ data, selected }: NodeProps<SimpleImageData>) => {
    const { url = '', alt = 'Image', width = 200, height = 150 } = data as SimpleImageData;

    return (
        <Box
            className="bg-[#f5f5f5] rounded-2xl p-4 cursor-move"
            style={{
                border: selected ? '2px solid #2196f3' : '2px dashed #ccc',
                minWidth: width,
                minHeight: height,
            }}
        >
            <Handle type="target" position={Position.Top} />
            {url ? (
                <Box
                    component="img"
                    src={url}
                    alt={alt}
                    style={{ width, height, objectFit: 'cover', borderRadius: '0.5rem' }}
                />
            ) : (
                <Box
                    className="flex items-center justify-center rounded-lg"
                    style={{ width, height }}
                >
                    <Typography
                        variant="body2"
                        className="text-center text-[#999]"
                    >
                        {alt}
                    </Typography>
                </Box>
            )}
            <Handle type="source" position={Position.Bottom} />
        </Box>
    );
});

SimpleImageNode.displayName = 'SimpleImageNode';
