import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

/**
 * Hook to get the active pathway for the current user.
 * Uses the adaptive learning model: diagnostic → learner profile → prerequisite graph → pathway
 */
export function useActivePathway() {
    return useQuery({
        queryKey: ["activePathway"],
        queryFn: () => apiClient.getActivePathway()
    });
}

/**
 * Hook to generate a personalized learning pathway based on a goal.
 * Integrates with learner profile for mastery-aware recommendations.
 */
export function useGeneratePathway() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ goal, constraints }: { goal: string; constraints?: { maxModules?: number; maxDurationMinutes?: number } }) =>
            apiClient.generatePathway(goal, constraints),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["activePathway"] });
        }
    });
}

/**
 * Hook to create a new active learning pathway.
 */
export function useCreatePathway() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ title, goal, moduleIds }: { title: string; goal: string; moduleIds: string[] }) =>
            apiClient.createPathway(title, goal, moduleIds),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["activePathway"] });
        }
    });
}

/**
 * Hook to advance the current pathway by marking a module as complete.
 * This triggers mastery updates and remediation recommendations in the adaptive learning model.
 */
export function useAdvancePathway() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ completedModuleId }: { completedModuleId: string }) =>
            apiClient.advancePathway(completedModuleId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["activePathway"] });
            queryClient.invalidateQueries({ queryKey: ["dashboard"] });
        }
    });
}

/**
 * Hook to get pathway recommendations based on user goals.
 */
export function useRecommendPath() {
    return useMutation({
        mutationFn: ({ goals }: { goals: string[] }) =>
            apiClient.generatePathway(goals.join(", ")),
        onSuccess: () => {
            // Invalidate relevant queries
        }
    });
}

/**
 * Hook to enroll in a learning pathway.
 */
export function useEnrollInPath() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ pathId }: { pathId: string }) =>
            apiClient.createPathway("", "", []),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["activePathway"] });
        }
    });
}

/**
 * Hook to get user's pathway enrollments.
 */
export function usePathEnrollments() {
    return useQuery({
        queryKey: ["pathEnrollments"],
        queryFn: () => apiClient.getActivePathway()
    });
}
