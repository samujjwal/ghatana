/**
 * Realtime Stream Integration for Data-Cloud
 *
 * Bridges @ghatana/realtime with Data-Cloud's EventCloud and Brain systems.
 * Provides typed hooks for real-time event and state streaming.
 *
 * @doc.type module
 * @doc.purpose Realtime integration for Data-Cloud
 * @doc.layer frontend
 * @doc.pattern Integration
 */

import * as React from 'react';
import { createContext, useContext, useMemo, useEffect, useState, useCallback, type ReactNode } from 'react';

// ============================================
// BASE TYPES (Local definitions to avoid import issues)
// ============================================

/** Stream event */
export interface StreamEvent<T = unknown> {
    id: string;
    type: string;
    timestamp: string;
    data: T;
    source?: string;
    payload?: Record<string, unknown>;
    severity?: 'info' | 'warning' | 'error' | 'critical';
    correlationId?: string;
    tags?: string[];
    metadata?: Record<string, unknown>;
}

// ============================================
// DATA-CLOUD SPECIFIC EVENT TYPES
// ============================================

/**
 * EventCloud event from Data-Cloud's event streaming system
 */
export interface EventCloudEvent {
    /** Event ID */
    id: string;
    /** Event type */
    type: string;
    /** Source system/tenant */
    source: string;
    /** Event timestamp */
    timestamp: string;
    /** Entity ID if applicable */
    entityId?: string;
    /** Entity type if applicable */
    entityType?: string;
    /** Event payload */
    payload: Record<string, unknown>;
    /** Event metadata */
    metadata?: {
        correlationId?: string;
        causationId?: string;
        tenantId?: string;
        version?: number;
    };
    /** Severity level */
    severity?: 'info' | 'warning' | 'error' | 'critical';
}

/**
 * Brain state update from the AI subsystem
 */
