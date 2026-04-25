/**
 * AI Operations Service
 *
 * Typed contract for cross-surface AI-assisted operation suggestions.
 * Covers alerts, workflows, data quality, and fabric placement advisories.
 *
 * All methods are backend-first with explicit boundary fallback:
 * - When the ML platform or feature scoring service is unavailable,
 *   methods raise a runtime boundary error (404/405/501 → boundary).
 * - Callers can catch `UnsupportedRuntimeBoundaryError` and render the
 *   appropriate `UnsupportedSurfaceBoundary` or capability-gated notice.
 *
 * Backend contract (not yet available — activate when ML platform ships):
 *   POST /api/v1/ai/suggestions          → AiOperationSuggestionListEnvelopeSchema
 *   POST /api/v1/ai/suggestions/:id/apply → AiApplySuggestionResponseSchema
 *   GET  /api/v1/ai/correlations          → AiCrossCorrelationListEnvelopeSchema
 *   GET  /api/v1/ai/advisories/workflows/:id → AiWorkflowAdvisorySchema
 *   GET  /api/v1/ai/advisories/quality/:collectionId → AiQualityAdvisorySchema
 *   GET  /api/v1/ai/advisories/fabric/:collectionId  → AiFabricAdvisorySchema
 *
 * @doc.type service
 * @doc.purpose Cross-surface AI-assisted operation suggestion client
 * @doc.layer frontend
 * @doc.pattern Service
 */

import { z } from 'zod';
import { apiClient } from '../lib/api/client';
import SessionBootstrap from '../lib/auth/session';
import {
  AI_OPERATIONS_SUGGESTION_BOUNDARY_MESSAGE,
  AI_OPERATIONS_CROSS_SURFACE_BOUNDARY_MESSAGE,
  AI_OPERATIONS_APPLY_BOUNDARY_MESSAGE,
  AI_WORKFLOW_ADVISORY_BOUNDARY_MESSAGE,
  AI_QUALITY_ADVISORY_BOUNDARY_MESSAGE,
  AI_FABRIC_ADVISORY_BOUNDARY_MESSAGE,
  createRuntimeBoundaryError,
} from '../lib/runtime-boundaries';

// ── Common schemas ────────────────────────────────────────────────────────────

/** The surface a suggestion applies to. Extensible as new surfaces are added. */
export const AiOperationSurfaceSchema = z.enum([
  'alerts',
  'workflows',
  'quality',
  'fabric',
  'query',
]);

export type AiOperationSurface = z.infer<typeof AiOperationSurfaceSchema>;

/** Confidence band for any AI advisory output. */
export const AiConfidenceBandSchema = z.enum(['high', 'medium', 'low', 'experimental']);
export type AiConfidenceBand = z.infer<typeof AiConfidenceBandSchema>;

// ── Operation suggestion schemas ──────────────────────────────────────────────

/**
 * A single AI-generated suggestion for a cross-surface operation.
 * Suggestions are advisory — they must never trigger destructive actions
 * automatically. Users must explicitly confirm via GuardedAction before applying.
 */
export const AiOperationSuggestionSchema = z.object({
  id: z.string(),
  surface: AiOperationSurfaceSchema,
  title: z.string(),
  description: z.string(),
  /** Normalised 0–1 confidence score from the ML scoring service. */
  confidence: z.number().min(0).max(1),
  confidenceBand: AiConfidenceBandSchema,
  /** Whether the suggestion can be automatically applied (subject to user confirmation). */
  canAutoApply: z.boolean(),
  /** Estimated impact of applying the suggestion. */
  impact: z.object({
    severity: z.enum(['low', 'medium', 'high']),
    affectedEntities: z.array(z.string()),
    description: z.string(),
  }),
  /** Context IDs this suggestion relates to (alertIds, workflowIds, collectionIds, etc.). */
  contextIds: z.array(z.string()),
  generatedAt: z.string().datetime(),
  /** Model or heuristic that generated this suggestion. */
  source: z.string(),
});

export const AiOperationSuggestionListEnvelopeSchema = z.object({
  tenantId: z.string(),
  surface: AiOperationSurfaceSchema.optional(),
  suggestions: z.array(AiOperationSuggestionSchema),
  count: z.number().int().nonnegative(),
  generatedAt: z.string().datetime(),
  modelVersion: z.string().optional(),
});

export const AiApplySuggestionResponseSchema = z.object({
  suggestionId: z.string(),
  applied: z.boolean(),
  outcome: z.enum(['success', 'partial', 'failed', 'deferred']),
  message: z.string(),
  appliedAt: z.string().datetime(),
  auditEventId: z.string().optional(),
});

// ── Cross-surface correlation schemas ─────────────────────────────────────────

/**
 * A cross-surface AI correlation linking alerts to related workflows,
 * quality issues, or fabric tier events.
 */
