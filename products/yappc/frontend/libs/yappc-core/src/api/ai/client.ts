/**
 * AI Service Client
 *
 * Client for interacting with AI backend services (Java AI agents, vector store, RAG).
 * Provides methods for copilot chat, insights, predictions, anomalies, and semantic search.
 *
 * @doc.type client
 * @doc.purpose AI service HTTP client
 * @doc.layer api
 * @doc.pattern API Client
 */

import { ApiClient, createCorrelationMiddleware } from '@ghatana/api';
import type {
  AIInsight,
  Prediction,
  AnomalyAlert,
  Recommendation,
} from '../../types/ai';

type RecommendationSuggestion = Recommendation;

/**
 * Copilot chat message
 */
export interface CopilotMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  context?: Record<string, unknown>;
}

/**
 * Copilot chat request
 */
export interface CopilotChatRequest {
  message: string;
  conversationId?: string;
  context?: {
    currentRoute?: string;
    selectedItems?: string[];
    userIntent?: string;
    [key: string]: unknown;
  };
}

/**
 * Copilot chat response
 */
export interface CopilotChatResponse {
  message: CopilotMessage;
  conversationId: string;
  suggestions?: string[];
  actions?: Array<{
    id: string;
    label: string;
    description?: string;
  }>;
}

/**
 * AI plan generation request
 */
export interface GeneratePlanRequest {
  workflowId: string;
  intent: string;
  context?: Record<string, unknown>;
}

/**
 * AI plan step
 */
export interface AIPlanStep {
  id: string;
  name: string;
  description: string;
  type: 'analysis' | 'generation' | 'validation' | 'deployment';
  estimatedMinutes: number;
  dependencies: string[];
  aiInstructions?: string;
}

/**
 * AI-generated plan response
 */
export interface GeneratePlanResponse {
  planId: string;
  workflowId: string;
  steps: AIPlanStep[];
  estimatedDuration: number;
  confidence: number;
  reasoning?: string;
}

/**
 * Semantic search request
 */
export interface SemanticSearchRequest {
  query: string;
  limit?: number;
  minScore?: number;
  filters?: Record<string, unknown>;
}

/**
 * Search result hit
 */
export interface SearchHit {
  documentId: string;
  content: string;
  score: number;
  metadata?: Record<string, unknown>;
}

/**
 * Semantic search response
 */
export interface SemanticSearchResponse {
  query: string;
  hits: SearchHit[];
  took: number;
}

/**
 * RAG (Retrieval-Augmented Generation) request
 */
export interface RagRequest {
  query: string;
  conversationHistory?: Array<{
    role: string;
    content: string;
  }>;
  topK?: number;
  minScore?: number;
}

/**
 * RAG response
 */
export interface RagResponse {
  answer: string;
  sources: Array<{
    documentId: string;
    content: string;
    score: number;
  }>;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

/**
 * AI Service Client
 *
 * Provides methods for interacting with AI backend services.
 * Includes automatic retries, caching, and error handling.
 */
export class AIClient {
  private readonly apiClient: ApiClient;
  private cache = new Map<string, { data: unknown; timestamp: number }>();

  private readonly toInsightArray = (value: unknown): AIInsight[] =>
    Array.isArray(value) ? (value as AIInsight[]) : [];

  private readonly cacheTTL = 30000; // 30 seconds

  constructor(baseUrl = '/api/ai') {
    this.apiClient = new ApiClient({ baseUrl });
    this.apiClient.useRequest(createCorrelationMiddleware());
  }

  /**
   * Get AI insights for a specific context
   */
  async getInsights(params: {
    context?: string;
    itemId?: string;
    phaseId?: string;
    limit?: number;
  }): Promise<AIInsight[]> {
    const cacheKey = `insights:${JSON.stringify(params)}`;
    const cached = this.getCached<AIInsight[]>(cacheKey);
    if (cached) return cached;

    const queryParams = new URLSearchParams();
    if (params.context) queryParams.append('context', params.context);
    if (params.itemId) queryParams.append('itemId', params.itemId);
    if (params.phaseId) queryParams.append('phaseId', params.phaseId);
    if (params.limit) queryParams.append('limit', params.limit.toString());

    const response = await this.apiClient.get<unknown>(`/insights?${queryParams}`);
    const rawInsights = response.data;
    const insights = this.toInsightArray(rawInsights);
    this.setCache(cacheKey, insights);
    return insights;
  }

