/**
 * AI API Client
 *
 * Provides typed API client for assisted features including:
 * - Natural Language to SQL conversion
 * - Schema suggestions
 * - Entity enrichment
 * - Semantic search
 * - Query recommendations
 *
 * @doc.type api-client
 * @doc.purpose AI service integration
 * @doc.layer frontend
 */

import {
  AnomalyDetectionRequestSchema,
  DetectedAnomalySchema,
  PipelineOptimisationHintsResponseSchema,
  WorkflowDraftEnvelopeSchema,
  type AnomalyDetectionRequest,
  type DetectedAnomaly,
  type PipelineOptimisationHintsResponse,
  type WorkflowDraft,
} from "../../contracts/schemas";
import {
  AI_DATA_QUALITY_ASSESSMENT_BOUNDARY_MESSAGE,
  AI_ENRICHMENT_SUGGESTION_BOUNDARY_MESSAGE,
  AI_LINEAGE_EXPLANATION_BOUNDARY_MESSAGE,
  AI_QUERY_RECOMMENDATIONS_BOUNDARY_MESSAGE,
  AI_RELATED_ENTITY_DISCOVERY_BOUNDARY_MESSAGE,
  AI_SEMANTIC_SEARCH_BOUNDARY_MESSAGE,
} from "../runtime-boundaries";
import { apiClient, type ApiResponse } from "./client";

export type {
  PipelineOptimisationHint,
  PipelineOptimisationHintsResponse,
  WorkflowDraft,
} from "../../contracts/schemas";

function withTenantParams(tenantId: string): { params: { tenantId: string } } {
  return { params: { tenantId } };
}

function unsupportedAiOperation<T>(message: string): Promise<ApiResponse<T>> {
  return Promise.reject(new Error(message));
}

// ============================================================================
// Types
// ============================================================================

/**
 * NLQ (Natural Language Query) request
 */
export interface NLQRequest {
  query: string;
  collectionName: string;
  context?: {
    recentQueries?: string[];
    selectedFields?: string[];
  };
}

/**
 * NLQ response with generated SQL
 */
export interface NLQResponse {
  sql: string;
  confidence: number;
  explanation: string;
  suggestedOptimizations?: string[];
  estimatedCost?: {
    rows: number;
    executionTimeMs: number;
  };
}

/**
 * Schema suggestion request
 */
export interface SchemaSuggestionRequest {
  collectionName: string;
  currentSchema: Record<string, unknown>;
  sampleData?: Record<string, unknown>[];
}

/**
 * Schema suggestion
 */
export interface SchemaSuggestion {
  type:
    | "add_index"
    | "add_field"
    | "modify_type"
    | "add_constraint"
    | "normalize"
    | "denormalize";
  field?: string;
  description: string;
  priority: "high" | "medium" | "low";
  confidence: number;
  impact: string;
  sql?: string;
}

/**
 * Entity enrichment request
 */
export interface EnrichmentRequest {
  collectionName: string;
  entityId: string;
  fields?: string[];
}

/**
 * Entity enrichment suggestion
 */
export interface EnrichmentSuggestion {
  field: string;
  suggestedValue: unknown;
  source: string;
  confidence: number;
  reasoning: string;
}

/**
 * Semantic search request
 */
export interface SemanticSearchRequest {
  query: string;
  scope?: "collections" | "entities" | "workflows" | "all";
  limit?: number;
  filters?: Record<string, unknown>;
}

/**
 * Semantic search result
 */
export interface SemanticSearchResult {
  type: "collection" | "entity" | "workflow" | "field";
  id: string;
  name: string;
  description?: string;
  relevanceScore: number;
  highlights: string[];
  metadata?: Record<string, unknown>;
}

/**
 * Query recommendation
 */
export interface QueryRecommendation {
  query: string;
  description: string;
  category: "frequent" | "suggested" | "template" | "optimized";
  confidence: number;
  frequency?: number;
}

/**
 * Lineage explanation request
 */
export interface LineageExplanationRequest {
  sourceId: string;
  targetId?: string;
  depth?: number;
}

/**
 * Lineage explanation
 */
