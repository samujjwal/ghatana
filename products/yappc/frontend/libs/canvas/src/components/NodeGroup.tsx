/**
 * Node Group Component
 *
 * Visual grouping system for organizing related nodes on the canvas.
 * Implements Journey 1.1 (PM Handoff Workflow) with status indicators.
 *
 * Features:
 * - Group multiple nodes with visual container
 * - Status-based border colors (Orange=Pending, Green=Ready)
 * - Editable group labels
 * - Expand/collapse functionality
 * - Right-click context menu for status changes
 *
 * @doc.type component
 * @doc.purpose Visual node grouping with status workflow
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import { Surface as Paper, Box, Typography, IconButton, TextField, Chip, Badge, Tooltip, Menu, MenuItem, Divider } from '@ghatana/ui';
import { Users as GroupIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Pencil as EditIcon, MoreVertical as MoreIcon, CheckCircle as ReadyIcon, Hourglass as PendingIcon, HelpCircle as UnknownIcon } from 'lucide-react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export type GroupStatus = 'unknown' | 'pending' | 'ready' | 'inProgress' | 'blocked' | 'completed';

export interface NodeGroupData {
    label: string;
    description?: string;
    status: GroupStatus;
    children?: string[]; // Node IDs contained in this group
    collapsed?: boolean;
    assignee?: string;
    tags?: string[];
}

// ============================================================================
// STATUS CONFIGURATION
// ============================================================================

const STATUS_CONFIG: Record<GroupStatus, {
    color: string;
    borderColor: string;
    label: string;
    icon: React.ReactElement;
}> = {
    unknown: {
        color: '#9e9e9e',
        borderColor: '#757575',
        label: 'Unknown',
        icon: <UnknownIcon />,
    },
    pending: {
        color: '#ff9800',
        borderColor: '#f57c00',
        label: 'Pending Review',
        icon: <PendingIcon />,
    },
    ready: {
        color: '#4caf50',
        borderColor: '#388e3c',
        label: 'Ready for Dev',
        icon: <ReadyIcon />,
    },
    inProgress: {
        color: '#2196f3',
        borderColor: '#1976d2',
        label: 'In Progress',
        icon: <PendingIcon />,
    },
    blocked: {
        color: '#f44336',
        borderColor: '#d32f2f',
        label: 'Blocked',
        icon: <PendingIcon />,
    },
    completed: {
        color: '#9c27b0',
        borderColor: '#7b1fa2',
        label: 'Completed',
        icon: <ReadyIcon />,
    },
};

// ============================================================================
// NODE GROUP COMPONENT
// ============================================================================

export const NodeGroup = memo<NodeProps<NodeGroupData>>(({ id, data, selected }) => {
    const [expanded, setExpanded] = useState(!data.collapsed);
    const [isEditingLabel, setIsEditingLabel] = useState(false);
    const [label, setLabel] = useState(data.label);
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    const statusConfig = STATUS_CONFIG[data.status] || STATUS_CONFIG.unknown;

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    const handleLabelSave = useCallback(() => {
        setIsEditingLabel(false);
        // NOTE: Update node data in React Flow store
        // Example: updateNodeData(id, { label });
    }, [label, id]);

    const handleLabelKeyPress = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            handleLabelSave();
        } else if (e.key === 'Escape') {
            setLabel(data.label);
            setIsEditingLabel(false);
        }
    }, [handleLabelSave, data.label]);

    const handleContextMenuOpen = useCallback((event: React.MouseEvent<HTMLElement>) => {
        event.preventDefault();
        setAnchorEl(event.currentTarget);
    }, []);

    const handleContextMenuClose = useCallback(() => {
        setAnchorEl(null);
    }, []);

    const handleStatusChange = useCallback((newStatus: GroupStatus) => {
        handleContextMenuClose();
        // NOTE: Update node data in React Flow store
        // Example: updateNodeData(id, { status: newStatus });
    }, [id, handleContextMenuClose]);

    // ========================================================================
    // RENDER
    // ========================================================================

    return (
        <>
            <Paper
                elevation={selected ? 8 : 2}
                onContextMenu={handleContextMenuOpen}
                className="w-full h-full min-w-[300px] min-h-[150px] border-[3px] p-4 relative cursor-move transition-all duration-200 hover:shadow-lg border-dashed" style={{ borderColor: statusConfig.borderColor, backgroundColor: selected ? 'rgba(0,0,0,0.08)' : 'rgba(0,0,0,0.04)' }} >
                {/* Header Section */}
                <Box
                    display="flex"
                    alignItems="center"
                    justifyContent="space-between"
                    mb={1}
                    pb={1}
                    borderBottom={1}
                    borderColor="divider"
                >
                    {/* Left: Icon + Label + Badge */}
                    <Box display="flex" alignItems="center" gap={1} flex={1}>
                        <GroupIcon style={{ color: statusConfig.color }} />

                        {isEditingLabel ? (
                            <TextField
                                value={label}
                                onChange={(e) => setLabel(e.target.value)}
                                onBlur={handleLabelSave}
                                onKeyDown={handleLabelKeyPress}
                                autoFocus
                                size="sm"
                                variant="standard"
                                className="flex-1"
                            />
                        ) : (
                            <Typography
                                as="h6"
                                className="flex-1 font-semibold text-gray-900 dark:text-gray-100"
                            >
                                {label}
                            </Typography>
                        )}

                        <Badge badgeContent={data.children?.length || 0} tone="primary">
                            <GroupIcon size={16} />
                        </Badge>
                    </Box>

                    {/* Right: Actions */}
                    <Box display="flex" alignItems="center" gap={0.5}>
                        <Tooltip title="Edit Label">
                            <IconButton
                                size="sm"
                                onClick={() => setIsEditingLabel(true)}
                            >
                                <EditIcon size={16} />
                            </IconButton>
                        </Tooltip>

                        <Tooltip title={expanded ? 'Collapse' : 'Expand'}>
                            <IconButton
                                size="sm"
                                onClick={() => setExpanded(!expanded)}
                            >
                                {expanded ? (
                                    <ExpandLessIcon size={16} />
                                ) : (
                                    <ExpandMoreIcon size={16} />
                                )}
                            </IconButton>
                        </Tooltip>

                        <Tooltip title="More Options">
                            <IconButton
                                size="sm"
                                onClick={handleContextMenuOpen}
                            >
                                <MoreIcon size={16} />
                            </IconButton>
                        </Tooltip>
                    </Box>
                </Box>

                {/* Status Indicator */}
                <Box mb={expanded ? 1 : 0}>
                    <Chip
                        icon={statusConfig.icon}
                        label={statusConfig.label}
                        size="sm"
                        style={{
                            backgroundColor: `${statusConfig.color}22`,
                            border: `1px solid ${statusConfig.borderColor}`,
                            color: statusConfig.borderColor,
                            fontWeight: 600,
                        }}
                    />
                </Box>

                {/* Expanded Content */}
                {expanded && (
                    <Box>
                        {data.description && (
                            <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                {data.description}
                            </Typography>
                        )}

                        {data.assignee && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block">
                                <strong>Assignee:</strong> {data.assignee}
                            </Typography>
                        )}

                        {data.children && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" mt={0.5}>
                                Contains {data.children.length} node{data.children.length !== 1 ? 's' : ''}
                            </Typography>
                        )}

                        {data.tags && data.tags.length > 0 && (
                            <Box display="flex" gap={0.5} mt={1} flexWrap="wrap">
                                {data.tags.map((tag, idx) => (
                                    <Chip
                                        key={idx}
                                        label={tag}
                                        size="sm"
                                        variant="outlined"
                                    />
                                ))}
                            </Box>
                        )}
                    </Box>
                )}

                {/* Connection Handles */}
                <Handle
                    type="target"
                    position={Position.Left}
                    style={{ left: -8,
                        background: statusConfig.borderColor,
                        width: 12,
                        height: 12 }}
                />
                <Handle
                    type="source"
                    position={Position.Right}
                    style={{
                        right: -8,
                        background: statusConfig.borderColor,
                        width: 12,
                        height: 12,
                    }}
                />
            </Paper>

            {/* Context Menu */}
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleContextMenuClose}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
            >
                <MenuItem disabled>
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Change Status
                    </Typography>
                </MenuItem>
                <Divider />

                {Object.entries(STATUS_CONFIG).map(([status, config]) => (
                    <MenuItem
                        key={status}
                        onClick={() => handleStatusChange(status as GroupStatus)}
                        selected={data.status === status}
                    >
                        <Box display="flex" alignItems="center" gap={1}>
                            <Box
                                component="span"
                                className="inline-flex text-base" >
                                {config.icon}
                            </Box>
                            <Typography as="p" className="text-sm">{config.label}</Typography>
                        </Box>
                    </MenuItem>
                ))}

                <Divider />

                <MenuItem onClick={handleContextMenuClose}>
                    <Typography as="p" className="text-sm" color="text.secondary">
                        Cancel
                    </Typography>
                </MenuItem>
            </Menu>
        </>
    );
});

NodeGroup.displayName = 'NodeGroup';

export default NodeGroup;
