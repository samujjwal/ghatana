/**
 * @tutorputor/api-client — API error types
 *
 * Typed error classes for all failure modes returned by the TutorPutor API.
 *
 * @doc.type module
 * @doc.purpose Typed API error hierarchy
 * @doc.layer product
 * @doc.pattern ValueObject
 */

// ---------------------------------------------------------------------------
// Error types
// ---------------------------------------------------------------------------

export type ApiErrorCode =
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "UNPROCESSABLE_ENTITY"
  | "RATE_LIMITED"
  | "SERVER_ERROR"
  | "NETWORK_ERROR"
  | "TIMEOUT";

/**
 * Base class for all API client errors.
 */
export class ApiError extends Error {
  constructor(
    public readonly code: ApiErrorCode,
    message: string,
    public readonly status?: number,
    public readonly body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message = "Authentication required") {
    super("UNAUTHORIZED", message, 401);
    this.name = "UnauthorizedError";
  }
}

export class ForbiddenError extends ApiError {
  constructor(message = "Access denied") {
    super("FORBIDDEN", message, 403);
    this.name = "ForbiddenError";
  }
}

export class NotFoundError extends ApiError {
  constructor(message = "Resource not found") {
    super("NOT_FOUND", message, 404);
    this.name = "NotFoundError";
  }
}

export class ConflictError extends ApiError {
  constructor(message = "Conflict", body?: unknown) {
    super("CONFLICT", message, 409, body);
    this.name = "ConflictError";
  }
}

export class UnprocessableEntityError extends ApiError {
  constructor(message = "Validation failed", body?: unknown) {
    super("UNPROCESSABLE_ENTITY", message, 422, body);
    this.name = "UnprocessableEntityError";
  }
}

export class RateLimitedError extends ApiError {
  constructor(retryAfterSeconds?: number) {
    super("RATE_LIMITED", "Rate limit exceeded", 429);
    this.name = "RateLimitedError";
    this.retryAfterSeconds = retryAfterSeconds ?? null;
  }

  readonly retryAfterSeconds: number | null;
}

export class ServerError extends ApiError {
  constructor(status: number, body?: unknown) {
    super("SERVER_ERROR", `Server error: ${status}`, status, body);
    this.name = "ServerError";
  }
}

export class NetworkError extends ApiError {
  constructor(cause?: unknown) {
    super("NETWORK_ERROR", "Network request failed");
    this.name = "NetworkError";
    this.cause = cause;
  }
}

// ---------------------------------------------------------------------------
// HTTP response → ApiError mapper
// ---------------------------------------------------------------------------

/**
 * Maps an HTTP Response to the appropriate typed ApiError.
 * Call this when `response.ok === false`.
 */
export async function mapHttpError(response: Response): Promise<ApiError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = undefined;
  }

  switch (response.status) {
    case 401:
      return new UnauthorizedError();
    case 403:
      return new ForbiddenError();
    case 404:
      return new NotFoundError();
    case 409:
      return new ConflictError("Conflict", body);
    case 422:
      return new UnprocessableEntityError("Validation failed", body);
    case 429:
      return new RateLimitedError();
    default:
      return new ServerError(response.status, body);
  }
}
