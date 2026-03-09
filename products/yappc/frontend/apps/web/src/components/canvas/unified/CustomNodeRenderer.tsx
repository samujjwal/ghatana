/**
 * Custom Node Renderer for Unified Canvas
 * 
 * Renders different node types with rich visual representations
 */

import React from 'react';
import { Box, Typography, IconButton } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { HierarchicalNode } from '../../../lib/canvas/HierarchyManager';
import { DrawingNode } from './DrawingNode';
import { StickyNoteNode } from './StickyNoteNode';
import { TextNode } from './TextNode';
import { CodeNode } from './CodeNode';
import { FrameNode } from './FrameNode';
import { RectangleNode } from './RectangleNode';
import { EllipseNode } from './EllipseNode';
import { LineNode } from './LineNode';
import { ArrowNode } from './ArrowNode';

export interface CustomNodeData {
    label: string;
    size: { width: number; height: number };
    isContainer: boolean;
    children: string[];
    nodeType?: 'task' | 'artifact' | 'component' | 'code' | 'text' | 'sticky' | 'shape';
    metadata?: Record<string, unknown>;
    [key: string]: unknown; // Allow additional properties
}

/**
 * Custom Node Component
 */
const CustomNodeComponent = ({ data, selected, id, isConnecting }: NodeProps<CustomNodeData>) => {
    const { label, size, isContainer, children, nodeType = 'shape' } = data;

    const nodeStyles: Record<string, { bgcolor: string; color: string; icon: string }> = {
        task: { bgcolor: '#e3f2fd', color: '#1976d2', icon: '✓' },
        artifact: { bgcolor: '#f3e5f5', color: '#7b1fa2', icon: '📦' },
        component: { bgcolor: '#e8f5e9', color: '#388e3c', icon: '🧩' },
        code: { bgcolor: '#fff3e0', color: '#f57c00', icon: '</>' },
        text: { bgcolor: '#ffffff', color: '#424242', icon: 'Aa' },
        sticky: { bgcolor: '#fff9c4', color: '#f57f17', icon: '📌' },
        shape: { bgcolor: '#fafafa', color: '#616161', icon: '▭' }
    };

    const style = nodeStyles[nodeType] || nodeStyles.shape;

    return (
        <Box
            role="button"
            tabIndex={0}
            aria-label={`${nodeType} node: ${label}`}
            aria-selected={selected}
            className="border-[2px] rounded flex flex-col overflow-hidden relative pointer-events-auto" style={{ width: size.width, height: size.height, borderColor: selected ? 'primary.main' : 'grey.300', boxShadow: selected ? 3 : 1 }}
        >
            {/* Node Header */}
            <Box
                className="px-3 py-2 flex items-center gap-2 border-gray-200 dark:border-gray-700" >
                <Typography variant="body2">{style.icon}</Typography>
                <Typography
                    variant="body2"
                    fontWeight={600}
                    color={style.color}
                    className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap"
                >
                    {label}
                </Typography>
                {isContainer && children.length > 0 && (
                    <Box
                        className="px-1.5 py-0.5 font-semibold bg-gray-200 dark:bg-gray-700 rounded-sm text-[10px]"
                    >
                        {children.length}
                    </Box>
                )}
            </Box>

            {/* Node Content */}
            <Box
                className="flex-1 p-3 flex items-center justify-center text-gray-500 dark:text-gray-400"
            >
                {isContainer && children.length > 0 ? (
                    <Typography variant="caption">
                        Double-click to zoom in ↓
                    </Typography>
                ) : (
                    <Typography variant="caption" color="text.disabled">
                        {nodeType} content
                    </Typography>
                )}
            </Box>

            {/* Connection Handles */}
            <Handle
                type="target"
                position={Position.Top}
                style={{ width: 12,
                    height: 12,
                    background: '#1976d2',
                    border: '2px solid white' }}
            />
            <Handle
                type="source"
                position={Position.Bottom}
                style={{ width: 12,
                    height: 12,
                    background: '#1976d2',
                    border: '2px solid white' }}
            />

            {/* Resize Handles (when selected) */}
            {selected && (
                <Box className="pointer-events-none">
                    <Box
                        className="absolute rounded-full top-[-4px] left-[-4px] w-[8px] h-[8px] bg-blue-600 border" style={{ borderColor: 'white', cursor: 'nw-resize' }}
                    />
                    <Box
                        className="absolute rounded-full top-[-4px] right-[-4px] w-[8px] h-[8px] bg-blue-600 border" style={{ borderColor: 'white', cursor: 'ne-resize' }}
                    />
                    <Box
                        className="absolute rounded-full bottom-[-4px] left-[-4px] w-[8px] h-[8px] bg-blue-600 border" style={{ borderColor: 'white', cursor: 'sw-resize' }}
                    />
                    <Box
                        className="absolute rounded-full bottom-[-4px] right-[-4px] w-[8px] h-[8px] bg-blue-600 border" style={{ borderColor: 'white', cursor: 'se-resize' }}
                    />

                    {/* Edge Handles */}
                    <Box
                        className="absolute rounded-full top-[-4px] left-[50%] w-[8px] h-[8px] bg-blue-600 border" style={{ transform: 'translateX(-50%)', borderColor: 'white', cursor: 'n-resize' }}
                    />
                    <Box
                        className="absolute rounded-full bottom-[-4px] left-[50%] w-[8px] h-[8px] bg-blue-600 border" style={{ transform: 'translateX(-50%)', borderColor: 'white', cursor: 's-resize' }}
                    />
                    <Box
                        className="absolute rounded-full left-[-4px] top-[50%] w-[8px] h-[8px] bg-blue-600 border" style={{ transform: 'translateY(-50%)', borderColor: 'white', cursor: 'w-resize' }}
                    />
                    <Box
                        className="absolute rounded-full right-[-4px] top-[50%] w-[8px] h-[8px] bg-blue-600 border" style={{ transform: 'translateY(-50%)', borderColor: 'white', cursor: 'e-resize' }}
                    />
                </Box>
            )}
        </Box>
    );
};

// Export memoized component for performance
export const CustomNodeRenderer = React.memo(CustomNodeComponent);

// Export node types for ReactFlow
export const nodeTypes = {
    default: CustomNodeRenderer,
    custom: CustomNodeRenderer,
    text: TextNode,
    code: CodeNode,
    drawing: DrawingNode,
    sticky: StickyNoteNode,
    rectangle: RectangleNode,
    ellipse: EllipseNode,
    line: LineNode,
    arrow: ArrowNode,
    frame: FrameNode,
};
