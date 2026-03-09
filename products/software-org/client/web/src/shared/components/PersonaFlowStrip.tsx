import { useNavigate } from 'react-router';
import { useMemo, useCallback } from 'react';
import type { PersonaId, DevSecOpsPhaseId, DevSecOpsFlowConfig } from '@/shared/types/org';

/**
 * Phase labels for display
 */
const PHASE_LABELS: Record<DevSecOpsPhaseId, string> = {
    intake: 'Intake',
    plan: 'Plan',
    build: 'Build',
    verify: 'Verify',
    review: 'Review',
    staging: 'Staging',
    deploy: 'Deploy',
    operate: 'Operate',
    learn: 'Learn',
};

/**
 * Default flow configurations for each persona
 */
const DEFAULT_PERSONA_FLOWS: Record<PersonaId, { phases: DevSecOpsPhaseId[]; description: string }> = {
    engineer: {
        phases: ['intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy', 'operate', 'learn'],
        description: 'Full development lifecycle from story to production',
    },
    lead: {
        phases: ['intake', 'plan', 'review', 'staging', 'deploy', 'operate', 'learn'],
        description: 'Oversight and approval flow for team deliverables',
    },
    sre: {
        phases: ['intake', 'plan', 'verify', 'deploy', 'operate', 'learn'],
        description: 'Incident response and reliability operations',
    },
    security: {
        phases: ['intake', 'plan', 'verify', 'operate', 'learn'],
        description: 'Security posture and compliance management',
    },
    admin: {
        phases: ['intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy', 'operate', 'learn'],
        description: 'Full organizational oversight',
    },
    viewer: {
        phases: ['operate', 'learn'],
        description: 'Read-only monitoring and reporting',
    },
};

/**
 * PersonaFlowStrip Props
 */
export interface PersonaFlowStripProps {
    /** Persona identifier */
    personaId: PersonaId;
    /** Current active phase (optional) */
    currentPhaseId?: DevSecOpsPhaseId | null;
    /** Custom flow configuration (overrides default) */
    flowConfig?: DevSecOpsFlowConfig;
    /** Callback when a phase is clicked */
    onPhaseClick?: (phaseId: DevSecOpsPhaseId) => void;
    /** Whether to show phase descriptions on hover */
    showDescriptions?: boolean;
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Additional CSS classes */
    className?: string;
}

/**
 * PersonaFlowStrip - Persona-specific DevSecOps flow visualization
 *
 * <p><b>Purpose</b><br>
 * Displays the DevSecOps phases relevant to a specific persona, with
 * visual indicators for the current phase and clickable navigation.
 *
 * <p><b>Features</b><br>
 * - Persona-specific phase filtering
 * - Current phase highlighting
 * - Clickable phases for navigation
 * - Completed/upcoming phase styling
 * - Responsive sizing
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <PersonaFlowStrip
 *   personaId="engineer"
 *   currentPhaseId="build"
 *   onPhaseClick={(phase) => navigate(`/devsecops/board?phase=${phase}`)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Persona-specific DevSecOps flow strip
 * @doc.layer shared
 * @doc.pattern Flow Visualization
 */
