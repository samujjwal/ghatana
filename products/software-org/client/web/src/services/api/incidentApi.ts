/**
 * Incident API client.
 */

import { Incident } from '@/features/incidents/components/IncidentCard';

/**
 * Incident API client.
 *
 * <p><b>Purpose</b><br>
 * Provides incident data retrieval, assignment, and status management operations.
 *
 * <p><b>Methods</b><br>
 * - getIncidents: Get all incidents with filtering
 * - getIncidentById: Get incident details
 * - assignIncident: Assign incident to user
 * - updateIncidentStatus: Update incident status
 * - getIncidentTimeline: Get incident event timeline
 *
 * @doc.type service
 * @doc.purpose Incident API operations
 * @doc.layer product
 * @doc.pattern API Client
 */
export const incidentApi = {
    /**
     * Get all incidents.
     *
     * @param tenantId - Tenant ID
     * @param filters - Optional filters (severity, status, assignee)
     * @returns Promise resolving to incident array
     */
    async getIncidents(
        tenantId: string,
        filters?: {
            severity?: string;
            status?: string;
            assignedTo?: string;
        }
    ): Promise<Incident[]> {
        try {
            const params = new URLSearchParams({ tenantId });
            if (filters?.severity) params.append('severity', filters.severity);
            if (filters?.status) params.append('status', filters.status);
            if (filters?.assignedTo) params.append('assignedTo', filters.assignedTo);

            const response = await fetch(`/api/v1/incidents?${params.toString()}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[incidentApi.getIncidents] API unavailable:', error);
            return [];
        }
    },

    /**
     * Get incident by ID.
     *
     * @param incidentId - Incident ID
     * @param tenantId - Tenant ID
     * @returns Promise resolving to incident details
     */
    async getIncidentById(incidentId: string, tenantId: string): Promise<Incident | null> {
        try {
            const response = await fetch(`/api/v1/incidents/${incidentId}?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[incidentApi.getIncidentById] API unavailable:', error);
            return null;
        }
    },

    /**
     * Assign incident to user.
     *
     * @param incidentId - Incident ID
     * @param assignedTo - User ID to assign to
     * @param tenantId - Tenant ID
     * @returns Promise resolving to updated incident
     */
    async assignIncident(incidentId: string, assignedTo: string, tenantId: string): Promise<Incident | null> {
        try {
            const response = await fetch(`/api/v1/incidents/${incidentId}/assign`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ assignedTo, tenantId }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[incidentApi.assignIncident] API unavailable:', error);
            return null;
        }
    },

    /**
     * Update incident status.
     *
     * @param incidentId - Incident ID
     * @param status - New status
     * @param tenantId - Tenant ID
     * @returns Promise resolving to updated incident
     */
    async updateIncidentStatus(
        incidentId: string,
        status: 'open' | 'investigating' | 'resolved' | 'acknowledged',
        tenantId: string
    ): Promise<Incident | null> {
        try {
            const response = await fetch(`/api/v1/incidents/${incidentId}/status`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status, tenantId }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[incidentApi.updateIncidentStatus] API unavailable:', error);
            return null;
        }
    },

    /**
     * Get incident event timeline.
     *
     * @param incidentId - Incident ID
     * @param tenantId - Tenant ID
     * @returns Promise resolving to timeline events
     */
    async getIncidentTimeline(
        incidentId: string,
        tenantId: string
    ): Promise<
        Array<{
            id: string;
            timestamp: Date;
            eventType: string;
            actor?: string;
            description: string;
            details?: Record<string, unknown>;
        }>
    > {
        try {
            const response = await fetch(`/api/v1/incidents/${incidentId}/timeline?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[incidentApi.getIncidentTimeline] API unavailable:', error);
            return [];
        }
    },
};

export default incidentApi;
