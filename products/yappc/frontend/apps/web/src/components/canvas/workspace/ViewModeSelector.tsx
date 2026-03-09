/**
 * View Mode Selector Component
 * 
 * Allows users to switch between different canvas view modes:
 * - All: Show all visible nodes
 * - My Work: Highlight user's assigned tasks, dim others
 * - Blockers: Highlight blocked nodes, dim unblocked
 * - Critical Path: Show dependency chain to blockers
 * 
 * From CANVAS_UX_DESIGN_SPEC.md Section 6.1:
 * - Task selector: Highlights assigned nodes with "My Task" halo
 * - Dims unrelated nodes in "My Work" mode
 * 
 * @doc.type component
 * @doc.purpose View mode switching for canvas
 * @doc.layer product
 * @doc.pattern Toolbar Control
 */

import React, { useCallback } from 'react';
import { useAtom } from 'jotai';
import { Box, ToggleButton, ToggleButtonGroup, Tooltip, Badge, Typography, Divider } from '@ghatana/ui';
import { LayoutGrid as AllIcon, User as MyWorkIcon, Ban as BlockersIcon, Activity as CriticalPathIcon, Filter as FilterIcon } from 'lucide-react';
import { viewModeAtom, type ViewModeConfig } from '../hooks/useComputedView';
import { nodesAtom, activePersonaAtom } from '../workspace/canvasAtoms';
import { useAtomValue } from 'jotai';

// ============================================================================
// Types
// ============================================================================

export type ViewMode = 'all' | 'my-work' | 'blockers' | 'critical-path';

export interface ViewModeSelectorProps {
    /** Optional override for view mode (controlled mode) */
    value?: ViewMode;
    /** Callback when view mode changes */
    onChange?: (mode: ViewMode) => void;
    /** Show badge counts for each mode */
    showBadges?: boolean;
    /** Compact mode (icons only) */
    compact?: boolean;
    /** Disabled state */
    disabled?: boolean;
}

// ============================================================================
// View Mode Definitions
// ============================================================================

interface ViewModeDefinition {
    mode: ViewMode;
    label: string;
    shortLabel: string;
    icon: React.ReactNode;
    tooltip: string;
    config: Omit<ViewModeConfig, 'mode'>;
}

const VIEW_MODES: ViewModeDefinition[] = [
    {
        mode: 'all',
        label: 'All Artifacts',
        shortLabel: 'All',
        icon: <AllIcon />,
        tooltip: 'Show all visible artifacts',
        config: {
            highlightBlockers: true,
            dimUnassigned: false,
            showCriticalPath: false,
        },
    },
    {
        mode: 'my-work',
        label: 'My Work',
        shortLabel: 'Mine',
        icon: <MyWorkIcon />,
        tooltip: 'Focus on tasks assigned to me',
        config: {
            highlightBlockers: true,
            dimUnassigned: true,
            showCriticalPath: false,
        },
    },
    {
        mode: 'blockers',
        label: 'Blockers',
        shortLabel: 'Blocked',
        icon: <BlockersIcon />,
        tooltip: 'Highlight blocked items',
        config: {
            highlightBlockers: true,
            dimUnassigned: false,
            showCriticalPath: false,
        },
    },
    {
        mode: 'critical-path',
        label: 'Critical Path',
        shortLabel: 'Path',
        icon: <CriticalPathIcon />,
        tooltip: 'Show dependency chain to blockers',
        config: {
            highlightBlockers: true,
            dimUnassigned: false,
            showCriticalPath: true,
        },
    },
];

// ============================================================================
// Component
// ============================================================================

export const ViewModeSelector: React.FC<ViewModeSelectorProps> = ({
    value,
    onChange,
    showBadges = true,
    compact = false,
    disabled = false,
}) => {
    const [viewModeConfig, setViewModeConfig] = useAtom(viewModeAtom);
    const nodes = useAtomValue(nodesAtom);

    // Calculate badge counts
    const badgeCounts = React.useMemo(() => {
        const myTasks = nodes.filter(n => n.data?.assignedTo === 'current-user').length; // Would use real user ID
        const blockers = nodes.filter(n => n.data?.status === 'blocked' || (n.data?.blockerCount ?? 0) > 0).length;

        return {
            all: nodes.length,
            'my-work': myTasks,
            blockers: blockers,
            'critical-path': blockers, // Same as blockers for now
        };
    }, [nodes]);

    // Get current mode from prop or atom
    const currentMode = value ?? viewModeConfig.mode;

    // Handle mode change
    const handleModeChange = useCallback(
        (_event: React.MouseEvent<HTMLElement>, newMode: ViewMode | null) => {
            if (newMode === null) return; // Don't allow deselection

            // Find the mode definition
            const modeDef = VIEW_MODES.find(m => m.mode === newMode);
            if (!modeDef) return;

            // Update atom with new config
            setViewModeConfig({
                mode: newMode,
                ...modeDef.config,
            });

            // Call onChange if provided
            onChange?.(newMode);
        },
        [setViewModeConfig, onChange]
    );

    return (
        <Box
            className="flex items-center gap-2"
        >
            {!compact && (
                <>
                    <FilterIcon size={16} color="action" />
                    <Typography as="p" className="text-sm" color="text.secondary" className="mr-2">
                        View:
                    </Typography>
                </>
            )}

            <ToggleButtonGroup
                value={currentMode}
                exclusive
                onChange={handleModeChange}
                size="sm"
                disabled={disabled}
                className={`[&_.MuiToggleButton-root]:normal-case ${compact ? '[&_.MuiToggleButton-root]:px-2' : '[&_.MuiToggleButton-root]:px-4'} [&_.MuiToggleButton-root]:py-1`}
            >
                {VIEW_MODES.map((modeDef) => (
                    <Tooltip key={modeDef.mode} title={modeDef.tooltip} arrow>
                        <ToggleButton
                            value={modeDef.mode}
                            className="gap-1"
                        >
                            {showBadges && badgeCounts[modeDef.mode] > 0 ? (
                                <Badge
                                    badgeContent={badgeCounts[modeDef.mode]}
                                    color={modeDef.mode === 'blockers' ? 'error' : 'primary'}
                                    max={99}
                                    className="[&_.MuiBadge-badge]:text-[10px] [&_.MuiBadge-badge]:min-w-[16px] [&_.MuiBadge-badge]:h-4"
                                >
                                    {modeDef.icon}
                                </Badge>
                            ) : (
                                modeDef.icon
                            )}
                            {!compact && (
                                <Typography as="p" className="text-sm" component="span">
                                    {modeDef.shortLabel}
                                </Typography>
                            )}
                        </ToggleButton>
                    </Tooltip>
                ))}
            </ToggleButtonGroup>
        </Box>
    );
};

/**
 * Compact version for toolbar use
 */
export const ViewModeSelectorCompact: React.FC<Omit<ViewModeSelectorProps, 'compact'>> = (props) => (
    <ViewModeSelector {...props} compact />
);

export default ViewModeSelector;