export const AiCrossCorrelationSchema = z.object({
  id: z.string(),
  primarySurface: AiOperationSurfaceSchema,
  primaryEntityId: z.string(),
  correlatedSurface: AiOperationSurfaceSchema,
  correlatedEntityId: z.string(),
  correlationType: z.enum(['causal', 'temporal', 'structural', 'semantic']),
  confidence: z.number().min(0).max(1),
  confidenceBand: AiConfidenceBandSchema,
  explanation: z.string(),
  suggestedAction: z.string().optional(),
  detectedAt: z.string().datetime(),
});

export const AiCrossCorrelationListEnvelopeSchema = z.object({
  tenantId: z.string(),
  correlations: z.array(AiCrossCorrelationSchema),
  count: z.number().int().nonnegative(),
  generatedAt: z.string().datetime(),
});

// ── Workflow advisory schemas ─────────────────────────────────────────────────

export const AiWorkflowAdvisorySchema = z.object({
  workflowId: z.string(),
  tenantId: z.string(),
  advisories: z.array(
    z.object({
      id: z.string(),
      type: z.enum(['performance', 'reliability', 'cost', 'security', 'quality']),
      title: z.string(),
      description: z.string(),
      confidence: z.number().min(0).max(1),
      confidenceBand: AiConfidenceBandSchema,
      suggestedAction: z.string().optional(),
      priority: z.enum(['critical', 'high', 'medium', 'low']),
    }),
  ),
  generatedAt: z.string().datetime(),
  modelVersion: z.string().optional(),
});

// ── Quality advisory schemas ──────────────────────────────────────────────────

export const AiQualityAdvisorySchema = z.object({
  collectionId: z.string(),
  tenantId: z.string(),
  overallScore: z.number().min(0).max(1),
  scoreBand: AiConfidenceBandSchema,
  advisories: z.array(
    z.object({
      id: z.string(),
      fieldName: z.string().optional(),
      type: z.enum(['completeness', 'accuracy', 'consistency', 'timeliness', 'validity']),
      title: z.string(),
      description: z.string(),
      affectedCount: z.number().int().nonnegative(),
      confidence: z.number().min(0).max(1),
      suggestedAction: z.string().optional(),
    }),
  ),
  generatedAt: z.string().datetime(),
  modelVersion: z.string().optional(),
});

// ── Fabric advisory schemas ───────────────────────────────────────────────────

export const AiFabricAdvisorySchema = z.object({
  collectionId: z.string(),
  tenantId: z.string(),
  currentTier: z.enum(['hot', 'warm', 'cold', 'archive']),
  recommendedTier: z.enum(['hot', 'warm', 'cold', 'archive']).optional(),
  advisories: z.array(
    z.object({
      id: z.string(),
      type: z.enum(['tier-migration', 'replication', 'partitioning', 'indexing', 'retention']),
      title: z.string(),
      description: z.string(),
      estimatedCostImpact: z.enum(['positive', 'neutral', 'negative']).optional(),
      confidence: z.number().min(0).max(1),
      confidenceBand: AiConfidenceBandSchema,
      suggestedAction: z.string().optional(),
    }),
  ),
  generatedAt: z.string().datetime(),
  modelVersion: z.string().optional(),
});

// ── Inferred types ────────────────────────────────────────────────────────────

export type AiOperationSuggestion = z.infer<typeof AiOperationSuggestionSchema>;
export type AiApplySuggestionResponse = z.infer<typeof AiApplySuggestionResponseSchema>;
export type AiCrossCorrelation = z.infer<typeof AiCrossCorrelationSchema>;
export type AiWorkflowAdvisory = z.infer<typeof AiWorkflowAdvisorySchema>;
export type AiQualityAdvisory = z.infer<typeof AiQualityAdvisorySchema>;
export type AiFabricAdvisory = z.infer<typeof AiFabricAdvisorySchema>;

// ── Request types ─────────────────────────────────────────────────────────────

export interface GetSuggestionsRequest {
  surface: AiOperationSurface;
  contextIds?: string[];
  limit?: number;
}

export interface GetCorrelationsRequest {
  primarySurface?: AiOperationSurface;
  primaryEntityIds?: string[];
  limit?: number;
}

// ── Internal helpers ──────────────────────────────────────────────────────────

function getTenantId(): string {
  return SessionBootstrap.requireTenantId();
}

/**
 * Maps HTTP 404/405/501 → runtime boundary error.
 * All other errors are re-thrown so genuine failures surface to TanStack Query.
 */
function normaliseAiApiError(error: unknown, boundaryMessage: string): never {
  if (typeof error === 'object' && error !== null && 'status' in error) {
    const status = Number((error as { status?: unknown }).status);
    if (status === 404 || status === 405 || status === 501) {
      throw createRuntimeBoundaryError(boundaryMessage);
    }
  }
  throw error instanceof Error ? error : new Error('Unknown AI operations API error');
}

// ── Service class ─────────────────────────────────────────────────────────────

