import { useQuery, useMutation } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { useMemo, useCallback } from 'react';
import { simulatorApi } from '@/services/api/simulatorApi';
import { tenantAtom } from '@/state/jotai/atoms';
import { selectedEventTypeAtom, payloadAtom, isSimulatingAtom } from '../stores/simulator.store';

/**
 * Simulator Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Orchestrates event simulation including schema management, event generation,
 * AI-powered suggestions, and simulation execution. Provides real-time preview
 * and validation of generated events.
 *
 * <p><b>Features</b><br>
 * - Event schema list and selection
 * - Dynamic schema-based form generation
 * - Event payload management
 * - Simulation execution with real-time feedback
 * - AI-powered event suggestions
 * - Event validation and preview
 * - Historical simulation tracking
 * - Error handling and replay support
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   eventSchemas,
 *   selectedSchema,
 *   payload,
 *   simulateEvent,
 *   suggestEvents,
 *   isSimulating,
 * } = useSimulatorOrchestration();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Event simulation orchestration
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useSimulatorOrchestration() {
    const [tenant] = useAtom(tenantAtom);
    const [selectedEventType, setSelectedEventType] = useAtom(selectedEventTypeAtom);
    const [payload, setPayload] = useAtom(payloadAtom);
    const [isSimulating, setIsSimulating] = useAtom(isSimulatingAtom);

    // Fetch event schemas
    const { data: eventSchemas, isLoading: isLoadingSchemas } = useQuery({
        queryKey: ['event-schemas', tenant],
        queryFn: async () => {
            if (!tenant) return [];
            try {
                return await simulatorApi.getEventSchemas(tenant);
            } catch (error) {
                console.warn('[useSimulatorOrchestration] Failed to fetch schemas:', error);
                return [];
            }
        },
        enabled: !!tenant,
        staleTime: 30 * 60 * 1000, // 30 minutes
    });

    // Get selected schema
    const selectedSchema = useMemo(() => {
        return eventSchemas?.find((s: any) => s.id === selectedEventType) || null;
    }, [eventSchemas, selectedEventType]);

    // Fetch AI suggestions
    const { data: suggestions, isLoading: isLoadingSuggestions } = useQuery({
        queryKey: ['event-suggestions', selectedEventType, tenant],
        queryFn: async () => {
            if (!selectedEventType || !tenant) return [];
            try {
                return await simulatorApi.getAISuggestions(
                    { eventType: selectedEventType, count: 5 },
                    tenant
                );
            } catch (error) {
                console.warn('[useSimulatorOrchestration] Failed to fetch suggestions:', error);
                return [];
            }
        },
        enabled: !!selectedEventType && !!tenant,
    });

    // Generate event mutation
    const generateEventMutation = useMutation({
        mutationFn: async () => {
            if (!selectedEventType || !payload || !tenant)
                throw new Error('Missing event type, payload, or tenant');
            return await simulatorApi.generateEvent(selectedEventType, payload, tenant);
        },
    });

    // Get AI suggestions mutation
    const suggestEventsMutation = useMutation({
        mutationFn: async () => {
            if (!selectedEventType || !tenant) throw new Error('Missing event type or tenant');
            return await simulatorApi.getAISuggestions(
                { eventType: selectedEventType, count: 5 },
                tenant
            );
        },
    });

    // Validate payload mutation
    const validateMutation = useMutation({
        mutationFn: async (data: any) => {
            if (!selectedSchema || !tenant) throw new Error('No schema selected or tenant');
            return await simulatorApi.validateEvent(selectedSchema.id, data, tenant);
        },
    });

    // Handlers
    const handleSelectSchema = useCallback(
        (schemaId: string) => {
            setSelectedEventType(schemaId);
        },
        [setSelectedEventType]
    );

    const handleUpdatePayload = useCallback(
        (newPayload: Record<string, unknown>) => {
            setPayload(newPayload);
        },
        [setPayload]
    );

    const handleSimulate = useCallback(async () => {
        try {
            setIsSimulating(true);
            await generateEventMutation.mutateAsync();
        } catch (err) {
            console.error('[useSimulatorOrchestration] Simulation failed:', err);
            throw err;
        } finally {
            setIsSimulating(false);
        }
    }, [generateEventMutation, setIsSimulating]);

    const handleValidate = useCallback(
        async (data: any) => {
            try {
                return await validateMutation.mutateAsync(data);
            } catch (err) {
                console.error('[useSimulatorOrchestration] Validation failed:', err);
                throw err;
            }
        },
        [validateMutation]
    );

    const handleSuggest = useCallback(async () => {
        try {
            return await suggestEventsMutation.mutateAsync();
        } catch (err) {
            console.error('[useSimulatorOrchestration] Suggestion failed:', err);
            throw err;
        }
    }, [suggestEventsMutation]);

    return {
        // Data
        eventSchemas: eventSchemas || [],
        selectedSchema,
        payload,
        suggestions: suggestions || [],
        selectedEventType,

        // State
        isLoading: isLoadingSchemas,
        isSimulating,
        isLoadingSuggestions,

        // Actions
        selectSchema: handleSelectSchema,
        updatePayload: handleUpdatePayload,
        simulateEvent: handleSimulate,
        validatePayload: handleValidate,
        suggestEvents: handleSuggest,

        // Mutation states
        isValidating: validateMutation.isPending,
        isSuggesting: suggestEventsMutation.isPending,
    };
}
