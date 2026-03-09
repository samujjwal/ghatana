/**
 * useUnifiedPersona Hook
 *
 * <p><b>Purpose</b><br>
 * React Query hooks for the unified persona model (human/agent agnostic).
 * Provides data fetching, caching, and state management for persona data,
 * work items, growth goals, and execution context.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useCurrentPersona, usePersonaWorkItems, useExecutionContext } from '@/hooks/useUnifiedPersona';
 *
 * function Dashboard() {
 *   const { data: persona, isLoading } = useCurrentPersona();
 *   const { data: workItems } = usePersonaWorkItems();
 *   // ...
 * }
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose React Query hooks for unified persona model
 * @doc.layer product
 * @doc.pattern Data Fetching Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom, useSetAtom } from 'jotai';
import { api } from '@/services/personaApi';
import {
    currentPersonaAtom,
    personaAvailabilityStatusAtom,
    workSessionAtom,
    personaGrowthGoalsAtom,
    personaPlannedAbsencesAtom,
    activeExecutionContextAtom,
    personaWorkItemsAtom,
    currentDevSecOpsPhaseAtom,
    type WorkSession,
    type PersonaWorkItem,
} from '@/state/jotai/atoms';
import type {
    PlannedAbsence,
    DevSecOpsPhaseId,
    PersonaAvailabilityStatus,
} from '@/shared/types/org';

// ============================================================================
// QUERY KEYS
// ============================================================================

export const personaQueryKeys = {
    all: ['persona'] as const,
    current: () => [...personaQueryKeys.all, 'current'] as const,
    workItems: (phase?: DevSecOpsPhaseId) => [...personaQueryKeys.all, 'work-items', phase] as const,
    growthGoals: () => [...personaQueryKeys.all, 'growth-goals'] as const,
    absences: () => [...personaQueryKeys.all, 'absences'] as const,
    executionContext: (workItemId: string) => [...personaQueryKeys.all, 'execution-context', workItemId] as const,
};

// ============================================================================
// CURRENT PERSONA HOOK
// ============================================================================

/**
 * Hook to fetch and manage the current persona entity.
 * Syncs with Jotai atom for global state access.
 */
export function useCurrentPersona() {
    const [persona, setPersona] = useAtom(currentPersonaAtom);
    const setAvailabilityStatus = useSetAtom(personaAvailabilityStatusAtom);

    const query = useQuery({
        queryKey: personaQueryKeys.current(),
        queryFn: () => api.getCurrentPersona(),
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 30 * 60 * 1000, // 30 minutes (formerly cacheTime)
    });

    // Sync with Jotai atom when data changes
    if (query.data && query.data !== persona) {
        setPersona(query.data);
        setAvailabilityStatus(query.data.availability.status);
    }

    return {
        ...query,
        persona: query.data ?? persona,
    };
}

// ============================================================================
// WORK ITEMS HOOK
// ============================================================================

/**
 * Hook to fetch persona work items with optional phase filter.
 * Syncs with Jotai atom for global state access.
 */
export function usePersonaWorkItems(phase?: DevSecOpsPhaseId) {
    const [workItems, setWorkItems] = useAtom(personaWorkItemsAtom);

    const query = useQuery({
        queryKey: personaQueryKeys.workItems(phase),
        queryFn: () => api.getPersonaWorkItems(phase),
        staleTime: 2 * 60 * 1000, // 2 minutes
    });

    // Sync with Jotai atom when data changes
    if (query.data && JSON.stringify(query.data) !== JSON.stringify(workItems)) {
        setWorkItems(query.data as PersonaWorkItem[]);
    }

    return {
        ...query,
        workItems: query.data ?? workItems,
    };
}

// ============================================================================
// WORK SESSION HOOKS
// ============================================================================

/**
 * Hook to manage work sessions (start/end).
 */
export function useWorkSession() {
    const [session, setSession] = useAtom(workSessionAtom);
    const setCurrentPhase = useSetAtom(currentDevSecOpsPhaseAtom);
    const queryClient = useQueryClient();

    const startMutation = useMutation({
        mutationFn: (workItemId?: string) => api.startWorkSession(workItemId),
        onSuccess: (data: WorkSession) => {
            setSession(data);
            if (data.currentPhase) {
                setCurrentPhase(data.currentPhase);
            }
        },
    });

    const endMutation = useMutation({
        mutationFn: (sessionId: string) => api.endWorkSession(sessionId),
        onSuccess: () => {
            setSession(null);
            setCurrentPhase(null);
            // Invalidate work items to refresh metrics
            queryClient.invalidateQueries({ queryKey: personaQueryKeys.workItems() });
        },
    });

    return {
        session,
        isActive: session !== null,
        startSession: startMutation.mutate,
        endSession: endMutation.mutate,
        isStarting: startMutation.isPending,
        isEnding: endMutation.isPending,
    };
}

