/**
 * TypeScript type definitions matching Java AI Service contract
 * 
 * These types are generated from the OpenAPI specification and Protobuf definitions.
 * They ensure type-safe communication between TypeScript backend and Java AI service.
 */

// ============================================================================
// Anomaly Detection Types
// ============================================================================

/**
 * Request to detect anomalies in time-series data using Isolation Forest
 */
export interface AnomalyDetectionRequest {
  /** Time series data points to analyze (minimum 10 points) */
  dataPoints: number[];
  
  /** Anomaly threshold (0-1), default 0.7. Higher = more anomalies flagged */
  threshold?: number;
  
  /** Expected contamination ratio (0-1), default 0.1 */
  contamination?: number;
  
  /** Optional metadata for tracking/logging */
  metadata?: Record<string, unknown>;
}

/**
 * Response containing anomaly detection results
 */
export interface AnomalyDetectionResponse {
  /** Overall anomaly result (true if any point above threshold) */
  isAnomaly: boolean;
  
  /** Anomaly score for each data point (0-1, higher = more anomalous) */
  scores: number[];
  
  /** Indices of anomalous data points */
  anomalies: number[];
  
  /** Detailed anomaly information */
  anomalyIndices?: AnomalyDetail[];
  
  /** Mean anomaly score across all points */
  meanScore: number;
  
  /** Maximum anomaly score */
  maxScore: number;
  
  /** Number of anomalies detected */
  anomalyCount?: number;
  
  /** Server timestamp */
  timestamp: string;
}

export interface AnomalyDetail {
  /** Index in data array */
  index: number;
  
  /** Value at this index */
  value: number;
  
  /** Anomaly score for this point */
  score: number;
}

// ============================================================================
// Baseline Calculation Types
// ============================================================================

/**
 * Request to calculate baseline from historical data
 */
export interface BaselineCalculationRequest {
  /** Historical data points for baseline calculation (minimum 20 points) */
  historicalData: number[];
  
  /** Moving average window size, default 5 */
  window?: number;
  
  /** Percentiles to calculate, default [25, 50, 75, 90, 95, 99] */
  percentiles?: number[];
  
  /** Optional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Response containing baseline statistics
 */
export interface BaselineCalculationResponse {
  /** Calculated baseline value (median or mean) */
  baseline: number;
  
  /** Mean of historical data */
  mean: number;
  
  /** Median of historical data */
  median: number;
  
  /** Variance of historical data */
  variance: number;
  
  /** Standard deviation */
  stdDev: number;
  
  /** Minimum value */
  min: number;
  
  /** Maximum value */
  max: number;
  
  /** Calculated percentiles (key = percentile, value = calculated value) */
  percentiles: Record<number, number>;
  
  /** Number of data points used */
  dataPoints: number;
  
  /** Server timestamp */
  timestamp: string;
}

// ============================================================================
// Trend Analysis Types
// ============================================================================

/**
 * Request to analyze trends in time series
 */
export interface TrendAnalysisRequest {
  /** Time series data for analysis (minimum 10 points) */
  timeSeries: number[];
  
  /** Period for moving average and trend detection, default 3 */
  period?: number;
  
  /** Confidence level (0.8-0.99), default 0.95 */
  confidenceLevel?: number;
  
  /** Optional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Response containing trend analysis results
 */
export interface TrendAnalysisResponse {
  /** Identified trend direction: UP, DOWN, or STABLE */
  trend: TrendDirection;
  
  /** Trend strength (0-1, confidence level) */
  strength: number;
  
  /** Linear regression slope */
  slope: number;
  
  /** Indices where trend changes occur */
  trendChangePoints: number[];
  
  /** Moving average values */
  movingAverage: number[];
  
  /** Confidence score for trend detection (0-1) */
  confidenceScore: number;
  
  /** Server timestamp */
  timestamp: string;
}

export type TrendDirection = 'UP' | 'DOWN' | 'STABLE';

// ============================================================================
// Risk Scoring Types
// ============================================================================

/**
 * Request to compute risk score
 */
export interface RiskScoringRequest {
  /** Feature values for risk scoring */
  features: Record<string, number>;
  
  /** Optional weights for features (default equal weights) */
  weights?: Record<string, number>;
  
  /** Normalize scores to 0-1 range, default true */
  normalize?: boolean;
  
  /** Optional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Response containing risk score and analysis
 */
export interface RiskScoringResponse {
  /** Overall risk score (0-1, higher = more risky) */
  score: number;
  
  /** Individual component scores for explainability */
  componentScores: Record<string, number>;
  
  /** Risk level category */
  riskLevel: RiskLevel;
  
  /** Recommended action based on risk */
  recommendation: string;
  
  /** Contributing factors to risk score */
  factors: string[];
  
  /** Server timestamp */
  timestamp: string;
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

// ============================================================================
// Health Check Types
// ============================================================================

/**
 * Response from health check endpoint
 */
export interface HealthCheckResponse {
  /** Service status: UP, DOWN, DEGRADED */
  status: ServiceStatus;
  
  /** Timestamp of health check */
  timestamp: string;
}

export type ServiceStatus = 'UP' | 'DOWN' | 'DEGRADED';

// ============================================================================
// Error Types
// ============================================================================

/**
 * Error response from Java service
 */
export interface AIServiceError {
  /** Error code/type */
  error: string;
  
  /** Human-readable error message */
  message: string;
  
  /** Trace ID for debugging */
  traceId?: string;
  
  /** Timestamp of error */
  timestamp: string;
}

// ============================================================================
// Configuration Types
// ============================================================================

/**
 * Configuration for AI Service client
 */
export interface AIServiceConfig {
  /** Base URL of Java AI service */
  baseUrl: string;
  
  /** Request timeout in milliseconds */
  timeout?: number;
  
  /** Maximum number of retries */
  maxRetries?: number;
  
  /** Retry backoff multiplier */
  retryBackoffMultiplier?: number;
  
  /** Enable request logging */
  logRequests?: boolean;
  
  /** Enable response logging */
  logResponses?: boolean;
}

// ============================================================================
// Unified Request/Response Types
// ============================================================================

/**
 * Generic AI Service request (used for client factory)
 */
export type AIServiceRequest =
  | AnomalyDetectionRequest
  | BaselineCalculationRequest
  | TrendAnalysisRequest
  | RiskScoringRequest;

/**
 * Generic AI Service response (used for client factory)
 */
export type AIServiceResponse =
  | AnomalyDetectionResponse
  | BaselineCalculationResponse
  | TrendAnalysisResponse
  | RiskScoringResponse;

// ============================================================================
// Request/Response Metadata
// ============================================================================

/**
 * Common metadata included in all requests
 */
export interface RequestMetadata {
  /** Unique request ID for tracking */
  requestId?: string;
  
  /** Request timestamp */
  timestamp?: string;
  
  /** Tenant ID (for multi-tenant isolation) */
  tenantId?: string;
  
  /** User ID making the request */
  userId?: string;
  
  /** Trace context for distributed tracing */
  traceContext?: {
    traceId: string;
    spanId: string;
    parentSpanId?: string;
  };
}

/**
 * Common metadata included in all responses
 */
export interface ResponseMetadata {
  /** Timestamp of response */
  timestamp: string;
  
  /** Request ID (echoed from request) */
  requestId?: string;
  
  /** Processing time in milliseconds */
  processingTimeMs?: number;
  
  /** Service version that processed the request */
  serviceVersion?: string;
}
