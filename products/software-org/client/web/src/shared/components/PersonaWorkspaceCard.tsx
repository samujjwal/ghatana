import { Link } from 'react-router';
import { useMemo } from 'react';
import { PersonaFlowStrip } from './PersonaFlowStrip';
import type { PersonaId, DevSecOpsPhaseId } from '@/shared/types/org';

/**
 * Persona configuration for workspace cards
 */
const PERSONA_CONFIG: Record<PersonaId, {
    icon: string;
    color: string;
    bgColor: string;
    borderColor: string;
    title: string;
    subtitle: string;
    primaryAction: { label: string; href: string };
    secondaryActions: { label: string; href: string; icon: string }[];
}> = {
    engineer: {
        icon: '👨‍💻',
        color: 'text-blue-700 dark:text-blue-300',
        bgColor: 'bg-blue-50 dark:bg-indigo-600/30',
        borderColor: 'border-blue-200 dark:border-blue-800',
        title: 'Engineer Workspace',
        subtitle: 'Build, test, and ship features',
        primaryAction: { label: 'My Stories', href: '/#my-stories' },
        secondaryActions: [
            { label: 'DevSecOps Board', href: '/devsecops/board?persona=engineer', icon: '🔄' },
            { label: 'Workflows', href: '/workflows', icon: '🔗' },
            { label: 'Automation', href: '/automation', icon: '⚙️' },
        ],
    },
    lead: {
        icon: '👔',
        color: 'text-purple-700 dark:text-purple-300',
        bgColor: 'bg-purple-50 dark:bg-violet-600/30',
        borderColor: 'border-purple-200 dark:border-purple-800',
        title: 'Lead Workspace',
        subtitle: 'Oversee team delivery and approvals',
        primaryAction: { label: 'Pending Approvals', href: '/hitl' },
        secondaryActions: [
            { label: 'DevSecOps Board', href: '/devsecops/board?persona=lead', icon: '🔄' },
            { label: 'Reports', href: '/reports', icon: '📈' },
            { label: 'Team Dashboard', href: '/dashboard', icon: '📊' },
        ],
    },
    sre: {
        icon: '🔧',
        color: 'text-orange-700 dark:text-orange-300',
        bgColor: 'bg-orange-50 dark:bg-orange-500/10',
        borderColor: 'border-orange-200 dark:border-orange-800',
        title: 'SRE Workspace',
        subtitle: 'Monitor, respond, and improve reliability',
        primaryAction: { label: 'Real-Time Monitor', href: '/realtime-monitor' },
        secondaryActions: [
            { label: 'DevSecOps Board', href: '/devsecops/board?persona=sre', icon: '🔄' },
            { label: 'Control Tower', href: '/dashboard', icon: '📊' },
            { label: 'ML Observatory', href: '/ml-observatory', icon: '🔬' },
        ],
    },
    security: {
        icon: '🛡️',
        color: 'text-red-700 dark:text-red-300',
        bgColor: 'bg-red-50 dark:bg-rose-600/30',
        borderColor: 'border-red-200 dark:border-red-800',
        title: 'Security Workspace',
        subtitle: 'Manage security posture and compliance',
        primaryAction: { label: 'Security Center', href: '/security' },
        secondaryActions: [
            { label: 'DevSecOps Board', href: '/devsecops/board?persona=security', icon: '🔄' },
            { label: 'Compliance Reports', href: '/reports?type=compliance', icon: '📈' },
            { label: 'Audit Log', href: '/security?tab=audit', icon: '📋' },
        ],
    },
    admin: {
        icon: '⚡',
        color: 'text-emerald-700 dark:text-emerald-300',
        bgColor: 'bg-emerald-50 dark:bg-emerald-900/20',
        borderColor: 'border-emerald-200 dark:border-emerald-800',
        title: 'Admin Workspace',
        subtitle: 'Configure and manage the organization',
        primaryAction: { label: 'Org Builder', href: '/org' },
        secondaryActions: [
            { label: 'Personas', href: '/personas', icon: '👤' },
            { label: 'Settings', href: '/settings', icon: '⚙️' },
            { label: 'Security', href: '/security', icon: '🔒' },
        ],
    },
    viewer: {
        icon: '👁️',
        color: 'text-slate-700 dark:text-neutral-300',
        bgColor: 'bg-slate-50 dark:bg-neutral-800/50',
        borderColor: 'border-slate-200 dark:border-neutral-600',
        title: 'Viewer Workspace',
        subtitle: 'Monitor dashboards and reports',
        primaryAction: { label: 'Control Tower', href: '/dashboard' },
        secondaryActions: [
            { label: 'Reports', href: '/reports', icon: '📈' },
            { label: 'Help Center', href: '/help', icon: '❓' },
        ],
    },
};

