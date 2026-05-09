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
import { Tooltip } from '@ghatana/design-system';
import { CheckCircle, Circle as RadioButtonUnchecked, Circle, Lightbulb as LightbulbOutlined, Palette, CheckCircle as CheckCircleOutline, Hammer as Build, Rocket as RocketLaunch, Eye as Visibility, TrendingUp } from 'lucide-react';
import { Button } from '../ui/Button';
import { LifecyclePhase, PHASE_LABELS, PHASE_DESCRIPTIONS } from '../../types/lifecycle';
import { useLifecyclePhase } from '../../hooks/useLifecyclePhase';
import { TRANSITIONS } from '../../styles/design-tokens';
import { useI18n } from '../../i18n/I18nProvider';

export interface CanvasPhaseNavigatorProps {
    /** Callback when phase is clicked */
    onPhaseClick?: (phase: LifecyclePhase) => void;
    /** Additional CSS classes */
    className?: string;
}

const ALL_PHASES: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.CONTEXT,
    LifecyclePhase.PLAN,
    LifecyclePhase.EXECUTE,
    LifecyclePhase.VERIFY,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.LEARN,
    LifecyclePhase.INSTITUTIONALIZE,
];

// Phase icons
const PHASE_ICONS: Record<LifecyclePhase, React.ReactNode> = {
    [LifecyclePhase.INTENT]: <LightbulbOutlined size={16} />,
    [LifecyclePhase.CONTEXT]: <Palette size={16} />,
    [LifecyclePhase.PLAN]: <CheckCircleOutline size={16} />,
    [LifecyclePhase.EXECUTE]: <Build size={16} />,
    [LifecyclePhase.VERIFY]: <RocketLaunch size={16} />,
    [LifecyclePhase.OBSERVE]: <Visibility size={16} />,
    [LifecyclePhase.LEARN]: <TrendingUp size={16} />,
    [LifecyclePhase.INSTITUTIONALIZE]: <CheckCircleOutline size={16} />,
};

// Phase colors
const PHASE_COLORS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'from-blue-500 to-blue-600',
    [LifecyclePhase.CONTEXT]: 'from-purple-500 to-purple-600',
    [LifecyclePhase.PLAN]: 'from-green-500 to-green-600',
    [LifecyclePhase.EXECUTE]: 'from-orange-500 to-orange-600',
    [LifecyclePhase.VERIFY]: 'from-red-500 to-red-600',
    [LifecyclePhase.OBSERVE]: 'from-teal-500 to-teal-600',
    [LifecyclePhase.LEARN]: 'from-pink-500 to-pink-600',
    [LifecyclePhase.INSTITUTIONALIZE]: 'from-indigo-500 to-indigo-600',
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
            classes += ' bg-success-bg dark:bg-success-bg/20 text-success-color dark:text-success-color hover:bg-success-bg dark:hover:bg-success-bg/30';
        } else if (isAccessible) {
            classes += ' bg-surface-muted dark:bg-surface-muted text-text-secondary hover:bg-surface-muted dark:hover:bg-surface-muted';
        } else {
            classes += ' bg-surface-muted/50 dark:bg-surface-muted/50 text-text-tertiary opacity-50 cursor-not-allowed';
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
            <Button
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
            </Button>
        </Tooltip>
    );
}

/**
 * Canvas Phase Navigator Component
 *
 * Compact vertical navigation for lifecycle phases within the canvas sidebar.
 */
export function CanvasPhaseNavigator({ onPhaseClick, className = '' }: CanvasPhaseNavigatorProps) {
    const { t } = useI18n();
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
            aria-label={t('canvas.phaseNavigator.navigation')}
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
                <div className="mx-4 mb-4 px-4 py-3 bg-info-bg dark:bg-info-bg/20 rounded-lg text-xs text-info-color dark:text-info-color border border-info-border">
                    <div className="flex items-center justify-between">
                        <span className="font-medium">Current Phase:</span>
                        <span className="font-semibold">{PHASE_LABELS[activePhase]}</span>
                    </div>
                </div>
            )}
        </nav>
    );
}
