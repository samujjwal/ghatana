// Common API types and interfaces

/** Standard API response format */
export interface ApiResponse<T = unknown> {
  /** Whether the request was successful */
  success: boolean;
  
  /** Response data (if successful) */
  data?: T;
  
  /** Error information (if not successful) */
  error?: {
    /** Error code */
    code: string;
    
    /** Human-readable error message */
    message: string;
    
    /** Additional error details */
    details?: unknown;
  };
  
  /** Metadata about the response */
  meta?: {
    /** Request ID for tracing */
    requestId?: string;
    
    /** Server timestamp */
    timestamp?: string;
    
    /** Pagination information */
    pagination?: {
      total: number;
      page: number;
      pageSize: number;
      totalPages: number;
    };
  };
}

/** Standard pagination parameters */
export interface PaginationParams {
  /** Page number (1-based) */
  page?: number;
  
  /** Number of items per page */
  pageSize?: number;
  
  /** Sort field */
  sortBy?: string;
  
  /** Sort direction */
  sortOrder?: 'asc' | 'desc';
}

/** Standard error response */
export interface ApiError extends Error {
  /** HTTP status code */
  statusCode: number;
  
  /** Error code */
  code: string;
  
  /** Additional error details */
  details?: unknown;
}

/** HTTP methods */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';

/** HTTP headers */
export type HttpHeaders = Record<string, string>;

/** HTTP request options */
export interface HttpRequestOptions {
  /** Request headers */
  headers?: HttpHeaders;
  
  /** Query parameters */
  params?: Record<string, unknown>;
  
  /** Request timeout in milliseconds */
  timeout?: number;
  
  /** Whether to include credentials */
  withCredentials?: boolean;
  
  /** Response type */
  responseType?: 'json' | 'text' | 'blob' | 'arraybuffer' | 'document' | 'stream';
}
