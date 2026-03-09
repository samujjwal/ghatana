import { useState, useEffect } from 'react';
import { Link } from 'react-router';
import { getWorkspacePreset, type WorkspacePreset } from '@/config/workspacePresets';
import type { PersonaId } from '@/shared/types/org';

/**
 * WorkspaceOnboardingBanner Props
 */
export interface WorkspaceOnboardingBannerProps {
    /** Persona identifier */
    personaId: PersonaId;
    /** Whether to show the banner (controlled mode) */
    show?: boolean;
    /** Callback when banner is dismissed */
    onDismiss?: () => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Theme color mappings for personas
 */
const THEME_COLORS: Record<string, { bg: string; border: string; text: string; accent: string }> = {
    blue: {
        bg: 'bg-blue-50 dark:bg-blue-950/30',
        border: 'border-blue-200 dark:border-blue-800',
        text: 'text-blue-900 dark:text-blue-100',
        accent: 'text-blue-600 dark:text-indigo-400',
    },
    purple: {
        bg: 'bg-purple-50 dark:bg-purple-950/30',
        border: 'border-purple-200 dark:border-purple-800',
        text: 'text-purple-900 dark:text-purple-100',
        accent: 'text-purple-600 dark:text-violet-400',
    },
    orange: {
        bg: 'bg-orange-50 dark:bg-orange-950/30',
        border: 'border-orange-200 dark:border-orange-800',
        text: 'text-orange-900 dark:text-orange-100',
        accent: 'text-orange-600 dark:text-orange-400',
    },
    red: {
        bg: 'bg-red-50 dark:bg-red-950/30',
        border: 'border-red-200 dark:border-red-800',
        text: 'text-red-900 dark:text-red-100',
        accent: 'text-red-600 dark:text-rose-400',
    },
    slate: {
        bg: 'bg-slate-100 dark:bg-neutral-800/50',
        border: 'border-slate-300 dark:border-neutral-600',
        text: 'text-slate-900 dark:text-neutral-200',
        accent: 'text-slate-600 dark:text-neutral-400',
    },
    gray: {
        bg: 'bg-slate-50 dark:bg-slate-900/50',
        border: 'border-slate-200 dark:border-neutral-600',
        text: 'text-slate-900 dark:text-neutral-200',
        accent: 'text-slate-600 dark:text-neutral-400',
    },
};

/**
 * WorkspaceOnboardingBanner - Persona-specific onboarding banner
 *
 * <p><b>Purpose</b><br>
 * Displays a welcome banner with AI-style guidance for a persona workspace.
 * Includes primary and secondary CTAs, and can be dismissed with localStorage persistence.
 *
 * <p><b>Features</b><br>
 * - Persona-specific theming
 * - AI guidance message
 * - Primary and secondary CTAs
 * - Dismissible with localStorage persistence
 * - Dark mode support
 *
 * @doc.type component
 * @doc.purpose Persona workspace onboarding banner
 * @doc.layer shared
 * @doc.pattern Onboarding Component
 */
export function WorkspaceOnboardingBanner({
    personaId,
    show: controlledShow,
    onDismiss,
    className = '',
}: WorkspaceOnboardingBannerProps) {
    const preset = getWorkspacePreset(personaId);

    // Internal visibility state (uncontrolled mode)
    const [internalShow, setInternalShow] = useState(() => {
        if (typeof window === 'undefined') return true;
        if (!preset) return false;
        return window.localStorage.getItem(preset.onboarding.dismissalKey) !== 'true';
    });

    // Use controlled or uncontrolled mode
    const isVisible = controlledShow !== undefined ? controlledShow : internalShow;

    const handleDismiss = () => {
        if (preset) {
            window.localStorage.setItem(preset.onboarding.dismissalKey, 'true');
        }
        setInternalShow(false);
        onDismiss?.();
    };

    if (!preset || !isVisible) {
        return null;
    }

    const colors = THEME_COLORS[preset.themeColor] || THEME_COLORS.blue;
    const { onboarding } = preset;

    return (
        <div className={`${colors.bg} ${colors.border} border rounded-lg p-5 ${className}`}>
            <div className="flex items-start justify-between gap-4">
                <div className="flex-1">
                    {/* Header */}
                    <div className="flex items-center gap-3 mb-2">
                        <span className="text-2xl">{preset.icon}</span>
                        <h2 className={`text-lg font-semibold ${colors.text}`}>
                            {onboarding.title}
                        </h2>
                    </div>

                    {/* Subtitle */}
                    <p className={`text-sm ${colors.accent} mb-3`}>
                        {onboarding.subtitle}
                    </p>

                    {/* AI Guidance */}
                    <div className="bg-white/50 dark:bg-slate-900/50 rounded-md p-3 mb-4">
                        <div className="flex items-start gap-2">
                            <span className="text-sm">🤖</span>
                            <p className={`text-sm ${colors.text} opacity-90`}>
                                {onboarding.aiGuidance}
                            </p>
                        </div>
                    </div>

                    {/* CTAs */}
                    <div className="flex flex-wrap items-center gap-3">
                        <Link
                            to={onboarding.primaryCtaHref}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium transition-colors"
                        >
                            {onboarding.primaryCtaLabel}
                            <span>→</span>
                        </Link>
                        {onboarding.secondaryCtaLabel && onboarding.secondaryCtaHref && (
                            <Link
                                to={onboarding.secondaryCtaHref}
                                className={`inline-flex items-center gap-2 px-4 py-2 ${colors.bg} ${colors.border} border rounded-lg text-sm font-medium ${colors.text} hover:opacity-80 transition-opacity`}
                            >
                                {onboarding.secondaryCtaLabel}
                            </Link>
                        )}
                    </div>
                </div>

                {/* Dismiss button */}
                <button
                    onClick={handleDismiss}
                    className={`p-1 rounded hover:bg-black/5 dark:hover:bg-white/5 ${colors.accent} transition-colors`}
                    aria-label="Dismiss"
                >
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                </button>
            </div>
        </div>
    );
}

/**
 * WorkspaceTipCard - Displays a random tip for a persona
 */
export interface WorkspaceTipCardProps {
    /** Persona identifier */
    personaId: PersonaId;
    /** Additional CSS classes */
    className?: string;
}

export function WorkspaceTipCard({ personaId, className = '' }: WorkspaceTipCardProps) {
    const preset = getWorkspacePreset(personaId);
    const [tipIndex, setTipIndex] = useState(0);

    useEffect(() => {
        if (preset) {
            setTipIndex(Math.floor(Math.random() * preset.tips.length));
        }
    }, [preset]);

    if (!preset || preset.tips.length === 0) {
        return null;
    }

    const colors = THEME_COLORS[preset.themeColor] || THEME_COLORS.blue;
    const tip = preset.tips[tipIndex];

    return (
        <div className={`${colors.bg} ${colors.border} border rounded-lg p-4 ${className}`}>
            <div className="flex items-start gap-3">
                <span className="text-lg">💡</span>
                <div>
                    <p className={`text-sm font-medium ${colors.text} mb-1`}>Tip</p>
                    <p className={`text-sm ${colors.accent}`}>{tip}</p>
                </div>
            </div>
        </div>
    );
}

/**
 * WorkspaceMetricHighlights - Displays key metrics for a persona
 */
export interface WorkspaceMetricHighlightsProps {
    /** Persona identifier */
    personaId: PersonaId;
    /** Metric data (keyed by dataKey) */
    data?: Record<string, number | string>;
    /** Additional CSS classes */
    className?: string;
}

export function WorkspaceMetricHighlights({ personaId, data = {}, className = '' }: WorkspaceMetricHighlightsProps) {
    const preset = getWorkspacePreset(personaId);

    if (!preset) {
        return null;
    }

    const getMetricStatus = (metric: typeof preset.metricHighlights[0], value: number): 'healthy' | 'warning' | 'critical' => {
        if (metric.criticalThreshold !== undefined && value <= metric.criticalThreshold) return 'critical';
        if (metric.warningThreshold !== undefined && value <= metric.warningThreshold) return 'warning';
        return 'healthy';
    };

    const getStatusColor = (status: 'healthy' | 'warning' | 'critical') => {
        switch (status) {
            case 'critical': return 'text-red-600 dark:text-rose-400';
            case 'warning': return 'text-yellow-600 dark:text-yellow-400';
            default: return 'text-green-600 dark:text-green-400';
        }
    };

    return (
        <div className={`grid grid-cols-2 md:grid-cols-4 gap-4 ${className}`}>
            {preset.metricHighlights.map((metric) => {
                const value = data[metric.dataKey];
                const numValue = typeof value === 'number' ? value : 0;
                const status = getMetricStatus(metric, numValue);

                return (
                    <div
                        key={metric.id}
                        className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-neutral-600 p-4"
                    >
                        <div className="flex items-center gap-2 mb-2">
                            <span className="text-lg">{metric.icon}</span>
                            <span className="text-xs text-slate-500 dark:text-neutral-400">{metric.label}</span>
                        </div>
                        <div className={`text-2xl font-bold ${getStatusColor(status)}`}>
                            {value ?? '—'}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

export default WorkspaceOnboardingBanner;
