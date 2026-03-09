/**
 * Unified Phase Rail Component
 * 
 * Always-visible phase navigation that shows the complete lifecycle.
 * Context-aware styling and interactions.
 * 
 * @doc.type component
 * @doc.purpose Lifecycle phase navigation
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import React, { useCallback } from 'react';
import { Tooltip } from '@ghatana/ui';
import { Lightbulb as IntentIcon, Sparkles as ShapeIcon, BadgeCheck as ValidateIcon, Code as GenerateIcon, Play as RunIcon, Eye as ObserveIcon, TrendingUp as ImproveIcon, CheckCircle as CompletedIcon, Circle as NotStartedIcon, Clock as InProgressIcon, Lock as LockedIcon } from 'lucide-react';

import { usePhaseContext } from '../../context/WorkflowContextProvider';
import { LifecyclePhase } from '../../types/lifecycle';
import { PHASE_LABELS, PHASE_DESCRIPTIONS, PHASE_COLORS, PHASE_ICONS } from '../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface UnifiedPhaseRailProps {
    /** Layout orientation */
    orientation?: 'horizontal' | 'vertical';
    /** Display variant */
    variant?: 'full' | 'compact' | 'minimal';
    /** Show phase descriptions */
    showDescriptions?: boolean;
    /** Additional class names */
    className?: string;
    /** Callback when phase is clicked */
    onPhaseClick?: (phase: LifecyclePhase) => void;
}

