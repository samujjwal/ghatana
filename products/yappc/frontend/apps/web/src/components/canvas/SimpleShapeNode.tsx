/**
 * Simple Shape Node
 * Basic shapes (rectangle, circle, diamond) for canvas
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface SimpleShapeData {
    shape: 'rectangle' | 'circle' | 'diamond';
    color?: string;
    label?: string;
}

export const SimpleShapeNode = React.memo(({ data, selected }: NodeProps<SimpleShapeData>) => {
    const { shape = 'rectangle', color = '#e3f2fd', label = '' } = data as SimpleShapeData;

    const renderShape = () => {
        const baseStyles = {
            backgroundColor: color,
            border: selected ? '2px solid #2196f3' : '2px solid #90caf9',
            cursor: 'move',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '14px',
            color: '#1976d2',
            fontWeight: 'bold'
        };

        switch (shape) {
            case 'circle':
                return (
                    <Box
                        style={{
                            ...baseStyles,
                            width: 100,
                            height: 100,
                            borderRadius: '50%',
                        }}
                    >
                        {label}
                    </Box>
                );
            case 'diamond':
                return (
                    <Box
                        style={{
                            ...baseStyles,
                            width: 80,
                            height: 80,
                            transform: 'rotate(45deg)',
                        }}
                        className="[&>*]:rotate-[-45deg]"
                    >
                        {label}
                    </Box>
                );
            default: // rectangle
                return (
                    <Box
                        style={{
                            ...baseStyles,
                            width: 120,
                            height: 80,
                            borderRadius: 8,
                        }}
                    >
                        {label}
                    </Box>
                );
        }
    };

    return (
        <Box className="relative">
            <Handle type="target" position={Position.Top} />
            <Handle type="target" position={Position.Left} />
            {renderShape()}
            <Handle type="source" position={Position.Right} />
            <Handle type="source" position={Position.Bottom} />
        </Box>
    );
});

SimpleShapeNode.displayName = 'SimpleShapeNode';
