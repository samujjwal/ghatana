/**
 * Kernel Health Hooks
 *
 * TanStack Query hooks for Kernel Health visibility data.
 * Provides type-safe query state for ProductUnit health views, lifecycle timelines,
 * and action recommendations.
 *
 * @doc.type hook
 * @doc.purpose TanStack Query hooks for Kernel ProductUnit health data
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import {
  listKernelProductUnits,
  getKernelProductUnitHealth,
  getKernelLifecycleTimeline,
  getKernelRecommendedActions,
  type KernelProductUnitHealthSummary,
  type KernelProductUnitHealthView,
  type KernelLifecycleTimelineView,
  type KernelActionRecommendation,
} from '../clients/kernelHealthClient';

// ============================================================================
// Query Keys
// ============================================================================

export const kernelHealthQueryKeys = {
  all: ['kernel-health'] as const,
  products: () => [...kernelHealthQueryKeys.all, 'products'] as const,
  product: (productUnitId: string) =>
    [...kernelHealthQueryKeys.products(), productUnitId] as const,
  timeline: (productUnitId: string) =>
    [...kernelHealthQueryKeys.product(productUnitId), 'timeline'] as const,
  recommendations: (productUnitId: string) =>
    [...kernelHealthQueryKeys.product(productUnitId), 'recommendations'] as const,
} as const;

// ============================================================================
// Hooks
// ============================================================================

/**
 * Lists all Kernel ProductUnit health summaries.
 * Refreshes every 30 seconds while the component is mounted.
 */
export function useKernelProductUnitList(): UseQueryResult<
  KernelProductUnitHealthSummary[]
> {
  return useQuery({
    queryKey: kernelHealthQueryKeys.products(),
    queryFn: listKernelProductUnits,
    refetchInterval: 30_000,
  });
}

/**
 * Returns the full health view for a single Kernel ProductUnit.
 * Only executes when {@code productUnitId} is a non-empty string.
 */
export function useKernelProductUnitHealth(
  productUnitId: string | undefined
): UseQueryResult<KernelProductUnitHealthView> {
  return useQuery({
    queryKey: kernelHealthQueryKeys.product(productUnitId ?? ''),
    queryFn: () => getKernelProductUnitHealth(productUnitId!),
    enabled: Boolean(productUnitId && productUnitId.trim().length > 0),
    refetchInterval: 30_000,
  });
}

/**
 * Returns the lifecycle timeline (phase run history) for a Kernel ProductUnit.
 * Only executes when {@code productUnitId} is a non-empty string.
 */
export function useKernelLifecycleTimeline(
  productUnitId: string | undefined
): UseQueryResult<KernelLifecycleTimelineView> {
  return useQuery({
    queryKey: kernelHealthQueryKeys.timeline(productUnitId ?? ''),
    queryFn: () => getKernelLifecycleTimeline(productUnitId!),
    enabled: Boolean(productUnitId && productUnitId.trim().length > 0),
    refetchInterval: 60_000,
  });
}

/**
 * Returns recommended actions for a Kernel ProductUnit based on current health state.
 * Only executes when {@code productUnitId} is a non-empty string.
 */
export function useKernelRecommendedActions(
  productUnitId: string | undefined
): UseQueryResult<KernelActionRecommendation[]> {
  return useQuery({
    queryKey: kernelHealthQueryKeys.recommendations(productUnitId ?? ''),
    queryFn: () => getKernelRecommendedActions(productUnitId!),
    enabled: Boolean(productUnitId && productUnitId.trim().length > 0),
    refetchInterval: 60_000,
  });
}
