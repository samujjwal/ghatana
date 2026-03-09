import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

/**
 * Hook to recommend a learning path based on goals.
 */
export function useRecommendPath() {
    return useMutation({
        mutationFn: ({ goals, currentSkills }: { goals: string[]; currentSkills?: string[] }) =>
            apiClient.recommendPath(goals, currentSkills)
    });
}

/**
 * Hook to enroll in a learning path.
 */
export function useEnrollInPath() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (pathId: string) => apiClient.enrollInPath(pathId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["pathEnrollments"] });
        }
    });
}

/**
 * Hook to get a specific path enrollment.
 */
export function usePathEnrollment(pathId: string) {
    return useQuery({
        queryKey: ["pathEnrollment", pathId],
        queryFn: () => apiClient.getPathEnrollment(pathId),
        enabled: !!pathId
    });
}

/**
 * Hook to list all path enrollments.
 */
export function usePathEnrollments() {
    return useQuery({
        queryKey: ["pathEnrollments"],
        queryFn: () => apiClient.listPathEnrollments()
    });
}

/**
 * Hook to update path progress.
 */
export function useUpdatePathProgress() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ pathId, nodeId, status }: { pathId: string; nodeId: string; status: string }) =>
            apiClient.updatePathProgress(pathId, nodeId, status),
        onSuccess: (_, { pathId }) => {
            queryClient.invalidateQueries({ queryKey: ["pathEnrollment", pathId] });
        }
    });
}
