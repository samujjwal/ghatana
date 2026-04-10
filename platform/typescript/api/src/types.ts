export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/**
 * Minimal schema interface compatible with Zod (`z.ZodType`) and any other
 * schema library that exposes a `.parse()` method.
 *
 * Pass a `ResponseSchema<T>` as the `schema` option on a request to validate
 * the response data at runtime. If validation fails, the client throws a
 * {@link ValidationError}.
 *
 * @example
 * ```ts
 * import { z } from 'zod';
 * const UserSchema = z.object({ id: z.string(), name: z.string() });
 * const user = await client.get('/me', { schema: UserSchema });
 * ```
 */
export interface ResponseSchema<T = unknown> {
  parse(data: unknown): T;
}

/**
 * Thrown when the response body does not satisfy the provided
 * {@link ResponseSchema}. Callers can distinguish validation failures
 * from network / HTTP errors by checking `instanceof ValidationError`.
 */
export class ValidationError extends Error {
  constructor(
    message: string,
    public readonly cause: unknown,
  ) {
    super(message);
    this.name = "ValidationError";
  }
}

/**
 * Categorises an API error so callers can respond appropriately without
 * inspecting raw HTTP status codes.
 *
 * - `NETWORK`  — no response received (timeout, DNS failure, CORS abort, etc.)
 * - `CLIENT`   — 4xx response; request is invalid and should NOT be retried
 * - `SERVER`   — 5xx response; server-side failure that MAY be retried
 */
export type ApiErrorCategory = "NETWORK" | "CLIENT" | "SERVER";

export interface ApiRequestInit extends Omit<RequestInit, "body" | "method"> {
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
  headers?: Record<string, string>;
  timeoutMs?: number;
  /**
   * Optional schema used to validate the parsed response data.
   * Accepts any object with a `.parse(data: unknown): T` method, making it
   * compatible with Zod, Valibot, and other schema-validation libraries.
   * Throws {@link ValidationError} when the response does not satisfy the schema.
   */
  schema?: ResponseSchema;
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
  /**
   * Categorises the failure type. Absent only for legacy error objects that
   * were constructed outside this client.
   */
  category?: ApiErrorCategory;
  /** Whether this error type is safe to retry. */
  isRetryable: boolean;
}

export type RequestMiddleware = (
  request: ApiRequest,
) => Promise<ApiRequest> | ApiRequest;

// Allow response middlewares to operate on any typed ApiResponse; individual middleware
// can still narrow types internally. Using `any` here avoids generic propagation issues
// when middlewares with different response payload types are registered.
export type ResponseMiddleware = (
  response: ApiResponse<unknown>,
  request: ApiRequest,
) => Promise<ApiResponse<unknown>> | ApiResponse<unknown>;

export interface ApiClientOptions {
  baseUrl?: string;
  defaultHeaders?: Record<string, string>;
  timeoutMs?: number;
  /**
   * API version sent as an {@code X-Api-Version} header on every request.
   * Consumers can also set this via {@code defaultHeaders}.
   */
  apiVersion?: string;
  retry?: {
    attempts: number;
    /** Base delay in milliseconds. Actual delay = backoffMs * 2^attempt (exponential). */
    backoffMs?: number;
  };
}
