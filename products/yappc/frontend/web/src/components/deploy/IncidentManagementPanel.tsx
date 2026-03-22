/**
 * Incident Management Panel
 * 
 * Incident tracking, root cause analysis, and postmortem management for OBSERVE phase.
 * 
 * @doc.type component
 * @doc.purpose OBSERVE phase incident management
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState } from 'react';
import { AlertCircle as ErrorIcon, CheckCircle, Clock as Schedule, User as Person, Plus as Add } from 'lucide-react';
import { useLifecycleArtifacts } from '../../services/canvas/lifecycle';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

export interface IncidentManagementPanelProps {
    projectId: string;
}

interface Incident {
    id: string;
    title: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'open' | 'investigating' | 'mitigated' | 'resolved';
    detectedAt: string;
    resolvedAt?: string;
    assignedTo?: string;
    impact: string;
    rootCause?: string;
    timeline: TimelineEvent[];
}

interface TimelineEvent {
    timestamp: string;
    event: string;
    user?: string;
}

export const IncidentManagementPanel: React.FC<IncidentManagementPanelProps> = ({ projectId }) => {
    const { createArtifact } = useLifecycleArtifacts(projectId);
    const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
    const [setShowNewIncidentDialog] = useState(false);

    // Mock incidents (in production, fetch from incident management system)
    const [incidents] = useState<Incident[]>([
        {
            id: 'inc-001',
            title: 'API Response Time Degradation',
            severity: 'high',
            status: 'resolved',
            detectedAt: '2026-01-10T08:30:00Z',
            resolvedAt: '2026-01-10T10:15:00Z',
            assignedTo: 'John Doe',
            impact: '25% of API requests experiencing 3-5s delays',
            rootCause: 'Database connection pool exhaustion due to memory leak in ORM query builder',
            timeline: [
                {
                    timestamp: '2026-01-10T08:30:00Z',
                    event: 'Alert triggered: API latency p95 > 3s',
                },
                {
                    timestamp: '2026-01-10T08:35:00Z',
                    event: 'Incident created and assigned to on-call engineer',
                    user: 'System',
                },
                {
                    timestamp: '2026-01-10T08:45:00Z',
                    event: 'Identified database connection pool exhaustion',
                    user: 'John Doe',
                },
                {
                    timestamp: '2026-01-10T09:00:00Z',
                    event: 'Mitigation: Restarted application servers to clear connections',
                    user: 'John Doe',
                },
                {
                    timestamp: '2026-01-10T09:30:00Z',
                    event: 'Root cause identified: memory leak in ORM',
                    user: 'John Doe',
                },
                {
                    timestamp: '2026-01-10T10:00:00Z',
                    event: 'Deployed hotfix with connection pool cleanup',
                    user: 'John Doe',
                },
                {
                    timestamp: '2026-01-10T10:15:00Z',
                    event: 'Incident resolved, monitoring for 24h',
                    user: 'John Doe',
                },
            ],
        },
        {
            id: 'inc-002',
            title: 'Payment Gateway Timeout',
            severity: 'critical',
            status: 'investigating',
            detectedAt: '2026-01-10T11:20:00Z',
            assignedTo: 'Jane Smith',
            impact: 'Payment processing failing for 100% of transactions',
            timeline: [
                {
                    timestamp: '2026-01-10T11:20:00Z',
                    event: 'Alert triggered: Payment success rate < 50%',
                },
                {
                    timestamp: '2026-01-10T11:22:00Z',
                    event: 'Incident escalated to critical',
                    user: 'System',
                },
                {
                    timestamp: '2026-01-10T11:25:00Z',
                    event: 'Investigating third-party payment gateway status',
                    user: 'Jane Smith',
                },
            ],
        },
        {
            id: 'inc-003',
            title: 'Disk Space Alert - Production DB',
            severity: 'medium',
            status: 'mitigated',
            detectedAt: '2026-01-09T14:00:00Z',
            assignedTo: 'Bob Wilson',
            impact: 'Database disk usage at 85%, approaching threshold',
            timeline: [
                {
                    timestamp: '2026-01-09T14:00:00Z',
                    event: 'Alert triggered: Disk usage > 80%',
                },
                {
                    timestamp: '2026-01-09T14:30:00Z',
                    event: 'Cleaned up old logs, gained 15GB',
                    user: 'Bob Wilson',
                },
                {
                    timestamp: '2026-01-09T15:00:00Z',
                    event: 'Scheduled disk expansion for next maintenance window',
                    user: 'Bob Wilson',
                },
            ],
        },
    ]);

    const handleSaveIncidentReport = (incident: Incident) => {
        const userId = 'current-user'; // NOTE: Get from auth

        const reportPayload = {
            incidentId: incident.id,
            title: incident.title,
            severity: incident.severity,
            detectedAt: incident.detectedAt,
            resolvedAt: incident.resolvedAt,
            impact: incident.impact,
            rootCause: incident.rootCause,
            timeline: incident.timeline,
            mitigations: [
                'Implemented connection pool monitoring',
                'Added automated cleanup job',
            ],
            followUps: [
                'Upgrade ORM to latest version',
                'Review all query patterns for leaks',
                'Add load testing to CI/CD',
            ],
        };

        createArtifact(LifecycleArtifactKind.INCIDENT_REPORT, userId);
        // In production, also update the artifact with payload
    };

    const getSeverityColor = (severity: Incident['severity']) => {
        switch (severity) {
            case 'critical': return 'bg-error-color text-white';
            case 'high': return 'bg-warning-color text-white';
            case 'medium': return 'bg-blue-500 text-white';
            case 'low': return 'bg-grey-400 text-white';
        }
    };

    const getStatusColor = (status: Incident['status']) => {
        switch (status) {
            case 'resolved': return 'text-success-color';
            case 'mitigated': return 'text-blue-600';
            case 'investigating': return 'text-warning-color';
            case 'open': return 'text-error-color';
        }
    };

    const getStatusIcon = (status: Incident['status']) => {
        switch (status) {
            case 'resolved': return <CheckCircle className="w-5 h-5" />;
            case 'investigating': return <Schedule className="w-5 h-5" />;
            default: return <ErrorIcon className="w-5 h-5" />;
        }
    };

    const formatTimestamp = (timestamp: string) => {
        const date = new Date(timestamp);
        return date.toLocaleString();
    };

    const calculateDuration = (start: string, end?: string) => {
        const startTime = new Date(start).getTime();
        const endTime = end ? new Date(end).getTime() : Date.now();
        const durationMs = endTime - startTime;

        const hours = Math.floor(durationMs / (1000 * 60 * 60));
        const minutes = Math.floor((durationMs % (1000 * 60 * 60)) / (1000 * 60));

        if (hours > 0) {
            return `${hours}h ${minutes}m`;
        }
        return `${minutes}m`;
    };

    return (
        <div className="flex flex-col h-full bg-bg-default">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-divider bg-bg-paper">
                <div>
                    <h2 className="text-lg font-semibold text-text-primary">Incident Management</h2>
                    <p className="text-sm text-text-secondary">OBSERVE Phase - Issue Tracking</p>
                </div>
                <button
                    onClick={() => setShowNewIncidentDialog(true)}
                    className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors"
                >
                    <Add className="w-4 h-4" />
                    New Incident
                </button>
            </div>

            {/* Content */}
            <div className="flex flex-1 overflow-hidden">
                {/* Incident List */}
                <div className="w-1/3 border-r border-divider overflow-auto">
                    <div className="p-4 space-y-3">
                        {incidents.map((incident) => (
                            <div
                                key={incident.id}
                                onClick={() => setSelectedIncident(incident)}
                                className={`p-3 rounded-lg border cursor-pointer transition-colors ${selectedIncident?.id === incident.id
                                    ? 'border-primary-600 bg-primary-50'
                                    : 'border-divider bg-bg-paper hover:border-primary-200'
                                    }`}
                            >
                                <div className="flex items-start justify-between mb-2">
                                    <div className="flex-1 min-w-0">
                                        <h4 className="text-sm font-medium text-text-primary truncate">{incident.title}</h4>
                                        <p className="text-xs text-text-secondary mt-1">{incident.id}</p>
                                    </div>
                                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ml-2 ${getSeverityColor(incident.severity)}`}>
                                        {incident.severity}
                                    </span>
                                </div>
                                <div className="flex items-center gap-2 text-xs mt-2">
                                    <span className={`flex items-center gap-1 ${getStatusColor(incident.status)}`}>
                                        {getStatusIcon(incident.status)}
                                        {incident.status}
                                    </span>
                                    <span>•</span>
                                    <span className="text-text-secondary">
                                        {calculateDuration(incident.detectedAt, incident.resolvedAt)}
                                    </span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Incident Details */}
                <div className="flex-1 overflow-auto">
                    {selectedIncident ? (
                        <div className="p-6 space-y-6">
                            {/* Header */}
                            <div>
                                <div className="flex items-start justify-between mb-2">
                                    <h3 className="text-xl font-semibold text-text-primary">{selectedIncident.title}</h3>
                                    <button
                                        onClick={() => handleSaveIncidentReport(selectedIncident)}
                                        className="px-3 py-1.5 text-sm bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors"
                                    >
                                        Generate Report
                                    </button>
                                </div>
                                <div className="flex items-center gap-3 text-sm">
                                    <span className={`px-2 py-1 rounded-full font-medium ${getSeverityColor(selectedIncident.severity)}`}>
                                        {selectedIncident.severity}
                                    </span>
                                    <span className={`flex items-center gap-1 ${getStatusColor(selectedIncident.status)}`}>
                                        {getStatusIcon(selectedIncident.status)}
                                        {selectedIncident.status}
                                    </span>
                                </div>
                            </div>

                            {/* Key Info */}
                            <div className="grid grid-cols-2 gap-4">
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <div className="text-xs text-text-secondary mb-1">Detected At</div>
                                    <div className="text-sm font-medium text-text-primary">
                                        {formatTimestamp(selectedIncident.detectedAt)}
                                    </div>
                                </div>
                                {selectedIncident.resolvedAt && (
                                    <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                        <div className="text-xs text-text-secondary mb-1">Resolved At</div>
                                        <div className="text-sm font-medium text-text-primary">
                                            {formatTimestamp(selectedIncident.resolvedAt)}
                                        </div>
                                    </div>
                                )}
                                {selectedIncident.assignedTo && (
                                    <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                        <div className="text-xs text-text-secondary mb-1">Assigned To</div>
                                        <div className="flex items-center gap-2">
                                            <Person className="w-4 h-4 text-text-secondary" />
                                            <span className="text-sm font-medium text-text-primary">
                                                {selectedIncident.assignedTo}
                                            </span>
                                        </div>
                                    </div>
                                )}
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <div className="text-xs text-text-secondary mb-1">Duration</div>
                                    <div className="text-sm font-medium text-text-primary">
                                        {calculateDuration(selectedIncident.detectedAt, selectedIncident.resolvedAt)}
                                    </div>
                                </div>
                            </div>

                            {/* Impact */}
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <h4 className="text-sm font-medium text-text-primary mb-2">Impact</h4>
                                <p className="text-sm text-text-secondary">{selectedIncident.impact}</p>
                            </div>

                            {/* Root Cause */}
                            {selectedIncident.rootCause && (
                                <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                    <h4 className="text-sm font-medium text-text-primary mb-2">Root Cause</h4>
                                    <p className="text-sm text-text-secondary">{selectedIncident.rootCause}</p>
                                </div>
                            )}

                            {/* Timeline */}
                            <div>
                                <h4 className="text-sm font-medium text-text-primary mb-3">Timeline</h4>
                                <div className="space-y-3">
                                    {selectedIncident.timeline.map((event, idx) => (
                                        <div key={idx} className="flex gap-3">
                                            <div className="flex flex-col items-center">
                                                <div className="w-3 h-3 rounded-full bg-primary-600" />
                                                {idx < selectedIncident.timeline.length - 1 && (
                                                    <div className="w-0.5 flex-1 bg-grey-200 mt-1" />
                                                )}
                                            </div>
                                            <div className="flex-1 pb-4">
                                                <div className="text-xs text-text-secondary mb-1">
                                                    {formatTimestamp(event.timestamp)}
                                                    {event.user && ` • ${event.user}`}
                                                </div>
                                                <p className="text-sm text-text-primary">{event.event}</p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="flex items-center justify-center h-full">
                            <p className="text-sm text-text-secondary">Select an incident to view details</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};