export interface LineageExplanation {
  summary: string;
  dataFlow: {
    step: number;
    from: string;
    to: string;
    transformation: string;
  }[];
  impactAnalysis: {
    upstreamCount: number;
    downstreamCount: number;
    criticalPaths: string[];
  };
  recommendations?: string[];
}

/**
 * Data quality assessment
 */
export interface DataQualityAssessment {
  collectionName: string;
  overallScore: number;
  dimensions: {
    completeness: number;
    accuracy: number;
    consistency: number;
    timeliness: number;
    uniqueness: number;
  };
  issues: {
    field: string;
    issue: string;
    severity: "high" | "medium" | "low";
    affectedRows: number;
    suggestion: string;
  }[];
  trends: {
    metric: string;
    direction: "improving" | "stable" | "declining";
    change: number;
  }[];
}

export interface WorkflowDraftGenerationResult {
  draft: WorkflowDraft;
  confidence: number;
  fallback: boolean;
  model: string;
}

// ============================================================================
// API Functions
// ============================================================================

function wrapResponse<T>(data: T): ApiResponse<T> {
  return { data, status: 200 };
}

/**
 * Convert natural language to SQL
 */
export async function convertNLToSQL(
  tenantId: string,
  request: NLQRequest,
): Promise<ApiResponse<NLQResponse>> {
  const data = await apiClient.post<NLQResponse>(
    "/analytics/suggest",
    request,
    withTenantParams(tenantId),
  );
  return wrapResponse(data);
}

/**
 * Get schema suggestions
 */
export async function getSchemaSuggestions(
  tenantId: string,
  request: SchemaSuggestionRequest,
): Promise<ApiResponse<SchemaSuggestion[]>> {
  const data = await apiClient.post<SchemaSuggestion[]>(
    `/entities/${request.collectionName}/suggest`,
    {
      currentSchema: request.currentSchema,
      sampleData: request.sampleData,
    },
    withTenantParams(tenantId),
  );
  return wrapResponse(data);
}

/**
 * Get entity enrichment suggestions
 */
export async function getEnrichmentSuggestions(
  tenantId: string,
  request: EnrichmentRequest,
): Promise<ApiResponse<EnrichmentSuggestion[]>> {
  void tenantId;
  void request;
  return unsupportedAiOperation<EnrichmentSuggestion[]>(
    AI_ENRICHMENT_SUGGESTION_BOUNDARY_MESSAGE,
  );
}

/**
 * Perform semantic search
 */
export async function semanticSearch(
  tenantId: string,
  request: SemanticSearchRequest,
): Promise<ApiResponse<SemanticSearchResult[]>> {
  void tenantId;
  void request;
  return unsupportedAiOperation<SemanticSearchResult[]>(
    AI_SEMANTIC_SEARCH_BOUNDARY_MESSAGE,
  );
}

/**
 * Get query recommendations
 */
export async function getQueryRecommendations(
  tenantId: string,
  collectionName: string,
  partialQuery?: string,
): Promise<ApiResponse<QueryRecommendation[]>> {
  void tenantId;
  void collectionName;
  void partialQuery;
  return unsupportedAiOperation<QueryRecommendation[]>(
    AI_QUERY_RECOMMENDATIONS_BOUNDARY_MESSAGE,
  );
}

/**
 * Explain data lineage
 */
export async function explainLineage(
  tenantId: string,
  request: LineageExplanationRequest,
): Promise<ApiResponse<LineageExplanation>> {
  void tenantId;
  void request;
  throw new Error(AI_LINEAGE_EXPLANATION_BOUNDARY_MESSAGE);
}

/**
 * Detect anomalies
 */
export async function detectAnomalies(
  tenantId: string,
  request: AnomalyDetectionRequest,
): Promise<ApiResponse<DetectedAnomaly[]>> {
  const validatedRequest = AnomalyDetectionRequestSchema.parse(request);
  const rawResponse = await apiClient.post<DetectedAnomaly[]>(
    `/entities/${request.collectionName}/anomalies`,
    validatedRequest,
    withTenantParams(tenantId),
  );
  const data = rawResponse.map((anomaly) =>
    DetectedAnomalySchema.parse(anomaly),
  );
  return wrapResponse(data);
}

