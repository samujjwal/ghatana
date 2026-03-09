/**
 * Audit Timeline Component
 * 
 * Displays audit events in a chronological timeline view with
 * filtering, search, and detail inspection capabilities.
 */

import React, { useState, useMemo } from 'react';
import { useAuditQuery } from '@/hooks/useAudit';
import {
    AuditAction,
    AuditResourceType,
    AuditSeverity,
    type AuditEvent,
    type AuditFilter,
} from '@/types/audit';

interface AuditTimelineProps {
    /** Initial filter to apply */
    initialFilter?: AuditFilter;
    /** Maximum height of timeline container */
    maxHeight?: number;
    /** Show filter controls */
    showFilters?: boolean;
    /** Callback when event is clicked */
    onEventClick?: (event: AuditEvent) => void;
}

/**
 * Format timestamp for display
 */
function formatTimestamp(timestamp: string): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

/**
 * Get color for severity
 */
function getSeverityColor(severity: AuditSeverity): string {
    switch (severity) {
        case AuditSeverity.INFO:
            return 'bg-blue-500';
        case AuditSeverity.WARNING:
            return 'bg-yellow-500';
        case AuditSeverity.ERROR:
            return 'bg-red-500';
        case AuditSeverity.CRITICAL:
            return 'bg-purple-500';
        default:
            return 'bg-slate-500';
    }
}

/**
 * Get icon for action type
 */
function getActionIcon(action: AuditAction): string {
    if (action.includes('created')) return '➕';
    if (action.includes('updated') || action.includes('modified')) return '✏️';
    if (action.includes('deleted')) return '🗑️';
    if (action.includes('added')) return '⬆️';
    if (action.includes('removed')) return '⬇️';
    if (action.includes('enabled')) return '✅';
    if (action.includes('disabled')) return '❌';
    if (action.includes('bulk')) return '📦';
    return '📄';
}

/**
 * Format action name for display
 */
function formatActionName(action: AuditAction): string {
    return action.split('.').map(word =>
        word.charAt(0).toUpperCase() + word.slice(1)
    ).join(' ');
}

