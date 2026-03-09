/**
 * Phase Progress Bar Component
 * 
 * Enhanced phase navigation UI with prominent progress visualization.
 * Replaces subtle dots with a clear, interactive progress indicator showing:
 * - Current phase with large indicator
 * - Phase names (not just icons)
 * - Visual progress percentage
 * - Click-to-navigate functionality
 * 
 * @doc.type component
 * @doc.purpose Enhanced phase progress visualization
 * @doc.layer product
 * @doc.pattern Interactive Progress Component
 */

import { useMemo } from 'react';
import { Tooltip } from '@ghatana/ui';
import { Lightbulb as LightbulbOutlined, PenLine as DrawOutlined, BadgeCheck as VerifiedOutlined, Code as CodeOutlined, Play as PlayArrowOutlined, HeartPulse as MonitorHeartOutlined, TrendingUp as TrendingUpOutlined, CheckCircle } from 'lucide-react';

import { LifecyclePhase } from '../../types/lifecycle';
import { TRANSITIONS } from '../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface PhaseProgressInfo {
    phase: LifecyclePhase;
    progress: number; // 0-100
    status: 'pending' | 'in-progress' | 'completed';
}

export interface PhaseProgressBarProps {
    /** All phases with progress info */
    phases: PhaseProgressInfo[];
    /** Current active phase */
    currentPhase: LifecyclePhase;
    /** Callback when a phase is clicked */
    onPhaseClick?: (phase: LifecyclePhase) => void;
    /** Variant style */
    variant?: 'full' | 'compact';
    /** Additional CSS classes */
    className?: string;
}

// ============================================================================
// Constants
// ============================================================================

const PHASE_CONFIG: Record<LifecyclePhase, {
    label: string;
    shortLabel: string;
    icon: React.ElementType;
    color: string;
    description: string;
}> = {
    [LifecyclePhase.INTENT]: {
        label: 'Intent',
        shortLabel: 'Intent',
        icon: LightbulbOutlined,
        color: '#F59E0B', // amber-500
        description: 'Define what to build',
    },
    [LifecyclePhase.SHAPE]: {
        label: 'Shape',
        shortLabel: 'Shape',
        icon: DrawOutlined,
        color: '#8B5CF6', // violet-500
        description: 'Design the architecture',
    },
    [LifecyclePhase.VALIDATE]: {
        label: 'Validate',
        shortLabel: 'Validate',
        icon: VerifiedOutlined,
        color: '#3B82F6', // blue-500
        description: 'Verify the design',
    },
    [LifecyclePhase.GENERATE]: {
        label: 'Generate',
        shortLabel: 'Generate',
        icon: CodeOutlined,
        color: '#10B981', // green-500
        description: 'Generate code',
    },
    [LifecyclePhase.RUN]: {
        label: 'Run',
        shortLabel: 'Run',
        icon: PlayArrowOutlined,
        color: '#06B6D4', // cyan-500
        description: 'Deploy & execute',
    },
    [LifecyclePhase.OBSERVE]: {
        label: 'Observe',
        shortLabel: 'Observe',
        icon: MonitorHeartOutlined,
        color: '#EC4899', // pink-500
        description: 'Monitor & observe',
    },
    [LifecyclePhase.IMPROVE]: {
        label: 'Improve',
        shortLabel: 'Improve',
        icon: TrendingUpOutlined,
        color: '#F97316', // orange-500
        description: 'Iterate & improve',
    },
};

const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

// ============================================================================
// Sub-components
// ============================================================================

interface PhaseStepProps {
    phase: LifecyclePhase;
    status: 'pending' | 'in-progress' | 'completed';
    isCurrent: boolean;
    progress: number;
    isLast: boolean;
    variant: 'full' | 'compact';
    onClick?: () => void;
}