/**
 * Assess data quality
 */
export async function assessDataQuality(
  tenantId: string,
  collectionName: string,
): Promise<ApiResponse<DataQualityAssessment>> {
  void tenantId;
  void collectionName;
  return unsupportedAiOperation<DataQualityAssessment>(
    AI_DATA_QUALITY_ASSESSMENT_BOUNDARY_MESSAGE,
  );
}

/**
 * Generate entity from description
 */
export async function suggestEntity(
  tenantId: string,
  collectionName: string,
  description: string,
): Promise<
  ApiResponse<{ suggestion: Record<string, unknown>; confidence: number }>
> {
  const data = await apiClient.post<{
    suggestion: Record<string, unknown>;
    confidence: number;
  }>(
    `/entities/${collectionName}/suggest`,
    { description },
    withTenantParams(tenantId),
  );
  return wrapResponse(data);
}

/**
 * Find related entities
 */
export async function findRelatedEntities(
  tenantId: string,
  collectionName: string,
  entityId: string,
  limit = 10,
): Promise<ApiResponse<SemanticSearchResult[]>> {
  void tenantId;
  void collectionName;
  void entityId;
  void limit;
  return unsupportedAiOperation<SemanticSearchResult[]>(
    AI_RELATED_ENTITY_DISCOVERY_BOUNDARY_MESSAGE,
  );
}

// ============================================================================
// Pipeline Optimisation Hints (AI Journey #4 — DC-E5-S1)
// ============================================================================

/**
 * Fetch AI optimisation hints for a specific pipeline / workflow.
 * Calls POST /api/v1/action/pipelines/:pipelineId/optimise-hint
 */
export async function getPipelineOptimisationHints(
  pipelineId: string,
): Promise<ApiResponse<PipelineOptimisationHintsResponse>> {
  const rawResponse = await apiClient.post<PipelineOptimisationHintsResponse>(
    `/action/pipelines/${pipelineId}/optimise-hint`,
    {},
  );
  const data = PipelineOptimisationHintsResponseSchema.parse(rawResponse);
  return wrapResponse(data);
}

/**
 * Generate an editable workflow draft from a natural-language prompt.
 */
export async function generateWorkflowDraft(
  tenantId: string,
  prompt: string,
): Promise<ApiResponse<WorkflowDraftGenerationResult>> {
  const rawResponse = await apiClient.post<unknown>(
    "/action/pipelines/draft",
    { prompt },
    { headers: { "X-Tenant-ID": tenantId } },
  );
  const envelope = WorkflowDraftEnvelopeSchema.parse(rawResponse);
  return wrapResponse({
    draft: envelope.data,
    confidence: envelope.ai?.confidence ?? 0,
    fallback: envelope.ai?.fallback ?? false,
    model: envelope.ai?.model ?? "unknown",
  });
}

// ============================================================================
// React Query Hooks
// ============================================================================

export const aiQueryKeys = {
  nlq: (tenantId: string, query: string) =>
    ["ai", "nlq", tenantId, query] as const,
  suggestions: (tenantId: string, collection: string) =>
    ["ai", "suggestions", tenantId, collection] as const,
  recommendations: (tenantId: string) =>
    ["ai", "recommendations", tenantId] as const,
  anomalies: (tenantId: string, collection: string) =>
    ["ai", "anomalies", tenantId, collection] as const,
  quality: (tenantId: string, collection: string) =>
    ["ai", "quality", tenantId, collection] as const,
  search: (tenantId: string, query: string) =>
    ["ai", "search", tenantId, query] as const,
  pipelineHints: (pipelineId: string) =>
    ["ai", "pipeline-hints", pipelineId] as const,
  workflowDraft: (tenantId: string, prompt: string) =>
    ["ai", "workflow-draft", tenantId, prompt] as const,
};

export default {
  convertNLToSQL,
  getSchemaSuggestions,
  getEnrichmentSuggestions,
  semanticSearch,
  getQueryRecommendations,
  explainLineage,
  detectAnomalies,
  assessDataQuality,
  suggestEntity,
  findRelatedEntities,
  getPipelineOptimisationHints,
  generateWorkflowDraft,
};
