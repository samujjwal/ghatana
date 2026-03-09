export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiRequestInit extends Omit<RequestInit, 'body' | 'method'> {
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
  headers?: Record<string, string>;
  timeoutMs?: number;
}

export interface ApiRequest extends ApiRequestInit {
  url: string;
  method?: HttpMethod;
}

export interface ApiResponse<T = unknown> {
  status: number;
  headers: Headers;
  data: T;
  raw: Response;
}

export interface ApiError<T = unknown> extends Error {
  status?: number;
  response?: ApiResponse<T>;
  request: ApiRequest;
}

export type RequestMiddleware = (
  request: ApiRequest
) => Promise<ApiRequest> | ApiRequest;

// Allow response middlewares to operate on any typed ApiResponse; individual middleware
// can still narrow types internally. Using `any` here avoids generic propagation issues
// when middlewares with different response payload types are registered.
export type ResponseMiddleware = (
  response: ApiResponse<unknown>,
  request: ApiRequest
) => Promise<ApiResponse<unknown>> | ApiResponse<unknown>;

export interface ApiClientOptions {
  baseUrl?: string;
  defaultHeaders?: Record<string, string>;
  timeoutMs?: number;
  retry?: {
    attempts: number;
    backoffMs?: number;
  };
}
