import { apiClient } from './index';

/**
 * Event Simulator API (Day 6).
 *
 * <p><b>Purpose</b><br>
 * API methods for simulating events, validating payloads, and running dry-run tests.
 *
 * <p><b>Endpoints</b><br>
 * - GET /schemas: Event schema definitions
 * - POST /events/validate: Validate event payload
 * - POST /events/dry-run: Simulate event without persisting
 * - POST /events/submit: Submit real event
 *
 * @doc.type service
 * @doc.purpose Event Simulator API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export interface EventSchema {
    type: string;
    description: string;
    fields: Array<{
        name: string;
        type: 'string' | 'number' | 'boolean' | 'datetime' | 'json';
        required: boolean;
        description?: string;
        default?: any;
    }>;
    examples?: Array<{ name: string; payload: Record<string, any> }>;
}

export interface EventValidationResponse {
    valid: boolean;
    errors?: string[];
    warnings?: string[];
}

export interface DryRunResponse {
    success: boolean;
    processed: boolean;
    incidents?: string[];
    affectedServices?: string[];
    executionTimeMs: number;
    message?: string;
}

export interface EventSubmitResponse {
    eventId: string;
    timestamp: string;
    status: 'accepted' | 'processing' | 'error';
    message?: string;
}

export const eventsApi = {
    /**
     * Get event schema definitions
     */
    async getSchemas() {
        const response = await apiClient.get<EventSchema[]>('/schemas');
        return response.data;
    },

    /**
     * Get single event schema by type
     */
    async getSchema(schemaType: string) {
        const response = await apiClient.get<EventSchema>(`/schemas/${schemaType}`);
        return response.data;
    },

    /**
     * Validate event payload against schema
     */
    async validateEvent(schemaType: string, payload: Record<string, any>) {
        const response = await apiClient.post<EventValidationResponse>('/events/validate', {
            schemaType,
            payload,
        });
        return response.data;
    },

    /**
     * Run event through pipelines without persisting (dry-run)
     */
    async dryRunEvent(schemaType: string, payload: Record<string, any>) {
        const response = await apiClient.post<DryRunResponse>('/events/dry-run', {
            schemaType,
            payload,
        });
        return response.data;
    },

    /**
     * Submit event to pipelines for processing
     */
    async submitEvent(schemaType: string, payload: Record<string, any>) {
        const response = await apiClient.post<EventSubmitResponse>('/events/submit', {
            schemaType,
            payload,
        });
        return response.data;
    },

    /**
     * Get recent submitted events
     */
    async getRecentEvents(limit: number = 10) {
        const response = await apiClient.get<Array<{
            eventId: string;
            type: string;
            timestamp: string;
            status: string;
        }>>('/events/recent', { params: { limit } });
        return response.data;
    },
};
