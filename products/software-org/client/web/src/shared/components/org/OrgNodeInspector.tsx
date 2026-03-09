import { Link } from 'react-router';
import type {
    OrgGraphNode,
    DepartmentConfig,
    ServiceConfig,
    WorkflowConfig,
    IntegrationConfig,
    PersonaBinding,
} from '@/shared/types/org';

/**
 * OrgNodeInspector Props
 */
export interface OrgNodeInspectorProps {
    /** Selected node to inspect */
    node: OrgGraphNode | null;
    /** Callback to close the inspector */
    onClose?: () => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Status badge component
 */
function StatusBadge({ status }: { status: string }) {
    const colors: Record<string, string> = {
        healthy: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
        degraded: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
        down: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
        unknown: 'bg-slate-100 text-slate-800 dark:bg-neutral-800 dark:text-neutral-300',
    };

    return (
        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${colors[status] || colors.unknown}`}>
            {status}
        </span>
    );
}

/**
 * Risk level badge component
 */
function RiskBadge({ level }: { level: string }) {
    const colors: Record<string, string> = {
        critical: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
        high: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
        medium: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
        low: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    };

    return (
        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${colors[level] || colors.low}`}>
            {level}
        </span>
    );
}

/**
 * Info row component
 */
function InfoRow({ label, value, children }: { label: string; value?: string | number; children?: React.ReactNode }) {
    return (
        <div className="flex justify-between items-start py-2 border-b border-slate-100 dark:border-neutral-600 last:border-0">
            <span className="text-xs text-slate-500 dark:text-neutral-400">{label}</span>
            {children || <span className="text-sm text-slate-900 dark:text-neutral-100 font-medium">{value}</span>}
        </div>
    );
}

/**
 * Quick action link component
 */
function QuickActionLink({ to, icon, label }: { to: string; icon: string; label: string }) {
    return (
        <Link
            to={to}
            className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
        >
            <span>{icon}</span>
            <span>{label}</span>
        </Link>
    );
}

/**
 * Department inspector content
 */
function DepartmentInspector({ data }: { data: DepartmentConfig }) {
    return (
        <>
            <div className="space-y-1">
                <InfoRow label="Owner" value={data.owner} />
                <InfoRow label="Members" value={data.members.length} />
                <InfoRow label="Services" value={data.serviceIds.length} />
                <InfoRow label="Workflows" value={data.workflowIds.length} />
            </div>

            <div className="mt-4">
                <h4 className="text-xs font-semibold text-slate-500 dark:text-neutral-400 uppercase tracking-wide mb-2">
                    Quick Actions
                </h4>
                <div className="space-y-1">
                    <QuickActionLink to={`/departments/${data.id}`} icon="📋" label="View Department" />
                    <QuickActionLink to={`/devsecops/board?department=${data.id}`} icon="🔄" label="DevSecOps Board" />
                    <QuickActionLink to={`/reports?department=${data.id}`} icon="📈" label="Department Reports" />
                </div>
            </div>
        </>
    );
}

/**
 * Service inspector content
 */
function ServiceInspector({ data }: { data: ServiceConfig }) {
    return (
        <>
            <div className="space-y-1">
                <InfoRow label="Tier" value={data.tier} />
                <InfoRow label="Risk Level">
                    <RiskBadge level={data.riskLevel} />
                </InfoRow>
                <InfoRow label="Dependencies" value={data.dependencies.length} />
                <InfoRow label="Dependents" value={data.dependents.length} />
                <InfoRow label="Environments" value={data.environments.join(', ')} />
            </div>

            <div className="mt-4 p-3 rounded-lg bg-slate-50 dark:bg-neutral-800/50">
                <h4 className="text-xs font-semibold text-slate-500 dark:text-neutral-400 uppercase tracking-wide mb-2">
                    SLO Targets
                </h4>
                <div className="grid grid-cols-3 gap-2 text-center">
                    <div>
                        <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">{data.slo.availability}%</div>
                        <div className="text-[10px] text-slate-500">Availability</div>
                    </div>
                    <div>
                        <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">{data.slo.latencyP95Ms}ms</div>
                        <div className="text-[10px] text-slate-500">P95 Latency</div>
                    </div>
                    <div>
                        <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">{data.slo.errorRateThreshold}%</div>
                        <div className="text-[10px] text-slate-500">Error Rate</div>
                    </div>
                </div>
            </div>

            <div className="mt-4">
                <h4 className="text-xs font-semibold text-slate-500 dark:text-neutral-400 uppercase tracking-wide mb-2">
                    Quick Actions
                </h4>
                <div className="space-y-1">
                    <QuickActionLink to={`/realtime-monitor?service=${data.id}`} icon="⏱️" label="Real-Time Monitor" />
                    <QuickActionLink to={`/devsecops/board?service=${data.id}`} icon="🔄" label="DevSecOps Board" />
                    <QuickActionLink to={`/models?service=${data.id}`} icon="📦" label="Model Catalog" />
                </div>
            </div>
        </>
    );
}

/**
 * Workflow inspector content
 */
