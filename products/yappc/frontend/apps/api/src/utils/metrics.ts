/**
 * Prometheus Metrics Configuration
 *
 * Provides application metrics in Prometheus format for monitoring and observability.
 * Exposes metrics at GET /metrics endpoint for Prometheus server scraping.
 *
 * @doc.type module
 * @doc.purpose Prometheus metrics collection and registry
 * @doc.layer platform
 * @doc.pattern Utility
 */
import client from 'prom-client';

// Create a Registry
export const register = new client.Registry();

// Add default metrics (CPU, memory, event loop lag, etc.)
client.collectDefaultMetrics({
    register,
    prefix: 'yappc_api_',
    timeout: 5000,
});

/**
 * HTTP Request Duration
 */
export const httpRequestDuration = new client.Histogram({
    name: 'yappc_api_http_request_duration_seconds',
    help: 'Duration of HTTP requests in seconds',
    labelNames: ['method', 'route', 'status_code'],
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 2, 5],
    registers: [register],
});

/**
 * HTTP Request Total
 */
export const httpRequestTotal = new client.Counter({
    name: 'yappc_api_http_requests_total',
    help: 'Total number of HTTP requests',
    labelNames: ['method', 'route', 'status_code'],
    registers: [register],
});

/**
 * GraphQL Operations
 */
export const graphqlOperationDuration = new client.Histogram({
    name: 'yappc_api_graphql_operation_duration_seconds',
    help: 'Duration of GraphQL operations in seconds',
    labelNames: ['operation_name', 'operation_type'],
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 2, 5],
    registers: [register],
});

/**
 * GraphQL Operations Total
 */
export const graphqlOperationTotal = new client.Counter({
    name: 'yappc_api_graphql_operations_total',
    help: 'Total number of GraphQL operations',
    labelNames: ['operation_name', 'operation_type', 'status'],
    registers: [register],
});

/**
 * WebSocket Connections
 */
export const websocketConnections = new client.Gauge({
    name: 'yappc_api_websocket_connections',
    help: 'Current number of WebSocket connections',
    labelNames: ['route'],
    registers: [register],
});

/**
 * Database Query Duration
 */
export const dbQueryDuration = new client.Histogram({
    name: 'yappc_api_db_query_duration_seconds',
    help: 'Duration of database queries in seconds',
    labelNames: ['query_type', 'table'],
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 2],
    registers: [register],
});

/**
 * Database Query Total
 */
export const dbQueryTotal = new client.Counter({
    name: 'yappc_api_db_queries_total',
    help: 'Total number of database queries',
    labelNames: ['query_type', 'table', 'status'],
    registers: [register],
});

/**
 * Canvas Collaboration Events
 */
export const canvasCollaborationEvents = new client.Counter({
    name: 'yappc_api_canvas_collaboration_events_total',
    help: 'Total number of canvas collaboration events',
    labelNames: ['event_type', 'projectId'],
    registers: [register],
});

/**
 * Active Projects
 */
export const activeProjects = new client.Gauge({
    name: 'yappc_api_active_projects',
    help: 'Number of active projects',
    registers: [register],
});

/**
 * Errors Total
 */
export const errorsTotal = new client.Counter({
    name: 'yappc_api_errors_total',
    help: 'Total number of errors',
    labelNames: ['error_type', 'route'],
    registers: [register],
});
