/**
 * useHitlQueue — combined TanStack Query + SSE hook for the HITL review queue.
 *
 * 1. Initial data from REST: `GET /api/v1/hitl/pending`
 * 2. Live updates via SSE: `hitl_request_created` events add items to the local cache without
 *    a full refetch.
 * 3. Approve/reject mutations update the cache optimistically.
 * 4. Smart prioritization by urgency (confidence × age × business impact).
 *
 * @doc.type hook
 * @doc.purpose Manage the HITL review queue with REST + live SSE updates and smart prioritization
 * @doc.layer frontend
 */
import { useRef, useMemo } from 'react';
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
import { isFeatureEnabled } from '@/lib/feature-flags';

export const HITL_QUEUE_QUERY_KEY = 'hitl-queue';

/**
 * Calculate urgency score for a review item
 * Formula: urgency = (1 - confidence) × log(age_in_hours) × business_impact_factor
 */
export function calculateUrgency(item: ReviewItem): number {
  const age = Date.now() - new Date(item.createdAt).getTime();
  const ageInHours = age / (1000 * 60 * 60);
  
  const confidence = item.confidenceScore ?? 0.5;
  const confidenceFactor = 1 - confidence;
  
  // Logarithmic scaling for age (older items get higher urgency but with diminishing returns)
  const ageFactor = Math.log(Math.max(ageInHours, 1));
  
  // Business impact factors based on item type
  const businessImpactFactors: Record<string, number> = {
    POLICY: 1.5, // Policy decisions have high business impact
    PATTERN: 1.2, // Patterns affect multiple runs
    AGENT_DECISION: 1.0, // Agent decisions are standard
  };
  const businessImpactFactor = businessImpactFactors[item.itemType] ?? 1.0;
  
  return confidenceFactor * ageFactor * businessImpactFactor;
}

/**
 * Get urgency level from score
 */
export function getUrgencyLevel(score: number): 'critical' | 'high' | 'medium' | 'low' {
  if (score > 2.0) return 'critical';
  if (score > 1.0) return 'high';
  if (score > 0.5) return 'medium';
  return 'low';
}

/**
 * Get urgency badge color
 */
export function getUrgencyColor(level: 'critical' | 'high' | 'medium' | 'low'): string {
  const colors = {
    critical: 'bg-red-100 text-red-700 border-red-200 dark:bg-red-900 dark:text-red-300 dark:border-red-800',
    high: 'bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-900 dark:text-orange-300 dark:border-orange-800',
    medium: 'bg-amber-100 text-amber-700 border-amber-200 dark:bg-amber-900 dark:text-amber-300 dark:border-amber-800',
    low: 'bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900 dark:text-blue-300 dark:border-blue-800',
  };
  return colors[level];
}

/** Returns live HITL queue. Receives new items via SSE without polling. */
export function useHitlQueue() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();
  const smartPrioritization = isFeatureEnabled('SMART_PRIORITIZATION');

  const query = useQuery({
    queryKey: [HITL_QUEUE_QUERY_KEY, tenantId],
    queryFn: () => listPendingReviews(tenantId),
    staleTime: 60_000,
  });

  // Sort by urgency if feature enabled
  const sortedItems = useMemo(() => {
    const items = query.data ?? [];
    if (!smartPrioritization) return items;
    
    return [...items].sort((a, b) => {
      const urgencyA = calculateUrgency(a);
      const urgencyB = calculateUrgency(b);
      return urgencyB - urgencyA; // Descending order (highest urgency first)
    });
  }, [query.data, smartPrioritization]);

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

  // Return query with sorted items
  return {
    ...query,
    data: sortedItems,
  };
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
