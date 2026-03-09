/**
 * HTTP Client for Java AI Service
 * 
 * This client provides:
 * - Type-safe interface to Java ML algorithms
 * - Retry logic with exponential backoff
 * - Request/response logging
 * - Mock support for testing and demos
 * - Error handling and validation
 */

import axios, { AxiosInstance, AxiosError } from 'axios';
import { v4 as uuidv4 } from 'uuid';

import type {
  AIServiceConfig,
  AnomalyDetectionRequest,
  AnomalyDetectionResponse,
  BaselineCalculationRequest,
  BaselineCalculationResponse,
  TrendAnalysisRequest,
  TrendAnalysisResponse,
  RiskScoringRequest,
  RiskScoringResponse,
  HealthCheckResponse,
  AIServiceError,
} from './types';

/**
 * Configuration defaults for AI Service client
 */
const DEFAULT_CONFIG: Partial<AIServiceConfig> = {
  timeout: 10000, // 10 seconds
  maxRetries: 3,
  retryBackoffMultiplier: 2,
  logRequests: false,
  logResponses: false,
};

/**
 * Mode for client operation
 */
export type ClientMode = 'http' | 'mock';

/**
 * HTTP Client for AI Service
 * 
 * Usage:
 * ```typescript
 * const client = new AIServiceClient({
 *   baseUrl: 'http://localhost:8080/api/ml',
 *   maxRetries: 3,
 * });
 * 
 * const result = await client.detectAnomalies({
 *   dataPoints: [1, 2, 3, 100, 4, 5],
 *   threshold: 0.7,
 * });
 * ```
 */
export class AIServiceClient {
  private httpClient: AxiosInstance;
  private config: Required<AIServiceConfig>;
  private mode: ClientMode = 'http';
  private mockResponses: Map<string, unknown> = new Map();

  constructor(config: AIServiceConfig, mode: ClientMode = 'http') {
    this.config = { ...DEFAULT_CONFIG, ...config } as Required<AIServiceConfig>;
    this.mode = mode;

    // Create axios instance with timeout
    this.httpClient = axios.create({
      baseURL: this.config.baseUrl,
      timeout: this.config.timeout,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'DevSecOps-AIServiceClient/1.0',
      },
    });

