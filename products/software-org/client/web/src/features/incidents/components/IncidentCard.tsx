import React from 'react';
import { StatusBadge } from '@/shared/components';

/**
 * Incident interface.
 */
export interface Incident {
    id: string;
    title: string;
    description: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'open' | 'investigating' | 'resolved' | 'acknowledged';
    createdAt: Date;
    assignedTo?: string;
    impactedSystems: string[];
    affectedUsers?: number;
    durationMinutes?: number;
}

/**
 * Incident Card Props interface.
 */
export interface IncidentCardProps {
    incident: Incident;
    onCardClick?: (incident: Incident) => void;
    onAssign?: (incident: Incident) => void;
    onResolve?: (incident: Incident) => void;
    showActions?: boolean;
}

/**
 * Incident Card - Displays incident information in a card.
 *
 * <p><b>Purpose</b><br>
 * Shows incident details with severity/status indicators and action buttons.
 *
 * <p><b>Features</b><br>
 * - Severity badges with color coding
 * - Status indicators
 * - Impact summary (affected systems/users)
 * - Duration tracking
 * - Assignee information
 * - Quick action buttons
 * - Dark mode support
 * - Keyboard accessible
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <IncidentCard 
 *   incident={incident}
 *   onCardClick={handleClick}
 *   onAssign={handleAssign}
 *   onResolve={handleResolve}
 *   showActions={true}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Incident display card
 * @doc.layer product
 * @doc.pattern Molecule
 */
export const IncidentCard = React.memo(
    ({
        incident,
        onCardClick,
        onAssign,
        onResolve,
        showActions = true,
    }: IncidentCardProps) => {
        const statusColors = {
            open: 'bg-red-50 border-red-200 dark:bg-rose-600/30 dark:border-red-800',
            investigating: 'bg-yellow-50 border-yellow-200 dark:bg-orange-600/30 dark:border-yellow-800',
            resolved: 'bg-green-50 border-green-200 dark:bg-green-600/30 dark:border-green-800',
            acknowledged: 'bg-blue-50 border-blue-200 dark:bg-indigo-600/30 dark:border-blue-800',
        };

        const statusIcons = {
            open: '🔴',
            investigating: '🟡',
            resolved: '✓',
            acknowledged: '📋',
        };

        return (
            <div
                onClick={() => onCardClick?.(incident)}
                role={onCardClick ? 'button' : undefined}
                tabIndex={onCardClick ? 0 : undefined}
                onKeyDown={(e) => {
                    if ((e.key === 'Enter' || e.key === ' ') && onCardClick) {
                        onCardClick(incident);
                    }
                }}
                className={`rounded-lg border p-4 transition-all ${statusColors[incident.status]} ${onCardClick ? 'cursor-pointer hover:shadow-md' : ''
                    }`}
            >
                {/* Header */}
                <div className="flex items-start justify-between mb-3">
                    <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                {incident.title}
                            </h3>
                            <StatusBadge
                                status={incident.severity}
                                statusMap={{
                                    high: { tone: 'danger', label: 'High' }
                                }}
                            />
                        </div>
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            {incident.description}
                        </p>
                    </div>
                    <span className="text-lg ml-2">{statusIcons[incident.status]}</span>
                </div>

                {/* Status and metadata */}
                <div className="flex items-center gap-4 mb-3 text-xs text-slate-600 dark:text-neutral-400">
                    <span className="capitalize font-medium">{incident.status}</span>
                    <time>{incident.createdAt.toLocaleString()}</time>
                    {incident.durationMinutes && (
                        <>
                            <span>•</span>
                            <span>{incident.durationMinutes} min</span>
                        </>
                    )}
                </div>

                {/* Impact summary */}
                <div className="mb-3 p-2 bg-slate-100/50 dark:bg-neutral-800/50 rounded text-sm">
                    <p className="text-slate-700 dark:text-neutral-300 mb-1">
                        <strong>Impacted Systems:</strong> {incident.impactedSystems.join(', ')}
                    </p>
                    {incident.affectedUsers && (
                        <p className="text-slate-700 dark:text-neutral-300">
                            <strong>Affected Users:</strong> {incident.affectedUsers}
                        </p>
                    )}
                </div>

                {/* Assignee */}
                {incident.assignedTo && (
                    <div className="mb-3 text-sm">
                        <p className="text-slate-600 dark:text-neutral-400">
                            <strong>Assigned to:</strong> {incident.assignedTo}
                        </p>
                    </div>
                )}

                {/* Actions */}
                {showActions && (onAssign || onResolve) && (
                    <div className="flex gap-2 pt-3 border-t border-slate-200 dark:border-neutral-600">
                        {onAssign && (
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onAssign(incident);
                                }}
                                className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:hover:bg-blue-500 transition-colors"
                                aria-label={`Assign incident ${incident.id}`}
                            >
                                Assign
                            </button>
                        )}
                        {onResolve && (
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onResolve(incident);
                                }}
                                className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-green-600 text-white hover:bg-green-700 dark:hover:bg-green-500 transition-colors"
                                aria-label={`Resolve incident ${incident.id}`}
                            >
                                Resolve
                            </button>
                        )}
                    </div>
                )}
            </div>
        );
    }
);

IncidentCard.displayName = 'IncidentCard';

export default IncidentCard;
