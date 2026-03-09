/**
 * Simulation Steps Hooks for Learning Paths
 *
 * React Query hooks for fetching and managing simulation-based learning steps.
 *
 * @doc.type module
 * @doc.purpose React hooks for simulation learning path integration
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// =============================================================================
// Local Types (avoiding external contract dependencies)
// =============================================================================

/**
 * Simulation domain types.
 */
export type SimulationDomain =
  | "CS_DISCRETE"
  | "PHYSICS"
  | "ECONOMICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ENGINEERING"
  | "MATHEMATICS";

/**
 * Difficulty levels for learning content.
 */
export type Difficulty = "INTRO" | "INTERMEDIATE" | "ADVANCED";

/**
 * Skill associated with a simulation step.
 */
export interface SimulationSkill {
  skillId: string;
  name: string;
  weight: number;
}

/**
 * Prerequisite for a simulation step.
 */
export interface SimulationPrerequisite {
  stepId: string;
  type: "required" | "recommended";
  minScore?: number;
}

/**
 * Reference to an assessment.
 */
export interface SimulationAssessmentRef {
  assessmentId: string;
  position: "pre" | "post" | "inline";
  required: boolean;
}

/**
 * Completion criteria for a step.
 */
export interface SimulationCompletionCriteria {
  minTimeSpentSeconds?: number;
  requiredSteps?: string[];
  minInteractions?: number;
  assessmentPassScore?: number;
}

/**
 * Metadata for a simulation step.
 */
export interface SimulationStepMetadata {
  title: string;
  description?: string;
  thumbnailUrl?: string;
  tags?: string[];
  keywords?: string[];
}

/**
 * A simulation step in a learning path.
 */
export interface SimulationLearningStep {
  id: string;
  type: "simulation";
  simulationId: string;
  manifestId: string;
  domain: SimulationDomain;
  difficulty: Difficulty;
  skills: SimulationSkill[];
  prerequisites: SimulationPrerequisite[];
  estimatedTimeMinutes: number;
  assessmentRefs: SimulationAssessmentRef[];
  learningObjectives?: string[];
  metadata: SimulationStepMetadata;
  completionCriteria?: SimulationCompletionCriteria;
}

/**
 * Progress status for a step.
 */
export type StepProgressStatus = "not_started" | "in_progress" | "completed" | "skipped";

/**
 * Progress on a learning step.
 */
export interface StepProgress {
  stepId: string;
  status: StepProgressStatus;
  score?: number;
  timeSpentSeconds: number;
  completedAt?: string;
  attempts?: number;
}

/**
 * A complete learning path.
 */
