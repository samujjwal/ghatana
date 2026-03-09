/**
 * OpenTelemetry Tracing Setup for FlashIt Gateway
 *
 * Must be imported BEFORE any other modules.
 * Usage in server.ts: import './plugins/tracing';
 *
 * @doc.type plugin
 * @doc.purpose Initialize OpenTelemetry distributed tracing
 * @doc.layer product
 * @doc.pattern Instrumentation
 */

import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { Resource } from '@opentelemetry/resources';
import {
  ATTR_SERVICE_NAME,
  ATTR_SERVICE_VERSION,
  ATTR_DEPLOYMENT_ENVIRONMENT,
} from '@opentelemetry/semantic-conventions';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { diag, DiagConsoleLogger, DiagLogLevel } from '@opentelemetry/api';

// Enable diagnostic logging in development
if (process.env.OTEL_DEBUG === 'true') {
  diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);
}

const OTEL_ENDPOINT = process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4318';
const SERVICE_NAME = process.env.OTEL_SERVICE_NAME || 'flashit-gateway';
const SERVICE_VERSION = process.env.npm_package_version || '1.0.0';
const ENVIRONMENT = process.env.NODE_ENV || 'development';

// Only initialize if tracing is enabled
const TRACING_ENABLED = process.env.OTEL_TRACING_ENABLED === 'true';

let sdk: NodeSDK | undefined;

if (TRACING_ENABLED) {
  const traceExporter = new OTLPTraceExporter({
    url: `${OTEL_ENDPOINT}/v1/traces`,
  });

  const metricExporter = new OTLPMetricExporter({
    url: `${OTEL_ENDPOINT}/v1/metrics`,
  });

  const resource = new Resource({
    [ATTR_SERVICE_NAME]: SERVICE_NAME,
    [ATTR_SERVICE_VERSION]: SERVICE_VERSION,
    [ATTR_DEPLOYMENT_ENVIRONMENT]: ENVIRONMENT,
  });

  sdk = new NodeSDK({
    resource,
    spanProcessors: [new BatchSpanProcessor(traceExporter)],
    metricReader: new PeriodicExportingMetricReader({
      exporter: metricExporter,
      exportIntervalMillis: 30000,
    }),
    instrumentations: [
      getNodeAutoInstrumentations({
        // Instrument HTTP, Fastify, pg (Prisma), ioredis, etc.
        '@opentelemetry/instrumentation-http': {
          enabled: true,
          ignoreIncomingRequestHook: (req) => {
            // Skip health checks and metrics endpoints from tracing
            const url = req.url || '';
            return url === '/health' || url === '/metrics' || url === '/ready';
          },
        },
        '@opentelemetry/instrumentation-fastify': { enabled: true },
        '@opentelemetry/instrumentation-pg': { enabled: true },
        '@opentelemetry/instrumentation-ioredis': { enabled: true },
        '@opentelemetry/instrumentation-fs': { enabled: false }, // Too noisy
        '@opentelemetry/instrumentation-dns': { enabled: false },
      }),
    ],
  });

  sdk.start();
  console.log(`[OTEL] Tracing initialized for ${SERVICE_NAME} → ${OTEL_ENDPOINT}`);

  // Graceful shutdown
  const shutdown = async () => {
    try {
      await sdk?.shutdown();
      console.log('[OTEL] Tracing shut down successfully');
    } catch (err) {
      console.error('[OTEL] Error shutting down tracing:', err);
    }
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
} else {
  console.log('[OTEL] Tracing disabled (set OTEL_TRACING_ENABLED=true to enable)');
}

export { sdk as otelSdk };
