import { apiClient } from './index';

/**
 * Workflow and event data access layer (Day 4).
 *
 * <p><b>Purpose</b><br>
 * API methods for fetching workflow events, flow visualization data, and event inspection.
 * Supports real-time event streaming via WebSocket.
 *
 * <p><b>Endpoints</b><br>
 * - GET /events: Event stream with filtering
 * - GET /events/:id: Event details
 * - GET /workflows: Workflow/flow definitions
 * - WS /events/stream: Real-time event WebSocket
 *
 * @doc.type service
 * @doc.purpose Workflow and Event API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export interface WorkflowEvent {
    id: string;
    type: string;
    timestamp: string;
    departmentId: string;
    sourceAgent: string;
    targetAgent?: string;
    status: 'pending' | 'completed' | 'failed' | 'incident';
    payload: Record<string, any>;
    metadata?: Record<string, any>;
}

export interface WorkflowNode {
    id: string;
    departmentId: string;
    name: string;
    emoji: string;
    x: number;
    y: number;
    status: 'healthy' | 'warning' | 'critical';
    incidents: number;
}

export interface WorkflowEdge {
    id: string;
    source: string;
    target: string;
    eventCount: number;
    avgLatency: number;
    errorRate: number;
}

export const workflowsApi = {
    /**
     * Get event stream with optional filtering
     */
    async getEvents(params?: {
        timeRange?: string;
        department?: string;
        status?: string;
        limit?: number;
    }) {
        const response = await apiClient.get<WorkflowEvent[]>('/events', { params });
        return response.data;
    },

    /**
     * Get single event details
     */
    async getEventDetails(eventId: string) {
        const response = await apiClient.get<WorkflowEvent>(`/events/${eventId}`);
        return response.data;
    },

    /**
     * Get workflow visualization (nodes and edges)
     */
    async getWorkflowGraph(params?: { timeRange?: string; department?: string }) {
        const response = await apiClient.get<{
            nodes: WorkflowNode[];
            edges: WorkflowEdge[];
        }>('/workflows/graph', { params });
        return response.data;
    },

    /**
     * Get workflow node details (with historical metrics)
     */
    async getWorkflowNode(nodeId: string, timeRange: string = '7d') {
        const response = await apiClient.get<WorkflowNode>(`/workflows/${nodeId}`, {
            params: { timeRange },
        });
        return response.data;
    },

    /**
     * Subscribe to event stream (WebSocket)
     * Returns unsubscribe function
     */
    subscribeToEvents(
        onMessage: (event: WorkflowEvent) => void,
        onError?: (error: any) => void
    ): () => void {
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/events/stream`;

        const ws = new WebSocket(wsUrl);

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                onMessage(data);
            } catch (err) {
                onError?.(err);
            }
        };

        ws.onerror = (error) => {
            onError?.(error);
        };

        return () => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.close();
            }
        };
    },
};
