/**
 * Prometheus metrics collection for monitoring Guardian backend performance and usage.
 *
 * <p><b>Purpose</b><br>
 * Defines and registers custom Prometheus metrics for monitoring Guardian backend
 * operations including authentication attempts, active sessions, database queries,
 * policy enforcement, device connectivity, and HTTP request performance.
 *
 * <p><b>Metrics Registry</b><br>
 * Exports Prometheus registry at /metrics endpoint for scraping by monitoring systems.
 * Includes default Node.js metrics (CPU, memory, event loop lag, GC stats) plus
 * 20+ custom application metrics.
 *
 * <p><b>Custom Metrics</b><br>
 * - authAttempts: Counter for login attempts (labels: result=success|failure)
 * - activeSessions: Gauge for concurrent authenticated sessions
 * - dbQueryDuration: Histogram for database query latency (p50, p95, p99)
 * - policyEnforcement: Counter for policy blocks by type
 * - devicesConnected: Gauge for active device count
 * - httpRequestDuration: Histogram for HTTP endpoint latency (labels: method, route, status)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * authAttempts.inc({ result: 'success' });
 * activeSessions.set(currentSessionCount);
 * dbQueryDuration.observe(queryTime);
 * httpRequestDuration.labels('POST', '/api/login', '200').observe(0.234);
 * }</pre>
 *
 * <p><b>Scraping</b><br>
 * Metrics exposed at GET /metrics in Prometheus exposition format.
 * Configure Prometheus server to scrape this endpoint every 15-30 seconds.
 *
 * @doc.type class
 * @doc.purpose Prometheus metrics collection and registry
 * @doc.layer backend
 * @doc.pattern Utility
 */
import client from 'prom-client';
import { logger } from './logger';

// Create a Registry
export const register = new client.Registry();

// Add default metrics (CPU, memory, event loop lag, etc.)
client.collectDefaultMetrics({
  register,
  prefix: 'guardian_',
});

/**
 * HTTP Metrics
 */
export const httpRequestDuration = new client.Histogram({
  name: 'guardian_http_request_duration_ms',
  help: 'Duration of HTTP requests in milliseconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [10, 50, 100, 300, 500, 1000, 3000, 5000], // milliseconds
});

export const httpRequestTotal = new client.Counter({
  name: 'guardian_http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'route', 'status_code'],
});

/**
 * Authentication Metrics
 */
export const authAttempts = new client.Counter({
  name: 'guardian_auth_attempts_total',
  help: 'Total authentication attempts',
  labelNames: ['type', 'status'], // type: login|register|refresh, status: success|failure
});

export const activeUsers = new client.Gauge({
  name: 'guardian_active_users',
  help: 'Number of currently active users',
});

export const activeSessions = new client.Gauge({
  name: 'guardian_active_sessions',
  help: 'Number of active sessions (refresh tokens)',
});

/**
 * Database Metrics
 */
export const dbQueryDuration = new client.Histogram({
  name: 'guardian_db_query_duration_ms',
  help: 'Duration of database queries in milliseconds',
  labelNames: ['operation'], // SELECT, INSERT, UPDATE, DELETE
  buckets: [10, 50, 100, 300, 500, 1000, 3000],
});

export const dbConnectionsActive = new client.Gauge({
  name: 'guardian_db_connections_active',
  help: 'Number of active database connections',
});

export const dbQueryErrors = new client.Counter({
  name: 'guardian_db_query_errors_total',
  help: 'Total number of database query errors',
  labelNames: ['type'], // error type
});

/**
 * Policy Enforcement Metrics
 */
export const policiesEnforced = new client.Counter({
  name: 'guardian_policies_enforced_total',
  help: 'Total number of policy enforcements',
  labelNames: ['policy_type', 'device_type'], // policy_type: time_limit|app_block|website_block
});

export const blockedAttempts = new client.Counter({
  name: 'guardian_blocked_attempts_total',
  help: 'Total number of blocked access attempts',
  labelNames: ['type', 'device_type'], // type: app|website|time
});

/**
 * Business Metrics
 */
export const childrenRegistered = new client.Gauge({
  name: 'guardian_children_registered',
  help: 'Total number of registered children',
});

export const devicesConnected = new client.Gauge({
  name: 'guardian_devices_connected',
  help: 'Total number of connected devices',
  labelNames: ['type'], // mobile|desktop|browser
});

export const activePolicies = new client.Gauge({
  name: 'guardian_active_policies',
  help: 'Total number of active policies',
});

/**
 * Agent / Command Metrics
 */
export const deviceCommandsEnqueued = new client.Counter({
  name: 'guardian_device_commands_enqueued_total',
  help: 'Total number of device commands enqueued for agents',
  labelNames: ['kind', 'action', 'source'], // source: parent|child|system
});

export const agentSyncRequests = new client.Counter({
  name: 'guardian_agent_sync_requests_total',
  help: 'Total number of agent sync requests',
  labelNames: ['result'], // result: success|device_not_found|error
});

/**
 * Rate Limiting Metrics
 */
export const rateLimitExceeded = new client.Counter({
  name: 'guardian_rate_limit_exceeded_total',
  help: 'Total number of rate limit violations',
  labelNames: ['endpoint'],
});

/**
 * Email Metrics
 */
export const emailsSent = new client.Counter({
  name: 'guardian_emails_sent_total',
  help: 'Total number of emails sent',
  labelNames: ['type'], // verification|password_reset|notification
});

export const emailErrors = new client.Counter({
  name: 'guardian_email_errors_total',
  help: 'Total number of email sending errors',
  labelNames: ['type'],
});

/**
 * Error Metrics
 */
export const applicationErrors = new client.Counter({
  name: 'guardian_application_errors_total',
  help: 'Total number of application errors',
  labelNames: ['type', 'severity'], // type: validation|database|auth|unknown, severity: low|medium|high|critical
});

// Register all metrics
register.registerMetric(httpRequestDuration);
register.registerMetric(httpRequestTotal);
register.registerMetric(authAttempts);
register.registerMetric(activeUsers);
register.registerMetric(activeSessions);
register.registerMetric(dbQueryDuration);
register.registerMetric(dbConnectionsActive);
register.registerMetric(dbQueryErrors);
register.registerMetric(policiesEnforced);
register.registerMetric(blockedAttempts);
register.registerMetric(childrenRegistered);
register.registerMetric(devicesConnected);
register.registerMetric(activePolicies);
register.registerMetric(deviceCommandsEnqueued);
register.registerMetric(agentSyncRequests);
register.registerMetric(rateLimitExceeded);
register.registerMetric(emailsSent);
register.registerMetric(emailErrors);
register.registerMetric(applicationErrors);

logger.info('Prometheus metrics registered', {
  metricsCount: register.getMetricsAsArray().length,
});

export default register;
