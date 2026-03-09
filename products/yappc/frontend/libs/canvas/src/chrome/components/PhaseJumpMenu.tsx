/**
 * PhaseJumpMenu Component
 * 
 * Quick phase navigation menu with metadata
 * Click phase indicator to reveal menu
 * 
 * Features:
 * - All 6 lifecycle phases
 * - Frame count per phase
 * - Completion % indicator
 * - Recent activity indicator
 * - Smooth pan+zoom animation to phase
 * - Phase health/status visualization
 * 
 * @doc.type component
 * @doc.purpose Quick phase navigation
 * @doc.layer components
 */

import {
  Box,
  Menu,
  Typography,
  LinearProgress,
  Chip,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useMemo } from 'react';

import { chromeCurrentPhaseAtom } from '../state/chrome-atoms';
import { CANVAS_TOKENS, LIFECYCLE_PHASES } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS } = CANVAS_TOKENS;

export interface PhaseStats {
    /** Phase ID */
    phase: keyof typeof LIFECYCLE_PHASES;

    /** Number of frames in phase */
    frameCount: number;

    /** Completion percentage (0-100) */
    completionPercent: number;

    /** Has recent activity (last 24h) */
    hasRecentActivity: boolean;

    /** Phase position in canvas coordinates */
    position: { x: number; y: number };
}

export interface PhaseJumpMenuProps {
    /** Anchor element for menu */
    anchorEl: HTMLElement | null;

    /** Callback when menu closes */
    onClose: () => void;

    /** Phase statistics */
    phaseStats: PhaseStats[];

    /** Callback when phase selected */
    onPhaseSelect: (phase: keyof typeof LIFECYCLE_PHASES, position: { x: number; y: number }) => void;

    /** Current canvas position */
    currentPosition?: { x: number; y: number };
}

/**
 * Get status color based on completion
 */
function getStatusColor(completionPercent: number): string {
    if (completionPercent >= 80) return COLORS.SUCCESS;
    if (completionPercent >= 50) return COLORS.WARNING;
    if (completionPercent > 0) return COLORS.INFO;
    return COLORS.NEUTRAL_400;
}

/**
 * Phase menu item
 */
function PhaseMenuItem({
    phase,
    stats,
    isActive,
    onClick,
}: {
    phase: keyof typeof LIFECYCLE_PHASES;
    stats: PhaseStats;
    isActive: boolean;
    onClick: () => void;
}) {
    const phaseConfig = LIFECYCLE_PHASES[phase];
    const statusColor = getStatusColor(stats.completionPercent);

    return (
        <MenuItem
            onClick={onClick}
            style={{
                paddingTop: SPACING.MD,
                paddingBottom: SPACING.MD,
                paddingLeft: SPACING.LG,
                paddingRight: SPACING.LG,
                gap: SPACING.MD,
                backgroundColor: isActive ? COLORS.SELECTION_BG : 'transparent',
                borderLeft: isActive ? `3px solid ${phaseConfig.color}` : '3px solid transparent',
            }}
        >
            {/* Phase icon and name */}
            <Box className="flex items-center flex-1 gap-2" >
                <Box
                    className="text-[24px] flex items-center justify-center w-[40px] h-[40px]" style={{ borderRadius: RADIUS.MD, backgroundColor: `${phaseConfig.color}15` }}
                >
                    {phaseConfig.emoji}
                </Box>

                <Box className="flex-1">
                    <Typography
                        style={{
                            fontWeight: FONT_WEIGHT.SEMIBOLD,
                            fontSize: TYPOGRAPHY.BASE,
                            color: COLORS.TEXT_PRIMARY,
                            marginBottom: SPACING.XXS,
                        }}
                    >
                        {phaseConfig.title}
                    </Typography>
                    <Typography
                        className="leading-[1.4]"
                        style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}
                    >
                        {phaseConfig.description}
                    </Typography>
                </Box>
            </Box>

            {/* Stats column */}
            <Box className="flex flex-col items-end gap-1">
                {/* Frame count */}
                <Typography
                    style={{
                        fontSize: TYPOGRAPHY.XS,
                        fontWeight: FONT_WEIGHT.MEDIUM,
                        color: COLORS.TEXT_SECONDARY,
                    }}
                >
                    {stats.frameCount} {stats.frameCount === 1 ? 'frame' : 'frames'}
                </Typography>

                {/* Completion indicator */}
                {stats.frameCount > 0 && (
                    <Box className="flex items-center gap-1">
                        <Box className="w-[60px]">
                            <LinearProgress
                                variant="determinate"
                                value={stats.completionPercent}
                                className="h-[4px]" style={{ borderRadius: RADIUS.FULL, backgroundColor: COLORS.NEUTRAL_200 }}
                            />
                        </Box>
                        <Typography
                            className="text-right min-w-[35px] text-xs font-semibold" >
                            {Math.round(stats.completionPercent)}%
                        </Typography>
                    </Box>
                )}

                {/* Activity indicator */}
                {stats.hasRecentActivity && (
                    <Chip
                        label="Active"
                        size="small"
                        className="h-[18px] text-white [&_.MuiChip-label]:px-1" style={{ fontSize: TYPOGRAPHY.XS, backgroundColor: COLORS.SUCCESS }}
                    />
                )}
            </Box>
        </MenuItem>
    );
}

/**
 * PhaseJumpMenu - Quick phase navigation with rich metadata
 * 
 * Shows all 6 lifecycle phases with:
 * - Frame counts
 * - Completion indicators
 * - Recent activity badges
 * - Visual phase colors
 * 
 * Clicking a phase triggers smooth pan+zoom to that phase area
 */
