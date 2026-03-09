/**
 * Telemetry middleware for ApiClient.
 *
 * <p><b>Purpose</b><br>
 * Provides reusable telemetry middleware that tracks API request metrics
 * including latency, errors, and request details.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { ApiClient } from '@ghatana/api';
 * import { createTelemetryMiddleware } from '@ghatana/api/middleware';
 * 
 * const client = new ApiClient({ baseUrl: '/api' });
 * const telemetry = createTelemetryMiddleware({
 *   onRequest: (event) => posthog.capture('api_request', event),
 *   onResponse: (event) => posthog.capture('api_response', event),
 *   onError: (event) => posthog.capture('api_error', event),
 * });
 * 
 * client.useRequest(telemetry.request);
 * client.useResponse(telemetry.response);
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Telemetry middleware
 * @doc.layer libs
 * @doc.pattern Middleware
 */

import type {
    ApiRequest,
    ApiResponse,
    RequestMiddleware,
    ResponseMiddleware,
} from './types';

/**
 * Request telemetry event.
 */
export interface RequestTelemetryEvent {
    /** Request ID (generated) */
    requestId: string;
    /** HTTP method */
    method: string;
    /** Request URL (sanitized) */
    url: string;
    /** Timestamp when request started */
    timestamp: number;
    /** Additional context */
    context?: Record<string, unknown>;
}

/**
 * Response telemetry event.
 */
export interface ResponseTelemetryEvent {
    /** Request ID */
    requestId: string;
    /** HTTP method */
    method: string;
    /** Request URL (sanitized) */
    url: string;
    /** Response status code */
    status: number;
    /** Request duration in ms */
    durationMs: number;
    /** Response size in bytes (if available) */
    responseSize?: number;
    /** Timestamp when response received */
    timestamp: number;
    /** Whether the request was successful (2xx) */
    success: boolean;
}

/**
 * Error telemetry event.
 */
export interface ErrorTelemetryEvent {
    /** Request ID */
    requestId: string;
    /** HTTP method */
    method: string;
    /** Request URL (sanitized) */
    url: string;
    /** Error status code (if available) */
    status?: number;
    /** Error message */
    error: string;
    /** Error type/name */
    errorType: string;
    /** Request duration in ms */
    durationMs: number;
    /** Timestamp when error occurred */
    timestamp: number;
}

/**
 * Telemetry middleware configuration.
 */
export interface TelemetryMiddlewareConfig {
    /** Callback for request start */
    onRequest?: (event: RequestTelemetryEvent) => void;
    /** Callback for successful response */
    onResponse?: (event: ResponseTelemetryEvent) => void;
    /** Callback for errors */
    onError?: (event: ErrorTelemetryEvent) => void;
    /** Function to sanitize URLs (remove sensitive data) */
    sanitizeUrl?: (url: string) => string;
    /** Additional context to include in all events */
    context?: Record<string, unknown>;
    /** Skip telemetry for these URL patterns */
    skipPatterns?: RegExp[];
}

/**
 * Telemetry middleware result.
 */
export interface TelemetryMiddleware {
    /** Request middleware */
    request: RequestMiddleware;
    /** Response middleware */
    response: ResponseMiddleware;
}

// Store request timing data
const requestTimings = new Map<string, number>();

/**
 * Generate a unique request ID.
 */
function generateRequestId(): string {
    return `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Default URL sanitizer - removes query params and path segments that look like IDs.
 */
function defaultSanitizeUrl(url: string): string {
    try {
        const parsed = new URL(url, 'http://localhost');
        // Remove query string
        let path = parsed.pathname;
        // Replace UUIDs with :id
        path = path.replace(
            /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi,
            ':id'
        );
        // Replace numeric IDs with :id
        path = path.replace(/\/\d+(?=\/|$)/g, '/:id');
        return `${parsed.host}${path}`;
    } catch {
        return url;
    }
}

/**
 * Create telemetry middleware.
 *
 * @param config - Telemetry configuration
 * @returns Request and response middleware
 */
export function createTelemetryMiddleware(
    config: TelemetryMiddlewareConfig = {}
): TelemetryMiddleware {
    const {
        onRequest,
        onResponse,
        onError,
        sanitizeUrl = defaultSanitizeUrl,
        context = {},
        skipPatterns = [],
    } = config;

    const shouldSkip = (url: string): boolean => {
        return skipPatterns.some((pattern) => pattern.test(url));
    };

    const request: RequestMiddleware = (req: ApiRequest): ApiRequest => {
        if (shouldSkip(req.url)) {
            return req;
        }

        const requestId = generateRequestId();
        const timestamp = Date.now();

        // Store timing for response
        requestTimings.set(requestId, timestamp);

        // Add request ID header for tracing
        const newReq: ApiRequest = {
            ...req,
            headers: {
                ...req.headers,
                'X-Request-ID': requestId,
            },
        };

        // Emit request event
        if (onRequest) {
            onRequest({
                requestId,
                method: req.method || 'GET',
                url: sanitizeUrl(req.url),
                timestamp,
                context,
            });
        }

        return newReq;
    };

    const response: ResponseMiddleware = (
        res: ApiResponse<unknown>,
        req: ApiRequest
    ): ApiResponse<unknown> => {
        if (shouldSkip(req.url)) {
            return res;
        }

        const requestId = req.headers?.['X-Request-ID'] || 'unknown';
        const startTime = requestTimings.get(requestId);
        const timestamp = Date.now();
        const durationMs = startTime ? timestamp - startTime : 0;

        // Clean up timing data
        requestTimings.delete(requestId);

        // Check if this is an error response
        const isError = res.status >= 400;
        const isSuccess = res.status >= 200 && res.status < 300;

        if (isError && onError) {
            onError({
                requestId,
                method: req.method || 'GET',
                url: sanitizeUrl(req.url),
                status: res.status,
                error: `HTTP ${res.status}`,
                errorType: 'HttpError',
                durationMs,
                timestamp,
            });
        }

        if (onResponse) {
            const contentLength = res.headers?.get('content-length');
            onResponse({
                requestId,
                method: req.method || 'GET',
                url: sanitizeUrl(req.url),
                status: res.status,
                durationMs,
                responseSize: contentLength ? parseInt(contentLength, 10) : undefined,
                timestamp,
                success: isSuccess,
            });
        }

        return res;
    };

    return { request, response };
}

/**
 * Create a simple console logger middleware for debugging.
 */
export function createLoggerMiddleware(
    prefix = '[API]'
): TelemetryMiddleware {
    return createTelemetryMiddleware({
        onRequest: (event) => {
            console.log(`${prefix} → ${event.method} ${event.url}`);
        },
        onResponse: (event) => {
            const status = event.success ? '✓' : '✗';
            console.log(
                `${prefix} ← ${status} ${event.status} ${event.url} (${event.durationMs}ms)`
            );
        },
        onError: (event) => {
            console.error(
                `${prefix} ✗ ${event.status || 'ERR'} ${event.url}: ${event.error}`
            );
        },
    });
}
