/**
 * Real-time EventCloud Hooks
 *
 * Provides React hooks for real-time EventCloud topology updates.
 * Connects to ActiveJ backend via @ghatana/realtime WebSocket client.
 *
 * @doc.type hooks
 * @doc.purpose Real-time EventCloud streaming hooks
 * @doc.layer product
 * @doc.pattern ReactHook
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    useActiveJStream,
    useActiveJSubscription,
    type ActiveJStreamMessage,
} from '@ghatana/realtime';
import type {
    EventCloudNode,
    EventCloudEdge,
    EventCloudNodeData,
    EventCloudEdgeData,
    TopologyMetrics,
    TopologyNodeStatus,
} from '@ghatana/canvas/topology';

// ============================================
// TYPES
// ============================================

/**
 * Topology update event from server.
 */
export interface TopologyUpdateEvent {
    type: 'node_added' | 'node_removed' | 'node_updated' | 'edge_added' | 'edge_removed' | 'full_sync';
    nodeId?: string;
    edgeId?: string;
    node?: EventCloudNodeData;
    edge?: EventCloudEdgeData;
    nodes?: EventCloudNode[];
    edges?: EventCloudEdge[];
}

/**
 * Metrics update event from server.
 */
export interface MetricsUpdateEvent {
    nodeId: string;
    metrics: TopologyMetrics;
    status?: TopologyNodeStatus;
    timestamp: number;
}

/**
 * Configuration for useEventCloudStream hook.
 */
export interface UseEventCloudStreamOptions {
    /** ActiveJ server base URL */
    serverUrl: string;

    /** Tenant ID for multi-tenant isolation */
    tenantId: string;

    /** Authentication token */
    authToken?: string;

    /** Auto-connect on mount */
    autoConnect?: boolean;

    /** Metrics refresh interval in ms */
    metricsRefreshInterval?: number;

    /** Initial nodes */
    initialNodes?: EventCloudNode[];

    /** Initial edges */
    initialEdges?: EventCloudEdge[];
}

/**
 * Return type for useEventCloudStream hook.
 */
export interface UseEventCloudStreamReturn {
    /** Current topology nodes */
    nodes: EventCloudNode[];

    /** Current topology edges */
    edges: EventCloudEdge[];

    /** Connection state */
    connectionState: 'disconnected' | 'connecting' | 'connected' | 'error';

    /** Any connection error */
    error: Error | null;

    /** Connect to stream */
    connect: () => Promise<void>;

    /** Disconnect from stream */
    disconnect: () => void;

    /** Request full topology sync */
    requestSync: () => void;

    /** Last update timestamp */
    lastUpdate: number | null;

    /** Whether currently syncing */
    isSyncing: boolean;
}

// ============================================
// HOOK IMPLEMENTATION
// ============================================

/**
 * React hook for real-time EventCloud topology streaming.
 *
 * Connects to ActiveJ backend and receives real-time updates for:
 * - Topology changes (nodes/edges added/removed)
 * - Metrics updates (throughput, latency, error rate)
 * - Status changes (healthy, warning, error)
 *
 * @example
 * ```tsx
 * function EventCloudMonitor() {
 *   const {
 *     nodes,
 *     edges,
 *     connectionState,
 *     error,
 *   } = useEventCloudStream({
 *     serverUrl: 'ws://localhost:8080',
 *     tenantId: 'tenant-123',
 *     autoConnect: true,
 *   });
 *
 *   return (
 *     <EventCloudTopology
 *       nodes={nodes}
 *       edges={edges}
 *       isLoading={connectionState === 'connecting'}
 *       error={error}
 *     />
 *   );
 * }
 * ```
 */
