/**
 * Phase Progress Pill Component
 * 
 * Shows current phase, completion percentage, and next phase.
 * Expandable to show gate criteria panel.
 * 
 * @doc.type component
 * @doc.purpose Phase progress indicator with gate criteria
 * @doc.layer product
 * @doc.pattern Pill Button + Expandable Panel
 */

import React, { useState } from 'react';
import { Box, Button, Chip, LinearProgress, Popper as Popover, Typography, InteractiveList as List, ListItem, ListItemIcon, ListItemText } from '@ghatana/ui';
import { CheckCircle, AlertTriangle as Warning, Clock as Pending, ArrowRight as ArrowForward, ChevronDown as ExpandMore } from 'lucide-react';
import { LifecyclePhase } from '@/types/lifecycle';
import { PHASE_COLORS, PHASE_LABELS, PHASE_ICONS, TIMING } from '../../../styles/design-tokens';

/**
 * Metadata about lifecycle phases
 */
const PHASE_META: Record<LifecyclePhase, { name: string; icon: string; color: string }> = {
    [LifecyclePhase.INTENT]: { name: PHASE_LABELS.INTENT, icon: PHASE_ICONS.INTENT, color: PHASE_COLORS.INTENT.background },
    [LifecyclePhase.SHAPE]: { name: PHASE_LABELS.SHAPE, icon: PHASE_ICONS.SHAPE, color: PHASE_COLORS.SHAPE.background },
    [LifecyclePhase.VALIDATE]: { name: PHASE_LABELS.VALIDATE, icon: PHASE_ICONS.VALIDATE, color: PHASE_COLORS.VALIDATE.background },
    [LifecyclePhase.GENERATE]: { name: PHASE_LABELS.GENERATE, icon: PHASE_ICONS.GENERATE, color: PHASE_COLORS.GENERATE.background },
    [LifecyclePhase.RUN]: { name: PHASE_LABELS.RUN, icon: PHASE_ICONS.RUN, color: PHASE_COLORS.RUN.background },
    [LifecyclePhase.OBSERVE]: { name: PHASE_LABELS.OBSERVE, icon: PHASE_ICONS.OBSERVE, color: PHASE_COLORS.OBSERVE.background },
    [LifecyclePhase.IMPROVE]: { name: PHASE_LABELS.IMPROVE, icon: PHASE_ICONS.IMPROVE, color: PHASE_COLORS.IMPROVE.background },
};

export interface GateCriterion {
    id: string;
    label: string;
    status: 'complete' | 'blocked' | 'in-progress' | 'failed';
    progress?: { current: number; total: number };
    personas?: string[]; // Required personas to unblock
}

export interface PhaseProgressPillProps {
    currentPhase: LifecyclePhase;
    nextPhase?: LifecyclePhase;
    completionPercent: number;
    gateCriteria: GateCriterion[];
    onProceedToNext?: () => void;
    onAIHelpUnblock?: () => void;
}