/**
 * PersonaWorkspaceCard Props
 */
export interface PersonaWorkspaceCardProps {
    /** Persona identifier */
    personaId: PersonaId;
    /** Current DevSecOps phase (optional) */
    currentPhaseId?: DevSecOpsPhaseId | null;
    /** Whether to show the flow strip */
    showFlowStrip?: boolean;
    /** Whether to show secondary actions */
    showSecondaryActions?: boolean;
    /** Compact mode (smaller padding, no secondary actions) */
    compact?: boolean;
    /** Additional CSS classes */
    className?: string;
}

/**
 * PersonaWorkspaceCard - Persona-specific workspace entry card
 *
 * <p><b>Purpose</b><br>
 * Provides a quick-access card for each persona's workspace with
 * primary action, secondary actions, and DevSecOps flow visualization.
 *
 * <p><b>Features</b><br>
 * - Persona-specific styling and icons
 * - Primary CTA button
 * - Secondary action links
 * - Integrated DevSecOps flow strip
 * - Compact mode for dashboard grids
 * - Dark mode support
 *
 * @doc.type component
 * @doc.purpose Persona workspace entry card
 * @doc.layer shared
 * @doc.pattern Card Component
 */
export function PersonaWorkspaceCard({
    personaId,
    currentPhaseId,
    showFlowStrip = true,
    showSecondaryActions = true,
    compact = false,
    className = '',
}: PersonaWorkspaceCardProps) {
    const config = useMemo(() => PERSONA_CONFIG[personaId], [personaId]);

    if (!config) {
        return null;
    }

    return (
        <div
            className={`
                rounded-xl border ${config.borderColor} ${config.bgColor}
                ${compact ? 'p-4' : 'p-6'}
                transition-shadow hover:shadow-md
                ${className}
            `}
        >
            {/* Header */}
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                    <span className="text-3xl">{config.icon}</span>
                    <div>
                        <h3 className={`font-semibold ${config.color}`}>{config.title}</h3>
                        <p className="text-xs text-slate-500 dark:text-neutral-400">{config.subtitle}</p>
                    </div>
                </div>
            </div>

            {/* Flow Strip */}
            {showFlowStrip && !compact && (
                <div className="mb-4">
                    <PersonaFlowStrip
                        personaId={personaId}
                        currentPhaseId={currentPhaseId}
                        size="sm"
                    />
                </div>
            )}

            {/* Primary Action */}
            <Link
                to={config.primaryAction.href}
                className={`
                    block w-full text-center rounded-lg font-medium transition-colors
                    ${compact ? 'px-3 py-2 text-sm' : 'px-4 py-2.5'}
                    bg-slate-900 dark:bg-white text-white dark:text-slate-900
                    hover:bg-slate-800 dark:hover:bg-slate-100
                `}
            >
                {config.primaryAction.label}
            </Link>

            {/* Secondary Actions */}
            {showSecondaryActions && !compact && config.secondaryActions.length > 0 && (
                <div className="mt-4 flex flex-wrap gap-2">
                    {config.secondaryActions.map((action) => (
                        <Link
                            key={action.href}
                            to={action.href}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-700 dark:text-neutral-300 bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                        >
                            <span>{action.icon}</span>
                            <span>{action.label}</span>
                        </Link>
                    ))}
                </div>
            )}
        </div>
    );
}

/**
 * PersonaWorkspaceGrid - Grid of persona workspace cards
 */
export interface PersonaWorkspaceGridProps {
    /** Personas to display (defaults to all) */
    personas?: PersonaId[];
    /** Current phase for flow strips */
    currentPhaseId?: DevSecOpsPhaseId | null;
    /** Number of columns */
    columns?: 1 | 2 | 3;
    /** Compact mode */
    compact?: boolean;
    /** Additional CSS classes */
    className?: string;
}

export function PersonaWorkspaceGrid({
    personas = ['engineer', 'lead', 'sre', 'security', 'admin'],
    currentPhaseId,
    columns = 2,
    compact = false,
    className = '',
}: PersonaWorkspaceGridProps) {
    const gridCols = {
        1: 'grid-cols-1',
        2: 'grid-cols-1 md:grid-cols-2',
        3: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
    };

    return (
        <div className={`grid ${gridCols[columns]} gap-4 ${className}`}>
            {personas.map((personaId) => (
                <PersonaWorkspaceCard
                    key={personaId}
                    personaId={personaId}
                    currentPhaseId={currentPhaseId}
                    compact={compact}
                />
            ))}
        </div>
    );
}

export default PersonaWorkspaceCard;
