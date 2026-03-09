import { Link, useLocation } from 'react-router';
import { useMemo } from 'react';
import type { PersonaId, DevSecOpsPhaseId } from '@/shared/types/org';

/**
 * Flow step configuration
 */
interface FlowStep {
    stepId: string;
    phaseId: DevSecOpsPhaseId;
    label: string;
    route: string;
    icon?: string;
    description?: string;
}

/**
 * Persona flow configurations - derived from devsecopsEngineerFlow.ts
 */
const PERSONA_FLOWS: Record<PersonaId, { name: string; description: string; steps: FlowStep[] }> = {
    engineer: {
        name: 'Engineer Flow',
        description: 'End-to-end development lifecycle',
        steps: [
            { stepId: 'eng-1', phaseId: 'intake', label: 'View Story', route: '/work-items', icon: '📋' },
            { stepId: 'eng-2', phaseId: 'plan', label: 'Plan Implementation', route: '/work-items', icon: '📝' },
            { stepId: 'eng-3', phaseId: 'build', label: 'Development', route: '/work-items', icon: '💻' },
            { stepId: 'eng-4', phaseId: 'verify', label: 'Run Tests', route: '/automation', icon: '🧪' },
            { stepId: 'eng-5', phaseId: 'review', label: 'Code Review', route: '/work-items', icon: '👀' },
            { stepId: 'eng-6', phaseId: 'staging', label: 'Staging Deploy', route: '/models', icon: '🚀' },
            { stepId: 'eng-7', phaseId: 'deploy', label: 'Production Deploy', route: '/models', icon: '🎯' },
            { stepId: 'eng-8', phaseId: 'operate', label: 'Monitor', route: '/dashboard', icon: '📊' },
            { stepId: 'eng-9', phaseId: 'learn', label: 'Retrospective', route: '/reports', icon: '📈' },
        ],
    },
    lead: {
        name: 'Lead Flow',
        description: 'Team oversight and approvals',
        steps: [
            { stepId: 'lead-1', phaseId: 'intake', label: 'Review Backlog', route: '/devsecops/board?persona=lead', icon: '📋' },
            { stepId: 'lead-2', phaseId: 'plan', label: 'Sprint Planning', route: '/work-items', icon: '📝' },
            { stepId: 'lead-3', phaseId: 'review', label: 'Pending Approvals', route: '/hitl', icon: '✅' },
            { stepId: 'lead-4', phaseId: 'staging', label: 'Release Review', route: '/models', icon: '🔍' },
            { stepId: 'lead-5', phaseId: 'deploy', label: 'Deploy Approval', route: '/hitl', icon: '🚀' },
            { stepId: 'lead-6', phaseId: 'operate', label: 'Team Dashboard', route: '/dashboard', icon: '📊' },
            { stepId: 'lead-7', phaseId: 'learn', label: 'Team Reports', route: '/reports', icon: '📈' },
        ],
    },
    sre: {
        name: 'SRE Flow',
        description: 'Incident response and reliability',
        steps: [
            { stepId: 'sre-1', phaseId: 'intake', label: 'Triage Alerts', route: '/realtime-monitor', icon: '🚨' },
            { stepId: 'sre-2', phaseId: 'plan', label: 'Plan Remediation', route: '/work-items', icon: '📝' },
            { stepId: 'sre-3', phaseId: 'verify', label: 'Validate Fix', route: '/automation', icon: '🧪' },
            { stepId: 'sre-4', phaseId: 'deploy', label: 'Deploy Fix', route: '/models', icon: '🚀' },
            { stepId: 'sre-5', phaseId: 'operate', label: 'Monitor Recovery', route: '/dashboard', icon: '📊' },
            { stepId: 'sre-6', phaseId: 'learn', label: 'Post-Mortem', route: '/reports', icon: '📈' },
        ],
    },
    security: {
        name: 'Security Flow',
        description: 'Security and compliance management',
        steps: [
            { stepId: 'sec-1', phaseId: 'intake', label: 'Review Posture', route: '/security', icon: '🛡️' },
            { stepId: 'sec-2', phaseId: 'plan', label: 'Plan Controls', route: '/work-items', icon: '📝' },
            { stepId: 'sec-3', phaseId: 'verify', label: 'Security Scan', route: '/automation', icon: '🔍' },
            { stepId: 'sec-4', phaseId: 'operate', label: 'Monitor Compliance', route: '/security', icon: '📊' },
            { stepId: 'sec-5', phaseId: 'learn', label: 'Compliance Report', route: '/reports?type=compliance', icon: '📈' },
        ],
    },
    admin: {
        name: 'Admin Flow',
        description: 'Organization configuration',
        steps: [
            { stepId: 'admin-1', phaseId: 'intake', label: 'Org Overview', route: '/org', icon: '🏗️' },
            { stepId: 'admin-2', phaseId: 'plan', label: 'Configure Personas', route: '/personas', icon: '👤' },
            { stepId: 'admin-3', phaseId: 'build', label: 'Manage Integrations', route: '/settings?tab=integrations', icon: '🔌' },
            { stepId: 'admin-4', phaseId: 'operate', label: 'Security Center', route: '/security', icon: '🔒' },
            { stepId: 'admin-5', phaseId: 'learn', label: 'Audit Reports', route: '/reports?type=audit', icon: '📈' },
        ],
    },
    viewer: {
        name: 'Viewer Flow',
        description: 'Read-only monitoring',
        steps: [
            { stepId: 'viewer-1', phaseId: 'operate', label: 'Control Tower', route: '/dashboard', icon: '📊' },
            { stepId: 'viewer-2', phaseId: 'learn', label: 'Reports', route: '/reports', icon: '📈' },
        ],
    },
};

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
 * PersonaFlowSidebar Props
 */