export interface LearningPath {
  id: string;
  tenantId: string;
  title: string;
  description?: string;
  domain: string;
  difficulty: Difficulty;
  steps: SimulationLearningStep[];
  totalDurationMinutes: number;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

/**
 * Diagnostic result for a skill.
 */
export interface DiagnosticResult {
  skillId: string;
  skillName: string;
  masteryLevel: number;
  confidenceScore: number;
}

/**
 * Constraints for learning path generation.
 */
export interface PathConstraints {
  maxDurationMinutes?: number;
  maxSteps?: number;
  preferredDomains?: SimulationDomain[];
  excludeSimulationIds?: string[];
  difficultyRange?: { min: Difficulty; max: Difficulty };
}

// =============================================================================
// Query Keys
// =============================================================================

export const simulationStepsKeys = {
  all: ["simulation-steps"] as const,
  list: (filters: SimulationStepsFilters) =>
    [...simulationStepsKeys.all, "list", filters] as const,
  detail: (stepId: string) =>
    [...simulationStepsKeys.all, "detail", stepId] as const,
  progress: (userId: string, stepId: string) =>
    [...simulationStepsKeys.all, "progress", userId, stepId] as const,
  plan: (goals: string[]) =>
    [...simulationStepsKeys.all, "plan", goals.join(",")] as const,
};

// =============================================================================
// Types
// =============================================================================

export interface SimulationStepsFilters {
  pathId?: string;
  domain?: SimulationDomain;
  status?: StepProgress["status"];
  limit?: number;
}

export interface SimulationStepsResponse {
  steps: Array<SimulationLearningStep & { progress?: StepProgress }>;
  nextCursor: string | null;
  totalCount: number;
}

export interface PlanFromDiagnosticInput {
  diagnosticResults: DiagnosticResult[];
  goals: string[];
  constraints?: PathConstraints;
}

export interface PlanFromDiagnosticResponse {
  path: LearningPath;
  simulationSteps: SimulationLearningStep[];
  estimatedDurationMinutes: number;
  confidence: number;
  rationale: string;
}

export interface UpdateProgressInput {
  stepId: string;
  status: StepProgress["status"];
  score?: number;
  timeSpentSeconds?: number;
}

// =============================================================================
// API Client Extensions
// =============================================================================

// Extend the API client with simulation steps methods
const simulationStepsApi = {
  /**
   * Fetch simulation steps for the current user.
   */
  async getSimulationSteps(
    userId: string,
    filters: SimulationStepsFilters
  ): Promise<SimulationStepsResponse> {
    const params = new URLSearchParams();
    if (filters.pathId) params.append("pathId", filters.pathId);
    if (filters.domain) params.append("domain", filters.domain);
    if (filters.status) params.append("status", filters.status);
    if (filters.limit) params.append("limit", filters.limit.toString());

    const response = await fetch(
      `/api/learning-path/simulation-steps/${userId}?${params.toString()}`,
      {
        headers: {
          "Content-Type": "application/json",
        },
      }
    );

    if (!response.ok) {
      throw new Error("Failed to fetch simulation steps");
    }

    return response.json();
  },

  /**
   * Get a single simulation step by ID.
   */
  async getSimulationStep(stepId: string): Promise<SimulationLearningStep> {
    const response = await fetch(`/api/learning-path/simulation-step/${stepId}`, {
      headers: {
        "Content-Type": "application/json",
      },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch simulation step");
    }

    return response.json();
  },

  /**
   * Plan a learning path from diagnostic results.
   */
  async planFromDiagnostic(
    input: PlanFromDiagnosticInput
  ): Promise<PlanFromDiagnosticResponse> {
    const response = await fetch("/api/learning-path/plan-from-diagnostic", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });

    if (!response.ok) {
      throw new Error("Failed to plan learning path");
    }

    return response.json();
  },

  /**
   * Update progress on a simulation step.
   */
  async updateStepProgress(input: UpdateProgressInput): Promise<StepProgress> {
    const response = await fetch(
      `/api/learning-path/simulation-step/${input.stepId}/progress`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          status: input.status,
          score: input.score,
          timeSpentSeconds: input.timeSpentSeconds,
        }),
      }
    );

    if (!response.ok) {
      throw new Error("Failed to update step progress");
    }

    return response.json();
  },
};

// =============================================================================
// Hooks
// =============================================================================

/**
 * Hook to fetch simulation steps for the current user.
 *
 * @param userId - The user ID to fetch steps for
 * @param filters - Optional filters for the query
 * @returns Query result with simulation steps
 */