export function PhaseJumpMenu({
    anchorEl,
    onClose,
    phaseStats,
    onPhaseSelect,
}: PhaseJumpMenuProps) {
    const [currentPhase] = useAtom(chromeCurrentPhaseAtom);

    // Get stats for each phase
    const phaseStatsMap = useMemo(() => {
        const map = new Map<string, PhaseStats>();
        phaseStats.forEach(stat => map.set(stat.phase, stat));
        return map;
    }, [phaseStats]);

    // Calculate total stats
    const totalStats = useMemo(() => {
        let totalFrames = 0;
        let totalCompletion = 0;
        let activePhases = 0;

        phaseStats.forEach(stat => {
            totalFrames += stat.frameCount;
            totalCompletion += stat.completionPercent * stat.frameCount;
            if (stat.hasRecentActivity) activePhases++;
        });

        const avgCompletion = totalFrames > 0 ? totalCompletion / totalFrames : 0;

        return { totalFrames, avgCompletion, activePhases };
    }, [phaseStats]);

    const handlePhaseClick = (phase: keyof typeof LIFECYCLE_PHASES) => {
        const stats = phaseStatsMap.get(phase);
        if (stats) {
            onPhaseSelect(phase, stats.position);
        }
        onClose();
    };

    return (
        <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={onClose}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'center',
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'center',
            }}
            slotProps={{
                paper: {
                    style: {
                        minWidth: 480,
                        maxWidth: 600,
                        borderRadius: RADIUS.LG,
                        boxShadow: CANVAS_TOKENS.SHADOWS.XL,
                        border: `1px solid ${COLORS.BORDER_LIGHT}`,
                    },
                },
            }}
        >
            {/* Menu header with overview stats */}
            <Box
                style={{
                    paddingLeft: SPACING.LG,
                    paddingRight: SPACING.LG,
                    paddingTop: SPACING.MD,
                    paddingBottom: SPACING.MD,
                    borderBottom: `1px solid ${COLORS.BORDER_LIGHT}`,
                    backgroundColor: COLORS.NEUTRAL_50,
                }}
            >
                <Typography
                    variant="subtitle1"
                    style={{
                        fontWeight: FONT_WEIGHT.BOLD,
                        fontSize: TYPOGRAPHY.LG,
                        marginBottom: SPACING.SM,
                    }}
                >
                    🚀 Product Lifecycle
                </Typography>

                <Box className="flex gap-6" >
                    <Box>
                        <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
                            Total Frames
                        </Typography>
                        <Typography style={{ fontSize: TYPOGRAPHY.BASE, fontWeight: FONT_WEIGHT.SEMIBOLD }}>
                            {totalStats.totalFrames}
                        </Typography>
                    </Box>

                    <Box>
                        <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
                            Avg. Completion
                        </Typography>
                        <Typography
                            style={{
                                fontSize: TYPOGRAPHY.BASE,
                                fontWeight: FONT_WEIGHT.SEMIBOLD,
                                color: getStatusColor(totalStats.avgCompletion),
                            }}
                        >
                            {Math.round(totalStats.avgCompletion)}%
                        </Typography>
                    </Box>

                    <Box>
                        <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
                            Active Phases
                        </Typography>
                        <Typography style={{ fontSize: TYPOGRAPHY.BASE, fontWeight: FONT_WEIGHT.SEMIBOLD }}>
                            {totalStats.activePhases} / 6
                        </Typography>
                    </Box>
                </Box>
            </Box>

            {/* Phase list */}
            <Box style={{ paddingTop: SPACING.XS, paddingBottom: SPACING.XS }}>
                {(Object.keys(LIFECYCLE_PHASES) as Array<keyof typeof LIFECYCLE_PHASES>).map((phase) => {
                    const stats = phaseStatsMap.get(phase) || {
                        phase,
                        frameCount: 0,
                        completionPercent: 0,
                        hasRecentActivity: false,
                        position: { x: 0, y: 0 },
                    };

                    return (
                        <PhaseMenuItem
                            key={phase}
                            phase={phase}
                            stats={stats}
                            isActive={currentPhase === phase}
                            onClick={() => handlePhaseClick(phase)}
                        />
                    );
                })}
            </Box>

            {/* Footer with overview mode shortcut */}
            <Box
                style={{
                    paddingLeft: SPACING.LG,
                    paddingRight: SPACING.LG,
                    paddingTop: SPACING.SM,
                    paddingBottom: SPACING.SM,
                    borderTop: `1px solid ${COLORS.BORDER_LIGHT}`,
                    backgroundColor: COLORS.NEUTRAL_50,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
                    Click a phase to navigate
                </Typography>
                <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
                    Press <Box component="kbd" className="font-mono" style={{ paddingLeft: SPACING.XS, paddingRight: SPACING.XS, paddingTop: SPACING.XXS, paddingBottom: SPACING.XXS, backgroundColor: COLORS.NEUTRAL_200, borderRadius: RADIUS.SM }}>⌘⇧O</Box> for overview
                </Typography>
            </Box>
        </Menu>
    );
}

/**
 * Hook to manage phase jump menu
 */
export function usePhaseJumpMenu() {
    const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);

    const openMenu = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const closeMenu = () => {
        setAnchorEl(null);
    };

    return {
        anchorEl,
        openMenu,
        closeMenu,
        isOpen: Boolean(anchorEl),
    };
}