export function AuditTimeline({
    initialFilter = {},
    maxHeight = 600,
    showFilters = true,
    onEventClick,
}: AuditTimelineProps) {
    const {
        events,
        total,
        hasMore,
        loading,
        error,
        filter,
        setFilter,
        loadMore,
        refresh,
    } = useAuditQuery(initialFilter);

    const [selectedEvent, setSelectedEvent] = useState<AuditEvent | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [actionFilter, setActionFilter] = useState<AuditAction[]>([]);
    const [resourceTypeFilter, setResourceTypeFilter] = useState<AuditResourceType[]>([]);
    const [severityFilter, setSeverityFilter] = useState<AuditSeverity[]>([]);

    // Apply filters
    const applyFilters = () => {
        const newFilter: AuditFilter = {
            ...filter,
            searchQuery: searchQuery || undefined,
            actions: actionFilter.length > 0 ? actionFilter : undefined,
            resourceTypes: resourceTypeFilter.length > 0 ? resourceTypeFilter : undefined,
            severities: severityFilter.length > 0 ? severityFilter : undefined,
            offset: 0, // Reset pagination
        };
        setFilter(newFilter);
    };

    // Clear filters
    const clearFilters = () => {
        setSearchQuery('');
        setActionFilter([]);
        setResourceTypeFilter([]);
        setSeverityFilter([]);
        setFilter({ limit: 50 });
    };

    // Handle event click
    const handleEventClick = (event: AuditEvent) => {
        setSelectedEvent(event);
        onEventClick?.(event);
    };

    // Group events by date
    const groupedEvents = useMemo(() => {
        const groups = new Map<string, AuditEvent[]>();

        events.forEach(event => {
            const date = new Date(event.timestamp).toLocaleDateString();
            if (!groups.has(date)) {
                groups.set(date, []);
            }
            groups.get(date)!.push(event);
        });

        return Array.from(groups.entries()).map(([date, events]) => ({
            date,
            events,
        }));
    }, [events]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between mb-4">
                <div>
                    <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">
                        Audit Timeline
                    </h2>
                    <p className="text-sm text-slate-600 dark:text-neutral-400">
                        {total} {total === 1 ? 'event' : 'events'} found
                    </p>
                </div>
                <button
                    onClick={refresh}
                    disabled={loading}
                    className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
                >
                    {loading ? 'Loading...' : 'Refresh'}
                </button>
            </div>

            {/* Filters */}
            {showFilters && (
                <div className="mb-4 p-4 bg-slate-50 dark:bg-neutral-800 rounded-lg space-y-3">
                    {/* Search */}
                    <div>
                        <input
                            type="text"
                            placeholder="Search events..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
                            className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                        />
                    </div>

                    {/* Filter Pills */}
                    <div className="flex flex-wrap gap-2">
                        <select
                            value=""
                            onChange={(e) => {
                                if (e.target.value && !actionFilter.includes(e.target.value as AuditAction)) {
                                    setActionFilter([...actionFilter, e.target.value as AuditAction]);
                                }
                            }}
                            className="px-3 py-1 text-sm border border-slate-300 dark:border-neutral-600 rounded bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="">+ Action</option>
                            {Object.values(AuditAction).map(action => (
                                <option key={action} value={action}>{formatActionName(action)}</option>
                            ))}
                        </select>

                        <select
                            value=""
                            onChange={(e) => {
                                if (e.target.value && !resourceTypeFilter.includes(e.target.value as AuditResourceType)) {
                                    setResourceTypeFilter([...resourceTypeFilter, e.target.value as AuditResourceType]);
                                }
                            }}
                            className="px-3 py-1 text-sm border border-slate-300 dark:border-neutral-600 rounded bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="">+ Resource Type</option>
                            {Object.values(AuditResourceType).map(type => (
                                <option key={type} value={type}>{type.toUpperCase()}</option>
                            ))}
                        </select>

                        <select
                            value=""
                            onChange={(e) => {
                                if (e.target.value && !severityFilter.includes(e.target.value as AuditSeverity)) {
                                    setSeverityFilter([...severityFilter, e.target.value as AuditSeverity]);
                                }
                            }}
                            className="px-3 py-1 text-sm border border-slate-300 dark:border-neutral-600 rounded bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="">+ Severity</option>
                            {Object.values(AuditSeverity).map(severity => (
                                <option key={severity} value={severity}>{severity.toUpperCase()}</option>
                            ))}
                        </select>

                        <button
                            onClick={applyFilters}
                            className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
                        >
                            Apply
                        </button>

                        {(searchQuery || actionFilter.length > 0 || resourceTypeFilter.length > 0 || severityFilter.length > 0) && (
                            <button
                                onClick={clearFilters}
                                className="px-3 py-1 text-sm bg-slate-600 text-white rounded hover:bg-slate-700"
                            >
                                Clear All
                            </button>
                        )}
                    </div>

                    {/* Active Filters */}
                    <div className="flex flex-wrap gap-2">
                        {actionFilter.map(action => (
                            <span
                                key={action}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded"
                            >
                                {formatActionName(action)}
                                <button
                                    onClick={() => setActionFilter(actionFilter.filter(a => a !== action))}
                                    className="hover:text-blue-600 dark:hover:text-blue-400"
                                >
                                    ×
                                </button>
                            </span>
                        ))}
                        {resourceTypeFilter.map(type => (
                            <span
                                key={type}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200 rounded"
                            >
                                {type.toUpperCase()}
                                <button
                                    onClick={() => setResourceTypeFilter(resourceTypeFilter.filter(t => t !== type))}
                                    className="hover:text-green-600 dark:hover:text-green-400"
                                >
                                    ×
                                </button>
                            </span>
                        ))}
                        {severityFilter.map(severity => (
                            <span
                                key={severity}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 rounded"
                            >
                                {severity.toUpperCase()}
                                <button
                                    onClick={() => setSeverityFilter(severityFilter.filter(s => s !== severity))}
                                    className="hover:text-yellow-600 dark:hover:text-yellow-400"
                                >
                                    ×
                                </button>
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {/* Error */}
            {error && (
                <div className="mb-4 p-3 bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded text-red-700 dark:text-rose-400">
                    Error loading events: {error.message}
                </div>
            )}

            {/* Timeline */}
            <div
                className="flex-1 overflow-y-auto space-y-6"
                style={{ maxHeight: `${maxHeight}px` }}
            >
                {groupedEvents.length === 0 && !loading && (
                    <div className="text-center py-8 text-slate-500 dark:text-neutral-400">
                        No events found
                    </div>
                )}

                {groupedEvents.map(({ date, events: dateEvents }) => (
                    <div key={date}>
                        {/* Date Header */}
                        <div className="sticky top-0 z-10 bg-white dark:bg-slate-900 py-2">
                            <h3 className="text-sm font-semibold text-slate-700 dark:text-neutral-300">
                                {date}
                            </h3>
                        </div>

                        {/* Events */}
                        <div className="relative pl-6 space-y-4">
                            {/* Timeline line */}
                            <div className="absolute left-2 top-0 bottom-0 w-0.5 bg-slate-200 dark:bg-neutral-700" />

                            {dateEvents.map((event) => (
                                <div
                                    key={event.eventId}
                                    className="relative group"
                                >
                                    {/* Timeline dot */}
                                    <div className={`absolute left-[-1.4rem] top-2 w-4 h-4 rounded-full border-2 border-white dark:border-slate-900 ${getSeverityColor(event.severity)}`} />

                                    {/* Event card */}
                                    <div
                                        onClick={() => handleEventClick(event)}
                                        className={`p-4 bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg cursor-pointer hover:shadow-md transition-shadow ${selectedEvent?.eventId === event.eventId ? 'ring-2 ring-blue-500' : ''
                                            }`}
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="flex-1">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <span className="text-lg">{getActionIcon(event.action)}</span>
                                                    <span className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {formatActionName(event.action)}
                                                    </span>
                                                    <span className="text-xs text-slate-500 dark:text-neutral-400">
                                                        {event.resourceType}
                                                    </span>
                                                    {!event.success && (
                                                        <span className="text-xs px-2 py-0.5 bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200 rounded">
                                                            FAILED
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-sm text-slate-700 dark:text-neutral-300">
                                                    {event.resourceName || event.resourceId}
                                                </p>
                                                {event.metadata.userName && (
                                                    <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">
                                                        by {event.metadata.userName}
                                                    </p>
                                                )}
                                                {event.metadata.reason && (
                                                    <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1 italic">
                                                        "{event.metadata.reason}"
                                                    </p>
                                                )}
                                            </div>
                                            <div className="text-right">
                                                <p className="text-xs text-slate-500 dark:text-neutral-400">
                                                    {formatTimestamp(event.timestamp)}
                                                </p>
                                                {event.changes.length > 0 && (
                                                    <p className="text-xs text-blue-600 dark:text-indigo-400 mt-1">
                                                        {event.changes.length} {event.changes.length === 1 ? 'change' : 'changes'}
                                                    </p>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                ))}

                {/* Load More */}
                {hasMore && (
                    <div className="text-center py-4">
                        <button
                            onClick={loadMore}
                            disabled={loading}
                            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
                        >
                            {loading ? 'Loading...' : 'Load More'}
                        </button>
                    </div>
                )}
            </div>

            {/* Event Details Modal */}
            {selectedEvent && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white dark:bg-neutral-800 rounded-lg max-w-2xl w-full max-h-[80vh] overflow-y-auto">
                        <div className="p-6">
                            <div className="flex items-center justify-between mb-4">
                                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                    Event Details
                                </h3>
                                <button
                                    onClick={() => setSelectedEvent(null)}
                                    className="text-slate-500 hover:text-slate-700 dark:text-neutral-400 dark:hover:text-slate-200"
                                >
                                    ✕
                                </button>
                            </div>

                            <div className="space-y-4">
                                <div>
                                    <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        Action
                                    </h4>
                                    <p className="text-sm text-slate-900 dark:text-neutral-100">
                                        {getActionIcon(selectedEvent.action)} {formatActionName(selectedEvent.action)}
                                    </p>
                                </div>

                                <div>
                                    <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        Resource
                                    </h4>
                                    <p className="text-sm text-slate-900 dark:text-neutral-100">
                                        {selectedEvent.resourceType}: {selectedEvent.resourceName || selectedEvent.resourceId}
                                    </p>
                                </div>

                                <div>
                                    <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        Timestamp
                                    </h4>
                                    <p className="text-sm text-slate-900 dark:text-neutral-100">
                                        {new Date(selectedEvent.timestamp).toLocaleString()}
                                    </p>
                                </div>

                                <div>
                                    <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        User
                                    </h4>
                                    <p className="text-sm text-slate-900 dark:text-neutral-100">
                                        {selectedEvent.metadata.userName || selectedEvent.metadata.userId}
                                        {selectedEvent.metadata.userEmail && ` (${selectedEvent.metadata.userEmail})`}
                                    </p>
                                </div>

                                {selectedEvent.changes.length > 0 && (
                                    <div>
                                        <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                            Changes
                                        </h4>
                                        <div className="space-y-2">
                                            {selectedEvent.changes.map((change, index) => (
                                                <div key={index} className="p-3 bg-slate-50 dark:bg-neutral-700 rounded">
                                                    <p className="text-sm font-medium text-slate-900 dark:text-neutral-100 mb-1">
                                                        {change.field}
                                                    </p>
                                                    <div className="flex items-center gap-2 text-xs">
                                                        <span className="text-red-600 dark:text-rose-400">
                                                            {JSON.stringify(change.oldValue)}
                                                        </span>
                                                        <span className="text-slate-500 dark:text-neutral-400">→</span>
                                                        <span className="text-green-600 dark:text-green-400">
                                                            {JSON.stringify(change.newValue)}
                                                        </span>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {selectedEvent.metadata.reason && (
                                    <div>
                                        <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                            Reason
                                        </h4>
                                        <p className="text-sm text-slate-900 dark:text-neutral-100 italic">
                                            "{selectedEvent.metadata.reason}"
                                        </p>
                                    </div>
                                )}

                                {!selectedEvent.success && selectedEvent.errorMessage && (
                                    <div className="p-3 bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded">
                                        <h4 className="text-sm font-medium text-red-700 dark:text-rose-400 mb-1">
                                            Error
                                        </h4>
                                        <p className="text-sm text-red-600 dark:text-red-300">
                                            {selectedEvent.errorMessage}
                                        </p>
                                    </div>
                                )}

                                <div>
                                    <h4 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        Event ID
                                    </h4>
                                    <p className="text-xs font-mono text-slate-600 dark:text-neutral-400">
                                        {selectedEvent.eventId}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
