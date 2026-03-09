/**
 * @file Type definitions for background transport module
 */

export interface TransportOptions {
  /** Request timeout in milliseconds */
  timeout?: number;
  /** Additional headers to include */
  headers?: Record<string, string>;
  /** Whether to retry on failure */
  retry?: boolean | {
    /** Number of retry attempts */
    attempts: number;
    /** Delay between retries in milliseconds */
    delay: number;
  };
}

export interface TransportResponse<T = unknown> {
  /** Response data */
  data: T;
  /** HTTP status code */
  status: number;
  /** Response headers */
  headers: Record<string, string>;
  /** Whether the request was successful */
  ok: boolean;
  /** Error message if the request failed */
  error?: string;
}

export function sendEnvelope<T = unknown>(
  url: string, 
  data: unknown, 
  options?: TransportOptions
): Promise<TransportResponse<T>>;

export function createTransport(baseUrl: string, options?: TransportOptions): {
  send: <T = unknown>(endpoint: string, data: unknown) => Promise<TransportResponse<T>>;
  get: <T = unknown>(endpoint: string, params?: Record<string, unknown>) => Promise<TransportResponse<T>>;
  post: <T = unknown>(endpoint: string, data: unknown) => Promise<TransportResponse<T>>;
  put: <T = unknown>(endpoint: string, data: unknown) => Promise<TransportResponse<T>>;
  delete: <T = unknown>(endpoint: string) => Promise<TransportResponse<T>>;
};
