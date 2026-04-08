/**
 * AI Service Types
 *
 * Type definitions for LLM integration and AI quality telemetry.
 *
 * @doc.type types
 * @doc.purpose Type definitions for AI services
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

// ============================================================================
// LLM Types
// ============================================================================

export type ModelProvider = 'openai' | 'anthropic' | 'azure' | 'local';

export type FallbackStrategy = 'sequential' | 'parallel' | 'circuit-breaker';

export interface LLMRequest {
  prompt: string;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  frequencyPenalty?: number;
  presencePenalty?: number;
  stopSequences?: string[];
  model?: string;
}

export interface LLMResponse {
  text: string;
  model: string;
  tokenCount: number;
  tokenProbs?: number[];
  finishReason?: string;
}

// ============================================================================
// Error Types
// ============================================================================

export type AIErrorCode =
  | 'TIMEOUT'
  | 'RATE_LIMIT'
  | 'INVALID_REQUEST'
  | 'AUTHENTICATION'
  | 'SERVER_ERROR'
  | 'NETWORK_ERROR'
  | 'UNKNOWN';

export interface AIError {
  code: AIErrorCode;
  message: string;
  retryable: boolean;
  details?: Record<string, unknown>;
}

// ============================================================================
// Quality Telemetry Types
// ============================================================================

export interface QualityMetric {
  timestamp: number;
  requestId: string;
  model: string;
  provider: ModelProvider;
  confidence: number;
  latencyMs: number;
  tokenCount: number;
  success: boolean;
  errorCode?: AIErrorCode;
  cached: boolean;
  fallbackUsed: boolean;
}

export interface QualitySummary {
  period: { start: number; end: number };
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  averageConfidence: number;
  averageLatencyMs: number;
  cacheHitRate: number;
  fallbackUsageRate: number;
  errorBreakdown: Record<AIErrorCode, number>;
  providerDistribution: Record<ModelProvider, number>;
}

export interface ConfidenceScore {
  overall: number;
  factors: {
    tokenConfidence: number;
    lengthPenalty: number;
    errorIndicatorPenalty: number;
  };
  reasoning: string;
}

// ============================================================================
// Request Metadata
// ============================================================================

export interface RequestMetadata {
  cached: boolean;
  fallbackUsed: boolean;
  retryCount: number;
  timestamp: number;
  requestId?: string;
}
