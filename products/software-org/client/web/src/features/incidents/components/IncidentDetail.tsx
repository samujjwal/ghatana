import React from 'react';
import { Incident } from './IncidentCard';
import { StatusBadge } from '@/shared/components';

/**
 * Incident Detail Props interface.
 */
export interface IncidentDetailProps {
    incident: Incident;
    timeline?: Array<{
        id: string;
        timestamp: Date;
        eventType: string;
        actor?: string;
        description: string;
    }>;
    isLoading?: boolean;
    onClose?: () => void;
}

/**
 * Incident Detail - Displays comprehensive incident information.
 *
 * <p><b>Purpose</b><br>
 * Shows complete incident details including timeline, impact analysis, and resolution history.
 *
 * <p><b>Features</b><br>
 * - Full incident metadata
 * - Event timeline
 * - Impact metrics
 * - Resolution history
 * - Assignment info
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <IncidentDetail 
 *   incident={selectedIncident}
 *   timeline={events}
 *   isLoading={false}
 *   onClose={handleClose}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Incident detail display
 * @doc.layer product
 * @doc.pattern Organism
 */
export const IncidentDetail = React.memo(
    ({ incident, timeline, isLoading, onClose }: IncidentDetailProps) => {
        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-4">
                        <div className="h-8 w-48 bg-slate-200 dark:bg-neutral-700 rounded" />
                        <div className="h-4 w-full bg-slate-200 dark:bg-neutral-700 rounded" />
                        <div className="h-40 bg-slate-200 dark:bg-neutral-700 rounded" />
                    </div>
                </div>
            );
        }

        return (
            <div className="rounded-lg border border-slate-200 bg-white dark:border-neutral-600 dark:bg-slate-900">
                {/* Header */}
                <div className="border-b border-slate-200 p-6 dark:border-neutral-600">
                    <div className="flex justify-between items-start mb-4">
                        <div>
                            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                {incident.title}
                            </h2>
                            <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                ID: {incident.id}
                            </p>
                        </div>
                        {onClose && (
                            <button
                                onClick={onClose}
                                className="text-slate-500 hover:text-slate-700 dark:text-neutral-400 dark:hover:text-slate-200 text-2xl"
                            >
                                ✕
                            </button>
                        )}
                    </div>

                    <div className="flex gap-2">
                        <StatusBadge status={incident.severity} />
                        <StatusBadge status={incident.status} />
                    </div>
                </div>

                {/* Body */}
                <div className="p-6 space-y-6">
                    {/* Description */}
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                            Description
                        </h3>
                        <p className="text-slate-700 dark:text-neutral-300">
                            {incident.description}
                        </p>
                    </div>

                    {/* Details Grid */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <div className="p-3 rounded-lg bg-slate-50 dark:bg-neutral-800">
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Created</p>
                            <p className="text-sm font-medium text-slate-900 dark:text-neutral-100 mt-1">
                                {new Date(incident.createdAt).toLocaleDateString()}
                            </p>
                        </div>
                        <div className="p-3 rounded-lg bg-slate-50 dark:bg-neutral-800">
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Assigned To</p>
                            <p className="text-sm font-medium text-slate-900 dark:text-neutral-100 mt-1">
                                {incident.assignedTo || 'Unassigned'}
                            </p>
                        </div>
                        <div className="p-3 rounded-lg bg-slate-50 dark:bg-neutral-800">
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Duration</p>
                            <p className="text-sm font-medium text-slate-900 dark:text-neutral-100 mt-1">
                                {incident.durationMinutes ? `${incident.durationMinutes}m` : 'Ongoing'}
                            </p>
                        </div>
                        <div className="p-3 rounded-lg bg-slate-50 dark:bg-neutral-800">
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Affected Users</p>
                            <p className="text-sm font-medium text-slate-900 dark:text-neutral-100 mt-1">
                                {incident.affectedUsers || '—'}
                            </p>
                        </div>
                    </div>

                    {/* Impacted Systems */}
                    {incident.impactedSystems && incident.impactedSystems.length > 0 && (
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                Impacted Systems
                            </h3>
                            <div className="flex flex-wrap gap-2">
                                {incident.impactedSystems.map((sys) => (
                                    <StatusBadge key={sys} status="critical" />
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Timeline */}
                    {timeline && timeline.length > 0 && (
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                Timeline
                            </h3>
                            <div className="space-y-3 max-h-64 overflow-y-auto">
                                {timeline.map((event, i) => (
                                    <div key={event.id} className="flex gap-4">
                                        <div className="flex flex-col items-center">
                                            <div className="w-3 h-3 rounded-full bg-blue-500 mt-1.5" />
                                            {i < timeline.length - 1 && (
                                                <div className="w-0.5 h-12 bg-slate-300 dark:bg-neutral-700 mt-2" />
                                            )}
                                        </div>
                                        <div className="pb-4">
                                            <p className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                                {event.eventType}
                                            </p>
                                            <p className="text-xs text-slate-600 dark:text-neutral-400 mt-0.5">
                                                {new Date(event.timestamp).toLocaleString()}
                                            </p>
                                            <p className="text-sm text-slate-700 dark:text-neutral-300 mt-1">
                                                {event.description}
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        );
    }
);

IncidentDetail.displayName = 'IncidentDetail';

export default IncidentDetail;