function WorkflowInspector({ data }: { data: WorkflowConfig }) {
    return (
        <>
            <div className="space-y-1">
                <InfoRow label="Trigger" value={data.trigger} />
                <InfoRow label="Steps" value={data.steps.length} />
                <InfoRow label="Departments" value={data.departmentIds.length} />
                <InfoRow label="Services" value={data.serviceIds.length} />
                <InfoRow label="Status">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${data.enabled
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
                        : 'bg-slate-100 text-slate-800 dark:bg-neutral-800 dark:text-neutral-300'
                        }`}>
                        {data.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                </InfoRow>
            </div>

            <div className="mt-4">
                <h4 className="text-xs font-semibold text-slate-500 dark:text-neutral-400 uppercase tracking-wide mb-2">
                    Quick Actions
                </h4>
                <div className="space-y-1">
                    <QuickActionLink to={`/workflows?id=${data.id}`} icon="🔗" label="View Workflow" />
                    <QuickActionLink to={`/automation?workflow=${data.id}`} icon="⚙️" label="Automation Engine" />
                </div>
            </div>
        </>
    );
}

/**
 * Integration inspector content
 */
function IntegrationInspector({ data }: { data: IntegrationConfig }) {
    return (
        <>
            <div className="space-y-1">
                <InfoRow label="Type" value={data.type} />
                <InfoRow label="Status">
                    <StatusBadge status={data.status} />
                </InfoRow>
                <InfoRow label="Departments" value={data.departmentIds.length} />
                <InfoRow label="Services" value={data.serviceIds.length} />
                <InfoRow label="Managed By" value={data.managedByPersonas.join(', ')} />
            </div>

            <div className="mt-4">
                <h4 className="text-xs font-semibold text-slate-500 dark:text-neutral-400 uppercase tracking-wide mb-2">
                    Quick Actions
                </h4>
                <div className="space-y-1">
                    {data.configPath && (
                        <QuickActionLink to={data.configPath} icon="⚙️" label="Configure Integration" />
                    )}
                    <QuickActionLink to="/settings" icon="🔧" label="Settings" />
                </div>
            </div>
        </>
    );
}

/**
 * Persona inspector content
 */
function PersonaInspector({ data }: { data: PersonaBinding }) {
    return (
        <>
            <div className="space-y-1">
                <InfoRow label="Departments" value={data.departmentIds.length} />
                <InfoRow label="Services" value={data.serviceIds.length} />
                <InfoRow label="Workflows" value={data.workflowIds.length} />
                <InfoRow label="Permissions" value={data.permissions.length} />
                <InfoRow label="Default Phases" value={data.defaultPhases.length} />
            </div>

            <div className="mt-4">
                <h4 className="text-xs font-semibold text-slate-500 dark:text-neutral-400 uppercase tracking-wide mb-2">
                    Quick Actions
                </h4>
                <div className="space-y-1">
                    <QuickActionLink to={`/personas/${data.personaId}`} icon="👤" label="Persona Workspace" />
                    <QuickActionLink to={`/devsecops/board?persona=${data.personaId}`} icon="🔄" label="DevSecOps Board" />
                </div>
            </div>
        </>
    );
}

/**
 * OrgNodeInspector - Contextual inspector panel for selected org nodes
 *
 * <p><b>Purpose</b><br>
 * Displays detailed information about a selected node in the Org Graph,
 * with quick actions to navigate to related pages.
 *
 * <p><b>Features</b><br>
 * - Type-specific content for departments, services, workflows, integrations, personas
 * - SLO visualization for services
 * - Status indicators for integrations
 * - Quick action links to related pages
 * - Responsive design with dark mode support
 *
 * @doc.type component
 * @doc.purpose Node detail inspector for Org Builder
 * @doc.layer shared
 * @doc.pattern Inspector Panel
 */
export function OrgNodeInspector({ node, onClose, className = '' }: OrgNodeInspectorProps) {
    if (!node) {
        return (
            <div className={`p-4 text-center text-slate-500 dark:text-neutral-400 ${className}`}>
                <span className="text-3xl mb-2 block">👆</span>
                <p className="text-sm">Select a node to view details</p>
            </div>
        );
    }

    const typeIcons: Record<string, string> = {
        department: '🏢',
        service: '⚙️',
        workflow: '🔄',
        integration: '🔌',
        persona: '👤',
    };

    return (
        <div className={`p-4 ${className}`}>
            {/* Header */}
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-2">
                    <span className="text-2xl">{node.style?.icon || typeIcons[node.type]}</span>
                    <div>
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100">{node.label}</h3>
                        <p className="text-xs text-slate-500 dark:text-neutral-400 capitalize">{node.type}</p>
                    </div>
                </div>
                {onClose && (
                    <button
                        type="button"
                        onClick={onClose}
                        className="p-1 rounded hover:bg-slate-100 dark:hover:bg-slate-700 text-slate-500"
                        aria-label="Close inspector"
                    >
                        ✕
                    </button>
                )}
            </div>

            {/* Description */}
            {'description' in node.data && node.data.description && (
                <p className="text-sm text-slate-600 dark:text-neutral-300 mb-4">
                    {node.data.description}
                </p>
            )}

            {/* Type-specific content */}
            {node.type === 'department' && <DepartmentInspector data={node.data as DepartmentConfig} />}
            {node.type === 'service' && <ServiceInspector data={node.data as ServiceConfig} />}
            {node.type === 'workflow' && <WorkflowInspector data={node.data as WorkflowConfig} />}
            {node.type === 'integration' && <IntegrationInspector data={node.data as IntegrationConfig} />}
            {node.type === 'persona' && <PersonaInspector data={node.data as PersonaBinding} />}
        </div>
    );
}

export default OrgNodeInspector;