interface PhaseItemProps {
    phase: LifecyclePhase;
    isActive: boolean;
    isCompleted: boolean;
    isAccessible: boolean;
    variant: 'full' | 'compact' | 'minimal';
    showDescription: boolean;
    onClick: () => void;
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Phase order
 */
const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

/**
 * Icons for each phase (MUI Components)
 */
const PHASE_MUI_ICONS: Record<LifecyclePhase, React.ElementType> = {
    [LifecyclePhase.INTENT]: IntentIcon,
    [LifecyclePhase.SHAPE]: ShapeIcon,
    [LifecyclePhase.VALIDATE]: ValidateIcon,
    [LifecyclePhase.GENERATE]: GenerateIcon,
    [LifecyclePhase.RUN]: RunIcon,
    [LifecyclePhase.OBSERVE]: ObserveIcon,
    [LifecyclePhase.IMPROVE]: ImproveIcon,
};

// ============================================================================
// Sub-components
// ============================================================================

/**
 * Individual phase item
 */
const PhaseItem: React.FC<PhaseItemProps> = ({
    phase,
    isActive,
    isCompleted,
    isAccessible,
    variant,
    showDescription,
    onClick,
}) => {
    const Icon = PHASE_MUI_ICONS[phase];
    const colors = PHASE_COLORS[phase];
    const label = PHASE_LABELS[phase];
    const description = PHASE_DESCRIPTIONS[phase];

    // Determine status icon
    let StatusIcon = NotStartedIcon;
    if (isCompleted) StatusIcon = CompletedIcon;
    else if (isActive) StatusIcon = InProgressIcon;
    else if (!isAccessible) StatusIcon = LockedIcon;

    // Build class names
    const baseClasses = 'relative flex items-center gap-3 rounded-lg transition-all duration-200';
    const sizeClasses = variant === 'minimal'
        ? 'p-2'
        : variant === 'compact'
            ? 'px-3 py-2'
            : 'px-4 py-3';

    const stateClasses = isActive
        ? `${colors.activeBg} text-white shadow-lg ring-2 ring-offset-2 ring-offset-white dark:ring-offset-grey-900`
        : isCompleted
            ? `${colors.bg} ${colors.text} border ${colors.border}`
            : isAccessible
                ? `bg-grey-50 dark:bg-grey-800 text-grey-700 dark:text-grey-300 hover:${colors.bg} hover:${colors.text} border border-transparent hover:${colors.border} cursor-pointer`
                : 'bg-grey-50 dark:bg-grey-900 text-grey-400 dark:text-grey-600 opacity-60 cursor-not-allowed';

    const ringColor = isActive
        ? colors.text.replace('text-', 'ring-').replace('-600', '-300').replace('-400', '-600')
        : '';

    const content = (
        <button
            type="button"
            onClick={isAccessible || isActive ? onClick : undefined}
            disabled={!isAccessible && !isActive}
            className={`${baseClasses} ${sizeClasses} ${stateClasses} ${ringColor}`}
            aria-current={isActive ? 'step' : undefined}
            aria-label={`${label}${isActive ? ' (current)' : ''}${isCompleted ? ' (completed)' : ''}`}
        >
            {/* Phase Icon */}
            <div className={`flex-shrink-0 ${variant === 'minimal' ? 'w-5 h-5' : 'w-6 h-6'}`}>
                <Icon className="w-full h-full" />
            </div>

            {/* Label and Description */}
            {variant !== 'minimal' && (
                <div className="flex-1 text-left min-w-0">
                    <span className="block font-medium text-sm truncate">{label}</span>
                    {showDescription && variant === 'full' && (
                        <span className={`block text-xs truncate ${isActive ? 'text-white/80' : 'text-grey-500 dark:text-grey-400'}`}>
                            {description}
                        </span>
                    )}
                </div>
            )}

            {/* Status Indicator */}
            {variant === 'full' && (
                <div className={`flex-shrink-0 w-4 h-4 ${isActive ? 'text-white/80' : isCompleted ? 'text-green-500' : 'text-grey-400'}`}>
                    <StatusIcon className="w-full h-full" />
                </div>
            )}

            {/* Active Indicator */}
            {isActive && (
                <span className="absolute -inset-px rounded-lg ring-2 ring-white/50 pointer-events-none" />
            )}
        </button>
    );

    // Wrap in tooltip for minimal variant
    if (variant === 'minimal') {
        return (
            <Tooltip title={`${label}: ${description}`} placement="right">
                {content}
            </Tooltip>
        );
    }

    return content;
};

/**
 * Connector between phases
 */
const PhaseConnector: React.FC<{ isCompleted: boolean; orientation: 'horizontal' | 'vertical' }> = ({
    isCompleted,
    orientation,
}) => {
    const baseClasses = 'flex-shrink-0 transition-colors duration-200';
    const sizeClasses = orientation === 'horizontal'
        ? 'w-8 h-0.5'
        : 'h-6 w-0.5 mx-auto';
    const colorClasses = isCompleted
        ? 'bg-green-500 dark:bg-green-400'
        : 'bg-grey-200 dark:bg-grey-700';

    return <div className={`${baseClasses} ${sizeClasses} ${colorClasses}`} />;
};

// ============================================================================
// Main Component
// ============================================================================

/**
 * Unified Phase Rail
 * 
 * A consistent, always-accessible navigation component for lifecycle phases.
 * 
 * @example
 * ```tsx
 * <UnifiedPhaseRail 
 *   orientation="horizontal" 
 *   variant="compact"
 *   onPhaseClick={(phase) => console.log('Clicked:', phase)}
 * />
 * ```
 */
export const UnifiedPhaseRail: React.FC<UnifiedPhaseRailProps> = ({
    orientation = 'horizontal',
    variant = 'compact',
    showDescriptions = false,
    className = '',
    onPhaseClick,
}) => {
    const { currentPhase, canTransitionTo, navigateToPhase } = usePhaseContext();

    // Get completed phases (simplified - could be enhanced with actual tracking)
    const completedPhases = React.useMemo(() => {
        if (!currentPhase) return [];
        const currentIndex = PHASE_ORDER.indexOf(currentPhase);
        return PHASE_ORDER.slice(0, currentIndex);
    }, [currentPhase]);

    const handlePhaseClick = useCallback((phase: LifecyclePhase) => {
        if (onPhaseClick) {
            onPhaseClick(phase);
        }
        navigateToPhase(phase);
    }, [onPhaseClick, navigateToPhase]);

    // Container classes
    const containerClasses = orientation === 'horizontal'
        ? 'flex items-center gap-1 overflow-x-auto pb-1'
        : 'flex flex-col gap-1';

    return (
        <nav
            className={`${containerClasses} ${className}`}
            aria-label="Lifecycle phases"
            role="navigation"
        >
            {PHASE_ORDER.map((phase, index) => {
                const isActive = currentPhase === phase;
                const isCompleted = completedPhases.includes(phase);
                const isAccessible = canTransitionTo(phase);
                const showConnector = index < PHASE_ORDER.length - 1;

                return (
                    <React.Fragment key={phase}>
                        <PhaseItem
                            phase={phase}
                            isActive={isActive}
                            isCompleted={isCompleted}
                            isAccessible={isAccessible}
                            variant={variant}
                            showDescription={showDescriptions}
                            onClick={() => handlePhaseClick(phase)}
                        />
                        {showConnector && (
                            <PhaseConnector
                                isCompleted={isCompleted}
                                orientation={orientation}
                            />
                        )}
                    </React.Fragment>
                );
            })}
        </nav>
    );
};

export default UnifiedPhaseRail;
