/**
 * @doc.type component
 * @doc.purpose Display project phase progress in canvas view
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { Box, Surface as Paper, IconButton, Tooltip, Typography, LinearProgress, Collapse, Stack, Chip, Spinner as CircularProgress } from '@ghatana/ui';
import { ChevronUp as ExpandLess, ChevronDown as ExpandMore, Lightbulb, Building2 as Architecture, CheckCircle, Code, Play as PlayArrow, Eye as Visibility, TrendingUp } from 'lucide-react';
import { LifecyclePhase } from '../../types/lifecycle';

// Phase configuration with icons
const PHASE_CONFIG: Record<
    LifecyclePhase,
    { icon: React.ReactNode; label: string; color: string }
> = {
    INTENT: { icon: <Lightbulb size={undefined} />, label: 'Intent', color: '#8B5CF6' },
    SHAPE: { icon: <Architecture size={undefined} />, label: 'Shape', color: '#3B82F6' },
    VALIDATE: { icon: <CheckCircle size={undefined} />, label: 'Validate', color: '#10B981' },
    GENERATE: { icon: <Code size={undefined} />, label: 'Generate', color: '#F59E0B' },
    RUN: { icon: <PlayArrow size={undefined} />, label: 'Run', color: '#EF4444' },
    OBSERVE: { icon: <Visibility size={undefined} />, label: 'Observe', color: '#06B6D4' },
    IMPROVE: { icon: <TrendingUp size={undefined} />, label: 'Improve', color: '#EC4899' },
};

export interface PhaseProgress {
    phase: LifecyclePhase;
    progress: number; // 0-100
    tasksCompleted: number;
    tasksTotal: number;
    status: 'pending' | 'in-progress' | 'completed';
}

interface CanvasProgressWidgetProps {
    phases: PhaseProgress[];
    currentPhase: LifecyclePhase;
    onPhaseClick?: (phase: LifecyclePhase) => void;
    variant?: 'full' | 'compact' | 'mini';
}

export function CanvasProgressWidget({
    phases,
    currentPhase,
    onPhaseClick,
    variant = 'compact',
}: CanvasProgressWidgetProps) {
    const [expanded, setExpanded] = useState(false);

    const overallProgress = useMemo(() => {
        if (phases.length === 0) return 0;
        const total = phases.reduce((sum, p) => sum + p.progress, 0);
        return Math.round(total / phases.length);
    }, [phases]);

    const completedPhases = useMemo(
        () => phases.filter(p => p.status === 'completed').length,
        [phases]
    );

    if (variant === 'mini') {
        return (
            <MiniProgress
                progress={overallProgress}
                completedPhases={completedPhases}
                totalPhases={phases.length}
            />
        );
    }

    return (
        <Paper
            elevation={3}
            className="fixed bottom-[24px] left-[320px] z-50 rounded-lg overflow-hidden transition-all duration-300" style={{ minWidth: expanded ? 400 : 'auto' }}
        >
            {/* Header */}
            <Box
                className="flex items-center gap-2 px-4 py-2 bg-gray-50 dark:bg-gray-950 border-gray-200 dark:border-gray-700 cursor-pointer" style={{ borderBottom: expanded ? 1 : 0 }}
                onClick={() => setExpanded(!expanded)}
            >
                {/* Overall progress circle */}
                <Box className="relative inline-flex">
                    <CircularProgress
                        variant="determinate"
                        value={overallProgress}
                        size={40}
                        thickness={4}
                        style={{ color: overallProgress === 100 ? 'success.main' : 'primary.main' }}
                    />
                    <Box
                        className="absolute flex items-center justify-center top-[0px] left-[0px] bottom-[0px] right-[0px]"
                    >
                        <Typography as="span" className="text-xs text-gray-500" fontWeight={600}>
                            {overallProgress}%
                        </Typography>
                    </Box>
                </Box>

                {/* Phase indicators (compact view) */}
                {!expanded && (
                    <Stack direction="row" spacing={0.5} className="ml-2">
                        {phases.map(phase => {
                            const config = PHASE_CONFIG[phase.phase];
                            const isActive = phase.phase === currentPhase;
                            return (
                                <Tooltip
                                    key={phase.phase}
                                    title={`${config.label}: ${phase.progress}%`}
                                >
                                    <Box
                                        className="w-[24px] h-[24px] rounded-full flex items-center justify-center text-sm" style={{ backgroundColor: phase.status === 'completed'
                                                    ? `${config.color, color: config.color }}
                                        onClick={e => {
                                            e.stopPropagation();
                                            onPhaseClick?.(phase.phase);
                                        }}
                                    >
                                        {config.icon}
                                    </Box>
                                </Tooltip>
                            );
                        })}
                    </Stack>
                )}

                <Box className="grow" />

                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    {completedPhases}/{phases.length} phases
                </Typography>

                <IconButton size="sm">
                    {expanded ? <ExpandMore /> : <ExpandLess />}
                </IconButton>
            </Box>

            {/* Expanded view */}
            <Collapse in={expanded}>
                <Box className="p-4">
                    {phases.map(phase => {
                        const config = PHASE_CONFIG[phase.phase];
                        const isActive = phase.phase === currentPhase;
                        return (
                            <Box
                                key={phase.phase}
                                className="mb-3 p-2 rounded" style={{ backgroundColor: isActive ? 'action.selected' : 'transparent', cursor: onPhaseClick ? 'pointer' : 'default' }}
                                onClick={() => onPhaseClick?.(phase.phase)}
                            >
                                <Box
                                    className="flex items-center gap-2 mb-1"
                                >
                                    <Box
                                        className="flex items-center text-base" >
                                        {config.icon}
                                    </Box>
                                    <Typography as="p" className="text-sm" fontWeight={isActive ? 600 : 400}>
                                        {config.label}
                                    </Typography>
                                    <Box className="grow" />
                                    <Chip
                                        label={`${phase.tasksCompleted}/${phase.tasksTotal}`}
                                        size="sm"
                                        className="h-[20px] text-[11px]"
                                    />
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        {phase.progress}%
                                    </Typography>
                                </Box>
                                <LinearProgress
                                    variant="determinate"
                                    value={phase.progress}
                                    className="h-[4px] rounded-lg" style={{ backgroundColor: `${config.color }}
                                />
                            </Box>
                        );
                    })}
                </Box>
            </Collapse>
        </Paper>
    );
}

// Mini variant - just a floating badge
function MiniProgress({
    progress,
    completedPhases,
    totalPhases,
}: {
    progress: number;
    completedPhases: number;
    totalPhases: number;
}) {
    return (
        <Tooltip
            title={`${progress}% complete (${completedPhases}/${totalPhases} phases)`}
        >
            <Paper
                elevation={2}
                className="fixed rounded-full flex items-center justify-center bottom-[24px] left-[320px] z-50 w-[48px] h-[48px]"
            >
                <Box className="relative inline-flex">
                    <CircularProgress
                        variant="determinate"
                        value={progress}
                        size={40}
                        thickness={4}
                        style={{ color: progress === 100 ? 'success.main' : 'primary.main' }}
                    />
                    <Box
                        className="absolute flex items-center justify-center top-[0px] left-[0px] bottom-[0px] right-[0px]"
                    >
                        <Typography as="span" className="text-xs text-gray-500" fontWeight={600} fontSize={10}>
                            {progress}%
                        </Typography>
                    </Box>
                </Box>
            </Paper>
        </Tooltip>
    );
}

export default CanvasProgressWidget;