  /**
   * Get predictions for items, workflows, or system-wide
   */
  async getPredictions(params: {
    type?: 'deadline' | 'blocker' | 'resource' | 'velocity' | 'quality';
    itemId?: string;
    workflowId?: string;
    limit?: number;
  }): Promise<Prediction[]> {
    const cacheKey = `predictions:${JSON.stringify(params)}`;
    const cached = this.getCached<Prediction[]>(cacheKey);
    if (cached) return cached;

    const queryParams = new URLSearchParams();
    if (params.type) queryParams.append('type', params.type);
    if (params.itemId) queryParams.append('itemId', params.itemId);
    if (params.workflowId) queryParams.append('workflowId', params.workflowId);
    if (params.limit) queryParams.append('limit', params.limit.toString());

    const response = await this.apiClient.get<Prediction[]>(`/predictions?${queryParams}`);
    const predictions = response.data;
    this.setCache(cacheKey, predictions);
    return predictions;
  }

  /**
   * Get active anomaly alerts
   */
  async getAnomalies(params: {
    severity?: 'critical' | 'high' | 'medium' | 'low' | 'info';
    acknowledged?: boolean;
    limit?: number;
  }): Promise<AnomalyAlert[]> {
    const cacheKey = `anomalies:${JSON.stringify(params)}`;
    const cached = this.getCached<AnomalyAlert[]>(cacheKey);
    if (cached) return cached;

    const queryParams = new URLSearchParams();
    if (params.severity) queryParams.append('severity', params.severity);
    if (params.acknowledged !== undefined) {
      queryParams.append('acknowledged', params.acknowledged.toString());
    }
    if (params.limit) queryParams.append('limit', params.limit.toString());

    const response = await this.apiClient.get<AnomalyAlert[]>(`/anomalies?${queryParams}`);
    const anomalies = response.data;
    this.setCache(cacheKey, anomalies);
    return anomalies;
  }

  /**
   * Acknowledge an anomaly alert
   */
  async acknowledgeAnomaly(anomalyId: string): Promise<void> {
    await this.apiClient.post<void>(`/anomalies/${anomalyId}/acknowledge`);
    // Invalidate anomalies cache
    this.invalidateCache('anomalies:');
  }

  /**
   * Get smart recommendations based on context
   */
  async getRecommendations(params: {
    context: string;
    type?: 'tag' | 'assignee' | 'action' | 'resource';
    input?: string;
    limit?: number;
  }): Promise<RecommendationSuggestion[]> {
    const response = await this.apiClient.post<RecommendationSuggestion[]>('/recommendations', { body: params });
    return response.data;
  }

  /**
   * Send a message to the AI copilot
   */
  async sendCopilotMessage(
    request: CopilotChatRequest
  ): Promise<CopilotChatResponse> {
    const response = await this.apiClient.post<CopilotChatResponse>('/copilot/chat', { body: request });
    return response.data;
  }

  /**
   * Generate an AI execution plan for a workflow
   */
  async generatePlan(
    request: GeneratePlanRequest
  ): Promise<GeneratePlanResponse> {
    const response = await this.apiClient.post<GeneratePlanResponse>('/generate-plan', { body: request });
    return response.data;
  }

  /**
   * Perform semantic search across documents
   */
  async semanticSearch(
    request: SemanticSearchRequest
  ): Promise<SemanticSearchResponse> {
    const response = await this.apiClient.post<SemanticSearchResponse>('/search', { body: request });
    return response.data;
  }

  /**
   * Query using RAG (Retrieval-Augmented Generation)
   */
  async ragQuery(request: RagRequest): Promise<RagResponse> {
    const response = await this.apiClient.post<RagResponse>('/rag', { body: request });
    return response.data;
  }

  /**
   * Get cached value if fresh
   */
  private getCached<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    const age = Date.now() - entry.timestamp;
    if (age > this.cacheTTL) {
      this.cache.delete(key);
      return null;
    }

    return entry.data as T;
  }

  /**
   * Set cache value
   */
  private setCache(key: string, data: unknown): void {
    this.cache.set(key, { data, timestamp: Date.now() });
  }

  /**
   * Invalidate cache entries by prefix
   */
  private invalidateCache(prefix: string): void {
    const keys = Array.from(this.cache.keys());
    keys.forEach((key) => {
      if (key.startsWith(prefix)) {
        this.cache.delete(key);
      }
    });
  }
}

/**
 * Singleton instance of AI client
 */
export const aiClient = new AIClient();

export default aiClient;
