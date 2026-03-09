/**
 * Layer Panel Component
 * 
 * Visual layer management panel showing z-order of elements
 * 
 * @doc.type component
 * @doc.purpose Layer visualization and management
 * @doc.layer component
 * @doc.pattern Panel Component
 */

import React, { useCallback } from 'react';
import {
  Box,
  Stack,
  IconButton,
  Typography,
  Tooltip,
  Surface as Paper,
} from '@ghatana/ui';
import { Eye as VisibilityIcon, EyeOff as VisibilityOffIcon, Lock as LockIcon, LockOpen as LockOpenIcon, GripVertical as DragIcon, ArrowUp as ArrowUpIcon, ArrowDown as ArrowDownIcon } from 'lucide-react';
import type { HierarchicalNode } from '../../../lib/canvas/HierarchyManager';

interface LayerPanelProps {
    nodes: HierarchicalNode[];
    selectedNodeIds: string[];
    onSelectNode: (nodeId: string, addToSelection?: boolean) => void;
    onBringForward: (nodeId: string) => void;
    onSendBackward: (nodeId: string) => void;
    onBringToFront: (nodeId: string) => void;
    onSendToBack: (nodeId: string) => void;
    onToggleVisibility?: (nodeId: string) => void;
    onToggleLock?: (nodeId: string) => void;
}

export const LayerPanel: React.FC<LayerPanelProps> = ({
    nodes,
    selectedNodeIds,
    onSelectNode,
    onBringForward,
    onSendBackward,
    onBringToFront,
    onSendToBack,
    onToggleVisibility,
    onToggleLock
}) => {
    // Sort nodes by z-index (highest first)
    const sortedNodes = React.useMemo(() => {
        return [...nodes].sort((a, b) => (b.zIndex || 0) - (a.zIndex || 0));
    }, [nodes]);

    const handleLayerClick = useCallback((nodeId: string, event: React.MouseEvent) => {
        const addToSelection = event.shiftKey || event.metaKey || event.ctrlKey;
        onSelectNode(nodeId, addToSelection);
    }, [onSelectNode]);

    return (
        <Paper
            elevation={2}
            className="h-full flex flex-col w-[280px] bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-700 border-l" >
            {/* Header */}
            <Box
                className="p-3 flex items-center justify-between border-gray-200 dark:border-gray-700 border-b" >
                <Typography variant="subtitle2" fontWeight={600}>
                    Layers ({nodes.length})
                </Typography>
            </Box>

            {/* Layer List */}
            <Box
                className="flex-1 overflow-auto p-2"
            >
                {sortedNodes.length === 0 ? (
                    <Box
                        className="p-6 text-center text-gray-500 dark:text-gray-400"
                    >
                        <Typography variant="body2">
                            No elements yet
                        </Typography>
                        <Typography variant="caption">
                            Add shapes, text, or other elements
                        </Typography>
                    </Box>
                ) : (
                    <Stack spacing={0.5}>
                        {sortedNodes.map((node, index) => {
                            const isSelected = selectedNodeIds.includes(node.id);
                            const isVisible = node.data?.visible !== false;
                            const isLocked = node.data?.locked === true;

                            return (
                                <Paper
                                    key={node.id}
                                    onClick={(e) => handleLayerClick(node.id, e)}
                                    className={`p-2 flex items-center gap-2 cursor-pointer border ${isSelected ? 'bg-blue-100 border-blue-600' : 'bg-gray-50 dark:bg-gray-950 border-gray-200 dark:border-gray-700'}`} style={{ opacity: isVisible ? 1 : 0.5 }}
                                >
                                    {/* Drag Handle */}
                                    <DragIcon className="cursor-grab text-base text-gray-500 dark:text-gray-400" />

                                    {/* Node Info */}
                                    <Box className="flex-1 min-w-0">
                                        <Typography
                                            variant="body2"
                                            noWrap
                                            style={{ fontWeight: isSelected ? 600 : 400 }}
                                        >
                                            {node.data?.label || node.type || `Layer ${index + 1}`}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            {node.type} • z:{node.zIndex || 0}
                                        </Typography>
                                    </Box>

                                    {/* Actions */}
                                    <Box className="flex gap-1">
                                        {/* Visibility Toggle */}
                                        {onToggleVisibility && (
                                            <Tooltip title={isVisible ? 'Hide' : 'Show'}>
                                                <IconButton
                                                    size="small"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        onToggleVisibility(node.id);
                                                    }}
                                                >
                                                    {isVisible ? (
                                                        <VisibilityIcon className="text-base" />
                                                    ) : (
                                                        <VisibilityOffIcon className="text-base" />
                                                    )}
                                                </IconButton>
                                            </Tooltip>
                                        )}

                                        {/* Lock Toggle */}
                                        {onToggleLock && (
                                            <Tooltip title={isLocked ? 'Unlock' : 'Lock'}>
                                                <IconButton
                                                    size="small"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        onToggleLock(node.id);
                                                    }}
                                                >
                                                    {isLocked ? (
                                                        <LockIcon className="text-base" />
                                                    ) : (
                                                        <LockOpenIcon className="text-base" />
                                                    )}
                                                </IconButton>
                                            </Tooltip>
                                        )}

                                        {/* Move Up */}
                                        <Tooltip title="Bring Forward">
                                            <IconButton
                                                size="small"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    onBringForward(node.id);
                                                }}
                                                disabled={index === 0}
                                            >
                                                <ArrowUpIcon className="text-base" />
                                            </IconButton>
                                        </Tooltip>

                                        {/* Move Down */}
                                        <Tooltip title="Send Backward">
                                            <IconButton
                                                size="small"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    onSendBackward(node.id);
                                                }}
                                                disabled={index === sortedNodes.length - 1}
                                            >
                                                <ArrowDownIcon className="text-base" />
                                            </IconButton>
                                        </Tooltip>
                                    </Box>
                                </Paper>
                            );
                        })}
                    </Stack>
                )}
            </Box>
        </Paper>
    );
};
