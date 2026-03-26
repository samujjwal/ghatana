import {
  ApiError,
  ApiRequest,
  ApiResponse,
  ApiClientOptions,
  RequestMiddleware,
  ResponseMiddleware,
  ApiRequestInit,
  ApiErrorCategory,
} from "./types";

function mergeHeaders(
  base: Record<string, string> | undefined,
  override: Record<string, string> | undefined,
): Record<string, string> {
  return { ...(base ?? {}), ...(override ?? {}) };
}

function buildUrl(baseUrl: string | undefined, path: string): URL {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return new URL(path);
  }

  const origin =
    baseUrl ??
    (typeof window !== "undefined" ? window.location.origin : undefined);

  if (!origin) {
    return new URL(path, "http://localhost");
  }

  return new URL(path, origin);
}

async function parseBody<T>(response: Response): Promise<T> {
  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  if (contentType && contentType.startsWith("text/")) {
    return (await response.text()) as unknown as T;
  }
  return (await response.arrayBuffer()) as unknown as T;
}

/**
 * Classify an HTTP status code into an error category and determine
 * whether the request should be retried.
 */
function classifyStatus(status: number): {
  category: ApiErrorCategory;
  isRetryable: boolean;
} {
  if (status >= 500) {
    return { category: "SERVER", isRetryable: true };
  }
  return { category: "CLIENT", isRetryable: false };
}

export class ApiClient {
  private readonly requestMiddleware: RequestMiddleware[] = [];
  private readonly responseMiddleware: ResponseMiddleware[] = [];

  constructor(private readonly options: ApiClientOptions = {}) {}

  useRequest(middleware: RequestMiddleware): () => void {
    this.requestMiddleware.push(middleware);
    return () => {
      const index = this.requestMiddleware.indexOf(middleware);
      if (index >= 0) {
        this.requestMiddleware.splice(index, 1);
      }
    };
  }

  useResponse(middleware: ResponseMiddleware): () => void {
    this.responseMiddleware.push(middleware);
    return () => {
      const index = this.responseMiddleware.indexOf(middleware);
      if (index >= 0) {
        this.responseMiddleware.splice(index, 1);
      }
    };
  }

