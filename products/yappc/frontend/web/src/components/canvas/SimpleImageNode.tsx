/**
 * Simple Image Node
 * Basic image element for canvas
 */

import React from 'react';
import { Box, Typography } from '@ghatana/design-system';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';

export interface SimpleImageData {
    [key: string]: unknown;
    url?: string;
    alt?: string;
    width?: number;
    height?: number;
}

type SimpleImageCanvasNode = Node<SimpleImageData, 'simple-image'>;

export const SimpleImageNode = React.memo(({ data, selected }: NodeProps<SimpleImageCanvasNode>) => {
    const { url = '', alt = 'Image', width = 200, height = 150 } = data;

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
                <img
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
