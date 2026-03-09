import { memo } from 'react';
import { useIncident } from '@/hooks/useIncidents';

/**
 * Incident summary panel showing status, impact, and remediation progress.
 *
 * <p><b>Purpose</b><br>
 * Displays incident status (open/resolved/escalated), affected services and users,
 * MTTR tracking, SLA compliance, and timeline of actions taken.
 * Part of HITL Console for incident coordination.
 *
 * <p><b>Features</b><br>
 * - Status badge with color-coding
 * - Impact metrics (affected services, user count)
 * - MTTR (Mean Time To Resolution) tracking
 * - SLA timeline visualization
 * - Action history with timestamps
 * - Escalation chain
 *
 * <p><b>Props</b><br>
 * @param incidentId - Incident identifier (e.g., "INC-42")
 * @param onEscalate - Callback for escalation action
 *
 * @doc.type component
 * @doc.purpose Incident summary panel
 * @doc.layer product
 * @doc.pattern Panel
 */
interface IncidentPanelProps {
    incidentId: string;
    onEscalate?: () => void;
}

interface Incident {
    id: string;
    title: string;
    status: 'open' | 'resolved' | 'escalated';
    severity: 'Low' | 'Medium' | 'High' | 'Critical';
    affectedServices: string[];
    affectedUsers: number;
    startTime: string;
    currentTime: string;
    estimatedResolution: string;
    mttr: string;
    slaRemaining: string;
    slaThreshold: number;
    actionsTaken: Array<{
        timestamp: string;
        action: string;
        actor: string;
    }>;
}

export const IncidentPanel = memo(function IncidentPanel({
    incidentId,
    onEscalate,
}: IncidentPanelProps) {
    // GIVEN: Incident details
    // WHEN: Panel displays status and action history
    // THEN: Show metrics, timeline, and escalation options

    const { data: incident } = useIncident(incidentId);

    // Use API data or fallback structure
    const incidentData = (incident as unknown as Incident) || {
        id: incidentId,
        title: 'Incident details loading...',
        status: 'open' as const,
        severity: 'Medium' as const,
        affectedServices: [],
        affectedUsers: 0,
        startTime: '--:--:--',
        currentTime: '--:--:--',
        estimatedResolution: '--:--:--',
        mttr: '-- -- --',
        slaRemaining: '-- -- --',
        slaThreshold: 90,
        actionsTaken: [],
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'open':
                return 'bg-red-950 text-red-400 border-red-700';
            case 'resolved':
                return 'bg-green-950 text-green-400 border-green-700';
            case 'escalated':
                return 'bg-yellow-950 text-yellow-400 border-yellow-700';
            default:
                return 'bg-slate-800 text-slate-400';
        }
    };

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'Low':
                return 'text-green-400';
            case 'Medium':
                return 'text-yellow-400';
            case 'High':
                return 'text-orange-400';
            case 'Critical':
                return 'text-red-400';
            default:
                return 'text-slate-400';
        }
    };

    const getSLABarColor = () => {
        if (incidentData.slaThreshold > 80) return 'bg-red-500';
        if (incidentData.slaThreshold > 50) return 'bg-yellow-500';
        return 'bg-green-500';
    };

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-start justify-between p-4 border-b border-slate-700 bg-slate-800">
                <div>
                    <div className="text-xs text-slate-500 mb-1 font-mono">{incidentData.id}</div>
                    <h2 className="text-lg font-semibold text-white mb-2">{incidentData.title}</h2>
                    <div className="flex gap-2 mb-2">
                        <span
                            className={`px-2 py-1 rounded text-xs font-medium border ${getStatusColor(
                                incidentData.status,
                            )}`}
                        >
                            {incidentData.status.toUpperCase()}
                        </span>
                        <span className={`px-2 py-1 rounded text-xs font-bold ${getSeverityColor(incidentData.severity)}`}>
                            {incidentData.severity}
                        </span>
                    </div>
                </div>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto">
                {/* Duration & SLA */}
                <div className="p-4 border-b border-slate-700">
                    <div className="grid grid-cols-2 gap-4 mb-4">
                        <div>
                            <div className="text-xs text-slate-500 mb-1">Duration</div>
                            <div className="font-mono text-sm font-bold text-white">{incidentData.mttr}</div>
                        </div>
                        <div>
                            <div className="text-xs text-slate-500 mb-1">Est. Resolution</div>
                            <div className="font-mono text-sm font-bold text-white">{incidentData.estimatedResolution}</div>
                        </div>
                    </div>

                    {/* SLA Progress */}
                    <div className="mb-3">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-xs text-slate-400">SLA Compliance</span>
                            <span className="text-xs font-mono text-yellow-400">{incidentData.slaThreshold}% used</span>
                        </div>
                        <div className="w-full bg-slate-700 rounded-full h-3">
                            <div
                                className={`h-full rounded-full transition-all ${getSLABarColor()}`}
                                style={{ width: `${incidentData.slaThreshold}%` }}
                            />
                        </div>
                        <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1">Remaining: {incidentData.slaRemaining}</div>
                    </div>
                </div>

                {/* Impact */}
                <div className="p-4 border-b border-slate-700">
                    <div className="mb-3">
                        <div className="text-xs text-slate-400 mb-2 font-semibold">AFFECTED SERVICES</div>
                        <div className="flex gap-2 flex-wrap">
                            {incidentData.affectedServices.map((service) => (
                                <span
                                    key={service}
                                    className="px-2 py-1 bg-blue-950 text-blue-300 rounded text-xs font-mono"
                                >
                                    {service}
                                </span>
                            ))}
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <div className="text-xs text-slate-500">Affected Users</div>
                            <div className="text-2xl font-bold text-orange-400 mt-1">{incidentData.affectedUsers.toLocaleString()}</div>
                        </div>
                        <div>
                            <div className="text-xs text-slate-500">Impact Scope</div>
                            <div className="text-2xl font-bold text-orange-400 mt-1">{incidentData.affectedServices.length} services</div>
                        </div>
                    </div>
                </div>

                {/* Timeline */}
                <div className="p-4">
                    <div className="text-xs text-slate-400 mb-3 font-semibold">ACTION HISTORY</div>
                    <div className="space-y-3">
                        {incidentData.actionsTaken.map((item, idx) => (
                            <div key={idx} className="flex gap-3 pb-3 border-b border-slate-700 last:border-0">
                                <div className="flex-shrink-0">
                                    <div className="w-2 h-2 rounded-full bg-slate-500 mt-2" />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-start justify-between gap-2">
                                        <div className="text-xs text-slate-400 font-mono">{item.timestamp}</div>
                                        <div className="text-xs text-slate-500">{item.actor}</div>
                                    </div>
                                    <div className="text-sm text-slate-200 mt-1">{item.action}</div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Action Footer */}
            <div className="border-t border-slate-700 p-4 bg-slate-950 space-y-2">
                <button
                    onClick={onEscalate}
                    className="w-full px-4 py-2 bg-orange-600 hover:bg-orange-500 text-white font-medium rounded transition-colors"
                >
                    📞 Escalate to On-Call
                </button>
                <button className="w-full px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded transition-colors">
                    📋 View Full Runbook
                </button>
            </div>
        </div>
    );
});

export default IncidentPanel;
