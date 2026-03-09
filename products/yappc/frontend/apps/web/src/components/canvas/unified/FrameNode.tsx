/**
 * FrameNode - Container Node for Grouping
 * 
 * A frame is a container that can hold child nodes
 * and automatically resizes to fit its children
 * 
 * @doc.type component
 * @doc.purpose Container node for hierarchical grouping
 * @doc.layer components
 * @doc.pattern Component
 */

import React, { useState, useRef, useEffect } from 'react';
import { Box, Typography, IconButton } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { ChevronDown as ExpandIcon, ChevronRight as CollapseIcon, ZoomIn as ZoomInIcon } from 'lucide-react';

export interface FrameNodeData {
    title: string;
    childCount?: number;
    isCollapsed?: boolean;
    frameColor?: string;
    onTitleChange?: (title: string) => void;
    onToggleCollapse?: () => void;
    onZoomInto?: () => void;
}

/**
 * Frame Node Component
 */
export const FrameNode = React.memo(({ data, selected, id }: NodeProps<FrameNodeData>) => {
    const {
        title = 'Frame',
        childCount = 0,
        isCollapsed = false,
        frameColor = '#6366f1',
        onTitleChange,
        onToggleCollapse,
        onZoomInto
    } = data;

    const [isEditingTitle, setIsEditingTitle] = useState(false);
    const [localTitle, setLocalTitle] = useState(title);
    const inputRef = useRef<HTMLInputElement>(null);

    // Auto-focus when editing
    useEffect(() => {
        if (isEditingTitle && inputRef.current) {
            inputRef.current.focus();
            inputRef.current.select();
        }
    }, [isEditingTitle]);

    // Handle title double-click to edit
    const handleTitleDoubleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        setIsEditingTitle(true);
    };

    // Handle blur - save changes
    const handleBlur = () => {
        setIsEditingTitle(false);
        if (localTitle !== title && onTitleChange) {
            onTitleChange(localTitle);
        }
    };

    // Handle title change
    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setLocalTitle(e.target.value);
    };

    // Handle key press
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Escape') {
            setLocalTitle(title);
            setIsEditingTitle(false);
        } else if (e.key === 'Enter') {
            handleBlur();
        }
    };

    return (
        <Box
            className="w-full h-full border-[3px] border-dashed" style={{ borderColor: selected ? frameColor : `${frameColor, baining: borderStyle: 'dashed' */
        >
            {/* Frame Header */}
            <Box
                className="absolute flex items-center px-2 gap-1 top-[-32px] left-[0px] right-[0px] h-[28px] text-white shadow rounded-[4px 4px 0 0px]" >
                {/* Collapse/Expand Button */}
                <IconButton
                    size="small"
                    onClick={(e) => {
                        e.stopPropagation();
                        onToggleCollapse?.();
                    }}
                    className="p-1 text-white"
                >
                    {isCollapsed ? <CollapseIcon size={16} /> : <ExpandIcon size={16} />}
                </IconButton>

                {/* Title */}
                {isEditingTitle ? (
                    <input
                        ref={inputRef}
                        value={localTitle}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        onKeyDown={handleKeyDown}
                        style={{ flex: 1,
                            background: 'rgba(255,255,255,0.2)',
                            border: 'none',
                            color: 'white',
                            fontSize: '13px',
                            fontWeight: 600,
                            padding: '2px 6px',
                            borderRadius: '3px',
                            outline: 'none', backgroundColor: 'rgba(255' }}
                    />
                ) : (
                    <Typography
                        variant="caption"
                        fontWeight={600}
                        className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap cursor-text"
                        onDoubleClick={handleTitleDoubleClick}
                    >
                        {title}
                    </Typography>
                )}

                {/* Child Count Badge */}
                {childCount > 0 && (
                    <Box
                        className="px-2 py-0.5 text-[11px] font-semibold rounded-[10px]" >
                        {childCount}
                    </Box>
                )}

                {/* Zoom Into Button */}
                {childCount > 0 && (
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            onZoomInto?.();
                        }}
                        className="p-1 text-white"
                        title="Zoom into frame"
                    >
                        <ZoomInIcon size={16} />
                    </IconButton>
                )}
            </Box>

            {/* Frame Content Area */}
            <Box
                className="flex-1 p-6 relative" style={{ display: isCollapsed ? 'none' : 'block', transform: 'translate(-50%' }}
            >
                {childCount === 0 && (
                    <Box
                        className="absolute text-center pointer-events-none top-[50%] left-[50%] text-gray-400 dark:text-gray-600" >
                        <Typography variant="body2" color="text.disabled">
                            Drag nodes here
                        </Typography>
                    </Box>
                )}
            </Box>

            {/* Collapsed State */}
            {isCollapsed && (
                <Box
                    className="p-4 flex items-center justify-center text-gray-400 dark:text-gray-600"
                >
                    <Typography variant="caption">
                        {childCount} {childCount === 1 ? 'item' : 'items'} collapsed
                    </Typography>
                </Box>
            )}

            {/* Connection Handles */}
            <Handle type="target" position={Position.Top} style={{ opacity: 0 }} />
            <Handle type="source" position={Position.Bottom} style={{ opacity: 0 }} />
        </Box>
    );
});

FrameNode.displayName = 'FrameNode';
