/**
 * Incidents Panel Component
 *
 * Incident management, timeline, and post-mortem tracking.
 * Used in Deploy surface Health segment.
 *
 * @doc.type component
 * @doc.purpose OBSERVE phase incident management
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { AlertTriangle as Warning, CheckCircle, XCircle as Cancel, Plus as Add, User as Person, Clock as Schedule, FileText as Description, ChevronDown as ExpandMore, ChevronUp as ExpandLess, Link as LinkIcon } from 'lucide-react';

export interface IncidentEvent {
    timestamp: string;
    type: 'created' | 'updated' | 'escalated' | 'resolved' | 'note';
    description: string;
    author: string;
}

export interface Incident {
    id: string;
    title: string;
    description: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'open' | 'investigating' | 'identified' | 'monitoring' | 'resolved';
    createdAt: string;
    updatedAt: string;
    resolvedAt?: string;
    assignee?: string;
    affectedServices: string[];
    timeline: IncidentEvent[];
    postMortemUrl?: string;
    relatedIncidents?: string[];
}

export interface IncidentsPanelProps {
    incidents: Incident[];
    onCreateIncident?: () => void;
    onUpdateStatus?: (incidentId: string, status: Incident['status']) => Promise<void>;
    onAddNote?: (incidentId: string, note: string) => Promise<void>;
    onAssign?: (incidentId: string, assignee: string) => Promise<void>;
    isLoading?: boolean;
}

const SEVERITY_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
    critical: { color: 'text-red-600', bg: 'bg-red-100 dark:bg-red-900/30', label: 'Critical' },
    high: { color: 'text-orange-600', bg: 'bg-orange-100 dark:bg-orange-900/30', label: 'High' },
    medium: { color: 'text-yellow-600', bg: 'bg-yellow-100 dark:bg-yellow-900/30', label: 'Medium' },
    low: { color: 'text-blue-600', bg: 'bg-blue-100 dark:bg-blue-900/30', label: 'Low' },
};

const STATUS_CONFIG: Record<string, { icon: React.ReactNode; color: string; label: string }> = {
    open: { icon: <Warning className="w-4 h-4" />, color: 'text-red-500', label: 'Open' },
    investigating: { icon: <Warning className="w-4 h-4" />, color: 'text-orange-500', label: 'Investigating' },
    identified: { icon: <Warning className="w-4 h-4" />, color: 'text-yellow-500', label: 'Identified' },
    monitoring: { icon: <CheckCircle className="w-4 h-4" />, color: 'text-blue-500', label: 'Monitoring' },
    resolved: { icon: <CheckCircle className="w-4 h-4" />, color: 'text-green-500', label: 'Resolved' },
};

const EVENT_ICONS: Record<string, React.ReactNode> = {
    created: <Add className="w-3 h-3" />,
    updated: <Description className="w-3 h-3" />,
    escalated: <Warning className="w-3 h-3" />,
    resolved: <CheckCircle className="w-3 h-3" />,
    note: <Description className="w-3 h-3" />,
};

function formatDuration(start: string, end?: string): string {
    const startDate = new Date(start);
    const endDate = end ? new Date(end) : new Date();
    const diffMs = endDate.getTime() - startDate.getTime();
    const hours = Math.floor(diffMs / (1000 * 60 * 60));
    const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
}

/**
 * Incidents Panel for OBSERVE phase.
 */
