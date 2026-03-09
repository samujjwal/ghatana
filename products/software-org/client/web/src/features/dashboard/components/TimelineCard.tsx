import React from 'react';
import { StatusBadge } from '@/shared/components';

/**
 * Timeline event interface.
 */
export interface TimelineEvent {
    id: string;
    timestamp: Date;
    title: string;
    description: string;
    severity: 'info' | 'warning' | 'critical';
    category: string;
}

/**
 * Timeline Card Props interface.
 */
export interface TimelineCardProps {
    events: TimelineEvent[];
    onEventClick?: (event: TimelineEvent) => void;
    maxVisible?: number;
}

/**
 * Timeline Card - Displays a vertical timeline of events.
 *
 * <p><b>Purpose</b><br>
 * Shows recent events in chronological order with severity indicators and drill-down capability.
 *
 * <p><b>Features</b><br>
 * - Vertical timeline layout
 * - Severity color coding (info/warning/critical)
 * - Event category badges
 * - Clickable events for detail view
 * - Scrollable with max visible items
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <TimelineCard 
 *   events={recentEvents}
 *   onEventClick={handleEventClick}
 *   maxVisible={5}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Timeline event display
 * @doc.layer product
 * @doc.pattern Molecule
 */
export const TimelineCard = React.memo(
    ({ events, onEventClick, maxVisible = 5 }: TimelineCardProps) => {
        const displayEvents = events.slice(0, maxVisible);

        // Border colors for severity indicators
        const severityBorderColors = {
            info: 'before:bg-blue-400 dark:before:bg-blue-600',
            warning: 'before:bg-yellow-400 dark:before:bg-yellow-600',
            critical: 'before:bg-red-400 dark:before:bg-red-600',
        } as const;

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-slate-900">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Recent Activity
                </h3>

                <div className="relative">
                    {displayEvents.length === 0 ? (
                        <p className="text-center text-slate-500 dark:text-neutral-400 py-6">
                            No recent events
                        </p>
                    ) : (
                        <div className="space-y-4">
                            {displayEvents.map((event, index) => (
                                <div
                                    key={event.id}
                                    className={`relative pl-6 pb-4 ${index !== displayEvents.length - 1 ? 'border-b border-slate-200 dark:border-neutral-600' : ''
                                        } ${onEventClick ? 'cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 -mx-4 px-4 rounded transition-colors' : ''
                                        }`}
                                    onClick={() => onEventClick?.(event)}
                                    role="button"
                                    tabIndex={0}
                                    onKeyDown={(e) => {
                                        if (e.key === 'Enter' || e.key === ' ') {
                                            onEventClick?.(event);
                                        }
                                    }}
                                >
                                    {/* Timeline dot */}
                                    <div
                                        className={`absolute left-0 top-1 w-3 h-3 rounded-full border-2 border-white dark:border-slate-900 ${event.severity === 'critical' ? 'bg-red-500' : event.severity === 'warning' ? 'bg-yellow-500' : 'bg-blue-500'}`}
                                    />

                                    {/* Event content */}
                                    <div className="flex flex-col gap-1">
                                        <div className="flex items-center gap-2">
                                            <h4 className="font-medium text-slate-900 dark:text-neutral-100">
                                                {event.title}
                                            </h4>
                                            <StatusBadge status={event.severity} />
                                            <span className="inline-block px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 dark:bg-neutral-800 dark:text-neutral-300">
                                                {event.category}
                                            </span>
                                        </div>
                                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                                            {event.description}
                                        </p>
                                        <time className="text-xs text-slate-500 dark:text-slate-500">
                                            {event.timestamp.toLocaleTimeString()}
                                        </time>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {events.length > maxVisible && (
                    <button
                        className="mt-4 w-full py-2 text-sm font-medium text-blue-600 hover:text-blue-700 dark:text-indigo-400 dark:hover:text-blue-300 transition-colors"
                        aria-label={`View ${events.length - maxVisible} more events`}
                    >
                        View all {events.length} events →
                    </button>
                )}
            </div>
        );
    }
);

TimelineCard.displayName = 'TimelineCard';

export default TimelineCard;
