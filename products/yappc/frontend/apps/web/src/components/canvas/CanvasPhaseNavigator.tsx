/**
 * Canvas Phase Navigator - Mini Lifecycle Phase Navigator for Canvas Sidebar
 *
 * Compact vertical phase navigation embedded in canvas left panel.
 * Replaces the need for a separate "Lifecycle" tab in project shell.
 *
 * Features:
 * - Vertical orientation optimized for sidebar
 * - Current phase highlighted
 * - Click to navigate between phases
 * - Tooltips for phase descriptions
 * - Compact visual design (icons + progress dots)
 *
 * @doc.type component
 * @doc.purpose Mini lifecycle navigator for canvas context
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import { useCallback, useMemo } from 'react';
import { Tooltip } from '@ghatana/ui';
import { CheckCircle, Circle as RadioButtonUnchecked, Circle, Lightbulb as LightbulbOutlined, Palette, CheckCircle as CheckCircleOutline, Hammer as Build, Rocket as RocketLaunch, Eye as Visibility, TrendingUp } from 'lucide-react';
import { LifecyclePhase, PHASE_LABELS, PHASE_DESCRIPTIONS } from '../../types/lifecycle';
import { useLifecyclePhase } from '../../hooks/useLifecyclePhase';
import { TRANSITIONS } from '../../styles/design-tokens';

export interface CanvasPhaseNavigatorProps {
    /** Callback when phase is clicked */
    onPhaseClick?: (phase: LifecyclePhase) => void;
    /** Additional CSS classes */
    className?: string;
}

const ALL_PHASES: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

// Phase icons
const PHASE_ICONS: Record<LifecyclePhase, React.ReactNode> = {
    [LifecyclePhase.INTENT]: <LightbulbOutlined size={16} />,
    [LifecyclePhase.SHAPE]: <Palette size={16} />,
    [LifecyclePhase.VALIDATE]: <CheckCircleOutline size={16} />,
    [LifecyclePhase.GENERATE]: <Build size={16} />,
    [LifecyclePhase.RUN]: <RocketLaunch size={16} />,
    [LifecyclePhase.OBSERVE]: <Visibility size={16} />,
    [LifecyclePhase.IMPROVE]: <TrendingUp size={16} />,
};

// Phase colors
const PHASE_COLORS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'from-blue-500 to-blue-600',
    [LifecyclePhase.SHAPE]: 'from-purple-500 to-purple-600',
    [LifecyclePhase.VALIDATE]: 'from-green-500 to-green-600',
    [LifecyclePhase.GENERATE]: 'from-orange-500 to-orange-600',
    [LifecyclePhase.RUN]: 'from-red-500 to-red-600',
    [LifecyclePhase.OBSERVE]: 'from-teal-500 to-teal-600',
    [LifecyclePhase.IMPROVE]: 'from-pink-500 to-pink-600',
};

/**
 * Individual Phase Item Component
 */
interface PhaseItemProps {
    phase: LifecyclePhase;
    isActive: boolean;
    isAccessible: boolean;
    isPast: boolean;
    onClick: () => void;
}

