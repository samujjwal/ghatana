/**
 * Kernel Health API Client
 *
 * Typed fetch-based client for YAPPC Kernel Health visibility endpoints.
 * All responses are validated at the boundary using Zod schemas.
 *
 * Endpoints served by the YAPPC backend at `/api/v1/yappc/kernel-health/`.
 *
 * @doc.type module
 * @doc.purpose Kernel Health API client with typed requests and Zod boundary validation
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { z } from 'zod';

// ============================================================================
// Response Schemas (Zod — boundary validation)
// ============================================================================

const LifecycleRunSummarySchema = z.object({
  phase: z.string(),
  status: z.string(),
  timestamp: z.string(),
});

const ProductUnitHealthSummarySchema = z.object({
  productUnitId: z.string(),
  overallStatus: z.enum(['healthy', 'degraded', 'failed', 'unknown']),
  currentPhase: z.string(),
  lastRunTimestamp: z.string(),
});

const ProductUnitHealthViewSchema = ProductUnitHealthSummarySchema.extend({
  gateFailureCount: z.number(),
  deploymentStatus: z.string(),
  healthSnapshot: z.record(z.string(), z.unknown()),
  lifecycleResult: z.record(z.string(), z.unknown()),
  deployment: z.record(z.string(), z.unknown()),
});

const LifecycleTimelineViewSchema = z.object({
  productUnitId: z.string(),
  runs: z.array(LifecycleRunSummarySchema),
});

const ActionRecommendationSchema = z.object({
  severity: z.enum(['critical', 'warning', 'info']),
  title: z.string(),
  description: z.string(),
  actionType: z.string(),
  owner: z.string().optional(),
  reason: z.string().optional(),
  evidenceId: z.string().optional(),
  nextAction: z.string().optional(),
});

const ProductUnitListSchema = z.array(ProductUnitHealthSummarySchema);
const RecommendedActionsListSchema = z.array(ActionRecommendationSchema);

// ============================================================================
// Exported Types
// ============================================================================

export type KernelLifecycleRunSummary = z.infer<typeof LifecycleRunSummarySchema>;
export type KernelProductUnitHealthSummary = z.infer<typeof ProductUnitHealthSummarySchema>;
export type KernelProductUnitHealthView = z.infer<typeof ProductUnitHealthViewSchema>;
export type KernelLifecycleTimelineView = z.infer<typeof LifecycleTimelineViewSchema>;
export type KernelActionRecommendation = z.infer<typeof ActionRecommendationSchema>;

// ============================================================================
// Internal helpers
// ============================================================================

const BASE_URL = '/api/v1/yappc/kernel-health';

async function apiFetch<T>(
  url: string,
  schema: z.ZodType<T>,
  options?: RequestInit
): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(
      `Kernel Health API error: ${response.status} ${response.statusText} for ${url}`
    );
  }

  const raw: unknown = await response.json();
  return schema.parse(raw);
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Lists all Kernel ProductUnit health summaries visible to the current user.
 */
export async function listKernelProductUnits(): Promise<KernelProductUnitHealthSummary[]> {
  return apiFetch(`${BASE_URL}/products`, ProductUnitListSchema);
}

/**
 * Returns the full health view for a single Kernel ProductUnit.
 */
export async function getKernelProductUnitHealth(
  productUnitId: string
): Promise<KernelProductUnitHealthView> {
  if (!productUnitId || productUnitId.trim().length === 0) {
    throw new Error('productUnitId is required');
  }
  return apiFetch(
    `${BASE_URL}/products/${encodeURIComponent(productUnitId)}`,
    ProductUnitHealthViewSchema
  );
}

/**
 * Returns the lifecycle timeline (phase run history) for a ProductUnit.
 */
export async function getKernelLifecycleTimeline(
  productUnitId: string
): Promise<KernelLifecycleTimelineView> {
  if (!productUnitId || productUnitId.trim().length === 0) {
    throw new Error('productUnitId is required');
  }
  return apiFetch(
    `${BASE_URL}/products/${encodeURIComponent(productUnitId)}/timeline`,
    LifecycleTimelineViewSchema
  );
}

/**
 * Returns recommended actions for a ProductUnit based on current health state.
 */
export async function getKernelRecommendedActions(
  productUnitId: string
): Promise<KernelActionRecommendation[]> {
  if (!productUnitId || productUnitId.trim().length === 0) {
    throw new Error('productUnitId is required');
  }
  return apiFetch(
    `${BASE_URL}/products/${encodeURIComponent(productUnitId)}/recommendations`,
    RecommendedActionsListSchema
  );
}
