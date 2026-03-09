/**
 * Approvals API Hooks
 *
 * @doc.type module
 * @doc.purpose React Query hooks for approval workflow API
 * @doc.layer presentation
 * @doc.pattern Custom Hook
 */

import { useMutation } from '@tanstack/react-query';

interface DelegateApprovalParams {
    approvalId: string;
    fromUserId: string;
    toUserId: string;
    reason?: string;
}

export function useDelegateApproval() {
    return useMutation({
        mutationFn: async ({ approvalId, fromUserId, toUserId, reason }: DelegateApprovalParams) => {
            const response = await fetch(`/api/v1/approvals/${approvalId}/delegate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    fromUserId,
                    toUserId,
                    reason,
                }),
            });
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to delegate approval');
            }
            return response.json();
        },
    });
}
