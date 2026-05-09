/**
 * Typed API Client
 *
 * A shared typed API client with automatic tenant/locale headers.
 * Provides type-safe API calls with automatic authentication and context headers.
 *
 * @doc.type class
 * @doc.purpose Shared typed API client with automatic headers
 * @doc.layer platform
 * @doc.pattern Client
 */

export interface ApiClientConfig {
  baseURL: string;
  tenantId?: string;
  userId?: string;
  locale?: string;
  authToken?: string;
  defaultHeaders?: Record<string, string>;
  timeout?: number;
}

export interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  headers?: Record<string, string>;
  body?: unknown;
  query?: Record<string, string | number | boolean>;
  timeout?: number;
}

export interface ApiResponse<T = unknown> {
  data: T;
  status: number;
  statusText: string;
  headers: Headers;
}

export class TypedApiClient {
  private config: ApiClientConfig;

  constructor(config: ApiClientConfig) {
    this.config = {
      timeout: 30000,
      ...config,
    };
  }

  /**
   * Update client configuration
   */
  updateConfig(config: Partial<ApiClientConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): ApiClientConfig {
    return { ...this.config };
  }

  /**
   * Set tenant ID
   */
  setTenantId(tenantId: string): void {
    this.config.tenantId = tenantId;
  }

  /**
   * Set user ID
   */
  setUserId(userId: string): void {
    this.config.userId = userId;
  }

  /**
   * Set locale
   */
  setLocale(locale: string): void {
    this.config.locale = locale;
  }

  /**
   * Set auth token
   */
  setAuthToken(token: string): void {
    this.config.authToken = token;
  }

  /**
   * Build request headers with automatic tenant/locale headers
   */
  private buildHeaders(customHeaders?: Record<string, string>): Headers {
    const headers = new Headers(this.config.defaultHeaders);

    // Add automatic headers
    if (this.config.tenantId) {
      headers.set("X-Tenant-ID", this.config.tenantId);
    }
    if (this.config.userId) {
      headers.set("X-User-ID", this.config.userId);
    }
    if (this.config.locale) {
      headers.set("X-Locale", this.config.locale);
    }
    if (this.config.authToken) {
      headers.set("Authorization", `Bearer ${this.config.authToken}`);
    }

    // Add custom headers
    if (customHeaders) {
      Object.entries(customHeaders).forEach(([key, value]) => {
        headers.set(key, value);
      });
    }

    // Set default content type for requests with body
    if (this.config.defaultHeaders?.["Content-Type"] === undefined) {
      headers.set("Content-Type", "application/json");
    }

    return headers;
  }

  /**
   * Build URL with query parameters
   */
  private buildUrl(path: string, query?: Record<string, string | number | boolean>): string {
    const url = new URL(path, this.config.baseURL);

    if (query) {
      Object.entries(query).forEach(([key, value]) => {
        url.searchParams.set(key, String(value));
      });
    }

    return url.toString();
  }

  /**
   * Make an API request
   */
  private async request<T>(
    path: string,
    options: ApiRequestOptions = {},
  ): Promise<ApiResponse<T>> {
    const {
      method = "GET",
      headers: customHeaders,
      body,
      query,
      timeout = this.config.timeout,
    } = options;

    const url = this.buildUrl(path, query);
    const headers = this.buildHeaders(customHeaders);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
      const response = await fetch(url, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new ApiError(
          response.status,
          response.statusText,
          await response.text().catch(() => undefined),
        );
      }

      const data = await response.json();

      return {
        data,
        status: response.status,
        statusText: response.statusText,
        headers: response.headers,
      };
    } catch (error) {
      clearTimeout(timeoutId);

      if (error instanceof ApiError) {
        throw error;
      }

      if (error instanceof Error && error.name === "AbortError") {
        throw new ApiError(408, "Request Timeout", "The request timed out");
      }

      throw new ApiError(0, "Network Error", error instanceof Error ? error.message : "Unknown error");
    }
  }

  /**
   * GET request
   */
  async get<T>(path: string, options?: Omit<ApiRequestOptions, "method" | "body">): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...options, method: "GET" });
  }

  /**
   * POST request
   */
  async post<T>(path: string, body: unknown, options?: Omit<ApiRequestOptions, "method">): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...options, method: "POST", body });
  }

  /**
   * PUT request
   */
  async put<T>(path: string, body: unknown, options?: Omit<ApiRequestOptions, "method">): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...options, method: "PUT", body });
  }

  /**
   * PATCH request
   */
  async patch<T>(path: string, body: unknown, options?: Omit<ApiRequestOptions, "method">): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...options, method: "PATCH", body });
  }

  /**
   * DELETE request
   */
  async delete<T>(path: string, options?: Omit<ApiRequestOptions, "method" | "body">): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...options, method: "DELETE" });
  }
}

/**
 * Custom API error class
 */
export class ApiError extends Error {
  constructor(
    public status: number,
    public statusText: string,
    public body?: string,
  ) {
    super(`API Error ${status}: ${statusText}`);
    this.name = "ApiError";
  }

  toJSON() {
    return {
      status: this.status,
      statusText: this.statusText,
      body: this.body,
      message: this.message,
    };
  }
}

// Singleton instance factory
let clientInstance: TypedApiClient | null = null;

export function createApiClient(config: ApiClientConfig): TypedApiClient {
  return new TypedApiClient(config);
}

export function getSharedApiClient(config?: ApiClientConfig): TypedApiClient {
  if (!clientInstance && config) {
    clientInstance = new TypedApiClient(config);
  } else if (config) {
    clientInstance?.updateConfig(config);
  }
  return clientInstance!;
}
