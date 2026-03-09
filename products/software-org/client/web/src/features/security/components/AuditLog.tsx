import { memo } from 'react';
import { useAuditLog } from '@/hooks/useSecurityData';

/**
 * Audit event log with filtering and search capabilities.
 *
 * <p><b>Purpose</b><br>
 * Displays audit trail of security events, access attempts, configuration changes.
 * Supports filtering by event type, user, date range, and severity.
 *
 * <p><b>Features</b><br>
 * - Event list with timestamps
 * - Event type filtering (Login, Logout, Access, Config, Error)
 * - User filtering
 * - Severity indicators (info, warning, error)
 * - Search across events
 * - Export audit trail
 * - Pagination support
 *
 * @doc.type component
 * @doc.purpose Audit log viewer
 * @doc.layer product
 * @doc.pattern Log
 */

// Type matches securityApi.AuditEvent structure
// { id, timestamp, eventType, userId, userName, resource, action, status, ipAddress, details }

// Event icon mapping
const getEventIcon = (type: string) => {
    switch (type) {
        case 'login':
            return '🔓';
        case 'logout':
            return '🔐';
        case 'access':
            return '👁️';
        case 'config':
            return '⚙️';
        case 'error':
            return '❌';
        default:
            return '•';
    }
};

// Status-based rendering now handled inline

export const AuditLog = memo(function AuditLog() {
    // GIVEN: Security dashboard opened on audit log tab
    // WHEN: Component renders
    // THEN: Display audit events with filtering capabilities

    // Fetch real audit log events from API
    const { data: allEvents = [], isLoading } = useAuditLog({ limit: 100, refetchInterval: 20000 });

    // Show loading state
    if (isLoading && allEvents.length === 0) {
        return (
            <div className="space-y-4">
                <div className="h-10 bg-slate-700 rounded animate-pulse" />
                <div className="space-y-2">
                    {[...Array(5)].map((_, i) => (
                        <div key={i} className="h-12 bg-slate-700 rounded animate-pulse" />
                    ))}
                </div>
            </div>
        );
    }

    const events = allEvents;

    return (
        <div className="space-y-4">
            {/* Filters */}
            <div className="flex gap-2 flex-wrap">
                <input
                    type="search"
                    placeholder="Search events..."
                    className="px-3 py-2 bg-slate-700 text-slate-200 rounded border border-slate-600 text-sm focus:border-blue-500 focus:outline-none"
                />
                <select className="px-3 py-2 bg-slate-700 text-slate-200 rounded border border-slate-600 text-sm">
                    <option>All Types</option>
                    <option>Login</option>
                    <option>Access</option>
                    <option>Config</option>
                    <option>Error</option>
                </select>
                <select className="px-3 py-2 bg-slate-700 text-slate-200 rounded border border-slate-600 text-sm">
                    <option>All Severity</option>
                    <option>Info</option>
                    <option>Warning</option>
                    <option>Error</option>
                </select>
                <button className="ml-auto px-3 py-2 text-sm bg-slate-700 hover:bg-slate-600 text-slate-300 rounded">
                    📤 Export
                </button>
            </div>

            {/* Event List */}
            <div className="space-y-2 max-h-96 overflow-y-auto">
                {events.map((event) => (
                    <div
                        key={event.id}
                        className={`rounded-lg p-3 ${event.status === 'failure' ? 'bg-red-950' : 'bg-green-950'} cursor-pointer hover:opacity-80 transition-opacity`}
                    >
                        <div className="flex items-start justify-between gap-2">
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 mb-1">
                                    <span className="text-lg">{getEventIcon(event.eventType)}</span>
                                    <span className="font-medium">{event.action}</span>
                                    <span className="text-xs text-slate-500 font-mono">{event.eventType.toUpperCase()}</span>
                                </div>

                                <div className="text-sm mb-1">
                                    <span className="text-slate-300">by</span>
                                    <span className="ml-1 font-mono text-xs text-slate-300">{event.userName}</span>
                                    <span className="ml-2 text-slate-500">→</span>
                                    <span className="ml-2 font-mono text-xs text-slate-300">{event.resource}</span>
                                </div>

                                <div className="flex items-center gap-3 text-xs text-slate-500">
                                    <span className="font-mono">{new Date(event.timestamp).toLocaleString()}</span>
                                    <span>IP: {event.ipAddress}</span>
                                </div>
                            </div>

                            <button className="px-2 py-1 text-xs bg-slate-700 hover:bg-slate-600 rounded text-slate-300">
                                Details
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* Statistics */}
            <div className="grid grid-cols-4 gap-2 mt-4 pt-4 border-t border-slate-700">
                <div className="bg-slate-800 rounded p-2 text-center">
                    <div className="text-xs text-slate-500">Total Events</div>
                    <div className="text-lg font-bold text-slate-200">{events.length}</div>
                </div>
                <div className="bg-slate-800 rounded p-2 text-center">
                    <div className="text-xs text-slate-500">Failures</div>
                    <div className="text-lg font-bold text-red-400">
                        {events.filter((e) => e.status === 'failure').length}
                    </div>
                </div>
                <div className="bg-slate-800 rounded p-2 text-center">
                    <div className="text-xs text-slate-500">Success</div>
                    <div className="text-lg font-bold text-green-400">
                        {events.filter((e) => e.status === 'success').length}
                    </div>
                </div>
                <div className="bg-slate-800 rounded p-2 text-center">
                    <div className="text-xs text-slate-500">Users Active</div>
                    <div className="text-lg font-bold text-slate-200">
                        {new Set(events.map((e) => e.userId)).size}
                    </div>
                </div>
            </div>
        </div>
    );
});

export default AuditLog;
