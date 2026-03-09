/**
 * OpenTelemetry configuration for Prometheus metrics export and observability.
 *
 * <p><b>Purpose</b><br>
 * Configures OpenTelemetry with Prometheus exporter to expose metrics at /metrics
 * endpoint for monitoring system scraping. Provides service metadata tracking and
 * performance monitoring for Guardian backend operations.
 *
 * <p><b>Metrics Export</b><br>
 * Exposes Prometheus scrape endpoint at http://localhost:9464/metrics (configurable
 * via PROMETHEUS_PORT). Metrics include:
 * - HTTP request duration and count (by route and status)
 * - Database query performance
 * - Authentication success/failure rates
 * - Active sessions and connected devices
 * - System metrics (CPU, memory, event loop)
 *
 * <p><b>Integration</b><br>
 * Works with utils/metrics.ts for custom metric registration. OpenTelemetry provides
 * the export infrastructure while Prometheus client defines the actual metrics.
 *
 * <p><b>Configuration</b><br>
 * - PROMETHEUS_PORT: Metrics scrape endpoint port (default 9464)
 * - Service name: guardian-backend
 * - Environment: production|staging|development
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Call at app startup
 * import { prometheusExporter } from './utils/telemetry';
 * 
 * // Metrics automatically exported at /metrics
 * // Configure Prometheus to scrape: http://localhost:9464/metrics
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose OpenTelemetry Prometheus metrics export configuration
 * @doc.layer backend
 * @doc.pattern Utility
 */
import { PrometheusExporter } from '@opentelemetry/exporter-prometheus';
import { logger } from './logger';

// Prometheus metrics exporter (scrape endpoint at /metrics)
export const prometheusExporter = new PrometheusExporter({
  port: parseInt(process.env.PROMETHEUS_PORT || '9464'),
});

/**
 * Start telemetry services
 */
export function startTelemetry() {
  try {
    prometheusExporter.startServer();
    logger.info('Telemetry initialized', {
      prometheusPort: parseInt(process.env.PROMETHEUS_PORT || '9464'),
    });
  } catch (error) {
    logger.error('Failed to initialize telemetry', { error });
  }
}

/**
 * Graceful shutdown
 */
export async function shutdownTelemetry() {
  try {
    await prometheusExporter.shutdown();
    logger.info('Telemetry shut down gracefully');
  } catch (error) {
    logger.error('Error during telemetry shutdown', { error });
  }
}

// Graceful shutdown on process termination
process.on('SIGTERM', async () => {
  await shutdownTelemetry();
});

export default prometheusExporter;
