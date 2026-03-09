/**
 * WebSocket Context Provider
 * 
 * Global WebSocket context for the application providing centralized WebSocket 
 * connection management, real-time data synchronization, and connection status
 * monitoring across all components.
 */

import {
    useWebSocket,
    useWebSocketStatus
} from '@ghatana/yappc-crdt/websocket';
import React, { createContext, useContext, useEffect, useState } from 'react';

import type {
    UseWebSocketReturn
} from '@ghatana/yappc-crdt/websocket';

/**
 *
 */
interface WebSocketContextValue extends UseWebSocketReturn {
    // Additional app-specific methods
    subscribeToBuildUpdates: (projectId: string) => () => void;
    subscribeToDeploymentUpdates: (projectId: string) => () => void;
    subscribeToMonitoringUpdates: (projectId: string) => () => void;
    sendBuildAction: (projectId: string, action: string, payload?: unknown) => boolean;
    sendDeploymentAction: (projectId: string, action: string, payload?: unknown) => boolean;
}

const WebSocketContext = createContext<WebSocketContextValue | undefined>(undefined);

/**
 *
 */
interface WebSocketProviderProps {
    children: React.ReactNode;
    wsUrl?: string;
    autoConnect?: boolean;
}

/**
 *
 */
export function WebSocketProvider({
    children,
    wsUrl = (import.meta.env.VITE_WEBSOCKET_URL as string) ?? 'ws://localhost:3001/ws',
    autoConnect = ((import.meta as unknown).env?.VITE_ENABLE_REAL_WS === 'string'
        ? (import.meta as unknown).env.VITE_ENABLE_REAL_WS === 'true'
        : false)
}: WebSocketProviderProps) {
    const webSocketReturn = useWebSocket({
        url: wsUrl,
        autoConnect,
        reconnect: true,
        maxReconnectAttempts: 5,
        protocols: ['yappc-v1']
    });

    const { send, subscribe } = webSocketReturn;

    // App-specific subscription methods
    const subscribeToBuildUpdates = (projectId: string) => {
        return subscribe(`build_update_${projectId}`, (message: unknown) => {
            console.log(`Build update for project ${projectId}:`, message.payload);

            // Dispatch custom event for global handling
            window.dispatchEvent(new CustomEvent('build-update', {
                detail: { projectId, ...message.payload }
            }));
        });
    };

    const subscribeToDeploymentUpdates = (projectId: string) => {
        return subscribe(`deployment_update_${projectId}`, (message: unknown) => {
            console.log(`Deployment update for project ${projectId}:`, message.payload);

            // Dispatch custom event for global handling
            window.dispatchEvent(new CustomEvent('deployment-update', {
                detail: { projectId, ...message.payload }
            }));
        });
    };

    const subscribeToMonitoringUpdates = (projectId: string) => {
        return subscribe(`monitoring_update_${projectId}`, (message: unknown) => {
            console.log(`Monitoring update for project ${projectId}:`, message.payload);

            // Dispatch custom event for global handling
            window.dispatchEvent(new CustomEvent('monitoring-update', {
                detail: { projectId, ...message.payload }
            }));
        });
    };

    const sendBuildAction = (projectId: string, action: string, payload?: unknown) => {
        return send({
            type: 'build_action',
            payload: {
                projectId,
                action,
                ...payload
            }
        });
    };

    const sendDeploymentAction = (projectId: string, action: string, payload?: unknown) => {
        return send({
            type: 'deployment_action',
            payload: {
                projectId,
                action,
                ...payload
            }
        });
    };

    const contextValue: WebSocketContextValue = {
        ...webSocketReturn,
        subscribeToBuildUpdates,
        subscribeToDeploymentUpdates,
        subscribeToMonitoringUpdates,
        sendBuildAction,
        sendDeploymentAction
    };

    return (
        <WebSocketContext.Provider value={contextValue}>
            {children}
            <ConnectionStatusNotification />
        </WebSocketContext.Provider>
    );
}

/**
 * Hook to access WebSocket context
 */
export function useWebSocketContext(): WebSocketContextValue {
    const context = useContext(WebSocketContext);
    if (context === undefined) {
        throw new Error('useWebSocketContext must be used within a WebSocketProvider');
    }
    return context;
}

