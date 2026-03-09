import { ApiError, ApiRequest, ApiResponse, ApiClientOptions, RequestMiddleware, ResponseMiddleware, ApiRequestInit } from './types';

function mergeHeaders(
  base: Record<string, string> | undefined,
  override: Record<string, string> | undefined
): Record<string, string> {
  return { ...(base ?? {}), ...(override ?? {}) };
}

function buildUrl(baseUrl: string | undefined, path: string): URL {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return new URL(path);
  }

  const origin =
    baseUrl ?? (typeof window !== 'undefined' ? window.location.origin : undefined);

  if (!origin) {
    return new URL(path, 'http://localhost');
  }

  return new URL(path, origin);
}

async function parseBody<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return (await response.json()) as T;
  }
  if (contentType && contentType.startsWith('text/')) {
    return (await response.text()) as unknown as T;
  }
  return (await response.arrayBuffer()) as unknown as T;
}

export class ApiClient {
  private readonly requestMiddleware: RequestMiddleware[] = [];
  private readonly responseMiddleware: ResponseMiddleware[] = [];

  constructor(private readonly options: ApiClientOptions = {}) { }

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
          method: input.method ?? 'GET',
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

    throw lastError ?? new Error('Request failed');
  }

  get<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: 'GET', ...init });
  }

  post<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: 'POST', ...init });
  }

  put<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: 'PUT', ...init });
  }

  patch<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: 'PATCH', ...init });
  }

  delete<T = unknown>(url: string, init: ApiRequestInit = {}) {
    return this.request<T>({ url, method: 'DELETE', ...init });
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
    request: ApiRequest
  ): Promise<ApiResponse<T>> {
    let current: ApiResponse<unknown> = response;
    for (const middleware of this.responseMiddleware) {
      current = await middleware(current, request);
    }
    return current as ApiResponse<T>;
  }

  private async executeRequest<T>(request: ApiRequest): Promise<ApiResponse<T>> {
    const url = buildUrl(this.options.baseUrl, request.url);
    const query = request.query;

    if (query) {
      Object.entries(query).forEach(([key, value]) => {
        if (value === undefined || value === null) return;
        url.searchParams.set(key, String(value));
      });
    }

    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timeout = request.timeoutMs ?? this.options.timeoutMs;
    let timer: ReturnType<typeof setTimeout> | undefined;

    if (controller && timeout) {
      timer = setTimeout(() => controller.abort(), timeout);
    }

    try {
      const headers = new Headers(request.headers);
      let body: BodyInit | undefined;

      if (request.body !== undefined && request.body !== null) {
        if (typeof request.body === 'string' || request.body instanceof Blob || request.body instanceof FormData) {
          body = request.body as BodyInit;
        } else {
          headers.set('Content-Type', headers.get('Content-Type') ?? 'application/json');
          body = JSON.stringify(request.body);
        }
      }

      const response = await fetch(url, {
        method: request.method as RequestInit['method'],
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
        error instanceof Error ? error : new Error('Request failed'),
        { request }
      );
      throw apiError;
    } finally {
      if (timer) {
        clearTimeout(timer);
      }
    }
  }
}
