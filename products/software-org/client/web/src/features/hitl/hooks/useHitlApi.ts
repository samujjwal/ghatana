/**
 * Hook for HITL operations
 *
 * <p><b>Purpose</b><br>
 * TanStack Query hooks for Human-in-the-Loop (HITL) operations including
 * action submission, approval/rejection, and status tracking.
 *
 * <p><b>Features</b><br>
 * - Submit actions for approval
 * - Approve/reject actions
 * - Get action status
 * - List pending actions
 * - Real-time updates via polling
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { data: pending } = usePendingActions();
 * const { mutate: submit } = useSubmitAction();
 * const { mutate: approve } = useApproveAction();
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose HITL API operations
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { hitlApi, type HitlAction, type ApprovalRequest, type RejectionRequest } from "../../../services/api/hitlApi";

/**
 * Fetch pending actions
 */
export function usePendingActions(options: { enabled?: boolean; refetchInterval?: number } = {}) {
    const { enabled = true, refetchInterval = 5000 } = options;

    return useQuery({
        queryKey: ["hitl", "pending"],
        queryFn: () => hitlApi.listPendingActions(),
        staleTime: 1000 * 30, // 30 seconds
        gcTime: 1000 * 60 * 5,
        enabled,
        retry: 2,
        refetchInterval, // Poll every 5 seconds for real-time updates
    });
}

/**
 * Get action status
 */
export function useActionStatus(actionId: string, options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["hitl", "action", actionId],
        queryFn: () => hitlApi.getActionStatus(actionId),
        staleTime: 1000 * 10, // 10 seconds
        gcTime: 1000 * 60 * 5,
        enabled: enabled && !!actionId,
        retry: 2,
        refetchInterval: 3000, // Poll every 3 seconds
    });
}

/**
 * Submit an action for approval
 */
export function useSubmitAction() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (action: HitlAction) => hitlApi.submitAction(action),
        onSuccess: (result) => {
            // Invalidate pending actions if it requires approval
            if (result.requiresApproval) {
                queryClient.invalidateQueries({ queryKey: ["hitl", "pending"] });
            }
            // Add the new action to cache
            queryClient.setQueryData(["hitl", "action", result.actionId], {
                actionId: result.actionId,
                state: result.status,
                submittedAt: new Date().toISOString(),
            });
        },
    });
}

/**
 * Approve an action
 */
export function useApproveAction() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ actionId, request }: { actionId: string; request: ApprovalRequest }) =>
            hitlApi.approveAction(actionId, request),
        onSuccess: (_, variables) => {
            // Invalidate pending actions and specific action
            queryClient.invalidateQueries({ queryKey: ["hitl", "pending"] });
            queryClient.invalidateQueries({ queryKey: ["hitl", "action", variables.actionId] });
        },
    });
}

/**
 * Reject an action
 */
export function useRejectAction() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ actionId, request }: { actionId: string; request: RejectionRequest }) =>
            hitlApi.rejectAction(actionId, request),
        onSuccess: (_, variables) => {
            // Invalidate pending actions and specific action
            queryClient.invalidateQueries({ queryKey: ["hitl", "pending"] });
            queryClient.invalidateQueries({ queryKey: ["hitl", "action", variables.actionId] });
        },
    });
}

export default {
    usePendingActions,
    useActionStatus,
    useSubmitAction,
    useApproveAction,
    useRejectAction,
};