export function useEventCloudStream(options: UseEventCloudStreamOptions): UseEventCloudStreamReturn {
    const {
        serverUrl,
        tenantId,
        authToken,
        autoConnect = true,
        metricsRefreshInterval = 5000,
        initialNodes = [],
        initialEdges = [],
    } = options;

    // State
    const [nodes, setNodes] = useState<EventCloudNode[]>(initialNodes);
    const [edges, setEdges] = useState<EventCloudEdge[]>(initialEdges);
    const [lastUpdate, setLastUpdate] = useState<number | null>(null);
    const [isSyncing, setIsSyncing] = useState(false);

    // Use ActiveJ stream hook
    const {
        state: connectionState,
        error,
        connect,
        disconnect,
        subscribe,
        send,
        isConnected,
    } = useActiveJStream<TopologyUpdateEvent | MetricsUpdateEvent>(
        serverUrl,
        tenantId,
        '/eventcloud/stream',
        {
            authToken,
            topics: ['topology', 'metrics'],
            autoConnect,
            reconnectDelay: 2000,
            maxReconnectAttempts: 10,
        }
    );

    // Handle topology updates
    useActiveJSubscription<TopologyUpdateEvent>(
        subscribe,
        'topology',
        useCallback((message: ActiveJStreamMessage<TopologyUpdateEvent>) => {
            const event = message.payload;
            setLastUpdate(message.timestamp);

            switch (event.type) {
                case 'full_sync':
                    if (event.nodes && event.edges) {
                        setNodes(event.nodes);
                        setEdges(event.edges);
                        setIsSyncing(false);
                    }
                    break;

                case 'node_added':
                    if (event.node && event.nodeId) {
                        setNodes((prev) => [
                            ...prev,
                            {
                                id: event.nodeId!,
                                type: 'eventcloud',
                                position: { x: 0, y: 0 },
                                data: event.node!,
                            },
                        ]);
                    }
                    break;

                case 'node_removed':
                    if (event.nodeId) {
                        setNodes((prev) => prev.filter((n) => n.id !== event.nodeId));
                        setEdges((prev) =>
                            prev.filter((e) => e.source !== event.nodeId && e.target !== event.nodeId)
                        );
                    }
                    break;

                case 'node_updated':
                    if (event.node && event.nodeId) {
                        setNodes((prev) =>
                            prev.map((n) =>
                                n.id === event.nodeId
                                    ? { ...n, data: { ...n.data, ...event.node } }
                                    : n
                            )
                        );
                    }
                    break;

                case 'edge_added':
                    if (event.edge && event.edgeId) {
                        setEdges((prev) => [
                            ...prev,
                            {
                                id: event.edgeId!,
                                source: '', // Would need source/target from event
                                target: '',
                                type: 'eventcloud',
                                data: event.edge!,
                            },
                        ]);
                    }
                    break;

                case 'edge_removed':
                    if (event.edgeId) {
                        setEdges((prev) => prev.filter((e) => e.id !== event.edgeId));
                    }
                    break;
            }
        }, []),
        [subscribe]
    );

    // Handle metrics updates
    useActiveJSubscription<MetricsUpdateEvent>(
        subscribe,
        'metrics',
        useCallback((message: ActiveJStreamMessage<MetricsUpdateEvent>) => {
            const { nodeId, metrics, status } = message.payload;
            setLastUpdate(message.timestamp);

            setNodes((prev) =>
                prev.map((node) =>
                    node.id === nodeId
                        ? {
                            ...node,
                            data: {
                                ...node.data,
                                metrics: { ...node.data.metrics, ...metrics },
                                status: status ?? node.data.status,
                            },
                        }
                        : node
                )
            );
        }, []),
        [subscribe]
    );

    // Request full sync on connect
    useEffect(() => {
        if (isConnected && nodes.length === 0) {
            requestSync();
        }
    }, [isConnected]);

    // Request sync function
    const requestSync = useCallback(() => {
        if (!isConnected) return;

        setIsSyncing(true);
        send({
            type: 'request',
            topic: 'topology',
            payload: { action: 'sync' },
            timestamp: Date.now(),
        });
    }, [isConnected, send]);

    return {
        nodes,
        edges,
        connectionState: connectionState as UseEventCloudStreamReturn['connectionState'],
        error,
        connect,
        disconnect,
        requestSync,
        lastUpdate,
        isSyncing,
    };
}

/**
 * Hook for subscribing to specific stream metrics.
 *
 * @param streamId - Stream/node ID to monitor
 * @param options - Stream connection options
 */
export function useStreamMetrics(
    streamId: string,
    options: Pick<UseEventCloudStreamOptions, 'serverUrl' | 'tenantId' | 'authToken'>
): {
    metrics: TopologyMetrics | null;
    status: TopologyNodeStatus;
    isLoading: boolean;
    error: Error | null;
} {
    const [metrics, setMetrics] = useState<TopologyMetrics | null>(null);
    const [status, setStatus] = useState<TopologyNodeStatus>('inactive');
    const [isLoading, setIsLoading] = useState(true);

    const { subscribe, isConnected, error } = useActiveJStream<MetricsUpdateEvent>(
        options.serverUrl,
        options.tenantId,
        '/eventcloud/metrics',
        {
            authToken: options.authToken,
            topics: [`metrics.${streamId}`],
            autoConnect: true,
        }
    );

    useActiveJSubscription<MetricsUpdateEvent>(
        subscribe,
        `metrics.${streamId}`,
        useCallback((message: ActiveJStreamMessage<MetricsUpdateEvent>) => {
            setMetrics(message.payload.metrics);
            if (message.payload.status) {
                setStatus(message.payload.status);
            }
            setIsLoading(false);
        }, []),
        [subscribe, streamId]
    );

    useEffect(() => {
        if (isConnected) {
            setIsLoading(true);
        }
    }, [isConnected, streamId]);

    return {
        metrics,
        status,
        isLoading,
        error,
    };
}
