import { useParams, useNavigate } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useIncident, useAcknowledgeIncident, useAssignIncident, useUpdateIncidentStatus } from '@/hooks/useOperateApi';
import {
    ArrowLeft,
    AlertTriangle,
    AlertCircle,
    User,
    Clock,
    CheckCircle2,
    Package,
    Activity,
    ExternalLink,
    Zap,
} from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Incident Detail
 *
 * <p><b>Purpose</b><br>
 * Detailed incident view with timeline, affected services, and action buttons.
 * Enables incident triage, assignment, and status updates.
 *
 * <p><b>Features</b><br>
 * - Incident timeline with events
 * - Affected services with links
 * - Assignment and acknowledgment
 * - Status updates (investigating, mitigating, resolved)
 * - Related workflows and metrics
 *
 * @doc.type component
 * @doc.purpose Incident detail and management
 * @doc.layer product
 * @doc.pattern Detail
 */
export function IncidentDetail() {
    const { incidentId } = useParams();
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';

    const { data: incident, isLoading, error } = useIncident(incidentId || '', tenantId);
    const acknowledgeMutation = useAcknowledgeIncident();
    const assignMutation = useAssignIncident();
    const updateStatusMutation = useUpdateIncidentStatus();

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="text-slate-600 dark:text-neutral-400">Loading incident details...</div>
            </div>
        );
    }

    if (error || !incident) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load incident: {error?.message || 'Incident not found'}
                </div>
            </div>
        );
    }

    const severityConfig = {
        'critical': { variant: 'danger' as const, color: 'text-red-600', bg: 'bg-red-50 dark:bg-red-900/20', icon: AlertTriangle },
        'high': { variant: 'danger' as const, color: 'text-red-500', bg: 'bg-red-50 dark:bg-red-900/20', icon: AlertTriangle },
        'medium': { variant: 'warning' as const, color: 'text-amber-500', bg: 'bg-amber-50 dark:bg-amber-900/20', icon: AlertCircle },
        'low': { variant: 'neutral' as const, color: 'text-slate-500', bg: 'bg-slate-50 dark:bg-slate-800', icon: AlertCircle },
    };

    const statusConfig = {
        'active': { variant: 'danger' as const, label: 'Active' },
        'investigating': { variant: 'warning' as const, label: 'Investigating' },
        'mitigating': { variant: 'warning' as const, label: 'Mitigating' },
        'resolved': { variant: 'success' as const, label: 'Resolved' },
    };

    const SeverityIcon = severityConfig[incident.severity].icon;

    const handleAcknowledge = async () => {
        try {
            await acknowledgeMutation.mutateAsync({
                incidentId: incident.id,
                tenantId,
                userId: 'current-user',
            });
        } catch (error) {
            console.error('Failed to acknowledge incident:', error);
        }
    };

    const handleAssignToMe = async () => {
        try {
            await assignMutation.mutateAsync({
                incidentId: incident.id,
                tenantId,
                assigneeId: 'current-user',
            });
        } catch (error) {
            console.error('Failed to assign incident:', error);
        }
    };

    const handleUpdateStatus = async (newStatus: string) => {
        try {
            await updateStatusMutation.mutateAsync({
                incidentId: incident.id,
                tenantId,
                status: newStatus,
            });
        } catch (error) {
            console.error('Failed to update status:', error);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <button
                        onClick={() => navigate('/operate/incidents')}
                        className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
                    >
                        <ArrowLeft className="h-5 w-5 text-slate-600 dark:text-neutral-400" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className={`p-3 rounded-lg ${severityConfig[incident.severity].bg}`}>
                            <SeverityIcon className={`h-6 w-6 ${severityConfig[incident.severity].color}`} />
                        </div>
                        <div>
                            <div className="flex items-center gap-3 mb-1">
                                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">{incident.title}</h1>
                                <Badge variant={severityConfig[incident.severity].variant}>
                                    {incident.severity}
                                </Badge>
                                <Badge variant={statusConfig[incident.status].variant}>
                                    {statusConfig[incident.status].label}
                                </Badge>
                            </div>
                            <p className="text-slate-600 dark:text-neutral-400">{incident.id}</p>
                        </div>
                    </div>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={handleAcknowledge}
                        disabled={acknowledgeMutation.isPending}
                        className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors disabled:opacity-50"
                    >
                        <CheckCircle2 className="h-4 w-4" />
                        Acknowledge
                    </button>
                    {!incident.assignee && (
                        <button
                            onClick={handleAssignToMe}
                            disabled={assignMutation.isPending}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
                        >
                            <User className="h-4 w-4" />
                            Assign to Me
                        </button>
                    )}
                </div>
            </div>

            {/* Description */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">Description</h2>
                <p className="text-slate-600 dark:text-neutral-400">{incident.description}</p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Content */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Timeline */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                            <Clock className="h-5 w-5" />
                            Timeline
                        </h2>
                        <div className="space-y-4">
                            {incident.timeline.map((event, index) => (
                                <div key={index} className="flex gap-4">
                                    <div className="flex flex-col items-center">
                                        <div className="w-3 h-3 bg-blue-500 rounded-full" />
                                        {index < incident.timeline.length - 1 && (
                                            <div className="w-0.5 h-full bg-slate-200 dark:bg-slate-700 mt-2" />
                                        )}
                                    </div>
                                    <div className="flex-1 pb-4">
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {event.event}
                                        </div>
                                        <div className="text-sm text-slate-500 dark:text-neutral-500 mt-1">
                                            {event.actor} • {new Date(event.timestamp).toLocaleString()}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Affected Services */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                            <Package className="h-5 w-5" />
                            Affected Services
                        </h2>
                        <div className="space-y-2">
                            {incident.affectedServices.map((service, index) => (
                                <button
                                    key={service}
                                    onClick={() => navigate(`/admin/services/${incident.affectedServicesIds[index]}`)}
                                    className="w-full flex items-center justify-between p-3 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                                >
                                    <div className="flex items-center gap-3">
                                        <Package className="h-4 w-4 text-slate-400" />
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">{service}</span>
                                    </div>
                                    <ExternalLink className="h-4 w-4 text-slate-400" />
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Related Workflows */}
                    {incident.relatedWorkflows.length > 0 && (
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                                <Zap className="h-5 w-5" />
                                Related Workflows
                            </h2>
                            <div className="space-y-2">
                                {incident.relatedWorkflows.map((workflowId) => (
                                    <button
                                        key={workflowId}
                                        onClick={() => navigate(`/build/workflows/${workflowId}`)}
                                        className="w-full flex items-center justify-between p-3 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                                    >
                                        <div className="flex items-center gap-3">
                                            <Zap className="h-4 w-4 text-purple-500" />
                                            <span className="font-mono text-sm text-slate-900 dark:text-neutral-100">{workflowId}</span>
                                        </div>
                                        <ExternalLink className="h-4 w-4 text-slate-400" />
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Related Metrics */}
                    {incident.relatedMetrics.length > 0 && (
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                                <Activity className="h-5 w-5" />
                                Related Metrics
                            </h2>
                            <div className="space-y-2">
                                {incident.relatedMetrics.map((metricId) => (
                                    <button
                                        key={metricId}
                                        onClick={() => navigate(`/observe/metric-detail/${metricId}`)}
                                        className="w-full flex items-center justify-between p-3 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                                    >
                                        <div className="flex items-center gap-3">
                                            <Activity className="h-4 w-4 text-blue-500" />
                                            <span className="font-mono text-sm text-slate-900 dark:text-neutral-100">{metricId}</span>
                                        </div>
                                        <ExternalLink className="h-4 w-4 text-slate-400" />
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* Sidebar */}
                <div className="space-y-6">
                    {/* Metadata */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Details</h2>
                        <div className="space-y-4">
                            <div>
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Assignee</div>
                                <div className="font-medium text-slate-900 dark:text-neutral-100">
                                    {incident.assignee || 'Unassigned'}
                                </div>
                            </div>
                            <div>
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Created</div>
                                <div className="text-sm text-slate-900 dark:text-neutral-100">
                                    {new Date(incident.createdAt).toLocaleString()}
                                </div>
                            </div>
                            <div>
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Last Updated</div>
                                <div className="text-sm text-slate-900 dark:text-neutral-100">
                                    {new Date(incident.updatedAt).toLocaleString()}
                                </div>
                            </div>
                            {incident.rootCause && (
                                <div>
                                    <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Root Cause</div>
                                    <div className="text-sm text-slate-900 dark:text-neutral-100">
                                        {incident.rootCause}
                                    </div>
                                </div>
                            )}
                            {incident.mitigation && (
                                <div>
                                    <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Mitigation</div>
                                    <div className="text-sm text-slate-900 dark:text-neutral-100">
                                        {incident.mitigation}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Actions */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Actions</h2>
                        <div className="space-y-2">
                            {incident.status !== 'resolved' && (
                                <>
                                    {incident.status === 'active' && (
                                        <button
                                            onClick={() => handleUpdateStatus('investigating')}
                                            disabled={updateStatusMutation.isPending}
                                            className="w-full px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 transition-colors disabled:opacity-50"
                                        >
                                            Start Investigation
                                        </button>
                                    )}
                                    {incident.status === 'investigating' && (
                                        <button
                                            onClick={() => handleUpdateStatus('mitigating')}
                                            disabled={updateStatusMutation.isPending}
                                            className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
                                        >
                                            Start Mitigation
                                        </button>
                                    )}
                                    {incident.status === 'mitigating' && (
                                        <button
                                            onClick={() => handleUpdateStatus('resolved')}
                                            disabled={updateStatusMutation.isPending}
                                            className="w-full px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
                                        >
                                            Mark Resolved
                                        </button>
                                    )}
                                </>
                            )}
                            <button
                                onClick={() => navigate('/observe/reports')}
                                className="w-full px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Generate Report
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
