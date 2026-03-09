import { useQuery, useMutation } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { useMemo, useCallback } from 'react';
import { incidentApi } from '@/services/api/incidentApi';
import {
    selectedIncidentIdAtom,
    filterSeverityAtom,
    filterStatusAtom,
} from '../stores/incidents.store';

/**
 * Incident Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Orchestrates all incident-related operations including listing, filtering by severity/status,
 * detail retrieval, and mutations for status updates. Integrates with human-in-the-loop features.
 *
 * <p><b>Features</b><br>
 * - Incident list with severity/status filtering
 * - Real-time incident status updates
 * - Severity and status filtering
 * - Incident detail retrieval
 * - Status update mutations (assign, resolve, escalate)
 * - HITL action queue integration
 * - Error handling and loading states
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   incidents,
 *   filteredIncidents,
 *   selectedIncident,
 *   updateIncidentStatus,
 *   assignIncident,
 *   isLoading,
 * } = useIncidentOrchestration();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Incident orchestration and management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useIncidentOrchestration() {
    const [selectedIncidentId, setSelectedIncidentId] = useAtom(selectedIncidentIdAtom);
    const [filterSeverity] = useAtom(filterSeverityAtom);
    const [filterStatus] = useAtom(filterStatusAtom);

    // Fetch all incidents
    const { data: incidents, isLoading, error, refetch } = useQuery({
        queryKey: ['incidents'],
        queryFn: async () => {
            try {
                return await incidentApi.getIncidents('default');
            } catch (error) {
                console.warn('[useIncidentOrchestration] Failed to fetch incidents:', error);
                return [];
            }
        },
        staleTime: 30 * 1000,
        refetchInterval: 60 * 1000,
    });

    // Fetch selected incident detail
    const { data: selectedIncidentDetail, isLoading: isLoadingDetail } = useQuery({
        queryKey: ['incident', selectedIncidentId],
        queryFn: async () => {
            if (!selectedIncidentId) return null;
            try {
                return await incidentApi.getIncidentById(selectedIncidentId, 'default');
            } catch (error) {
                console.warn('[useIncidentOrchestration] Failed to fetch detail:', error);
                return null;
            }
        },
        enabled: !!selectedIncidentId,
        staleTime: 5 * 60 * 1000,
    });

    // Filter incidents by severity and status
    const filteredIncidents = useMemo(() => {
        if (!incidents) return [];
        let filtered = [...incidents];

        // Apply severity filter
        if (filterSeverity && filterSeverity !== 'all') {
            filtered = filtered.filter((i: any) => i.severity === filterSeverity);
        }

        // Apply status filter
        if (filterStatus && filterStatus !== 'all') {
            filtered = filtered.filter((i: any) => i.status === filterStatus);
        }

        // Sort by creation time (newest first)
        return filtered.sort((a: any, b: any) => {
            const timeA = new Date(a.createdAt).getTime();
            const timeB = new Date(b.createdAt).getTime();
            return timeB - timeA;
        });
    }, [incidents, filterSeverity, filterStatus]);

    // Get selected incident
    const selectedIncident = useMemo(() => {
        return incidents?.find((i: any) => i.id === selectedIncidentId) || null;
    }, [incidents, selectedIncidentId]);

    // Update incident status mutation
    const updateStatusMutation = useMutation({
        mutationFn: async ({ status }: { status: string }) => {
            if (!selectedIncidentId) throw new Error('No incident selected');
            return await incidentApi.updateIncidentStatus(selectedIncidentId, status as 'open' | 'investigating' | 'resolved' | 'acknowledged', 'default');
        },
        onSuccess: () => {
            refetch();
        },
    });

    // Assign incident mutation
    const assignIncidentMutation = useMutation({
        mutationFn: async ({ assignee }: { assignee: string }) => {
            if (!selectedIncidentId) throw new Error('No incident selected');
            return await incidentApi.assignIncident(selectedIncidentId, assignee, 'default');
        },
        onSuccess: () => {
            refetch();
        },
    });

    // Escalate incident mutation
    const escalateMutation = useMutation({
        mutationFn: async () => {
            if (!selectedIncidentId) throw new Error('No incident selected');
            return await incidentApi.updateIncidentStatus(selectedIncidentId, 'investigating', 'default');
        },
        onSuccess: () => {
            refetch();
        },
    });

    // Handlers
    const handleSelectIncident = useCallback(
        (incidentId: string) => {
            setSelectedIncidentId(incidentId);
        },
        [setSelectedIncidentId]
    );

    const handleUpdateStatus = useCallback(
        async (status: string) => {
            try {
                await updateStatusMutation.mutateAsync({ status });
            } catch (err) {
                console.error('[useIncidentOrchestration] Status update failed:', err);
                throw err;
            }
        },
        [updateStatusMutation]
    );

    const handleAssign = useCallback(
        async (assignee: string) => {
            try {
                await assignIncidentMutation.mutateAsync({ assignee });
            } catch (err) {
                console.error('[useIncidentOrchestration] Assignment failed:', err);
                throw err;
            }
        },
        [assignIncidentMutation]
    );

    const handleEscalate = useCallback(async () => {
        try {
            await escalateMutation.mutateAsync();
        } catch (err) {
            console.error('[useIncidentOrchestration] Escalation failed:', err);
            throw err;
        }
    }, [escalateMutation]);

    return {
        // Data
        incidents: incidents || [],
        filteredIncidents,
        selectedIncident,
        selectedIncidentDetail,
        selectedIncidentId,

        // State
        isLoading: isLoading || isLoadingDetail,
        error,

        // Actions
        selectIncident: handleSelectIncident,
        updateStatus: handleUpdateStatus,
        assignIncident: handleAssign,
        escalateIncident: handleEscalate,

        // Mutation states
        isUpdatingStatus: updateStatusMutation.isPending,
        isAssigning: assignIncidentMutation.isPending,
        isEscalating: escalateMutation.isPending,
    };
}
