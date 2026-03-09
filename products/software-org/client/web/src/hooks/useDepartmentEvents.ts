import { useEffect, useState, useCallback } from 'react';
import { useAtom } from 'jotai';
import { departmentEventsAtom } from '../state/orgState';

export interface DepartmentEvent {
    id: string;
    type: string;
    department: string;
    timestamp: string;
    payload: Record<string, unknown>;
    tenantId: string;
    correlationId: string;
}

/**
 * Hook for subscribing to real-time department events via EventCloud tail.
 * 
 * Features:
 * - WebSocket connection with automatic reconnection
 * - Fallback to polling if WebSocket unavailable
 * - Event filtering by department
 * - Automatic cleanup on unmount
 */
export function useDepartmentEvents(department?: string, startFrom?: 'now' | 'latest') {
    const [events, setEvents] = useAtom(departmentEventsAtom);
    const [isConnected, setIsConnected] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const handleNewEvent = useCallback((event: DepartmentEvent) => {
        if (!department || event.department === department) {
            setEvents((prevEvents) => [event, ...prevEvents].slice(0, 100)); // Keep last 100
        }
    }, [department, setEvents]);

    useEffect(() => {
        let ws: WebSocket | null = null;
        let reconnectTimeout: NodeJS.Timeout;
        let pollInterval: NodeJS.Timeout;

        const connectWebSocket = () => {
            try {
                const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                const eventFilter = department ? `?department=${encodeURIComponent(department)}` : '';
                ws = new WebSocket(`${protocol}//${window.location.host}/api/v1/events/tail${eventFilter}`);

                ws.onopen = () => {
                    setIsConnected(true);
                    setError(null);
                };

                ws.onmessage = (event) => {
                    try {
                        const data = JSON.parse(event.data);
                        handleNewEvent(data);
                    } catch (err) {
                        console.error('Failed to parse event:', err);
                    }
                };

                ws.onerror = (event) => {
                    console.error('WebSocket error:', event);
                    setError(new Error('WebSocket connection failed'));
                    setIsConnected(false);
                };

                ws.onclose = () => {
                    setIsConnected(false);
                    // Reconnect after 3 seconds
                    reconnectTimeout = setTimeout(() => connectWebSocket(), 3000);
                };
            } catch (err) {
                console.error('WebSocket connection error:', err);
                setError(err instanceof Error ? err : new Error('Unknown error'));
                // Fallback to polling
                startPolling();
            }
        };

        const startPolling = () => {
            pollInterval = setInterval(async () => {
                try {
                    const eventFilter = department ? `?department=${encodeURIComponent(department)}` : '';
                    const response = await fetch(`/api/v1/events/recent${eventFilter}`, {
                        headers: {
                            'X-Tenant-Id': localStorage.getItem('tenantId') || 'default',
                        },
                    });
                    if (response.ok) {
                        const data = await response.json();
                        if (Array.isArray(data)) {
                            data.forEach(handleNewEvent);
                        }
                    }
                } catch (err) {
                    console.error('Polling error:', err);
                }
            }, 5000); // Poll every 5 seconds
        };

        connectWebSocket();

        return () => {
            if (ws) {
                ws.close();
            }
            clearTimeout(reconnectTimeout);
            clearInterval(pollInterval);
        };
    }, [department, handleNewEvent]);

    return { events, isConnected, error };
}
