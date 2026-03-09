/**
 * Related Events Panel Component
 *
 * Shows events related to the selected audit entry:
 * - Temporally related (before/after)
 * - Same actor events
 * - Same resource events
 * - Correlated changes
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Card, Chip, Stack } from '@ghatana/ui';
import type { AuditEntry } from '@/types/org.types';

interface RelatedEventsPanelProps {
    currentEntry: AuditEntry;
    allEntries: AuditEntry[];
    onEventClick?: (entry: AuditEntry) => void;
}

interface RelatedEventsGroup {
    temporal: AuditEntry[]; // Events before/after current event
    sameActor: AuditEntry[]; // Other events by same user
    sameResource: AuditEntry[]; // Other changes to same resource
    correlated: AuditEntry[]; // Potentially related based on pattern
}

/**
 * Find related events based on various criteria
 */
function findRelatedEvents(
    currentEntry: AuditEntry,
    allEntries: AuditEntry[]
): RelatedEventsGroup {
    const currentTime = new Date(currentEntry.timestamp).getTime();
    const timeWindow = 3600000; // 1 hour in milliseconds

    const temporal: AuditEntry[] = [];
    const sameActor: AuditEntry[] = [];
    const sameResource: AuditEntry[] = [];
    const correlated: AuditEntry[] = [];

    for (const entry of allEntries) {
        if (entry.id === currentEntry.id) continue;

        const entryTime = new Date(entry.timestamp).getTime();
        const timeDiff = Math.abs(currentTime - entryTime);

        // Temporal: within 1 hour
        if (timeDiff <= timeWindow) {
            temporal.push(entry);
        }

        // Same actor
        if (entry.actor === currentEntry.actor && entry.id !== currentEntry.id) {
            sameActor.push(entry);
        }

        // Same resource
        if (
            entry.target.type === currentEntry.target.type &&
            entry.target.id === currentEntry.target.id &&
            entry.id !== currentEntry.id
        ) {
            sameResource.push(entry);
        }

        // Correlated: related actions or mentioned in metadata
        if (
            entry.metadata?.relatedTo === currentEntry.id ||
            currentEntry.metadata?.relatedTo === entry.id ||
            (entry.action.split(':')[0] === currentEntry.action.split(':')[0] && timeDiff <= timeWindow)
        ) {
            correlated.push(entry);
        }
    }

    // Sort by timestamp (most recent first)
    const sortByTime = (a: AuditEntry, b: AuditEntry) =>
        new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();

    temporal.sort(sortByTime);
    sameActor.sort(sortByTime);
    sameResource.sort(sortByTime);
    correlated.sort(sortByTime);

    return {
        temporal: temporal.slice(0, 5),
        sameActor: sameActor.slice(0, 5),
        sameResource: sameResource.slice(0, 5),
        correlated: correlated.slice(0, 5),
    };
}

/**
 * Format timestamp relative to current time
 */
function formatRelativeTime(timestamp: Date): string {
    const now = new Date();
    const diff = now.getTime() - new Date(timestamp).getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    return `${days}d ago`;
}

/**
 * Get action icon
 */
function getActionIcon(action: string): string {
    if (action.includes('created')) return '➕';
    if (action.includes('deleted')) return '🗑️';
    if (action.includes('updated')) return '✏️';
    if (action.includes('moved')) return '↔️';
    if (action.includes('approved')) return '✅';
    return '📝';
}

/**
 * Event list item component
 */
function EventListItem({
    entry,
    onClick,
    showTimestamp = true,
}: {
    entry: AuditEntry;
    onClick?: (entry: AuditEntry) => void;
    showTimestamp?: boolean;
}) {
    return (
        <div
            className={`
        p-3 rounded-lg border border-slate-200 dark:border-neutral-700
        ${onClick ? 'cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800 hover:border-blue-300' : ''}
        transition-all
      `}
            onClick={() => onClick?.(entry)}
        >
            <div className="flex items-start gap-3">
                <span className="text-lg">{getActionIcon(entry.action)}</span>
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-slate-900 dark:text-neutral-100 truncate">
                            {entry.action.replace(/:/g, ' ')}
                        </span>
                        <Chip label={entry.target.type} size="small" variant="outlined" />
                    </div>
                    <p className="text-xs text-slate-600 dark:text-neutral-400 truncate">
                        {entry.target.name}
                    </p>
                    {showTimestamp && (
                        <p className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                            {formatRelativeTime(entry.timestamp)}
                        </p>
                    )}
                </div>
            </div>
        </div>
    );
}