/**
 * Connection status notification component
 */
function ConnectionStatusNotification() {
    const { showNotification, status, lastError, reconnectAttempt } = useWebSocketStatus();
    const [isVisible, setIsVisible] = useState(false);

    useEffect(() => {
        if (showNotification) {
            setIsVisible(true);

            // Auto-hide after 5 seconds for success messages
            if (status === 'connected') {
                const timer = setTimeout(() => setIsVisible(false), 5000);
                return () => clearTimeout(timer);
            }
        } else {
            setIsVisible(false);
        }
    }, [showNotification, status]);

    if (!isVisible) return null;

    const getNotificationContent = () => {
        switch (status) {
            case 'connecting':
                return {
                    type: 'info',
                    title: 'Connecting...',
                    message: 'Establishing real-time connection'
                };
            case 'connected':
                return {
                    type: 'success',
                    title: 'Connected',
                    message: 'Real-time updates are now active'
                };
            case 'reconnecting':
                return {
                    type: 'warning',
                    title: 'Reconnecting...',
                    message: `Attempting to reconnect (${reconnectAttempt}/5)`
                };
            case 'disconnected':
                return {
                    type: 'warning',
                    title: 'Disconnected',
                    message: 'Real-time updates are unavailable'
                };
            case 'failed':
                return {
                    type: 'error',
                    title: 'Connection Failed',
                    message: lastError?.message || 'Unable to establish real-time connection'
                };
            default:
                return null;
        }
    };

    const notification = getNotificationContent();
    if (!notification) return null;

    const getNotificationStyles = () => {
        const baseStyles = {
            position: 'fixed' as const,
            top: '1rem',
            right: '1rem',
            padding: '1rem',
            borderRadius: '8px',
            boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
            zIndex: 9999,
            minWidth: '320px',
            maxWidth: '400px',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            fontSize: '14px',
            fontWeight: '500',
            transition: 'all 0.3s ease',
            cursor: 'pointer'
        };

        switch (notification.type) {
            case 'success':
                return {
                    ...baseStyles,
                    backgroundColor: 'var(--success-color)',
                    color: 'white',
                    border: '1px solid var(--success-color)'
                };
            case 'warning':
                return {
                    ...baseStyles,
                    backgroundColor: 'var(--warning-color)',
                    color: 'white',
                    border: '1px solid var(--warning-color)'
                };
            case 'error':
                return {
                    ...baseStyles,
                    backgroundColor: 'var(--error-color)',
                    color: 'white',
                    border: '1px solid var(--error-color)'
                };
            case 'info':
            default:
                return {
                    ...baseStyles,
                    backgroundColor: 'var(--info-color)',
                    color: 'white',
                    border: '1px solid var(--info-color)'
                };
        }
    };

    const getStatusIcon = () => {
        switch (notification.type) {
            case 'success':
                return '✓';
            case 'warning':
                return '⚠';
            case 'error':
                return '✕';
            case 'info':
            default:
                return 'ℹ';
        }
    };

    return (
        <div
            style={getNotificationStyles()}
            onClick={() => setIsVisible(false)}
            role="alert"
            aria-live="polite"
        >
            <span style={{ fontSize: '16px', fontWeight: 'bold' }}>
                {getStatusIcon()}
            </span>
            <div>
                <div style={{ fontWeight: '600', marginBottom: '0.25rem' }}>
                    {notification.title}
                </div>
                <div style={{ opacity: 0.9, fontSize: '13px' }}>
                    {notification.message}
                </div>
            </div>
            <button
                onClick={(e) => {
                    e.stopPropagation();
                    setIsVisible(false);
                }}
                style={{
                    marginLeft: 'auto',
                    background: 'none',
                    border: 'none',
                    color: 'inherit',
                    cursor: 'pointer',
                    fontSize: '18px',
                    padding: '0.25rem',
                    borderRadius: '4px',
                    opacity: 0.7,
                    transition: 'opacity 0.2s ease'
                }}
                onMouseEnter={(e) => e.currentTarget.style.opacity = '1'}
                onMouseLeave={(e) => e.currentTarget.style.opacity = '0.7'}
                aria-label="Close notification"
            >
                ×
            </button>
        </div>
    );
}