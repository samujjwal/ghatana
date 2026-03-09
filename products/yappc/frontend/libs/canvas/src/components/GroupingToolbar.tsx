/**
 * Grouping Toolbar Component
 *
 * Toolbar controls for node grouping operations.
 * Provides buttons for group/ungroup, status changes.
 *
 * @doc.type component
 * @doc.purpose Grouping controls toolbar
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useCallback, useMemo } from 'react';
import { Box, Button, ToggleButtonGroup as ButtonGroup, Tooltip, Menu, MenuItem, Divider, Typography } from '@ghatana/ui';
import { Group as GroupIcon, GitFork as UngroupIcon, Circle as StatusIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';
import type { GroupStatus } from '../components/NodeGroup';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface GroupingToolbarProps {
    selectedNodes: Node[];
    onGroup: (label?: string) => void;
    onUngroup: () => void;
    onChangeStatus: (status: GroupStatus) => void;
    disabled?: boolean;
}

// ============================================================================
// STATUS OPTIONS
// ============================================================================

const STATUS_OPTIONS: Array<{ value: GroupStatus; label: string; color: string }> = [
    { value: 'unknown', label: 'Unknown', color: '#9e9e9e' },
    { value: 'pending', label: 'Pending Review', color: '#ff9800' },
    { value: 'ready', label: 'Ready for Dev', color: '#4caf50' },
    { value: 'inProgress', label: 'In Progress', color: '#2196f3' },
    { value: 'blocked', label: 'Blocked', color: '#f44336' },
    { value: 'completed', label: 'Completed', color: '#9c27b0' },
];

// ============================================================================
// GROUPING TOOLBAR COMPONENT
// ============================================================================

export const GroupingToolbar: React.FC<GroupingToolbarProps> = ({
    selectedNodes,
    onGroup,
    onUngroup,
    onChangeStatus,
    disabled = false,
}) => {
    const [statusMenuAnchor, setStatusMenuAnchor] = React.useState<null | HTMLElement>(null);

    // ========================================================================
    // COMPUTED VALUES
    // ========================================================================

    const canGroup = useMemo(() => {
        return selectedNodes.length >= 2 && !selectedNodes.some((n) => n.type === 'nodeGroup');
    }, [selectedNodes]);

    const canUngroup = useMemo(() => {
        return selectedNodes.length === 1 && selectedNodes[0].type === 'nodeGroup';
    }, [selectedNodes]);

    const canChangeStatus = useMemo(() => {
        return selectedNodes.length === 1 && selectedNodes[0].type === 'nodeGroup';
    }, [selectedNodes]);

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    const handleGroup = useCallback(() => {
        if (!canGroup) return;
        onGroup();
    }, [canGroup, onGroup]);

    const handleUngroup = useCallback(() => {
        if (!canUngroup) return;
        onUngroup();
    }, [canUngroup, onUngroup]);

    const handleStatusMenuOpen = useCallback((event: React.MouseEvent<HTMLElement>) => {
        setStatusMenuAnchor(event.currentTarget);
    }, []);

    const handleStatusMenuClose = useCallback(() => {
        setStatusMenuAnchor(null);
    }, []);

    const handleStatusSelect = useCallback((status: GroupStatus) => {
        onChangeStatus(status);
        handleStatusMenuClose();
    }, [onChangeStatus, handleStatusMenuClose]);

    // ========================================================================
    // RENDER
    // ========================================================================

    return (
        <Box display="flex" gap={1} alignItems="center">
            <ButtonGroup variant="outlined" size="sm" disabled={disabled}>
                {/* Group Button */}
                <Tooltip
                    title={
                        canGroup
                            ? 'Group selected nodes'
                            : selectedNodes.length < 2
                                ? 'Select 2 or more nodes to group'
                                : 'Cannot group: selection contains a group'
                    }
                >
                    <span>
                        <Button
                            onClick={handleGroup}
                            disabled={!canGroup}
                            startIcon={<GroupIcon />}
                        >
                            Group
                        </Button>
                    </span>
                </Tooltip>

                {/* Ungroup Button */}
                <Tooltip
                    title={
                        canUngroup
                            ? 'Ungroup selected group'
                            : 'Select a group to ungroup'
                    }
                >
                    <span>
                        <Button
                            onClick={handleUngroup}
                            disabled={!canUngroup}
                            startIcon={<UngroupIcon />}
                        >
                            Ungroup
                        </Button>
                    </span>
                </Tooltip>
            </ButtonGroup>

            {/* Status Change Button */}
            <Tooltip
                title={
                    canChangeStatus
                        ? 'Change group status'
                        : 'Select a group to change status'
                }
            >
                <span>
                    <Button
                        variant="outlined"
                        size="sm"
                        onClick={handleStatusMenuOpen}
                        disabled={!canChangeStatus || disabled}
                        startIcon={<StatusIcon />}
                    >
                        Status
                    </Button>
                </span>
            </Tooltip>

            {/* Status Menu */}
            <Menu
                anchorEl={statusMenuAnchor}
                open={Boolean(statusMenuAnchor)}
                onClose={handleStatusMenuClose}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'left',
                }}
            >
                <MenuItem disabled>
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Change Group Status
                    </Typography>
                </MenuItem>
                <Divider />

                {STATUS_OPTIONS.map((option) => (
                    <MenuItem
                        key={option.value}
                        onClick={() => handleStatusSelect(option.value)}
                    >
                        <Box display="flex" alignItems="center" gap={1.5}>
                            <Box
                                className="rounded-full w-[16px] h-[16px]" style={{ backgroundColor: 'option.color' }} />
                            <Typography as="p" className="text-sm">{option.label}</Typography>
                        </Box>
                    </MenuItem>
                ))}
            </Menu>

            {/* Selection Info */}
            {selectedNodes.length > 0 && (
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="ml-2">
                    {selectedNodes.length} node{selectedNodes.length !== 1 ? 's' : ''} selected
                </Typography>
            )}
        </Box>
    );
};

export default GroupingToolbar;
