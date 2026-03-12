/**
 * EventExplorerPage — Real-time AEP event stream explorer.
 *
 * Allows browsing, filtering, and live-tailing events flowing through
 * the Data-Cloud event fabric across all four tiers (HOT/WARM/COOL/COLD).
 *
 * @doc.type component
 * @doc.purpose Real-time event explorer for Data-Cloud event fabric
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { eventsService, type EventEntry, type EventTier, type EventQueryParams } from '../api/events.service';

// =============================================================================
// Constants
// =============================================================================

const TIER_COLORS: Record<EventTier, string> = {
  HOT: 'bg-red-100 text-red-800',
  WARM: 'bg-orange-100 text-orange-800',
  COOL: 'bg-blue-100 text-blue-800',
  COLD: 'bg-slate-100 text-slate-700',
};

const TIER_ORDER: EventTier[] = ['HOT', 'WARM', 'COOL', 'COLD'];

const MAX_LIVE_EVENTS = 200;

// =============================================================================
// Sub-components
// =============================================================================

function TierBadge({ tier }: { tier: EventTier }): React.ReactElement {
  return (
    <span className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ${TIER_COLORS[tier]}`}>
      {tier}
    </span>
  );
}

function EventRow({
  event,
  selected,
  onClick,
}: {
  event: EventEntry;
  selected: boolean;
  onClick: () => void;
}): React.ReactElement {
  return (
    <tr
      className={`cursor-pointer border-b border-gray-100 hover:bg-gray-50 transition-colors ${selected ? 'bg-indigo-50' : ''}`}
      onClick={onClick}
      data-testid={`event-row-${event.id}`}
    >
      <td className="px-4 py-2 font-mono text-xs text-gray-500 whitespace-nowrap">
        {new Date(event.timestamp).toLocaleTimeString()}
      </td>
      <td className="px-4 py-2">
        <TierBadge tier={event.tier} />
      </td>
      <td className="px-4 py-2 text-sm font-medium text-gray-800 max-w-xs truncate">
        {event.eventType}
      </td>
      <td className="px-4 py-2 font-mono text-xs text-gray-500 max-w-xs truncate">
        {event.source ?? '—'}
      </td>
      <td className="px-4 py-2 font-mono text-xs text-gray-400 truncate max-w-xs">
        {event.id}
      </td>
    </tr>
  );
}

function EventDetailPanel({
  event,
  onClose,
}: {
  event: EventEntry;
  onClose: () => void;
}): React.ReactElement {
  return (
    <aside className="w-96 shrink-0 border-l border-gray-200 bg-white flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
        <h3 className="text-sm font-semibold text-gray-800">Event Detail</h3>
        <button
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600"
          aria-label="Close detail panel"
        >
          ✕
        </button>
      </div>
      <div className="p-4 space-y-4 overflow-y-auto flex-1 text-sm">
        <dl className="space-y-2">
          {[
            ['ID', event.id],
            ['Type', event.eventType],
            ['Tier', event.tier],
            ['Tenant', event.tenantId],
            ['Source', event.source ?? '—'],
            ['Timestamp', new Date(event.timestamp).toISOString()],
            ['Idempotency Key', event.idempotencyKey ?? '—'],
            ['Correlation ID', event.correlationId ?? '—'],
          ].map(([label, value]) => (
            <div key={label} className="flex gap-2">
              <dt className="w-36 shrink-0 text-gray-500 font-medium">{label}</dt>
              <dd className="font-mono text-xs text-gray-700 break-all">{value as string}</dd>
            </div>
          ))}
        </dl>
        <div>
          <p className="text-gray-500 font-medium mb-1">Payload</p>
          <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-xs overflow-auto max-h-60">
            {JSON.stringify(event.payload, null, 2)}
          </pre>
        </div>
        {Object.keys(event.metadata).length > 0 && (
          <div>
            <p className="text-gray-500 font-medium mb-1">Metadata</p>
            <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-xs overflow-auto max-h-40">
              {JSON.stringify(event.metadata, null, 2)}
            </pre>
          </div>
        )}
      </div>
    </aside>
  );
}

// =============================================================================
// Page
// =============================================================================

/**
 * EventExplorerPage — browse and live-tail events across the DC event fabric.
 *
 * @doc.type component
 * @doc.purpose Event explorer page with SSE live-tail
 * @doc.layer product
 * @doc.pattern Page
 */
