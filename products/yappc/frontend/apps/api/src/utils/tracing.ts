/**
 * OpenTelemetry Tracing Configuration
 *
 * Provides distributed tracing capabilities using OpenTelemetry.
 * Exports traces to Jaeger via OTLP protocol for end-to-end request visibility.
 *
 * Uses manual instrumentation for Fastify to avoid ESM/CommonJS compatibility issues
 * with auto-instrumentation packages like import-in-the-middle.
 *
 * @doc.type module
 * @doc.purpose OpenTelemetry distributed tracing setup
 * @doc.layer platform
 * @doc.pattern Utility
 */
import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { Resource } from '@opentelemetry/resources';
import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions';
import { trace, context, SpanStatusCode } from '@opentelemetry/api';
import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';

// OTLP Exporter Configuration
const traceExporter = new OTLPTraceExporter({
    url: process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4318/v1/traces',
    headers: {},
});

// Resource attributes
const resource = new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: 'yappc-api',
    [SemanticResourceAttributes.SERVICE_VERSION]: '1.0.0',
    [SemanticResourceAttributes.DEPLOYMENT_ENVIRONMENT]: process.env.NODE_ENV || 'development',
});

// Initialize OpenTelemetry SDK without auto-instrumentations
const sdk = new NodeSDK({
    resource,
    traceExporter,
    instrumentations: [],
});

// Get tracer for manual instrumentation
const tracer = trace.getTracer('yappc-api', '1.0.0');

/**
 * Start OpenTelemetry tracing
 */
export function startTracing() {
    try {
        sdk.start();
        console.log('✅ OpenTelemetry tracing initialized');
        console.log(`📊 Exporting traces to: ${traceExporter.url}`);
    } catch (error) {
        console.error('❌ Failed to initialize OpenTelemetry tracing:', error);
    }
}

/**
 * Shutdown OpenTelemetry tracing gracefully
 */
export async function shutdownTracing() {
    try {
        await sdk.shutdown();
        console.log('✅ OpenTelemetry tracing shut down');
    } catch (error) {
        console.error('❌ Failed to shutdown OpenTelemetry tracing:', error);
    }
}

/**
 * Manual instrumentation for Fastify
 *
 * Creates spans for each HTTP request with:
 * - Request method and URL
 * - Response status code
 * - Request/response duration
 * - Error tracking
 *
 * This avoids ESM/CommonJS issues with auto-instrumentation packages.
 */
export function instrumentFastify(app: FastifyInstance) {
    // Skip instrumentation for health/metrics endpoints
    const skipPaths = ['/health', '/metrics', '/readiness', '/liveness'];

    app.addHook('onRequest', async (request: FastifyRequest, reply: FastifyReply) => {
        // Skip instrumentation for monitoring endpoints
        if (skipPaths.some((path) => request.url.startsWith(path))) {
            return;
        }

        // Create a span for the incoming request
        const span = tracer.startSpan(`${request.method} ${request.url}`, {
            attributes: {
                'http.method': request.method,
                'http.url': request.url,
                'http.target': request.url,
                'http.host': request.hostname,
                'http.scheme': request.protocol,
            },
        });

        // Store span and start time in request for later use
        (request as unknown).span = span;
        (request as unknown).spanStartTime = Date.now();

        // Set up context for the request
        return context.with(trace.setSpan(context.active(), span), () => {
            // Continue with the request
        });
    });

    app.addHook('onResponse', async (request: FastifyRequest, reply: FastifyReply) => {
        const span = (request as unknown).span;
        if (!span) return;

        // Record response information
        const duration = Date.now() - (request as unknown).spanStartTime;
        span.setAttributes({
            'http.status_code': reply.statusCode,
            'http.response_content_length': reply.getHeader('content-length') || 0,
            'http.duration_ms': duration,
        });

        // Set span status based on HTTP status code
        if (reply.statusCode >= 400) {
            span.setStatus({ code: SpanStatusCode.ERROR, message: `HTTP ${reply.statusCode}` });
        } else {
            span.setStatus({ code: SpanStatusCode.OK });
        }

        // End the span
        span.end();
    });

    app.addHook('onError', async (request: FastifyRequest, reply: FastifyReply, error: Error) => {
        const span = (request as unknown).span;
        if (!span) return;

        // Record error information
        span.recordException(error);
        span.setStatus({ code: SpanStatusCode.ERROR, message: error.message });
    });
}

// Export SDK and tracer for advanced use cases
export { sdk, tracer };

