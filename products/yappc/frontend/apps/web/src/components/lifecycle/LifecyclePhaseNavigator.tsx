/**
 * Lifecycle Phase Navigator Component
 * 
 * Provides visual navigation through lifecycle phases with transition validation.
 * Shows current phase, allowed next steps, and provides navigation actions.
 * 
 * @doc.type component
 * @doc.purpose Lifecycle phase navigation UI
 * @doc.layer product
 * @doc.pattern Interactive Navigation Component
 */

import React from 'react';
import {
    LifecyclePhase,
    PHASE_LABELS,
    PHASE_DESCRIPTIONS,
} from '../../types/lifecycle';
import { useLifecyclePhase } from '../../hooks/useLifecyclePhase';

/**
 * Props for LifecyclePhaseNavigator component
 */
export interface LifecyclePhaseNavigatorProps {
    /** Visual layout orientation */
    orientation?: 'horizontal' | 'vertical';
    /** Show only phase names, or include descriptions */
    variant?: 'compact' | 'detailed';
    /** Custom class name */
    className?: string;
}

/**
 * All phases in order
 */
const ALL_PHASES: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

/**
 * Lifecycle Phase Navigator Component
 * 
 * Displays all lifecycle phases with visual indicators for:
 * - Current phase (highlighted)
 * - Accessible phases (clickable)
 * - Inaccessible phases (disabled)
 * 
 * Usage:
 * ```tsx
 * <LifecyclePhaseNavigator orientation="horizontal" variant="compact" />
 * ```
 */
export const LifecyclePhaseNavigator: React.FC<LifecyclePhaseNavigatorProps> = ({
    orientation = 'horizontal',
    variant = 'compact',
    className = '',
}) => {
    const {
        currentPhase,
        projectPhase,
        navigateToPhase,
        canTransitionTo,
        isLoading,
    } = useLifecyclePhase();

    // Use project phase if available, otherwise use current phase from route
    const activePhase = projectPhase ?? currentPhase;

    const handlePhaseClick = (phase: LifecyclePhase) => {
        if (canTransitionTo(phase)) {
            navigateToPhase(phase);
        }
    };

    const containerClasses = orientation === 'horizontal'
        ? 'flex items-center gap-2 overflow-x-auto'
        : 'flex flex-col gap-2';

    return (
        <nav
            className={`${containerClasses} ${className}`}
            aria-label="Lifecycle phase navigation"
            role="navigation"
        >
            {ALL_PHASES.map((phase, index) => {
                const isActive = activePhase === phase;
                const isAccessible = activePhase ? canTransitionTo(phase) : false;
                const isPast = activePhase ? phase < activePhase : false;
                const isFuture = activePhase ? phase > activePhase : false;

                return (
                    <React.Fragment key={phase}>
                        <PhaseButton
                            phase={phase}
                            isActive={isActive}
                            isAccessible={isAccessible}
                            isPast={isPast}
                            isFuture={isFuture}
                            variant={variant}
                            onClick={() => handlePhaseClick(phase)}
                            disabled={isLoading || !isAccessible}
                        />

                        {/* Connector between phases */}
                        {index < ALL_PHASES.length - 1 && (
                            <PhaseConnector
                                isCompleted={isPast}
                                orientation={orientation}
                            />
                        )}
                    </React.Fragment>
                );
            })}
        </nav>
    );
};

/**
 * Individual phase button component
 */
interface PhaseButtonProps {
    phase: LifecyclePhase;
    isActive: boolean;
    isAccessible: boolean;
    isPast: boolean;
    isFuture: boolean; // Reserved for future styling enhancements
    variant: 'compact' | 'detailed';
    onClick: () => void;
    disabled: boolean;
}

const PhaseButton: React.FC<PhaseButtonProps> = ({
    phase,
    isActive,
    isAccessible,
    isPast,
    isFuture: _isFuture, // Reserved for future styling
    variant,
    onClick,
    disabled,
}) => {
    const label = PHASE_LABELS[phase];
    const description = PHASE_DESCRIPTIONS[phase];

    // Determine button styling based on state
    let buttonClasses = 'relative flex flex-col items-center justify-center rounded-lg transition-all duration-200';

    if (isActive) {
        buttonClasses += ' bg-blue-600 text-white shadow-lg scale-105';
    } else if (isPast) {
        buttonClasses += ' bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300';
    } else if (isAccessible) {
        buttonClasses += ' bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 cursor-pointer';
    } else {
        // isFuture phases that aren't accessible yet
        buttonClasses += ' bg-gray-50 text-gray-400 dark:bg-gray-900/50 dark:text-gray-600 opacity-50 cursor-not-allowed';
    }

    const sizeClasses = variant === 'compact' ? 'px-3 py-2 text-sm' : 'px-4 py-3 text-base';

    return (
        <button
            onClick={onClick}
            disabled={disabled}
            className={`${buttonClasses} ${sizeClasses}`}
            title={description}
            aria-label={`${label} phase${isActive ? ' (current)' : ''}${!isAccessible ? ' (not accessible)' : ''}`}
            aria-current={isActive ? 'step' : undefined}
        >
            <span className="font-semibold">{label}</span>
            {variant === 'detailed' && (
                <span className="text-xs opacity-80 mt-1">{description}</span>
            )}
            {isActive && (
                <span className="absolute -top-1 -right-1 w-3 h-3 bg-white rounded-full border-2 border-blue-600" />
            )}
        </button>
    );
};

/**
 * Connector line between phases
 */
interface PhaseConnectorProps {
    isCompleted: boolean;
    orientation: 'horizontal' | 'vertical';
}

const PhaseConnector: React.FC<PhaseConnectorProps> = ({ isCompleted, orientation }) => {
    const baseClasses = 'flex-shrink-0 transition-colors duration-200';
    const colorClasses = isCompleted
        ? 'bg-green-500 dark:bg-green-600'
        : 'bg-gray-300 dark:bg-gray-700';

    const sizeClasses = orientation === 'horizontal'
        ? 'w-8 h-0.5'
        : 'h-8 w-0.5';

    return (
        <div
            className={`${baseClasses} ${colorClasses} ${sizeClasses}`}
            role="presentation"
            aria-hidden="true"
        />
    );
};

export default LifecyclePhaseNavigator;