  async request<T = unknown>(input: ApiRequest): Promise<ApiResponse<T>> {
    const attemptLimit = this.options.retry?.attempts ?? 1;
    let attempt = 0;
    let lastError: ApiError | null = null;

    while (attempt < attemptLimit) {
      try {
        const processed = await this.runRequestMiddleware({
          ...input,
          method: input.method ?? "GET",
        });
        const result = await this.executeRequest<T>(processed);
        return await this.runResponseMiddleware(result, processed);
      } catch (error) {
        const apiError = error as ApiError;
        lastError = apiError;
        attempt += 1;

        // Do not retry 4xx client errors — the request is invalid and retrying
        // will not help. Only network errors and 5xx server errors are retryable.
        if (!apiError.isRetryable) {
          break;
        }

        if (attempt >= attemptLimit) {
          break;
        }

        // Exponential backoff: baseMs * 2^attempt (1×, 2×, 4×, …)
        const baseMs = this.options.retry?.backoffMs ?? 250;
        const delay = baseMs * Math.pow(2, attempt - 1);
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }

    throw lastError ?? new Error("Request failed");
  }

  get<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "GET", ...init });
  }

  post<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "POST", ...init });
  }

  put<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "PUT", ...init });
  }

  patch<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "PATCH", ...init });
  }

  delete<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "DELETE", ...init });
  }

  private async runRequestMiddleware(request: ApiRequest): Promise<ApiRequest> {
    let current: ApiRequest = {
      ...request,
      headers: mergeHeaders(this.options.defaultHeaders, request.headers),
      timeoutMs: request.timeoutMs ?? this.options.timeoutMs,
    };

    // Inject API version header when configured
    if (this.options.apiVersion && !current.headers?.["X-Api-Version"]) {
      current = {
        ...current,
        headers: {
          ...(current.headers ?? {}),
          "X-Api-Version": this.options.apiVersion,
        },
      };
    }

    for (const middleware of this.requestMiddleware) {
      current = await middleware(current);
    }

    return current;
  }

  private async runResponseMiddleware<T>(
    response: ApiResponse<T>,
    request: ApiRequest,
  ): Promise<ApiResponse<T>> {
    let current: ApiResponse<unknown> = response;
    for (const middleware of this.responseMiddleware) {
      current = await middleware(current, request);
    }
    return current as ApiResponse<T>;
  }

  private async executeRequest<T>(
    request: ApiRequest,
  ): Promise<ApiResponse<T>> {
    const url = buildUrl(this.options.baseUrl, request.url);
    const query = request.query;

    if (query) {
      Object.entries(query).forEach(([key, value]) => {
        if (value === undefined || value === null) return;
        url.searchParams.set(key, String(value));
      });
    }

    const controller =
      typeof AbortController !== "undefined" ? new AbortController() : null;
    const timeout = request.timeoutMs ?? this.options.timeoutMs;
    let timer: ReturnType<typeof setTimeout> | undefined;

    if (controller && timeout) {
      timer = setTimeout(() => controller.abort(), timeout);
    }

    try {
      const headers = new Headers(request.headers);
      let body: BodyInit | undefined;

      if (request.body !== undefined && request.body !== null) {
        if (
          typeof request.body === "string" ||
          request.body instanceof Blob ||
          request.body instanceof FormData
        ) {
          body = request.body as BodyInit;
        } else {
          headers.set(
            "Content-Type",
            headers.get("Content-Type") ?? "application/json",
          );
          body = JSON.stringify(request.body);
        }
      }

      const response = await fetch(url, {
        method: request.method as RequestInit["method"],
        body,
        headers,
        signal: controller?.signal,
      } as RequestInit);

      const data = await parseBody<T>(response);

      const apiResponse: ApiResponse<T> = {
        status: response.status,
        headers: response.headers,
        data,
        raw: response,
      };

      if (!response.ok) {
        const { category, isRetryable } = classifyStatus(response.status);
        const error: ApiError = Object.assign(new Error(response.statusText), {
          status: response.status,
          response: apiResponse as ApiResponse<unknown>,
          request,
          category,
          isRetryable,
        });
        throw error;
      }

      return apiResponse;
    } catch (error) {
      if (
        error instanceof Error &&
        (error as ApiError).category !== undefined
      ) {
        // Already a categorised ApiError — rethrow as-is
        throw error;
      }
      // Network-level error (no response): NETWORK category, retryable
      const apiError: ApiError = Object.assign(
        error instanceof Error ? error : new Error("Request failed"),
        {
          request,
          category: "NETWORK" as ApiErrorCategory,
          isRetryable: true,
        },
      );
      throw apiError;
    } finally {
      if (timer) {
        clearTimeout(timer);
      }
    }
  }
}

function mergeHeaders(
  base: Record<string, string> | undefined,
  override: Record<string, string> | undefined,
): Record<string, string> {
  return { ...(base ?? {}), ...(override ?? {}) };
}

function buildUrl(baseUrl: string | undefined, path: string): URL {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return new URL(path);
  }

  const origin =
    baseUrl ??
    (typeof window !== "undefined" ? window.location.origin : undefined);

  if (!origin) {
    return new URL(path, "http://localhost");
  }

  return new URL(path, origin);
}

async function parseBody<T>(response: Response): Promise<T> {
  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  if (contentType && contentType.startsWith("text/")) {
    return (await response.text()) as unknown as T;
  }
  return (await response.arrayBuffer()) as unknown as T;
}

export class ApiClient {
  private readonly requestMiddleware: RequestMiddleware[] = [];
  private readonly responseMiddleware: ResponseMiddleware[] = [];

  constructor(private readonly options: ApiClientOptions = {}) {}

  useRequest(middleware: RequestMiddleware): () => void {
    this.requestMiddleware.push(middleware);
    return () => {
      const index = this.requestMiddleware.indexOf(middleware);
      if (index >= 0) {
        this.requestMiddleware.splice(index, 1);
      }
    };
  }

