/**
 * Shared API Client Library
 *
 * Provides centralized HTTP client functionality with:
 * - Consistent authentication handling
 * - Standardized error handling
 * - Retry logic and circuit breaking
 * - Type safety
 * - Performance monitoring
 */

import { getConfig } from "../config/config.js";
import { createLogger, securityLogger } from "../utils/logger.js";

type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
type RequestBody = RequestInit["body"];

export interface ApiClientConfig {
  baseURL?: string;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
  enableCircuitBreaker?: boolean;
  enableMetrics?: boolean;
}

export interface RequestConfig {
  url?: string;
  method?: HttpMethod;
  headers?: Record<string, string>;
  body?: RequestBody | null;
  data?: unknown;
  signal?: AbortSignal;
  skipAuth?: boolean;
  skipRetry?: boolean;
  customHeaders?: Record<string, string>;
  metadata?: {
    requestId?: string;
    startTime?: number;
  };
}

export interface ApiResponse<T = unknown> {
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
  response?: ApiResponse<unknown>;
  config?: RequestConfig;
  isRetryable?: boolean;
}

class CircuitBreaker {
  private failures = 0;
  private lastFailureTime = 0;
  private state: "CLOSED" | "OPEN" | "HALF_OPEN" = "CLOSED";

  constructor(
    private readonly threshold: number = 5,
    private readonly timeout: number = 60000,
  ) {}

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    if (this.state === "OPEN") {
      if (Date.now() - this.lastFailureTime > this.timeout) {
        this.state = "HALF_OPEN";
      } else {
        throw new Error("Circuit breaker is OPEN");
      }
    }

    try {
      const result = await operation();
      if (this.state === "HALF_OPEN") {
        this.reset();
      }
      return result;
    } catch (error) {
      this.recordFailure();
      throw error;
    }
  }

  private recordFailure(): void {
    this.failures += 1;
    this.lastFailureTime = Date.now();
    if (this.failures >= this.threshold) {
      this.state = "OPEN";
    }
  }

  private reset(): void {
    this.failures = 0;
    this.state = "CLOSED";
  }

  getState(): string {
    return this.state;
  }
}

export class SharedApiClient {
  private logger = createLogger("api-client");
  private circuitBreakers = new Map<string, CircuitBreaker>();
  private config: Required<ApiClientConfig>;

  constructor(config: ApiClientConfig = {}) {
    this.config = {
      baseURL: config.baseURL ?? getConfig().AI_SERVICE_URL,
      timeout: config.timeout ?? 30000,
      retries: config.retries ?? 3,
      retryDelay: config.retryDelay ?? 1000,
      enableCircuitBreaker: config.enableCircuitBreaker ?? true,
      enableMetrics: config.enableMetrics ?? true,
    };
  }

  private async executeRequest<T>(
    config: RequestConfig,
  ): Promise<ApiResponse<T>> {
    const requestUrl = config.url ?? "unknown";
    const circuitBreaker = this.getCircuitBreaker(requestUrl);

    const operation = async (): Promise<ApiResponse<T>> => {
      let lastError: ApiError | undefined;

      for (let attempt = 1; attempt <= this.config.retries; attempt += 1) {
        const requestId = this.generateRequestId();
        const startTime = Date.now();
        const requestConfig: RequestConfig = {
          ...config,
          metadata: {
            ...config.metadata,
            requestId,
            startTime,
          },
        };

        try {
          const response = await this.performFetch<T>(requestConfig);
          return response;
        } catch (error) {
          lastError = this.enhanceError(error, requestConfig);

          if (
            requestConfig.skipRetry ||
            !lastError.isRetryable ||
            attempt === this.config.retries
          ) {
            throw lastError;
          }

          const delay = this.config.retryDelay * Math.pow(2, attempt - 1);
          await this.sleep(delay);
        }
      }

      throw lastError ?? new Error("Request failed without an error payload");
    };

    if (this.config.enableCircuitBreaker) {
      return circuitBreaker.execute(operation);
    }

    return operation();
  }

