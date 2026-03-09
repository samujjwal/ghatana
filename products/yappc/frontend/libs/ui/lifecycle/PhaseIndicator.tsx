/**
 * Phase Indicator Component
 * 
 * Shared component for displaying lifecycle phases with consistent styling
 * across the application. Uses centralized design tokens.
 * 
 * @doc.type component
 * @doc.purpose Unified phase display component
 * @doc.layer shared
 * @doc.pattern Component Library
 */

import React from 'react';
import type { LifecyclePhase } from '../../../apps/web/src/types/lifecycle';
import { PHASE_LABELS, PHASE_COLORS } from '../../../apps/web/src/styles/design-tokens';

interface PhaseIndicatorProps {
    phase: LifecyclePhase;
    /** Display style variant */
    variant?: 'badge' | 'card' | 'pill' | 'minimal';
    /** Size of the indicator */
    size?: 'sm' | 'md' | 'lg';
    /** Show phase icon */
    showIcon?: boolean;
    /** Show phase number */
    showNumber?: boolean;
    /** Custom className for additional styling */
    className?: string;
    /** Click handler */
    onClick?: () => void;
}

const PHASE_NUMBERS: Record<LifecyclePhase, number> = {
    INTENT: 1,
    SHAPE: 2,
    VALIDATE: 3,
    GENERATE: 4,
    RUN: 5,
    OBSERVE: 6,
    IMPROVE: 7,
};

/**
 * PhaseIndicator - Displays a lifecycle phase with consistent styling
 * 
 * @example
 * ```tsx
 * <PhaseIndicator phase="INTENT" variant="badge" showIcon />
 * <PhaseIndicator phase="SHAPE" variant="card" size="lg" />
 * ```
 */
export const PhaseIndicator: React.FC<PhaseIndicatorProps> = ({
    phase,
    variant = 'badge',
    size = 'md',
    showIcon = true,
    showNumber = false,
    className = '',
    onClick,
}) => {
    const colors = PHASE_COLORS[phase];
    const label = PHASE_LABELS[phase];
    const icon = colors.icon;
    const number = PHASE_NUMBERS[phase];

    // Size classes
    const sizeClasses = {
        sm: 'text-xs px-2 py-0.5 gap-1',
        md: 'text-sm px-3 py-1 gap-1.5',
        lg: 'text-base px-4 py-2 gap-2',
    };

    // Variant-specific styles
    const getVariantClasses = () => {
        switch (variant) {
            case 'badge':
                return `inline-flex items-center rounded-full font-medium ${colors.bg} ${colors.text} ${colors.border} border`;
            case 'card':
                return `flex items-center rounded-lg ${colors.bg} ${colors.text} ${colors.border} border-2 shadow-sm`;
            case 'pill':
                return `inline-flex items-center rounded-full font-semibold ${colors.activeBg} ${colors.text}`;
            case 'minimal':
                return `inline-flex items-center ${colors.text} font-medium`;
            default:
                return '';
        }
    };

    const baseClasses = `
        ${getVariantClasses()}
        ${sizeClasses[size]}
        ${onClick ? 'cursor-pointer hover:opacity-80 transition-opacity' : ''}
        ${className}
    `.trim();

    return (
        <div className={baseClasses} onClick={onClick}>
            {showIcon && <span className="text-current">{icon}</span>}
            {showNumber && <span className="font-bold">{number}.</span>}
            <span>{label}</span>
        </div>
    );
};

/**
 * PhaseGradientBadge - Displays phase with gradient background
 */
export const PhaseGradientBadge: React.FC<Omit<PhaseIndicatorProps, 'variant'>> = (props) => {
    const colors = PHASE_COLORS[props.phase];
    const label = PHASE_LABELS[props.phase];
    const icon = colors.icon;

    return (
        <div
            className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-gradient-to-r ${colors.gradient} text-white font-semibold shadow-md ${props.className || ''}`}
            onClick={props.onClick}
        >
            {props.showIcon && <span className="text-lg">{icon}</span>}
            {props.showNumber && <span className="font-bold">{PHASE_NUMBERS[props.phase]}.</span>}
            <span>{label}</span>
        </div>
    );
};

/**
 * PhaseProgressIndicator - Shows phase with completion status
 */
interface PhaseProgressIndicatorProps extends PhaseIndicatorProps {
    /** Completion percentage (0-100) */
    progress?: number;
    /** Total artifacts in phase */
    totalArtifacts?: number;
    /** Completed artifacts */
    completedArtifacts?: number;
}

export const PhaseProgressIndicator: React.FC<PhaseProgressIndicatorProps> = ({
    phase,
    progress,
    totalArtifacts,
    completedArtifacts,
    ...props
}) => {
    const colors = PHASE_COLORS[phase];
    const label = PHASE_LABELS[phase];
    const icon = colors.icon;
    const number = PHASE_NUMBERS[phase];

    const calculatedProgress = progress ?? (totalArtifacts && completedArtifacts
        ? Math.round((completedArtifacts / totalArtifacts) * 100)
        : 0);

    return (
        <div className={`flex flex-col gap-2 ${props.className || ''}`}>
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                    {props.showIcon && <span className={colors.text}>{icon}</span>}
                    {props.showNumber && <span className={`${colors.text} font-bold`}>{number}.</span>}
                    <span className={`${colors.text} font-semibold`}>{label}</span>
                </div>
                <span className={`text-sm ${colors.text} font-medium`}>
                    {calculatedProgress}%
                </span>
            </div>

            {/* Progress bar */}
            <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                    className={`h-full bg-gradient-to-r ${colors.gradient} transition-all duration-300`}
                    style={{ width: `${calculatedProgress}%` }}
                />
            </div>

            {/* Artifact count */}
            {totalArtifacts !== undefined && completedArtifacts !== undefined && (
                <div className="text-xs text-gray-600">
                    {completedArtifacts} of {totalArtifacts} artifacts complete
                </div>
            )}
        </div>
    );
};
