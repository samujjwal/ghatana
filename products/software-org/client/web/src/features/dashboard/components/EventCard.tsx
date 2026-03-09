import React from 'react';
import { StatusBadge } from '@/shared/components';

/**
 * Event Card Props interface.
 */
export interface EventCardProps {
    eventType: string;
    eventId: string;
    source: string;
    timestamp: Date;
    description: string;
    details?: Record<string, unknown>;
    onViewDetails?: (eventId: string) => void;
    metadata?: {
        department?: string;
        agent?: string;
        status?: 'processed' | 'pending' | 'failed';
    };
}

/**
 * Event Card - Displays a single event with metadata.
 *
 * <p><b>Purpose</b><br>
 * Shows event details in a compact card format with drill-down capabilities.
 *
 * <p><b>Features</b><br>
 * - Event type badge
 * - Source indicator
 * - Timestamp formatting
 * - Status badge (processed/pending/failed)
 * - Expandable details
 * - Dark mode support
 * - Keyboard accessible
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <EventCard 
 *   eventType="workflow_completed"
 *   eventId="evt_123"
 *   source="automation_engine"
 *   timestamp={new Date()}
 *   description="Workflow completed successfully"
 *   onViewDetails={handleViewDetails}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Single event display
 * @doc.layer product
 * @doc.pattern Molecule
 */
export const EventCard = React.memo(
    ({ eventType, eventId, source, timestamp, description, details, onViewDetails, metadata }: EventCardProps) => {
        const status = metadata?.status || 'processed';

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-slate-900 hover:shadow-md dark:hover:shadow-lg transition-shadow">
                {/* Header with event type and status */}
                <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-2">
                        <span className="inline-block px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300">
                            {eventType}
                        </span>
                        {metadata?.status && (
                            <StatusBadge status={status} />
                        )}
                    </div>
                    <time className="text-xs text-slate-500 dark:text-neutral-400">
                        {timestamp.toLocaleString()}
                    </time>
                </div>

                {/* Description */}
                <p className="text-sm text-slate-900 dark:text-neutral-200 mb-3">
                    {description}
                </p>

                {/* Metadata grid */}
                <div className="grid grid-cols-2 gap-2 mb-3 text-xs">
                    <div>
                        <span className="font-medium text-slate-600 dark:text-neutral-400">Source:</span>
                        <p className="text-slate-900 dark:text-neutral-200">{source}</p>
                    </div>
                    {metadata?.department && (
                        <div>
                            <span className="font-medium text-slate-600 dark:text-neutral-400">Department:</span>
                            <p className="text-slate-900 dark:text-neutral-200">{metadata.department}</p>
                        </div>
                    )}
                    {metadata?.agent && (
                        <div>
                            <span className="font-medium text-slate-600 dark:text-neutral-400">Agent:</span>
                            <p className="text-slate-900 dark:text-neutral-200">{metadata.agent}</p>
                        </div>
                    )}
                    <div>
                        <span className="font-medium text-slate-600 dark:text-neutral-400">Event ID:</span>
                        <p className="font-mono text-slate-900 dark:text-neutral-200 truncate">{eventId.slice(0, 8)}...</p>
                    </div>
                </div>

                {/* Details if available */}
                {details && Object.keys(details).length > 0 && (
                    <div className="mb-3 p-2 bg-slate-50 dark:bg-neutral-800 rounded text-xs max-h-24 overflow-y-auto">
                        <pre className="text-slate-700 dark:text-neutral-300 whitespace-pre-wrap break-words">
                            {JSON.stringify(details, null, 2)}
                        </pre>
                    </div>
                )}

                {/* Action button */}
                {onViewDetails && (
                    <button
                        onClick={() => onViewDetails(eventId)}
                        className="w-full py-2 px-3 rounded-md bg-blue-50 text-blue-600 hover:bg-blue-100 dark:bg-indigo-600/30 dark:text-indigo-400 dark:hover:bg-blue-900/40 font-medium text-sm transition-colors"
                        aria-label={`View details for event ${eventId}`}
                    >
                        View Details →
                    </button>
                )}
            </div>
        );
    }
);

EventCard.displayName = 'EventCard';

export default EventCard;