export const PhaseProgressPill: React.FC<PhaseProgressPillProps> = ({
    currentPhase,
    nextPhase,
    completionPercent,
    gateCriteria,
    onProceedToNext,
    onAIHelpUnblock,
}) => {
    const theme = useTheme();
    const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);

    const currentMeta = PHASE_META[currentPhase];
    const nextMeta = nextPhase !== undefined ? PHASE_META[nextPhase] : null;
    const open = Boolean(anchorEl);

    const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const allComplete = gateCriteria.every(c => c.status === 'complete');
    const hasBlocking = gateCriteria.some(c => c.status === 'blocked');

    const getStatusIcon = (status: GateCriterion['status']) => {
        switch (status) {
            case 'complete':
                return <CheckCircle className="text-green-600" />;
            case 'blocked':
                return <Warning className="text-red-600" />;
            case 'in-progress':
                return <Pending className="text-sky-600" />;
            case 'failed':
                return <Warning className="text-red-600" />;
        }
    };

    return (
        <>
            <Button
                variant="outlined"
                onClick={handleClick}
                endIcon={<ExpandMore />}
                className="rounded-[64px] px-6 py-2 bg-white dark:bg-gray-900 hover:bg-gray-100" style={{ borderColor: hasBlocking ? 'error.main' : 'divider' }}
            >
                <Box className="flex items-center gap-3">
                    <span style={{ fontSize: '1.2em' }}>{currentMeta.icon}</span>
                    <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                        {currentMeta.name}
                    </Typography>
                    <Chip
                        label={`${completionPercent}%`}
                        size="sm"
                        color={allComplete ? 'success' : hasBlocking ? 'error' : 'default'}
                    />
                    {nextMeta && (
                        <>
                            <ArrowForward className="text-gray-500 dark:text-gray-400 text-base" />
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {nextMeta.name}
                            </Typography>
                        </>
                    )}
                </Box>
            </Button>

            <Popover
                open={open}
                anchorEl={anchorEl}
                onClose={handleClose}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'center',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'center',
                }}
                PaperProps={{
                    sx: {
                        mt: 1,
                        minWidth: 400,
                        maxWidth: 500,
                        boxShadow: theme.shadows[8],
                    },
                }}
            >
                <Box className="p-4">
                    <Typography as="h6" gutterBottom>
                        Gate Criteria: {currentMeta.name} → {nextMeta?.name || 'Complete'}
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                        {allComplete
                            ? '✓ All criteria met! Ready to proceed.'
                            : `To proceed to ${nextMeta?.name || 'next phase'}, complete:`}
                    </Typography>

                    <List dense className="mt-4">
                        {gateCriteria.map((criterion) => (
                            <ListItem
                                key={criterion.id}
                                className="border border-gray-200 dark:border-gray-700 rounded mb-2" style={{ backgroundColor: criterion.status === 'blocked'
                                            ? 'error.light'
                                            : criterion.status === 'complete'
                                                ? 'success.light'
                                                : 'background.default' }}
                            >
                                <ListItemIcon>{getStatusIcon(criterion.status)}</ListItemIcon>
                                <ListItemText
                                    primary={criterion.label}
                                    secondary={
                                        <>
                                            {criterion.progress && (
                                                <Typography as="span" className="text-xs text-gray-500" display="block">
                                                    {criterion.progress.current}/{criterion.progress.total} completed
                                                </Typography>
                                            )}
                                            {criterion.status === 'blocked' && criterion.personas && (
                                                <Typography
                                                    as="span" className="text-xs text-gray-500"
                                                    display="block"
                                                    tone="danger"
                                                    className="mt-1"
                                                >
                                                    <strong>← BLOCKING</strong> · Need: {criterion.personas.join(', ')}
                                                </Typography>
                                            )}
                                        </>
                                    }
                                />
                            </ListItem>
                        ))}
                    </List>

                    {!allComplete && (
                        <LinearProgress
                            variant="determinate"
                            value={completionPercent}
                            className="mt-4 mb-2"
                        />
                    )}

                    <Box className="mt-4 flex gap-2">
                        {allComplete && onProceedToNext ? (
                            <Button
                                variant="solid"
                                tone="primary"
                                fullWidth
                                onClick={() => {
                                    onProceedToNext();
                                    handleClose();
                                }}
                            >
                                Proceed to {nextMeta?.name || 'Next Phase'}
                            </Button>
                        ) : (
                            <>
                                <Button variant="outlined" onClick={handleClose}>
                                    View Details
                                </Button>
                                {onAIHelpUnblock && (
                                    <Button
                                        variant="solid"
                                        tone="secondary"
                                        onClick={() => {
                                            onAIHelpUnblock();
                                            handleClose();
                                        }}
                                    >
                                        AI: Help Unblock
                                    </Button>
                                )}
                            </>
                        )}
                    </Box>
                </Box>
            </Popover>
        </>
    );
};
