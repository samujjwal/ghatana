/**
 * AI API Client
 * 
 * Provides typed API client for AI-powered features including:
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

import { apiClient, type ApiResponse } from './client';

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
    type: 'add_index' | 'add_field' | 'modify_type' | 'add_constraint' | 'normalize' | 'denormalize';
    field?: string;
    description: string;
    priority: 'high' | 'medium' | 'low';
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
    scope?: 'collections' | 'entities' | 'workflows' | 'all';
    limit?: number;
    filters?: Record<string, unknown>;
}

/**
 * Semantic search result
 */
export interface SemanticSearchResult {
    type: 'collection' | 'entity' | 'workflow' | 'field';
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
    category: 'frequent' | 'suggested' | 'template' | 'optimized';
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
 * Anomaly detection request
 */
export interface AnomalyDetectionRequest {
    collectionName: string;
    timeRange?: {
        start: string;
        end: string;
    };
    metrics?: string[];
}

/**
 * Detected anomaly
 */
export interface DetectedAnomaly {
    id: string;
    type: 'spike' | 'drop' | 'pattern_change' | 'outlier' | 'missing_data';
    severity: 'critical' | 'warning' | 'info';
    metric: string;
    timestamp: string;
    value: number;
    expectedValue: number;
    deviation: number;
    description: string;
    suggestedAction?: string;
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
        severity: 'high' | 'medium' | 'low';
        affectedRows: number;
        suggestion: string;
    }[];
    trends: {
        metric: string;
        direction: 'improving' | 'stable' | 'declining';
        change: number;
    }[];
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
    request: NLQRequest
): Promise<ApiResponse<NLQResponse>> {
    const data = await apiClient.post<NLQResponse>(
        `/api/${tenantId}/ai/nlq`,
        request
    );
    return wrapResponse(data);
}

/**
 * Get schema suggestions
 */
export async function getSchemaSuggestions(
    tenantId: string,
    request: SchemaSuggestionRequest
): Promise<ApiResponse<SchemaSuggestion[]>> {
    const data = await apiClient.post<SchemaSuggestion[]>(
        `/api/${tenantId}/ai/schema/suggestions`,
        request
    );
    return wrapResponse(data);
}

/**
 * Get entity enrichment suggestions
 */
export async function getEnrichmentSuggestions(
    tenantId: string,
    request: EnrichmentRequest
): Promise<ApiResponse<EnrichmentSuggestion[]>> {
    const data = await apiClient.post<EnrichmentSuggestion[]>(
        `/api/${tenantId}/entities/${request.collectionName}/${request.entityId}/enrich`,
        { fields: request.fields }
    );
    return wrapResponse(data);
}

/**
 * Perform semantic search
 */
export async function semanticSearch(
    tenantId: string,
    request: SemanticSearchRequest
): Promise<ApiResponse<SemanticSearchResult[]>> {
    const data = await apiClient.post<SemanticSearchResult[]>(
        `/api/${tenantId}/ai/search`,
        request
    );
    return wrapResponse(data);
}

/**
 * Get query recommendations
 */
export async function getQueryRecommendations(
    tenantId: string,
    collectionName: string,
    partialQuery?: string
): Promise<ApiResponse<QueryRecommendation[]>> {
    const data = await apiClient.get<QueryRecommendation[]>(
        `/api/${tenantId}/ai/recommendations`,
        {
            params: { collectionName, query: partialQuery }
        }
    );
    return wrapResponse(data);
}

/**
 * Explain data lineage
 */
export async function explainLineage(
    tenantId: string,
    request: LineageExplanationRequest
): Promise<ApiResponse<LineageExplanation>> {
    const data = await apiClient.post<LineageExplanation>(
        `/api/${tenantId}/ai/lineage/explain`,
        request
    );
    return wrapResponse(data);
}

/**
 * Detect anomalies
 */
export async function detectAnomalies(
    tenantId: string,
    request: AnomalyDetectionRequest
): Promise<ApiResponse<DetectedAnomaly[]>> {
    const data = await apiClient.post<DetectedAnomaly[]>(
        `/api/${tenantId}/ai/anomalies/detect`,
        request
    );
    return wrapResponse(data);
}

/**
 * Assess data quality
 */
export async function assessDataQuality(
    tenantId: string,
    collectionName: string
): Promise<ApiResponse<DataQualityAssessment>> {
    const data = await apiClient.get<DataQualityAssessment>(
        `/api/${tenantId}/collections/${collectionName}/quality`
    );
    return wrapResponse(data);
}

/**
 * Generate entity from description
 */
export async function suggestEntity(
    tenantId: string,
    collectionName: string,
    description: string
): Promise<ApiResponse<{ suggestion: Record<string, unknown>; confidence: number }>> {
    const data = await apiClient.post<{ suggestion: Record<string, unknown>; confidence: number }>(
        `/api/${tenantId}/entities/suggest`,
        { collectionName, description }
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
    limit = 10
): Promise<ApiResponse<SemanticSearchResult[]>> {
    const data = await apiClient.get<SemanticSearchResult[]>(
        `/api/${tenantId}/entities/${collectionName}/${entityId}/related`,
        { params: { limit } }
    );
    return wrapResponse(data);
}

// ============================================================================
// React Query Hooks
// ============================================================================

export const aiQueryKeys = {
    nlq: (tenantId: string, query: string) => ['ai', 'nlq', tenantId, query] as const,
    suggestions: (tenantId: string, collection: string) => ['ai', 'suggestions', tenantId, collection] as const,
    recommendations: (tenantId: string) => ['ai', 'recommendations', tenantId] as const,
    anomalies: (tenantId: string, collection: string) => ['ai', 'anomalies', tenantId, collection] as const,
    quality: (tenantId: string, collection: string) => ['ai', 'quality', tenantId, collection] as const,
    search: (tenantId: string, query: string) => ['ai', 'search', tenantId, query] as const,
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
};