    // Add response interceptor for error handling
    this.httpClient.interceptors.response.use(
      (response: unknown) => response,
      (error: unknown) => this.handleError(error as AxiosError)
    );
  }

  /**
   * Set mode (http or mock)
   */
  setMode(mode: ClientMode): void {
    this.mode = mode;
  }

  /**
   * Get current mode
   */
  getMode(): ClientMode {
    return this.mode;
  }

  /**
   * Register a mock response for testing
   * 
   * Usage:
   * ```typescript
   * client.setMockResponse('detectAnomalies', {
   *   isAnomaly: true,
   *   scores: [0.1, 0.95],
   *   anomalies: [1],
   * });
   * ```
   */
  setMockResponse(endpoint: string, response: unknown): void {
    this.mockResponses.set(endpoint, response);
  }

  /**
   * Clear all mock responses
   */
  clearMockResponses(): void {
    this.mockResponses.clear();
  }

  /**
   * Health check endpoint
   */
  async healthCheck(): Promise<HealthCheckResponse> {
    if (this.mode === 'mock') {
      return {
        status: 'UP',
        timestamp: new Date().toISOString(),
      };
    }

    try {
      const response = await this.withRetry(() =>
        this.httpClient.get<HealthCheckResponse>('/health')
      );
      return (response as unknown as { data: HealthCheckResponse }).data;
    } catch (error) {
      console.error('Health check failed:', error);
      throw error;
    }
  }

  /**
   * Detect anomalies using Isolation Forest algorithm
   */
  async detectAnomalies(
    request: AnomalyDetectionRequest
  ): Promise<AnomalyDetectionResponse> {
    this.validateAnomalyDetectionRequest(request);

    const endpoint = 'detectAnomalies';
    if (this.mode === 'mock') {
      const mock = this.mockResponses.get(endpoint);
      if (mock) {
        return mock as AnomalyDetectionResponse;
      }
      return this.generateMockAnomalyResponse(request);
    }

    try {
      this.logRequest('POST', '/anomalies/detect', request);

      const response = await this.withRetry(() =>
        this.httpClient.post<AnomalyDetectionResponse>(
          '/anomalies/detect',
          request
        )
      );

      const typedResponse = response as unknown as { data: AnomalyDetectionResponse };
      this.logResponse('detectAnomalies', typedResponse.data);
      return typedResponse.data;
    } catch (error) {
      console.error('Anomaly detection failed:', error);
      throw error;
    }
  }

  /**
   * Calculate baseline from historical data
   */
  async calculateBaseline(
    request: BaselineCalculationRequest
  ): Promise<BaselineCalculationResponse> {
    this.validateBaselineCalculationRequest(request);

    const endpoint = 'calculateBaseline';
    if (this.mode === 'mock') {
      const mock = this.mockResponses.get(endpoint);
      if (mock) {
        return mock as BaselineCalculationResponse;
      }
      return this.generateMockBaselineResponse(request);
    }

    try {
      this.logRequest('POST', '/baseline/calculate', request);

      const response = await this.withRetry(() =>
        this.httpClient.post<BaselineCalculationResponse>(
          '/baseline/calculate',
          request
        )
      );

      const typedResponse = response as unknown as { data: BaselineCalculationResponse };
      this.logResponse('calculateBaseline', typedResponse.data);
      return typedResponse.data;
    } catch (error) {
      console.error('Baseline calculation failed:', error);
      throw error;
    }
  }

  /**
   * Analyze trends in time series
   */
  async analyzeTrends(
    request: TrendAnalysisRequest
  ): Promise<TrendAnalysisResponse> {
    this.validateTrendAnalysisRequest(request);

    const endpoint = 'analyzeTrends';
    if (this.mode === 'mock') {
      const mock = this.mockResponses.get(endpoint);
      if (mock) {
        return mock as TrendAnalysisResponse;
      }
      return this.generateMockTrendResponse(request);
    }

    try {
      this.logRequest('POST', '/trends/analyze', request);

      const response = await this.withRetry(() =>
        this.httpClient.post<TrendAnalysisResponse>('/trends/analyze', request)
      );

      const typedResponse = response as unknown as { data: TrendAnalysisResponse };
      this.logResponse('analyzeTrends', typedResponse.data);
      return typedResponse.data;
    } catch (error) {
      console.error('Trend analysis failed:', error);
      throw error;
    }
  }

  /**
   * Compute risk score from features
   */
  async computeScore(
    request: RiskScoringRequest
  ): Promise<RiskScoringResponse> {
    this.validateRiskScoringRequest(request);

    const endpoint = 'computeScore';
    if (this.mode === 'mock') {
      const mock = this.mockResponses.get(endpoint);
      if (mock) {
        return mock as RiskScoringResponse;
      }
      return this.generateMockRiskResponse(request);
    }

    try {
      this.logRequest('POST', '/scores/compute', request);

      const response = await this.withRetry(() =>
        this.httpClient.post<RiskScoringResponse>('/scores/compute', request)
      );

      const typedResponse = response as unknown as { data: RiskScoringResponse };
      this.logResponse('computeScore', typedResponse.data);
      return typedResponse.data;
    } catch (error) {
      console.error('Risk scoring failed:', error);
      throw error;
    }
  }

  // ============================================================================
  // Private Methods
  // ============================================================================

  /**
   * Retry logic with exponential backoff
   */
  private async withRetry<T>(
    operation: () => Promise<T>,
    attempt = 0
  ): Promise<T> {
    try {
      return await operation();
    } catch (error) {
      if (attempt < this.config.maxRetries) {
        const delay = Math.pow(this.config.retryBackoffMultiplier, attempt) * 100;
        console.warn(
          `Request failed, retrying in ${delay}ms (attempt ${attempt + 1}/${this.config.maxRetries})`
        );
        await new Promise((resolve) => setTimeout(resolve, delay));
        return this.withRetry(operation, attempt + 1);
      }
      throw error;
    }
  }

  /**
   * Handle axios errors
   */
  private handleError(error: AxiosError): never {
    if (error.response?.data) {
      const data = error.response.data as AIServiceError;
      const message = `AI Service Error: ${data.error} - ${data.message}`;
      console.error(message);
      throw new Error(message);
    }

    if (error.message) {
      console.error(`Request failed: ${error.message}`);
      throw new Error(`Request failed: ${error.message}`);
    }

    throw error;
  }

  /**
   * Validate anomaly detection request
   */
  private validateAnomalyDetectionRequest(
    request: AnomalyDetectionRequest
  ): void {
    if (!request.dataPoints || request.dataPoints.length < 10) {
      throw new Error('dataPoints must have at least 10 elements');
    }
    if (request.threshold !== undefined) {
      if (request.threshold < 0 || request.threshold > 1) {
        throw new Error('threshold must be between 0 and 1');
      }
    }
  }

  /**
   * Validate baseline calculation request
   */
  private validateBaselineCalculationRequest(
    request: BaselineCalculationRequest
  ): void {
    if (!request.historicalData || request.historicalData.length < 20) {
      throw new Error('historicalData must have at least 20 elements');
    }
  }

  /**
   * Validate trend analysis request
   */
  private validateTrendAnalysisRequest(request: TrendAnalysisRequest): void {
    if (!request.timeSeries || request.timeSeries.length < 10) {
      throw new Error('timeSeries must have at least 10 elements');
    }
  }

  /**
   * Validate risk scoring request
   */
  private validateRiskScoringRequest(request: RiskScoringRequest): void {
    if (!request.features || Object.keys(request.features).length === 0) {
      throw new Error('features must not be empty');
    }
  }

  /**
   * Generate mock anomaly detection response
   */
  private generateMockAnomalyResponse(
    request: AnomalyDetectionRequest
  ): AnomalyDetectionResponse {
    const scores = request.dataPoints.map((value, index) => {
      // Generate realistic anomaly scores
      // Last value is typically 5% chance to be anomaly
      if (index === request.dataPoints.length - 1) {
        return Math.random() > 0.95 ? 0.85 : 0.15;
      }
      return Math.random() * 0.2; // Most are low
    });

    const threshold = request.threshold || 0.7;
    const anomalies = scores
      .map((score, index) => (score > threshold ? index : -1))
      .filter((index) => index !== -1);

    return {
      isAnomaly: anomalies.length > 0,
      scores,
      anomalies,
      anomalyIndices: anomalies.map((index) => ({
        index,
        value: request.dataPoints[index],
        score: scores[index],
      })),
      meanScore: scores.reduce((a, b) => a + b, 0) / scores.length,
      maxScore: Math.max(...scores),
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Generate mock baseline response
   */
  private generateMockBaselineResponse(
    request: BaselineCalculationRequest
  ): BaselineCalculationResponse {
    const sorted = [...request.historicalData].sort((a, b) => a - b);
    const mean = sorted.reduce((a, b) => a + b, 0) / sorted.length;
    const median =
      sorted.length % 2 === 0
        ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
        : sorted[Math.floor(sorted.length / 2)];

    const variance =
      sorted.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) /
      sorted.length;
    const stdDev = Math.sqrt(variance);

    return {
      baseline: median,
      mean,
      median,
      variance,
      stdDev,
      min: Math.min(...sorted),
      max: Math.max(...sorted),
      percentiles: {
        25: sorted[Math.floor(sorted.length * 0.25)],
        50: median,
        75: sorted[Math.floor(sorted.length * 0.75)],
        90: sorted[Math.floor(sorted.length * 0.9)],
        95: sorted[Math.floor(sorted.length * 0.95)],
        99: sorted[Math.floor(sorted.length * 0.99)],
      },
      dataPoints: sorted.length,
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Generate mock trend response
   */
  private generateMockTrendResponse(
    request: TrendAnalysisRequest
  ): TrendAnalysisResponse {
    const timeSeries = request.timeSeries;
    const firstHalf = timeSeries.slice(0, Math.floor(timeSeries.length / 2));
    const secondHalf = timeSeries.slice(Math.floor(timeSeries.length / 2));

    const firstAvg = firstHalf.reduce((a, b) => a + b, 0) / firstHalf.length;
    const secondAvg =
      secondHalf.reduce((a, b) => a + b, 0) / secondHalf.length;

    let trend: 'UP' | 'DOWN' | 'STABLE' = 'STABLE';
    if (secondAvg > firstAvg * 1.1) trend = 'UP';
    else if (secondAvg < firstAvg * 0.9) trend = 'DOWN';

    return {
      trend,
      strength: Math.abs(secondAvg - firstAvg) / firstAvg,
      slope:
        (timeSeries[timeSeries.length - 1] - timeSeries[0]) /
        timeSeries.length,
      trendChangePoints: [],
      movingAverage: timeSeries.slice(1),
      confidenceScore: 0.85,
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Generate mock risk response
   */
  private generateMockRiskResponse(
    request: RiskScoringRequest
  ): RiskScoringResponse {
    const componentScores: Record<string, number> = {};
    let totalScore = 0;

    Object.entries(request.features).forEach(([key, value]) => {
      const normalized = Math.min(1, Math.abs(value) / 10);
      componentScores[key] = normalized;
      totalScore += normalized;
    });

    const score = totalScore / Object.keys(request.features).length;
    let riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' = 'LOW';
    if (score > 0.75) riskLevel = 'CRITICAL';
    else if (score > 0.5) riskLevel = 'HIGH';
    else if (score > 0.25) riskLevel = 'MEDIUM';

    return {
      score,
      componentScores,
      riskLevel,
      recommendation: `Risk level is ${riskLevel}. Monitor closely.`,
      factors: Object.keys(componentScores),
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Log request if logging enabled
   */
  private logRequest(
    method: string,
    path: string,
    data: unknown
  ): void {
    if (this.config.logRequests) {
      console.log(`[AIServiceClient] ${method} ${path}`, JSON.stringify(data));
    }
  }

  /**
   * Log response if logging enabled
   */
  private logResponse(endpoint: string, data: unknown): void {
    if (this.config.logResponses) {
      console.log(
        `[AIServiceClient] Response from ${endpoint}`,
        JSON.stringify(data)
      );
    }
  }
}

/**
 * Factory function to create AI Service client
 */
export function createAIServiceClient(
  baseUrl: string,
  mode: ClientMode = 'http'
): AIServiceClient {
  return new AIServiceClient({ baseUrl }, mode);
}

/**
 * Factory function for testing with mock client
 */
export function createMockAIServiceClient(): AIServiceClient {
  return new AIServiceClient(
    { baseUrl: 'http://localhost:8080/api/ml' },
    'mock'
  );
}
