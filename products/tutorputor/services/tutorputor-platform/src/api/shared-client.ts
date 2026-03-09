/**
 * Shared API Client Library
 * 
 * Provides centralized HTTP client functionality with:
 * - Consistent authentication handling
 * - Standardized error handling
 * - Request/response interceptors
 * - Retry logic and circuit breaking
 * - Type safety
 * - Performance monitoring
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { getConfig } from '../config/config.js';
import { createLogger } from '../utils/logger.js';
import { securityLogger } from '../utils/logger.js';

export interface ApiClientConfig {
  baseURL?: string;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
  enableCircuitBreaker?: boolean;
  enableMetrics?: boolean;
}

export interface RequestConfig extends AxiosRequestConfig {
  skipAuth?: boolean;
  skipRetry?: boolean;
  customHeaders?: Record<string, string>;
  metadata?: {
    requestId?: string;
    startTime?: number;
  };
}

export interface ApiResponse<T = any> {
  data: T;
  status: number;
  statusText: string;
  headers: Record<string, string>;
  requestId?: string;
  duration?: number;
}

export interface ApiError extends Error {
  status?: number;
  statusText?: string;
  response?: ApiResponse;
  config?: RequestConfig;
  isRetryable?: boolean;
}

/**
 * Circuit Breaker Implementation
 */
class CircuitBreaker {
  private failures = 0;
  private lastFailureTime = 0;
  private state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';
  private readonly threshold: number;
  private readonly timeout: number;

  constructor(threshold: number = 5, timeout: number = 60000) {
    this.threshold = threshold;
    this.timeout = timeout;
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    if (this.state === 'OPEN') {
      if (Date.now() - this.lastFailureTime > this.timeout) {
        this.state = 'HALF_OPEN';
      } else {
        throw new Error('Circuit breaker is OPEN');
      }
    }

    try {
      const result = await operation();
      
      if (this.state === 'HALF_OPEN') {
        this.reset();
      }
      
      return result;
    } catch (error) {
      this.recordFailure();
      throw error;
    }
  }

  private recordFailure(): void {
    this.failures++;
    this.lastFailureTime = Date.now();
    
    if (this.failures >= this.threshold) {
      this.state = 'OPEN';
    }
  }

  private reset(): void {
    this.failures = 0;
    this.state = 'CLOSED';
  }

  getState(): string {
    return this.state;
  }
}

/**
 * Shared API Client
 */
export class SharedApiClient {
  private axiosInstance: AxiosInstance;
  private logger = createLogger('api-client');
  private circuitBreakers = new Map<string, CircuitBreaker>();
  private config: ApiClientConfig;

  constructor(config: ApiClientConfig = {}) {
    this.config = {
      timeout: 30000,
      retries: 3,
      retryDelay: 1000,
      enableCircuitBreaker: true,
      enableMetrics: true,
      ...config,
    };

    this.axiosInstance = axios.create({
      baseURL: this.config.baseURL || getConfig().AI_SERVICE_URL,
      timeout: this.config.timeout,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'TutorPutor-Platform/1.0.0',
      },
    });

