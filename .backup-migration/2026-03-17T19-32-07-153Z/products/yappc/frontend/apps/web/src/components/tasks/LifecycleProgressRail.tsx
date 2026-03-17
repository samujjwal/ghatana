/**
 * Lifecycle Progress Rail Component
 *
 * Vertical navigation rail showing lifecycle stages with progress indicators.
 * Allows stage selection and displays completion status.
 *
 * @doc.type component
 * @doc.purpose Lifecycle stage navigation
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import React from 'react';
import { Box, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, Typography, Chip, LinearProgress, Tooltip, Badge } from '@ghatana/ui';
import { Lightbulb as IntentIcon, Hash as ContextIcon, ClipboardList as PlanIcon, Code as ExecuteIcon, Bug as VerifyIcon, HeartPulse as ObserveIcon, LineChart as LearnIcon, Library as InstitutionalizeIcon, CheckCircle as CompleteIcon, Lock as LockedIcon, PlayCircle as ActiveIcon } from 'lucide-react';
import type { LifecycleStage } from '@ghatana/yappc-types/tasks';

// ============================================================================
// Types
// ============================================================================

interface LifecycleProgressRailProps {
    currentStage: LifecycleStage | null;
    completedStages: LifecycleStage[];
    stageProgress?: Partial<Record<LifecycleStage, number>>;
    stageTasks?: Partial<Record<LifecycleStage, { total: number; completed: number }>>;
    onStageSelect?: (stage: LifecycleStage) => void;
    disabled?: boolean;
}

interface StageConfig {
    id: LifecycleStage;
    name: string;
    description: string;
    icon: React.ElementType;
    group: 'planning' | 'building' | 'operating' | 'learning';
}

// ============================================================================
// Constants
// ============================================================================

const STAGES: StageConfig[] = [
    {
        id: 'intent',
        name: 'Intent',
        description: 'Problem framing & goals',
        icon: IntentIcon,
        group: 'planning',
    },
    {
        id: 'context',
        name: 'Context',
        description: 'Gathering & synthesis',
        icon: ContextIcon,
        group: 'planning',
    },
    {
        id: 'plan',
        name: 'Plan',
        description: 'Requirements & design',
        icon: PlanIcon,
        group: 'planning',
    },
    {
        id: 'execute',
        name: 'Execute',
        description: 'Implementation & build',
        icon: ExecuteIcon,
        group: 'building',
    },
    {
        id: 'verify',
        name: 'Verify',
        description: 'Testing & compliance',
        icon: VerifyIcon,
        group: 'building',
    },
    {
        id: 'observe',
        name: 'Observe',
        description: 'Monitoring & operations',
        icon: ObserveIcon,
        group: 'operating',
    },
    {
        id: 'learn',
        name: 'Learn',
        description: 'Analytics & insights',
        icon: LearnIcon,
        group: 'learning',
    },
    {
        id: 'institutionalize',
        name: 'Institutionalize',
        description: 'Knowledge & standards',
        icon: InstitutionalizeIcon,
        group: 'learning',
    },
];

const GROUP_LABELS: Record<string, string> = {
    planning: 'Planning',
    building: 'Building',
    operating: 'Operating',
    learning: 'Learning',
};

// ============================================================================
// Component
// ============================================================================

export function LifecycleProgressRail({
    currentStage,
    completedStages,
    stageProgress = {},
    stageTasks = {},
    onStageSelect,
    disabled = false,
}: LifecycleProgressRailProps) {
    // Group stages
    const groupedStages = React.useMemo(() => {
        const groups: Record<string, StageConfig[]> = {
            planning: [],
            building: [],
            operating: [],
            learning: [],
        };
        STAGES.forEach((stage) => {
            groups[stage.group].push(stage);
        });
        return groups;
    }, []);

    // Get stage status
    const getStageStatus = React.useCallback(
        (stageId: LifecycleStage): 'completed' | 'active' | 'available' | 'locked' => {
            if (completedStages.includes(stageId)) return 'completed';
            if (currentStage === stageId) return 'active';
            // Check if previous stage is completed or active
            const stageIndex = STAGES.findIndex((s) => s.id === stageId);
            const prevStage = STAGES[stageIndex - 1];
            if (!prevStage || completedStages.includes(prevStage.id) || currentStage === prevStage.id) {
                return 'available';
            }
            return 'locked';
        },
        [currentStage, completedStages]
    );

    // Render stage item
    const renderStageItem = (stage: StageConfig) => {
        const status = getStageStatus(stage.id);
        const isDisabled = disabled || status === 'locked';
        const progress = stageProgress[stage.id] ?? 0;
        const tasks = stageTasks[stage.id];
        const StageIcon = stage.icon;

        return (
            <Tooltip
                key={stage.id}
                title={isDisabled ? 'Complete previous stages first' : stage.description}
                placement="right"
            >
                <ListItemButton
                    selected={status === 'active'}
                    disabled={isDisabled}
                    onClick={() => onStageSelect?.(stage.id)}
                    className="rounded mb-1 relative" style={{ opacity: isDisabled ? 0.5 : 1 }}
                >
                    <ListItemIcon className="min-w-[40px]">
                        <Badge
                            overlap="circular"
                            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                            badgeContent={
                                status === 'completed' ? (
                                    <CompleteIcon
                                        className="text-green-600 text-sm"
                                    />
                                ) : status === 'active' ? (
                                    <ActiveIcon className="text-blue-600 text-sm" />
                                ) : status === 'locked' ? (
                                    <LockedIcon className="text-gray-400 dark:text-gray-600 text-xs" />
                                ) : null
                            }
                        >
                            <StageIcon
                                style={{ color: status === 'completed'
                                            ? 'success.main'
                                            : status === 'active'
                                                ? 'primary.main'
                                                : 'text.secondary' }}
                            />
                        </Badge>
                    </ListItemIcon>
                    <ListItemText
                        primary={
                            <Typography
                                as="p" className="text-sm"
                                fontWeight={status === 'active' ? 600 : 400}
                            >
                                {stage.name}
                            </Typography>
                        }
                        secondary={
                            tasks ? (
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {tasks.completed}/{tasks.total} tasks
                                </Typography>
                            ) : undefined
                        }
                    />
                    {progress > 0 && progress < 100 && (
                        <Chip
                            label={`${progress}%`}
                            size="sm"
                            tone="primary"
                            variant="outlined"
                            className="ml-2 h-[20px] text-[0.7rem]"
                        />
                    )}
                </ListItemButton>
            </Tooltip>
        );
    };

    return (
        <Box className="w-full">
            {Object.entries(groupedStages).map(([group, stages]) => (
                <Box key={group} className="mb-6">
                    <Typography
                        as="span" className="text-xs uppercase tracking-wider"
                        color="text.secondary"
                        className="px-4 block mb-2"
                    >
                        {GROUP_LABELS[group]}
                    </Typography>
                    <List dense disablePadding>
                        {stages.map(renderStageItem)}
                    </List>
                </Box>
            ))}

            {/* Overall Progress */}
            <Box className="px-4 mt-4">
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    Overall Progress
                </Typography>
                <LinearProgress
                    variant="determinate"
                    value={(completedStages.length / STAGES.length) * 100}
                    className="mt-2 rounded"
                />
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-1">
                    {completedStages.length} of {STAGES.length} stages complete
                </Typography>
            </Box>
        </Box>
    );
}

export default LifecycleProgressRail;