export function useSimulationSteps(
  userId: string,
  filters: SimulationStepsFilters = {}
) {
  return useQuery({
    queryKey: simulationStepsKeys.list(filters),
    queryFn: () => simulationStepsApi.getSimulationSteps(userId, filters),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch a single simulation step by ID.
 *
 * @param stepId - The step ID to fetch
 * @returns Query result with the simulation step
 */
export function useSimulationStep(stepId: string) {
  return useQuery({
    queryKey: simulationStepsKeys.detail(stepId),
    queryFn: () => simulationStepsApi.getSimulationStep(stepId),
    enabled: !!stepId,
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
}

/**
 * Hook to plan a learning path from diagnostic results.
 *
 * @returns Mutation for planning a learning path
 */
export function usePlanFromDiagnostic() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: simulationStepsApi.planFromDiagnostic,
    onSuccess: (data) => {
      // Invalidate simulation steps queries to reflect new path
      queryClient.invalidateQueries({
        queryKey: simulationStepsKeys.all,
      });

      // Cache the plan result
      queryClient.setQueryData(
        simulationStepsKeys.plan(data.path.title.split(": ")[1]?.split(", ") ?? []),
        data
      );
    },
  });
}

/**
 * Hook to update progress on a simulation step.
 *
 * @returns Mutation for updating step progress
 */
export function useUpdateSimulationProgress() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: simulationStepsApi.updateStepProgress,
    onSuccess: (data, variables) => {
      // Update the specific step's progress in cache
      queryClient.setQueryData(
        simulationStepsKeys.detail(variables.stepId),
        (old: SimulationLearningStep | undefined) => {
          if (!old) return old;
          return {
            ...old,
            progress: data,
          };
        }
      );

      // Invalidate list queries to reflect updated progress
      queryClient.invalidateQueries({
        queryKey: simulationStepsKeys.all,
        exact: false,
      });
    },
    // Optimistic update
    onMutate: async (variables) => {
      // Cancel outgoing queries
      await queryClient.cancelQueries({
        queryKey: simulationStepsKeys.detail(variables.stepId),
      });

      // Snapshot previous value
      const previousStep = queryClient.getQueryData<SimulationLearningStep>(
        simulationStepsKeys.detail(variables.stepId)
      );

      // Optimistically update
      if (previousStep) {
        queryClient.setQueryData(simulationStepsKeys.detail(variables.stepId), {
          ...previousStep,
          progress: {
            stepId: variables.stepId,
            status: variables.status,
            score: variables.score,
            timeSpentSeconds: variables.timeSpentSeconds ?? 0,
          },
        });
      }

      return { previousStep };
    },
    onError: (_err, variables, context) => {
      // Rollback on error
      if (context?.previousStep) {
        queryClient.setQueryData(
          simulationStepsKeys.detail(variables.stepId),
          context.previousStep
        );
      }
    },
  });
}

/**
 * Hook to mark a simulation step as started.
 *
 * @returns Mutation for starting a step
 */
export function useStartSimulationStep() {
  const updateProgress = useUpdateSimulationProgress();

  return {
    ...updateProgress,
    mutate: (stepId: string) => {
      updateProgress.mutate({
        stepId,
        status: "in_progress",
      });
    },
    mutateAsync: (stepId: string) => {
      return updateProgress.mutateAsync({
        stepId,
        status: "in_progress",
      });
    },
  };
}

/**
 * Hook to mark a simulation step as completed.
 *
 * @returns Mutation for completing a step
 */
export function useCompleteSimulationStep() {
  const updateProgress = useUpdateSimulationProgress();

  return {
    ...updateProgress,
    mutate: (params: { stepId: string; score?: number; timeSpentSeconds?: number }) => {
      updateProgress.mutate({
        stepId: params.stepId,
        status: "completed",
        score: params.score,
        timeSpentSeconds: params.timeSpentSeconds,
      });
    },
    mutateAsync: (params: { stepId: string; score?: number; timeSpentSeconds?: number }) => {
      return updateProgress.mutateAsync({
        stepId: params.stepId,
        status: "completed",
        score: params.score,
        timeSpentSeconds: params.timeSpentSeconds,
      });
    },
  };
}

/**
 * Hook to get simulation steps by domain.
 *
 * @param domain - The simulation domain to filter by
 * @returns Query result with simulation steps for the domain
 */
export function useSimulationStepsByDomain(
  userId: string,
  domain: SimulationDomain
) {
  return useSimulationSteps(userId, { domain });
}

/**
 * Hook to get in-progress simulation steps.
 *
 * @param userId - The user ID to fetch in-progress steps for
 * @returns Query result with in-progress simulation steps
 */
export function useInProgressSimulationSteps(userId: string) {
  return useSimulationSteps(userId, { status: "in_progress" });
}

/**
 * Hook to get completed simulation steps.
 *
 * @param userId - The user ID to fetch completed steps for
 * @returns Query result with completed simulation steps
 */
export function useCompletedSimulationSteps(userId: string) {
  return useSimulationSteps(userId, { status: "completed" });
}