export function EventExplorerPage(): React.ReactElement {
  const [filters, setFilters] = useState<EventQueryParams>({});
  const [selectedEvent, setSelectedEvent] = useState<EventEntry | null>(null);
  const [liveMode, setLiveMode] = useState(false);
  const [liveEvents, setLiveEvents] = useState<EventEntry[]>([]);
  const [tierFilter, setTierFilter] = useState<EventTier | 'ALL'>('ALL');
  const [typeFilter, setTypeFilter] = useState('');
  const sseRef = useRef<EventSource | null>(null);

  const queryParams: EventQueryParams = {
    ...filters,
    ...(tierFilter !== 'ALL' ? { tier: tierFilter } : {}),
    ...(typeFilter ? { eventType: typeFilter } : {}),
    limit: 50,
  };

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dc', 'events', queryParams],
    queryFn: () => eventsService.listEvents(queryParams),
    enabled: !liveMode,
    refetchInterval: false,
  });

  const { data: stats } = useQuery({
    queryKey: ['dc', 'events', 'stats'],
    queryFn: () => eventsService.getStats(),
    refetchInterval: 15_000,
  });

  const startLive = useCallback(() => {
    setLiveMode(true);
    setLiveEvents([]);
    const params: EventQueryParams = {};
    if (tierFilter !== 'ALL') params.tier = tierFilter;
    if (typeFilter) params.eventType = typeFilter;
    const sse = eventsService.openStream(params);
    sse.onmessage = (e: MessageEvent) => {
      try {
        const event = JSON.parse(e.data as string) as EventEntry;
        setLiveEvents((prev) => [event, ...prev].slice(0, MAX_LIVE_EVENTS));
      } catch {
        // ignore malformed frames
      }
    };
    sseRef.current = sse;
  }, [tierFilter, typeFilter]);

  const stopLive = useCallback(() => {
    sseRef.current?.close();
    sseRef.current = null;
    setLiveMode(false);
  }, []);

  useEffect(() => () => sseRef.current?.close(), []);

  const displayedEvents = liveMode ? liveEvents : (data?.events ?? []);

  return (
    <div className="flex flex-col h-full bg-white" data-testid="event-explorer-page">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Event Explorer</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Browse events across the Data-Cloud four-tier fabric
          </p>
        </div>
        <div className="flex items-center gap-3">
          {stats && (
            <span className="text-xs text-gray-500">
              {stats.eventsPerMinute.toFixed(1)} events/min
            </span>
          )}
          {liveMode ? (
            <button
              onClick={stopLive}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-red-600 text-white text-sm rounded hover:bg-red-700"
            >
              <span className="inline-block w-2 h-2 bg-white rounded-full animate-pulse" />
              Stop Live
            </button>
          ) : (
            <button
              onClick={startLive}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white text-sm rounded hover:bg-green-700"
            >
              ▶ Live Tail
            </button>
          )}
          {!liveMode && (
            <button
              onClick={() => void refetch()}
              className="px-3 py-1.5 border border-gray-300 text-sm rounded hover:bg-gray-50"
            >
              Refresh
            </button>
          )}
        </div>
      </div>

      {/* Stats Bar */}
      {stats && (
        <div className="flex items-center gap-6 px-6 py-3 bg-gray-50 border-b border-gray-200 text-sm">
          <span className="text-gray-600">Total: <strong>{stats.total.toLocaleString()}</strong></span>
          {TIER_ORDER.map((tier) => (
            <span key={tier} className="text-gray-600">
              <TierBadge tier={tier} /> <strong>{stats.byTier[tier] ?? 0}</strong>
            </span>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="flex items-center gap-3 px-6 py-3 border-b border-gray-200">
        <span className="text-xs text-gray-500 font-medium">Tier:</span>
        {(['ALL', ...TIER_ORDER] as Array<EventTier | 'ALL'>).map((t) => (
          <button
            key={t}
            onClick={() => setTierFilter(t)}
            className={`px-2.5 py-1 text-xs rounded border transition-colors ${
              tierFilter === t
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
            }`}
          >
            {t}
          </button>
        ))}
        <input
          type="text"
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          placeholder="Filter by event type…"
          className="ml-4 px-3 py-1 text-sm border border-gray-300 rounded flex-1 max-w-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
        />
      </div>

      {/* Content */}
      <div className="flex flex-1 overflow-hidden">
        <div className="flex-1 overflow-auto">
          {isLoading && !liveMode && (
            <div className="flex justify-center items-center h-32 text-gray-400">
              Loading events…
            </div>
          )}
          {error instanceof Error && (
            <div className="m-6 p-4 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
              Failed to load events: {error.message}
            </div>
          )}
          {liveMode && liveEvents.length === 0 && (
            <div className="flex justify-center items-center h-32 text-gray-400 text-sm">
              Waiting for events…
            </div>
          )}
          {displayedEvents.length > 0 && (
            <table className="w-full text-left text-sm" aria-label="Event list">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50">
                  <th className="px-4 py-2 font-medium text-gray-500 text-xs">TIME</th>
                  <th className="px-4 py-2 font-medium text-gray-500 text-xs">TIER</th>
                  <th className="px-4 py-2 font-medium text-gray-500 text-xs">TYPE</th>
                  <th className="px-4 py-2 font-medium text-gray-500 text-xs">SOURCE</th>
                  <th className="px-4 py-2 font-medium text-gray-500 text-xs">ID</th>
                </tr>
              </thead>
              <tbody>
                {displayedEvents.map((ev) => (
                  <EventRow
                    key={ev.id}
                    event={ev}
                    selected={selectedEvent?.id === ev.id}
                    onClick={() =>
                      setSelectedEvent((prev) => (prev?.id === ev.id ? null : ev))
                    }
                  />
                ))}
              </tbody>
            </table>
          )}
          {!isLoading && !liveMode && displayedEvents.length === 0 && (
            <div className="flex flex-col items-center justify-center h-32 text-gray-400 text-sm">
              <p>No events found</p>
              <p className="text-xs mt-1">Adjust filters or check the time range</p>
            </div>
          )}
        </div>

        {selectedEvent && (
          <EventDetailPanel
            event={selectedEvent}
            onClose={() => setSelectedEvent(null)}
          />
        )}
      </div>
    </div>
  );
}