    this.setupInterceptors();
  }

  /**
   * Setup request and response interceptors
   */
  private setupInterceptors(): void {
    // Request interceptor
    this.axiosInstance.interceptors.request.use(
      (config) => {
        const requestId = this.generateRequestId();
        (config as any).headers['X-Request-ID'] = requestId;
        (config as any).metadata = { requestId, startTime: Date.now() };

        this.logger.debug({
          requestId,
          method: config.method?.toUpperCase(),
          url: config.url,
          headers: this.sanitizeHeaders(config.headers),
        }, 'API request started');

        return config;
      },
      (error) => {
        this.logger.error({ error: error.message || 'Unknown error' }, 'Request interceptor error');
        return Promise.reject(error);
      }
    );

    // Response interceptor
    this.axiosInstance.interceptors.response.use(
      (response) => {
        const { requestId, startTime } = (response.config as any).metadata || {};
        const duration = startTime ? Date.now() - startTime : undefined;

        this.logger.debug({
          requestId,
          status: response.status,
          duration,
          url: response.config.url,
        }, 'API request completed');

        if (this.config.enableMetrics && duration) {
          this.recordMetrics(response.config.url || 'unknown', duration, response.status);
        }

        return response;
      },
      (error) => {
        const { requestId, startTime } = (error.config as any)?.metadata || {};
        const duration = startTime ? Date.now() - startTime : undefined;

        this.logger.error({
          requestId,
          status: error.response?.status,
          duration,
          url: error.config?.url,
          error: error.message,
        }, 'API request failed');

        // Log security violations
        if (error.response?.status === 401 || error.response?.status === 403) {
          securityLogger.logAuthzEvent('access_denied', {
            resource: error.config?.url,
            action: error.config?.method?.toUpperCase(),
            reason: error.message,
          });
        }

        return Promise.reject(this.enhanceError(error));
      }
    );
  }

  /**
   * Enhanced error handling
   */
  private enhanceError(error: AxiosError): ApiError {
    const apiError: ApiError = new Error(error.message) as ApiError;
    apiError.status = error.response?.status;
    apiError.statusText = error.response?.statusText;
    apiError.response = error.response?.data as ApiResponse;
    apiError.config = error.config as RequestConfig;
    apiError.isRetryable = this.isRetryableError(error);

    return apiError;
  }

  /**
   * Determine if error is retryable
   */
  private isRetryableError(error: AxiosError): boolean {
    if (!error.response) return true; // Network errors are retryable
    
    const status = error.response.status;
    return (
      status === 408 || // Request Timeout
      status === 429 || // Too Many Requests
      status >= 500     // Server errors
    );
  }

  /**
   * Execute request with retry logic and circuit breaking
   */
  private async executeRequest<T>(config: RequestConfig): Promise<ApiResponse<T>> {
    const url = config.url || 'unknown';
    const circuitBreaker = this.getCircuitBreaker(url);

    const operation = async () => {
      let lastError: ApiError;
      
      for (let attempt = 1; attempt <= (this.config.retries || 3); attempt++) {
        try {
          const response = await this.axiosInstance.request<ApiResponse<T>>(config as any);
          return response.data as ApiResponse<T>;
        } catch (error) {
          lastError = error as ApiError;
          
          // Don't retry if explicitly disabled or error is not retryable
          if (config.skipRetry || !lastError.isRetryable) {
            throw lastError;
          }

          // Don't retry on last attempt
          if (attempt === (this.config.retries || 3)) {
            throw lastError;
          }

          // Wait before retry
          const delay = (this.config.retryDelay || 1000) * Math.pow(2, attempt - 1);
          await this.sleep(delay);
        }
      }

      throw lastError!;
    };

    if (this.config.enableCircuitBreaker) {
      return circuitBreaker.execute(operation);
    }

    return operation();
  }

  /**
   * HTTP Methods
   */
  async get<T>(url: string, config: RequestConfig = {}): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: 'GET', url });
  }

  async post<T>(url: string, data?: any, config: RequestConfig = {}): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: 'POST', url, data });
  }

  async put<T>(url: string, data?: any, config: RequestConfig = {}): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: 'PUT', url, data });
  }

  async patch<T>(url: string, data?: any, config: RequestConfig = {}): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: 'PATCH', url, data });
  }

  async delete<T>(url: string, config: RequestConfig = {}): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: 'DELETE', url });
  }

  /**
   * Utility methods
   */
  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private sanitizeHeaders(headers: any): any {
    const sanitized = { ...headers };
    const sensitiveHeaders = ['authorization', 'x-api-key', 'cookie'];
    
    for (const header of sensitiveHeaders) {
      if (sanitized[header]) {
        sanitized[header] = '[REDACTED]';
      }
    }
    
    return sanitized;
  }

  private getCircuitBreaker(url: string): CircuitBreaker {
    if (!this.circuitBreakers.has(url)) {
      this.circuitBreakers.set(url, new CircuitBreaker());
    }
    return this.circuitBreakers.get(url)!;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  private recordMetrics(url: string, duration: number, status: number): void {
    // This would integrate with your metrics system
    // For now, just log the metrics
    this.logger.debug({
      url,
      duration,
      status,
      category: 'performance',
    }, 'API request metrics');
  }

  /**
   * Health check
   */
  async healthCheck(): Promise<boolean> {
    try {
      await this.get('/health');
      return true;
    } catch (error) {
      this.logger.error({ error: error instanceof Error ? error.message : 'Unknown error' }, 'API client health check failed');
      return false;
    }
  }

  /**
   * Get circuit breaker status
   */
  getCircuitBreakerStatus(): Record<string, string> {
    const status: Record<string, string> = {};
    for (const [url, breaker] of this.circuitBreakers.entries()) {
      status[url] = breaker.getState();
    }
    return status;
  }
}

/**
 * API Client Factory
 */
export function createApiClient(config: ApiClientConfig = {}): SharedApiClient {
  return new SharedApiClient(config);
}

/**
 * Specialized API clients
 */
export class AIServiceClient extends SharedApiClient {
  constructor() {
    super({
      baseURL: getConfig().AI_SERVICE_URL,
      timeout: 60000, // AI services might take longer
      retries: 2,
      enableCircuitBreaker: true,
    });
  }

  async generateContent(prompt: string, options: any = {}) {
    return this.post('/ai/generate', { prompt, ...options });
  }

  async validateContent(content: any) {
    return this.post('/ai/validate', content);
  }
}

export class SimulationServiceClient extends SharedApiClient {
  constructor() {
    super({
      baseURL: getConfig().SIM_RUNTIME_URL,
      timeout: 120000, // Simulations can be long-running
      retries: 1,
      enableCircuitBreaker: true,
    });
  }

  async runSimulation(simulationId: string, parameters: any) {
    return this.post(`/simulations/${simulationId}/run`, parameters);
  }

  async getSimulationState(simulationId: string) {
    return this.get(`/simulations/${simulationId}/state`);
  }
}

// Export singleton instances
export const apiClient = createApiClient();
export const aiServiceClient = new AIServiceClient();
export const simulationServiceClient = new SimulationServiceClient();
