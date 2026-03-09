/**
 * DevSecOps Real-time Updates Hook
 *
 * @doc.type hook
 * @doc.purpose WebSocket subscription for DevSecOps stage updates
 * @doc.layer product
 * @doc.pattern Custom Hook
 *
 * Purpose:
 * Provides real-time updates for DevSecOps stages using WebSocket connection.
 * Manages stage health, work items, and timeline events with automatic reconnection.
 *
 * Features:
 * - Real-time stage health updates
 * - Live work item status changes
 * - Timeline event streaming
 * - Automatic reconnection on disconnect
 * - Type-safe event handling
 * - Integration with notification system
 *
 * Message Types:
 * - stage-health-update: Stage health metrics changed
 * - work-item-update: Work item status/priority changed
 * - timeline-event: New event occurred in stage
 * - incident-alert: Critical incident detected
 * - deployment-status: Deployment state changed
 */

import { useEffect, useCallback, useRef, useState } from 'react';
import { useDevSecOpsWebSocket } from '@/hooks/useDevSecOpsWebSocket';
import { useToast } from '@/lib/toast';
import type { StageHealth } from '@/types/devsecops';
import type { DevSecOpsItem } from '@/lib/devsecops/mapWorkItemToDevSecOpsItem';
import type { StageEvent } from '@/features/devsecops/mockStageEvents';

/**
 * DevSecOps update event types
 */
export type DevSecOpsEventType =
    | 'stage-health-update'
    | 'work-item-update'
    | 'timeline-event'
    | 'incident-alert'
    | 'deployment-status';

/**
 * Stage health update payload
 */
export interface StageHealthUpdate {
    stageKey: string;
    health: StageHealth;
    timestamp: string;
}

/**
 * Work item update payload
 */
export interface WorkItemUpdate {
    stageKey: string;
    item: DevSecOpsItem;
    changeType: 'created' | 'updated' | 'deleted' | 'status-changed' | 'priority-changed';
    timestamp: string;
}

/**
 * Timeline event update payload
 */
export interface TimelineEventUpdate {
    stageKey: string;
    event: StageEvent;
    timestamp: string;
}

/**
 * Incident alert payload
 */
export interface IncidentAlert {
    stageKey: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    title: string;
    message: string;
    affectedItems?: string[];
    timestamp: string;
}

/**
 * Deployment status payload
 */
export interface DeploymentStatus {
    stageKey: string;
    status: 'pending' | 'in-progress' | 'success' | 'failed' | 'rolled-back';
    environment: string;
    version?: string;
    timestamp: string;
}

/**
 * DevSecOps update event union type
 */
export type DevSecOpsUpdate =
    | { type: 'stage-health-update'; data: StageHealthUpdate }
    | { type: 'work-item-update'; data: WorkItemUpdate }
    | { type: 'timeline-event'; data: TimelineEventUpdate }
    | { type: 'incident-alert'; data: IncidentAlert }
    | { type: 'deployment-status'; data: DeploymentStatus };

/**
 * Hook options
 */
export interface UseDevSecOpsUpdatesOptions {
    /** Stage key to subscribe to (undefined = all stages) */
    stageKey?: string;
    /** Enable/disable notifications */
    notifications?: boolean;
    /** Callback for stage health updates */
    onHealthUpdate?: (data: StageHealthUpdate) => void;
    /** Callback for work item updates */
    onWorkItemUpdate?: (data: WorkItemUpdate) => void;
    /** Callback for timeline events */
    onTimelineEvent?: (data: TimelineEventUpdate) => void;
    /** Callback for incident alerts */
    onIncidentAlert?: (data: IncidentAlert) => void;
    /** Callback for deployment status */
    onDeploymentStatus?: (data: DeploymentStatus) => void;
}

/**
 * Hook return value
 */
export interface UseDevSecOpsUpdatesReturn {
    /** Is connected to WebSocket */
    isConnected: boolean;
    /** Is reconnecting after disconnect */
    isReconnecting: boolean;
    /** Last update timestamp */
    lastUpdate: Date | null;
    /** Connection error */
    error: Error | null;
    /** Manually refresh connection */
    reconnect: () => Promise<void>;
}

