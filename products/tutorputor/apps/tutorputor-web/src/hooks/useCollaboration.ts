import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

/**
 * Hook to list discussion threads.
 */
export function useThreads(moduleId?: string) {
    return useQuery({
        queryKey: ["threads", moduleId],
        queryFn: () => apiClient.listThreads(moduleId)
    });
}

/**
 * Hook to get a specific thread with posts.
 */
export function useThread(threadId: string) {
    return useQuery({
        queryKey: ["thread", threadId],
        queryFn: () => apiClient.getThread(threadId),
        enabled: !!threadId
    });
}

/**
 * Hook to create a new thread.
 */
export function useCreateThread() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (data: { moduleId: string; title: string; content: string }) =>
            apiClient.createThread(data),
        onSuccess: (_, { moduleId }) => {
            queryClient.invalidateQueries({ queryKey: ["threads", moduleId] });
        }
    });
}

/**
 * Hook to create a post reply.
 */
export function useCreatePost() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ threadId, content, parentId }: { threadId: string; content: string; parentId?: string }) =>
            apiClient.createPost(threadId, content, parentId),
        onSuccess: (_, { threadId }) => {
            queryClient.invalidateQueries({ queryKey: ["thread", threadId] });
        }
    });
}

/**
 * Hook to vote on a post.
 */
export function useVoteOnPost() {
    // Note: We'd need threadId context for proper cache invalidation
    return useMutation({
        mutationFn: ({ postId, vote }: { postId: string; vote: "up" | "down" }) =>
            apiClient.voteOnPost(postId, vote),
        onSuccess: () => {
            // Invalidate current thread - we'd need threadId context here
            // For now, just mark as success
        }
    });
}
