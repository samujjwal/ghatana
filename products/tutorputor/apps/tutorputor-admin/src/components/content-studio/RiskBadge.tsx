/**
 * Risk Badge Component
 * 
 * Displays risk level indicators for AI-generated content.
 * Shows LOW (green), MEDIUM (yellow), HIGH (red) risk levels.
 */

import { clsx } from 'clsx';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

interface RiskBadgeProps {
    riskLevel: RiskLevel;
    showLabel?: boolean;
    size?: 'sm' | 'md' | 'lg';
    className?: string;
}

const riskColors: Record<RiskLevel, { bg: string; text: string; border: string }> = {
    LOW: {
        bg: 'bg-green-100',
        text: 'text-green-800',
        border: 'border-green-200',
    },
    MEDIUM: {
        bg: 'bg-yellow-100',
        text: 'text-yellow-800',
        border: 'border-yellow-200',
    },
    HIGH: {
        bg: 'bg-red-100',
        text: 'text-red-800',
        border: 'border-red-200',
    },
};

const riskLabels: Record<RiskLevel, string> = {
    LOW: 'Low Risk',
    MEDIUM: 'Medium Risk',
    HIGH: 'High Risk - Review Required',
};

const sizeClasses = {
    sm: 'px-1.5 py-0.5 text-xs',
    md: 'px-2 py-1 text-sm',
    lg: 'px-3 py-1.5 text-base',
};

export function RiskBadge({
    riskLevel,
    showLabel = true,
    size = 'md',
    className
}: RiskBadgeProps) {
    const colors = riskColors[riskLevel];

    return (
        <span
            className={clsx(
                'inline-flex items-center gap-1 rounded-full border font-medium',
                colors.bg,
                colors.text,
                colors.border,
                sizeClasses[size],
                className
            )}
            title={riskLabels[riskLevel]}
        >
            <span
                className={clsx(
                    'inline-block rounded-full',
                    size === 'sm' ? 'h-1.5 w-1.5' : size === 'md' ? 'h-2 w-2' : 'h-2.5 w-2.5',
                    riskLevel === 'LOW' && 'bg-green-500',
                    riskLevel === 'MEDIUM' && 'bg-yellow-500',
                    riskLevel === 'HIGH' && 'bg-red-500'
                )}
            />
            {showLabel && <span>{riskLevel}</span>}
        </span>
    );
}

interface ConfidenceIndicatorProps {
    score: number;
    showPercentage?: boolean;
    size?: 'sm' | 'md' | 'lg';
    className?: string;
}

export function ConfidenceIndicator({
    score,
    showPercentage = true,
    size = 'md',
    className,
}: ConfidenceIndicatorProps) {
    const percentage = Math.round(score * 100);

    const getColor = () => {
        if (percentage >= 80) return 'bg-green-500';
        if (percentage >= 60) return 'bg-yellow-500';
        return 'bg-red-500';
    };

    const barHeight = size === 'sm' ? 'h-1' : size === 'md' ? 'h-1.5' : 'h-2';
    const textSize = size === 'sm' ? 'text-xs' : size === 'md' ? 'text-sm' : 'text-base';

    return (
        <div className={clsx('flex items-center gap-2', className)}>
            <div className={clsx('flex-1 rounded-full bg-gray-200', barHeight)}>
                <div
                    className={clsx('rounded-full transition-all duration-300', barHeight, getColor())}
                    style={{ width: `${percentage}%` }}
                />
            </div>
            {showPercentage && (
                <span className={clsx('font-medium text-gray-600', textSize)}>
                    {percentage}%
                </span>
            )}
        </div>
    );
}

interface GuardrailStatusProps {
    passed: boolean;
    flags: string[];
    className?: string;
}

export function GuardrailStatus({ passed, flags, className }: GuardrailStatusProps) {
    if (passed && flags.length === 0) {
        return (
            <div className={clsx('flex items-center gap-2 text-green-600', className)}>
                <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                    <path
                        fillRule="evenodd"
                        d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                        clipRule="evenodd"
                    />
                </svg>
                <span className="text-sm font-medium">All guardrails passed</span>
            </div>
        );
    }

    return (
        <div className={clsx('space-y-2', className)}>
            <div className="flex items-center gap-2 text-amber-600">
                <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                    <path
                        fillRule="evenodd"
                        d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                        clipRule="evenodd"
                    />
                </svg>
                <span className="text-sm font-medium">
                    {flags.length} guardrail {flags.length === 1 ? 'flag' : 'flags'}
                </span>
            </div>
            <ul className="ml-7 list-disc space-y-1 text-sm text-gray-600">
                {flags.map((flag, idx) => (
                    <li key={idx}>{flag.replace(/_/g, ' ')}</li>
                ))}
            </ul>
        </div>
    );
}