/**
 * Hook for DevSecOps real-time updates
 *
 * @example
 * ```tsx
 * function StageDashboard({ stageKey }) {
 *   const { isConnected, lastUpdate } = useDevSecOpsUpdates({
 *     stageKey,
 *     notifications: true,
 *     onHealthUpdate: (data) => {
 *       setStageHealth(data.health);
 *     },
 *     onIncidentAlert: (data) => {
 *       if (data.severity === 'critical') {
 *         // Show urgent alert
 *       }
 *     }
 *   });
 *
 *   return (
 *     <div>
 *       <ConnectionStatus connected={isConnected} lastUpdate={lastUpdate} />
 *       ...
 *     </div>
 *   );
 * }
 * ```
 */
export function useDevSecOpsUpdates(
    options: UseDevSecOpsUpdatesOptions = {}
): UseDevSecOpsUpdatesReturn {
    const {
        stageKey,
        notifications = true,
        onHealthUpdate,
        onWorkItemUpdate,
        onTimelineEvent,
        onIncidentAlert,
        onDeploymentStatus,
    } = options;

    const { showSuccess, showError, showWarning, showInfo } = useToast();
    const [lastUpdate, setLastUpdate] = useState<Date | null>(null);

    // Get API URL from environment
    const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:3101';

    // Use Socket.IO WebSocket hook
    const { items, isConnected, isConnecting, error, subscribe, unsubscribe } = useDevSecOpsWebSocket({
        apiUrl,
        stageKey,
        autoConnect: true,
    });

    // Track if component is mounted to prevent state updates after unmount
    const isMounted = useRef(true);
    useEffect(() => {
        return () => {
            isMounted.current = false;
        };
    }, []);

    // Subscribe to specific stage when stageKey changes
    useEffect(() => {
        if (stageKey && isConnected) {
            subscribe(stageKey);
        }
    }, [stageKey, isConnected, subscribe]);

    // Process items updates
    useEffect(() => {
        if (items.length > 0 && isMounted.current) {
            setLastUpdate(new Date());

            // Emit work item updates
            if (onWorkItemUpdate) {
                items.forEach((item) => {
                    onWorkItemUpdate({
                        stageKey: item.stageKey,
                        item: item as unknown as DevSecOpsItem,
                        changeType: 'updated',
                        timestamp: new Date().toISOString(),
                    });
                });
            }

            // Show success notification when items load
            if (notifications && items.length > 0) {
                showSuccess(`📊 Loaded ${items.length} items for ${stageKey || 'all stages'}`);
            }
        }
    }, [items, stageKey, notifications, onWorkItemUpdate, showSuccess]);

    // Handle connection error
    useEffect(() => {
        if (error && isMounted.current) {
            showError(`WebSocket connection error: ${error.message}`);
            console.error('[DevSecOps] Connection error:', error);
        }
    }, [error, showError]);

    return {
        isConnected,
        isReconnecting: isConnecting,
        lastUpdate,
        error,
        reconnect: async () => {
            // Reconnect is handled automatically by Socket.IO
            if (!isConnected) {
                console.log('[DevSecOps] Reconnecting...');
            }
        },
    };
}

/**
 * Hook for stage-specific updates
 *
 * Convenience wrapper for single-stage subscriptions
 */
export function useStageUpdates(
    stageKey: string,
    options: Omit<UseDevSecOpsUpdatesOptions, 'stageKey'> = {}
): UseDevSecOpsUpdatesReturn {
    return useDevSecOpsUpdates({ ...options, stageKey });
}

/**
 * Hook for all stages updates
 *
 * Convenience wrapper for global subscriptions
 */
export function useAllStagesUpdates(
    options: Omit<UseDevSecOpsUpdatesOptions, 'stageKey'> = {}
): UseDevSecOpsUpdatesReturn {
    return useDevSecOpsUpdates(options);
}
