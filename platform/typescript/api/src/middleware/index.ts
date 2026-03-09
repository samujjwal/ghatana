/**
 * Middleware module exports.
 *
 * @doc.type module
 * @doc.purpose API client middleware
 * @doc.layer libs
 * @doc.pattern Module
 */

// Re-export types from parent
export type {
    ApiRequest,
    ApiResponse,
    RequestMiddleware,
    ResponseMiddleware,
} from './types';

// Auth middleware
export {
    createAuthMiddleware,
    createTenantMiddleware,
    createUserMiddleware,
} from './auth';
export type { AuthMiddlewareConfig, AuthMiddleware } from './auth';

// Telemetry middleware
export {
    createTelemetryMiddleware,
    createLoggerMiddleware,
} from './telemetry';
export type {
    TelemetryMiddlewareConfig,
    TelemetryMiddleware,
    RequestTelemetryEvent,
    ResponseTelemetryEvent,
    ErrorTelemetryEvent,
} from './telemetry';