function PhaseItem({ phase, isActive, isAccessible, isPast, onClick }: PhaseItemProps) {
    const label = PHASE_LABELS[phase];
    const description = PHASE_DESCRIPTIONS[phase];
    const icon = PHASE_ICONS[phase];
    const colorGradient = PHASE_COLORS[phase];

    const buttonClasses = useMemo(() => {
        let classes = `
      flex items-center gap-2 w-full px-2 py-1.5 rounded-md text-sm
      ${TRANSITIONS.fast}
    `;

        if (isActive) {
            classes += ` bg-gradient-to-r ${colorGradient} text-white shadow-md font-semibold`;
        } else if (isPast) {
            classes += ' bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 hover:bg-green-100 dark:hover:bg-green-900/30';
        } else if (isAccessible) {
            classes += ' bg-grey-50 dark:bg-grey-800 text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-700';
        } else {
            classes += ' bg-grey-50/50 dark:bg-grey-900/30 text-text-tertiary opacity-50 cursor-not-allowed';
        }

        return classes;
    }, [isActive, isPast, isAccessible, colorGradient]);

    const statusIcon = useMemo(() => {
        if (isPast) return <CheckCircle className="w-3.5 h-3.5 flex-shrink-0" />;
        if (isActive) return <Circle className="w-3.5 h-3.5 flex-shrink-0" />;
        return <RadioButtonUnchecked className="w-3.5 h-3.5 flex-shrink-0" />;
    }, [isPast, isActive]);

    return (
        <Tooltip title={`${label}: ${description}`} placement="right">
            <button
                onClick={onClick}
                disabled={!isAccessible}
                className={buttonClasses}
                aria-label={`${label} phase${isActive ? ' (current)' : ''}${!isAccessible ? ' (not accessible)' : ''}`}
                aria-current={isActive ? 'step' : undefined}
            >
                {/* Phase Icon */}
                <span className="text-base flex-shrink-0">{icon}</span>

                {/* Phase Label */}
                <span className="flex-1 text-left truncate">{label}</span>

                {/* Status Icon */}
                {statusIcon}
            </button>
        </Tooltip>
    );
}

/**
 * Canvas Phase Navigator Component
 *
 * Compact vertical navigation for lifecycle phases within the canvas sidebar.
 */
export function CanvasPhaseNavigator({ onPhaseClick, className = '' }: CanvasPhaseNavigatorProps) {
    const { currentPhase, projectPhase, navigateToPhase, canTransitionTo } = useLifecyclePhase();

    // Use project phase if available, otherwise route phase
    // If neither project nor route phase is available, fall back to INTENT so UI is actionable
    const activePhase = projectPhase ?? currentPhase ?? LifecyclePhase.INTENT;

    const handlePhaseClick = useCallback(
        (phase: LifecyclePhase) => {
            // Allow navigation attempts; `navigateToPhase` handles project-id checks
            if (canTransitionTo(phase)) {
                onPhaseClick?.(phase);
                navigateToPhase(phase);
            } else {
                // Even if transition rules prevent automatic move, still allow user to request navigation
                onPhaseClick?.(phase);
                navigateToPhase(phase);
            }
        },
        [canTransitionTo, navigateToPhase, onPhaseClick]
    );

    return (
        <nav
            className={`flex flex-col ${className}`}
            aria-label="Lifecycle phase navigation"
            role="navigation"
        >
            {/* Header */}
            <div className="px-6 py-4 border-b border-divider">
                <h3 className="font-semibold text-text-secondary text-xs uppercase tracking-wider">
                    Lifecycle Phases
                </h3>
            </div>

            {/* Phase List */}
            <div className="flex flex-col gap-1 px-4 py-3">
                {ALL_PHASES.map((phase) => {
                    const isActive = activePhase === phase;
                    const isAccessible = canTransitionTo(phase);
                    const isPast = activePhase ? phase < activePhase : false;

                    return (
                        <PhaseItem
                            key={phase}
                            phase={phase}
                            isActive={isActive}
                            isAccessible={isAccessible}
                            isPast={isPast}
                            onClick={() => handlePhaseClick(phase)}
                        />
                    );
                })}
            </div>

            {/* Progress Summary */}
            {activePhase && (
                <div className="mx-4 mb-4 px-4 py-3 bg-primary-50 dark:bg-primary-900/20 rounded-lg text-xs text-primary-700 dark:text-primary-300 border border-primary-200 dark:border-primary-800">
                    <div className="flex items-center justify-between">
                        <span className="font-medium">Current Phase:</span>
                        <span className="font-semibold">{PHASE_LABELS[activePhase]}</span>
                    </div>
                </div>
            )}
        </nav>
    );
}
