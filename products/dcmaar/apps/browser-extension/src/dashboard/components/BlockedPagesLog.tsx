/**
 * @fileoverview Blocked Pages Log Component
 *
 * Time-ordered list of blocked events with allow/temp-allow actions.
 */

import React from 'react';
import { BlockedEventRow } from '../../components/shared/DomainRow';
import type { BlockedEvent } from '../../types';

export interface BlockedPagesLogProps {
    events: BlockedEvent[];
    onAllow: (domain: string) => void;
    onTempAllow: (domain: string) => void;
}

export function BlockedPagesLog({
    events,
    onAllow,
    onTempAllow,
}: BlockedPagesLogProps) {
    if (events.length === 0) {
        return (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">Blocked Pages</h2>
                <p className="text-sm text-gray-500 text-center py-8">
                    No blocked pages in the selected time range.
                </p>
            </div>
        );
    }

    return (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900">Blocked Pages</h2>
                <span className="text-xs text-gray-500">{events.length} events</span>
            </div>

            <div className="space-y-2 max-h-96 overflow-y-auto">
                {events.map((event) => (
                    <BlockedEventRow
                        key={event.id}
                        timestamp={event.timestamp}
                        domain={event.domain}
                        url={event.url}
                        title={event.title}
                        reason={event.reason}
                        onAllow={() => onAllow(event.domain)}
                        onTempAllow={() => onTempAllow(event.domain)}
                    />
                ))}
            </div>
        </div>
    );
}
