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
import { lifecycleAPI, Artifact, Evidence, Task, GateStatus, AIRecommendation, AuditEvent } from '@/services/lifecycle/api';
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
    audit: (projectId: string) => [...lifecycleKeys.all, 'audit', projectId] as const,
    derivedPersona: (projectId: string, phase: LifecyclePhase, fowStage: FOWStage) => [...lifecycleKeys.all, 'persona', projectId, phase, fowStage] as const,
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
        onSuccess: (data) => {
            // Invalidate relevant queries
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(data.projectId) });
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.evidence(data.projectId) });
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.gate(data.projectId, data.fowStage) });
        },
        ...options,
    });
}

export function useUpdateArtifact(options?: UseMutationOptions<Artifact, Error, { artifactId: string; data: Partial<Artifact> }>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ artifactId, data }) => lifecycleAPI.artifacts.updateArtifact(artifactId, data),
        onSuccess: (data, variables) => {
            // Update cache optimistically
            queryClient.setQueryData(lifecycleKeys.artifact(variables.artifactId), data);
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(data.projectId) });
            queryClient.invalidateQueries({ queryKey: lifecycleKeys.gate(data.projectId, data.fowStage) });
        },
        ...options,
    });
}

export function useDeleteArtifact(options?: UseMutationOptions<void, Error, { artifactId: string; projectId: string }>) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ artifactId }) => lifecycleAPI.artifacts.deleteArtifact(artifactId),
        onSuccess: (_, variables) => {
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
        onSuccess: (_, variables) => {
            // Invalidate evidence queries
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
        onSuccess: (_, variables) => {
            // Invalidate all gates and evidence
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
        onSuccess: (data) => {
            // Invalidate artifacts and evidence after task completion
            if (data.artifacts && data.artifacts.length > 0) {
                const projectId = data.artifacts[0].projectId;
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.artifacts(projectId) });
                queryClient.invalidateQueries({ queryKey: lifecycleKeys.evidence(projectId) });
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
        onSuccess: (_, variables) => {
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