  private async performFetch<T>(
    config: RequestConfig,
  ): Promise<ApiResponse<T>> {
    const url = this.resolveUrl(config.url ?? "");
    const headers = this.buildHeaders(config);
    const requestInit = this.buildRequestInit(config, headers);
    const timeoutSignal = AbortSignal.timeout(this.config.timeout);
    const signal = config.signal
      ? AbortSignal.any([config.signal, timeoutSignal])
      : timeoutSignal;

    this.logger.debug(
      {
        ...(config.metadata?.requestId
          ? { requestId: config.metadata.requestId }
          : {}),
        ...(config.method ? { method: config.method } : {}),
        url,
        headers: this.sanitizeHeaders(headers),
      },
      "API request started",
    );

    let response: Response;
    try {
      response = await fetch(url, { ...requestInit, signal });
    } catch (error) {
      this.logger.error(
        {
          ...(config.metadata?.requestId
            ? { requestId: config.metadata.requestId }
            : {}),
          ...(config.method ? { method: config.method } : {}),
          url,
          error: error instanceof Error ? error.message : "Unknown error",
        },
        "API request failed",
      );
      throw error;
    }

    const duration = config.metadata?.startTime
      ? Date.now() - config.metadata.startTime
      : undefined;
    const apiResponse = await this.toApiResponse<T>(response, duration);

    this.logger.debug(
      {
        ...(config.metadata?.requestId
          ? { requestId: config.metadata.requestId }
          : {}),
        status: response.status,
        ...(duration !== undefined ? { duration } : {}),
        url,
      },
      "API request completed",
    );

    if (this.config.enableMetrics && duration !== undefined) {
      this.recordMetrics(url, duration, response.status);
    }

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        securityLogger.logAuthzEvent("access_denied", {
          resource: url,
          ...(config.method ? { action: config.method } : {}),
          reason:
            typeof apiResponse.data === "string"
              ? apiResponse.data
              : response.statusText,
        });
      }