export interface PersonaFlowSidebarProps {
    /** Persona identifier */
    personaId: PersonaId;
    /** Current active step (optional) */
    currentStepId?: string | null;
    /** Whether to show phase groupings */
    showPhaseGroups?: boolean;
    /** Compact mode */
    compact?: boolean;
    /** Additional CSS classes */
    className?: string;
}

/**
 * PersonaFlowSidebar - Persona-specific workflow checklist sidebar
 *
 * <p><b>Purpose</b><br>
 * Displays a checklist of workflow steps for a specific persona,
 * with links to the relevant pages and visual indicators for
 * the current step.
 *
 * <p><b>Features</b><br>
 * - Persona-specific step configurations
 * - Current step highlighting
 * - Phase grouping (optional)
 * - Compact mode for embedded use
 * - Dark mode support
 *
 * @doc.type component
 * @doc.purpose Persona workflow checklist sidebar
 * @doc.layer shared
 * @doc.pattern Navigation Component
 */
export function PersonaFlowSidebar({
    personaId,
    currentStepId,
    showPhaseGroups = true,
    compact = false,
    className = '',
}: PersonaFlowSidebarProps) {
    const location = useLocation();
    const flow = PERSONA_FLOWS[personaId];

    // Group steps by phase if enabled
    const groupedSteps = useMemo(() => {
        if (!showPhaseGroups) {
            return [{ phase: null, steps: flow?.steps || [] }];
        }

        const groups: { phase: DevSecOpsPhaseId | null; steps: FlowStep[] }[] = [];
        let currentPhase: DevSecOpsPhaseId | null = null;

        (flow?.steps || []).forEach((step) => {
            if (step.phaseId !== currentPhase) {
                currentPhase = step.phaseId;
                groups.push({ phase: currentPhase, steps: [] });
            }
            groups[groups.length - 1].steps.push(step);
        });

        return groups;
    }, [flow, showPhaseGroups]);

    // Check if a step is active based on current route
    const isStepActive = (step: FlowStep) => {
        if (currentStepId) {
            return step.stepId === currentStepId;
        }
        // Fallback: match by route
        return location.pathname.startsWith(step.route.split('?')[0]);
    };

    if (!flow) {
        return null;
    }

    return (
        <div className={`bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-neutral-600 ${className}`}>
            {/* Header */}
            <div className={`border-b border-slate-200 dark:border-neutral-600 ${compact ? 'p-3' : 'p-4'}`}>
                <h3 className={`font-semibold text-slate-900 dark:text-neutral-100 ${compact ? 'text-sm' : 'text-base'}`}>
                    {flow.name}
                </h3>
                <p className={`text-slate-500 dark:text-neutral-400 ${compact ? 'text-xs' : 'text-sm'} mt-0.5`}>
                    {flow.description}
                </p>
            </div>

            {/* Steps */}
            <div className={compact ? 'p-2' : 'p-3'}>
                {groupedSteps.map((group, groupIndex) => (
                    <div key={group.phase || groupIndex} className={groupIndex > 0 ? 'mt-4' : ''}>
                        {/* Phase header */}
                        {showPhaseGroups && group.phase && (
                            <div className={`text-[10px] font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500 ${compact ? 'mb-1 px-2' : 'mb-2 px-3'}`}>
                                {PHASE_LABELS[group.phase]}
                            </div>
                        )}

                        {/* Steps in this phase */}
                        <div className="space-y-1">
                            {group.steps.map((step) => {
                                const isActive = isStepActive(step);
                                return (
                                    <Link
                                        key={step.stepId}
                                        to={step.route}
                                        className={`
                                            flex items-center gap-2 rounded-md transition-colors
                                            ${compact ? 'px-2 py-1.5 text-xs' : 'px-3 py-2 text-sm'}
                                            ${isActive
                                                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-200 font-medium'
                                                : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                                            }
                                        `}
                                    >
                                        {step.icon && <span className={compact ? 'text-sm' : 'text-base'}>{step.icon}</span>}
                                        <span className="flex-1 truncate">{step.label}</span>
                                        {isActive && (
                                            <span className="w-1.5 h-1.5 rounded-full bg-blue-600 dark:bg-blue-400" />
                                        )}
                                    </Link>
                                );
                            })}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default PersonaFlowSidebar;