  useResponse(middleware: ResponseMiddleware): () => void {
    this.responseMiddleware.push(middleware);
    return () => {
      const index = this.responseMiddleware.indexOf(middleware);
      if (index >= 0) {
        this.responseMiddleware.splice(index, 1);
      }
    };
  }

  async request<T = unknown>(input: ApiRequest): Promise<ApiResponse<T>> {
    const attemptLimit = this.options.retry?.attempts ?? 1;
    let attempt = 0;
    let lastError: ApiError | null = null;

    while (attempt < attemptLimit) {
      try {
        const processed = await this.runRequestMiddleware({
          ...input,
          method: input.method ?? "GET",
        });
        const result = await this.executeRequest<T>(processed);
        return await this.runResponseMiddleware(result, processed);
      } catch (error) {
        lastError = error as ApiError;
        attempt += 1;
        if (attempt >= attemptLimit) {
          break;
        }
        const backoff = this.options.retry?.backoffMs ?? 250;
        await new Promise((resolve) => setTimeout(resolve, backoff * attempt));
      }
    }

    throw lastError ?? new Error("Request failed");
  }

  get<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "GET", ...init });
  }

  post<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "POST", ...init });
  }

  put<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "PUT", ...init });
  }

  patch<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "PATCH", ...init });
  }

  delete<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: "DELETE", ...init });
  }

  private async runRequestMiddleware(request: ApiRequest): Promise<ApiRequest> {
    let current: ApiRequest = {
      ...request,
      headers: mergeHeaders(this.options.defaultHeaders, request.headers),
      timeoutMs: request.timeoutMs ?? this.options.timeoutMs,
    };

    for (const middleware of this.requestMiddleware) {
      current = await middleware(current);
    }

    return current;
  }

  private async runResponseMiddleware<T>(
    response: ApiResponse<T>,
    request: ApiRequest,
  ): Promise<ApiResponse<T>> {
    let current: ApiResponse<unknown> = response;
    for (const middleware of this.responseMiddleware) {
      current = await middleware(current, request);
    }
    return current as ApiResponse<T>;
  }

  private async executeRequest<T>(
    request: ApiRequest,
  ): Promise<ApiResponse<T>> {
    const url = buildUrl(this.options.baseUrl, request.url);
    const query = request.query;

    if (query) {
      Object.entries(query).forEach(([key, value]) => {
        if (value === undefined || value === null) return;
        url.searchParams.set(key, String(value));
      });
    }

    const controller =
      typeof AbortController !== "undefined" ? new AbortController() : null;
    const timeout = request.timeoutMs ?? this.options.timeoutMs;
    let timer: ReturnType<typeof setTimeout> | undefined;

    if (controller && timeout) {
      timer = setTimeout(() => controller.abort(), timeout);
    }

    try {
      const headers = new Headers(request.headers);
      let body: BodyInit | undefined;

      if (request.body !== undefined && request.body !== null) {
        if (
          typeof request.body === "string" ||
          request.body instanceof Blob ||
          request.body instanceof FormData
        ) {
          body = request.body as BodyInit;
        } else {
          headers.set(
            "Content-Type",
            headers.get("Content-Type") ?? "application/json",
          );
          body = JSON.stringify(request.body);
        }
      }

      const response = await fetch(url, {
        method: request.method as RequestInit["method"],
        body,
        headers,
        signal: controller?.signal,
      } as RequestInit);

      const data = await parseBody<T>(response);

      const apiResponse: ApiResponse<T> = {
        status: response.status,
        headers: response.headers,
        data,
        raw: response,
      };

      if (!response.ok) {
        const error: ApiError = Object.assign(new Error(response.statusText), {
          status: response.status,
          response: apiResponse as ApiResponse<unknown>,
          request,
        });
        throw error;
      }

      return apiResponse;
    } catch (error) {
      const apiError: ApiError = Object.assign(
        error instanceof Error ? error : new Error("Request failed"),
        { request },
      );
      throw apiError;
    } finally {
      if (timer) {
        clearTimeout(timer);
      }
    }
  }
}
