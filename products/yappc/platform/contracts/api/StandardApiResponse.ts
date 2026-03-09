/**
 * Standardized API Error Response
 * 
 * All YAPPC services must use this consistent error response format
 * to ensure uniform error handling across the platform.
 * 
 * @doc.type class
 * @doc.purpose Standard error response format for all APIs
 * @doc.layer platform
 * @doc.pattern Standardized Response
 */

export interface ApiErrorResponse {
  /** HTTP status code */
  status: number;
  
  /** Error type/category */
  error: string;
  
  /** Human-readable error message */
  message: string;
  
  /** Error code for programmatic handling */
  code: string;
  
  /** Request ID for tracing */
  requestId: string;
  
  /** Timestamp when error occurred */
  timestamp: string;
  
  /** Additional error details (optional) */
  details?: Record<string, unknown>;
  
  /** Link to documentation */
  documentation?: string;
}

/**
 * Standardized API Success Response
 * 
 * @doc.type class
 * @doc.purpose Standard success response wrapper
 * @doc.layer platform
 * @doc.pattern Standardized Response
 */
export interface ApiSuccessResponse<T> {
  /** HTTP status code */
  status: number;
  
  /** Response data */
  data: T;
  
  /** Response metadata */
  meta?: {
    /** Pagination info */
    pagination?: {
      page: number;
      perPage: number;
      total: number;
      totalPages: number;
    };
    /** Request ID for tracing */
    requestId: string;
    /** Timestamp */
    timestamp: string;
  };
}

/**
 * Standard API Response Type
 * Union type for all API responses
 */
export type ApiResponse<T> = ApiSuccessResponse<T> | ApiErrorResponse;

/**
 * Standard Error Codes
 * Use these codes consistently across all services
 */
export const ErrorCodes = {
  // 4xx Client Errors
  BAD_REQUEST: 'E4000',
  UNAUTHORIZED: 'E4001',
  FORBIDDEN: 'E4003',
  NOT_FOUND: 'E4004',
  CONFLICT: 'E4009',
  VALIDATION_ERROR: 'E4220',
  RATE_LIMITED: 'E4290',
  
  // 5xx Server Errors
  INTERNAL_ERROR: 'E5000',
  SERVICE_UNAVAILABLE: 'E5030',
  TIMEOUT: 'E5040',
  
  // Domain-specific errors (add as needed)
  AGENT_NOT_FOUND: 'E4100',
  WORKSPACE_NOT_FOUND: 'E4101',
  PROJECT_NOT_FOUND: 'E4102',
  INVALID_STATE_TRANSITION: 'E4221',
} as const;

export type ErrorCode = typeof ErrorCodes[keyof typeof ErrorCodes];

/**
 * Create standardized error response
 */
export function createErrorResponse(
  status: number,
  error: string,
  message: string,
  code: string,
  requestId: string,
  details?: Record<string, unknown>
): ApiErrorResponse {
  return {
    status,
    error,
    message,
    code,
    requestId,
    timestamp: new Date().toISOString(),
    details,
    documentation: `https://docs.yappc.dev/errors/${code}`,
  };
}

/**
 * Create standardized success response
 */
export function createSuccessResponse<T>(
  data: T,
  requestId: string,
  pagination?: {
    page: number;
    perPage: number;
    total: number;
    totalPages: number;
  }
): ApiSuccessResponse<T> {
  return {
    status: 200,
    data,
    meta: {
      requestId,
      timestamp: new Date().toISOString(),
      ...(pagination && { pagination }),
    },
  };
}
