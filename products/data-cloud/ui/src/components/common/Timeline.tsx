/**
 * Timeline Component
 * 
 * Displays a vertical timeline of events or activities.
 * Supports different event types, icons, and timestamps.
 * 
 * @doc.type component
 * @doc.purpose Timeline visualization
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';
import {
    CheckCircle2,
    XCircle,
    AlertCircle,
    Info,
    Clock,
    User,
    Settings,
    Database,
    Workflow,
} from 'lucide-react';
import { cn, textStyles } from '../../lib/theme';

/**
 * Timeline event type
 */
export type TimelineEventType =
    | 'success'
    | 'error'
    | 'warning'
    | 'info'
    | 'pending'
    | 'user'
    | 'system'
    | 'data'
    | 'workflow';

/**
 * Timeline event interface
 */
export interface TimelineEvent {
    id: string;
    type: TimelineEventType;
    title: string;
    description?: string;
    timestamp: string | Date;
    user?: string;
    metadata?: Record<string, string | number>;
    icon?: React.ReactNode;
}

/**
 * Event type styles
 */
const eventTypeStyles: Record<TimelineEventType, { bg: string; icon: React.ReactNode }> = {
    success: {
        bg: 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-400',
        icon: <CheckCircle2 className="h-4 w-4" />,
    },
    error: {
        bg: 'bg-red-100 dark:bg-red-900 text-red-600 dark:text-red-400',
        icon: <XCircle className="h-4 w-4" />,
    },
    warning: {
        bg: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-600 dark:text-yellow-400',
        icon: <AlertCircle className="h-4 w-4" />,
    },
    info: {
        bg: 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-400',
        icon: <Info className="h-4 w-4" />,
    },
    pending: {
        bg: 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400',
        icon: <Clock className="h-4 w-4" />,
    },
    user: {
        bg: 'bg-purple-100 dark:bg-purple-900 text-purple-600 dark:text-purple-400',
        icon: <User className="h-4 w-4" />,
    },
    system: {
        bg: 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400',
        icon: <Settings className="h-4 w-4" />,
    },
    data: {
        bg: 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-400',
        icon: <Database className="h-4 w-4" />,
    },
    workflow: {
        bg: 'bg-indigo-100 dark:bg-indigo-900 text-indigo-600 dark:text-indigo-400',
        icon: <Workflow className="h-4 w-4" />,
    },
};

/**
 * Format timestamp
 */
function formatTimestamp(timestamp: string | Date): string {
    const date = typeof timestamp === 'string' ? new Date(timestamp) : timestamp;
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    // Less than 1 minute
    if (diff < 60000) {
        return 'Just now';
    }

    // Less than 1 hour
    if (diff < 3600000) {
        const minutes = Math.floor(diff / 60000);
        return `${minutes}m ago`;
    }

    // Less than 24 hours
    if (diff < 86400000) {
        const hours = Math.floor(diff / 3600000);
        return `${hours}h ago`;
    }

    // Less than 7 days
    if (diff < 604800000) {
        const days = Math.floor(diff / 86400000);
        return `${days}d ago`;
    }

    // Default to date
    return date.toLocaleDateString();
}

interface TimelineProps {
    events: TimelineEvent[];
    className?: string;
    showConnector?: boolean;
    maxItems?: number;
}

/**
 * Timeline Component
 */
export function Timeline({
    events,
    className,
    showConnector = true,
    maxItems,
}: TimelineProps): React.ReactElement {
    const displayEvents = maxItems ? events.slice(0, maxItems) : events;

    return (
        <div className={cn('relative', className)}>
            {displayEvents.map((event, index) => {
                const style = eventTypeStyles[event.type];
                const isLast = index === displayEvents.length - 1;

                return (
                    <div key={event.id} className="relative flex gap-4 pb-6 last:pb-0">
                        {/* Connector Line */}
                        {showConnector && !isLast && (
                            <div className="absolute left-[15px] top-8 bottom-0 w-0.5 bg-gray-200 dark:bg-gray-700" />
                        )}

                        {/* Icon */}
                        <div className={cn(
                            'relative z-10 flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center',
                            style.bg
                        )}>
                            {event.icon || style.icon}
                        </div>

                        {/* Content */}
                        <div className="flex-1 min-w-0 pt-0.5">
                            <div className="flex items-start justify-between gap-2">
                                <div>
                                    <p className={textStyles.h4}>{event.title}</p>
                                    {event.description && (
                                        <p className={cn(textStyles.small, 'mt-0.5')}>{event.description}</p>
                                    )}
                                </div>
                                <span className={cn(textStyles.xs, 'flex-shrink-0')}>
                                    {formatTimestamp(event.timestamp)}
                                </span>
                            </div>

                            {/* User */}
                            {event.user && (
                                <p className={cn(textStyles.xs, 'mt-1')}>
                                    by {event.user}
                                </p>
                            )}

                            {/* Metadata */}
                            {event.metadata && Object.keys(event.metadata).length > 0 && (
                                <div className="mt-2 flex flex-wrap gap-2">
                                    {Object.entries(event.metadata).map(([key, value]) => (
                                        <span
                                            key={key}
                                            className={cn(
                                                'px-2 py-0.5 text-xs rounded',
                                                'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                                            )}
                                        >
                                            {key}: {value}
                                        </span>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                );
            })}

            {maxItems && events.length > maxItems && (
                <div className="text-center pt-2">
                    <button className={cn(textStyles.link, 'text-sm')}>
                        View {events.length - maxItems} more events
                    </button>
                </div>
            )}
        </div>
    );
}

/**
 * Compact Timeline for sidebars/cards
 */
export function CompactTimeline({
    events,
    maxItems = 5,
}: { events: TimelineEvent[]; maxItems?: number }): React.ReactElement {
    const displayEvents = events.slice(0, maxItems);

    return (
        <div className="space-y-3">
            {displayEvents.map((event) => {
                const style = eventTypeStyles[event.type];

                return (
                    <div key={event.id} className="flex items-start gap-3">
                        <div className={cn(
                            'flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center',
                            style.bg
                        )}>
                            {event.icon ?? style.icon}
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className={cn(textStyles.small, 'truncate')}>{event.title}</p>
                            <p className={textStyles.xs}>{formatTimestamp(event.timestamp)}</p>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

export default Timeline;
