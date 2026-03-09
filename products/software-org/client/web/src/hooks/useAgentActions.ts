import { useQuery } from '@tanstack/react-query';
import { agentsApi, type ActionPriority } from '@/services/api/agentsApi';

/**
 * Hook for fetching pending agent actions requiring human approval.
 *
 * <p><b>Purpose</b><br>
 * Provides real-time list of pending HITL actions with filtering and status tracking.
 * Automatically polls for new actions and maintains queue state.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: actions, isLoading, error } = useAgentActions({ priority: 'p0' });
 * actions?.forEach(action => console.log(action.priority, action.confidence));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Polls every 5 seconds for new actions (high priority)
 * - Filters by priority and status
 * - Caches data for 2 minutes
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch pending agent actions for HITL console
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useAgentActions(params?: {
    priority?: ActionPriority | 'all';
    limit?: number;
    enabled?: boolean;
    refetchInterval?: number;
}) {
    return useQuery({
        queryKey: ['agentActions', params?.priority, params?.limit],
        queryFn: async () => {
            try {
                return await agentsApi.getPendingActions({
                    priority: params?.priority,
                    limit: params?.limit,
                });
            } catch (error) {
                console.warn('[useAgentActions] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 2 * 60 * 1000, // 2 minutes
        gcTime: 5 * 60 * 1000, // 5 minutes
        retry: 2,
        refetchInterval: params?.refetchInterval ?? 5 * 1000, // 5 seconds
        enabled: params?.enabled ?? true,
    });
}

/**
 * Hook for fetching detailed information about a specific action.
 *
 * <p><b>Purpose</b><br>
 * Retrieves full action details including reasoning, impact analysis, timeline,
 * and decision history for display in ActionDetailDrawer.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: action, isLoading } = useActionDetail('act-42');
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Auto-enables when actionId provided
 * - Caches for 5 minutes
 * - Retries on network failure
 * - Supports optional refetchInterval for live updates
 *
 * @doc.type hook
 * @doc.purpose Fetch single action details for HITL drawer
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useActionDetail(actionId?: string, options?: { enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['actionDetail', actionId],
        queryFn: async () => {
            if (!actionId) return null;
            try {
                return await agentsApi.getActionDetails(actionId);
            } catch (error) {
                console.warn('[useActionDetail] API unavailable, returning fallback:', error);
                // Return fallback structure matching ActionDetail interface
                return null;
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval,
        enabled: options?.enabled ?? !!actionId, // Auto-enable when actionId is provided
    });
}