export function RelatedEventsPanel({
    currentEntry,
    allEntries,
    onEventClick,
}: RelatedEventsPanelProps) {
    const [activeTab, setActiveTab] = useState<'temporal' | 'actor' | 'resource' | 'correlated'>(
        'temporal'
    );

    const relatedEvents = findRelatedEvents(currentEntry, allEntries);

    const tabs = [
        {
            id: 'temporal' as const,
            label: 'Timeline',
            count: relatedEvents.temporal.length,
            icon: '⏱️',
        },
        {
            id: 'actor' as const,
            label: 'Same User',
            count: relatedEvents.sameActor.length,
            icon: '👤',
        },
        {
            id: 'resource' as const,
            label: 'Same Resource',
            count: relatedEvents.sameResource.length,
            icon: '📦',
        },
        {
            id: 'correlated' as const,
            label: 'Correlated',
            count: relatedEvents.correlated.length,
            icon: '🔗',
        },
    ];

    const getActiveEvents = () => {
        switch (activeTab) {
            case 'temporal':
                return relatedEvents.temporal;
            case 'actor':
                return relatedEvents.sameActor;
            case 'resource':
                return relatedEvents.sameResource;
            case 'correlated':
                return relatedEvents.correlated;
        }
    };

    const activeEvents = getActiveEvents();

    return (
        <Card>
            <Box className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Related Events
                </h3>

                {/* Tabs */}
                <div className="flex gap-2 mb-4 overflow-x-auto">
                    {tabs.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className={`
                flex items-center gap-2 px-4 py-2 rounded-lg font-medium text-sm whitespace-nowrap
                transition-all
                ${activeTab === tab.id
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-neutral-700'
                                }
              `}
                        >
                            <span>{tab.icon}</span>
                            <span>{tab.label}</span>
                            {tab.count > 0 && (
                                <span
                                    className={`
                    px-2 py-0.5 rounded-full text-xs font-bold
                    ${activeTab === tab.id
                                            ? 'bg-blue-500 text-white'
                                            : 'bg-slate-200 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300'
                                        }
                  `}
                                >
                                    {tab.count}
                                </span>
                            )}
                        </button>
                    ))}
                </div>

                {/* Event List */}
                {activeEvents.length > 0 ? (
                    <Stack spacing={2}>
                        {activeEvents.map((entry) => (
                            <EventListItem key={entry.id} entry={entry} onClick={onEventClick} />
                        ))}
                    </Stack>
                ) : (
                    <div className="py-12 text-center text-slate-500 dark:text-neutral-400">
                        <div className="text-4xl mb-4">🔍</div>
                        <p className="text-sm">No related events found</p>
                        <p className="text-xs mt-1">
                            {activeTab === 'temporal' && 'No events within 1 hour of this event'}
                            {activeTab === 'actor' && 'No other events by this user'}
                            {activeTab === 'resource' && 'No other changes to this resource'}
                            {activeTab === 'correlated' && 'No correlated events detected'}
                        </p>
                    </div>
                )}

                {/* Summary Stats */}
                <div className="mt-6 p-4 bg-slate-50 dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-700">
                    <h4 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-3">
                        Related Events Summary
                    </h4>
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Within Timeline</p>
                            <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {relatedEvents.temporal.length}
                            </p>
                        </div>
                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400">By Same User</p>
                            <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {relatedEvents.sameActor.length}
                            </p>
                        </div>
                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Same Resource</p>
                            <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {relatedEvents.sameResource.length}
                            </p>
                        </div>
                        <div>
                            <p className="text-xs text-slate-600 dark:text-neutral-400">Correlated</p>
                            <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {relatedEvents.correlated.length}
                            </p>
                        </div>
                    </div>
                </div>

                {/* Pattern Detection */}
                {relatedEvents.sameActor.length >= 3 && (
                    <div className="mt-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-700">
                        <div className="flex items-start gap-2">
                            <span className="text-yellow-600 text-lg">⚠️</span>
                            <div>
                                <p className="text-sm font-semibold text-yellow-800 dark:text-yellow-400">
                                    Pattern Detected
                                </p>
                                <p className="text-xs text-yellow-700 dark:text-yellow-500 mt-1">
                                    User has made {relatedEvents.sameActor.length} changes recently. This may indicate
                                    unusual activity.
                                </p>
                            </div>
                        </div>
                    </div>
                )}
            </Box>
        </Card>
    );
}