export interface BrainStateUpdate {
    /** Update ID */
    id: string;
    /** Brain subsystem */
    subsystem: 'spotlight' | 'autonomy' | 'learning' | 'governance' | 'optimization';
    /** Update type */
    type: 'decision' | 'insight' | 'action' | 'alert' | 'status';
    /** Timestamp */
    timestamp: string;
    /** Update payload */
    payload: {
        title: string;
        description?: string;
        confidence?: number;
        affectedEntities?: string[];
        actionRequired?: boolean;
    };
    /** Priority */
    priority?: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Entity change event
 */
export interface EntityChangeEvent {
    /** Event ID */
    id: string;
    /** Entity ID */
    entityId: string;
    /** Entity type */
    entityType: string;
    /** Change type */
    changeType: 'created' | 'updated' | 'deleted' | 'linked' | 'unlinked';
    /** Timestamp */
    timestamp: string;
    /** Changed fields */
    changedFields?: string[];
    /** Change actor */
    actor?: {
        type: 'user' | 'system' | 'brain';
        id: string;
        name?: string;
    };
    /** Previous values (for updates) */
    previousValues?: Record<string, unknown>;
    /** New values */
    newValues?: Record<string, unknown>;
}

// ============================================
// WEBSOCKET CONNECTION
// ============================================

type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

interface WebSocketState<T> {
    data: T[];
    status: ConnectionStatus;
    error: Error | null;
    lastEventTime: Date | null;
}

function createWebSocketHook<T>(
    endpoint: string,
    options?: {
        maxEvents?: number;
        reconnectInterval?: number;
        transform?: (raw: unknown) => T;
    }
) {
    const maxEvents = options?.maxEvents ?? 1000;
    const reconnectInterval = options?.reconnectInterval ?? 3000;
    const transform = options?.transform ?? ((raw) => raw as T);

    return function useWebSocketStream(
        topic: string,
        enabled: boolean = true
    ): WebSocketState<T> & { clear: () => void } {
        const [state, setState] = useState<WebSocketState<T>>({
            data: [],
            status: 'disconnected',
            error: null,
            lastEventTime: null,
        });

        const clear = useCallback(() => {
            setState((prev) => ({ ...prev, data: [] }));
        }, []);

        useEffect(() => {
            if (!enabled) {
                setState((prev) => ({ ...prev, status: 'disconnected' }));
                return;
            }

            let ws: WebSocket | null = null;
            let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

            const connect = () => {
                setState((prev) => ({ ...prev, status: 'connecting', error: null }));

                const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}${endpoint}?topic=${encodeURIComponent(topic)}`;
                ws = new WebSocket(wsUrl);

                ws.onopen = () => {
                    setState((prev) => ({ ...prev, status: 'connected', error: null }));
                };

                ws.onmessage = (event) => {
                    try {
                        const raw = JSON.parse(event.data);
                        const transformed = transform(raw);
                        setState((prev) => ({
                            ...prev,
                            data: [transformed, ...prev.data].slice(0, maxEvents),
                            lastEventTime: new Date(),
                        }));
                    } catch (err) {
                        console.error('Failed to parse WebSocket message:', err);
                    }
                };

                ws.onerror = (event) => {
                    setState((prev) => ({
                        ...prev,
                        status: 'error',
                        error: new Error('WebSocket connection error'),
                    }));
                };

                ws.onclose = () => {
                    setState((prev) => ({ ...prev, status: 'disconnected' }));
                    // Attempt to reconnect
                    reconnectTimer = setTimeout(connect, reconnectInterval);
                };
            };

            connect();

            return () => {
                if (reconnectTimer) clearTimeout(reconnectTimer);
                if (ws) {
                    ws.close();
                    ws = null;
                }
            };
        }, [endpoint, topic, enabled, maxEvents, reconnectInterval, transform]);

        return { ...state, clear };
    };
}

// ============================================
// HOOKS
// ============================================

/**
 * Hook for EventCloud streaming
 *
 * @example
 * ```tsx
 * const { events, status, clear } = useEventCloudStream('tenant-1/entities');
 *
 * <EventStreamVisualization
 *   events={events}
 *   realtime={status === 'connected'}
 *   onEventClick={(e) => showEventDetails(e)}
 * />
 * ```
 */
export function useEventCloudStream(
    topic: string,
    options?: {
        enabled?: boolean;
        maxEvents?: number;
        filter?: {
            types?: string[];
            entityTypes?: string[];
            severities?: Array<'info' | 'warning' | 'error' | 'critical'>;
        };
    }
) {
    const useStream = useMemo(
        () =>
            createWebSocketHook<EventCloudEvent>('/api/ws/eventcloud', {
                maxEvents: options?.maxEvents ?? 1000,
                transform: (raw) => raw as EventCloudEvent,
            }),
        [options?.maxEvents]
    );

    const { data, status, error, lastEventTime, clear } = useStream(
        topic,
        options?.enabled !== false
    );

    // Apply client-side filtering
    const filteredData = useMemo(() => {
        let result = data;

        if (options?.filter?.types?.length) {
            result = result.filter((e) => options.filter!.types!.includes(e.type));
        }

        if (options?.filter?.entityTypes?.length) {
            result = result.filter((e) => e.entityType && options.filter!.entityTypes!.includes(e.entityType));
        }

        if (options?.filter?.severities?.length) {
            result = result.filter((e) => e.severity && options.filter!.severities!.includes(e.severity));
        }

        return result;
    }, [data, options?.filter]);

    // Convert to StreamEvent for @ghatana/ui-extensions compatibility
    const eventsAsStreamEvents: StreamEvent[] = useMemo(
        () =>
            filteredData.map((e) => ({
                id: e.id,
                type: e.type,
                source: e.source,
                timestamp: e.timestamp,
                data: e.payload,
                payload: e.payload,
                severity: e.severity,
                correlationId: e.metadata?.correlationId,
                tags: e.entityType ? [e.entityType] : undefined,
                metadata: e.metadata,
            })),
        [filteredData]
    );

    // Stats
    const stats = useMemo(() => {
        const bySeverity: Record<string, number> = { info: 0, warning: 0, error: 0, critical: 0 };
        const byType: Record<string, number> = {};

        data.forEach((e) => {
            if (e.severity) bySeverity[e.severity]++;
            byType[e.type] = (byType[e.type] ?? 0) + 1;
        });

        return {
            total: data.length,
            filtered: filteredData.length,
            bySeverity,
            byType,
        };
    }, [data, filteredData]);

    return {
        events: eventsAsStreamEvents,
        rawEvents: filteredData,
        allEvents: data,
        status,
        isConnected: status === 'connected',
        error,
        lastEventTime,
        stats,
        clear,
    };
}

/**
 * Hook for Brain state streaming
 *
 * @example
 * ```tsx
 * const { updates, status } = useBrainStateStream();
 *
 * // Show brain updates in real-time
 * updates.map(update => (
 *   <BrainUpdateCard key={update.id} update={update} />
 * ))
 * ```
 */
export function useBrainStateStream(options?: {
    enabled?: boolean;
    subsystems?: BrainStateUpdate['subsystem'][];
    maxUpdates?: number;
}) {
    const useStream = useMemo(
        () =>
            createWebSocketHook<BrainStateUpdate>('/api/ws/brain', {
                maxEvents: options?.maxUpdates ?? 500,
                transform: (raw) => raw as BrainStateUpdate,
            }),
        [options?.maxUpdates]
    );

    const { data, status, error, lastEventTime, clear } = useStream(
        'brain-state',
        options?.enabled !== false
    );

    // Apply subsystem filter
    const filteredData = useMemo(() => {
        if (!options?.subsystems?.length) return data;
        return data.filter((u) => options.subsystems!.includes(u.subsystem));
    }, [data, options?.subsystems]);

    // Convert to StreamEvent for visualization
    const updatesAsStreamEvents: StreamEvent[] = useMemo(
        () =>
            filteredData.map((u) => ({
                id: u.id,
                type: u.type,
                source: `brain/${u.subsystem}`,
                timestamp: u.timestamp,
                data: u.payload,
                payload: u.payload as Record<string, unknown>,
                severity: u.priority === 'critical' ? 'critical' : u.priority === 'high' ? 'warning' : 'info',
                tags: [u.subsystem, u.type],
            })),
        [filteredData]
    );

    // Stats
    const stats = useMemo(() => {
        const bySubsystem: Record<string, number> = {};
        const byType: Record<string, number> = {};
        let actionRequired = 0;

        data.forEach((u) => {
            bySubsystem[u.subsystem] = (bySubsystem[u.subsystem] ?? 0) + 1;
            byType[u.type] = (byType[u.type] ?? 0) + 1;
            if (u.payload.actionRequired) actionRequired++;
        });

        return {
            total: data.length,
            filtered: filteredData.length,
            actionRequired,
            bySubsystem,
            byType,
        };
    }, [data, filteredData]);

    return {
        updates: filteredData,
        updatesAsEvents: updatesAsStreamEvents,
        status,
        isConnected: status === 'connected',
        error,
        lastEventTime,
        stats,
        clear,
    };
}

/**
 * Hook for entity change streaming
 *
 * @example
 * ```tsx
 * const { changes, status } = useEntityChangeStream({
 *   entityTypes: ['customer', 'order'],
 * });
 *
 * // React to entity changes
 * useEffect(() => {
 *   if (changes.length > 0) {
 *     const latest = changes[0];
 *     toast(`${latest.entityType} ${latest.changeType}`);
 *   }
 * }, [changes]);
 * ```
 */
export function useEntityChangeStream(options?: {
    enabled?: boolean;
    entityTypes?: string[];
    changeTypes?: EntityChangeEvent['changeType'][];
    maxChanges?: number;
}) {
    const useStream = useMemo(
        () =>
            createWebSocketHook<EntityChangeEvent>('/api/ws/entities', {
                maxEvents: options?.maxChanges ?? 500,
                transform: (raw) => raw as EntityChangeEvent,
            }),
        [options?.maxChanges]
    );

    const { data, status, error, lastEventTime, clear } = useStream(
        'entity-changes',
        options?.enabled !== false
    );

    // Apply filters
    const filteredData = useMemo(() => {
        let result = data;

        if (options?.entityTypes?.length) {
            result = result.filter((e) => options.entityTypes!.includes(e.entityType));
        }

        if (options?.changeTypes?.length) {
            result = result.filter((e) => options.changeTypes!.includes(e.changeType));
        }

        return result;
    }, [data, options?.entityTypes, options?.changeTypes]);

    // Convert to StreamEvent
    const changesAsStreamEvents: StreamEvent[] = useMemo(
        () =>
            filteredData.map((c) => ({
                id: c.id,
                type: c.changeType,
                source: c.entityType,
                timestamp: c.timestamp,
                data: {
                    entityId: c.entityId,
                    changedFields: c.changedFields,
                    actor: c.actor,
                    previousValues: c.previousValues,
                    newValues: c.newValues,
                },
                payload: {
                    entityId: c.entityId,
                    changedFields: c.changedFields,
                    actor: c.actor,
                    previousValues: c.previousValues,
                    newValues: c.newValues,
                },
                tags: [c.entityType, c.changeType],
            })),
        [filteredData]
    );

    // Stats
    const stats = useMemo(() => {
        const byEntityType: Record<string, number> = {};
        const byChangeType: Record<string, number> = {};

        data.forEach((c) => {
            byEntityType[c.entityType] = (byEntityType[c.entityType] ?? 0) + 1;
            byChangeType[c.changeType] = (byChangeType[c.changeType] ?? 0) + 1;
        });

        return {
            total: data.length,
            filtered: filteredData.length,
            byEntityType,
            byChangeType,
        };
    }, [data, filteredData]);

    return {
        changes: filteredData,
        changesAsEvents: changesAsStreamEvents,
        status,
        isConnected: status === 'connected',
        error,
        lastEventTime,
        stats,
        clear,
    };
}

// ============================================
// CONTEXT & PROVIDER
// ============================================

interface DataCloudRealtimeContextValue {
    eventCloudStatus: ConnectionStatus;
    brainStatus: ConnectionStatus;
    entityStatus: ConnectionStatus;
    isFullyConnected: boolean;
}

const DataCloudRealtimeContext = createContext<DataCloudRealtimeContextValue | null>(null);

/**
 * Provider for Data-Cloud realtime streaming
 *
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <DataCloudRealtimeProvider>
 *       <Dashboard />
 *     </DataCloudRealtimeProvider>
 *   );
 * }
 * ```
 */
export function DataCloudRealtimeProvider({
    children,
    defaultTopic = 'default',
}: {
    children: ReactNode;
    defaultTopic?: string;
}) {
    const eventCloud = useEventCloudStream(defaultTopic);
    const brain = useBrainStateStream();
    const entity = useEntityChangeStream();

    const value: DataCloudRealtimeContextValue = useMemo(
        () => ({
            eventCloudStatus: eventCloud.status,
            brainStatus: brain.status,
            entityStatus: entity.status,
            isFullyConnected:
                eventCloud.isConnected && brain.isConnected && entity.isConnected,
        }),
        [eventCloud.status, brain.status, entity.status, eventCloud.isConnected, brain.isConnected, entity.isConnected]
    );

    return (
        <DataCloudRealtimeContext.Provider value={value}>
            {children}
        </DataCloudRealtimeContext.Provider>
    );
}

/**
 * Hook to access Data-Cloud realtime context
 */
export function useDataCloudRealtimeContext() {
    const context = useContext(DataCloudRealtimeContext);
    if (!context) {
        throw new Error('useDataCloudRealtimeContext must be used within DataCloudRealtimeProvider');
    }
    return context;
}
