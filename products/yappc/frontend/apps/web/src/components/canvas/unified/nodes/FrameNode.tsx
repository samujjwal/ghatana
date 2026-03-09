/**
 * FrameNode - AFFiNE-style frame/container
 * 
 * Frames are grouping containers that can:
 * - Contain other nodes
 * - Have a title/label
 * - Be used for organizing content
 * - Support presentation mode (slides)
 * 
 * @doc.type component
 * @doc.purpose Frame container for grouping canvas elements
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';
import { Box, Typography, IconButton } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

export interface FrameNodeData {
    title: string;
    description?: string;
    color?: string;
    backgroundColor?: string;
    backgroundOpacity?: number;
    // Frame settings
    showTitle?: boolean;
    collapsed?: boolean;
    locked?: boolean;
    // Presentation mode
    presentationIndex?: number;
    // Child nodes (for tracking)
    childNodeIds?: string[];
}

interface FrameNodeProps extends NodeProps {
    data: FrameNodeData;
}

function FrameNodeComponent({ data, selected, id }: FrameNodeProps) {
    const {
        title = 'Frame',
        description,
        color = '#6366f1',
        backgroundColor = '#f8fafc',
        backgroundOpacity = 0.5,
        showTitle = true,
        collapsed = false,
        locked = false,
        presentationIndex,
        childNodeIds = []
    } = data;

    const [isEditingTitle, setIsEditingTitle] = useState(false);
    const [editedTitle, setEditedTitle] = useState(title);

    const handleTitleDoubleClick = useCallback(() => {
        if (!locked) {
            setIsEditingTitle(true);
        }
    }, [locked]);

    const handleTitleBlur = useCallback(() => {
        setIsEditingTitle(false);
        // Would dispatch update action here
    }, []);

    const handleTitleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            setIsEditingTitle(false);
        } else if (e.key === 'Escape') {
            setEditedTitle(title);
            setIsEditingTitle(false);
        }
    }, [title]);

    return (
        <>
            {/* Node resizer */}
            <NodeResizer
                minWidth={200}
                minHeight={150}
                isVisible={selected && !locked}
                lineStyle={{
                    borderColor: color,
                    borderWidth: 2
                }}
                handleStyle={{
                    backgroundColor: color,
                    width: 10,
                    height: 10,
                    borderRadius: 2
                }}
            />

            <Box
                className="w-full h-full flex flex-col rounded-lg border-[2px]" style={{ borderColor: selected ? color : `${color, backgroundColor: 'rgba(255', backgroundColor: 'color' }}
            >
                {/* Title Bar */}
                {showTitle && (
                    <Box
                        className="flex items-center justify-between px-3 py-maining sx: bgcolor: color */
                    >
                        <Box className="flex items-center gap-2 flex-1">
                            {/* Presentation index badge */}
                            {presentationIndex !== undefined && (
                                <Box
                                    className="rounded px-1.5 py-0.5 font-semibold text-[0.7rem]" >
                                    #{presentationIndex}
                                </Box>
                            )}

                            {/* Title */}
                            {isEditingTitle ? (
                                <TextField
                                    value={editedTitle}
                                    onChange={(e) => setEditedTitle(e.target.value)}
                                    onBlur={handleTitleBlur}
                                    onKeyDown={handleTitleKeyDown}
                                    autoFocus
                                    size="small"
                                    InputProps={{
                                        style: { color: 'white', fontSize: '0.875rem', fontWeight: 600 },
                                    }}
                                    className="[&_.MuiOutlinedInput-notchedOutline]:border-white/30"
                                />
                            ) : (
                                <Typography
                                    variant="subtitle2"
                                    fontWeight={600}
                                    onDoubleClick={handleTitleDoubleClick}
                                    className="select-none" style={{ cursor: locked ? 'default' : 'text', backgroundColor: 'rgba(0' }}
                                >
                                    {title}
                                </Typography>
                            )}
                        </Box>

                        {/* Frame controls */}
                        <Box className="flex gap-1">
                            {locked && (
                                <Box className="text-xs opacity-[0.8]">🔒</Box>
                            )}
                            <IconButton
                                size="small"
                                className="text-white opacity-[0.8] hover:opacity-100"
                                title={collapsed ? 'Expand' : 'Collapse'}
                            >
                                {collapsed ? '▼' : '▲'}
                            </IconButton>
                        </Box>
                    </Box>
                )}

                {/* Content Area */}
                {!collapsed && (
                    <Box
                        className="flex-1 relative p-2 min-h-[100px]"
                    >
                        {/* Description (if no children) */}
                        {description && childNodeIds.length === 0 && (
                            <Typography
                                variant="body2"
                                color="text.secondary"
                                className="italic"
                            >
                                {description}
                            </Typography>
                        )}

                        {/* Child count indicator */}
                        {childNodeIds.length > 0 && (
                            <Box
                                className="absolute rounded px-2 py-0.5 bottom-[8px] right-[8px] text-[0.7rem] text-gray-500 dark:text-gray-400" >
                                {childNodeIds.length} items
                            </Box>
                        )}
                    </Box>
                )}

                {/* Collapsed indicator */}
                {collapsed && (
                    <Box
                        className="flex items-center justify-center py-2 text-xs text-gray-500 dark:text-gray-400"
                    >
                        {childNodeIds.length} items hidden
                    </Box>
                )}
            </Box>

            {/* Connection handles (hidden by default, show on hover) */}
            <Handle
                type="source"
                position={Position.Right}
                style={{
                    opacity: selected ? 1 : 0,
                    background: color,
                    width: 8,
                    height: 8,
                    border: '2px solid white',
                    transition: 'opacity 0.15s'
                }}
            />
            <Handle
                type="target"
                position={Position.Left}
                style={{
                    opacity: selected ? 1 : 0,
                    background: color,
                    width: 8,
                    height: 8,
                    border: '2px solid white',
                    transition: 'opacity 0.15s'
                }}
            />
        </>
    );
}

export const FrameNode = memo(FrameNodeComponent);
export default FrameNode;
