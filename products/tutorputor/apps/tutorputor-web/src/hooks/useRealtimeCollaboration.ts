/**
 * Real-time Collaboration Hook for TutorPutor
 * 
 * Provides real-time updates for threads and discussions.
 * Currently uses polling fallback. WebSocket integration via @ghatana/realtime
 * will be enabled when the package is properly linked.
 * 
 * @doc.type hook
 * @doc.purpose Real-time thread/post updates in collaboration features
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

// Local type definitions (these exist in contracts but may not be linked properly)
type ModuleId = string;
type ThreadId = string;

interface RealtimeCollaborationOptions {
    moduleId?: ModuleId;
    threadId?: ThreadId;
    enabled?: boolean;
}

interface UseRealtimeCollaborationReturn {
    isConnected: boolean;
    isConnecting: boolean;
    activeUsers: number;
    subscribeToThread: (threadId: ThreadId) => void;
    unsubscribeFromThread: (threadId: ThreadId) => void;
    sendTypingIndicator: (threadId: ThreadId, isTyping: boolean) => void;
}

/**
 * Hook for real-time collaboration features.
 * 
 * Currently uses polling as a fallback until WebSocket integration
 * is properly configured. The hook provides the same API as the
 * full WebSocket implementation.
 */
export function useRealtimeCollaboration(
    options: RealtimeCollaborationOptions = {}
): UseRealtimeCollaborationReturn {
    const { moduleId, enabled = true } = options;
    const queryClient = useQueryClient();
    const [activeUsers] = useState(1); // Stub - would come from WebSocket

    // Polling fallback for real-time updates
    useEffect(() => {
        if (!enabled || !moduleId) return;

        // Poll for thread updates every 30 seconds
        const intervalId = setInterval(() => {
            queryClient.invalidateQueries({ queryKey: ["threads", moduleId] });
        }, 30000);

        return () => clearInterval(intervalId);
    }, [enabled, moduleId, queryClient]);

    // Stub implementations - will be replaced with WebSocket calls
    const subscribeToThread = useCallback((_threadId: ThreadId) => {
        // WebSocket: send({ type: "thread:join", data: { threadId } })
    }, []);

    const unsubscribeFromThread = useCallback((_threadId: ThreadId) => {
        // WebSocket: send({ type: "thread:leave", data: { threadId } })
    }, []);

    const sendTypingIndicator = useCallback((_threadId: ThreadId, _isTyping: boolean) => {
        // WebSocket: send({ type: "thread:typing", data: { threadId, isTyping } })
    }, []);

    return {
        isConnected: true, // Polling is always "connected"
        isConnecting: false,
        activeUsers,
        subscribeToThread,
        unsubscribeFromThread,
        sendTypingIndicator
    };
}

/**
 * Hook to listen for typing indicators in a thread.
 * Stub implementation - returns empty array until WebSocket is enabled.
 */
export function useTypingIndicators(_threadId?: ThreadId) {
    const [typingUsers] = useState<string[]>([]);
    return { typingUsers };
}

export default useRealtimeCollaboration;
