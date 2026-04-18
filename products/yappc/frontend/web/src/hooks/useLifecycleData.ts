/**
 * Lifecycle React Query Hooks
 * 
 * TanStack Query hooks for all lifecycle data operations.
 * Provides caching, loading states, and optimistic updates.
 * 
 * @doc.type hooks
 * @doc.purpose Lifecycle data hooks
 * @doc.layer product
 * @doc.pattern React Query Hooks
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';
import {
    lifecycleAPI,
    Artifact,
    Evidence,
    Task,
    GateStatus,
    AIRecommendation,
    AuditEvent,
    ReadinessAnomalyAlert,
    LifecycleAutomationPlan,
    LifecycleAutomationPlanRequest,
} from '@/services/lifecycle/api';
import { FOWStage, ArtifactType } from '@/types/fow-stages';
import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// Query Keys
// ============================================================================

export const lifecycleKeys = {
    all: ['lifecycle'] as const,
    artifacts: (projectId: string) => [...lifecycleKeys.all, 'artifacts', projectId] as const,
    artifact: (artifactId: string) => [...lifecycleKeys.all, 'artifact', artifactId] as const,
    artifactsByType: (projectId: string, type: ArtifactType) => [...lifecycleKeys.artifacts(projectId), 'type', type] as const,
    artifactsByStage: (projectId: string, stage: FOWStage) => [...lifecycleKeys.artifacts(projectId), 'stage', stage] as const,
    evidence: (projectId: string) => [...lifecycleKeys.all, 'evidence', projectId] as const,
    evidenceByType: (projectId: string, type: string) => [...lifecycleKeys.evidence(projectId), 'type', type] as const,
    gate: (projectId: string, stage: FOWStage) => [...lifecycleKeys.all, 'gate', projectId, stage] as const,
    nextTask: (projectId: string, phase: LifecyclePhase, persona?: string) => [...lifecycleKeys.all, 'next-task', projectId, phase, persona] as const,
    aiRecommendations: (projectId: string, phase: LifecyclePhase, fowStage: FOWStage) => [...lifecycleKeys.all, 'ai-recommendations', projectId, phase, fowStage] as const,
    aiInsights: (projectId: string) => [...lifecycleKeys.all, 'ai-insights', projectId] as const,
    readinessAnomalies: (projectId: string) => [...lifecycleKeys.all, 'readiness-anomalies', projectId] as const,
    audit: (projectId: string) => [...lifecycleKeys.all, 'audit', projectId] as const,
    derivedPersona: (projectId: string, phase: LifecyclePhase, fowStage: FOWStage) => [...lifecycleKeys.all, 'persona', projectId, phase, fowStage] as const,
    automationPlan: (projectId: string, phase: LifecyclePhase) => [...lifecycleKeys.all, 'automation-plan', projectId, phase] as const,
};

// ============================================================================
// Artifact Hooks
// ============================================================================

export function useArtifacts(projectId: string, options?: UseQueryOptions<Artifact[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.artifacts(projectId),
        queryFn: () => lifecycleAPI.artifacts.getArtifacts(projectId),
        enabled: !!projectId,
        staleTime: 30000, // 30 seconds
        ...options,
    });
}

export function useArtifact(artifactId: string, options?: UseQueryOptions<Artifact, Error>) {
    return useQuery({
        queryKey: lifecycleKeys.artifact(artifactId),
        queryFn: () => lifecycleAPI.artifacts.getArtifact(artifactId),
        enabled: !!artifactId,
        ...options,
    });
}

export function useArtifactsByType(projectId: string, type: ArtifactType, options?: UseQueryOptions<Artifact[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.artifactsByType(projectId, type),
        queryFn: () => lifecycleAPI.artifacts.getArtifactsByType(projectId, type),
        enabled: !!projectId && !!type,
        ...options,
    });
}

export function useArtifactsByStage(projectId: string, stage: FOWStage, options?: UseQueryOptions<Artifact[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.artifactsByStage(projectId, stage),
        queryFn: () => lifecycleAPI.artifacts.getArtifactsByStage(projectId, stage),
        enabled: !!projectId && stage !== undefined,
        ...options,
    });
}

export function useCreateArtifact(options?: UseMutationOptions<Artifact, Error, Omit<Artifact, 'id' | 'createdAt' | 'updatedAt' | 'version'>>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: lifecycleAPI.artifacts.createArtifact,
        onMutate: async (newArtifact) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.artifacts(newArtifact.projectId) });
            const previousArtifacts = queryClient.getQueryData(lifecycleKeys.artifacts(newArtifact.projectId));

            const tempArtifact: Artifact = {
                ...newArtifact,
                id: 'temp-' + Date.now(),
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
                version: 1,
            };

            queryClient.setQueryData(lifecycleKeys.artifacts(newArtifact.projectId), (old: Artifact[] = []) => [...old, tempArtifact]);

            return { previousArtifacts, tempArtifact };
        },
        onError: (err, _, context) => {
            queryClient.setQueryData(lifecycleKeys.artifacts(context?.tempArtifact.projectId || ''), context?.previousArtifacts);
        },
        onSettled: (data) => {
            if (data) {
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(data.projectId) });
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.evidence(data.projectId) });
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.gate(data.projectId, data.fowStage) });
            }
        },
        ...options,
    });
}

export function useUpdateArtifact(options?: UseMutationOptions<Artifact, Error, { artifactId: string; data: Partial<Artifact> }>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ artifactId, data }) => lifecycleAPI.artifacts.updateArtifact(artifactId, data),
        onMutate: async ({ artifactId, data }) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.artifact(artifactId) });
            const previousArtifact = queryClient.getQueryData(lifecycleKeys.artifact(artifactId));

            queryClient.setQueryData(lifecycleKeys.artifact(artifactId), (old: Artifact) => ({
                ...old,
                ...data,
                updatedAt: new Date().toISOString(),
            }));

            return { previousArtifact };
        },
        onError: (err, variables, context) => {
            queryClient.setQueryData(lifecycleKeys.artifact(variables.artifactId), context?.previousArtifact);
        },
        onSettled: (data, variables) => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifact(variables.artifactId) });
            if (data) {
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(data.projectId) });
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.gate(data.projectId, data.fowStage) });
            }
        },
        ...options,
    });
}

export function useDeleteArtifact(options?: UseMutationOptions<void, Error, { artifactId: string; projectId: string }>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ artifactId }) => lifecycleAPI.artifacts.deleteArtifact(artifactId),
        onMutate: async ({ artifactId, projectId }) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.artifacts(projectId) });
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.artifact(artifactId) });
            const previousArtifacts = queryClient.getQueryData(lifecycleKeys.artifacts(projectId));
            const previousArtifact = queryClient.getQueryData(lifecycleKeys.artifact(artifactId));

            queryClient.setQueryData(lifecycleKeys.artifacts(projectId), (old: Artifact[] = []) =>
                old.filter(a => a.id !== artifactId)
            );

            return { previousArtifacts, previousArtifact };
        },
        onError: (err, variables, context) => {
            queryClient.setQueryData(lifecycleKeys.artifacts(variables.projectId), context?.previousArtifacts);
            queryClient.setQueryData(lifecycleKeys.artifact(variables.artifactId), context?.previousArtifact);
        },
        onSettled: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(variables.projectId) });
            queryClient.removeQueries({ queryKey: lifecycleKeys.artifact(variables.artifactId) });
        },
        ...options,
    });
}

// ============================================================================
// Evidence Hooks
// ============================================================================

export function useEvidence(projectId: string, options?: UseQueryOptions<Evidence[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.evidence(projectId),
        queryFn: () => lifecycleAPI.evidence.getEvidence(projectId),
        enabled: !!projectId,
        staleTime: 30000,
        ...options,
    });
}

export function useEvidenceByType(projectId: string, type: Evidence['type'], options?: UseQueryOptions<Evidence[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.evidenceByType(projectId, type),
        queryFn: () => lifecycleAPI.evidence.getEvidenceByType(projectId, type),
        enabled: !!projectId && !!type,
        ...options,
    });
}

export function useCreateEvidence(options?: UseMutationOptions<Evidence, Error, Omit<Evidence, 'id'>>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: lifecycleAPI.evidence.createEvidence,
        onMutate: async (newEvidence) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.evidence(newEvidence.projectId) });
            const previousEvidence = queryClient.getQueryData(lifecycleKeys.evidence(newEvidence.projectId));

            const tempEvidence: Evidence = {
                ...newEvidence,
                id: 'temp-' + Date.now(),
                timestamp: new Date().toISOString(),
            };

            queryClient.setQueryData(lifecycleKeys.evidence(newEvidence.projectId), (old: Evidence[] = []) => [...old, tempEvidence]);

            return { previousEvidence, tempEvidence };
        },
        onError: (err, _, context) => {
            queryClient.setQueryData(lifecycleKeys.evidence(context?.tempEvidence.projectId || ''), context?.previousEvidence);
        },
        onSettled: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.evidence(variables.projectId) });
        },
        ...options,
    });
}

// ============================================================================
// Gate Hooks
// ============================================================================

export function useGateStatus(projectId: string, stage?: FOWStage, options?: UseQueryOptions<GateStatus, Error>) {
    return useQuery({
        queryKey: lifecycleKeys.gate(projectId, stage as FOWStage),
        queryFn: () => lifecycleAPI.gates.checkGate(projectId, stage as FOWStage),
        enabled: !!projectId && stage !== undefined,
        staleTime: 10000, // 10 seconds - gates should be fresh
        refetchInterval: 30000, // Refetch every 30 seconds
        ...options,
    });
}

export function useTransitionStage(options?: UseMutationOptions<{ success: boolean; currentStage: FOWStage }, Error, { projectId: string; targetStage: FOWStage }>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ projectId, targetStage }) => lifecycleAPI.gates.transitionStage(projectId, targetStage),
        onMutate: async ({ projectId, targetStage }) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.all });
            const previousGates = queryClient.getQueryData(lifecycleKeys.all);

            queryClient.setQueryData(lifecycleKeys.gate(projectId, targetStage), (old: GateStatus) => ({
                ...old,
                status: 'IN_PROGRESS',
            }));

            return { previousGates };
        },
        onError: (err, _, context) => {
            queryClient.setQueryData(lifecycleKeys.all, context?.previousGates);
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.all });
        },
        ...options,
    });
}

// ============================================================================
// Task Hooks
// ============================================================================

export function useNextBestTask(projectId: string, phase?: LifecyclePhase, persona?: string, options?: UseQueryOptions<Task, Error>) {
    return useQuery({
        queryKey: lifecycleKeys.nextTask(projectId, phase as LifecyclePhase, persona),
        queryFn: () => lifecycleAPI.tasks.getNextBestTask(projectId, phase as LifecyclePhase, persona),
        enabled: !!projectId && phase !== undefined,
        staleTime: 60000, // 1 minute
        ...options,
    });
}

export function useExecuteTask(options?: UseMutationOptions<unknown, Error, { taskId: string; input: Record<string, unknown> }>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ taskId, input }) => lifecycleAPI.tasks.executeTask(taskId, input),
        onMutate: async ({ taskId }) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.all });
            const previousData = queryClient.getQueryData(lifecycleKeys.all);

            return { previousData };
        },
        onError: (err, _, context) => {
            queryClient.setQueryData(lifecycleKeys.all, context?.previousData);
        },
        onSettled: (data) => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.all });
            if (data && typeof data === 'object' && 'artifacts' in data && Array.isArray(data.artifacts) && data.artifacts.length > 0) {
                const projectId = (data.artifacts[0] as { projectId?: string }).projectId;
                if (projectId) {
                    queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(projectId) });
                    queryClient.invalidateQueries({ queryKey: lifecycleKeys.evidence(projectId) });
                }
            }
        },
        ...options,
    });
}

// ============================================================================
// AI Hooks
// ============================================================================

export function useAIRecommendations(
    projectId: string,
    context: {
        phase: LifecyclePhase;
        fowStage: FOWStage;
        persona?: string;
        recentActivity?: string[];
    },
    options?: UseQueryOptions<AIRecommendation[], Error>
) {
    return useQuery({
        queryKey: lifecycleKeys.aiRecommendations(projectId, context.phase, context.fowStage),
        queryFn: () => lifecycleAPI.ai.getRecommendations(projectId, context),
        enabled: !!projectId && context.phase !== undefined && context.fowStage !== undefined,
        staleTime: 60000,
        ...options,
    });
}

export function useAIInsights(projectId: string, options?: UseQueryOptions<Evidence[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.aiInsights(projectId),
        queryFn: () => lifecycleAPI.ai.getInsights(projectId),
        enabled: !!projectId,
        staleTime: 300000, // 5 minutes
        ...options,
    });
}

export function useReadinessAnomalies(projectId: string, options?: UseQueryOptions<ReadinessAnomalyAlert[], Error>) {
    return useQuery({
        queryKey: lifecycleKeys.readinessAnomalies(projectId),
        queryFn: () => lifecycleAPI.devSecOps.getAnomalyAlerts(),
        enabled: !!projectId,
        staleTime: 60000,
        ...options,
    });
}

export function useValidateArtifact(options?: UseMutationOptions<unknown, Error, string>) {
    return useMutation({
        mutationFn: (artifactId: string) => lifecycleAPI.ai.validateArtifact(artifactId),
        ...options,
    });
}

// ============================================================================
// Audit Hooks
// ============================================================================

export function useAuditEvents(
    projectId: string,
    filters?: {
        fowStage?: FOWStage;
        phase?: LifecyclePhase;
        startDate?: Date;
        endDate?: Date;
    },
    options?: UseQueryOptions<AuditEvent[], Error>
) {
    return useQuery({
        queryKey: [...lifecycleKeys.audit(projectId), filters],
        queryFn: () => lifecycleAPI.audit.getAuditEvents(projectId, filters),
        staleTime: 30000,
        ...options,
    });
}

export function useEmitAuditEvent(options?: UseMutationOptions<AuditEvent, Error, Omit<AuditEvent, 'id' | 'timestamp'>>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: lifecycleAPI.audit.emitEvent,
        onMutate: async (newEvent) => {
            await queryClient.cancelQueries({ queryKey: lifecycleKeys.audit(newEvent.projectId) });
            const previousAudit = queryClient.getQueryData(lifecycleKeys.audit(newEvent.projectId));

            const tempEvent: AuditEvent = {
                ...newEvent,
                id: 'temp-' + Date.now(),
                timestamp: new Date().toISOString(),
            };

            queryClient.setQueryData(lifecycleKeys.audit(newEvent.projectId), (old: AuditEvent[] = []) => [...old, tempEvent]);

            return { previousAudit, tempEvent };
        },
        onError: (err, _, context) => {
            queryClient.setQueryData(lifecycleKeys.audit(context?.tempEvent.projectId || ''), context?.previousAudit);
        },
        onSettled: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.audit(variables.projectId) });
        },
        ...options,
    });
}

// ============================================================================
// Persona Hooks
// ============================================================================

export function useDerivedPersona(
    context: {
        projectId: string;
        phase: LifecyclePhase;
        fowStage: FOWStage;
        recentTasks?: string[];
        recentArtifacts?: ArtifactType[];
    },
    options?: UseQueryOptions<{ persona: string; confidence: number; reasoning: string }, Error>
) {
    return useQuery({
        queryKey: lifecycleKeys.derivedPersona(context.projectId, context.phase, context.fowStage),
        queryFn: () => lifecycleAPI.personas.derivePersona(context),
        staleTime: 120000, // 2 minutes
        ...options,
    });
}

// ============================================================================
// Automation Hooks
// ============================================================================

export function useLifecycleAutomationPlan(
    projectId: string,
    phase: LifecyclePhase,
    options?: UseQueryOptions<LifecycleAutomationPlan, Error>
) {
    return useQuery({
        queryKey: lifecycleKeys.automationPlan(projectId, phase),
        queryFn: () => lifecycleAPI.automation.buildPlan(projectId, { phase }),
        enabled: !!projectId && phase !== undefined,
        staleTime: 30000,
        ...options,
    });
}

export function useApplyLifecycleAutomationPlan(
    options?: UseMutationOptions<LifecycleAutomationPlan, Error, { projectId: string; request: LifecycleAutomationPlanRequest }>
) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ projectId, request }) => lifecycleAPI.automation.buildPlan(projectId, request),
        onSettled: (_, __, variables) => {
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.all });
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.automationPlan(variables.projectId, (variables.request.phase ?? 'INTENT') as LifecyclePhase) });
        },
        ...options,
    });
}

// ============================================================================
// Composite Hooks (multiple queries)
// ============================================================================

/**
 * Get complete lifecycle context for a project
 */
export function useLifecycleContext(projectId: string, fowStage: FOWStage, phase: LifecyclePhase) {
    const artifacts = useArtifacts(projectId);
    const evidence = useEvidence(projectId);
    const gateStatus = useGateStatus(projectId, fowStage);
    const nextTask = useNextBestTask(projectId, phase);
    const persona = useDerivedPersona({ projectId, phase, fowStage });

    return {
        artifacts,
        evidence,
        gateStatus,
        nextTask,
        persona,
        isLoading: artifacts.isLoading || evidence.isLoading || gateStatus.isLoading,
        isError: artifacts.isError || evidence.isError || gateStatus.isError,
    };
}