export function PersonaFlowStrip({
    personaId,
    currentPhaseId,
    flowConfig,
    onPhaseClick,
    showDescriptions = false,
    size = 'md',
    className = '',
}: PersonaFlowStripProps) {
    const navigate = useNavigate();

    // Get phases for this persona
    const phases = useMemo(() => {
        if (flowConfig?.phases) {
            return flowConfig.phases;
        }
        return DEFAULT_PERSONA_FLOWS[personaId]?.phases || [];
    }, [personaId, flowConfig]);

    // Get current phase index
    const currentIndex = useMemo(() => {
        if (!currentPhaseId) return -1;
        return phases.indexOf(currentPhaseId);
    }, [phases, currentPhaseId]);

    // Handle phase click
    const handlePhaseClick = useCallback(
        (phaseId: DevSecOpsPhaseId) => {
            if (onPhaseClick) {
                onPhaseClick(phaseId);
            } else {
                // Default: navigate to IC tasks with phase filter
                navigate(`/ic/tasks?phase=${phaseId}`);
            }
        },
        [onPhaseClick, navigate]
    );

    // Size classes
    const sizeClasses = {
        sm: { pill: 'px-2 py-0.5 text-[10px]', dot: 'h-1.5 w-1.5', gap: 'gap-1' },
        md: { pill: 'px-3 py-1 text-xs', dot: 'h-2 w-2', gap: 'gap-2' },
        lg: { pill: 'px-4 py-1.5 text-sm', dot: 'h-2.5 w-2.5', gap: 'gap-3' },
    };

    const sizeConfig = sizeClasses[size];

    if (phases.length === 0) {
        return null;
    }

    return (
        <div className={`flex items-center ${sizeConfig.gap} ${className}`}>
            {phases.map((phaseId, index) => {
                const isCompleted = currentIndex !== -1 && index < currentIndex;
                const isCurrent = currentIndex !== -1 && index === currentIndex;
                const isClickable = typeof onPhaseClick === 'function' || true; // Always clickable by default

                let pillClasses = `inline-flex items-center ${sizeConfig.gap} rounded-full ${sizeConfig.pill} whitespace-nowrap transition-all`;
                let dotClasses = `inline-flex ${sizeConfig.dot} rounded-full`;

                if (isClickable) {
                    pillClasses += ' cursor-pointer hover:shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-indigo-500';
                }

                if (isCurrent) {
                    pillClasses += ' bg-indigo-600 text-white shadow-md';
                    dotClasses += ' bg-indigo-300';
                } else if (isCompleted) {
                    pillClasses += ' bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300 hover:bg-emerald-200 dark:hover:bg-emerald-900/50';
                    dotClasses += ' bg-emerald-500';
                } else {
                    pillClasses += ' bg-slate-100 text-slate-600 dark:bg-neutral-800 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700';
                    dotClasses += ' bg-slate-400';
                }

                return (
                    <div key={phaseId} className="flex items-center gap-2 flex-1 min-w-0">
                        <button
                            type="button"
                            onClick={() => handlePhaseClick(phaseId)}
                            className={pillClasses}
                            title={showDescriptions ? `${PHASE_LABELS[phaseId]} phase` : undefined}
                        >
                            <span className={dotClasses} />
                            <span className="truncate">{PHASE_LABELS[phaseId]}</span>
                        </button>
                        {index < phases.length - 1 && (
                            <div className="flex-1 h-px bg-slate-200 dark:bg-neutral-700 min-w-[8px]" />
                        )}
                    </div>
                );
            })}
        </div>
    );
}

/**
 * PersonaFlowCard - Card wrapper for PersonaFlowStrip with title and description
 */
export interface PersonaFlowCardProps extends PersonaFlowStripProps {
    /** Card title */
    title?: string;
    /** Show the persona's flow description */
    showFlowDescription?: boolean;
}

export function PersonaFlowCard({
    personaId,
    title,
    showFlowDescription = true,
    ...stripProps
}: PersonaFlowCardProps) {
    const flowDescription = DEFAULT_PERSONA_FLOWS[personaId]?.description || '';

    return (
        <div className="rounded-lg border border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 p-4">
            {(title || showFlowDescription) && (
                <div className="mb-3">
                    {title && (
                        <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                            {title}
                        </h3>
                    )}
                    {showFlowDescription && flowDescription && (
                        <p className="text-xs text-slate-500 dark:text-neutral-400 mt-0.5">
                            {flowDescription}
                        </p>
                    )}
                </div>
            )}
            <PersonaFlowStrip personaId={personaId} {...stripProps} />
        </div>
    );
}

export default PersonaFlowStrip;
