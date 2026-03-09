/**
 * Simulator API client.
 */

import { EventSchema } from '@/features/simulator/components/EventSchemaForm';

/**
 * Simulator API client.
 *
 * <p><b>Purpose</b><br>
 * Provides event schema retrieval, event generation, and AI suggestion operations.
 *
 * <p><b>Methods</b><br>
 * - getEventSchemas: Get all available event schemas
 * - generateEvent: Generate test event
 * - getAISuggestions: Get AI-powered event suggestions
 * - validateEvent: Validate event against schema
 *
 * @doc.type service
 * @doc.purpose Simulator API operations
 * @doc.layer product
 * @doc.pattern API Client
 */
export const simulatorApi = {
    /**
     * Get all available event schemas.
     *
     * @param tenantId - Tenant ID
     * @returns Promise resolving to event schemas array
     */
    async getEventSchemas(tenantId: string): Promise<EventSchema[]> {
        try {
            const response = await fetch(`/api/v1/event-schemas?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[simulatorApi.getEventSchemas] API unavailable:', error);
            return [];
        }
    },

    /**
     * Generate test event.
     *
     * @param eventType - Event type name
     * @param payload - Event payload
     * @param tenantId - Tenant ID
     * @returns Promise resolving to generated event ID
     */
    async generateEvent(
        eventType: string,
        payload: Record<string, unknown>,
        tenantId: string
    ): Promise<{ eventId: string; processedAt: Date } | null> {
        try {
            const response = await fetch('/api/v1/simulator/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ eventType, payload, tenantId }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[simulatorApi.generateEvent] API unavailable:', error);
            return null;
        }
    },

    /**
     * Get AI-powered event suggestions.
     *
     * @param context - Context for suggestions (event type, department, etc.)
     * @param tenantId - Tenant ID
     * @returns Promise resolving to AI suggestions
     */
    async getAISuggestions(
        context: {
            eventType?: string;
            department?: string;
            recentEvents?: Record<string, unknown>[];
            count?: number;
        },
        tenantId: string
    ): Promise<
        Array<{
            id: string;
            title: string;
            description: string;
            eventData: Record<string, unknown>;
            confidence: number;
            category: string;
            explanation: string;
        }>
    > {
        try {
            const response = await fetch('/api/v1/simulator/suggestions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...context, tenantId }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[simulatorApi.getAISuggestions] API unavailable:', error);
            return [];
        }
    },

    /**
     * Validate event against schema.
     *
     * @param schemaId - Event schema ID
     * @param payload - Event payload to validate
     * @param tenantId - Tenant ID
     * @returns Promise resolving to validation result
     */
    async validateEvent(
        schemaId: string,
        payload: Record<string, unknown>,
        tenantId: string
    ): Promise<{ valid: boolean; errors?: string[] }> {
        try {
            const response = await fetch('/api/v1/simulator/validate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ schemaId, payload, tenantId }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[simulatorApi.validateEvent] API unavailable:', error);
            return { valid: false, errors: ['Validation service unavailable'] };
        }
    },
};

export default simulatorApi;
