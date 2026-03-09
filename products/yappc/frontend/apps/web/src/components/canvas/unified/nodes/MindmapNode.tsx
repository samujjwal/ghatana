/**
 * MindmapNode - AFFiNE-style mind map node
 * 
 * Mind map nodes that can:
 * - Auto-layout in tree structure
 * - Support multiple layout directions
 * - Have styled branches and connections
 * - Expand/collapse children
 * 
 * @doc.type component
 * @doc.purpose Mind map node for hierarchical thinking
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Box, Typography, IconButton } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

export type MindmapStyle = 'default' | 'outline' | 'bubble' | 'org-chart';
export type MindmapLayoutDirection = 'right' | 'left' | 'both' | 'down';

export interface MindmapNodeData {
    text: string;
    style?: MindmapStyle;
    layoutDirection?: MindmapLayoutDirection;
    // Node styling
    color?: string;
    backgroundColor?: string;
    fontSize?: number;
    fontWeight?: number;
    // Hierarchy info
    level?: number;  // 0 = root, 1 = child, etc.
    isRoot?: boolean;
    parentId?: string;
    childIds?: string[];
    // State
    collapsed?: boolean;
    editing?: boolean;
}

interface MindmapNodeProps extends NodeProps {
    data: MindmapNodeData;
}

// Color schemes for different levels
const LEVEL_COLORS = [
    { bg: '#1976d2', text: '#ffffff' },  // Level 0 (root)
    { bg: '#e3f2fd', text: '#1565c0' },  // Level 1
    { bg: '#f3e5f5', text: '#7b1fa2' },  // Level 2
    { bg: '#fff3e0', text: '#ef6c00' },  // Level 3
    { bg: '#e8f5e9', text: '#2e7d32' },  // Level 4+
];

function getStyleForLevel(level: number, style?: MindmapStyle) {
    const colorIndex = Math.min(level, LEVEL_COLORS.length - 1);
    const colors = LEVEL_COLORS[colorIndex];

    switch (style) {
        case 'outline':
            return {
                backgroundColor: 'transparent',
                border: level === 0 ? `2px solid ${colors.bg}` : 'none',
                borderBottom: level > 0 ? `2px solid ${colors.bg}` : undefined,
                color: colors.bg,
                borderRadius: level === 0 ? 8 : 0
            };
        case 'bubble':
            return {
                backgroundColor: colors.bg,
                color: colors.text,
                borderRadius: 16,
                padding: '8px 16px'
            };
        case 'org-chart':
            return {
                backgroundColor: '#ffffff',
                border: `2px solid ${colors.bg}`,
                color: colors.bg,
                borderRadius: 4,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
            };
        default:
            return {
                backgroundColor: colors.bg,
                color: colors.text,
                borderRadius: level === 0 ? 8 : 4
            };
    }
}

function MindmapNodeComponent({ data, selected, id }: MindmapNodeProps) {
    const {
        text = 'New Idea',
        style = 'default',
        layoutDirection = 'right',
        level = 0,
        isRoot = false,
        childIds = [],
        collapsed = false,
        fontSize = 14,
        fontWeight
    } = data;

    const [isEditing, setIsEditing] = useState(false);
    const [editText, setEditText] = useState(text);

    const levelStyle = getStyleForLevel(level, style);
    const hasChildren = childIds.length > 0;
    const calculatedFontWeight = fontWeight || (level === 0 ? 700 : level === 1 ? 600 : 400);
    const calculatedFontSize = fontSize - (level * 1);

    const handleDoubleClick = useCallback(() => {
        setIsEditing(true);
    }, []);

    const handleBlur = useCallback(() => {
        setIsEditing(false);
        // Would dispatch update action here
    }, []);

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            setIsEditing(false);
        } else if (e.key === 'Escape') {
            setEditText(text);
            setIsEditing(false);
        } else if (e.key === 'Tab') {
            e.preventDefault();
            // Would trigger "add child" action
        }
    }, [text]);

    // Determine handle positions based on layout
    const getHandlePositions = () => {
        switch (layoutDirection) {
            case 'left':
                return { source: Position.Left, target: Position.Right };
            case 'down':
                return { source: Position.Bottom, target: Position.Top };
            case 'both':
                return { source: Position.Right, target: Position.Left };
            default:
                return { source: Position.Right, target: Position.Left };
        }
    };

    const handlePositions = getHandlePositions();

    return (
        <Box
            className="relative inline-flex items-center gap-1"
        >
            {/* Main node content */}
            <Box
                onDoubleClick={handleDoubleClick}
                className="max-w-[300px] transition-all duration-150" style={{ paddingLeft: level === 0 ? 16 : 12, paddingRight: level === 0 ? 16 : 12, paddingTop: level === 0 ? 12 : 6, paddingBottom: level === 0 ? 12 : 6, minWidth: level === 0 ? 120 : 80, cursor: isEditing ? 'text' : 'pointer', boxShadow: selected ? '0 0 0 2px rgba(25, 118, 210, 0.4)' : 'none' }}
            >
                {isEditing ? (
                    <TextField
                        value={editText}
                        onChange={(e) => setEditText(e.target.value)}
                        onBlur={handleBlur}
                        onKeyDown={handleKeyDown}
                        autoFocus
                        multiline
                        size="small"
                        variant="standard"
                        InputProps={{
                            style: {
                                color: levelStyle.color,
                                fontSize: calculatedFontSize,
                                fontWeight: calculatedFontWeight,
                            },
                        }}
                        className="[&_.MuiInput-underline::before]:border-b-0"
                    />
                ) : (
                    <Typography
                        className="break-words select-none" style={{ fontSize: calculatedFontSize, fontWeight: calculatedFontWeight, textAlign: level === 0 ? 'center' : 'left' }}
                    >
                        {text}
                    </Typography>
                )}
            </Box>

            {/* Expand/Collapse button for nodes with children */}
            {hasChildren && (
                <IconButton
                    size="small"
                    className="absolute w-[20px] h-[20px] bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 text-[0.7rem] hover:bg-gray-100 hover:dark:bg-gray-800" style={{ right: layoutDirection === 'left' ? 'auto' : -24, left: layoutDirection === 'left' ? -24 : 'auto' }}
                    title={collapsed ? 'Expand' : 'Collapse'}
                >
                    {collapsed ? `+${childIds.length}` : '−'}
                </IconButton>
            )}

            {/* Add child button (visible when selected) */}
            {selected && !isEditing && (
                <IconButton
                    size="small"
                    className="absolute w-[20px] h-[20px] bg-blue-600 text-white text-[0.8rem] hover:bg-blue-800" style={{ right: layoutDirection === 'left' ? 'auto' : hasChildren ? -48 : -24, left: layoutDirection === 'left' ? (hasChildren ? -48 : -24) : 'auto' }}
                    title="Add child (Tab)"
                >
                    +
                </IconButton>
            )}

            {/* Connection handles */}
            {!isRoot && (
                <Handle
                    type="target"
                    position={handlePositions.target}
                    style={{
                        background: levelStyle.backgroundColor || '#1976d2',
                        width: 8,
                        height: 8,
                        border: '2px solid white'
                    }}
                />
            )}

            <Handle
                type="source"
                position={handlePositions.source}
                style={{
                    background: levelStyle.backgroundColor || '#1976d2',
                    width: 8,
                    height: 8,
                    border: '2px solid white'
                }}
            />
        </Box>
    );
}

export const MindmapNode = memo(MindmapNodeComponent);
export default MindmapNode;