export const IncidentsPanel: React.FC<IncidentsPanelProps> = ({
    incidents,
    onCreateIncident,
    onUpdateStatus,
    onAddNote,
    onAssign,
    isLoading = false,
}) => {
    const [expandedIncident, setExpandedIncident] = useState<string | null>(null);
    const [filter, setFilter] = useState<'all' | 'open' | 'resolved'>('all');
    const [newNote, setNewNote] = useState<Record<string, string>>({});
    const [isSubmittingNote, setIsSubmittingNote] = useState(false);

    const filteredIncidents = incidents.filter((incident) => {
        if (filter === 'all') return true;
        if (filter === 'open') return incident.status !== 'resolved';
        return incident.status === 'resolved';
    });

    const openCount = incidents.filter((i) => i.status !== 'resolved').length;
    const criticalCount = incidents.filter((i) => i.severity === 'critical' && i.status !== 'resolved').length;

    const handleAddNote = useCallback(
        async (incidentId: string) => {
            if (!onAddNote || !newNote[incidentId]?.trim()) return;
            setIsSubmittingNote(true);
            try {
                await onAddNote(incidentId, newNote[incidentId]);
                setNewNote((prev) => ({ ...prev, [incidentId]: '' }));
            } finally {
                setIsSubmittingNote(false);
            }
        },
        [onAddNote, newNote],
    );

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-3">
                    <div
                        className={`p-2 rounded-lg ${criticalCount > 0
                                ? 'bg-red-100 dark:bg-red-900/30'
                                : openCount > 0
                                    ? 'bg-yellow-100 dark:bg-yellow-900/30'
                                    : 'bg-green-100 dark:bg-green-900/30'
                            }`}
                    >
                        <Warning
                            className={`w-5 h-5 ${criticalCount > 0
                                    ? 'text-red-600'
                                    : openCount > 0
                                        ? 'text-yellow-600'
                                        : 'text-green-600'
                                }`}
                        />
                    </div>
                    <div>
                        <h3 className="font-semibold text-text-primary">Incidents</h3>
                        <p className="text-xs text-text-secondary">
                            {openCount} open
                            {criticalCount > 0 && <span className="text-red-500"> • {criticalCount} critical</span>}
                        </p>
                    </div>
                </div>
                {onCreateIncident && (
                    <button
                        onClick={onCreateIncident}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
                    >
                        <Add className="w-4 h-4" /> New Incident
                    </button>
                )}
            </div>

            {/* Filter */}
            <div className="flex gap-2 px-4 py-2 border-b border-divider bg-grey-50 dark:bg-grey-800/50">
                {(['all', 'open', 'resolved'] as const).map((f) => (
                    <button
                        key={f}
                        onClick={() => setFilter(f)}
                        className={`px-3 py-1 text-xs rounded-lg transition-colors ${filter === f
                                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                    >
                        {f.charAt(0).toUpperCase() + f.slice(1)}
                        {f === 'open' && openCount > 0 && (
                            <span className="ml-1 px-1.5 bg-red-500 text-white rounded-full text-xs">
                                {openCount}
                            </span>
                        )}
                    </button>
                ))}
            </div>

            {/* Incidents List */}
            <div className="flex-1 overflow-auto p-4">
                {filteredIncidents.length === 0 ? (
                    <div className="text-center py-8 text-text-secondary">
                        <CheckCircle className="w-12 h-12 mx-auto mb-2 opacity-50 text-green-500" />
                        <p className="text-sm">No incidents to show</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {filteredIncidents.map((incident) => (
                            <div
                                key={incident.id}
                                className={`border rounded-lg overflow-hidden ${incident.severity === 'critical' && incident.status !== 'resolved'
                                        ? 'border-red-200 dark:border-red-800'
                                        : 'border-divider'
                                    }`}
                            >
                                {/* Incident Header */}
                                <button
                                    onClick={() =>
                                        setExpandedIncident(
                                            expandedIncident === incident.id ? null : incident.id,
                                        )
                                    }
                                    className={`w-full flex items-start gap-3 p-3 text-left transition-colors hover:bg-grey-50 dark:hover:bg-grey-800/50 ${incident.status !== 'resolved'
                                            ? SEVERITY_CONFIG[incident.severity].bg
                                            : 'bg-bg-paper'
                                        }`}
                                >
                                    <div className={STATUS_CONFIG[incident.status].color}>
                                        {STATUS_CONFIG[incident.status].icon}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2 mb-1">
                                            <span className="font-medium text-sm text-text-primary">
                                                {incident.title}
                                            </span>
                                            <span
                                                className={`px-1.5 py-0.5 text-xs rounded ${SEVERITY_CONFIG[incident.severity].bg} ${SEVERITY_CONFIG[incident.severity].color}`}
                                            >
                                                {SEVERITY_CONFIG[incident.severity].label}
                                            </span>
                                        </div>
                                        <div className="flex items-center gap-3 text-xs text-text-secondary">
                                            <span className={STATUS_CONFIG[incident.status].color}>
                                                {STATUS_CONFIG[incident.status].label}
                                            </span>
                                            <span className="flex items-center gap-1">
                                                <Schedule className="w-3 h-3" />
                                                {formatDuration(incident.createdAt, incident.resolvedAt)}
                                            </span>
                                            {incident.assignee && (
                                                <span className="flex items-center gap-1">
                                                    <Person className="w-3 h-3" />
                                                    {incident.assignee}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                    {expandedIncident === incident.id ? (
                                        <ExpandLess className="w-4 h-4 text-text-secondary" />
                                    ) : (
                                        <ExpandMore className="w-4 h-4 text-text-secondary" />
                                    )}
                                </button>

                                {/* Expanded Details */}
                                {expandedIncident === incident.id && (
                                    <div className="border-t border-divider bg-bg-paper">
                                        {/* Description */}
                                        <div className="p-3 border-b border-divider">
                                            <p className="text-sm text-text-primary">{incident.description}</p>
                                            {incident.affectedServices.length > 0 && (
                                                <div className="flex flex-wrap gap-1 mt-2">
                                                    {incident.affectedServices.map((service, idx) => (
                                                        <span
                                                            key={idx}
                                                            className="px-2 py-0.5 text-xs bg-grey-100 dark:bg-grey-800 text-text-secondary rounded"
                                                        >
                                                            {service}
                                                        </span>
                                                    ))}
                                                </div>
                                            )}
                                        </div>

                                        {/* Status Update */}
                                        {onUpdateStatus && incident.status !== 'resolved' && (
                                            <div className="p-3 border-b border-divider flex items-center gap-2">
                                                <span className="text-xs text-text-secondary">Update status:</span>
                                                {(['investigating', 'identified', 'monitoring', 'resolved'] as const).map(
                                                    (status) => (
                                                        <button
                                                            key={status}
                                                            onClick={() => onUpdateStatus(incident.id, status)}
                                                            disabled={incident.status === status}
                                                            className={`px-2 py-1 text-xs rounded transition-colors ${incident.status === status
                                                                    ? 'bg-grey-200 dark:bg-grey-700 text-text-secondary'
                                                                    : status === 'resolved'
                                                                        ? 'bg-green-100 text-green-700 hover:bg-green-200 dark:bg-green-900/30 dark:text-green-300'
                                                                        : 'bg-grey-100 text-text-primary hover:bg-grey-200 dark:bg-grey-800 dark:hover:bg-grey-700'
                                                                }`}
                                                        >
                                                            {STATUS_CONFIG[status].label}
                                                        </button>
                                                    ),
                                                )}
                                            </div>
                                        )}

                                        {/* Timeline */}
                                        <div className="p-3 border-b border-divider">
                                            <h5 className="text-xs font-medium text-text-secondary mb-2">
                                                Timeline
                                            </h5>
                                            <div className="space-y-2 max-h-48 overflow-auto">
                                                {incident.timeline.map((event, idx) => (
                                                    <div key={idx} className="flex gap-2 text-xs">
                                                        <span className="text-text-secondary shrink-0">
                                                            {new Date(event.timestamp).toLocaleTimeString()}
                                                        </span>
                                                        <span className="text-text-secondary">
                                                            {EVENT_ICONS[event.type]}
                                                        </span>
                                                        <span className="text-text-primary flex-1">
                                                            {event.description}
                                                        </span>
                                                        <span className="text-text-secondary">{event.author}</span>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Add Note */}
                                        {onAddNote && incident.status !== 'resolved' && (
                                            <div className="p-3 border-b border-divider">
                                                <div className="flex gap-2">
                                                    <input
                                                        type="text"
                                                        value={newNote[incident.id] || ''}
                                                        onChange={(e) =>
                                                            setNewNote((prev) => ({
                                                                ...prev,
                                                                [incident.id]: e.target.value,
                                                            }))
                                                        }
                                                        placeholder="Add a note..."
                                                        className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                    />
                                                    <button
                                                        onClick={() => handleAddNote(incident.id)}
                                                        disabled={isSubmittingNote || !newNote[incident.id]?.trim()}
                                                        className="px-3 py-1 text-sm bg-primary-600 text-white rounded hover:bg-primary-700 transition-colors disabled:opacity-50"
                                                    >
                                                        Add
                                                    </button>
                                                </div>
                                            </div>
                                        )}

                                        {/* Post-mortem Link */}
                                        {incident.postMortemUrl && (
                                            <div className="p-3">
                                                <a
                                                    href={incident.postMortemUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="flex items-center gap-2 text-sm text-primary-600 hover:text-primary-700"
                                                >
                                                    <LinkIcon className="w-4 h-4" />
                                                    View Post-Mortem
                                                </a>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default IncidentsPanel;