function PhaseStep({
    phase,
    status,
    isCurrent,
    progress,
    isLast,
    variant,
    onClick
}: PhaseStepProps) {
    const config = PHASE_CONFIG[phase];
    const Icon = config.icon;

    const stepClasses = useMemo(() => {
        const base = `flex flex-col items-center cursor-pointer ${TRANSITIONS.default} hover:scale-105`;
        return base;
    }, []);

    const iconContainerClasses = useMemo(() => {
        const base = `
            relative flex items-center justify-center 
            rounded-full ${TRANSITIONS.default}
            ${isCurrent ? 'w-12 h-12' : 'w-10 h-10'}
        `;

        if (isCurrent) {
            return `${base} bg-white shadow-lg ring-4 ring-opacity-50`;
        }

        if (status === 'completed') {
            return `${base} bg-green-500 text-white shadow-md`;
        }

        return `${base} bg-grey-200 dark:bg-grey-700 text-grey-500 dark:text-grey-400`;
    }, [isCurrent, status]);

    return (
        <div className="flex items-center">
            {/* Phase Step */}
            <Tooltip title={config.description}>
                <button
                    onClick={onClick}
                    className={stepClasses}
                    aria-label={`Go to ${config.label} phase`}
                    style={{ minWidth: variant === 'full' ? '80px' : '60px' }}
                >
                    {/* Icon Circle */}
                    <div
                        className={iconContainerClasses}
                        style={{
                            color: isCurrent ? config.color : undefined,
                            borderColor: isCurrent ? config.color : undefined,
                        }}
                    >
                        {status === 'completed' ? (
                            <CheckCircle style={{ fontSize: isCurrent ? 28 : 24 }} />
                        ) : (
                            <Icon style={{ fontSize: isCurrent ? 28 : 24 }} />
                        )}

                        {/* Progress Ring (for current phase) */}
                        {isCurrent && progress > 0 && (
                            <svg
                                className="absolute inset-0"
                                style={{ width: '100%', height: '100%' }}
                            >
                                <circle
                                    cx="50%"
                                    cy="50%"
                                    r="45%"
                                    fill="none"
                                    stroke={config.color}
                                    strokeWidth="3"
                                    strokeDasharray={`${progress * 2.83} 283`}
                                    strokeLinecap="round"
                                    transform="rotate(-90 24 24)"
                                    style={{ transition: 'stroke-dasharray 0.3s ease' }}
                                />
                            </svg>
                        )}
                    </div>

                    {/* Phase Label */}
                    <span
                        className={`
                            mt-1.5 text-xs font-medium whitespace-nowrap
                            ${isCurrent ? 'text-text-primary font-semibold' : 'text-text-secondary'}
                            ${TRANSITIONS.fast}
                        `}
                        style={{ color: isCurrent ? config.color : undefined }}
                    >
                        {variant === 'full' ? config.label : config.shortLabel}
                    </span>

                    {/* Progress Percentage (only for current phase in full variant) */}
                    {isCurrent && variant === 'full' && progress > 0 && (
                        <span
                            className="text-xs text-text-tertiary mt-0.5"
                        >
                            {progress}%
                        </span>
                    )}
                </button>
            </Tooltip>

            {/* Connector Line */}
            {!isLast && (
                <div
                    className={`
                        flex-1 h-0.5 mx-1 ${TRANSITIONS.fast}
                        ${status === 'completed' ? 'bg-green-500' : 'bg-grey-300 dark:bg-grey-600'}
                    `}
                    style={{
                        minWidth: variant === 'full' ? '24px' : '16px',
                        maxWidth: variant === 'full' ? '40px' : '24px',
                    }}
                />
            )}
        </div>
    );
}

// ============================================================================
// Main Component
// ============================================================================

export function PhaseProgressBar({
    phases,
    currentPhase,
    onPhaseClick,
    variant = 'full',
    className = '',
}: PhaseProgressBarProps) {
    // Get phase info by phase
    const getPhaseInfo = (phase: LifecyclePhase): PhaseProgressInfo => {
        return phases.find(p => p.phase === phase) || {
            phase,
            progress: 0,
            status: 'pending' as const,
        };
    };

    // Calculate overall progress
    const overallProgress = useMemo(() => {
        const currentIndex = PHASE_ORDER.indexOf(currentPhase);
        const completedPhases = currentIndex; // phases before current are completed
        const currentPhaseProgress = getPhaseInfo(currentPhase).progress;

        // Overall = (completed phases + current phase progress) / total phases
        return Math.round(((completedPhases + currentPhaseProgress / 100) / PHASE_ORDER.length) * 100);
    }, [phases, currentPhase]);

    return (
        <div className={`flex flex-col ${className}`}>
            {/* Overall Progress Bar */}
            {variant === 'full' && (
                <div className="mb-3">
                    <div className="flex items-center justify-between mb-1">
                        <span className="text-xs font-medium text-text-secondary">
                            Overall Progress
                        </span>
                        <span className="text-xs font-semibold text-text-primary">
                            {overallProgress}%
                        </span>
                    </div>
                    <div className="w-full h-2 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                        <div
                            className={`h-full bg-gradient-to-r from-blue-500 to-green-500 ${TRANSITIONS.default}`}
                            style={{ width: `${overallProgress}%` }}
                        />
                    </div>
                </div>
            )}

            {/* Phase Steps */}
            <div className="flex items-start justify-between">
                {PHASE_ORDER.map((phase, index) => {
                    const info = getPhaseInfo(phase);
                    const isLast = index === PHASE_ORDER.length - 1;

                    return (
                        <PhaseStep
                            key={phase}
                            phase={phase}
                            status={info.status}
                            isCurrent={phase === currentPhase}
                            progress={info.progress}
                            isLast={isLast}
                            variant={variant}
                            onClick={() => onPhaseClick?.(phase)}
                        />
                    );
                })}
            </div>
        </div>
    );
}