      const error = new Error(
        `Request failed with status ${response.status}`,
      ) as ApiError;
      error.status = response.status;
      error.statusText = response.statusText;
      error.response = apiResponse as ApiResponse<unknown>;
      error.config = config;
      error.isRetryable = this.isRetryableStatus(response.status);
      throw error;
    }

    return apiResponse;
  }

  async get<T>(
    url: string,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: "GET", url });
  }

  async post<T>(
    url: string,
    data?: unknown,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: "POST", url, data });
  }

  async put<T>(
    url: string,
    data?: unknown,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: "PUT", url, data });
  }

  async patch<T>(
    url: string,
    data?: unknown,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: "PATCH", url, data });
  }

  async delete<T>(
    url: string,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    return this.executeRequest<T>({ ...config, method: "DELETE", url });
  }

  async healthCheck(): Promise<boolean> {
    try {
      await this.get("/health");
      return true;
    } catch (error) {
      this.logger.error(
        {
          error: error instanceof Error ? error.message : "Unknown error",
        },
        "API client health check failed",
      );
      return false;
    }
  }

  getCircuitBreakerStatus(): Record<string, string> {
    const status: Record<string, string> = {};
    for (const [url, breaker] of this.circuitBreakers.entries()) {
      status[url] = breaker.getState();
    }
    return status;
  }

  private buildHeaders(config: RequestConfig): Record<string, string> {
    return {
      "Content-Type": "application/json",
      "User-Agent": "TutorPutor-Platform/1.0.0",
      "X-Request-ID": config.metadata?.requestId ?? this.generateRequestId(),
      ...(config.headers ?? {}),
      ...(config.customHeaders ?? {}),
    };
  }

  private buildRequestInit(
    config: RequestConfig,
    headers: Record<string, string>,
  ): RequestInit {
    const body = this.toRequestBody(config.data, config.body, headers);
    if (body instanceof FormData) {
      delete headers["Content-Type"];
    }

    return {
      ...(config.method ? { method: config.method } : {}),
      headers,
      ...(body !== undefined ? { body } : {}),
    };
  }

  private toRequestBody(
    data: unknown,
    body: RequestBody | null | undefined,
    headers: Record<string, string>,
  ): RequestInit["body"] {
    if (body !== undefined) {
      return body;
    }

    if (data === undefined) {
      return undefined;
    }

    if (
      typeof data === "string" ||
      data instanceof Blob ||
      data instanceof FormData ||
      data instanceof URLSearchParams ||
      data instanceof ArrayBuffer ||
      ArrayBuffer.isView(data)
    ) {
      return data as RequestBody;
    }

    headers["Content-Type"] = "application/json";
    return JSON.stringify(data);
  }

  private async toApiResponse<T>(
    response: Response,
    duration?: number,
  ): Promise<ApiResponse<T>> {
    const contentType = response.headers.get("content-type") ?? "";
    let data: T;

    if (contentType.includes("application/json")) {
      data = (await response.json()) as T;
    } else {
      data = (await response.text()) as T;
    }

    const result: ApiResponse<T> = {
      data,
      status: response.status,
      statusText: response.statusText,
      headers: Object.fromEntries(response.headers.entries()),
    };
    const requestId = response.headers.get("x-request-id");
    if (requestId) {
      result.requestId = requestId;
    }
    if (duration !== undefined) {
      result.duration = duration;
    }

    return result;
  }

  private enhanceError(error: any, config: RequestConfig): ApiError {
    if (error instanceof Error && this.isApiError(error)) {
      return error;
    }

    const apiError = new Error(
      error instanceof Error ? error.message : "Unknown request error",
    ) as ApiError;
    apiError.config = config;
    apiError.isRetryable = true;
    return apiError;
  }

  private isApiError(error: Error): error is ApiError {
    return "isRetryable" in error || "status" in error;
  }

  private isRetryableStatus(status: number): boolean {
    return status === 408 || status === 429 || status >= 500;
  }

  private getCircuitBreaker(url: string): CircuitBreaker {
    const existing = this.circuitBreakers.get(url);
    if (existing) {
      return existing;
    }

    const breaker = new CircuitBreaker();
    this.circuitBreakers.set(url, breaker);
    return breaker;
  }

  private resolveUrl(path: string): string {
    if (/^https?:\/\//.test(path)) {
      return path;
    }

    try {
      return new URL(path, this.config.baseURL).toString();
    } catch {
      return `${this.config.baseURL.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
    }
  }

  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;
  }

  private sanitizeHeaders(
    headers: Record<string, string>,
  ): Record<string, string> {
    const sanitized = { ...headers };
    for (const header of ["authorization", "x-api-key", "cookie"]) {
      if (sanitized[header]) {
        sanitized[header] = "[REDACTED]";
      }
    }
    return sanitized;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  private recordMetrics(url: string, duration: number, status: number): void {
    this.logger.debug(
      {
        url,
        duration,
        status,
        category: "performance",
      },
      "API request metrics",
    );
  }
}

export function createApiClient(config: ApiClientConfig = {}): SharedApiClient {
  return new SharedApiClient(config);
}

export class AIServiceClient extends SharedApiClient {
  constructor() {
    super({
      baseURL: getConfig().AI_SERVICE_URL,
      timeout: 60000,
      retries: 2,
      enableCircuitBreaker: true,
    });
  }

  async generateContent(prompt: string, options: Record<string, unknown> = {}) {
    return this.post("/ai/generate", { prompt, ...options });
  }

  async validateContent(content: any) {
    return this.post("/ai/validate", content);
  }
}

export class SimulationServiceClient extends SharedApiClient {
  constructor() {
    super({
      baseURL: getConfig().SIM_RUNTIME_URL,
      timeout: 120000,
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

export const apiClient = createApiClient();
export const aiServiceClient = new AIServiceClient();
export const simulationServiceClient = new SimulationServiceClient();
