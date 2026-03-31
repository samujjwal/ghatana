/**
 * useHitlQueue — combined TanStack Query + SSE hook for the HITL review queue.
 *
 * 1. Initial data from REST: `GET /api/v1/hitl/pending`
 * 2. Live updates via SSE: `hitl_request_created` events add items to the local cache without
 *    a full refetch.
 * 3. Approve/reject mutations update the cache optimistically.
 *
 * @doc.type hook
 * @doc.purpose Manage the HITL review queue with REST + live SSE updates
 * @doc.layer frontend
 */
import { useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  listPendingReviews,
  approveReview,
  rejectReview,
  type ReviewItem,
} from '@/api/aep.api';
import { subscribeToAepStream } from '@/api/sse';
import { useSSESubscription } from '@ghatana/realtime';

export const HITL_QUEUE_QUERY_KEY = 'hitl-queue';

/** Returns live HITL queue. Receives new items via SSE without polling. */
export function useHitlQueue() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: [HITL_QUEUE_QUERY_KEY, tenantId],
    queryFn: () => listPendingReviews(tenantId),
    staleTime: 60_000,
  });

  // Keep callbacks stable in refs so the subscription only restarts when tenantId changes.
  const queryClientRef = useRef(queryClient);
  queryClientRef.current = queryClient;

  // Live push: new HITL items arrive via SSE
  useSSESubscription(
    () =>
      subscribeToAepStream(
        tenantId,
        (msg) => {
          if (msg.type !== 'hitl_request_created') return;
          const item = msg.data as ReviewItem | undefined;
          if (!item) return;

          queryClientRef.current.setQueryData<ReviewItem[]>(
            [HITL_QUEUE_QUERY_KEY, tenantId],
            (prev = []) => {
              if (prev.some((r) => r.reviewId === item.reviewId)) return prev;
              return [item, ...prev];
            },
          );
        },
        () => {
          queryClientRef.current.invalidateQueries({
            queryKey: [HITL_QUEUE_QUERY_KEY, tenantId],
          });
        },
      ),
    [tenantId],
  );

  return query;
}

/** Approve a HITL review item. Invalidates the queue on success. */
export function useApproveItem() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ reviewId, note }: { reviewId: string; note?: string }) =>
      approveReview(reviewId, { note, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [HITL_QUEUE_QUERY_KEY, tenantId] });
    },
  });
}

/** Reject a HITL review item. Invalidates the queue on success. */
export function useRejectItem() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ reviewId, reason }: { reviewId: string; reason: string }) =>
      rejectReview(reviewId, { reason, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [HITL_QUEUE_QUERY_KEY, tenantId] });
    },
  });
}
