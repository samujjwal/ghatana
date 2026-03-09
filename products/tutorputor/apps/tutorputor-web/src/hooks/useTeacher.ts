import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

/**
 * Hook to list all classrooms for a teacher.
 */
export function useClassrooms() {
    return useQuery({
        queryKey: ["classrooms"],
        queryFn: () => apiClient.listClassrooms()
    });
}

/**
 * Hook to get a specific classroom.
 */
export function useClassroom(classroomId: string) {
    return useQuery({
        queryKey: ["classroom", classroomId],
        queryFn: () => apiClient.getClassroom(classroomId),
        enabled: !!classroomId
    });
}

/**
 * Hook to create a new classroom.
 */
export function useCreateClassroom() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (data: { name: string; description?: string }) =>
            apiClient.createClassroom(data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["classrooms"] });
        }
    });
}

/**
 * Hook to get classroom student progress.
 */
export function useClassroomProgress(classroomId: string) {
    return useQuery({
        queryKey: ["classroomProgress", classroomId],
        queryFn: () => apiClient.getClassroomProgress(classroomId),
        enabled: !!classroomId
    });
}

/**
 * Hook to add a student to a classroom.
 */
export function useAddStudent() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ classroomId, studentId }: { classroomId: string; studentId: string }) =>
            apiClient.addStudentToClassroom(classroomId, studentId),
        onSuccess: (_, { classroomId }) => {
            queryClient.invalidateQueries({ queryKey: ["classroom", classroomId] });
            queryClient.invalidateQueries({ queryKey: ["classroomProgress", classroomId] });
        }
    });
}
