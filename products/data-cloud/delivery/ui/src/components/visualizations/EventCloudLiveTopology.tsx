/**
 * EventCloud Live Topology Component
 *
 * A real-time streaming version of EventCloudTopology that connects to
 * the ActiveJ backend for live metrics and topology updates.
 *
 * @doc.type component
 * @doc.purpose Live EventCloud topology visualization
 * @doc.layer product
 * @doc.pattern SmartComponent
 */

import React, { useCallback, useEffect, useMemo } from 'react';
import { EventCloudTopology, type EventCloudTopologyProps } from './EventCloudTopology';
import { useEventCloudStream, type UseEventCloudStreamOptions } from '../../hooks';
import { cn, cardStyles, textStyles, bgStyles } from '../../lib/theme';

// ============================================
// TYPES
// ============================================

export interface EventCloudLiveTopologyProps
    extends Omit<EventCloudTopologyProps, 'nodes' | 'edges' | 'isLoading' | 'error'> {
    /** ActiveJ server URL for streaming */
    serverUrl: string;

    /** Tenant ID for multi-tenant isolation */
    tenantId: string;

    /** Authentication token */
    authToken?: string;

    /** Whether to show connection status */
    showConnectionStatus?: boolean;

    /** Callback when connection state changes */
    onConnectionStateChange?: (state: 'disconnected' | 'connecting' | 'connected' | 'error') => void;

    /** Callback when sync completes */
    onSyncComplete?: () => void;

    /** Fallback UI when disconnected */
    disconnectedFallback?: React.ReactNode;
}

// ============================================
// CONNECTION STATUS COMPONENT
// ============================================

interface ConnectionStatusProps {
    state: 'disconnected' | 'connecting' | 'connected' | 'error';
    lastUpdate: number | null;
    onReconnect: () => void;
    error?: Error | null;
}

function ConnectionStatus({ state, lastUpdate, onReconnect, error }: ConnectionStatusProps) {
    const stateConfig = {
        disconnected: {
            color: 'text-gray-500',
            bg: 'bg-gray-100 dark:bg-gray-800',
            icon: '○',
            label: 'Disconnected',
        },
        connecting: {
            color: 'text-yellow-500',
            bg: 'bg-yellow-50 dark:bg-yellow-900/20',
            icon: '◐',
            label: 'Connecting...',
        },
        connected: {
            color: 'text-green-500',
            bg: 'bg-green-50 dark:bg-green-900/20',
            icon: '●',
            label: 'Live',
        },
        error: {
            color: 'text-red-500',
            bg: 'bg-red-50 dark:bg-red-900/20',
            icon: '✕',
            label: 'Error',
        },
    };

    const config = stateConfig[state];

    const formatLastUpdate = (timestamp: number | null): string => {
        if (!timestamp) return 'Never';
        const seconds = Math.floor((Date.now() - timestamp) / 1000);
        if (seconds < 5) return 'Just now';
        if (seconds < 60) return `${seconds}s ago`;
        if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
        return `${Math.floor(seconds / 3600)}h ago`;
    };

    return (
        <div
            className={cn(
                'flex items-center gap-2 px-3 py-1.5 rounded-full',
                config.bg,
                cardStyles.base
            )}
            role="status"
            aria-live="polite"
        >
            <span className={cn('text-sm', config.color, state === 'connecting' && 'animate-pulse')}>
                {config.icon}
            </span>
            <span className={cn('text-xs font-medium', config.color)}>{config.label}</span>

            {state === 'connected' && lastUpdate && (
                <span className="text-xs text-gray-400 dark:text-gray-500">
                    • Updated {formatLastUpdate(lastUpdate)}
                </span>
            )}

            {(state === 'disconnected' || state === 'error') && (
                <button
                    onClick={onReconnect}
                    className={cn(
                        'ml-2 text-xs font-medium',
                        'text-blue-500 hover:text-blue-600 dark:text-blue-400 dark:hover:text-blue-300',
                        'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2'
                    )}
                    aria-label="Reconnect to stream"
                >
                    Reconnect
                </button>
            )}

            {error && state === 'error' && (
                <span className="text-xs text-red-400" title={error.message}>
                    ({error.message.slice(0, 30)}...)
                </span>
            )}
        </div>
    );
}

// ============================================
// MAIN COMPONENT
// ============================================

/**
 * EventCloudLiveTopology - Real-time streaming topology visualization.
 *
 * Wraps EventCloudTopology with live data streaming from ActiveJ backend.
 * Automatically connects, handles reconnection, and provides connection status UI.
 *
 * @example
 * ```tsx
 * function StreamMonitor() {
 *   return (
 *     <EventCloudLiveTopology
 *       serverUrl="ws://localhost:8080"
 *       tenantId="tenant-123"
 *       authToken={token}
 *       showConnectionStatus
 *       onNodeClick={(node) => console.log('Selected:', node)}
 *     />
 *   );
 * }
 * ```
 */
export function EventCloudLiveTopology({
    serverUrl,
    tenantId,
    authToken,
    showConnectionStatus = true,
    onConnectionStateChange,
    onSyncComplete,
    disconnectedFallback,
    className,
    ...topologyProps
}: EventCloudLiveTopologyProps) {
    // Connect to streaming hook
    const {
        nodes,
        edges,
        connectionState,
        error,
        connect,
        disconnect,
        requestSync,
        lastUpdate,
        isSyncing,
    } = useEventCloudStream({
        serverUrl,
        tenantId,
        authToken,
        autoConnect: true,
    });

    // Notify parent of connection state changes
    useEffect(() => {
        onConnectionStateChange?.(connectionState);
    }, [connectionState, onConnectionStateChange]);

    // Notify parent when sync completes
    useEffect(() => {
        if (!isSyncing && nodes.length > 0) {
            onSyncComplete?.();
        }
    }, [isSyncing, nodes.length, onSyncComplete]);

    // Handle reconnection
    const handleReconnect = useCallback(async () => {
        try {
            await connect();
        } catch (err) {
            console.error('Failed to reconnect:', err);
        }
    }, [connect]);

    // Cleanup on unmount
    useEffect(() => {
        return () => {
            disconnect();
        };
    }, [disconnect]);

    // Show fallback if disconnected and no cached data
    if (connectionState === 'disconnected' && nodes.length === 0 && disconnectedFallback) {
        return <>{disconnectedFallback}</>;
    }

    return (
        <div className={cn('relative w-full h-full', className)}>
            {/* Connection Status Overlay */}
            {showConnectionStatus && (
                <div className="absolute top-4 right-4 z-10">
                    <ConnectionStatus
                        state={connectionState}
                        lastUpdate={lastUpdate}
                        onReconnect={handleReconnect}
                        error={error}
                    />
                </div>
            )}

            {/* Sync Indicator */}
            {isSyncing && (
                <div className="absolute inset-0 bg-white/50 dark:bg-gray-900/50 flex items-center justify-center z-20">
                    <div className={cn('flex items-center gap-3', cardStyles.base, 'px-6 py-4')}>
                        <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                        <span className={textStyles.muted}>Syncing topology...</span>
                    </div>
                </div>
            )}

            {/* Main Topology */}
            <EventCloudTopology
                nodes={nodes}
                edges={edges}
                isLoading={connectionState === 'connecting' && nodes.length === 0}
                error={error}
                {...topologyProps}
            />
        </div>
    );
}

export default EventCloudLiveTopology;