// ============================================================================
// GROWTH GOALS HOOKS
// ============================================================================

/**
 * Hook to fetch and manage growth goals.
 */
export function useGrowthGoals() {
    const [goals, setGoals] = useAtom(personaGrowthGoalsAtom);
    const queryClient = useQueryClient();

    const query = useQuery({
        queryKey: personaQueryKeys.growthGoals(),
        queryFn: () => api.getGrowthGoals(),
        staleTime: 10 * 60 * 1000, // 10 minutes
    });

    // Sync with Jotai atom
    if (query.data && JSON.stringify(query.data) !== JSON.stringify(goals)) {
        setGoals(query.data);
    }

    const updateProgressMutation = useMutation({
        mutationFn: ({ goalId, progress }: { goalId: string; progress: number }) =>
            api.updateGrowthGoalProgress(goalId, progress),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: personaQueryKeys.growthGoals() });
        },
    });

    return {
        ...query,
        goals: query.data ?? goals,
        updateProgress: updateProgressMutation.mutate,
        isUpdating: updateProgressMutation.isPending,
    };
}

// ============================================================================
// PLANNED ABSENCES HOOKS
// ============================================================================

/**
 * Hook to fetch and manage planned absences (PTO for humans, maintenance for agents).
 */
export function usePlannedAbsences() {
    const [absences, setAbsences] = useAtom(personaPlannedAbsencesAtom);
    const queryClient = useQueryClient();

    const query = useQuery({
        queryKey: personaQueryKeys.absences(),
        queryFn: () => api.getPlannedAbsences(),
        staleTime: 10 * 60 * 1000, // 10 minutes
    });

    // Sync with Jotai atom
    if (query.data && JSON.stringify(query.data) !== JSON.stringify(absences)) {
        setAbsences(query.data);
    }

    const requestAbsenceMutation = useMutation({
        mutationFn: (absence: Omit<PlannedAbsence, 'id' | 'status'>) => api.requestAbsence(absence),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: personaQueryKeys.absences() });
        },
    });

    return {
        ...query,
        absences: query.data ?? absences,
        requestAbsence: requestAbsenceMutation.mutate,
        isRequesting: requestAbsenceMutation.isPending,
    };
}

// ============================================================================
// EXECUTION CONTEXT HOOK
// ============================================================================

/**
 * Hook to fetch execution context for a work item.
 * Provides all tools and integrations needed to complete the work.
 */
export function useExecutionContext(workItemId: string | null) {
    const [context, setContext] = useAtom(activeExecutionContextAtom);

    const query = useQuery({
        queryKey: personaQueryKeys.executionContext(workItemId ?? ''),
        queryFn: () => api.getExecutionContext(workItemId!),
        enabled: !!workItemId,
        staleTime: 1 * 60 * 1000, // 1 minute
    });

    // Sync with Jotai atom
    if (query.data && query.data !== context) {
        setContext(query.data);
    }

    return {
        ...query,
        context: query.data ?? context,
    };
}

// ============================================================================
// AVAILABILITY STATUS HOOK
// ============================================================================

/**
 * Hook to update persona availability status.
 */
export function useAvailabilityStatus() {
    const [status, setStatus] = useAtom(personaAvailabilityStatusAtom);
    const queryClient = useQueryClient();

    const updateMutation = useMutation({
        mutationFn: ({ status, message }: { status: PersonaAvailabilityStatus; message?: string }) =>
            api.updateAvailabilityStatus(status, message),
        onSuccess: (_, variables) => {
            setStatus(variables.status);
            queryClient.invalidateQueries({ queryKey: personaQueryKeys.current() });
        },
    });

    return {
        status,
        updateStatus: updateMutation.mutate,
        isUpdating: updateMutation.isPending,
    };
}

// ============================================================================
// COMBINED PERSONA DASHBOARD HOOK
// ============================================================================

/**
 * Combined hook for persona dashboard - fetches all necessary data.
 */
export function usePersonaDashboard() {
    const personaQuery = useCurrentPersona();
    const workItemsQuery = usePersonaWorkItems();
    const growthQuery = useGrowthGoals();
    const absencesQuery = usePlannedAbsences();
    const workSession = useWorkSession();

    const isLoading =
        personaQuery.isLoading ||
        workItemsQuery.isLoading ||
        growthQuery.isLoading ||
        absencesQuery.isLoading;

    const isError =
        personaQuery.isError ||
        workItemsQuery.isError ||
        growthQuery.isError ||
        absencesQuery.isError;

    return {
        persona: personaQuery.persona,
        workItems: workItemsQuery.workItems,
        goals: growthQuery.goals,
        absences: absencesQuery.absences,
        workSession,
        isLoading,
        isError,
        refetch: () => {
            personaQuery.refetch();
            workItemsQuery.refetch();
            growthQuery.refetch();
            absencesQuery.refetch();
        },
    };
}
