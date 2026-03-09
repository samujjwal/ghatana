import { useState } from "react";
import { useNavigate } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useIncidents } from '@/hooks/useOperateApi';
import { AlertTriangle, AlertCircle, Clock, User, CheckCircle2, Filter } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Incidents Explorer
 *
 * <p><b>Purpose</b><br>
 * View and manage active incidents with filtering and triage capabilities.
 * Provides SREs and incident commanders with incident management interface.
 *
 * <p><b>Features</b><br>
 * - Incident list with severity and status filtering
 * - Real-time incident stats
 * - Affected services display
 * - Quick navigation to incident details
 *
 * @doc.type component
 * @doc.purpose Incident management list
 * @doc.layer product
 * @doc.pattern Page
 */
export function IncidentsExplorer() {
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const [filterStatus, setFilterStatus] = useState<'all' | 'active' | 'investigating' | 'mitigating' | 'resolved'>('all');
    const [filterSeverity, setFilterSeverity] = useState<'all' | 'critical' | 'high' | 'medium' | 'low'>('all');

    const tenantId = selectedTenant || 'acme-payments-id';

    const statusParam = filterStatus === 'all' ? undefined : filterStatus;
    const severityParam = filterSeverity === 'all' ? undefined : filterSeverity;

    const { data: incidentsData, isLoading, error } = useIncidents(tenantId, statusParam, severityParam);

    const incidents = incidentsData?.data || [];

    // Calculate stats
    const stats = {
        total: incidents.length,
        critical: incidents.filter(i => i.severity === 'critical').length,
        active: incidents.filter(i => i.status === 'active').length,
        resolved: incidents.filter(i => i.status === 'resolved').length,
    };

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

    if (error) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load incidents: {error.message}
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Incidents</h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-1">
                    Active incident management and tracking
                </p>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Incidents" value={stats.total} icon={<AlertTriangle className="h-5 w-5" />} />
                <StatCard label="Critical" value={stats.critical} icon={<AlertTriangle className="h-5 w-5 text-red-600" />} />
                <StatCard label="Active" value={stats.active} icon={<AlertCircle className="h-5 w-5 text-amber-500" />} />
                <StatCard label="Resolved" value={stats.resolved} icon={<CheckCircle2 className="h-5 w-5 text-green-500" />} />
            </div>

            {/* Filters */}
            <div className="flex items-center gap-4 flex-wrap">
                <div className="flex items-center gap-2">
                    <Filter className="h-4 w-4 text-slate-500" />
                    <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">Status:</span>
                    <div className="flex gap-2">
                        {['all', 'active', 'investigating', 'mitigating', 'resolved'].map((status) => (
                            <button
                                key={status}
                                onClick={() => setFilterStatus(status as typeof filterStatus)}
                                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                                    filterStatus === status
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                                }`}
                            >
                                {status.charAt(0).toUpperCase() + status.slice(1)}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">Severity:</span>
                    <div className="flex gap-2">
                        {['all', 'critical', 'high', 'medium', 'low'].map((severity) => (
                            <button
                                key={severity}
                                onClick={() => setFilterSeverity(severity as typeof filterSeverity)}
                                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                                    filterSeverity === severity
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                                }`}
                            >
                                {severity.charAt(0).toUpperCase() + severity.slice(1)}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {/* Incidents List */}
            {isLoading ? (
                <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                    Loading incidents...
                </div>
            ) : incidents.length === 0 ? (
                <div className="text-center py-12 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700">
                    <CheckCircle2 className="h-12 w-12 text-green-500 mx-auto mb-4" />
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                        No Incidents Found
                    </h3>
                    <p className="text-sm text-slate-500 dark:text-neutral-500">
                        {filterStatus !== 'all' || filterSeverity !== 'all'
                            ? 'No incidents match the selected filters'
                            : 'All systems operational'}
                    </p>
                </div>
            ) : (
                <div className="space-y-4">
                    {incidents.map((incident) => {
                        const SeverityIcon = severityConfig[incident.severity].icon;
                        return (
                            <div
                                key={incident.id}
                                onClick={() => navigate(`/operate/incident-detail/${incident.id}`)}
                                className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6 hover:shadow-lg dark:hover:shadow-slate-700/30 transition-all cursor-pointer"
                            >
                                <div className="flex items-start justify-between mb-4">
                                    <div className="flex items-start gap-3 flex-1">
                                        <div className={`p-2 rounded-lg ${severityConfig[incident.severity].bg}`}>
                                            <SeverityIcon className={`h-5 w-5 ${severityConfig[incident.severity].color}`} />
                                        </div>
                                        <div className="flex-1">
                                            <div className="flex items-center gap-3 mb-2">
                                                <h3 className="font-semibold text-lg text-slate-900 dark:text-neutral-100">
                                                    {incident.title}
                                                </h3>
                                                <Badge variant={severityConfig[incident.severity].variant}>
                                                    {incident.severity}
                                                </Badge>
                                                <Badge variant={statusConfig[incident.status].variant}>
                                                    {statusConfig[incident.status].label}
                                                </Badge>
                                            </div>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400 mb-3">
                                                {incident.description}
                                            </p>
                                            <div className="flex items-center gap-4 text-sm text-slate-500 dark:text-neutral-500">
                                                <div className="flex items-center gap-1">
                                                    <span className="font-mono font-semibold">{incident.id}</span>
                                                </div>
                                                {incident.assignee && (
                                                    <div className="flex items-center gap-1">
                                                        <User className="h-4 w-4" />
                                                        {incident.assignee}
                                                    </div>
                                                )}
                                                <div className="flex items-center gap-1">
                                                    <Clock className="h-4 w-4" />
                                                    {new Date(incident.createdAt).toLocaleString()}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Affected Services */}
                                {incident.affectedServices.length > 0 && (
                                    <div className="flex items-center gap-2 pt-4 border-t border-slate-200 dark:border-slate-700">
                                        <span className="text-sm text-slate-600 dark:text-neutral-400">Affected Services:</span>
                                        <div className="flex gap-2 flex-wrap">
                                            {incident.affectedServices.map((service) => (
                                                <span
                                                    key={service}
                                                    className="px-2 py-1 text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-neutral-300 rounded"
                                                >
                                                    {service}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

// Helper component
function StatCard({ label, value, icon }: { label: string; value: number; icon: React.ReactNode }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center justify-between">
                <div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{label}</div>
                </div>
                <div className="text-slate-400 dark:text-neutral-500">
                    {icon}
                </div>
            </div>
        </div>
    );
}