/**
 * AiOperationsService
 *
 * Canonical boundary-aware client for the launcher AI operations APIs.
 *
 * Design invariants:
 * - Every method is backend-first: attempts the real endpoint first.
 * - HTTP 404/405/501 responses mean the ML platform is not yet deployed —
 *   the method raises a `createRuntimeBoundaryError` with a descriptive
 *   message that surface components can catch and display as advisory copy.
 * - Suggestions are never applied automatically. All apply paths must go
 *   through a `GuardedAction` confirmation before calling `applySuggestion`.
 * - Cross-surface correlations link alerts ↔ workflows ↔ quality ↔ fabric
 *   through the unified operation event model (not yet available).
 */
export class AiOperationsService {
  /**
   * Fetch AI-generated operation suggestions for a specific surface.
   *
   * POST /api/v1/ai/suggestions
   *
   * @param request - Surface context and optional entity IDs to scope suggestions.
   */
  async getSuggestions(request: GetSuggestionsRequest): Promise<AiOperationSuggestion[]> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.post(
        '/ai/suggestions',
        {
          surface: request.surface,
          contextIds: request.contextIds ?? [],
          limit: request.limit ?? 10,
        },
        { headers: { 'X-Tenant-ID': tenantId } },
      );
      return AiOperationSuggestionListEnvelopeSchema.parse(response).suggestions;
    } catch (error) {
      return normaliseAiApiError(error, AI_OPERATIONS_SUGGESTION_BOUNDARY_MESSAGE);
    }
  }

  /**
   * Apply an AI suggestion. Must only be called after explicit user confirmation
   * via `GuardedAction`. Returns the outcome of the apply operation.
   *
   * POST /api/v1/ai/suggestions/:id/apply
   *
   * @param suggestionId - The ID of the suggestion to apply.
   * @param context - Optional additional context for the apply operation.
   */
  async applySuggestion(
    suggestionId: string,
    context?: Record<string, unknown>,
  ): Promise<AiApplySuggestionResponse> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.post(
        `/ai/suggestions/${suggestionId}/apply`,
        { context: context ?? {} },
        { headers: { 'X-Tenant-ID': tenantId } },
      );
      return AiApplySuggestionResponseSchema.parse(response);
    } catch (error) {
      return normaliseAiApiError(error, AI_OPERATIONS_APPLY_BOUNDARY_MESSAGE);
    }
  }

  /**
   * Fetch cross-surface AI correlations linking alerts, workflows, quality,
   * and fabric events through the unified operation event model.
   *
   * GET /api/v1/ai/correlations
   *
   * @param request - Optional primary surface and entity IDs to scope correlations.
   */
  async getCrossCorrelations(request: GetCorrelationsRequest = {}): Promise<AiCrossCorrelation[]> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get('/ai/correlations', {
        params: {
          primarySurface: request.primarySurface,
          primaryEntityIds: request.primaryEntityIds?.join(','),
          limit: request.limit ?? 20,
        },
        headers: { 'X-Tenant-ID': tenantId },
      });
      return AiCrossCorrelationListEnvelopeSchema.parse(response).correlations;
    } catch (error) {
      return normaliseAiApiError(error, AI_OPERATIONS_CROSS_SURFACE_BOUNDARY_MESSAGE);
    }
  }

  /**
   * Fetch AI advisories for a specific workflow.
   *
   * GET /api/v1/ai/advisories/workflows/:workflowId
   */
  async getWorkflowAdvisories(workflowId: string): Promise<AiWorkflowAdvisory> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get(`/ai/advisories/workflows/${workflowId}`, {
        headers: { 'X-Tenant-ID': tenantId },
      });
      return AiWorkflowAdvisorySchema.parse(response);
    } catch (error) {
      return normaliseAiApiError(error, AI_WORKFLOW_ADVISORY_BOUNDARY_MESSAGE);
    }
  }

  /**
   * Fetch AI data quality advisories for a collection.
   *
   * GET /api/v1/ai/advisories/quality/:collectionId
   */
  async getQualityAdvisories(collectionId: string): Promise<AiQualityAdvisory> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get(`/ai/advisories/quality/${collectionId}`, {
        headers: { 'X-Tenant-ID': tenantId },
      });
      return AiQualityAdvisorySchema.parse(response);
    } catch (error) {
      return normaliseAiApiError(error, AI_QUALITY_ADVISORY_BOUNDARY_MESSAGE);
    }
  }

  /**
   * Fetch AI fabric tier placement advisories for a collection.
   *
   * GET /api/v1/ai/advisories/fabric/:collectionId
   */
  async getFabricAdvisories(collectionId: string): Promise<AiFabricAdvisory> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get(`/ai/advisories/fabric/${collectionId}`, {
        headers: { 'X-Tenant-ID': tenantId },
      });
      return AiFabricAdvisorySchema.parse(response);
    } catch (error) {
      return normaliseAiApiError(error, AI_FABRIC_ADVISORY_BOUNDARY_MESSAGE);
    }
  }
}

export const aiOperationsService = new AiOperationsService();
